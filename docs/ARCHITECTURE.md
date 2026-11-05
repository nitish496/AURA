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
