# AURA 2.0 design specification

The visual language for AURA 2.0, and the contract between design and the screens. Every value here exists in code
(`dev.lilt.player.DesignTokens`, `ui.*`) and in the prototype (`design/prototype.html`, screenshots in
`design/screens/`). If a screen needs something that is not here, add it to the kit first.

Regenerate generated assets with `node design/src/build.mjs` (icons, logo, launcher icon, tokens.json, prototype) and
`node design/src/render.mjs final` (screenshots, `docs/aura-logo.png`).

## 1. Principles

1. **Content first, glass on top.** Content (artwork, rows, text) scrolls full-bleed. The floating navigation and
   control layer (tab bar, mini player, top bar, circular buttons, menus, sheets, player controls) is *Liquid Glass*.
   Glass is never used for content: no glass cards, rows or tiles.
2. **Colour comes from artwork.** The UI is neutral graphite (`#0B0B0C` canvas) and white. Now Playing, the mini
   player tint, feature-card footers and the widget backdrop take their colour from the playing artwork. There is no
   decorative gradient anywhere. Missing artwork is a graphite tile with initials.
3. **One accent, used for state.** *Ember* (`#FF6A3D` dark / `#C23A12` light) marks the selected tab, the playing
   row, toggles that are on, the primary action label and the heart. It is never a background wash, never a gradient.
4. **Editorial, left-aligned hierarchy.** Large titles and section headers are left aligned. Rows are dense (60dp);
   whitespace goes between sections (28dp), not inside rows.
5. **A strict system.** 4dp grid, 11 type styles, 6 radii, 3 durations, one icon family. No one-off sizes.
6. **Honest copy.** Plain labels ("Recently Played", "Top Picks for You"). No marketing lines, no exclamation marks,
   no emoji. Errors say what happened and what still works.

**Banned:** purple / violet / indigo / magenta anywhere; purple→pink or blue→purple gradients; gradient text; neon
glows; aurora blobs; emoji in the UI; hero banners with CTAs; three-column feature grids; centred-everything
layouts; card-in-card nesting; shadows on every container; pill badges everywhere; a letter "A" in a gradient shape;
equaliser-bar, headphone, music-note or play-triangle logos. `DesignTokensTest` fails the build if a colour role is
purple.

## 2. Brand

**Name: AURA** (kept). It is short, sayable, already the app's identity and install base, and nothing stronger and
original came up. The mark makes the name about *light bending through glass*, which is the interface material,
not about decorative glow.

**Mark, "Refraction".** A disc cut by a plane of glass: the upper half sits in air, the lower half is seen through
the glass and appears displaced to the right. Two semicircles, radius 18.5, gap 2.6, offset 5.6 on a 48-unit grid.
Monochrome first; the mark is white on graphite in the launcher and the app, and follows the text colour elsewhere.

**Wordmark.** "AURA", Inter Display SemiBold, +6% tracking, outlined (`design/logo.svg`, `res/drawable/aura_wordmark.xml`).

| Asset | File |
|---|---|
| Logo + wordmark (SVG) | `design/logo.svg` |
| Mark (SVG) | `design/logo-mark.svg` |
| App icon 1024 (SVG) / README PNG | `design/app-icon.svg`, `docs/aura-logo.png` |
| Adaptive launcher icon | `res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` (background `@color/ic_launcher_background`, foreground `ic_launcher_foreground`, **monochrome** `ic_launcher_monochrome` for Android 13 themed icons) |
| Startup / about logo | `R.drawable.aura_logo` (108dp vector; use at 72–108dp) |
| Wordmark drawable | `R.drawable.aura_wordmark` |
| Notification small icon | `R.drawable.ic_notification` |
| In-icon-set glyph | `R.drawable.ic_aura` (24dp grid) |

Minimum size 16dp. Clear space around the mark: half its height. Never recolour with Ember, never put it on artwork
without a graphite plate.

## 3. Tokens

### Colour (`ui.ColorRoles`, `Theme.colors(ctx)`)

| Role | Dark | Light | Use |
|---|---|---|---|
| canvas | `#0B0B0C` | `#FFFFFF` | window background |
| surface | `#161618` | `#F4F4F6` | grouped sections, notices |
| surfaceElevated | `#1E1E21` | `#FFFFFF` | lifted rows, glass-less sheets |
| fill | `#262629` | `#EDEDF0` | secondary buttons, chips, search field |
| fillStrong | `#313135` | `#E2E2E6` | pressed, selected segment, slider track |
| separator | white 10% | black 10% | row hairlines (0.5–1dp) |
| textPrimary | `#F5F5F7` | `#0B0B0C` | titles, body |
| textSecondary | `#A1A1A8` | `#545459` | subtitles, metadata |
| textTertiary | `#85858C` | `#6E6E74` | counts, durations. **Never on `fill`** |
| accent (Ember) | `#FF6A3D` | `#C23A12` | state only |
| onAccent | `#0B0B0C` | `#FFFFFF` | label on an Ember fill |
| error | `#FF6961` | `#C4261D` | destructive / failed |
| glassTint | `#1C1C1E` 60% | white 65% | glass over blur (API 31+) |
| glassTintFallback | `#1E1E21` 92% | `#FAFAFC` 94% | glass without blur |

Contrast (tested): every text role is ≥ 4.5:1 on canvas, surface and surfaceElevated; primary and secondary are also
≥ 4.5:1 on `fill`; primary is ≥ 7:1 on canvas. On artwork tints text is `ColorRoles.ON_ART_*` and
`ArtworkPalette` guarantees 7:1 (primary) and 4.5:1 (secondary) on every colour of the mesh. Now Playing, Lyrics and
Queue are artwork-tinted dark in both themes. Dark is the default; light follows the system (or Settings).

### Type (`ui.TypeScale`, apply with `ui.Type`)

Fonts: **Inter** (Regular, Medium, SemiBold, Bold) and **Inter Display Bold**, bundled OFL fonts in `res/font`
(Latin subsets, ~1.1 MB total in the APK; licence in `docs/licenses/Inter-OFL-1.1.txt`). Inter Display is used at
22sp and above. Tabular figures (`Type.tabular`) for every time, count and rank. Use the real minus U+2212 in
remaining time.

| Style | sp / line | Weight | Tracking | Use |
|---|---|---|---|---|
| LARGE_TITLE | 34 / 40 | Display Bold | −1.2% | screen titles (Home, Browse, Library) |
| TITLE1 | 28 / 34 | Display Bold | −1.0% | album / playlist title |
| TITLE2 | 22 / 28 | Display Bold | −0.8% | section headers, empty-state title |
| TITLE3 | 20 / 24 | SemiBold | −1.6% | now-playing title, search top hit |
| HEADLINE | 17 / 22 | SemiBold | −1.3% | sheet titles, feature-card title |
| BODY | 17 / 22 | Regular | −1.3% | long text |
| CALLOUT | 16 / 21 | Regular | −1.1% | settings rows |
| SUBHEAD | 15 / 20 | Regular | −0.9% | descriptions, empty-state body |
| FOOTNOTE | 13 / 18 | Regular | −0.3% | row subtitles, metadata |
| CAPTION1 / 2 | 12 / 16, 11 / 13 | Medium | 0 / +0.5% | scrubber times, badges |
| ROW_TITLE | 16 / 21 | Medium | −1.1% | song row title |
| CARD_TITLE | 15 / 20 | Medium | −0.9% | card title |
| BUTTON | 16 / 20 | SemiBold | −1.0% | pill buttons |
| CHIP | 14 / 18 | Medium | −0.6% | chips |
| TAB | 10 / 12 | Medium | +1.0% | tab labels |
| LYRIC | 30 / 37 | Display Bold | −1.2% | lyrics lines |
| STAT | 64 / 68 | Display Bold | −2.0% | Replay total |
| OVERLINE | 13 / 18 | Regular | +6% | UPPERCASE group headers |

### Spacing, radii, sizes (`DesignTokens`)

- Grid 4dp (2dp for hairline gaps). Screen margin **16**, shelf/grid gutter **12**, section gap **28**, header→content **12**,
  player margin **24**.
- Radii: XS 4 (badge) · S 6 (row art) · M 10 (card art) · L 14 (feature card, groups, notices) · XL 22 (menus) ·
  SHEET 32 · capsule = half height. Artwork radius by size: ≤48 → 6, ≤96 → 8, ≤200 → 10, larger → 12
  (`DesignTokens.artworkRadius`). Inner radius = outer − inset (`DesignTokens.concentric`).
- Sizes: touch 48 · icon 24 (small 20, inline 14) · song row 60 (art 44) · track row 52 · list row 52 / 64 ·
  card 152 · feature card 272 (+84 footer) · artist circle 112 · genre tile 96 tall · chip 32 (in 48) · button 48 ·
  segmented 36 (in 48) · tab bar 64 · search button 64 · mini player 56 · top bar 52 · glass button 40 / 36.
- Shadows: glass 12dp (soft, wide, 35% black), Now Playing artwork 16dp, lifted row 8dp. Nothing else casts a shadow.

### Motion (`ui.Motion`)

150 / 250 / 350 ms (sheet 300). Standard curve `(0.2, 0, 0, 1)`; arrivals decelerate, exits accelerate (150 ms).
Chrome (tab bar minimise, toggles, segmented thumb, press release) uses the spring: response 0.32 s, damping 0.86,
≈ 360 ms, overshoot < 12%. Large moves (player expand, sheets) use the gentle spring (≈ 450 ms). Press feedback on
cards is a 97% scale. Everything jumps to its end state when animations are disabled (`Motion.enabled()`).
The Now Playing mesh drifts on a 24 s loop while playing; cross-fades between palettes in 600 ms.

### Haptics (`ui.Haptics`)

`tick` (scrubber ends, chips, segments, snap steps) · `click` (play/pause, skip) · `longPress` (context menu opens) ·
`confirm` (saved, queued, timer set) · `reject` (offline, unavailable) · `toggle` · `dragStart` (queue row lifts) ·
`gestureEnd` (sheet / player settles). One effect per event; never on scroll.

## 4. Liquid Glass (`ui.GlassView`, `GlassDrawable`, `WindowBlur`)

Layers: **backdrop blur → tint → specular rim → soft shadow**.

- **Blur (API 31+)**: before each frame the *backdrop view* (a sibling layer behind the chrome) is recorded into a
  private `RenderNode`, offset to the glass position and padded by the blur radius, with a chained
  `RenderEffect` (colour-matrix saturation 1.8 + blur 24dp), drawn clipped to the glass shape. The glass is never
  part of its own backdrop (`GlassView.capturing`, and blur is disabled if the backdrop contains the glass).
  Nothing is allocated per frame; recording references existing display lists and runs only while content redraws.
- **Tint**: `glassTint` (dark `#1C1C1E` 60%, light white 65%). On artwork use `setOnArtwork(true)` (white 14%).
- **Rim**: 1px stroke, light from the top: white 35% → 5% (light theme 90% → 30%).
- **Shadow**: outline-provider elevation 12dp, coloured black 35% (light 12%).
- **Fallback (API 26–30, battery saver, `Theme.setReduceTransparency`)**: same shape, rim and shadow with
  `glassTintFallback` (92–94% opaque), no blur. Text contrast is tested against the fallback tint.
- **Separate windows** (system dialogs): `WindowBlur.apply(dialog, roles, radiusPx)` uses `FLAG_BLUR_BEHIND` +
  `setBackgroundBlurRadius` when `isCrossWindowBlurEnabled()`, else a dim. In-window sheets use `GlassView`.

Layering rule for every screen root:

```java
FrameLayout root = new FrameLayout(ctx);
FrameLayout content = new FrameLayout(ctx);               // scrolling screens: the glass backdrop
root.addView(content, MATCH, MATCH);
glass.setBackdrop(content);                                // GlassView / GlassButton / chrome
root.addView(glass, params);                               // sibling above, not inside, content
```

## 5. Components (`dev.lilt.player.ui`)

| Class | What | Notes |
|---|---|---|
| `Theme` | `colors(ctx)`, `isDark`, `setMode`, `glassBlurEnabled` | follows system unless set |
| `Type` | `text(ctx, style, color[, text])`, `apply`, `tabular`, `singleLine`, `maxLines` | bundled fonts |
| `Icons` / `IconPaths` / `GlyphDrawable` | tinted `ic_*` vectors / path table / context-free drawable | 24dp grid, 1.8 stroke, round caps |
| `Ui` | `dp`, `roundRect`, `pressable`, `rowRipple`, `roundOutline`, `ovalOutline`, `row`/`column`, `margins` | |
| `Motion`, `Haptics`, `EdgeToEdge` | motion, haptics, insets (`apply`, `onInsets`, `padding`) | |
| `GlassView`, `GlassButton`, `GlassDrawable` | the glass layer | tab bar, mini player, top bar, menus are built from `GlassView` |
| `ScrollTracker`, `ScrollBehavior` | scroll offset/delta; minimise hysteresis; large-title collapse | |
| `SongRow` | 60dp song row; 52dp track row via `setNumber`; `setRank`, `setSaveState`, `setPlaying`, `setUnavailable` | recycle in `getView` |
| `ListRow`, `GroupedSection`, `SectionHeader` | navigation / settings rows, inset groups, section titles | |
| `AlbumCard` (+ `artist`, `mosaic`), `FeatureCard`, `ShelfView` | cards and the horizontal shelf | `ShelfMetrics` sizes |
| `PillButton`, `Chip`, `Chip.Row`, `SegmentedControl`, `Toggle`, `Slider` | controls | |
| `BottomSheet`, `WindowBlur` | glass sheet (scrim, drag, Back) | host in the screen root |
| `Skeleton`, `EmptyState`, `Notice` | loading, empty / offline / error, inline notice | |
| `PlaceholderDrawable`, `Initials` | typographic artwork placeholder | graphite + initials |
| `ArtworkPalette`, `ArtworkColors`, `MeshBackground`, `NowPlayingIndicator` | artwork colour, mesh backdrop, playing bars | contrast guaranteed |
| `NowPlayingLayout`, `TimeText`, `LyricsTiming`, `SpringCurve` | layout maths, formatting, lyric timing, spring | pure Java, tested |

Usage:

```java
Context c = this; ColorRoles roles = Theme.colors(c);
TextView title = Type.text(c, TypeScale.LARGE_TITLE, roles.textPrimary, "Library");

SongRow row = new SongRow(c);
row.bind("Low Tide", "Field Theory", /*explicit*/ false, SongRow.SOURCE_STREAM);
row.setPlaying(isCurrent, player.isPlaying());          // Ember title + animated bars over the art
artwork.load(row.cover(), track);                       // placeholder: new PlaceholderDrawable(c, album, false)

GlassButton back = new GlassButton(c, R.drawable.ic_back, "Back", DesignTokens.GLASS_BUTTON);
back.setBackdrop(contentLayer);

BottomSheet sheet = new BottomSheet(c, "Sleep Timer", "Playback pauses with a short fade.");
sheet.addOption("In 15 minutes", false, () -> setTimer(15));
sheet.show(root, contentLayer, bottomInsetPx);          // onBackPressed: if (sheet.handleBack()) return;
```

Rules: never hard-code a colour or size, use roles and tokens. Artwork loads through `Artwork` only. List rows are
recycled (`ListView` + holder / `ShelfView`). No work on the main thread: palettes via `ArtworkColors.paletteAsync`.
Every interactive view has a `contentDescription` and ≥ 48dp touch size. Icons that mean state (heart, repeat) change
shape *and* colour, not colour alone.

## 6. Floating chrome

### Tab bar and search button

- Capsule glass 64dp tall, left margin 16, width = screen − 32 − 8 − 64; right of it a 64dp circular glass **Search**
  button. Bottom: gesture inset + 8dp. Three tabs: Home · Browse · Library (24dp icon over a 10sp label).
- Selected tab: filled icon variant, Ember icon and label, plus a *lens* (capsule `glassLens` fill) that slides to
  the tab with the spring. Unselected: textPrimary.
- **Minimise on scroll** (`ScrollBehavior(24dp threshold)`): scrolling down 24dp collapses the capsule to a 48dp circle
  showing the active tab (left), the mini player docks inline between it and a 48dp search circle (right), all 48dp
  tall at the same baseline, spring 360 ms. Scrolling up 12dp, reaching the top zone, or tapping the circle restores.
- Search active: the bar becomes `[home circle][glass search field 48dp]`, mini player stays above it.

### Mini player

Glass capsule 56dp, 16dp side margins, 8dp above the tab bar. 40dp artwork (radius 8), title (15 medium) over artist
(13 secondary), 44dp play/pause and next. A 2dp progress line is not used (the scrubber lives in Now Playing). Tap
expands the player (shared element: the artwork grows to the Now Playing artwork with the gentle spring, 450 ms;
the capsule's glass cross-fades into the mesh). Docked: 48dp tall, 32dp artwork, title only, 22/20dp controls.

### Top bar (large title collapse)

Screens open with a left-aligned **Large Title** (34/40) at y = 52dp. Circular 40dp glass buttons (Account / Plus / Back /
More) sit at the right (or left for Back) at y = 48–52dp. When the title scrolls under the status bar
(`ScrollBehavior.collapseProgress`, 16dp range) a full-width glass bar (status inset + 52dp, bottom hairline) fades
in carrying a centred 17sp semibold title; the large title fades out. Detail screens (album, artist) have no large title;
back/more buttons float over the artwork on a 38% black top scrim.

### Context menu

Long-press a row (or tap "more"): the row lifts (`surfaceElevated`, 8dp shadow, haptic `longPress`), the screen dims
(`scrim`) and blurs, and a glass popover (radius 22, 260dp wide, 8dp padding, rows 46dp, 17sp, trailing 22dp icon)
appears below it. First row group: Play Next, Play Last, Add to Playlist…, Favourite. Divider (8dp darker band).
Second: Go to Album, Go to Artist, Share…. Selected/pressed row uses `glassLens`.

## 7. Screens

All frames are 390 × 844dp in `design/screens/`. Positions are from the prototype, in dp from the window top.

**Home** (`home.png`, `home-scrolled.png`). Large title "Home" + Account glass button. Greeting is the title itself;
no hero. *Top Picks for You*: section header (22 bold) at y 108 (no chevron); `FeatureCard` shelf at y 148, cards
272 wide, eyebrow footnote ("Made for You", "Because you played X"), 12dp gutter. *Recently Played* with chevron
(y 560) → `AlbumCard` shelf (152). Scrolled: *Made for You* mixes (AURA mix artwork: top-left mark + "MIX", title bottom-left on
the tinted tile), *Trending on Audius* as song rows with cloud badges. Sections 28dp apart. Pull to refresh uses the system
control. Empty library: replace Top Picks with the catalogue.

**Browse** (`browse.png`). Large title. *Trending Playlists* (200dp cards), *Top Songs This Week* (rank 20dp column, then song
row), *Genres* (2-column `GenreTile`: 175 × 96, radius 10, solid tone, headline top-left; tones come from
`screens.discover.GenrePalette`, curated, no purple). Offline: `EmptyState.offline` then "Available Offline" shelf.

**Search** (`search.png`, `search-results.png`, `search-error.png`). The search field is a glass capsule at the *bottom*
(48dp, above the gesture inset) with a home circle to its left. Idle: *Recently Searched* rows (44dp circle/art, × at right,
"Clear" in Ember) then *Browse Categories* tiles. Typing: chips (All · Songs · Artists · Albums · Playlists · Your Library),
top-hit row (80dp art, title 20, "On this phone" source line, play circle), then *Songs* (rows with source badges), *Artists*
(96dp circles). Catalogue failure: `Notice` ("Couldn't reach Audius" · "Showing your library only." · Try Again) above
*On This Phone*. No results: `EmptyState.noResults`.

**Library** (`library.png`, `songs.png`). Large title + Plus button. `ListRow`s (icon Ember, title, tabular count, chevron):
Playlists, Artists, Albums, Songs, Saved from Audius. *Collections*: `AlbumCard.mosaic` 152 (Favourites, Most Played, Recently
Added). *Recently Added* shelf (175). Songs: back + sort glass buttons, large title (34), count line, Play / Shuffle pills, rows
(336dp wide to leave room for the 16dp A–Z index rail, 10sp secondary). Saved streamed songs show the `cloud_check` badge.

**Album / Playlist** (`album.png`, `playlist.png`). Album: 390 × 390 artwork bleeding to the top edge under a 38% scrim;
glass Back / Heart / More at 48dp; title (Title 1 28), artist (20 medium secondary), metadata (13 tertiary: genre · year · source); Play
(Ember label) and Shuffle (primary label) pills 175 wide, 48 tall, 8dp gap; track rows 52dp with tabular number,
playing row = Ember title + bars, trailing duration. Playlist: 148dp artwork at left, overline "PLAYLIST · AUDIUS", Title 2, curator,
count · duration, description (2 lines), Play / Shuffle / Save (+) pills, song rows.

**Artist** (`artist.png`). 372dp hero (artist picture or tinted initials plate), Large Title over the bottom-left, followers line,
56dp Ember play FAB at the bottom-right (the one filled-accent control on the screen), *Top Songs*, *Albums* shelf.

**Now Playing** (`now-playing.png`). See section 8.

**Lyrics** (`lyrics.png`). Same mesh. Header (48dp art, title, artist, heart, more) at y 52. Lines in `LYRIC` (30/37 bold), left aligned in
a 342dp column from y 132, 18dp between lines. Active line white 100%; neighbours 36% (blurred 0.6dp on API 31+); past lines 26% (1.2dp blur). Auto-scroll keeps the active line
at ~35% of the viewport, 350 ms standard curve; tapping a line seeks to it. Unsynced lyrics: plain text, no highlight. Bottom capsule with *lyrics*
selected.

**Queue** (`queue.png`). Header as Lyrics. *Playing Next* (17 semibold) + footnote ("From Low Tide") with three 44 × 36 toggles
(shuffle, repeat, autoplay). Rows 60dp on art (white text), drag handles right (45% white), the dragged row lifts (16% white
plate, radius 14, 8dp shadow, haptic `dragStart`, slot ticks). *Autoplay* section dims rows to 55%. Swipe a row to remove.

**Sound** (`sound.png`). Back glass button + centred title. `GroupedSection`s: Equaliser (toggle, preset chips Flat · Bass · Vocal ·
Acoustic · Custom, 5-band curve on a −12…+12 dB grid, Ember line, 14dp handles), Playback (loudness normalisation toggle with subtitle,
crossfade slider 0–12 s, gapless toggle), Sleep timer / Spatial audio rows. Sleep timer opens the glass `BottomSheet` (`sleep-timer.png`).

**Replay** (`replay.png`). Large title; `SegmentedControl` Week | Month | Year; STAT number (64 bold, tabular) + "minutes listened in October"
+ footnote; *Top Artists* (rank, 40dp circle, name, minutes) and *Top Songs* (rank, row, plays). No charts, no confetti.

**States**: loading `Skeleton` (`loading.png`) mirrors the final layout, never a spinner on content; offline (`offline.png`); empty first run
(`empty.png`): permission empty state with Allow Access (Ember fill, the only one) and a text action; partial failure (`search-error.png`).

**Widgets** (`PlayerWidget`, layouts `widget_compact/deck/large`). Graphite panel (`#161618`, light `#F4F4F6`) radius 22dp, 10–12dp padding; cover with a
38px/180px corner; title 15–17sp medium, artist 13sp secondary; transport as 24dp `ic_previous/play/next` (48dp targets); Artwork theme
uses `ArtworkPalette.background` (white ink guaranteed 7:1). Themes: Dark, Light, Ember (Ember play button), Artwork (default). The play button
always sends the broadcast; with an empty queue it calls `play()`, which makes the service resume the saved queue.

## 8. Now Playing: exact layout (no volume slider)

Hardware keys own volume; there is no volume control. The window is edge to edge; all measurements are dp from the top. The layout is
adaptive: **drag handle → artwork → title row → scrubber → transport → glass capsule**, the artwork as large as the screen allows and the
remaining height distributed between six gaps in fixed proportions (`ui.NowPlayingLayout`, tested on 7 screen sizes × 3 inset sets).

| Group | Height | Detail |
|---|---|---|
| Handle | 20 | 36 × 5 capsule, white 40%, centred, 8dp below the status inset |
| Artwork | `art` | square, centred, `art = clamp(160, 520, min(width − 48, budget))`, radius 12 (concentric: 12 on 342), shadow 16dp; scale 0.86 when paused (spring), 1.0 playing |
| Title row | 52 | title (20 semibold, 1 line) over artist (20 regular, 64% white) at the 24dp margin; right: heart (36dp glass, Ember when saved), more (36dp glass) 8dp apart, vertically centred |
| Scrubber | 32 | `Slider.PLAYER`: 4dp track (8dp while dragging), no thumb, played 88% white, track 22% white; below it elapsed (left) and −remaining (right), 12 medium tabular, 64% white |
| Transport | 72 | previous 40dp · pause/play **56dp** · next 40dp, white, evenly distributed over the 342dp column (centres at 1/6, 1/2, 5/6), optically centred |
| Capsule | 52 | glass on artwork, 192dp wide, centred: lyrics · output · queue (22dp icons, 52 × 40 hit areas); the selected one is a white pill with a dark icon |

Gaps, in order (handle→art, art→title, title→scrubber, scrubber→transport, transport→capsule, capsule→bottom inset): minimum
**8 / 28 / 16 / 16 / 20 / 12**. `budget = height − topInset − bottomInset − 196 (fixed groups) − 100 (minimum gaps)`; extra =
`budget − art`, shared **12 % / 12 % / 8 % / 26 % / 26 % / 16 %**. The last gap absorbs rounding so the capsule bottom lands exactly on the bottom inset.

Examples (width × height, insets top/bottom → artwork; gaps):
390 × 844, 40/24 → 342; 21 / 41 / 25 / 45 / 49 / 29 · 360 × 640, 24/48 → 240; 8 / 28 / 16 / 16 / 20 / 12 · 360 × 800, 32/24 → 312;
20 / 40 / 24 / 43 / 47 / 30 · 412 × 915, 40/24 → 364; 27 / 47 / 29 / 57 / 61 / 38.
Landscape / split screen (width > height or budget < 160): two panes, artwork left, controls right (`twoPane`).

Background: `MeshBackground` from `ArtworkPalette` (3 blurred fields over the tint; no artwork → graphite). Colours of controls: `ON_ART_*`.
Collapse: drag down from anywhere on the artwork or handle; the artwork shrinks to the mini-player artwork (gentle spring, 450 ms).

## 9. Accessibility

- Contrast tested in `DesignTokensTest` / `UiLogicTest`. Touch targets ≥ 48dp. Headings are marked; icon-only controls have labels.
- Scrubber/Slider expose range info and ±step actions; Toggle reports as a Switch; playing rows describe themselves.
- Respect "remove animations" (`Motion.enabled()`), battery saver and reduce transparency (`Theme.glassBlurEnabled`).
- Large fonts: rows grow; titles ellipsize at 1–2 lines, never overlap controls.

## 10. Verification

`scripts/compile-check.sh`, `scripts/run-logic-tests.sh` (`tests/ui/*`: colour maths, tokens vs `design/tokens.json`, layout fit, palette contrast,
path parsing, icon bounds, formatting, scroll behaviour, springs, lyric timing). CI runs aapt2 and lint on the generated vector drawables.
