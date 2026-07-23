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
                              "Werkstoffzertifikat (material-certification-record)"]}
   ;; KOR verified this session via the law.go.kr Open API (lawService.do,
   ;; law ID 001742, fetched as machine-readable XML -- the search UI was
   ;; not used) and https://www.krs.co.kr/ + https://www.mof.go.kr/en/index.do
   ;; (both WebFetched directly). Honest gap disclosure: 선박안전법 제60조제2항
   ;; (Ship Safety Act art. 60(2)) authorizes 해양수산부장관 (the MOF Minister)
   ;; to delegate ship registration/seaworthiness-evaluation work ("선급업무")
   ;; to ANY domestic-or-foreign corporation meeting Minister-published
   ;; criteria (a "선급법인") -- the statute text itself does NOT name 한국선급
   ;; (Korean Register) by name. KR is listed as the class-society half of
   ;; owner-authority because KR's own official site (fetched this session)
   ;; describes itself as performing exactly this delegated role ("Government
   ;; delegation inspections on behalf of the Korean government"), not
   ;; because the Act names KR. Similarly, KR's rule body is cited only as
   ;; "Classification Technical Rules" -- the term KR's own English site
   ;; uses for it -- because a more specific formal rule-book title (e.g. a
   ;; numbered "Rules for the Classification of Steel Ships") was not
   ;; independently confirmed this session, so it is deliberately not
   ;; asserted.
   "KOR" {:name "South Korea"
          :owner-authority "해양수산부 (Ministry of Oceans and Fisheries, MOF) / 한국선급 (Korean Register, KR)"
          :legal-basis "선박안전법 (Ship Safety Act) 제60조제2항(검사등업무의 대행 -- MOF가 지정한 선급법인에 선급업무를 위임) / 선박법 (Ships Act, 선박의 국적ㆍ톤수측정 및 등록) / 한국선급(KR) Classification Technical Rules"
          :national-spec "한국 선적 선박의 건조ㆍ검사ㆍ등록 및 선급 요건 (Korean-flag construction, survey, registration and class requirements)"
          :provenance "https://www.mof.go.kr/en/index.do"
          :required-evidence ["CAE 시뮬레이션 보고서 (CAE-simulation-report)"
                              "CFD 검증 보고서 (CFD-verification-report)"
                              "비파괴검사 이력관리 기록 (NDT-chain-of-custody-record)"
                              "재료 증명서 (material-certification-record)"]}
   ;; NOR verified this session directly against sdir.no (Sjøfartsdirektoratet
   ;; / Norwegian Maritime Authority, fetched https://www.sdir.no/en/ --
   ;; confirmed it is the official NMA site administering ship-safety
   ;; regulation), lovdata.no (fetched the FULL raw HTML of
   ;; https://lovdata.no/dokument/NLE/lov/2007-02-16-9 -- Lovdata's English
   ;; translation of skipssikkerhetsloven -- and extracted its plain text
   ;; directly, not via a summarizer, to confirm verbatim wording) and
   ;; dnv.com (fetched https://www.dnv.com/maritime/ ,
   ;; https://www.dnv.com/about/in-brief/our-history/ and the 2023-07-13
   ;; DNV news item on the July 2023 rules edition). Honest gap disclosure,
   ;; mirroring the KOR entry's pattern: § 41 of the Ship Safety and
   ;; Security Act authorizes the Ministry to delegate "supervisory
   ;; authority" to "one or more classification societies" by agreement
   ;; and to regulate "requirements for recognised classification
   ;; societies" -- the statute text itself does NOT name DNV. DNV is
   ;; listed as the class-society half of :owner-authority because (a) DNV
   ;; (Det Norske Veritas) was founded in 1864 in Oslo, Norway -- per DNV's
   ;; own history page, "as a membership organization ... by mutual marine
   ;; insurance clubs. We establish a uniform set of rules and procedures
   ;; to assess the condition and seaworthiness of vessels" -- and remains
   ;; headquartered in Norway, and (b) DNV's own maritime homepage
   ;; describes DNV as "the world's leading classification society and a
   ;; recognized advisor for the maritime industry", not because a specific
   ;; delegation instrument naming DNV was independently confirmed this
   ;; session (none was found, so none is asserted). The Lovdata page
   ;; itself discloses (fetched verbatim): "This is an unofficial
   ;; translation of the Norwegian version of the Act and is provided for
   ;; information purposes only. Legal authenticity remains with the
   ;; Norwegian version as published in Norsk Lovtidend. ... The
   ;; translation is provided by Sjøfartsdirektoratet – Norwegian Maritime
   ;; Authority" -- i.e. the English text is the regulator's own
   ;; translation, not this session's translation. The Norwegian phrases
   ;; in :required-evidence below, by contrast, ARE this session's own
   ;; translation of the generic evidence-record set (mirroring the
   ;; docstring's CAE/CFD/NDT/material-certification checklist) -- they are
   ;; not official Sjøfartsdirektoratet or DNV terminology and are not
   ;; presented as such.
   "NOR" {:name "Norway"
          :owner-authority "Sjøfartsdirektoratet (Norwegian Maritime Authority, NMA) / DNV (Det Norske Veritas)"
          :legal-basis "Lov om skipssikkerhet / skipssikkerhetsloven (Ship Safety and Security Act, LOV-2007-02-16-9) § 9 (technical safety -- design, construction and equipment, incl. hull strength) and § 41 (supervisory authority -- Ministry may delegate to recognised classification societies) / DNV Rules for Classification of Ships"
          :national-spec "NO flag construction, survey and class requirements"
          :provenance "https://www.sdir.no/en/"
          :required-evidence ["CAE-simuleringsrapport (CAE-simulation-report)"
                              "CFD-verifiseringsrapport (CFD-verification-report)"
                              "NDT-sporbarhetsjournal (NDT-chain-of-custody-record)"
                              "Materialsertifikat (material-certification-record)"]}})

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
