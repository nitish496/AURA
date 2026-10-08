# Development timeline

Historical milestones are documentation, not preserved source snapshots. Only the current1.21 source is available for this initial repository.

# AURA release history

##1.19
AURA1.19 /2026-10-07: Three artwork decoders with bounded per-source striped locks; unrelated URI requests no longer serialize globally. Independent bounded optional disk writer and atomic PNG saves,256MiB disk cache, corrected custom-art provenance. Upcoming actual next queue song (including shuffle) prefetches small and full-player sizes once peridentity. Startup logo/progress screen covers firstscan and1–1.8sec initial warm only; reduced duplicate label/progress/bitmap tint writes and event-gated playback snapshot/widget work. Build/lint/signature and211 host+45 structural checks passed. Final API35 stationarySearch test-dawn displayed customblue actual cover without scrolling; tests/search-stationary-1.19.png. No before/after latency measurement yet. Active Recents attempt did not establish removal, not a passing dismissal check. Android16 image installer still running withoutcompletion; Android16/physicalRedmi checks pending. AURA-debug.apk delivered current source; no publication.

##1.18
AURA1.18 /2026-10-07: Artists now uses recycled grouped ListView rather than eager rows/cover requests. Home Favourites is a complete220dp sideways shelf directly after the header Recently played block (no four-song limit/settings navigation). Search category pills removed; all metadata search retained. Shared rounded Home-style mini-player/progress across navigation. Supplied Downloads/stitch_premium_music_player_ui/screen.png copied byte-for-byte into launcher artwork; launcher system mask remains Android-controlled. Skip overhead reduced by avoiding widget artwork extraction when no widget is installed; decoded covers are dispatched before PNG persistence/cache maintenance. Build/lint/signature passed;45 structural checks, prior211 logic checks unchanged. Emulator Home and Library Artists→collection and Search cleanup checked; no crash-buffer entries, final Search mini screenshot inspected. Original phone crash not reproduced (fixtures have one artist); no guarantee all crashes/Redmi latency resolved, no physical phone test. No publication.

## 1.17
AURA 1.17: native Search reference implemented with offline filters, counts, clear action and per-result artwork. Size-aware shared thumbnail/large caches, sampled decode, disk-prune throttling and MediaStore dirty refresh replace periodic rescans; list positions preserved. Corrected crossfade preparation timeout for already-ready incoming songs and bounded/coalesced notification artwork loading.
Validation: Android build/lint/signature passed; 211 host checks +45 structural checks passed. API35 emulator: Search query test-dawn returned one actual fixture; repeated warmed scroll/resume retained extraction counter17 while memory/view cache hits increased. Screenshot tests/aura-search-1.17.png. This pass does not establish audible transition smoothness, active Recents dismissal, random-stop resolution or OEM notification appearance on Redmi Note13 Pro+ Android16. Original AURA dial logo file still pending. No publication.

## 1.16
Always-visible6dp rounded playback bar, no dot/thumb;10dp duringdrag with48dp toucharea. Removedplayerarrow/header, keptcentredlayout andAndroidBacknavigation.

## 1.15
Renamed to AURA, with original scalable gold A launcher monogram. More compact Home artwork, headings, pills and spacing. Content capped/centred720dp, window-based sizing, measured square Favourites, adaptive album columns and narrow player. Saved data/package retained.

## 1.14
User-supplied HTML Home adapted natively: warm charcoal/coral palette, layered resume card with live progress, real library shortcuts, recent-song rows and Clear, compact mixed artwork shelves, two-column favourites preview with View all, mini progress line. Actual metadata replaces example content; no synthetic storage/FLAC claims. Play restarts an ended item.

## 1.13
Source-preserving shuffle; staged crossfade waits for incoming playback instead of cancelling asynchronous start. Stop playback service on Recents removal. Bounded decoder I/O retry and private diagnostics. Clean known source suffixes from display titles. Opening cover warm-up and128MiB private disk cache. Lighter transport controls and cover shadow. Embedded/custom notification art supplied to Android; Dolby settings shortcut with truthful fallback. Phone playback stability and audible smoothness require retest.

## 1.12.1
Favourites show the entire cover in a square area within the tall card; title and artist sit below on an artwork-coloured footer. Removed portrait cropping.

## 1.12
Centred full player with shared artwork/metadata/timeline edges, balanced header, lighter actions and Up next. Slim draggable seek bar with growing thumb, live preview, elapsed and remaining labels.

## 1.11
Full player background uses a stronger readable colour sampled from real artwork. No real artwork means black. Compact music widget shows the real cover, metadata and playback controls; default Artwork theme follows song changes, with black fallback. Existing manual widget themes retained.

## 1.10
Home cards no longer display hearts. Favourites use taller artwork cards with readable title overlays; standard shelves have uniform square covers and separate gaps. Sideways positions survive vertical recycling. Shared custom cover/source caches and missing-art caching reduce repeated extraction. Final verification recorded in tests/latest-verification.json.

## 1.9
Mixed-album Home shelves, Favourites replacing Recently added, heart controls, extended artwork tinting, full-library queues for ordinary library playback and full-library Shuffle action.

## 1.8.2
Crossfade source-end race fix. Emulator playback settings, decoded two-tone overlap and actual queue-end autoplay checked. Capture harness removed from delivery; physical-output volume envelope/phone regression remain separate.

## 1.8.1
Star songs from rows, player or song menu; local Favourites collection. Improved ranked offline search with Unicode handling, cautious typo tolerance and debounced background scoring.

## 1.8
Artwork-matched muted Now Playing background. Integrated adjustable crossfades, related local autoplay and widget review fixes; build/host checks available, audible/launcher regression pending. Playback settings in player overflow.

## 1.7.1
Generic Download/Downloads and missing-album tracks appear together as Other songs. Real album tags remain grouped regardless of artist/year. No files moved/deleted.

## 1.7
Larger launcher logo. Two resizable home-screen player widgets with independent Dark/Light/Gold themes, playback controls and settings. Launcher runtime validation pending.

## 1.6.3
Radhimma (also Radimma spelling) pinned at top of Home when present in local library.

## 1.6.2
Same-name movie/album tags group across singers and provider album IDs in Home, Albums and playback. Case/spacing normalized; language editions and unknown albums preserved. Old custom cover keys remain readable.

## 1.6.1
User-supplied gold Geetly logo installed as adaptive launcher icon, with proportional inset and dark background. Crossfades/autoplay remain pending separately.

## 1.6 — Geetly
App renamed to Geetly with the existing package and debug signing retained for compatible upgrades. Explicit non-debuggable release configuration; private environment/key ignore rules; all20 user-supplied security checks reviewed in SHIP_CHECKLIST.md. Logo pending.

## 1.5
Artwork-led Home with recently added albums, real playback history and horizontal shelves covering every local song. Non-square artwork uses a blurred image fill without stretching or black bars. Sleep timer accepts any whole minute from 0 to 60, with 0 cancelling.

## 1.4
Modern minimalist layout, queue bottom sheet, compact transports and library shortcuts, background/coalesced persistence, cached higher-resolution covers and reduced redraws. Build/signature/46 Java tests pass; focused emulator testing with303 tracks and rectangular artwork completed.

## 1.3
Dark neutral interface, circular play controls, proportional covers, cover-art queue, sideways shelves, local mood mixes, native spatial-audio status/settings and service-owned sleep timer.

## 1.2
Recycled library lists, improved cover decoding, album identities and queue/position restoration. User reports playback, shuffle, repeat and Up next working on their phone.

## 1.1
Collection screens, navigation restoration, seek and queue ordering.

## 1.0
Initial native offline library and playback.

##1.20
Dial-only logo; artwork preload for Next and Previous.

##1.21
Larger resume card, theme-coloured current titles, Home tagline, logo-only loading and retained library on rotation.
