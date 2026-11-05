#!/usr/bin/env bash
# Type-checks ALL of app/src/main/java/**/*.java with javac, without the Android SDK:
#   android.* / org.json / java.*  -> Robolectric android-all API 35 (Maven Central, cached)
#   androidx.media3.*              -> signature stubs generated from media3 1.5.1 (tools/stubs/media3)
#   androidx.annotation.OptIn      -> stub (annotation-experimental is an api dep of media3)
#   guava                          -> real jar (Maven Central, cached)
#   R / BuildConfig                -> generated from app/src/main/res on every run
# Also runs an UnsafeOptInUsageError check (media3 @UnstableApi needs @OptIn, else CI lint fails).
#
# Usage: scripts/compile-check.sh [--lint] [--no-optin] [-q]
#   --lint      also print javac -Xlint warnings (deprecation, unchecked, ...)
#   --no-optin  skip the @UnstableApi opt-in check
#   -q          print nothing on success
# Exit status: 0 = compiles, 1 = compile/opt-in errors, 2 = usage, 3 = harness setup problem.
# Not covered (CI's Gradle build still decides): Android lint (NewApi etc.), manifest/resource
# linking, and @hide framework APIs (android-all contains them; the real android.jar does not).
set -euo pipefail
cd "$(dirname "$0")/.."
# shellcheck source=lib/deps.sh
source scripts/lib/deps.sh

LINT=0 OPTIN=1 QUIET=0
for a in "$@"; do
  case "$a" in
    --lint) LINT=1 ;;
    --no-optin) OPTIN=0 ;;
    -q | --quiet) QUIET=1 ;;
    -h | --help) sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown option: $a (see --help)" >&2; exit 2 ;;
  esac
done
# javac needs neither the network nor the sandbox's JAVA_TOOL_OPTIONS (which only adds noise).
jv() { env -u JAVA_TOOL_OPTIONS "$@"; }

ANDROID_ALL="$(aura_android_all)" || { echo "compile-check: android-all jar unavailable (offline?)" >&2; exit 3; }
GUAVA_CP="$(aura_guava_cp)" || { echo "compile-check: guava unavailable (offline?)" >&2; exit 3; }
WITH_ANN=0
if aura_declares androidx.annotation:annotation; then WITH_ANN=1; fi
STUBS="$(aura_stub_classes "$ANDROID_ALL" "$GUAVA_CP" "$WITH_ANN")" || exit 3
if grep -Eq "['\"]androidx\.core:" app/build.gradle; then
  echo "compile-check: WARNING app/build.gradle declares androidx.core, which has no stubs here." >&2
fi

TOOLS="$(aura_harness_tools)" || { echo "compile-check: could not build tools/harness" >&2; exit 3; }

# Per-run work dir: other agents may be running this script at the same time.
mkdir -p build/compile-check
WORK="$(mktemp -d build/compile-check/run.XXXXXX)"
trap 'rm -rf "$WORK"' EXIT
GEN="$WORK/gen" OUT="$WORK/classes"
mkdir -p "$GEN" "$OUT"
jv java -cp "$TOOLS" GenR . "$GEN" || { echo "compile-check: resource scan failed (see above)" >&2; exit 1; }

mapfile -t SRCS < <(find app/src/main/java "$GEN" -name '*.java' | sort)
FLAGS=(--release 17 --limit-modules java.base,java.logging,java.prefs,java.sql,java.xml
  -proc:none -encoding UTF-8 -Xmaxerrs 500 -implicit:none
  -cp "$ANDROID_ALL:$GUAVA_CP:$STUBS" -d "$OUT")
if [[ "$LINT" == 1 ]]; then FLAGS+=(-Xlint:all,-serial,-processing,-options); fi
if [[ "$OPTIN" == 1 ]]; then FLAGS+=(-processorpath "$TOOLS" -Xplugin:AuraOptInCheck); fi

if jv javac "${FLAGS[@]}" "${SRCS[@]}"; then
  [[ "$QUIET" == 1 ]] || echo "compile-check: OK (${#SRCS[@]} files)"
  exit 0
fi
echo "compile-check: FAILED" >&2
exit 1
