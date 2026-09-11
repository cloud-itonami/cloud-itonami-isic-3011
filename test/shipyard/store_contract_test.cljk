(ns shipyard.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the sibling
  actor."
  (:require [clojure.test :refer [deftest is testing]]
            [shipyard.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sakura Double-Bottom Block DB-04" (:unit-name (store/block s "block-1"))))
      (is (= "JPN" (:jurisdiction (store/block s "block-1"))))
      (is (= 0.05 (:dimensional-tolerance-actual (store/block s "block-1"))))
      (is (= -0.10 (:dimensional-tolerance-min (store/block s "block-1"))))
      (is (= 0.10 (:dimensional-tolerance-max (store/block s "block-1"))))
      (is (false? (:ndt-defect-unresolved? (store/block s "block-1"))))
      (is (= 0.35 (:dimensional-tolerance-actual (store/block s "block-3"))))
      (is (true? (:ndt-defect-unresolved? (store/block s "block-4"))))
      (is (false? (:block-dispatched? (store/block s "block-1"))))
      (is (false? (:class-certified? (store/block s "block-1"))))
      (is (= ["block-1" "block-2" "block-3" "block-4"]
             (mapv :id (store/all-blocks s))))
      (is (nil? (store/ndt-screen-of s "block-1")))
      (is (nil? (store/requirements-verification-of s "block-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/dispatch-history s)))
      (is (= [] (store/evidence-history s)))
      (is (zero? (store/next-dispatch-sequence s "JPN")))
      (is (zero? (store/next-evidence-sequence s "JPN")))
      (is (false? (store/block-already-dispatched? s "block-1")))
      (is (false? (store/block-already-certified? s "block-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :block/upsert
                                 :value {:id "block-1" :unit-name "Sakura Double-Bottom Block DB-04"}})
        (is (= "Sakura Double-Bottom Block DB-04" (:unit-name (store/block s "block-1"))))
        (is (= 0.05 (:dimensional-tolerance-actual (store/block s "block-1"))) "unrelated field preserved"))
      (testing "verification / NDT-screen payloads commit and read back"
        (store/commit-record! s {:effect :verification/set :path ["block-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/requirements-verification-of s "block-1")))
        (store/commit-record! s {:effect :ndt-screen/set :path ["block-1"]
                                 :payload {:block-id "block-1" :verdict :resolved}})
        (is (= {:block-id "block-1" :verdict :resolved} (store/ndt-screen-of s "block-1"))))
      (testing "block dispatch drafts a record and advances the sequence"
        (store/commit-record! s {:effect :block/mark-dispatched :path ["block-1"]})
        (is (= "JPN-BLK-000000" (get (first (store/dispatch-history s)) "record_id")))
        (is (= "block-dispatch-draft" (get (first (store/dispatch-history s)) "kind")))
        (is (true? (:block-dispatched? (store/block s "block-1"))))
        (is (= 1 (count (store/dispatch-history s))))
        (is (= 1 (store/next-dispatch-sequence s "JPN")))
        (is (true? (store/block-already-dispatched? s "block-1")))
        (is (false? (store/block-already-dispatched? s "block-2"))))
      (testing "class evidence drafts a record and advances the sequence"
        (store/commit-record! s {:effect :block/mark-certified :path ["block-1"]})
        (is (= "JPN-CLS-000000" (get (first (store/evidence-history s)) "record_id")))
        (is (= "class-evidence-draft" (get (first (store/evidence-history s)) "kind")))
        (is (true? (:class-certified? (store/block s "block-1"))))
        (is (= 1 (count (store/evidence-history s))))
        (is (= 1 (store/next-evidence-sequence s "JPN")))
        (is (true? (store/block-already-certified? s "block-1")))
        (is (false? (store/block-already-certified? s "block-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/block s "nope")))
    (is (= [] (store/all-blocks s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/dispatch-history s)))
    (is (= [] (store/evidence-history s)))
    (is (zero? (store/next-dispatch-sequence s "JPN")))
    (is (zero? (store/next-evidence-sequence s "JPN")))
    (store/with-blocks s {"x" {:id "x" :unit-name "n" :dimensional-tolerance-actual 0.05
                                   :dimensional-tolerance-min -0.10 :dimensional-tolerance-max 0.10
                                   :ndt-defect-unresolved? false
                                   :block-dispatched? false :class-certified? false
                                   :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:unit-name (store/block s "x"))))))
