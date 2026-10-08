# Public repository privacy review

The published history, tracked file list, commit attribution and current APK were checked for common credential formats, private local workspace paths, personal email patterns, signing keys and local environment files. No relevant matches were found in these targeted scans. This is not a guarantee against every possible sensitive item.

Excluded: private signing keys, local SDK/build tooling, local configuration, test audio fixtures, personal development notes and device logs. Public commits use the GitHub account handle and a GitHub noreply address. That account identity is intentionally public.

This app has no Internet permission. It reads permitted local audio, and stores settings and playlists on the device. Please never attach private songs, credentials or unredacted personal logs to issues.

## 1.23 update

The public export was scanned again for common service tokens, private-key blocks and private local paths; no targeted matches found. Private signing configuration remains excluded. The new widget reads the existing local playback state and adds no network or audio-recording permission. This is a targeted review, not an exhaustive security guarantee.
