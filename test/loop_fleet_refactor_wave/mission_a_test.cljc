(ns loop-fleet-refactor-wave.mission-a-test
  (:require [loop-fleet-refactor-wave.mission-a :as a]
            #?(:clj  [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer [deftest is testing] :include-macros true])))

(deftest decentralization-claim?-test
  (testing "positive: explicit words in README text"
    (is (true? (a/decentralization-claim? "This is a decentralized ledger.")))
    (is (true? (a/decentralization-claim? "blockchain settlement layer")))
    (is (true? (a/decentralization-claim? "分散型のノードです"))))
  (testing "negative: ordinary appview README"
    (is (false? (a/decentralization-claim? "A content management appview for cats.")))
    (is (false? (a/decentralization-claim? nil)))))

(deftest d1-binding?-test
  (is (true? (a/d1-binding? "\"d1_databases\": [{\"binding\": \"DB\"}]")))
  (is (false? (a/d1-binding? "\"kv_namespaces\": []")))
  (is (false? (a/d1-binding? nil))))

(deftest cas-arbiter-signal?-test
  (testing "positive: arbiter-shaped SQL/HTTP conditional-write patterns"
    (is (true? (a/cas-arbiter-signal? "UPDATE heads SET sha = ? WHERE sequence = ?")))
    (is (true? (a/cas-arbiter-signal? "onlyIf.etagMatches(etag)")))
    (is (true? (a/cas-arbiter-signal? "headers: {'If-Match': etag}"))))
  (testing "negative: ordinary insert/select"
    (is (false? (a/cas-arbiter-signal? "INSERT INTO sessions (id, data) VALUES (?, ?)")))
    (is (false? (a/cas-arbiter-signal? nil)))))

(deftest mission-a-candidate?-test
  (testing "candidate: all three signals present"
    (is (true? (a/mission-a-candidate?
                {:readme-text "A decentralized ref plane."
                 :wrangler-text "\"d1_databases\": [{\"binding\": \"DB\"}]"
                 :source-text "UPDATE heads SET sha = ? WHERE sequence = ?"}))))
  (testing "not a candidate: D1 present but no decentralization claim (the common case)"
    (is (false? (a/mission-a-candidate?
                 {:readme-text "A cat photo appview."
                  :wrangler-text "\"d1_databases\": [{\"binding\": \"DB\"}]"
                  :source-text "UPDATE heads SET sha = ? WHERE sequence = ?"}))))
  (testing "not a candidate: decentralized claim but no D1 at all"
    (is (false? (a/mission-a-candidate?
                 {:readme-text "A decentralized ref plane."
                  :wrangler-text "\"kv_namespaces\": []"
                  :source-text "UPDATE heads SET sha = ? WHERE sequence = ?"}))))
  (testing "not a candidate: decentralized claim + D1 present but no arbiter signal (ordinary data)"
    (is (false? (a/mission-a-candidate?
                 {:readme-text "A decentralized settlement appview."
                  :wrangler-text "\"d1_databases\": [{\"binding\": \"DB\"}]"
                  :source-text "INSERT INTO sessions (id, data) VALUES (?, ?)"})))))
