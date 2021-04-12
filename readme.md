## The Sequence Is Key



### Usage

```Clojure
;; valid rules are: #{::not-eventually ::is-after ::relax ::next ::free}
(require '[sequence.engine.machine :as m])
(m/defrules demorules-t
  {:header {::m/not-eventually :header}
   :beta {::m/is-after :header}
   :trailer {::m/is-after :header
             ::m/relax [:header]
             ::m/next :header}})

(def my-data
[:header :beta :trailer :header])

(m/is-ok? (reduce (partial m/rule-parsing demorules-t)
        m/rule-parsing-default
        my-data))
;=> true
(m/is-ok? (reduce (partial m/rule-parsing demorules-t)
        m/rule-parsing-default
        [:header :beta :header]))
;=> false

```