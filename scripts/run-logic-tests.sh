#!/usr/bin/env bash
# Compiles and runs the plain-Java logic tests: every tests/**/*Test.java is a class with main()
# that throws AssertionError on failure (its `package` line decides the class name). App classes a
# test uses are compiled on demand from app/src/main/java. Tests whose code needs android.* /
# org.json / media3 types compile and run against Robolectric android-all (real AOSP code; see
# scripts/README.md) + the media3 stubs. If android-all can't be downloaded those tests are
# reported SKIP (AURA_REQUIRE_ANDROID_TESTS=1, set in CI, turns SKIP into FAIL).
# Usage: scripts/run-logic-tests.sh   (no arguments; exit status non-zero if any test fails)
set -euo pipefail
cd "$(dirname "$0")/.."
# shellcheck source=lib/deps.sh
source scripts/lib/deps.sh
jv() { env -u JAVA_TOOL_OPTIONS "$@"; } # tests need no proxy settings; keeps the log readable

mapfile -t TESTS < <(find tests -name '*Test.java' -not -path 'tests/out/*' | sort)
if [[ ${#TESTS[@]} -eq 0 ]]; then
  echo "no tests found under tests/" >&2
  exit 1
fi

mkdir -p build
WORK="$(mktemp -d build/logic-tests.XXXXXX)" # per run: agents may run this concurrently
trap 'rm -rf "$WORK"' EXIT
OUT="$WORK/classes" GEN="$WORK/gen"
mkdir -p "$OUT" "$GEN"

CP=""
if ANDROID_ALL="$(aura_android_all)" && GUAVA_CP="$(aura_guava_cp)"; then
  WITH_ANN=0
  if aura_declares androidx.annotation:annotation; then WITH_ANN=1; fi
  if STUBS="$(aura_stub_classes "$ANDROID_ALL" "$GUAVA_CP" "$WITH_ANN")"; then
    CP="$ANDROID_ALL:$GUAVA_CP:$STUBS"
  fi
fi
if [[ -z "$CP" ]]; then
  echo "NOTE: android-all unavailable; tests that need android/org.json/media3 classes will SKIP" >&2
fi
if TOOLS="$(aura_harness_tools)"; then
  jv java -cp "$TOOLS" GenR . "$GEN" || echo "NOTE: R generation failed; tests using R won't compile" >&2
fi

JFLAGS=(--release 17 -encoding UTF-8 -proc:none -nowarn -implicit:class
  -sourcepath "app/src/main/java:$GEN" -cp "${CP:-$OUT}" -d "$OUT")
needs_android() {
  grep -Eq 'package (android|androidx|org\.json|com\.google)[A-Za-z0-9_.]* does not exist' "$1"
}

pass=0 fail=0 skip=0
COMPILED=()
if jv javac "${JFLAGS[@]}" "${TESTS[@]}" 2> "$WORK/javac.log"; then
  COMPILED=("${TESTS[@]}")
else
  # Compile each test on its own so one broken test (or app file) doesn't hide the others.
  for t in "${TESTS[@]}"; do
    if jv javac "${JFLAGS[@]}" "$t" 2> "$WORK/one.log"; then
      COMPILED+=("$t")
    elif [[ -z "$CP" ]] && needs_android "$WORK/one.log"; then
      if [[ "${AURA_REQUIRE_ANDROID_TESTS:-0}" == 1 ]]; then
        echo "FAIL $t (needs android-all, which is unavailable)"
        fail=$((fail + 1))
      else
        echo "SKIP $t (needs android-all; offline)"
        skip=$((skip + 1))
      fi
    else
      cat "$WORK/one.log"
      echo "FAIL $t (does not compile)"
      fail=$((fail + 1))
    fi
  done
fi

TIMEOUT=()
if command -v timeout > /dev/null 2>&1; then TIMEOUT=(timeout 300); fi
for t in "${COMPILED[@]}"; do
  cls="$(grep -m1 '^package ' "$t" | sed 's/package \(.*\);/\1/').$(basename "$t" .java)"
  if jv "${TIMEOUT[@]}" java -ea -cp "$OUT${CP:+:$CP}" "$cls"; then
    echo "PASS $cls"
    pass=$((pass + 1))
  else
    echo "FAIL $cls"
    fail=$((fail + 1))
  fi
done
echo "logic tests: $pass passed, $fail failed, $skip skipped"
[[ "$fail" == 0 ]]
