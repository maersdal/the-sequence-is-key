```

idioms checker:
clojure -M:project/kibit
clojure -M:lint/eastwood
check for unused vars
clojure -M:project/unused --opts '{:dry-run true :paths ["test" "src"]}'
show tag sha:
git rev-list -n 1 0.0.1

```