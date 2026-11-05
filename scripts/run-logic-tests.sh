#!/usr/bin/env bash
# Compiles and runs the plain-Java logic tests in tests/ against the pure-Java
# rule classes in app/src/main/java. Each test class exposes main() and throws
# AssertionError on failure. Usage: scripts/run-logic-tests.sh
set -euo pipefail
cd "$(dirname "$0")/.."
SRC=app/src/main/java/dev/lilt/player
OUT=tests/out
rm -rf "$OUT" && mkdir -p "$OUT"
# Pure-Java sources only: anything importing android.* / androidx.* is excluded.
mapfile -t PURE < <(grep -L -E '(^import (static )?(android|androidx|com\.google)\.)|@androidx\.|extends PlayerWidget' $(find "$SRC" -name '*.java'))
mapfile -t TESTS < <(find tests -name '*Test.java' | sort)
javac -encoding UTF-8 -d "$OUT" "${PURE[@]}" "${TESTS[@]}"
fail=0
for t in "${TESTS[@]}"; do
  cls="$(grep -m1 '^package ' "$t" | sed 's/package \(.*\);/\1/').$(basename "$t" .java)"
  if java -ea -cp "$OUT" "$cls"; then echo "PASS $cls"; else echo "FAIL $cls"; fail=1; fi
done
exit $fail
