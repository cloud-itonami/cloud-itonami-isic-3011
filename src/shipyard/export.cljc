(ns shipyard.export
  "Audit-package export for social / regulatory hand-off.

  Produces plain EDN maps and CSV strings over a `shipyard.store/Store`
  snapshot -- the same append-only ledger, block-dispatch drafts and
  class-evidence drafts the governor writes. Pure data transforms only:
  no I/O, no network, no signature. The yard's own act is to sign and
  file the package; this namespace only materializes the package body.

  This is the honest delivery of the industry-stack `:export?` contract
  (robotics / audit-ledger capabilities) for ISIC 3011."
  (:require [clojure.string :as str]
            [shipyard.store :as store]))

(defn- csv-escape [v]
  (let [s (str (if (nil? v) "" v))]
    (if (re-find #"[,\"\n\r]" s)
      (str "\"" (str/replace s "\"" "\"\"") "\"")
      s)))

(defn- csv-row [cols]
  (str/join "," (map csv-escape cols)))

(defn ledger-rows
  "Normalize ledger facts into flat row maps suitable for CSV."
  [st]
  (mapv (fn [i f]
          {:seq i
           :t (:t f)
           :op (str (:op f))
           :actor (:actor f)
           :subject (:subject f)
           :disposition (str (:disposition f))
           :basis (pr-str (:basis f))
           :summary (:summary f)})
        (range)
        (store/ledger st)))

(defn dispatch-rows [st]
  (mapv (fn [i r]
          {:seq i
           :record_id (get r "record_id")
           :kind (get r "kind")
           :block_id (get r "block_id")
           :jurisdiction (get r "jurisdiction")})
        (range)
        (store/dispatch-history st)))

(defn evidence-rows [st]
  (mapv (fn [i r]
          {:seq i
           :record_id (get r "record_id")
           :kind (get r "kind")
           :block_id (get r "block_id")
           :jurisdiction (get r "jurisdiction")})
        (range)
        (store/evidence-history st)))

(defn blocks-snapshot [st]
  (mapv (fn [b]
          (select-keys b [:id :unit-name :jurisdiction :status
                          :dimensional-tolerance-actual
                          :dimensional-tolerance-min
                          :dimensional-tolerance-max
                          :ndt-defect-unresolved?
                          :block-dispatched?
                          :class-certified?
                          :dispatch-number
                          :evidence-number]))
        (store/all-blocks st)))

(defn audit-package
  "Full audit package for a store snapshot -- the body a shipyard would
  hand to class surveyors, flag-state inspectors or internal compliance.
  `:format` is always `:edn-maps` for the nested package; use
  `package->csv-bundle` for CSV strings."
  [st]
  {:isic "3011"
   :business-id "cloud-itonami-isic-3011"
   :format :edn-maps
   :blocks (blocks-snapshot st)
   :ledger (vec (store/ledger st))
   :dispatches (vec (store/dispatch-history st))
   :class-evidence (vec (store/evidence-history st))
   :counts {:blocks (count (store/all-blocks st))
            :ledger (count (store/ledger st))
            :dispatches (count (store/dispatch-history st))
            :class-evidence (count (store/evidence-history st))}})

(defn rows->csv
  "Render a seq of flat maps as CSV using `header` column order."
  [header rows]
  (let [lines (into [(csv-row (map name header))]
                    (map (fn [r] (csv-row (map #(get r %) header))) rows))]
    (str (str/join "\n" lines) (when (seq lines) "\n"))))

(defn package->csv-bundle
  "CSV bundle for spreadsheet hand-off. Keys are filenames; values are
  CSV body strings."
  [st]
  {"blocks.csv" (rows->csv [:id :unit-name :jurisdiction :status
                            :dimensional-tolerance-actual
                            :block-dispatched? :class-certified?
                            :dispatch-number :evidence-number]
                           (blocks-snapshot st))
   "ledger.csv" (rows->csv [:seq :t :op :actor :subject :disposition :basis :summary]
                           (ledger-rows st))
   "dispatches.csv" (rows->csv [:seq :record_id :kind :block_id :jurisdiction]
                               (dispatch-rows st))
   "class-evidence.csv" (rows->csv [:seq :record_id :kind :block_id :jurisdiction]
                                   (evidence-rows st))})

#?(:clj
(defn write-csv-bundle!
  "Write `package->csv-bundle` files under `dir` (created if missing).
  Returns the absolute path of `dir`. JVM-only I/O seam for social
  hand-off scripts; pure package construction stays in `package->csv-bundle`."
  [st dir]
  (let [d (java.io.File. (str dir))
        _ (.mkdirs d)
        bundle (package->csv-bundle st)]
    (doseq [[name body] bundle]
      (spit (java.io.File. d (str name)) body))
    (.getAbsolutePath d))))
