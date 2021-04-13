## The Sequence Is Key

A DSL for a limited subset of Linear Temporal Logic

### Whom this library is for

You have a sequence of data entering from somewhere. But there is something special about this data; 
the ordering of content matters in your application, and there is a certain ordering that either must be preserved, or can never happen. 

In other words, you can say stuff like: "if there is a `:header`, then there MUST be a `:trailer` before we can see a `:header` again." Like this:
```Clojure
(require '[sequence.engine.machine :as m])
(m/defrules my-rules 
  {:header  {::m/not-eventually :header}
   :trailer {::m/relax [:header]}}
```
This can be extended by saying "`:trailer` must follow a `:header`, and immediately after `:trailer` we must see a `:header`":
```Clojure
(m/defrules my-rules 
  {:header  {::m/not-eventually :header}
   :trailer {::m/is-after :header
             ::m/relax [:header]
             ::m/next :header}}
```

This is hard to describe in any programming language, even in math.  I've been inspired mostly by [Linear Temporal Logic](https://en.wikipedia.org/wiki/Linear_temporal_logic).
For integrated circuits there is the [property specification language](https://en.wikipedia.org/wiki/Property_Specification_Language), But I don't find very much about it online.


The basic structure of the data input must be a sequence (`coll`) of data that can be tagged by a tag function.
The tag function is the information that the rule machine works on.

The user must also write the rules that are to be checked. The rules must be a hashmap that provides a hasmap of new rules to apply when the tag is hit

The rule machine does the job of transforming the user-provided rules and the tagged sequence, into a reduction which keeps track of what the legal states are for each successive position in the sequence.

### What this library is not for

You will not use this library for type-checking you data. That is more easily done with `clojure/spec.alpha`, `prismatic/schema`, or `com.taoensso/truss`.

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

## Features

* Automatic rule checking: `defrules` runs a small set of checks to see if rules are feasible

* Rules:
  * `::not-eventually x`: x is no longer a valid sequence item
  * `::is-after y`: y must come before
  * `::relax [x y z]`: the active rules concerning x, y, and z are released.
  * `::next x`: x must be the following element in the collection
  * `::free y`: do nothing operator