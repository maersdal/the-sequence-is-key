(ns sequence.engine.machine-test
  (:require
   [sequence.engine.machine :as m :refer [is-ok? rule-parser validate-rules validate
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
  (is (is-ok? ((rule-parser demorules-t) demorules-t :trailer)))
  (is (not (is-ok? ((rule-parser demorules-t) demorules-t :header)))))

(deftest t-next
  (is (is-ok? ((rule-parser demorules-t) {::m/next :header} :header)))
  (is (not (is-ok? ((rule-parser demorules-t) {::m/next :header} :trailer))))
  (is (not (is-ok? (validate {:rules demorules-t
                              :tag-fn identity
                              :user-sequence [:header :trailer :trailer]})))))

(deftest tree-seqt
  (is (not (is-ok? (reduce (rule-parser demorules-t) {} [:header :header]))))
  (is (not (is-ok? (reduce (rule-parser demorules-t) {} [:header :trailer :trailer]))))
  (is (not (is-ok? (reduce (rule-parser demorules-t) {} [:header :trailer :header :header]))))
  (is (= {::m/next :header} (select-keys (reduce (rule-parser demorules-t) {} [:header :trailer])
                                         [::m/next])))
  (is (not (is-ok? (reduce (rule-parser demorules-t) {} [:trailer])))))

(deftest tree-seqt2
  (is (not (is-ok? (reduce (m/rule-parser demorules-t) {} [:header :header]))))
  (is (not (is-ok? (reduce (m/rule-parser demorules-t) {} [:header :trailer :trailer]))))
  (is (not (is-ok? (reduce (m/rule-parser demorules-t) {} [:header :trailer :header :header]))))
  (is (= {::m/next :header} (select-keys (reduce (m/rule-parser demorules-t) {} [:header :trailer])
                                         [::m/next])))
  (is (not (is-ok? (reduce (rule-parser demorules-t) {} [:trailer])))))

(deftest positional
  (is (= 2 (::m/position (last (::m/problem
                                (validate {:rules demorules-t
                                           :tag-fn identity
                                           :user-sequence [:header :trailer :trailer]})))))))
(deftest regression
  (is (is-ok? (reduce (rule-parser demorules-t) {} [:header :beta :beta :trailer]))))
(deftest regression2
  (is (is-ok? (validate {:rules demorules-t
                         :tag-fn identity
                         :user-sequence [:header :beta :trailer :header :beta :beta :beta]}))))

(deftest regression3
  (is (is-ok? (validate {:rules demorules-t
                         :tag-fn identity
                         :user-sequence [:header :trailer :header]}))))

(deftest badrules
  (let [r
        [{:X {::m/not-eventually :Y}}
         {:X {::m/relax [:Y]}}]]
    (is (not (valid-rules r)))))

(reduce (rule-parser {:header {::m/not-eventually :header
                                        ::m/is-after :b}})
        rule-parsing-default
        [:b :header])
(reduce (rule-parser {:header {::m/not-eventually :header
                                        ::m/is-after :b
                                        }
                               ;:b {::m/free :b}
                               })
        rule-parsing-default
        [:q :header])


(comment
  (t/run-tests)
  (t/test-vars [#'sequence.engine.machine-test/tree-seqt])
  (binding [m/debugprint true]
    (t/test-vars [#'sequence.engine.machine-test/positional]))
  (t/test-vars [#'sequence.engine.machine-test/tree-seqt2])
  (t/test-vars [#'sequence.engine.machine-test/tree-seqt])

  (use '[clojure.repl])
  (require '[clojure.pprint :refer [pprint]])
  (pprint {:sequence.engine.machine/position 2, 
           :sequence.engine.machine/problem 
           ({:rule [:sequence.engine.machine/next :trailer], 
             :sequence.engine.machine/position 1} 
            {:rule [:sequence.engine.machine/next :header], 
             :sequence.engine.machine/position 0}),
           :sequence.engine.machine/next :header, 
           :trailer 
           #:sequence.engine.machine{:is-after :header, :relax [:header], :next :header}})
  )