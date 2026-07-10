(ns shipyard.facts
  "Per-jurisdiction shipbuilding / class-society rules catalog -- the
  G2-style spec-basis table the Shipyard Governor checks every
  `:class-rules/verify` proposal against ('did the advisor cite an
  OFFICIAL public source for this jurisdiction's class rules, or did
  it invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official maritime
  safety authority and a major class society (see `:provenance`); they
  are a STARTING catalog, not a survey of every flag state or class
  society. Extending coverage is additive: add one map to `catalog`,
  cite a real source, done -- never invent a jurisdiction's
  requirements to make coverage look bigger.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  CAE-simulation-report/CFD-verification-report/NDT-chain-of-custody-
  record/material-certification-record evidence set submitted in some
  form; `:legal-basis` / `:owner-authority` / `:provenance` are the G2
  citation the governor requires before any `:class-rules/verify`
  proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "国土交通省海事局 / 一般財団法人日本海事協会 (ClassNK)"
          :legal-basis "船舶安全法 / 鋼船規則 (ClassNK Rules for the Survey and Construction of Steel Ships)"
          :national-spec "船舶の構造・溶接・検査に関する法定要件および船級規則"
          :provenance "https://www.mlit.go.jp/maritime/"
          :required-evidence ["CAEシミュレーション報告書 (CAE-simulation-report)"
                              "CFD検証報告書 (CFD-verification-report)"
                              "非破壊検査連鎖記録 (NDT-chain-of-custody-record)"
                              "材料証明記録 (material-certification-record)"]}
   "USA" {:name "United States"
          :owner-authority "U.S. Coast Guard (USCG) / American Bureau of Shipping (ABS)"
          :legal-basis "46 CFR Subchapter F (Marine Engineering) / ABS Rules for Building and Classing Marine Vessels"
          :national-spec "US flag construction, survey and class requirements"
          :provenance "https://www.dco.uscg.mil/Our-Organization/Assistant-Commandant-for-Prevention-Policy-CG-5P/Commercial-Regulations-standards-CG-5PS/"
          :required-evidence ["CAE-simulation-report"
                              "CFD-verification-report"
                              "NDT-chain-of-custody-record"
                              "Material-certification-record"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Maritime and Coastguard Agency (MCA) / Lloyd's Register"
          :legal-basis "Merchant Shipping Act 1995 / Lloyd's Register Rules and Regulations for the Classification of Ships"
          :national-spec "UK flag construction, survey and class requirements"
          :provenance "https://www.gov.uk/government/organisations/maritime-and-coastguard-agency"
          :required-evidence ["CAE-simulation-report"
                              "CFD-verification-report"
                              "NDT-chain-of-custody-record"
                              "Material-certification-record"]}
   "DEU" {:name "Germany"
          :owner-authority "Bundesamt für Seeschifffahrt und Hydrographie (BSH) / DNV"
          :legal-basis "Schiffssicherheitsgesetz / DNV Rules for Classification of Ships"
          :national-spec "DE flag construction, survey and class requirements"
          :provenance "https://www.bsh.de/EN/TOPICS/Shipping/Shipping_node.html"
          :required-evidence ["CAE-Simulationsbericht (CAE-simulation-report)"
                              "CFD-Verifizierungsbericht (CFD-verification-report)"
                              "ZfP-Rückverfolgbarkeitsnachweis (NDT-chain-of-custody-record)"
                              "Werkstoffzertifikat (material-certification-record)"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to dispatch a
  block action or issue class evidence on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-3011 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all flag "
                 "states -- extend `shipyard.facts/catalog`, never "
                 "fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
