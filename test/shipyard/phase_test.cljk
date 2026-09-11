(ns shipyard.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:actuation/dispatch-block`/`:actuation/issue-
  class-evidence` must NEVER be a member of any phase's
  `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [shipyard.phase :as phase]))

(deftest dispatch-assembly-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real robot block dispatch"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/dispatch-block))
          (str "phase " n " must not auto-commit :actuation/dispatch-block")))))

(deftest issue-class-evidence-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits real class evidence"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/issue-class-evidence))
          (str "phase " n " must not auto-commit :actuation/issue-class-evidence")))))

(deftest ndt-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :ndt/screen))
          (str "phase " n " must not auto-commit :ndt/screen")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":block/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:block/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :block/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/dispatch-block} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/issue-class-evidence} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :block/intake} :commit)))))
