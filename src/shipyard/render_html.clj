(ns shipyard.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave3): this repo previously had a hand-authored
  `docs/samples/operator-console.html` and NO generator that drives the
  REAL actor stack. This namespace runs
  (`shipyard.operation` -> `shipyard.governor` -> `shipyard.store`)
  through a scenario adapted from this repo's own `shipyard.sim` demo
  driver (`clojure -M:dev:run`, confirmed by actually running it before
  this file was written -- every disposition it produces
  (phase-3 auto-commit on `:block/intake`, escalate+approve on
  class-rules / NDT / both actuations, and HARD holds
  `:no-spec-basis` / `:block-tolerance-out-of-range` /
  `:ndt-defect-unresolved` / `:already-dispatched` /
  `:already-certified`) matches `shipyard.governor`'s own checks
  precisely) and rendered deterministically -- no invented numbers, no
  timestamps in the page content, byte-identical across reruns against
  the same seed (verify by diffing two consecutive runs).

  Styling follows the isic-9522/`applianceshop.render-html` reference:
  `jp-go-dds.skin/dds+skin` (デジタル庁デザインシステム + skin).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [shipyard.store :as store]
            [shipyard.operation :as op]
            [langgraph.graph :as g]))

(def ^:private operator
  {:actor-id "op-1" :actor-role :shipyard-engineer :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a freshly seeded store through a scenario mixing every
  disposition this actor can reach, using ONLY real block ids from
  `shipyard.store/demo-data`:

  block-1 (\"Sakura Double-Bottom Block DB-04\", JPN, clean tolerance
  0.05 ∈ [-0.10,0.10], no unresolved NDT) walks the full clean
  lifecycle: `:block/intake` is a phase-3 auto-commit (the ONLY
  auto-eligible op); `:class-rules/verify` and `:ndt/screen` always
  escalate (never auto at any phase) and are approved; both
  actuations (`:actuation/dispatch-block` /
  `:actuation/issue-class-evidence`) ALWAYS escalate (governor
  high-stakes + phase table agree independently) and are approved,
  producing draft records JPN-BLK-000000 / JPN-CLS-000000.

  Then five DISTINCT HARD-hold reasons, none of which ever reach a
  human (a human approver cannot override a HARD violation):
    - block-2 (jurisdiction ATL, not in `shipyard.facts`):
      `:class-rules/verify` HARD-holds on `:no-spec-basis`.
    - block-3 (JPN, tolerance 0.35 outside [-0.10,0.10]):
      class-rules verified first (clean escalate+approve) so the
      dispatch hold below is isolated to the tolerance check alone,
      then `:actuation/dispatch-block` HARD-holds on
      `:block-tolerance-out-of-range`.
    - block-4 (JPN, ndt-defect-unresolved? true):
      `:ndt/screen` HARD-holds on `:ndt-defect-unresolved`.
    - block-1 again: double `:actuation/dispatch-block` HARD-holds
      on `:already-dispatched`.
    - block-1 again: double `:actuation/issue-class-evidence`
      HARD-holds on `:already-certified`.

  Returns the resulting store -- every field read by `render` below is
  real governor/store output, not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    ;; block-1 clean lifecycle
    (exec! actor "t1" {:op :block/intake :subject "block-1"
                       :patch {:id "block-1"
                               :unit-name "Sakura Double-Bottom Block DB-04"}})
    (exec! actor "t2" {:op :class-rules/verify :subject "block-1"})
    (approve! actor "t2")
    (exec! actor "t3" {:op :ndt/screen :subject "block-1"})
    (approve! actor "t3")
    (exec! actor "t4" {:op :actuation/dispatch-block :subject "block-1"})
    (approve! actor "t4")
    (exec! actor "t5" {:op :actuation/issue-class-evidence :subject "block-1"})
    (approve! actor "t5")

    ;; HARD holds
    (exec! actor "t6" {:op :class-rules/verify :subject "block-2" :no-spec? true})
    (exec! actor "t7" {:op :class-rules/verify :subject "block-3"})
    (approve! actor "t7")
    (exec! actor "t8" {:op :actuation/dispatch-block :subject "block-3"})
    (exec! actor "t9" {:op :ndt/screen :subject "block-4"})
    (exec! actor "t10" {:op :actuation/dispatch-block :subject "block-1"})
    (exec! actor "t11" {:op :actuation/issue-class-evidence :subject "block-1"})
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger subject-id]
  (last (filter #(= (:subject %) subject-id) ledger)))

(defn- status-cell [ledger subject-id]
  (let [f (last-fact-for ledger subject-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (or (-> f :violations first :rule)
                     (-> f :basis first))]
        (str "<span class=\"critical\">HARD hold &middot; "
             (esc (name (or rule :unknown))) "</span>"))
      (= :approval-rejected (:t f)) "<span class=\"critical\">rejected</span>"
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- tolerance-cell
  [{:keys [dimensional-tolerance-actual dimensional-tolerance-min
           dimensional-tolerance-max]}]
  (let [a dimensional-tolerance-actual
        lo dimensional-tolerance-min
        hi dimensional-tolerance-max
        in? (and (number? a) (number? lo) (number? hi)
                 (<= lo a hi))]
    (if in?
      (str "<span class=\"ok\">" (esc a) " ∈ [" (esc lo) "," (esc hi) "]</span>")
      (str "<span class=\"critical\">" (esc a) " out of [" (esc lo) "," (esc hi) "]</span>"))))

(defn- ndt-cell [block]
  (if (:ndt-defect-unresolved? block)
    "<span class=\"critical\">unresolved</span>"
    "<span class=\"ok\">resolved / clean</span>"))

(defn- block-row [ledger block]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc (:id block))
          (esc (:unit-name block))
          (esc (:jurisdiction block))
          (tolerance-cell block)
          (ndt-cell block)
          (if (:block-dispatched? block)
            (str "<span class=\"ok\">dispatched &middot; "
                 (esc (:dispatch-number block)) "</span>")
            "<span class=\"muted\">not dispatched</span>")
          (if (:class-certified? block)
            (str "<span class=\"ok\">certified &middot; "
                 (esc (:evidence-number block)) "</span>")
            "<span class=\"muted\">not certified</span>")
          (status-cell ledger (:id block))))

(defn- kw-label [k]
  (if (keyword? k)
    (if-let [ns (namespace k)]
      (str ns "/" (name k))
      (name k))
    (str k)))

(defn- ledger-row [{:keys [t op subject disposition basis violations]}]
  (let [basis-str (or (some->> basis
                               (map #(if (keyword? %) (kw-label %) (str %)))
                               (str/join ", "))
                      (some->> violations (map :rule) (map kw-label) (str/join ", "))
                      (some-> disposition kw-label)
                      "")]
    (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
            (esc (kw-label t))
            (esc (kw-label (or op :n-a)))
            (esc subject)
            (esc basis-str))))

(defn- draft-row [m]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc (get m "record_id"))
          (esc (get m "kind"))
          (esc (get m "block_id"))
          (esc (get m "jurisdiction"))
          (if (get m "immutable")
            "<span class=\"ok\">immutable draft</span>"
            "<span class=\"muted\">n/a</span>")))

(def ^:private action-gate-rows
  ;; Static description of this actor's own op contract
  ;; (`shipyard.governor`/`shipyard.phase`) -- documentation of fixed
  ;; behavior, not runtime telemetry.
  ["        <tr><td><code>:block/intake</code></td><td><span class=\"ok\">phase-3 auto-commit when clean, no capital risk yet -- the ONLY auto-eligible op in this domain</span></td></tr>"
   "        <tr><td><code>:class-rules/verify</code></td><td><span class=\"warn\">ALWAYS human approval when clean &middot; HARD hold on <code>:no-spec-basis</code> (never fabricate class rules)</span></td></tr>"
   "        <tr><td><code>:ndt/screen</code></td><td><span class=\"warn\">ALWAYS human approval when clean &middot; HARD hold on <code>:ndt-defect-unresolved</code></span></td></tr>"
   "        <tr><td><code>:actuation/dispatch-block</code></td><td><span class=\"warn\">ALWAYS human approval &middot; real structure-critical act &middot; HARD on out-of-spec tolerance / incomplete evidence / already-dispatched</span></td></tr>"
   "        <tr><td><code>:actuation/issue-class-evidence</code></td><td><span class=\"warn\">ALWAYS human approval &middot; real class-society act &middot; HARD on unresolved NDT / incomplete evidence / already-certified</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        blocks (store/all-blocks db)
        block-rows (str/join "\n" (map (partial block-row ledger) blocks))
        ledger-rows (str/join "\n" (map ledger-row ledger))
        dispatch-rows (str/join "\n" (map draft-row (store/dispatch-history db)))
        evidence-rows (str/join "\n" (map draft-row (store/evidence-history db)))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-3011 &middot; building of ships and floating structures</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Building of ships and floating structures (ISIC 3011) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · block dispatch / class evidence always human-approved</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Hull blocks</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>shipyard.store</code> via <code>shipyard.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly. No invented data.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Block</th><th>Name</th><th>Jurisdiction</th><th>Tolerance</th><th>NDT</th><th>Dispatch</th><th>Class evidence</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     block-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Draft block-dispatch records</h2>\n"
     "    <p class=\"muted\">Unsigned drafts only — the yard's own act of signing/dispatching hardware is outside this actor's authority.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Record id</th><th>Kind</th><th>Block</th><th>Jurisdiction</th><th>Status</th></tr></thead>\n"
     "      <tbody>\n"
     dispatch-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Draft class-evidence records</h2>\n"
     "    <p class=\"muted\">Unsigned drafts only — class-society submission is a yard act, not this actor's.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Record id</th><th>Kind</th><th>Block</th><th>Jurisdiction</th><th>Status</th></tr></thead>\n"
     "      <tbody>\n"
     evidence-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Shipyard Manufacturing Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden by a human approver. Spec-basis, dimensional tolerance, NDT defect status and double-actuation guards are independently recomputed, never trusted from the advisor's proposal; real block dispatch and class-evidence issuance are always a human shipyard engineer's call, at every rollout phase.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)
        out-file (java.io.File. out)]
    (.. out-file getParentFile mkdirs)
    (spit out-file html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/dispatch-history db)) "dispatches,"
             (count (store/evidence-history db)) "class-evidence )")))
