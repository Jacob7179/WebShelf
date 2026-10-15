# Native Android Media3 playback (v10)

- **Settings → Native audio player** accepts a *direct HTTPS audio/media playlist URL*, optional title, artist, and artwork. Tap **Play** to start Media3 ExoPlayer in a `MediaSessionService` that survives activity backgrounding.
- Android notification, lock screen, headphones and system media controls are handled by Media3 independently of WebView. Playback remains subject to device power policy, media source availability, network access and user controls.
- Supports streams that ExoPlayer can decode, such as MP3, AAC and supported HLS. Links to webpages, YouTube watch/embed pages, DRM streams, or streams needing authentication headers are **not** playable by simply pasting a URL. No extraction or bypass logic.
- **Stop service** stops the player. The existing **Background playback** toggle remains the separate WebView mode. Starting native audio stops the old WebView media notification service to avoid duplicate sessions. The native player does not require the WebView background toggle.
- The native player does not automatically discover hidden media URLs. The user must provide a permitted direct URL.
- Media3 dependencies require Google Maven and online Gradle dependency resolution on first build.
- No Android build/device test performed here.
