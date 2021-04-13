(ns sequence.engine.machine
  (:require 
   [clojure.string :as str]
   [clojure.spec.alpha :as s]))
;; 2021 Magnus Rentsch Ersdal
(def ^:dynamic debugprint false)
(def valid-rules #{::not-eventually ::is-after ::relax ::next ::free})
(defn get-rule-entries [rules]
  (set (keys rules)))

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
  (every? (get-rule-entries rules) (get-subjects rules)))

(defn relax-is-on-defined-rules? 
  "::relax must be applied to defined rules to make any sense."
  [rules]
  (let [rlx-triggers (->> (vals rules)
                          (map #(get % ::relax))
                          (filter (complement nil?)))]
    (if (not-empty rlx-triggers)
      (every? (get-rule-entries rules) (set (flatten rlx-triggers)))
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

(defmacro defrules
  [name rulemap]
  (let [e (validate-rules rulemap)
        errstr (explain-rules rulemap)]
    (if e
      `(def ~name ~rulemap)
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

(defn rule-reduce
  ([active-rules clause k]
   (rule-reduce active-rules clause k false))
  ([active-rules clause k immediate]
   (reduce (fn [arules kvp]
             (let [[rule value] kvp
                  ;_ (println arules kvp)
                   ]
               (case rule
                 ::not-eventually (if (and (= value k) (not immediate))
                                    (update arules ::problem conj {:rule [::not-eventually value]
                                                                   :broken-by k
                                                                   ::position (active-rules ::position)})
                                    arules)
                 ::is-after (if-not (contains? active-rules value)
                              (update arules ::problem conj {:rule [::is-after value]
                                                             :broken-by k
                                                             ::position (active-rules ::position)})
                              arules)
                 ::relax (apply dissoc arules value)
                 ::next (assoc arules ::next value)
                 ::free arules
                 :default (update arules ::problem conj
                                  {:rule [::not-implemented rule value]
                                   ::position (active-rules ::position)}))))
           active-rules clause)))

(defn rule-reduce-immediate [rules clause k]
  (rule-reduce rules clause k true))

(defn inc-or-zero [x]
  (if (not (nil? x))
    (inc x)
    0))

(def rule-parsing-default {::position 0})

(defn rule-parsing [all-rules active-rules k]
  (when debugprint
    (println active-rules k))
  ;; next is priority rule!
  (if-let [target (active-rules ::next)]
    (if-not (= k (active-rules ::next))
      (update active-rules ::problem conj {:rule [::next target]  
                                           :broken-by k 
                                           ::position (active-rules ::position)})
      (rule-parsing all-rules (dissoc active-rules ::next) k))
    ;;
    (if (contains? active-rules k)
      (let [clause (active-rules k)]
        (-> (rule-reduce active-rules clause k)
            (update ::position inc-or-zero)))
      (let [clause (all-rules k)]
        (-> (rule-reduce-immediate active-rules clause k)
            (assoc k clause)
            (update ::position inc-or-zero))))))

(defn is-ok? [x]
  (if-not (or (contains? x ::problem) (nil? x))
    true
    false))

(defn validate
  "accept rules and data which should be a collection containing 
   maps with the ident key for the names of things which are in the rules"
  [rules ident data]
  (reduce
   (partial rule-parsing rules) rule-parsing-default
   (filter (complement nil?) (map #(get % ident) data))))


(defn validate-2
  [properties]
  {:pre [(every? properties [:rules :tag-fn :user-sequence])
         (keyword? ((:tag-fn properties) (first (:user-sequence properties))))]}
  (let [{:keys [rules tag-fn user-sequence]} properties
        seq-xf (comp
                (filter (complement nil?))
                (map tag-fn))
        seq-ed (eduction seq-xf user-sequence)
        red-rules (reduce
                   (partial rule-parsing rules) rule-parsing-default
                   seq-ed)]
    (if (is-ok? red-rules)
      {:ok true}
      {::problem (red-rules ::problem)})))


(comment
  (validate-2 {:rules {:header {::not-eventually :header}, :beta {::is-after :header}}
               :tag-fn (fn [_] 1)
               :user-sequence [:header :trailer :header]})

  (let [seq-xf (comp
                (filter (complement nil?))
                (map #(:fun %)))]
    (transduce seq-xf conj [{:fun 1} {:a 2}])
    )
  (let [allrules {:header {::not-eventually :header}, :beta {::is-after :header}, :trailer {::is-after :header, ::relax [:header], ::next :header}}
        activerules {:header {::not-eventually :header}, :beta {::is-after :header}}
        k :beta]
    (println "red:" (reduce (partial rule-parsing allrules) activerules [:beta]))
    (println "rp:" (rule-parsing allrules activerules k))
    (println "rr:" (rule-reduce activerules {::is-after :header} :beta)))
  (def demorules
    {:header {::not-eventually :header}
     :beta {::is-after :header}
     :trailer {::is-after :header
               ::relax [:header]
               ::next :header}})

  (validate-rules demorules)


  (rule-parsing demorules
                {:trailer {::is-after :header
                           ::relax [:header]
                           ::next :header}}
                :header)
  (rule-parsing demorules
                {:trailer
                 {::is-after :header, ::relax [:header], ::next :header}
                 :header {::not-eventually :header}}
                :header)
  (reduce (partial rule-parsing demorules) {} [:header :header])
  (reduce (partial rule-parsing demorules) {} [:header :trailer])
  (reduce (partial rule-parsing demorules) {} [:header :beta :beta :trailer])
  (reduce (partial rule-parsing demorules) {} [:header :trailer :header])
  (reduce (partial rule-parsing demorules) {} [:trailer])

  (binding [debugprint true]
    (reduce (partial rule-parsing demorules) {} [:header :trailer :header]))


  (s/explain ::rule-consistent {:X {::not-eventually :Y}})
  (get-subjects demorules)
  (get-rule-entries demorules))