# AURA 2.0 — session handoff

Written for a fresh Claude Code session picking this up. Branch: `claude/affectionate-lamport-wu2n64`
(repo `nitish496/AURA`). Read this file, then `docs/DESIGN.md` and `docs/ARCHITECTURE.md`.

## What the owner wants
Turn the old offline Android player into a music app good enough to replace Apple Music / Spotify /
YouTube Music: a streaming catalogue (no downloads needed) alongside the on-device library, Apple
Music-grade features, and a premium Apple-HIG / **Liquid Glass** design. They want it fast, with
real screenshots and a downloadable APK at the end. Name is kept as **AURA** (new "Refraction" mark).

**Owner's hard design rules (non-negotiable):**
- NO purple/violet/indigo/magenta anywhere. No "AI slop": no gradient blobs, glows, gradient text,
  sparkles, emoji, generic marketing copy ("Discover your sound"), SaaS hero banners, feature grids,
  card-in-card, shadows on everything, cliché logos.
- Liquid Glass ONLY on the floating nav/control layer (capsule tab bar + separate glass search circle,
  glass mini player, top-bar buttons, menus, sheets). Content is never glass.
- Colour comes from artwork; one non-purple accent (Ember `#FF6A3D`) for state only; Inter fonts.
- Now Playing has **no volume slider**; its layout adapts to any phone height (spec in DESIGN.md).
- The owner liked the prototype Home frame (`design/screens/home.png`): 34sp "Home" title with round
  glass account button, "Top Picks for You" mix cards, "Recently Played" shelf, floating glass tab bar
  with mini player above it. The real app should match it.
- Streaming source is **Audius** (free, legal) + **LRCLIB** lyrics. Do NOT scrape YouTube/Spotify/Apple.

## State of the code (all pushed)
Done and compiling (`scripts/compile-check.sh` clean, `scripts/run-logic-tests.sh` 41/41 green):
- **Streaming** `stream/*`: Audius client with host failover, saved-to-library catalogue, lyrics
  (embedded ID3/FLAC first, then LRCLIB), connectivity. Offline-tested with fixtures.
- **Audio engine** `PlaybackService` + `audio/*`: cached streaming (SimpleCache ~1 GB), EQ presets,
  loudness normalisation, crossfade, sleep timer, listening stats, queue persistence incl. remote tracks.
  Command/Bundle API in `docs/ARCHITECTURE.md`.
- **Design system** `ui/*`, `DesignTokens`, 66 vector icons, Inter fonts, logo/launcher icon, widgets
  restyled, `design/prototype.html` + `design/screens/*.png`, `docs/DESIGN.md`.
- **Shell** `app/*` + `app/chrome/*`: `Shell` (AppHost + Navigator), `PlayerHubImpl`, `LibraryRepoImpl`,
  menus, floating chrome (TabBar, MiniPlayer, SearchPill, TopBar), `DebugHooks` (screenshot intents),
  thin `MainActivity` (64 lines).
- **Screens done:** Songs, Album, Artist, Playlist (on `screens/library/ListScreen` + `Kit`), Browse,
  Search, Genre, CatalogPlaylist, CatalogArtist, NowPlaying, Lyrics, Queue, Sound, Replay, Settings.

## What is NOT done (start here)
1. **`screens/HomeScreen.java` and `screens/LibraryScreen.java` are still 17-line PLACEHOLDERS.**
   The frontend agent that owned them was stopped mid-work. Build them on `ListScreen` (rows may be
   arbitrary Views) using `Kit` (largeTitle, section, albumShelf, pills), `ui.FeatureCard`/`AlbumCard`/
   `ShelfView`/`SectionHeader`/`ListRow`, `app/chrome/MixArt` (mix card artwork) and `app/MixRules`
   (heavyRotation, rediscover, spread, daySeed, summary). Home per the prototype frame; honour
   `SettingsScreen.showCatalogue(ctx)` for the Audius "Trending" shelf. Library = Apple-Music-style
   collection list (Playlists, Artists, Albums, Songs, Saved, Favourites, Most Played, Recently Added)
   plus a Recently Added album grid. Also uncommitted: small edits to ArtistScreen/PlaylistScreen.
2. **Nothing has run on a device yet.** After step 1: push, let CI build, then review emulator
   screenshots against `design/screens/*.png` and fix layout/glass/perf issues.
3. `Menus`/`Shell` untested at runtime: present-after-dismiss, search tab field, IME insets, theme switch.
4. Known gaps: Lyrics/Queue/Settings have no drag-to-dismiss; artist-by-id lookup doesn't exist in
   Audius (artist photos come from name search); no "library shortcuts" in empty Search state.

## Environment facts (sandbox)
- No Android SDK and Google Maven is blocked; Audius/LRCLIB hosts are blocked from the sandbox.
- Local checks: `scripts/compile-check.sh` (javac vs Robolectric android-all + generated Media3 stubs;
  also catches `@UnstableApi` without `@OptIn`) and `scripts/run-logic-tests.sh`. `androidx.annotation.
  Nullable` and `androidx.core` are NOT on the app's compile classpath.
- **Real verification is GitHub Actions** (`.github/workflows/android.yml`), runs on every push:
  `build` (logic tests, `assembleDebug`, `lintDebug`, uploads APK) → `release` (publishes the APK as
  pre-release tag **`aura-2.0-preview`**, asset `AURA-2.0-preview.apk`) and `screenshots` (API 35
  emulator, generated demo library from `scripts/ci/demo_media.py`, opens each screen via debug intent
  extras listed in `scripts/ci/screens.txt`, commits JPEGs to `docs/screenshots/` with a bot commit —
  `git fetch` + merge before pushing). Add `[no screenshots]` to a commit message to skip the emulator
  job; `[video]` records a scroll clip. Lint failures so far were real source issues (literal BOMs).
- CI artifacts can't be downloaded from the sandbox (blob host blocked); read logs through the GitHub
  MCP tools (`actions_list`, `get_job_logs`) and screenshots from the `docs/screenshots/` commit.
- Version is 2.0.0 (code 32), debug-signed. A differently-signed older AURA must be uninstalled first.
- Git: develop on this branch only, no PR unless asked; end commits with the attribution lines the
  harness gives. A stop hook asks to commit and push whenever the tree is dirty.

## Deliverables owed to the owner
- A **download link** for the APK: the `aura-2.0-preview` GitHub pre-release once CI is green.
- **Phone screenshots** of the finished app running (from `docs/screenshots/`), then they will ask
  for changes. Keep replies short and honest; say what is verified vs. unverified.
