# scripts/

Local checks that work without the Android SDK (no Google Maven here). CI
(`.github/workflows/android.yml`) still runs the real Gradle build + lint and has the final say.

## `scripts/compile-check.sh` — type-check the whole app (~10 s)

```sh
scripts/compile-check.sh            # all of app/src/main/java/**/*.java
scripts/compile-check.sh --lint     # + javac -Xlint warnings
scripts/compile-check.sh --no-optin # skip the @UnstableApi opt-in check
```

Exit 0 = compiles. Errors are normal javac errors (`file:line: error: ...`).

What it compiles against:

| Code uses | Comes from |
|---|---|
| `android.*`, `org.json`, `java.*` | Robolectric `android-all` API 35 jar (Maven Central, cached in `tools/cache/`) |
| `androidx.media3.*` | stubs generated from the real media3 **1.5.1** sources (`tools/stubs/media3`) |
| `androidx.annotation.OptIn` | stub (annotation-experimental is an `api` dep of media3) |
| guava (`ListenableFuture`, `Futures`, `ImmutableList`, …) | real guava 33.3.1-android jar |
| `R`, `BuildConfig` | regenerated from `app/src/main/res` on every run (`tools/harness/GenR.java`) |

Rules it enforces that javac alone would not (both fail CI):

- **`[UnsafeOptInUsageError]`**: using anything marked `@UnstableApi` (media3 or our own classes
  annotated with it, e.g. `PlaybackStore`) needs `@OptIn(markerClass = UnstableApi.class)` or
  `@UnstableApi` on the enclosing method/class — Android lint fails `lintDebug` otherwise.
- **`R` ids are not constants** (AGP 8 `nonFinalResIds`): no `switch` on `R.id.*`.
- Only the Android-available JDK modules are visible (`java.net.http`, `java.awt`, … are rejected).

Not on the app's compile classpath in the real build either, so also unavailable here:
`androidx.annotation.Nullable/NonNull/...` and `androidx.core.*` (they are `implementation` deps
of media3). Add them to `app/build.gradle` first if you really need them.

Known gaps (CI catches these): Android lint (`NewApi` for minSdk 26, …), manifest/resource
linking errors, and `@hide` framework APIs (android-all contains them, `android.jar` does not).

Missing media3 class? Add it to `ROOTS` in `tools/stubgen/regen-media3-stubs.py` and run
`python3 -I tools/stubgen/regen-media3-stubs.py` (fetches sources from raw.githubusercontent.com,
regenerates `tools/stubs/media3`, iterates until the stubs compile).

## `scripts/run-logic-tests.sh` — plain-Java logic tests

```sh
scripts/run-logic-tests.sh
```

Runs every `tests/**/*Test.java` (a class with `main()` that throws `AssertionError` on failure;
`package` must match the classes it tests, usually `dev.lilt.player...`). Tests run with the repo
root as working directory, so fixtures are read as `tests/fixtures/...`.

- Tests that only touch pure-Java classes always run.
- Tests whose code needs `android.*`/`org.json`/media3 types compile against the same
  android-all + stub classpath as compile-check. At runtime android-all is the *real* AOSP
  implementation: `org.json`, `android.net.Uri`, `Base64`, `TextUtils`, `Bundle`, `SparseArray`
  work on the JVM; `Log`, `SystemClock`, `Looper`/`Handler`, `Bitmap` (native code) and
  `LruCache` (needs Android's `LinkedHashMap.eldest()`) do not, and media3 stubs throw
  `RuntimeException("stub")` — keep that logic out of what you test.
- App classes are compiled on demand from `app/src/main/java`, so a broken unrelated file only
  breaks the tests that use it.
- Offline without a cached android-all: android-dependent tests are reported `SKIP`
  (`AURA_REQUIRE_ANDROID_TESTS=1` turns that into a failure; CI sets it).
