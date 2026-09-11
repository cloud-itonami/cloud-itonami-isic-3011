(ns shipyard.governor-contract-test
  "The governor contract as executable tests -- the shipyard-
  manufacturer analog of `cloud-itonami-isic-6512`'s `casualty.
  governor-contract-test`. The single invariant under test:

    Shipyard Advisor never dispatches a block action or issues
    class evidence the Shipyard Governor would
    reject, `:actuation/dispatch-block`/`:actuation/issue-
    class-evidence` NEVER auto-commit at any phase,
    `:block/intake` (no direct capital risk) MAY auto-commit when
    clean, and every decision (commit OR hold) leaves exactly one
    ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [shipyard.store :as store]
            [shipyard.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :shipyard-engineer :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through verify -> approve, leaving a requirements
  verification on file. Uses distinct thread-ids per call site by
  suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :class-rules/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(defn- screen!
  "Walks `subject` through NDT-defect screening -> approve, leaving a
  screening on file. Only safe to call for a block whose defect
  status has already resolved -- an unresolved defect HARD-holds the
  screen itself (see `ndt-defect-is-held-and-unoverridable`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-screen") {:op :ndt/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-screen")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :block/intake :subject "block-1"
                   :patch {:id "block-1" :unit-name "Sakura Double-Bottom Block DB-04"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sakura Double-Bottom Block DB-04" (:unit-name (store/block db "block-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest requirements-verify-always-needs-approval
  (testing "verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :class-rules/verify :subject "block-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/requirements-verification-of db "block-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a class-rules/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :class-rules/verify :subject "block-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/requirements-verification-of db "block-1")) "no verification written"))))

(deftest dispatch-assembly-without-verification-is-held
  (testing "actuation/dispatch-block before any requirements verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :actuation/dispatch-block :subject "block-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest block-tolerance-out-of-range-is-held
  (testing "a block whose own dimensional tolerance falls outside its own spec bounds -> HOLD"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "block-3")
          res (exec-op actor "t5" {:op :actuation/dispatch-block :subject "block-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:block-tolerance-out-of-range} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest ndt-defect-is-held-and-unoverridable
  (testing "an unresolved NDT defect on a block -> HOLD, and never reaches request-approval -- exercised via :ndt/screen DIRECTLY, not via the actuation op against an unscreened block (see this actor's governor ns docstring / parksafety's ADR-2607071922 Decision 5 / eldercare's, museum's, conservation's, salon's, entertainment's, casework's, hospital's, facility's, school's, association's, leasing's, behavioral's, secondary's, card's, water's and telecom's ADR-0001s)"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :ndt/screen :subject "block-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:ndt-defect-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/ndt-screen-of db "block-4")) "no clearance written"))))

(deftest dispatch-assembly-always-escalates-then-human-decides
  (testing "a clean, fully-verified, in-spec block still ALWAYS interrupts for human approval -- actuation/dispatch-block is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "block-1")
          r1 (exec-op actor "t7" {:op :actuation/dispatch-block :subject "block-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, dispatch record drafted"
        (let [r2 (approve! actor "t7")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:block-dispatched? (store/block db "block-1"))))
          (is (= 1 (count (store/dispatch-history db))) "one draft dispatch record"))))))

(deftest issue-class-evidence-always-escalates-then-human-decides
  (testing "a clean, fully-verified, resolved-defect block still ALWAYS interrupts for human approval -- actuation/issue-class-evidence is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "block-1")
          _ (screen! actor "t8pre2" "block-1")
          r1 (exec-op actor "t8" {:op :actuation/issue-class-evidence :subject "block-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, evidence record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:class-certified? (store/block db "block-1"))))
          (is (= 1 (count (store/evidence-history db))) "one draft evidence record"))))))

(deftest dispatch-assembly-double-dispatch-is-held
  (testing "dispatching the same block's action twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "block-1")
          _ (exec-op actor "t9a" {:op :actuation/dispatch-block :subject "block-1"} operator)
          _ (approve! actor "t9a")
          res (exec-op actor "t9" {:op :actuation/dispatch-block :subject "block-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-dispatched} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/dispatch-history db))) "still only the one earlier dispatch"))))

(deftest issue-class-evidence-double-issuance-is-held
  (testing "issuing the same block's class evidence twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "block-1")
          _ (screen! actor "t10pre2" "block-1")
          _ (exec-op actor "t10a" {:op :actuation/issue-class-evidence :subject "block-1"} operator)
          _ (approve! actor "t10a")
          res (exec-op actor "t10" {:op :actuation/issue-class-evidence :subject "block-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-certified} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/evidence-history db))) "still only the one earlier evidence issuance"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :block/intake :subject "block-1"
                          :patch {:id "block-1" :unit-name "Sakura Double-Bottom Block DB-04"}} operator)
      (exec-op actor "b" {:op :class-rules/verify :subject "block-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
