# Background media notes

This patch supplies artwork to Android MediaSession and notification controls, and holds a CPU wake lock only when the web player reports playing. It does **not** make web-based YouTube audio a native Media3 player.

The reference APK bundles @capgo/capacitor-media-session and its website also creates a browser media-session audio bridge. WebShelf cannot force the embedded YouTube iframe to continue executing after Android suspends the WebView. This may require changing the source website or using a provider-authorized native audio source.

Artwork is loaded only from HTTPS URLs and capped at 2 MB. This is unverified on an Android device.


V5 stability updates:
- Suppress duplicate JS-to-Android media state messages and redundant notification rebuilds.
- Avoid immediately pausing the WebView as part of turning the background option off.
- Allow artwork redirects and log artwork HTTP/decoding failures to Logcat (tag WebShelfMedia).
- Publish album, art and display icon metadata for Android media surfaces.

Limitations: WebView/YouTube iframe playback is still subject to site and OS policies; foreground services cannot guarantee that an embedded YouTube iframe keeps playing while hidden.


## v6: WebView visibility and immediate metadata
- Added `BackgroundWebView.java`, an opt-in visibility workaround adapted from the provided Capacitor example for WebShelf's native Java Activity.
- Enabling background mode requests metadata immediately from the current page and child frames; a navigation also triggers refresh.
- WebView timers are resumed while background mode is enabled; disabling restores normal behavior.
- YouTube iframe restrictions and OEM background process limits can still interrupt playback. This is not a separate native decoder.


## v7: Reference comparison and background visibility

The YouTube Music Player v1.7.0 project uses `BackgroundCapacitorWebView.onWindowVisibilityChanged`: while hidden, it **returns** on `GONE` rather than sending a synthetic `VISIBLE` event. WebShelf's `BackgroundWebView` now follows that pattern only while the user enables Background playback.

The sample site also creates a silent HTML5 WAV element in its own `script.js` (via `syncBrowserMediaSessionBridge`) to keep the parent page's Media Session registered, in addition to the YouTube iframe. WebShelf does not inject an additional silent player into arbitrary third-party websites.

WebShelf's foreground service, wake lock, Media Session and song artwork integration are preserved. This is best-effort only: Android Chromium's actual visibility state and YouTube's player policy cannot be guaranteed to remain foreground just by overriding the WebView callback.


## v8 stability changes
- Start media service before first metadata snapshot to avoid dropping the initial update.
- Remove `WebView.onResume()` calls from stopped/paused Activity lifecycle methods; those calls can cause renderer and UI instability.
- Preserve MediaSession notification after moving WebShelf to background where Android allows it.
- Resolve relative artwork URLs and reduce unnecessary document-wide mutation observers.
- Use a timed partial wake lock to reduce risk of retaining the lock after playback state stops updating.

**Important:** This does not disguise Android foreground/background state from YouTube and does not bypass YouTube Free's background playback restrictions. The notification service cannot make a restricted embedded YouTube iframe keep playing.

## v9 Media Session timeline and seeking
Added duration, position, Android ACTION_SEEK_TO, native-to-JavaScript seek commands, and a position-state hook. Supported HTML5 players and the sample website's own `handleSystemMediaAction('seekto')` are targeted. Position updates are throttled to two-second buckets. Background behavior remains subject to site/browser constraints.
