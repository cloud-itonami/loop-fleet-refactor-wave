(ns loop-fleet-refactor-wave.mission-b-test
  (:require [loop-fleet-refactor-wave.mission-b :as b]
            #?(:clj  [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer [deftest is testing] :include-macros true])))

(deftest custody-gated?-test
  (testing "positive: greps migration.edn or svelte/ in a verify script"
    (is (true? (b/custody-gated? "(slurp \"migration.edn\")")))
    (is (true? (b/custody-gated? "find . -path '*/svelte/*'"))))
  (testing "negative: unrelated script"
    (is (false? (b/custody-gated? "(println \"hello\")")))
    (is (false? (b/custody-gated? nil)))))

(deftest kotoba-twin-exists?-test
  (testing "positive: stem matches an existing .kotoba path"
    (is (true? (b/kotoba-twin-exists? "src/cloud/itonami/app/fleet_core.cljc"
                                       ["src/cloud/itonami/app/fleet_core.kotoba"]))))
  (testing "negative: no matching stem"
    (is (false? (b/kotoba-twin-exists? "src/cloud/itonami/app/store_core.cljc"
                                        ["src/cloud/itonami/app/fleet_core.kotoba"]))))
  (testing "negative: empty kotoba path list"
    (is (false? (b/kotoba-twin-exists? "src/cloud/itonami/app/store_core.cljc" [])))))

(deftest host-mechanism-signal?-test
  (testing "positive: I/O / socket / credential surface signals"
    (is (true? (b/host-mechanism-signal? "(require '[clj-http.client :as http])")))
    (is (true? (b/host-mechanism-signal? "(.spawnSync cp \"git\" args)")))
    (is (true? (b/host-mechanism-signal? "(System/getenv \"SECRET\")"))))
  (testing "negative: pure state-transition logic"
    (is (false? (b/host-mechanism-signal? "(defn append-message [state msg] (update state :messages conj msg))")))
    (is (false? (b/host-mechanism-signal? nil)))))

(deftest candidate-slice?-test
  (testing "candidate: small, pure, no twin, not custody-gated"
    (is (true? (b/candidate-slice? {:line-count 121 :custody-gated? false
                                     :has-kotoba-twin? false :host-mechanism? false}))))
  (testing "not a candidate: already has a .kotoba twin"
    (is (false? (b/candidate-slice? {:line-count 121 :custody-gated? false
                                      :has-kotoba-twin? true :host-mechanism? false}))))
  (testing "not a candidate: custody-gated"
    (is (false? (b/candidate-slice? {:line-count 121 :custody-gated? true
                                      :has-kotoba-twin? false :host-mechanism? false}))))
  (testing "not a candidate: shows host-mechanism signal"
    (is (false? (b/candidate-slice? {:line-count 121 :custody-gated? false
                                      :has-kotoba-twin? false :host-mechanism? true}))))
  (testing "not a candidate: too large for a first-wave slice"
    (is (false? (b/candidate-slice? {:line-count 5000 :custody-gated? false
                                      :has-kotoba-twin? false :host-mechanism? false})))))
