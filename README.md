# WebShelf

<p align="center">
  <img src="docs/webshelf-app-icon.png" alt="WebShelf app icon" width="128" height="128">
</p>

<p align="center"><strong>Your personal web space</strong></p>

A compact Android WebView app by [Jacob7179](https://github.com/Jacob7179).

## Features

- Save, edit, remove, and switch between multiple websites.
- Scan with camera (below Add website) reads website QR codes and automatically saves, selects, and opens valid HTTP(S) links. Scanning an existing website selects and opens its saved entry without adding a duplicate. Google Play services is required; the scanner module may need an internet connection on first use.
- Tap Adjust in Sites, touch and hold the ↕ handle, and drag a saved website to a new position. Drop above or below a card to place it; order saves immediately and survives restart. Tap Done to leave Adjust mode.
- The Sites panel separates Current page, Selected home site, and Saved sites. Current-site and selected-home badges are independent. Home returns to the selected saved URL; following links does not change that selection.
- Persistent cookies, localStorage, and IndexedDB, separated by website origin.
- One top toolbar with Home, address/reload, Sites, and a menu for the other actions. The Advanced Function submenu has a Back action to return to the main menu.
- Optional background playback with Android media notification, title/artist/artwork detection, playback controls, and compatible wired/Bluetooth earphone buttons.
- Website microphone access with site permission handling, configurable desktop mode, and website permission reset.
- Portrait/landscape rotation resizes the existing WebView instead of rebuilding it and reloading the document.
- English, Simplified Chinese, Bahasa Melayu, Japanese, and Korean.
- Black-and-white interface with Auto / On / Off dark mode. Default: Off.
- About dialog with the author profile, GitHub link, and installed app version.
- Android 8.0 (API 26) or newer. Internet access is needed for online pages.

## Build on Windows

### Requirements

- JDK 17 or newer (a full JDK, including `javac`, `jar`, and `keytool`).
- Android SDK platform **35** and build-tools **36.1.0**.
- Windows PowerShell 5.1 or newer, included with Windows.

Install the SDK components through Android Studio's SDK Manager. Set `JAVA_HOME` to your JDK and `ANDROID_HOME` to your SDK, or pass their paths explicitly. Java on PATH and the standard `%LOCALAPPDATA%\Android\Sdk` location are also detected.

Run from Command Prompt:

```bat
build.bat
```

Or specify paths:

```bat
build.bat -Sdk "C:\Android\Sdk" -JavaHome "C:\Java\jdk-17"
```

From PowerShell, use `.\build.bat`. An alternative installed build-tools version can be selected with `-BuildTools`.

**Debug output:** `WebShelf-debug.apk` in the project folder. The script uses the bundled Gradle 8.9 wrapper to resolve scanner dependencies, merge Android resources, and build the debug APK. First build requires internet access. Use JDK 17–21 and SDK platform 35. The legacy `-BuildTools` argument remains accepted for compatibility; Gradle selects its supported build tools.

### Signing and app updates

Running `build.bat` without arguments displays an immediate keypress menu (no Enter required):

| Option | Build | Signing |
| --- | --- | --- |
| `1` | Debug APK | Default Android debug key |
| `2` | Release APK | Your own existing or new keystore |

For option **2**, follow the prompts to select your keystore and enter its key alias, keystore password, and key password (press Enter at the last prompt to reuse the keystore password). Alternatively, use `build.bat -CustomSign` or supply `-Keystore` and `-KeyAlias` to the script. **Never commit or share the keystore or passwords.** Keep a secure backup: Android requires the same signing identity to install an update over an existing app.

The build requires a compatible installed JDK and Android SDK. If `:app:compileReleaseJavaWithJavac` fails, check the Java compiler message above the Gradle summary. A signing key cannot correct a source-compilation failure.

### Android Studio / Gradle

Open this folder in Android Studio or run `gradlew.bat assembleDebug lintDebug`. The project uses Android Gradle Plugin 8.7.2 and Java 17 source compatibility. The wrapper now uses compatible Gradle 8.9 without requiring a downloaded JDK 25.

### Feature verification

Run `verify.ps1` with a JDK and Node.js to check existing site matching/storage behavior and scanned URL validation/duplicate handling. On a physical device, verify: valid website QR, duplicate QR, non-website QR, cancellation, unavailable scanner, and first-use module download. In Sites, test dragging upward/downward, scrolling a long list, editing/opening after reorder, and reopening the app to confirm persistence. Camera and drag gestures require device testing.

## Publish to GitHub

Extract the source ZIP and publish the extracted **WebShelf** folder. Generated APKs, build directories, local SDK settings, and signing keys are ignored. No repository has been created or pushed automatically.

For a new local repository:

```text
git init
git add .
git commit -m "Initial WebShelf source"
git branch -M main
git remote add origin https://github.com/Jacob7179/WebShelf.git
git push -u origin main
```

Create the empty GitHub repository first and adjust the remote URL if you choose a different repository name. APKs can be uploaded separately to a GitHub Release.

## Website settings integration

The app never rewrites URL paths, query parameters, or fragments for language selection.

| localStorage key | Values |
| --- | --- |
| `wip-language` | `en`, `zh-cn`, `ms`, `ja`, `ko` |
| `wip-theme-mode` | `auto`, `on`, `off` |

On ngrok subdomains, both keys are written even when missing. On other HTTP(S) sites, each key is updated only if it already exists. `wip-form-design` and unrelated data are preserved.

For the recognized WIP language engine at `/static/language.js`, WebShelf adapts its response copy to respect localStorage and apply languages without URL navigation. This runs inside the app; it does not modify the website server. Unknown script versions pass through. Compatibility depends on that website engine's structure and may need updating when the website changes. Old cached copies of this specific asset are refreshed without clearing offline records or other website data.

Dark website rendering depends on the website, Android version, and installed WebView. The app requests dark styling and permits algorithmic darkening on Android 13+ when dark mode is on.

## Project layout

```text
app/src/main/java/com/webshelf/app/  Android activity and WIP integration
app/src/main/res/                   Translations, themes, icons, profile image
app/build.gradle                   App version and Android SDK configuration
build.bat                          Windows build entry point
build-sdk.ps1                      Direct SDK build and signing
localize.py                        Optional translation-resource generator
verify.bat / verify.ps1             Local integration test runner
tests/                            Storage and language test fixtures
```

Edit `app/build.gradle` to change `versionCode` and `versionName`; both build routes read this file. If using `localize.py`, edit its translation table and run `python localize.py` to regenerate string resources. Python is optional for ordinary builds.

## Validation

Run `verify.bat` to execute storage tests. This requires Java and Node.js, which are not needed together for the APK build. To test the WIP adapter against a downloaded copy of your website's current language script:

```bat
verify.bat -WipScript "C:\path\to\language.js"
```

The adapter tests execute the adapted website script in a small DOM fixture; they are not Android UI tests. The APK has been built and signature-verified, but physical-device visual checks remain necessary.

For manual storage testing, serve `tests/storage.html` from a computer, open it in WebShelf, save a value, switch sites, restart the app, and check the value again. `tests/appearance.html` checks website color preference. Also check navigation, all five languages, dark modes, About, and ngrok language changes on a device.

## Storage and permissions

The app requests permissions for internet/network access, camera, microphone/audio, location, notifications, and foreground media playback as needed by enabled features. Android and per-website consent requirements still apply. JavaScript and DOM storage are enabled. File/content URL access is disabled, and HTTPS pages cannot load mixed insecure content. User-entered HTTP intranet URLs are allowed. Removing a bookmark does not clear website data; uninstalling or clearing app storage does.

Websites requiring uploads, downloads, camera, microphone, location, popups, or external-browser login may have browser- or site-specific limitations.

## Credits

Author: [Jacob7179](https://github.com/Jacob7179). The bundled profile image was supplied from the author's GitHub avatar URL.



## Website camera access (v1.27)
Based on v1.25; cancelled v1.26 language changes are excluded. HTTPS websites can request camera access for getUserMedia scanners. WebShelf asks for site consent and Android camera permission, grants video only, and cancels pending requests on navigation. Use the HTTPS project address. On a device, test Allow, Deny, permission revocation in Android Settings, navigating away during the prompt, and scanning in both the WIP record and quick-login screens.


Version 1.28: Scanning an ngrok quick-login link inserts or replaces its language prefix using the selected app language (en, zh-cn, ms, ja, ko). Query strings and token fragments are preserved. Existing equivalent bookmarks are updated, selected and opened. Other scanned paths remain unchanged.


Version 1.29 supersedes v1.28: remove language prefixes from scanned ngrok quick-login links. Existing prefixed quick-login bookmarks are cleaned and deduplicated on startup. Query strings and token fragments are preserved.


Version 1.30: For origins with a saved ngrok quick-login bookmark, main-frame GET navigation removes leading language prefixes, including the post-login redirect. Cookies and the selected bookmark are preserved. POST requests, subresources and unrelated origins are not rewritten. Verify QR login and subsequent page navigation on a device.


### Optional background playback and media controls (Android)

Open **⋮ → Advanced Function → Background playback** and enable background mode while WebShelf is running. This keeps the WebView active during app switching when supported and starts an Android media-playback foreground service with system media controls. Disable the feature to return to standard WebView pause behavior. The media notification can display a track title, artist/channel, and thumbnail/album artwork when the active website exposes them.

**Media controls**

| Control | Action |
| --- | --- |
| Play / Pause | Resume or pause the website player |
| Previous / Next | Change tracks or videos if supported by the website |
| Rewind / Forward 30 seconds | Seek within supported media |
| Wired / Bluetooth headset button: 1 press | Play / Pause |
| Wired / Bluetooth headset button: 2 presses | Next track |
| Wired / Bluetooth headset button: 3 presses | Previous track |

For multifunction headset presses, the app groups clicks over a **450 ms** interval. Dedicated hardware next/previous buttons still use their corresponding media actions. Some earphones or Bluetooth firmware intercept press patterns before Android receives them, so physical-device behavior may differ.

**Website compatibility:** WebShelf first uses available browser media information and controls, then attempts site-specific/player-button fallbacks. This includes YouTube, YouTube Music, and the [YouTube Music Player Web](https://jacob7179.github.io/YouTube-Music-Player-Web/) site. Metadata may come from Media Session, page elements, or page metadata; if not exposed, artist/artwork can be missing or outdated. Next/Previous requires an available website action, playlist, or player control and may be unavailable for individual videos or cross-origin embedded players. Background playback cannot bypass website restrictions, WebView lifecycle limits, or device battery policies.

**Suggested device checks:** Enable background playback, play a track, open the Android notification panel, verify title/artist/artwork, test next/previous and 30-second seeking, lock the screen, and try single/double/triple presses using both wired and Bluetooth earphones. Also test YouTube and a normal HTML5 audio site. These features require real-device verification; a successful APK build alone does not confirm headset or website support.

### AdBlock (basic)

In **Advanced Function**, enable **AdBlock (basic)** to block requests to a conservative list of common ad and tracking domains. It is disabled by default and its setting is saved between launches. Toggle it off if a website has trouble loading. This is not a full ad blocker: embedded or first-party ads (including some YouTube advertisements) may still appear. No external filter-list downloads or VPN are used.


### AdBlock: YouTube compatibility (experimental)

When **AdBlock (Basic)** is enabled in the second menu, WebShelf additionally blocks a small set of YouTube advertising/tracking endpoints and injects a YouTube-specific page filter that hides some overlay advertisements and attempts to activate a skip control when one becomes available. This is **not** the full AdGuard engine or an integration with the AdGuard application. The blocker does not inspect/decrypt HTTPS video streams, and **cannot guarantee ad-free 5-second or 30-second YouTube playback**. If playback breaks, disable AdBlock and reload.

AdGuard-style filtering involves maintained network, cosmetic, and script rules; WebShelf currently uses only a narrow subset, not the full AdGuard filter-list syntax.


### Battery optimization and advanced-menu options

Open **⋮ → Advanced Function** to access the optional background playback, AdBlock, and battery optimization controls. **Disable battery optimization** is a **checkable menu item**: a checkmark means Android currently exempts WebShelf from battery optimization; no checkmark means it does not. Tap it to open Android’s battery optimization permission prompt or settings. Android must approve the change: the app does not silently change system settings. Reopen the menu to refresh the checkmark based on the actual system state. On devices or Android versions where the exemption is unavailable, WebShelf displays a message or opens the available battery settings.

Battery optimization exemption can reduce interruptions to background playback but does not guarantee that websites, YouTube playback, or WebView media will keep running when the app is in the background. Android device manufacturers may impose additional restrictions.

**Native Audio Player** is intentionally **hidden from the Advanced Function menu**, without removing its implementation. In `app/src/main/java/com/webshelf/app/MainActivity.java`, the original menu registration is kept as a comment:

```java
//menu.getMenu().add(0,11,5,getString(R.string.native_audio_title));
```

Remove the `//` to show that menu entry again, then rebuild the APK. This affects menu visibility only; it does not uninstall or disable the underlying media service.

**Manual verification:** Check the battery menu item before granting the exemption, enable it through the Android system prompt, reopen the menu and confirm the tick appears. Restore battery optimization in Android Settings and verify that the tick clears when the menu is reopened. Confirm Native Audio Player is absent from the menu while AdBlock and background playback remain available.
