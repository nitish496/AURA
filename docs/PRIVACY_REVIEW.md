# Public repository privacy review

The published history, tracked file list, commit attribution and current APK were checked for common credential formats, private local workspace paths, personal email patterns, signing keys and local environment files. No relevant matches were found in these targeted scans. This is not a guarantee against every possible sensitive item.

Excluded: private signing keys, local SDK/build tooling, local configuration, test audio fixtures, personal development notes and device logs. Public commits use the GitHub account handle and a GitHub noreply address. That account identity is intentionally public.

Versions up to 1.23 had no Internet permission. Version 2.0 uses the network for the streaming catalogue and for lyrics, as described below. AURA still reads permitted local audio and stores settings and playlists on the device. Please never attach private songs, credentials or unredacted personal logs to issues.

## 1.23 update

The public export was scanned again for common service tokens, private-key blocks and private local paths; no targeted matches found. Private signing configuration remains excluded. The new widget reads the existing local playback state and adds no network or audio-recording permission. This is a targeted review, not an exhaustive security guarantee.

## 2.0 update: network use

2.0 adds the `INTERNET` and `ACCESS_NETWORK_STATE` permissions. There is no account, no sign-in, no cookies, no advertising or analytics SDK, and no device identifier is sent anywhere. No new third-party libraries were added; requests use Android's own HTTP stack. All traffic is HTTPS: cleartext HTTP is disabled (the Android default at target SDK 35), and only `https://` hosts are accepted from the Audius host list.

**Audius streaming catalogue.** The catalogue comes from the public [Audius API](https://docs.audius.org), where artists publish tracks for free streaming. AURA contacts:

- `api.audius.co`, to fetch the list of Audius API hosts. It also serves as a fallback when no host from that list responds.
- One host from that list, chosen by a quick parallel check. It is remembered on the device for up to a day (`files/stream/audius_host.json`).
- Audius content servers. The stream address redirects to them for audio, and they serve cover images.

These requests carry search text you type, genre and time filters, the ids of the tracks, playlists and artists you open, `app_name=AURA` (Audius asks apps to identify themselves), and the User-Agent `AURA/2.0 (Android; https://github.com/nitish496/AURA)` or `AURA/2.0 (Android)`. Like any web request they also reveal your IP address. Audius can therefore see which tracks are browsed and streamed from that address. AURA never uploads your local library to Audius. Tracks that are deleted, unavailable, or gated behind a purchase or follow are skipped. AURA does not try to get around such restrictions.

**LRCLIB lyrics.** When lyrics are requested for a song, AURA first reads lyrics embedded in local files (ID3 or FLAC tags) on the device. Those lyrics are never sent anywhere. If a song has none, and for streamed songs, AURA asks [lrclib.net](https://lrclib.net) for lyrics. That request sends the song's title, artist, album and length in seconds, with the User-Agent `AURA/2.0 (https://github.com/nitish496/AURA)`, and also applies to songs stored on the phone. The app makes the request only when lyrics are needed, not for the whole library.

**Network state.** `ACCESS_NETWORK_STATE` lets the app tell whether a validated connection exists so it can show offline states. Checking this makes no network request.

**Stored on the device** (removed with the app or by clearing its storage; `allowBackup` stays off, so nothing is copied to cloud backups):

| Data | Location | Bound |
| --- | --- | --- |
| Songs and playlists saved to the library from the catalogue (metadata only, no audio) | `files/stream/saved_tracks.json`, `saved_playlists.json` | 20,000 songs, 1,000 playlists |
| Metadata of streamed tracks that are queued, favourited or in playlists | `files/stream/remote_tracks.json` | 5,000 tracks, oldest first out |
| Cover images (local and streamed) | `cache/cover-*.png` | 256 MB, oldest first out |
| Lyrics lookups, including "not found" results | `cache/lyrics/` | 2,000 entries; 30 days (2 days for "not found") |
| Streamed audio, so repeat plays work offline (audio engine) | `cache/media/` | about 1 GB, least recently used out |

This review covers the code paths above. It is a targeted review, not an exhaustive security guarantee.
