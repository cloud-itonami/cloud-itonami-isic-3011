(ns shipyard.governor
  "Shipyard Governor -- the independent compliance layer
  that earns the Shipyard Advisor the right to commit. The LLM has no
  notion of class-rules law, whether a block's own
  measured dimensional tolerance actually stays within its own
  recorded spec bounds, whether an NDT-detected defect against the
  block has actually stayed unresolved, or when an act stops being
  a draft and becomes a real-world robot block dispatch or
  class-evidence issuance, so this MUST be a separate system
  able to *reject* a proposal and fall back to HOLD -- the shipyard-
  manufacturer analog of `cloud-itonami-isic-6512`'s CasualtyGovernor.

  Six checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated class spec-basis, incomplete evidence, an out-of-
  spec block, an unresolved NDT defect, or a double dispatch/
  evidence-issuance). The confidence/actuation gate is SOFT: it asks a
  human to look (low confidence / actuation), and the human may
  approve -- but see `shipyard.phase`: for `:stake :actuation/
  dispatch-assembly`/`:actuation/issue-class-evidence` (a real
  safety-critical act) NO phase ever allows auto-commit either. Two
  independent layers agree that actuation is always a human call.

    1. Spec-basis                  -- did the requirements proposal cite
                                       an OFFICIAL source (`shipyard.
                                       facts`), or invent one?
    2. Evidence incomplete         -- for `:actuation/dispatch-
                                       assembly`/`:actuation/issue-
                                       class-evidence`, has the
                                       block actually been verified
                                       with a full CAE-simulation-
                                       report/CFD-verification-report/
                                       NDT-chain-of-custody-record/
                                       material-certification-record
                                       evidence checklist on file?
    3. Block tolerance out of
       range                         -- for `:actuation/dispatch-
                                       assembly`, INDEPENDENTLY
                                       recompute whether the
                                       block's own measured
                                       dimensional tolerance falls
                                       outside its own recorded spec
                                       bounds (`shipyard.registry/
                                       assembly-tolerance-out-of-
                                       range?`) -- needs no proposal
                                       inspection or stored-verdict
                                       lookup at all. The FOURTH
                                       instance of this fleet's two-
                                       sided range check family
                                       (`testlab.governor/within-
                                       tolerance-violations`/
                                       `conservation.governor/body-
                                       condition-out-of-range-
                                       violations`/`water.governor/
                                       contaminant-level-out-of-range-
                                       violations` established the
                                       first three).
    4. NDT defect unresolved        -- reported by THIS proposal itself
                                       (an `:ndt/screen` that just
                                       found an unresolved defect), or
                                       already on file for the
                                       block (`:ndt/screen`/
                                       `:actuation/issue-class-
                                       evidence`). Evaluated
                                       UNCONDITIONALLY (not scoped to a
                                       specific op), the SAME
                                       discipline `casualty.governor/
                                       sanctions-violations`/...
                                       (twenty-six prior siblings)...
                                       established -- the TWENTY-
                                       SEVENTH distinct application of
                                       this exact discipline, and the
                                       FIRST specifically for an NDT-
                                       defect concept. Like the
                                       sixteen most recent siblings'
                                       equivalent checks, this is
                                       exercised in tests/demo via
                                       `:ndt/screen` DIRECTLY, not via
                                       an actuation op against an
                                       unscreened block -- see this
                                       ns's own test suite.
    5. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/
                                       dispatch-assembly`/`:actuation/
                                       issue-class-evidence`
                                       (REAL safety-critical acts) ->
                                       escalate.

  Two more guards, double-dispatch/double-evidence-issuance
  prevention, are enforced but NOT listed as numbered HARD checks
  above because they need no upstream comparison at all --
  `already-dispatched-violations`/`already-certified-violations`
  refuse to dispatch a block action/issue class evidence
  for the SAME block twice, off dedicated `:block-dispatched?`/
  `:class-certified?` facts (never a `:status` value) -- the
  SAME 'check a dedicated boolean, not status' discipline every prior
  sibling governor's guards establish, informed by `cloud-itonami-
  isic-6492`'s status-lifecycle bug (ADR-2607071320)."
  (:require [shipyard.facts :as facts]
            [shipyard.registry :as registry]
            [shipyard.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Dispatching a real robot block action on a structure-critical
  structure and issuing real class evidence are the two real-
  world actuation events this actor performs -- a two-member set,
  matching every prior dual-actuation sibling's shape."
  #{:actuation/dispatch-block :actuation/issue-class-evidence})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:class-rules/verify` (or actuation) proposal with no spec-basis
  citation is a HARD violation -- never invent a jurisdiction's
  class-rules requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:class-rules/verify :actuation/dispatch-block :actuation/issue-class-evidence} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は耐空性要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:actuation/dispatch-block`/`:actuation/issue-class-
  evidence`, the jurisdiction's required CAE-simulation-report/CFD-
  verification-report/NDT-chain-of-custody-record/material-
  certification-record evidence must actually be satisfied -- do not
  trust the advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:actuation/dispatch-block :actuation/issue-class-evidence} op)
    (let [a (store/block st subject)
          verification (store/requirements-verification-of st subject)]
      (when-not (and verification
                     (facts/required-evidence-satisfied?
                      (:jurisdiction a) (:checklist verification)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(CAEシミュレーション報告書/CFD検証報告書/非破壊検査連鎖記録/材料証明記録等)が充足していない状態での提案"}]))))

(defn- block-tolerance-out-of-range-violations
  "For `:actuation/dispatch-block`, INDEPENDENTLY recompute whether
  the block's own dimensional tolerance falls outside its own
  recorded spec bounds via `shipyard.registry/assembly-tolerance-
  out-of-range?` -- needs no proposal inspection or stored-verdict
  lookup at all, since its inputs are permanent ground-truth fields
  already on the block."
  [{:keys [op subject]} st]
  (when (= op :actuation/dispatch-block)
    (let [a (store/block st subject)]
      (when (registry/block-tolerance-out-of-range? a)
        [{:rule :block-tolerance-out-of-range
          :detail (str subject " の実測公差(" (:dimensional-tolerance-actual a)
                      ")が仕様範囲[" (:dimensional-tolerance-min a) "," (:dimensional-tolerance-max a) "]を逸脱")}]))))

(defn- ndt-defect-unresolved-violations
  "An unresolved NDT-detected defect -- reported by THIS proposal (e.g.
  an `:ndt/screen` that itself just found one), or already on file in
  the store for the block (`:ndt/screen`/`:actuation/issue-
  class-evidence`) -- is a HARD, un-overridable hold.
  Evaluated UNCONDITIONALLY (not scoped to a specific op) so the
  screening op itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        block-id (when (contains? #{:ndt/screen :actuation/issue-class-evidence} op) subject)
        hit-on-file? (and block-id (= :unresolved (:verdict (store/ndt-screen-of st block-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :ndt-defect-unresolved
        :detail "未解決の非破壊検査欠陥がある状態での耐空性証拠発行提案は進められない"}])))

(defn- already-dispatched-violations
  "For `:actuation/dispatch-block`, refuses to dispatch a block
  action for the SAME block twice, off a dedicated `:assembly-
  dispatched?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/dispatch-block)
    (when (store/block-already-dispatched? st subject)
      [{:rule :already-dispatched
        :detail (str subject " は既に組立実行済み")}])))

(defn- already-certified-violations
  "For `:actuation/issue-class-evidence`, refuses to issue
  class evidence for the SAME block twice, off a dedicated
  `:class-certified?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/issue-class-evidence)
    (when (store/block-already-certified? st subject)
      [{:rule :already-certified
        :detail (str subject " は既に耐空性証拠発行済み")}])))

(defn check
  "Censors an Shipyard Advisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (block-tolerance-out-of-range-violations request st)
                           (ndt-defect-unresolved-violations request proposal st)
                           (already-dispatched-violations request st)
                           (already-certified-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
