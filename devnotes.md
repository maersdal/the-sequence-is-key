```
idioms checker:
clojure -M:project/kibit
check for unused vars
clojure -M:project/unused --opts '{:dry-run true :paths ["test" "src"]}'

```