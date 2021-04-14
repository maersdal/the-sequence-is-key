(ns sequence.engine.machine
  (:require
   [clojure.string :as str]
   [clojure.spec.alpha :as s]
   [clojure.set :as set]
   [clojure.pprint :as pprint]))
;; 2021 Magnus Rentsch Ersdal
(def ^:dynamic debugprint false)
(def valid-rules 
  "temporality: 
   ::not-eventually - post
   ::next - post
   ::relax - post
   ::is-after - immediate
   "
  #{::not-eventually ::is-after ::relax ::next ::free})

(defn get-subjects [rules]
  (->> (vals rules)
       (map vals)
       (flatten)
       (set)))

(defn subjects-in-rules?
  "each subject (kw in rule) must exist somewhere in the rules
   is really not good enough. 
   working example:
   {:header {::not-eventually :header
             ::is-after :b}}
   fails this test...
   what really is the issue is ::relax
   "
  [rules]
  (every? (set (keys rules)) (get-subjects rules)))

(defn relax-is-on-defined-rules?
  "::relax must be applied to defined rules to make any sense."
  [rules]
  (let [rlx-triggers (->> (vals rules)
                          (map #(get % ::relax))
                          (filter (complement nil?)))]
    (if (not-empty rlx-triggers)
      (every? (set (keys rules)) (set (flatten rlx-triggers)))
      true)))

(s/def ::rule.entry (s/or :a keyword? :b (s/+ keyword?)))
(s/def ::rule valid-rules)
(s/def ::rules (s/map-of keyword? (s/map-of ::rule ::rule.entry)))
(s/def ::rule-consistent relax-is-on-defined-rules?)

(defn validate-rules [rules]
  (let [r (s/valid? ::rules rules)
        c (s/valid? ::rule-consistent rules)]
    (and r c)))

(defn explain-rules [rules]
  (let [notok #(if (str/includes? % "Success!") "" %)]
    (str (notok (s/explain-str ::rules rules))
         (notok (s/explain-str ::rule-consistent rules)))))

(defn get-trigd-by 
  "get whatever ::rule triggers in rules"
  [rules rule]
  (->> (vals rules)
       (map #(select-keys % [rule]))
       (remove empty?)
       (map vals)
       flatten))

(defn not-eventually-not-released-warnings 
  "warning: not eventually should be released by something?"
  [rules]
  (let [not-eventually-triggers (set (get-trigd-by rules ::not-eventually))
        relax-targets (set (get-trigd-by rules ::relax))
        m-s (set/difference not-eventually-triggers relax-targets)
        _ (println not-eventually-triggers
                   relax-targets)]
    (if-not (empty? m-s)
      (let [n (count m-s)
            plural? (> n 1)
            noun (fn [singular plural] (if plural? singular plural))
            repr (if plural? (vec m-s) (first m-s))]
        (str "Warning:"
             " the " (noun "targets" "target") " " repr " "
             (noun "are" "is") " never ::relax 'ed, thus " repr
             " can never appear more than once in your sequence"
             " please make sure that this is your intended behavior"))
      nil)))

(defn explain-warnings [rules]
  (remove nil? ((juxt
                 not-eventually-not-released-warnings)
                rules)))



(defmacro defrules
  [name rulemap]
  (let [e (validate-rules rulemap)
        errstr (explain-rules rulemap)
        warnings (explain-warnings rulemap)]
    (if e
      (do
        (when (not-empty warnings)
          (pprint/pprint warnings))
        `(def ~name ~rulemap))
      (throw (ex-info (str "bad definition of rules, problem:\n" errstr) {})))))

;; data -> get rule from data
;; every data -> check rule -> possibly do action of rule 
;; (hit :eventually removes the rule), (hit ::not-eventually assigns error)
;; every data point is a key, which might, or might not be in the rules.
;; hitting a rule CHANGES how the next data is expected to behave...
;; hitting :header makes two targets :trailer and :header, with the corresponding rules: :eventually and ::not-eventually
;; if end is reached and there is still any eventually in effect, we have a failure...
;; if :header is reached and the rule is in effect, then we have a failure..
;; 
;; so.. [:header :trailer] would give
;;  0 [] -> []
;;  1 :header -> {:header {::not-eventually :header}}
;;                
;;  2 :trailer -> :trailer {::is-after :header 
;;                          ::relax [:header] 
;;                          ::next :header}}
;;                           
;;  ::is-after looks if :header is in the rules,
;;  ::relax removes the :header kvp,
;;  ::next :header is moved to ::next kw
;;  3 [] -> {::next :header}
;;  
(defn conj-with-vec [x item]
  (if (nil? x)
    [item]
    (conj x item)))
(defn inc-or-zero [x]
  (if (nil? x)
    0
    (inc x)))


(defn check-cond 
  "all conditions in here."
  [rules item]
  {:pre [(keyword? item)]}
  (let [clauses (rules item)
        pos (rules ::position)
        clause-reducer (fn [arules [rule value]]
                         (case rule
                           ::not-eventually (if (= value item)
                                              (update arules ::problem conj-with-vec {:rule [::not-eventually value]
                                                                             :broken-by item
                                                                             ::position pos})
                                              arules)
                           ::is-after (if-not (contains? rules value)
                                        (update arules ::problem conj-with-vec {:rule [::is-after value]
                                                                       :broken-by item
                                                                       ::position pos})
                                        arules)
                           arules))
        rules (let [r (get rules ::next :no-next-clause)]
                (condp = r
                  item rules
                  :no-next-clause rules
                  (update rules
                          ::problem
                          conj-with-vec
                          {:rule [::next item]
                           :broken-by item
                           ::position pos})))]
    (reduce clause-reducer
            rules clauses)))

(defn update-cond-pre
  ""
  [all-rules rules item]
  (let [clauses (all-rules item)]
    (reduce (fn [arules clause]
              (let [[rule value] clause]
                (case rule
                  ::is-after (update arules item conj clause)
                  arules))) rules clauses)))


(defn update-cond-post [all-rules rules item]
  {:pre [(keyword? item)]}
  (let [rules (-> (dissoc rules ::next)
                  (update ::position inc-or-zero)) ; ::next is always relaxed.
        clauses (all-rules item)
        newrules (assoc rules item clauses)]
    (reduce (fn [arules [rule value]]
              (case rule
                ::next (assoc arules ::next value)
                ::relax (apply dissoc arules value)
                arules))
            newrules clauses)))


(defn rule-parser
  "construct reducer for given rules
   three logical things: 
   ADD pre-conditions, the new rules created by hitting the item, which must be checked against in the current step
   CHECK conditions, all checks.
   ADD post-conditions, which will affect next item
   "
  [all-rules]
  (fn [active-rules item]
    (when debugprint
      (println active-rules item))
    (let [
          pre-rules (update-cond-pre all-rules active-rules item)
          checked-rules (check-cond pre-rules item)
          post-rules (update-cond-post all-rules checked-rules item)
          ]
      post-rules)
    ))

(def rule-parsing-default {::position 0})

(defn reduce-over-rules [rules items]
  (reduce (rule-parser rules) {::position 0} items))

(defn is-ok? [x]
  (not (or (contains? x ::problem) (nil? x))))

(defn validate
  ""
  [properties]
  {:pre [(every? properties [:rules :tag-fn :user-sequence])
         (keyword? ((:tag-fn properties) (first (:user-sequence properties))))]}
  (let [{:keys [rules tag-fn user-sequence]} properties
        seq-xf (comp
                (filter (complement nil?))
                (map tag-fn))
        seq-ed (eduction seq-xf user-sequence)
        red-rules (reduce-over-rules rules seq-ed)]
    (if (is-ok? red-rules)
      {:ok true}
      {::problem (red-rules ::problem)})))

(comment
  (defrules mdmd {:header {::not-eventually :header}
                  :beta {::is-after :header}
                  :trailer {::is-after :header
                            ::relax [:beta]
                            ::next :header}
                  :q {::relax [:q]}})

  (binding [debugprint true]
    ;; has no ::relax, fails on second header
    (validate {:rules {:header {::not-eventually :header}, :beta {::is-after :header}}
               :tag-fn identity
               :user-sequence [:header :trailer :header]}))

  (let [allrules {:header {::not-eventually :header}, :beta {::is-after :header}, :trailer {::is-after :header, ::relax [:header], ::next :header}}
        activerules {:header {::not-eventually :header}, :beta {::is-after :header}}
        k :beta]
    (println "red:" (reduce (rule-parser allrules) activerules [:beta]))
    (println "rp:" ((rule-parser allrules) activerules k))
    (println "rr:" (reduce (rule-parser allrules) {::is-after :header} [:beta])))
  (def demorules
    {:header {::not-eventually :header}
     :beta {::is-after :header}
     :trailer {::is-after :header
               ::relax [:header]
               ::next :header}})

  (validate-rules demorules)

  (reduce (rule-parser demorules) {} [:header :header])
  (reduce (rule-parser demorules) {} [:header :trailer])
  (reduce (rule-parser demorules) {} [:header :beta :beta :trailer])
  (reduce (rule-parser demorules) {} [:header :trailer :header])
  (reduce (rule-parser demorules) {} [:trailer])

  (binding [debugprint true]
    (reduce (rule-parser demorules) {} [:header :trailer :header]))


  (s/explain ::rule-consistent {:X {::not-eventually :Y}}))