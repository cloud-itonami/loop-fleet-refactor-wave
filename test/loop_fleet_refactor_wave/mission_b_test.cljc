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

(deftest operational-script-signal?-test
  (testing "positive: real 2026-08-29 run_tests.clj shape"
    (is (true? (b/operational-script-signal? "run_tests.clj"
                                              "(apply clojure.test/run-tests n)"))))
  (testing "positive: basename alone is enough"
    (is (true? (b/operational-script-signal? "src/foo/test_runner.clj" nil))))
  (testing "negative: ordinary decision-core file"
    (is (false? (b/operational-script-signal? "src/cloud/itonami/app/store_core.cljc"
                                               "(defn append-message [s m] (update s :messages conj m))")))))

(deftest defn-count-test
  (is (= 0 (b/defn-count nil)))
  (is (= 0 (b/defn-count "(require '[clojure.test :as t])")))
  (is (= 2 (b/defn-count "(defn a [] 1)\n(defn- b [] 2)"))))

(deftest candidate-slice?-test
  (testing "candidate: small, pure, no twin, not custody-gated, has a defn, not a runner"
    (is (true? (b/candidate-slice? {:line-count 121 :custody-gated? false
                                     :has-kotoba-twin? false :host-mechanism? false
                                     :operational-script? false :defn-count 6}))))
  (testing "not a candidate: already has a .kotoba twin"
    (is (false? (b/candidate-slice? {:line-count 121 :custody-gated? false
                                      :has-kotoba-twin? true :host-mechanism? false
                                      :operational-script? false :defn-count 6}))))
  (testing "not a candidate: custody-gated"
    (is (false? (b/candidate-slice? {:line-count 121 :custody-gated? true
                                      :has-kotoba-twin? false :host-mechanism? false
                                      :operational-script? false :defn-count 6}))))
  (testing "not a candidate: shows host-mechanism signal"
    (is (false? (b/candidate-slice? {:line-count 121 :custody-gated? false
                                      :has-kotoba-twin? false :host-mechanism? true
                                      :operational-script? false :defn-count 6}))))
  (testing "not a candidate: too large for a first-wave slice"
    (is (false? (b/candidate-slice? {:line-count 5000 :custody-gated? false
                                      :has-kotoba-twin? false :host-mechanism? false
                                      :operational-script? false :defn-count 6}))))
  (testing "not a candidate: operational script (the 2026-08-29 run_tests.clj regression)"
    (is (false? (b/candidate-slice? {:line-count 4 :custody-gated? false
                                      :has-kotoba-twin? false :host-mechanism? false
                                      :operational-script? true :defn-count 0}))))
  (testing "not a candidate: zero defn (requires/data only)"
    (is (false? (b/candidate-slice? {:line-count 4 :custody-gated? false
                                      :has-kotoba-twin? false :host-mechanism? false
                                      :operational-script? false :defn-count 0})))))
