(ns sequence.engine.machine-test
  (:require
   [sequence.engine.machine :as m :refer [is-ok? rule-parsing validate-rules validate
                                          rule-parsing-default valid-rules]]
   [clojure.test :as t :refer [deftest is]]))


(m/defrules demorules-t
  {:header {::m/not-eventually :header}
   :beta {::m/is-after :header}
   :trailer {::m/is-after :header
             ::m/relax [:header]
             ::m/next :header}})

;; [:header :data :data :trailer] -> pass
  ;; [:header :data :trailer :header :data :trailer ] -> pass
  ;; [:header :data :data :header :trailer ] -> fail
  ;; [:header :data :header] -> fail
  ;; [:trailer :header] -> fail  ('before' criterion)
  ;; 
;;

(deftest rules-valid
  (is (validate-rules demorules-t)))

(deftest rp0
  (is (is-ok? (rule-parsing demorules-t demorules-t :trailer)))
  (is (not (is-ok? (rule-parsing demorules-t demorules-t :header)))))

(deftest t-next
  (is (is-ok? (rule-parsing demorules-t {::m/next :header} :header)))
  (is (not (is-ok? (rule-parsing demorules-t {::m/next :header} :trailer))))
  (is (not (is-ok? (validate demorules-t :id (mapv (fn [x]  {:id x}) [:header :trailer :trailer]))))))

(deftest tree-seqt
  (is (not (is-ok? (reduce (partial rule-parsing demorules-t) {} [:header :header]))))
  (is (not (is-ok? (reduce (partial rule-parsing demorules-t) {} [:header :trailer :trailer]))))
  (is (not (is-ok? (reduce (partial rule-parsing demorules-t) {} [:header :trailer :header :header]))))
  (is (= {::m/next :header} (select-keys (reduce (partial rule-parsing demorules-t) {} [:header :trailer])
                                         [::m/next])))
  (is (not (is-ok? (reduce (partial rule-parsing demorules-t) {} [:trailer])))))

(deftest positional
  (is (= 2 (last (last ((validate demorules-t :id (mapv (fn [x]  {:id x}) [:header :trailer :trailer])) ::m/problem))))))
(deftest regression
  (is (is-ok? (reduce (partial rule-parsing demorules-t) {} [:header :beta :beta :trailer]))))
(deftest regression2
  (is (is-ok? (reduce (partial rule-parsing demorules-t) rule-parsing-default [:header :beta :trailer :header :beta :beta :beta]))))

(deftest badrules
  (let [r
        [{:X {::m/not-eventually :Y}}
         {:X {::m/relax [:Y]}}]]
    (is (not (valid-rules r)))))

(reduce (partial rule-parsing {:header {::m/not-eventually :header
                                        ::m/is-after :b}})
        rule-parsing-default
        [:b :header])
(reduce (partial rule-parsing {:header {::m/not-eventually :header
                                        ::m/is-after :b
                                        }
                               ;:b {::m/free :b}
                               })
        rule-parsing-default
        [:q :header])


(comment
  (t/run-tests)

  (use '[clojure.repl]))