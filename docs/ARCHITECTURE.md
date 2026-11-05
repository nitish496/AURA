# AURA architecture

Native Android (Java 17, minSdk 26, target 35), AndroidX Media3 1.5.1. Sections are owned by the
team member who maintains that part of the app.

## Audio engine

Owner files: `PlaybackService`, `PlaybackStore`, `CrossfadeEngine`, `CrossfadeRules`, `SleepTimer`,
`LocalBitmapLoader`, `SpatialAudioSupport`, `DolbySettings` and the package `dev.lilt.player.audio`.
Pure-Java logic (tested by `scripts/run-logic-tests.sh`, tests in `tests/audio/`): `CacheKeys`,
`MediaIds`, `QueueRestorePlan`, `PlaybackErrorPolicy`, `EqualizerPresets`, `AudioEffectSettings`,
`LoudnessMeter`, `LoudnessRules`, `PlayCounter`, `ListeningLedger`, `CrossfadeRules`.

### Players and data path

`PlaybackService` runs one `MediaSession` over two ExoPlayers built by `audio.PlayerFactory`: the
active player and a spare that only plays during a crossfade (the two swap roles at the hand-off,
and `session.setPlayer` follows). Both share one data path (`audio.MediaCache`):

```
DefaultDataSource ── content://, file://, asset ─────────────► platform (never copied to the cache)
                  └─ http(s) ─► CacheDataSource ─► DefaultHttpDataSource
                                 SimpleCache cacheDir/media      UA "AURA/2.0 (Android)", cross-protocol
                                 LRU ~1 GB (<= 1/5 free space)   redirects, 10 s connect / 15 s read
                                 StandaloneDatabaseProvider index
```

- One `SimpleCache` per process (a second instance on the folder would throw). Cache key: the
  MediaItem's custom cache key (`Track.item()` sets `"audius:<id>"`), otherwise
  `CacheKeys.forUri()` (Audius `/v1/tracks/<id>/stream` URLs map to the track id). Keys therefore
  survive host failover and streamed songs replay offline. `FLAG_IGNORE_CACHE_ON_ERROR`: a damaged
  cache falls back to the network instead of failing playback.
- Buffering: start after 1.5 s, refill to 30 s, at most 120 s ahead (also bounded by Media3's
  audio byte target), 3 s after a rebuffer. Loads retry 6 times with Media3's back-off (~20 s)
  before an error surfaces; playback continues from the buffer meanwhile.
- MP3 constant-bitrate seeking is enabled so streams without a seek table stay seekable.

Audio quality decisions:

- Decoding keeps the file's sample rate and channel count; AURA never resamples.
- Float output is **off** on purpose: in Media3 1.5.1 `DefaultAudioSink` skips encoder-delay
  trimming (gapless MP3/AAC), channel mapping and Sonic speed control whenever it outputs float.
  Turning it on would put gaps between tracks of continuous albums and mixes.
- Gapless is Media3's default and is untouched when crossfade is 0 (the engine does not even tick).
- Audio offload and platform speed control stay off (less reliable); decoder fallback is on.
- Audio attributes `USAGE_MEDIA` / `CONTENT_TYPE_MUSIC` (Android may spatialize compatible
  multichannel audio). Only the active player handles audio focus; both pause on
  becoming-noisy. Wake mode is `NETWORK` (wake + Wi-Fi lock) while the queue contains streams,
  `LOCAL` otherwise.

### Controller API

Custom `SessionCommand` constants on `PlaybackService`, all available to AURA's own controllers
(`MediaController.sendCustomCommand(command, args)`); results arrive in `SessionResult.extras`.
Absent keys keep their current value. Invalid values return `SessionError.ERROR_BAD_VALUE`.

| Command | Args | Result extras |
| --- | --- | --- |
| `SET_PLAYBACK_OPTIONS` / `GET_PLAYBACK_OPTIONS` | `crossfadeSeconds` int 0-12, `autoplayEnabled` bool, `stopOnTaskRemoved` bool | same three keys |
| `SET_SLEEP_TIMER` / `GET_SLEEP_TIMER` | `minutes` int 0-720 (0 cancels) **or** `endOfTrack` true (pause when the current song ends) | `remainingMillis` long, `endOfTrack` bool |
| `SET_AUDIO_EFFECTS` / `GET_AUDIO_EFFECTS` | `eqEnabled` bool, `eqPreset` String, `eqBands` short[] (millibels, one per device band; selects "Custom"), `normalize` bool, `speed` float 0.5-2.0 | the five keys plus `eqAvailable` bool, `eqPresets` String[], `eqBandFrequencies` int[] (Hz), `eqLevelRange` short[]{min,max} (mB), `normalizeGainDb` float (applied to the current song), `normalizeSource` "replaygain" / "measured" / "none", `speedRange` float[]{0.5, 2.0} |
| `QUEUE_PLAY_NEXT` | `index` int | (none) — makes that queue item play right after the current one, also while shuffled |
| `GET_CACHE_INFO` / `CLEAR_CACHE` | — | `cacheBytes` long, `cacheLimitBytes` long |

`eqBands` must have exactly `eqBandFrequencies.length` entries (else `ERROR_BAD_VALUE`; with no
equaliser on the device `ERROR_NOT_SUPPORTED`). Presets: Flat, Bass Boost, Bass Reducer,
Treble Boost, Treble Reducer, Vocal, Loudness, Acoustic, Classical, Electronic, Hip-Hop, Jazz, Pop,
Rock, Late Night, Spoken Word, Small Speakers (`EqualizerPresets.names()`), plus "Custom".

Session extras (`MediaController.getSessionExtras()`, change callback
`MediaController.Listener.onExtrasChanged`): `waitingForNetwork` bool — playback paused because
the phone went offline; it resumes by itself when a validated connection returns.

Messages for the user arrive as non-fatal `SessionError`s (code `ERROR_IO`, readable `message`) via
`MediaController.Listener.onError(controller, sessionError)`. Show them as a toast/snackbar. The
service toasts itself only when no AURA screen is connected. `Player.Listener.onPlayerError` still
fires with Media3's technical error; prefer the `SessionError` text for streams.

### Queue

Use the standard `MediaController` playlist API; the service persists every change.

- Play: `setMediaItems(items, startIndex, 0); prepare(); play()`.
- Play next: `addMediaItem(getCurrentMediaItemIndex() + 1, item)`; if shuffle is on, follow with
  `QUEUE_PLAY_NEXT {index}` so the item really plays next (a plain insert lands at a random place
  in the shuffled order).
- Add to queue: `addMediaItem(item)`. Reorder: `moveMediaItem(from, to)`. Remove:
  `removeMediaItem(i)` / `removeMediaItems(from, to)`.
- Streamed songs: build items with `Track.item(null)` and `RemoteTrackStore.put(track)` so their ids
  resolve later (stats, saved queues) even offline.

`PlaybackStore` saves items, index, position, shuffle/repeat and the shuffled play order (writes on
its own thread, coalesced). On start-up the saved queue is read and resolved off the main thread and
put on the player **paused** (device files are prepared at once; a stream is only opened when the
user presses play). Catalogue entries resolve through `RemoteTrackStore` (fresh host) and otherwise
from the snapshot's own stream URL and metadata; device files need the media permission. Entries
that cannot be resolved are dropped (`QueueRestorePlan`): if the current song is gone the next
playable one starts from 0, and the shuffle order is remapped. `play()` on an empty player (widget,
headset, system controls) triggers `onPlaybackResumption`, which resumes the saved queue.

### Errors and connectivity (`PlaybackErrorPolicy`)

| Situation | Action |
| --- | --- |
| Error while the user is not playing (e.g. restored queue) | ignored; pressing play prepares again |
| Stream, phone offline, network error | pause, `waitingForNetwork = true`, resume automatically when online |
| Stream, online, first failure | retry once from the same position on the next healthy host (`AudiusClient.failover`) |
| Stream, retry failed or undecodable | skip to the next song (max 3 in a row, then pause) |
| Device file, unspecified I/O | retry once, then pause with a message |
| Device file missing/damaged | pause with a message (the user's own queue is never skipped silently) |

### Equaliser and loudness normalisation

`audio.AudioEffects` binds an `android.media.audiofx.Equalizer` to each player's audio session
(main and spare; rebinds if a session id changes) and announces the sessions with
`ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` so system sound apps can attach too. Every audiofx call
is guarded: a phone without a working equaliser reports `eqAvailable = false` and plays normally.
Presets are curves at octave centres, interpolated in log frequency onto the device's own bands and
clamped to its level range (`EqualizerPresets`); custom bands are stored as frequency/gain points,
so they survive a phone change. Settings persist in the `audio_effects` preferences.

Normalisation targets -14 LUFS. Gain source, in order: ReplayGain / Opus R128 tags (ID3 `TXXX`,
Vorbis comments, MP4 `----` atoms), AURA's own ITU-R BS.1770 measurement from an earlier listen
(`LoudnessMeter` fed by a read-only `TeeAudioProcessor` tap in each player, stored per media id by
`LoudnessStore` once at least a minute or half the song was heard), else 0 dB. Cuts (most songs)
are applied as player volume, boosts through `LoudnessEnhancer`, capped at +6 dB and by the song's
peak so nothing clips. Gains change at song transitions and follow crossfades.

### Crossfade (`CrossfadeEngine`)

The next song is prepared on the spare player 3 s (device) or 10 s (stream) before the fade
window. The ramp starts only after the spare is buffered and actually playing; otherwise (not
ready within 6 s / 12 s, a stall, an error, a queue edit) the overlap is cancelled and the main
player does its normal gapless transition. The spare copies the queue, the shuffled play order,
repeat mode and speed, and fades to the incoming song's normalised level. Crossfade is skipped for
repeat-one, speed other than 1x, the last seconds of a sleep timer and "end of song" sleep. The
engine ticks fast only near the end of a song and not at all while paused.

### Sleep timer

`SET_SLEEP_TIMER {minutes}` pauses after 1-720 minutes with an 8 s fade-out; `{endOfTrack: true}`
pauses when the current song ends (`setPauseAtEndOfMediaItems`). Setting either cancels the other.

### Listening stats (`audio.ListeningStats`)

`ListeningStats.get(context)` is a process-wide store (SharedPreferences `listening_stats`). The
service records automatically: a play counts after 30 s of listening or half the song
(`PlayCounter`, content time, so speed is respected); listening time is added continuously.

```java
ListeningStats stats = ListeningStats.get(context);           // call queries off the main thread
List<ListeningLedger.TrackStat> top = stats.topTracks(25, sinceEpochMs); // id, title, artist, album, plays, lastPlayedAt, msListened
List<ListeningLedger.ArtistStat> artists = stats.topArtists(10, sinceEpochMs); // name, plays, tracks, msListened
long minutes = stats.totalMinutes(sinceEpochMs);              // 0 = all time
stats.track(id); stats.recent(20); stats.clear();
stats.addListener(runnable);                                  // main thread, after changes
```

Periods use UTC day buckets (a "since" time includes its whole day). Bounded to 4000 tracks and
20 000 day rows, oldest first out.

### Autoplay

When the queue is about to end (20 s before, or at the end) and autoplay is on, one related song is
appended: catalogue seeds use `AudiusClient.related(seed, 25)` (same artist and genre), device
seeds AURA's local `AutoplayRules` (album, artist, mood). Both avoid the queue and the last 50
plays, run on a worker thread and never interrupt a queue the user changed meanwhile.

### Lifecycle and threading

- Foreground service type `mediaPlayback`; Media3 manages the notification. Android 13+ media
  notifications are exempt from `POST_NOTIFICATIONS` (declared so the UI may still request it).
- Removing AURA from Recents stops playback (the owner's preference; `stopOnTaskRemoved = false`
  keeps playing in the foreground). A paused service always stops.
- Notification artwork: `LocalBitmapLoader` decodes device art via `Artwork` and fetches https
  catalogue art through the media cache (so cached songs show covers offline), sampled to 512 px.
- Players run on the main thread. Disk, network and scans run on: `AuraQueueStore` (queue),
  `AuraPlaybackWorker` (autoplay, cache info), `AuraLoudness`, `AuraStats`, `AuraNotificationArt`.
  All are shut down in `onDestroy`; the stats store lives for the process.

## Streaming (`dev.lilt.player.stream`)

The streaming layer provides the Audius catalogue, the library of songs and playlists saved without
downloading audio, lyrics, network state, and https artwork in `Artwork`. It uses only platform
APIs: `HttpURLConnection`, `org.json` and `java.util.concurrent`. It has no third-party
dependencies.

### Layout

The parsing and policy code is pure Java (`org.json` at most), so it runs in the offline logic tests.
Thin Android facades sit on top of it.

| Pure core (tested on the JVM) | Android facade (app-facing) |
| --- | --- |
| `AudiusApi`: endpoints, host pool, failover, TTL cache; `AudiusParser`; `AudiusTrack`; `HostPool`; `TtlCache`; `Http` | `AudiusClient` (singleton; returns `Track`) |
| `RecordStore`: ordered, bounded JSON store; `JsonFiles` (atomic writes) | `RemoteTrackStore`, `SavedCatalog` |
| `LrclibApi`, `LrcParser`, `Lyrics`, `EmbeddedLyrics` (ID3/FLAC tags) | `LyricsClient` |
| `CatalogArtist`, `CatalogPlaylist`, `StreamArt` (artwork variants) | `Connectivity`; `StreamStorage` (package-private app context and dirs) |

### Track contract (`dev.lilt.player.Track`)

- `source` is `"local"` or `"audius"`, and `isRemote()` tells them apart. Remote ids are always
  `"audius:<trackId>"`. For a remote track, `uri` is the stream URL
  `{host}/v1/tracks/{id}/stream?app_name=AURA`, which redirects to the audio.
- `artworkUrl` holds the best https cover (1000x1000, else 480, else 150), or null. `genre`,
  `artistId` and `permalink` are `""` when unknown, and always `""` for local tracks.
  `artworkUrl(px)` picks the 150, 480 or 1000 variant. `webUrl()` returns the audius.co share link.
- `Track.remote(...)` comes in two overloads: the contract's 8-argument form, and a 10-argument
  form that adds `artistId` and `permalink`. A blank album becomes `"Single"`.
  `withStreamUrl(url)` returns a copy for host failover.
- `item(Uri)` is unchanged for local tracks. For remote tracks it sets the artwork URI to
  `artworkUrl` (custom covers apply only to local files), `albumArtist`, `genre`, `durationMs`, and
  these extras: `source`, `artistId`, `permalink` and `durationMs`.
- `albumKey()` is unchanged for local tracks. Remote tracks never merge with local albums:
  `remote-single:<id>` for singles, and `remote-album:<album>:<artist>` for albums.
- `toJson()` / `fromJson()` persist remote (and local) metadata. `fromJson` returns null for an
  unusable record and never throws.

### Audius catalogue (`AudiusClient`)

`AudiusClient.get()` (or `get(context)`, which also turns on host memory) exposes `trending`,
`searchTracks`, `searchArtists`, `searchPlaylists`, `trendingPlaylists`, `playlistTracks(id |
CatalogPlaylist)`, `artistTracks` (most played first), `related(seed, n)` and `track(id)`.
`related` interleaves the same artist's top tracks with trending tracks of the same genre and leaves
out the seed. For local seeds it matches the artist by exact name. `genres()` returns the curated
Browse genres in exact Audius spelling.

- **Threading.** Every catalogue call blocks and throws `IllegalStateException` on the main thread.
  Errors are `IOException`. `Http.StatusException.code` carries the HTTP status. A 404 from
  `track(id)` returns null.
- **Hosts.** `GET https://api.audius.co` returns the host list. Only https origins are accepted.
  Up to 4 hosts are probed in parallel with `GET /v1/tracks/trending?limit=1`, and the first one
  that answers healthy within 4 s wins. That host stays in use until a request fails with an I/O
  error, a timeout, a 5xx, 408 or 429, or a malformed body. A failed host cools down for 5 minutes,
  and the request is retried (3 attempts) on a newly probed host, then on the `api.audius.co`
  gateway. The gateway is itself retried against discovered hosts after 5 minutes. Other 4xx
  answers do not fail over. The chosen host and the host list are remembered for a day in
  `files/stream/audius_host.json`, so a cold start skips discovery. Timeouts are connect 6 s and
  read 10 s (probes: 3 s and 4 s). Requests ask for gzip. Every request carries `app_name=AURA`.
- **Cache.** Parsed results are cached in memory, LRU 64 entries: trending and catalogue pages for
  10 minutes, searches for 2 minutes.
- **Filtering.** Tracks are dropped when deleted, `is_streamable == false`, unavailable,
  stream-gated without access (purchase or follow), or owned by a deactivated user. Playlists are
  dropped when deleted, private, gated or empty. Duration is converted from seconds to ms, and
  `user.name` becomes the artist. The album comes from the album context
  (`playlistTracks(CatalogPlaylist)` for an album), then `album_backlink`, then `"Single"`.
- **Streams.** `streamUrl(id)` uses the current healthy host, or the gateway. `refresh(track)`
  moves a stored track to the current host, and keeps its own URL while no host is known and that
  URL's host has not failed. `failover(track)` marks the stream's host as failed and returns the
  track on the next host. Both are non-blocking. The audio engine calls `failover` when a stream
  fails to open, and keys its stream cache by track id (`audio.CacheKeys`), so a host change never
  invalidates cached audio.

### Library without downloads (`RemoteTrackStore`, `SavedCatalog`)

Both use `RecordStore`. The store loads its JSON file once, in the background, when the first
instance is created. An earlier access waits for that load. Reads are in-memory. Writes are
debounced by about 0.5 s onto a background thread and replace the file atomically (write, fsync,
rename). A corrupt file is moved to `*.corrupt` and the store starts empty. All instances in the
process share one state.

- `RemoteTrackStore` holds the metadata of remote tracks that must resolve later: queued,
  favourited, in playlists or recent (`files/stream/remote_tracks.json`, 5,000 entries, least
  recently `put` evicted). `get(id)` also resolves saved songs and tracks of saved playlists, so
  ids stored anywhere (Library playlists, favourites, the queue) resolve offline. Every returned
  track goes through `AudiusClient.refresh`.
- `SavedCatalog` holds the user's streamed library (`saved_tracks.json`, up to 20,000 songs;
  `saved_playlists.json`, up to 1,000 playlists, each with a snapshot of up to 2,000 tracks).
  - Songs: `add`, `addAll`, `remove`, `contains`, `savedAt`, `get`, `tracks()` (newest first),
    `isFull`.
  - Playlists: `addPlaylist(p, tracks)`, `updatePlaylistTracks`, `removePlaylist`,
    `containsPlaylist`, `playlist`, `playlists()`, `playlistTracks`.
  - Saving again refreshes the metadata without reordering. When the library is full, `add` returns
    false; it never silently drops an older song.
  - `Listener.onSavedCatalogChanged()` runs on the main thread, coalesced per burst of changes.
    Remove listeners in `onDestroy`.

### Lyrics (`LyricsClient`, `EmbeddedLyrics`, `LrclibApi`, `LrcParser`)

`LyricsClient.get(context).fetch(track)` blocks and never returns null. It returns `Lyrics.NONE` or
an `instrumental` marker when nothing is found, and throws `IOException` only when LRCLIB is
needed but cannot be reached and nothing is cached.

1. **Local tracks** first check lyrics embedded in the file. The client reads them with
   `ContentResolver.openInputStream`, reading at most the tag and never more than 2 MB, and skips
   FLAC pictures without reading them. Supported formats:
   - ID3v2.2, 2.3 and 2.4 `USLT`/`ULT` frames. A text with LRC timestamps counts as synced.
   - `SYLT`/`SLT` frames with millisecond timestamps, including karaoke syllables.
   - Encodings 0–3, tag- and frame-level unsynchronisation, extended headers, syncsafe and legacy
     iTunes plain frame sizes, zlib-compressed frames.
   - FLAC Vorbis comments `LYRICS` and `UNSYNCEDLYRICS`.
2. **LRCLIB** (`https://lrclib.net`, User-Agent `AURA/2.0 (https://github.com/nitish496/AURA)`)
   is the fallback, and the only source for remote tracks.
   - `/api/get` is called with the exact signature when the album is meaningful.
   - Otherwise, `/api/search` is called by title and artist, retrying with a cleaned title (without
     "feat.", brackets or " - Remaster"). The search keeps results by the same artist and picks the
     closest duration.
   - Synced lines are kept only within 3 s of the track's duration. A match within 10 s contributes
     plain lyrics only, so the timing is never wrong.
3. **Caching.** Results are cached per track id in memory. LRCLIB answers are also cached on disk,
   one JSON file per song in `cache/lyrics` (2,000 files): 30 days when found and 2 days when not
   found. Expired lyrics are still served if the refresh fails.

`Lyrics` holds `lines` (sorted `Line{timeMs, text}`; a blank text marks an instrumental gap),
`plain`, `instrumental` and `source` (`embedded` or `lrclib`). `synced()` reports whether lines
exist, and `indexAt(positionMs)` finds the current line by binary search, returning -1 before the
first line. `LrcParser` handles several timestamps per line, `[mm:ss]`, `.x`, `.xxx` and `:xx`
fractions, `[offset:±ms]`, metadata tags, `<mm:ss.xx>` word stamps, a BOM, and CR/LF line endings.

### Artwork for streamed tracks (`dev.lilt.player.Artwork`)

The public API is unchanged. `load(view, track)`, `prefetch`, `warm` and `loadActual(...)` accept
remote tracks and https URIs. The local code path is untouched.

- Remote covers use the same memory caches, placeholders and request coalescing as local covers.
- Downloads run on a separate 4-thread LIFO pool, so the newest (visible) requests go first and
  network latency never delays local decodes.
- A queued download is dropped when every view waiting for it has been recycled.
- Lists fetch the 150 or 480 variant. The player fetches the 1000x1000 original, sampled so that
  at least 900 px remain.
- The original bytes go into the shared `cache/cover-*.png` disk cache (256 MB, oldest first out).
- Failures are cached: 60 s for network errors, 6 h for 404 or a body that is not an image. A failed
  view keeps its placeholder and retries on its next `load`.
- `loadActual` maps an Audius stream URL to the stored artwork of its track. It never runs
  `MediaMetadataRetriever` on a network URI.

### Connectivity

- `Connectivity.online(context)` reports whether the default network has validated internet
  access.
- `metered(context)` reports whether that network is metered.
- `listen(context, listener)` reports online/offline flips on the main thread. Call `cancel()` on
  the returned subscription in `onDestroy`.

These checks need `ACCESS_NETWORK_STATE`. The manifest also declares `INTERNET`. Cleartext traffic
stays disabled, which is the default at targetSdk 35.

### Integration notes for UI code

- Call `AudiusClient.get(context)`, `LyricsClient.get(context)` or any constructor that takes a
  `Context` early (for example in `onCreate`). The context turns on host memory, the lyrics disk
  cache and embedded lyrics. Optionally call `AudiusClient.get().warmUp()` at start.
- Run every catalogue or lyrics call on a worker executor and post the results back. Show offline
  states with `Connectivity`.
- `put` remote tracks into `RemoteTrackStore` when they are queued, favourited or added to a Library
  playlist. Saving to the library is `SavedCatalog.add`. Pass the album object to
  `playlistTracks(CatalogPlaylist)` so its tracks carry the album name.
- Load lyrics only when the lyrics view opens: LRCLIB lookups send song metadata off the device (see
  `docs/PRIVACY_REVIEW.md`).

### Tests

The logic tests in `tests/stream/` run offline on the JVM through `scripts/run-logic-tests.sh`:

- `AudiusParserTest` checks parsing against fixtures shaped like real API responses
  (`tests/fixtures/audius`).
- `AudiusApiTest` runs discovery, the parallel probe, failover to the next host and then the
  gateway, caching, gzip, 404 handling and host memory against loopback HTTP servers.
- `LrclibApiTest` covers LRCLIB matching and caching (`tests/fixtures/lrclib`).
- `LrcParserTest` covers LRC parsing.
- `EmbeddedLyricsTest` covers tags written by mutagen (`tests/fixtures/embedded`, the CI demo
  shape) plus synthetic byte fixtures for every ID3 and FLAC branch.
- `RecordStoreTest` covers ordering, bounds, atomic persistence and corrupt files.
- `TrackJsonTest` covers the `Track` model on android-all.

The sandbox cannot reach Audius or LRCLIB, so the tests use fixtures and loopback servers only.
