(ns shipyard.registry-test
  (:require [clojure.test :refer [deftest is]]
            [shipyard.registry :as r]))

;; ----------------------------- block-tolerance-out-of-range? -----------------------------

(deftest not-out-of-range-when-within-bounds
  (is (not (r/block-tolerance-out-of-range? {:dimensional-tolerance-actual 0.05 :dimensional-tolerance-min -0.10 :dimensional-tolerance-max 0.10})))
  (is (not (r/block-tolerance-out-of-range? {:dimensional-tolerance-actual -0.10 :dimensional-tolerance-min -0.10 :dimensional-tolerance-max 0.10})))
  (is (not (r/block-tolerance-out-of-range? {:dimensional-tolerance-actual 0.10 :dimensional-tolerance-min -0.10 :dimensional-tolerance-max 0.10}))))

(deftest out-of-range-when-below-minimum-or-above-maximum
  (is (r/block-tolerance-out-of-range? {:dimensional-tolerance-actual -0.35 :dimensional-tolerance-min -0.10 :dimensional-tolerance-max 0.10}))
  (is (r/block-tolerance-out-of-range? {:dimensional-tolerance-actual 0.35 :dimensional-tolerance-min -0.10 :dimensional-tolerance-max 0.10})))

(deftest out-of-range-is-false-on-missing-fields
  (is (not (r/block-tolerance-out-of-range? {})))
  (is (not (r/block-tolerance-out-of-range? {:dimensional-tolerance-actual 0.35}))))

;; ----------------------------- register-block-dispatch -----------------------------

(deftest dispatch-is-a-draft-not-a-real-dispatch
  (let [result (r/register-block-dispatch "block-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest dispatch-assigns-dispatch-number
  (let [result (r/register-block-dispatch "block-1" "JPN" 7)]
    (is (= (get result "dispatch_number") "JPN-BLK-000007"))
    (is (= (get-in result ["record" "block_id"]) "block-1"))
    (is (= (get-in result ["record" "kind"]) "block-dispatch-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest dispatch-validation-rules
  (is (thrown? Exception (r/register-block-dispatch "" "JPN" 0)))
  (is (thrown? Exception (r/register-block-dispatch "block-1" "" 0)))
  (is (thrown? Exception (r/register-block-dispatch "block-1" "JPN" -1))))

;; ----------------------------- register-class-evidence -----------------------------

(deftest evidence-is-a-draft-not-real-certification
  (let [result (r/register-class-evidence "block-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest evidence-assigns-evidence-number
  (let [result (r/register-class-evidence "block-1" "JPN" 3)]
    (is (= (get result "evidence_number") "JPN-CLS-000003"))
    (is (= (get-in result ["record" "block_id"]) "block-1"))
    (is (= (get-in result ["record" "kind"]) "class-evidence-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest evidence-validation-rules
  (is (thrown? Exception (r/register-class-evidence "" "JPN" 0)))
  (is (thrown? Exception (r/register-class-evidence "block-1" "" 0)))
  (is (thrown? Exception (r/register-class-evidence "block-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-block-dispatch "block-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-block-dispatch "block-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-BLK-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-BLK-000001" (get-in hist2 [1 "record_id"])))))
