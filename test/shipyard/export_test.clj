(ns shipyard.export-test
  "Audit-package export contract -- social/regulatory hand-off shape."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [langgraph.graph :as g]
            [shipyard.export :as export]
            [shipyard.operation :as op]
            [shipyard.store :as store]))

(def operator {:actor-id "op-1" :actor-role :shipyard-engineer :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn- seed-with-one-dispatch []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "v" {:op :class-rules/verify :subject "block-1"})
    (approve! actor "v")
    (exec! actor "d" {:op :actuation/dispatch-block :subject "block-1"})
    (approve! actor "d")
    db))

(deftest audit-package-shape
  (let [db (seed-with-one-dispatch)
        pkg (export/audit-package db)]
    (is (= "3011" (:isic pkg)))
    (is (= "cloud-itonami-isic-3011" (:business-id pkg)))
    (is (= :edn-maps (:format pkg)))
    (is (pos? (get-in pkg [:counts :ledger])))
    (is (= 1 (get-in pkg [:counts :dispatches])))
    (is (some #(= "block-1" (:id %)) (:blocks pkg)))
    (is (true? (:block-dispatched?
                (first (filter #(= "block-1" (:id %)) (:blocks pkg))))))))

(deftest csv-bundle-has-headers-and-rows
  (let [db (seed-with-one-dispatch)
        bundle (export/package->csv-bundle db)]
    (is (every? bundle ["blocks.csv" "ledger.csv" "dispatches.csv" "class-evidence.csv"]))
    (is (str/starts-with? (get bundle "blocks.csv") "id,unit-name,"))
    (is (re-find #"block-1" (get bundle "blocks.csv")))
    (is (re-find #"JPN-BLK-000000" (get bundle "dispatches.csv")))
    (is (re-find #":actuation/dispatch-block" (get bundle "ledger.csv")))))

(deftest empty-store-export-is-usable
  (let [db (store/seed-db)
        pkg (export/audit-package db)
        bundle (export/package->csv-bundle db)]
    (is (= 0 (get-in pkg [:counts :dispatches])))
    (is (= 4 (get-in pkg [:counts :blocks])))
    (is (str/includes? (get bundle "ledger.csv") "seq,t,op"))))
