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

(deftest host-boundary-path?-test
  (testing "positive: real 2026-08-29 miss -- tadori host adapter"
    (is (true? (b/host-boundary-path? "orgs/cloud-itonami/tadori/src/tadori/host/http.clj"))))
  (testing "positive: host as a leading segment"
    (is (true? (b/host-boundary-path? "host/adapter.clj"))))
  (testing "negative: ordinary decision-core path"
    (is (false? (b/host-boundary-path? "orgs/cloud-itonami/hikari/cells/grid_edge/state_machine.cljc")))
    (is (false? (b/host-boundary-path? nil))))
  (testing "negative: 'host' inside a longer word is not a segment"
    (is (false? (b/host-boundary-path? "src/app/hosting_plan.clj")))
    (is (false? (b/host-boundary-path? "src/app/localhost_util.clj")))))

(deftest host-mechanism-signal?-babashka-test
  (testing "the 2026-08-29 miss: babashka.http-client was not in the enumeration"
    (is (true? (b/host-mechanism-signal?
                "(:require [babashka.http-client :as http])"))))
  (testing "other babashka authority namespaces"
    (is (true? (b/host-mechanism-signal? "(babashka.process/shell \"ls\")")))
    (is (true? (b/host-mechanism-signal? "(babashka.fs/exists? p)"))))
  (testing "bare filesystem verbs"
    (is (true? (b/host-mechanism-signal? "(slurp \"x.edn\")")))
    (is (true? (b/host-mechanism-signal? "(spit \"x.edn\" v)"))))
  (testing "negative: still does not fire on pure logic"
    (is (false? (b/host-mechanism-signal?
                 "(defn step [s e] (assoc s :last e))")))))

(deftest unactivated-scaffold?-test
  (testing "positive: the real 2026-08-29 hikari R0 scaffold shape"
    (is (true? (b/unactivated-scaffold?
                (str "(defn solve [_state]\n"
                     "  (throw (ex-info \"hikari R0 scaffold: not activated.\"\n"
                     "                  {:cell :storage-battery :status :r0-scaffold})))")))))
  (testing "positive: docstring between name and arg vector still matches"
    (is (true? (b/unactivated-scaffold?
                "(defn solve \"doc\" [s] (throw (ex-info \"nope\" {})))"))))
  (testing "negative: defensive throw nested in when-not is real logic"
    (is (false? (b/unactivated-scaffold?
                 (str "(defn commit [state]\n"
                      "  (let [cs (grid-state state)]\n"
                      "    (when-not (get cs \"freq_restored\")\n"
                      "      (throw (ex-info \"not restored\" {})))\n"
                      "    {\"cell_state\" cs}))")))))
  (testing "negative: mixed file -- one scaffold defn, one real defn"
    (is (false? (b/unactivated-scaffold?
                 (str "(defn a [s] (throw (ex-info \"x\" {})))\n"
                      "(defn b [s] (assoc s :ok true))")))))
  (testing "negative: no defn at all"
    (is (false? (b/unactivated-scaffold? "(ns x)")))
    (is (false? (b/unactivated-scaffold? nil)))))

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
                                      :operational-script? false :defn-count 0}))))
  (testing "not a candidate: sits on a declared host boundary (tadori regression)"
    (is (false? (b/candidate-slice? {:line-count 7 :custody-gated? false
                                      :has-kotoba-twin? false :host-mechanism? false
                                      :operational-script? false :host-boundary? true
                                      :unactivated-scaffold? false :defn-count 1}))))
  (testing "not a candidate: unactivated R0 scaffold (hikari regression)"
    (is (false? (b/candidate-slice? {:line-count 9 :custody-gated? false
                                      :has-kotoba-twin? false :host-mechanism? false
                                      :operational-script? false :host-boundary? false
                                      :unactivated-scaffold? true :defn-count 1}))))
  (testing "still a candidate when both new gates are clear"
    (is (true? (b/candidate-slice? {:line-count 121 :custody-gated? false
                                     :has-kotoba-twin? false :host-mechanism? false
                                     :operational-script? false :host-boundary? false
                                     :unactivated-scaffold? false :defn-count 6})))))

(def ^:private mio-social
  "Verbatim orgs/cloud-itonami/mio/src/mio/methods/social.cljc, the single
   candidate the 2026-08-30 wave surfaced."
  (str "(ns mio.methods.social\n"
       "  \"mio configuration wrapper around the shared social-publication membrane.\"\n"
       "  (:require [etzhayyim.social.publication :as publication]))\n"
       "\n"
       "(def config {:actor-id \"mio\" :display-name \"mio\"})\n"
       "(def DISCLAIMER (publication/disclaimer config))\n"
       "\n"
       "(defn draft-observation-post\n"
       "  ([subject body sources] (draft-observation-post subject body sources \"\"))\n"
       "  ([subject body sources author]\n"
       "   (publication/draft-observation-post config subject body sources author)))\n"
       "\n"
       "(defn build-live [& args] (apply publication/build-live config args))\n"))

(deftest decision-free-passthrough?-test
  (testing "positive: the real 2026-08-30 candidate -- every defn delegates"
    (is (true? (b/decision-free-passthrough? mio-social))))
  (testing "positive: a 13-line social_post adapter"
    (is (true? (b/decision-free-passthrough?
                (str "(ns amime.cells.social-post.state-machine\n"
                     "  (:require [amime.methods.social :as social]\n"
                     "            [etzhayyim.social.publication :as publication]))\n"
                     "\n"
                     "(def phase-init publication/phase-init)\n"
                     "\n"
                     "(defn transition-to-drafted [state]\n"
                     "  (publication/transition-to-drafted social/config state))\n")))))
  (testing "negative: delegates AND decides -- a branch is a decision"
    (is (false? (b/decision-free-passthrough?
                 (str "(ns a.b (:require [x.y :as y]))\n"
                      "\n"
                      "(defn f [s]\n"
                      "  (if (:ready? s) (y/go s) (y/wait s)))\n")))))
  (testing "negative: delegates AND decides -- a comparison is a decision"
    (is (false? (b/decision-free-passthrough?
                 (str "(ns a.b (:require [x.y :as y]))\n"
                      "\n"
                      "(defn f [s n]\n"
                      "  (y/emit s (> n 3)))\n")))))
  (testing "negative: real decision core with no delegation at all"
    (is (false? (b/decision-free-passthrough?
                 (str "(ns a.b)\n"
                      "\n"
                      "(defn solve [state]\n"
                      "  (cond (:done? state) :complete\n"
                      "        :else :pending))\n")))))
  (testing "negative: no defn at all is not this class -- defn-count rejects it"
    (is (false? (b/decision-free-passthrough?
                 "(ns a.b (:require [x.y :as y]))\n\n(def z y/w)\n"))))
  (testing "the ns form's own :require aliases must not count as calls"
    (is (false? (b/decision-free-passthrough? "(ns a.b (:require [x.y :as y]))\n\n"))))
  (testing "nil / empty"
    (is (false? (b/decision-free-passthrough? nil)))
    (is (false? (b/decision-free-passthrough? "")))))

(deftest candidate-slice?-rejects-passthrough-test
  (testing "a file that passes every other predicate is still rejected"
    (is (false? (b/candidate-slice? {:line-count 13
                                     :custody-gated? false
                                     :has-kotoba-twin? false
                                     :host-mechanism? false
                                     :operational-script? false
                                     :host-boundary? false
                                     :unactivated-scaffold? false
                                     :decision-free-passthrough? true
                                     :defn-count 2}))))
  (testing "and accepted once it is not a pass-through"
    (is (true? (b/candidate-slice? {:line-count 13
                                    :custody-gated? false
                                    :has-kotoba-twin? false
                                    :host-mechanism? false
                                    :operational-script? false
                                    :host-boundary? false
                                    :unactivated-scaffold? false
                                    :decision-free-passthrough? false
                                    :defn-count 2})))))
