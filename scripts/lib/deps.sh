# Shared helpers for scripts/compile-check.sh and scripts/run-logic-tests.sh (source, don't run).
# Downloads pinned jars from Maven Central into tools/cache/ (gitignored) and verifies SHA-1.

AURA_ROOT="${AURA_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
AURA_CACHE="$AURA_ROOT/tools/cache"
AURA_MAVEN="${AURA_MAVEN:-https://repo1.maven.org/maven2}"

# group/artifact/version path, jar name, sha1
AURA_ANDROID_ALL=("org/robolectric/android-all/15-robolectric-12650502" "android-all-15-robolectric-12650502.jar" "d58d66c41a3ca3acd2043811cea1910bb2c4149a")
AURA_GUAVA=("com/google/guava/guava/33.3.1-android" "guava-33.3.1-android.jar" "36162b3b4e9df67dae90db62a2c54616ca42c1bd")
AURA_FAILUREACCESS=("com/google/guava/failureaccess/1.0.2" "failureaccess-1.0.2.jar" "c4a06a64e650562f30b7bf9aaec1bfed43aca12b")
# Only so javac can read guava's @ReflectionSupport(Level.FULL) without a spurious warning.
AURA_J2OBJC=("com/google/j2objc/j2objc-annotations/3.0.0" "j2objc-annotations-3.0.0.jar" "7399e65dd7e9ff3404f4535b2f017093bdb134c7")

# Runs "$@" holding an exclusive lock (several agents/CI steps may share the caches).
aura_locked() {
  local lock="$1"
  shift
  mkdir -p "$(dirname "$lock")"
  if command -v flock > /dev/null 2>&1; then
    (
      flock 9
      "$@"
    ) 9> "$lock"
  else
    "$@"
  fi
}

# aura_fetch <path> <jar> <sha1>  -> prints the local jar path; returns 1 if unavailable.
aura_fetch() { aura_locked "$AURA_CACHE/.lock" _aura_fetch "$@"; }
_aura_fetch() {
  local path="$1" jar="$2" sha="$3" dest="$AURA_CACHE/$2"
  mkdir -p "$AURA_CACHE"
  if [[ -f "$dest" ]]; then
    printf '%s\n' "$dest"
    return 0
  fi
  if [[ "${AURA_OFFLINE:-0}" == 1 ]]; then return 1; fi
  echo "Downloading $jar (one-time, cached in tools/cache/) ..." >&2
  if ! curl -fsSL --retry 6 --retry-delay 5 --retry-all-errors -m 900 -o "$dest.part" "$AURA_MAVEN/$path/$jar"; then
    rm -f "$dest.part"
    echo "WARN: could not download $jar from $AURA_MAVEN" >&2
    return 1
  fi
  local got
  got="$(sha1sum "$dest.part" | cut -d' ' -f1)"
  if [[ "$got" != "$sha" ]]; then
    rm -f "$dest.part"
    echo "ERROR: $jar checksum mismatch (got $got, want $sha)" >&2
    return 1
  fi
  mv "$dest.part" "$dest"
  printf '%s\n' "$dest"
}

aura_android_all() { aura_fetch "${AURA_ANDROID_ALL[@]}"; }

# Classpath with guava (ListenableFuture, ImmutableList, ...) for code that uses media3 types.
aura_guava_cp() {
  local g f j
  g="$(aura_fetch "${AURA_GUAVA[@]}")" || return 1
  f="$(aura_fetch "${AURA_FAILUREACCESS[@]}")" || return 1
  j="$(aura_fetch "${AURA_J2OBJC[@]}")" || return 1
  printf '%s:%s:%s\n' "$g" "$f" "$j"
}

# Content hash of a list of files (order-independent), for cache keys.
aura_hash_files() {
  sort | xargs -r sha1sum | sha1sum | cut -c1-16
}

# Builds (or reuses) the compiled media3 stubs for the app classpath.
# Placeholder libraries (androidx.core, ...) are compiled alongside but removed from the output:
# they are `implementation` deps of media3, so the real app cannot see them either.
# Prints the classes directory.
aura_stub_classes() { aura_locked "$AURA_ROOT/build/compile-check/.lock" _aura_stub_classes "$@"; }
_aura_stub_classes() {
  local android_all="$1" guava_cp="$2" with_annotation="${3:-0}"
  local stubs="$AURA_ROOT/tools/stubs"
  local dirs=("$stubs/media3" "$stubs/annotation-experimental" "$stubs/placeholders")
  if [[ "$with_annotation" == 1 ]]; then dirs+=("$stubs/androidx-annotation"); fi
  local key
  key="$( { find "${dirs[@]}" -name '*.java' 2>/dev/null | sort | xargs -r sha1sum; echo "annotation=$with_annotation"; } | sha1sum | cut -c1-16)"
  local out="$AURA_ROOT/build/compile-check/stubs-$key"
  if [[ -f "$out/.ok" ]]; then
    printf '%s\n' "$out"
    return 0
  fi
  rm -rf "$AURA_ROOT"/build/compile-check/stubs-*
  mkdir -p "$out"
  find "${dirs[@]}" -name '*.java' 2>/dev/null > "$out.sources"
  echo "Compiling media3 stubs (once per stub change) ..." >&2
  if ! env -u JAVA_TOOL_OPTIONS javac --release 17 -proc:none -nowarn -encoding UTF-8 -Xmaxerrs 1000 \
      -cp "$android_all:$guava_cp" -d "$out" "@$out.sources" > "$out.log" 2>&1; then
    cat "$out.log" >&2
    echo "ERROR: media3 stubs failed to compile (tools/stubs is broken)" >&2
    return 1
  fi
  rm -f "$out.log"
  # Drop placeholder classes from what the app compiles against.
  local p rel
  while IFS= read -r p; do
    rel="${p#"$stubs/placeholders/"}"
    rel="${rel%.java}"
    rm -f "$out/$rel.class" "$out/$rel"\$*.class
  done < <(find "$stubs/placeholders" -name '*.java')
  rm -f "$out.sources"
  touch "$out/.ok"
  printf '%s\n' "$out"
}

# Does app/build.gradle declare a dependency (group:artifact)?
aura_declares() {
  grep -Eq "['\"]$1:" "$AURA_ROOT/app/build.gradle"
}

# Compiles tools/harness (GenR + the opt-in javac plugin) once per change; prints the directory.
aura_harness_tools() { aura_locked "$AURA_ROOT/build/compile-check/.lock" _aura_harness_tools; }
_aura_harness_tools() {
  local key out
  key="$(find "$AURA_ROOT/tools/harness" -name '*.java' | aura_hash_files)"
  out="$AURA_ROOT/build/compile-check/tools-$key"
  if [[ ! -f "$out/.ok" ]]; then
    rm -rf "$AURA_ROOT"/build/compile-check/tools-*
    mkdir -p "$out/META-INF/services"
    env -u JAVA_TOOL_OPTIONS javac --release 17 -nowarn -d "$out" "$AURA_ROOT"/tools/harness/*.java >&2 || return 1
    echo OptInCheck > "$out/META-INF/services/com.sun.source.util.Plugin"
    touch "$out/.ok"
  fi
  printf '%s\n' "$out"
}
