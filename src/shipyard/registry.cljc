(ns shipyard.registry
  "Pure-function block-dispatch + class-evidence record
  construction -- an append-only shipyard book-of-record
  draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for an block-dispatch or
  class-evidence reference number -- every manufacturer/
  jurisdiction assigns its own reference format. This namespace does
  NOT invent one; it builds a jurisdiction-scoped sequence number and
  validates the record's required fields, the same honest, non-
  fabricating discipline `shipyard.facts` uses.

  `block-tolerance-out-of-range?` is the FOURTH instance of this
  fleet's two-sided range check family (`testlab.registry/within-
  tolerance?` established the first, `conservation.registry/body-
  condition-out-of-range?` the second, `water.registry/contaminant-
  level-out-of-range?` the third), applying the SAME lo/hi bounds-
  comparison shape to a block's own measured dimensional
  tolerance against the block's own recorded spec bounds.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real fab/assembly-line control system. It builds the
  RECORD a manufacturer would keep, not the act of dispatching the
  robot block action or issuing the class evidence itself
  (that is `shipyard.operation`'s `:actuation/dispatch-block`/
  `:actuation/issue-class-evidence`, always human-gated -- see
  README `Actuation`)."
  (:require [kotoba.lang.text :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  manufacturer's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn block-tolerance-out-of-range?
  "Does `block`'s own `:dimensional-tolerance-actual` fall outside
  its own `[:dimensional-tolerance-min :dimensional-tolerance-max]`
  recorded spec-bounds? A pure ground-truth check against the
  block's own permanent fields -- no upstream comparison needed.
  The FOURTH instance of this fleet's two-sided range check family
  (see ns docstring)."
  [{:keys [dimensional-tolerance-actual dimensional-tolerance-min dimensional-tolerance-max]}]
  (and (number? dimensional-tolerance-actual) (number? dimensional-tolerance-min) (number? dimensional-tolerance-max)
       (or (< dimensional-tolerance-actual dimensional-tolerance-min)
           (> dimensional-tolerance-actual dimensional-tolerance-max))))

(defn register-block-dispatch
  "Validate + construct the ASSEMBLY-DISPATCH registration DRAFT --
  the manufacturer's own act of dispatching a real robot fastening/
  layup/NDT action to complete an hull block. Pure function --
  does not touch any real fab/assembly-line control system; it builds
  the RECORD a manufacturer would keep. `shipyard.governor`
  independently re-verifies the block's own dimensional-tolerance
  sufficiency against its own spec bounds, and blocks a double-
  dispatch for the same block, before this is ever allowed to
  commit."
  [block-id jurisdiction sequence]
  (when-not (and block-id (not= block-id ""))
    (throw (ex-info "block-dispatch: block_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "block-dispatch: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "block-dispatch: sequence must be >= 0" {})))
  (let [dispatch-number (str (str/upper jurisdiction) "-BLK-" (zero-pad sequence 6))
        record {"record_id" dispatch-number
                "kind" "block-dispatch-draft"
                "block_id" block-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "dispatch_number" dispatch-number
     "certificate" (unsigned-certificate "BlockDispatch" dispatch-number dispatch-number)}))

(defn register-class-evidence
  "Validate + construct the AIRWORTHINESS-EVIDENCE registration DRAFT
  -- the manufacturer's own act of issuing real class evidence
  certifying a block as class-worthy. Pure function -- does not
  touch any real fab/assembly-line control system; it builds the
  RECORD a manufacturer would keep. `shipyard.governor` independently
  re-verifies the block's own NDT-defect resolution status, and
  blocks a double-issuance for the same block, before this is ever
  allowed to commit."
  [block-id jurisdiction sequence]
  (when-not (and block-id (not= block-id ""))
    (throw (ex-info "class-evidence: block_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "class-evidence: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "class-evidence: sequence must be >= 0" {})))
  (let [evidence-number (str (str/upper jurisdiction) "-CLS-" (zero-pad sequence 6))
        record {"record_id" evidence-number
                "kind" "class-evidence-draft"
                "block_id" block-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "evidence_number" evidence-number
     "certificate" (unsigned-certificate "ClassEvidence" evidence-number evidence-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
