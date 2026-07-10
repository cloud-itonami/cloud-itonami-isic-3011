(ns shipyard.store
  "SSoT for the shipbuilding actor, behind a `Store`
  protocol so the backend is a swap, not a rewrite -- the same seam
  every prior `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/shipyard/store_contract_test.clj), which is the whole point:
  the actor, the Shipyard Governor and the audit ledger
  never know which SSoT they run on.

  Like `telecom.store`'s dual number-provisioning/billing-suppression
  history and every other dual-actuation sibling before it, this actor
  has TWO actuation events (dispatching a block action, issuing
  class evidence) acting on the SAME entity (a block),
  each with its OWN history collection, sequence counter and dedicated
  double-actuation-guard boolean (`:block-dispatched?`/
  `:class-certified?`, never a `:status` value) -- the same
  discipline every prior sibling governor's guards establish, informed
  by `cloud-itonami-isic-6492`'s status-lifecycle bug
  (ADR-2607071320).

  The ledger stays append-only on every backend: 'which block was
  screened for an unresolved NDT defect, which block action was
  dispatched, which class evidence was issued, on what
  jurisdictional basis, approved by whom' is always a query over an
  immutable log -- the audit trail a community trusting an shipyard
  manufacturer needs, and the evidence a manufacturer needs if a
  dispatch or class-evidence decision is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [shipyard.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (block [s id])
  (all-blocks [s])
  (ndt-screen-of [s block-id] "committed NDT-defect screening verdict for a block, or nil")
  (requirements-verification-of [s block-id] "committed requirements verification, or nil")
  (ledger [s])
  (dispatch-history [s] "the append-only block-dispatch history (shipyard.registry drafts)")
  (evidence-history [s] "the append-only class-evidence history (shipyard.registry drafts)")
  (next-dispatch-sequence [s jurisdiction] "next dispatch-number sequence for a jurisdiction")
  (next-evidence-sequence [s jurisdiction] "next evidence-number sequence for a jurisdiction")
  (block-already-dispatched? [s block-id] "has this block's action already been dispatched?")
  (block-already-certified? [s block-id] "has this block's class evidence already been issued?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-blocks [s blocks] "replace/seed the block directory (map id->block)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained block set covering both actuation
  lifecycles (dispatching a block action, issuing class
  evidence) so the actor + tests run offline."
  []
  {:blocks
   {"block-1" {:id "block-1" :unit-name "Sakura Double-Bottom Block DB-04"
                  :dimensional-tolerance-actual 0.05 :dimensional-tolerance-min -0.10 :dimensional-tolerance-max 0.10
                  :ndt-defect-unresolved? false
                  :block-dispatched? false :class-certified? false
                  :jurisdiction "JPN" :status :intake}
    "block-2" {:id "block-2" :unit-name "Atlantis Side-Shell Block SS-12"
                  :dimensional-tolerance-actual 0.05 :dimensional-tolerance-min -0.10 :dimensional-tolerance-max 0.10
                  :ndt-defect-unresolved? false
                  :block-dispatched? false :class-certified? false
                  :jurisdiction "ATL" :status :intake}
    "block-3" {:id "block-3" :unit-name "鈴木トランスバース・ウェブ TW-07"
                  :dimensional-tolerance-actual 0.35 :dimensional-tolerance-min -0.10 :dimensional-tolerance-max 0.10
                  :ndt-defect-unresolved? false
                  :block-dispatched? false :class-certified? false
                  :jurisdiction "JPN" :status :intake}
    "block-4" {:id "block-4" :unit-name "田中バルクヘッド BH-03"
                  :dimensional-tolerance-actual 0.05 :dimensional-tolerance-min -0.10 :dimensional-tolerance-max 0.10
                  :ndt-defect-unresolved? true
                  :block-dispatched? false :class-certified? false
                  :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- dispatch-block!
  "Backend-agnostic `:block/mark-dispatched` -- looks up the
  block via the protocol and drafts the block-dispatch record,
  and returns {:result .. :block-patch ..} for the caller to
  persist."
  [s block-id]
  (let [a (block s block-id)
        seq-n (next-dispatch-sequence s (:jurisdiction a))
        result (registry/register-block-dispatch block-id (:jurisdiction a) seq-n)]
    {:result result
     :block-patch {:block-dispatched? true
                      :dispatch-number (get result "dispatch_number")}}))

(defn- issue-class-evidence!
  "Backend-agnostic `:block/mark-certified` -- looks up the
  block via the protocol and drafts the class-evidence
  record, and returns {:result .. :block-patch ..} for the caller
  to persist."
  [s block-id]
  (let [a (block s block-id)
        seq-n (next-evidence-sequence s (:jurisdiction a))
        result (registry/register-class-evidence block-id (:jurisdiction a) seq-n)]
    {:result result
     :block-patch {:class-certified? true
                      :evidence-number (get result "evidence_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (block [_ id] (get-in @a [:blocks id]))
  (all-blocks [_] (sort-by :id (vals (:blocks @a))))
  (ndt-screen-of [_ id] (get-in @a [:ndt-screens id]))
  (requirements-verification-of [_ block-id] (get-in @a [:verifications block-id]))
  (ledger [_] (:ledger @a))
  (dispatch-history [_] (:dispatches @a))
  (evidence-history [_] (:evidences @a))
  (next-dispatch-sequence [_ jurisdiction] (get-in @a [:dispatch-sequences jurisdiction] 0))
  (next-evidence-sequence [_ jurisdiction] (get-in @a [:evidence-sequences jurisdiction] 0))
  (block-already-dispatched? [_ block-id] (boolean (get-in @a [:blocks block-id :block-dispatched?])))
  (block-already-certified? [_ block-id] (boolean (get-in @a [:blocks block-id :class-certified?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :block/upsert
      (swap! a update-in [:blocks (:id value)] merge value)

      :verification/set
      (swap! a assoc-in [:verifications (first path)] payload)

      :ndt-screen/set
      (swap! a assoc-in [:ndt-screens (first path)] payload)

      :block/mark-dispatched
      (let [block-id (first path)
            {:keys [result block-patch]} (dispatch-block! s block-id)
            jurisdiction (:jurisdiction (block s block-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:dispatch-sequences jurisdiction] (fnil inc 0))
                       (update-in [:blocks block-id] merge block-patch)
                       (update :dispatches registry/append result))))
        result)

      :block/mark-certified
      (let [block-id (first path)
            {:keys [result block-patch]} (issue-class-evidence! s block-id)
            jurisdiction (:jurisdiction (block s block-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:evidence-sequences jurisdiction] (fnil inc 0))
                       (update-in [:blocks block-id] merge block-patch)
                       (update :evidences registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-blocks [s blocks] (when (seq blocks) (swap! a assoc :blocks blocks)) s))

(defn seed-db
  "A MemStore seeded with the demo block set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :verifications {} :ndt-screens {} :ledger [] :dispatch-sequences {}
                           :dispatches [] :evidence-sequences {} :evidences []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (verification/NDT-screen payloads, ledger facts,
  dispatch/evidence records) are stored as EDN strings so `langchain.
  db` doesn't expand them into sub-entities -- the same convention
  every sibling actor's store uses."
  {:block/id                       {:db/unique :db.unique/identity}
   :verification/block-id          {:db/unique :db.unique/identity}
   :ndt-screen/block-id            {:db/unique :db.unique/identity}
   :ledger/seq                        {:db/unique :db.unique/identity}
   :dispatch/seq                      {:db/unique :db.unique/identity}
   :evidence/seq                      {:db/unique :db.unique/identity}
   :dispatch-sequence/jurisdiction    {:db/unique :db.unique/identity}
   :evidence-sequence/jurisdiction    {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- block->tx [{:keys [id unit-name dimensional-tolerance-actual dimensional-tolerance-min dimensional-tolerance-max
                             ndt-defect-unresolved?
                             block-dispatched? class-certified?
                             jurisdiction status dispatch-number evidence-number]}]
  (cond-> {:block/id id}
    unit-name                                  (assoc :block/unit-name unit-name)
    dimensional-tolerance-actual                (assoc :block/dimensional-tolerance-actual dimensional-tolerance-actual)
    dimensional-tolerance-min                   (assoc :block/dimensional-tolerance-min dimensional-tolerance-min)
    dimensional-tolerance-max                   (assoc :block/dimensional-tolerance-max dimensional-tolerance-max)
    (some? ndt-defect-unresolved?)              (assoc :block/ndt-defect-unresolved? ndt-defect-unresolved?)
    (some? block-dispatched?)                (assoc :block/block-dispatched? block-dispatched?)
    (some? class-certified?)            (assoc :block/class-certified? class-certified?)
    jurisdiction                                (assoc :block/jurisdiction jurisdiction)
    status                                      (assoc :block/status status)
    dispatch-number                             (assoc :block/dispatch-number dispatch-number)
    evidence-number                             (assoc :block/evidence-number evidence-number)))

(def ^:private block-pull
  [:block/id :block/unit-name :block/dimensional-tolerance-actual
   :block/dimensional-tolerance-min :block/dimensional-tolerance-max
   :block/ndt-defect-unresolved? :block/block-dispatched? :block/class-certified?
   :block/jurisdiction :block/status :block/dispatch-number :block/evidence-number])

(defn- pull->block [m]
  (when (:block/id m)
    {:id (:block/id m) :unit-name (:block/unit-name m)
     :dimensional-tolerance-actual (:block/dimensional-tolerance-actual m)
     :dimensional-tolerance-min (:block/dimensional-tolerance-min m)
     :dimensional-tolerance-max (:block/dimensional-tolerance-max m)
     :ndt-defect-unresolved? (boolean (:block/ndt-defect-unresolved? m))
     :block-dispatched? (boolean (:block/block-dispatched? m))
     :class-certified? (boolean (:block/class-certified? m))
     :jurisdiction (:block/jurisdiction m) :status (:block/status m)
     :dispatch-number (:block/dispatch-number m) :evidence-number (:block/evidence-number m)}))

(defrecord DatomicStore [conn]
  Store
  (block [_ id]
    (pull->block (d/pull (d/db conn) block-pull [:block/id id])))
  (all-blocks [_]
    (->> (d/q '[:find [?id ...] :where [?e :block/id ?id]] (d/db conn))
         (map #(pull->block (d/pull (d/db conn) block-pull [:block/id %])))
         (sort-by :id)))
  (ndt-screen-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?aid
                :where [?k :ndt-screen/block-id ?aid] [?k :ndt-screen/payload ?p]]
              (d/db conn) id)))
  (requirements-verification-of [_ block-id]
    (dec* (d/q '[:find ?p . :in $ ?aid
                :where [?a :verification/block-id ?aid] [?a :verification/payload ?p]]
              (d/db conn) block-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (dispatch-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :dispatch/seq ?s] [?e :dispatch/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (evidence-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :evidence/seq ?s] [?e :evidence/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-dispatch-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :dispatch-sequence/jurisdiction ?j] [?e :dispatch-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-evidence-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :evidence-sequence/jurisdiction ?j] [?e :evidence-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (block-already-dispatched? [s block-id]
    (boolean (:block-dispatched? (block s block-id))))
  (block-already-certified? [s block-id]
    (boolean (:class-certified? (block s block-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :block/upsert
      (d/transact! conn [(block->tx value)])

      :verification/set
      (d/transact! conn [{:verification/block-id (first path) :verification/payload (enc payload)}])

      :ndt-screen/set
      (d/transact! conn [{:ndt-screen/block-id (first path) :ndt-screen/payload (enc payload)}])

      :block/mark-dispatched
      (let [block-id (first path)
            {:keys [result block-patch]} (dispatch-block! s block-id)
            jurisdiction (:jurisdiction (block s block-id))
            next-n (inc (next-dispatch-sequence s jurisdiction))]
        (d/transact! conn
                     [(block->tx (assoc block-patch :id block-id))
                      {:dispatch-sequence/jurisdiction jurisdiction :dispatch-sequence/next next-n}
                      {:dispatch/seq (count (dispatch-history s)) :dispatch/record (enc (get result "record"))}])
        result)

      :block/mark-certified
      (let [block-id (first path)
            {:keys [result block-patch]} (issue-class-evidence! s block-id)
            jurisdiction (:jurisdiction (block s block-id))
            next-n (inc (next-evidence-sequence s jurisdiction))]
        (d/transact! conn
                     [(block->tx (assoc block-patch :id block-id))
                      {:evidence-sequence/jurisdiction jurisdiction :evidence-sequence/next next-n}
                      {:evidence/seq (count (evidence-history s)) :evidence/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-blocks [s blocks]
    (when (seq blocks) (d/transact! conn (mapv block->tx (vals blocks)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:blocks ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [blocks]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-blocks s blocks))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo block set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
