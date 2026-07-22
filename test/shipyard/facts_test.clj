(ns shipyard.facts-test
  (:require [clojure.test :refer [deftest is]]
            [shipyard.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:provenance (facts/spec-basis "JPN")))))

(deftest kor-has-a-spec-basis
  (let [basis (facts/spec-basis "KOR")]
    (is (some? basis))
    (is (= "South Korea" (:name basis)))
    (is (string? (:provenance basis)))
    (is (re-find #"Korean Register|한국선급" (:owner-authority basis)))
    (is (re-find #"선박안전법|Ship Safety Act" (:legal-basis basis)))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "GBR" "KOR"])]
    (is (= 3 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["GBR" "JPN" "KOR"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "JPN")]
    (is (facts/required-evidence-satisfied? "JPN" all))
    (is (not (facts/required-evidence-satisfied? "JPN" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))

(deftest kor-required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "KOR")]
    (is (seq all))
    (is (facts/required-evidence-satisfied? "KOR" all))
    (is (not (facts/required-evidence-satisfied? "KOR" (rest all))))))
