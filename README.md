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
- One top toolbar with Home, address/reload, Sites, and a menu for the other actions.
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

**Output:** `WebShelf-debug.apk` in the project folder. The script uses the bundled Gradle 8.9 wrapper to resolve scanner dependencies, merge Android resources, and build the debug APK. First build requires internet access. Use JDK 17–21 and SDK platform 35. The legacy `-BuildTools` argument remains accepted for compatibility; Gradle selects its supported build tools.

### Signing and app updates

Gradle uses the standard Android debug signing key. To update an existing installation, build with the same signing key as that installation. A differently signed APK cannot update it. No existing signing key is included in the source ZIP.

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

Only INTERNET and ACCESS_NETWORK_STATE permissions are declared. JavaScript and DOM storage are enabled. File/content URL access is disabled, and HTTPS pages cannot load mixed insecure content. User-entered HTTP intranet URLs are allowed. Removing a bookmark does not clear website data; uninstalling or clearing app storage does.

Websites requiring uploads, downloads, camera, location, popups, or external-browser login may require additional integration.

## Credits

Author: [Jacob7179](https://github.com/Jacob7179). The bundled profile image was supplied from the author's GitHub avatar URL.



## Website camera access (v1.27)
Based on v1.25; cancelled v1.26 language changes are excluded. HTTPS websites can request camera access for getUserMedia scanners. WebShelf asks for site consent and Android camera permission, grants video only, and cancels pending requests on navigation. Use the HTTPS project address. On a device, test Allow, Deny, permission revocation in Android Settings, navigating away during the prompt, and scanning in both the WIP record and quick-login screens.


Version 1.28: Scanning an ngrok quick-login link inserts or replaces its language prefix using the selected app language (en, zh-cn, ms, ja, ko). Query strings and token fragments are preserved. Existing equivalent bookmarks are updated, selected and opened. Other scanned paths remain unchanged.


Version 1.29 supersedes v1.28: remove language prefixes from scanned ngrok quick-login links. Existing prefixed quick-login bookmarks are cleaned and deduplicated on startup. Query strings and token fragments are preserved.


Version 1.30: For origins with a saved ngrok quick-login bookmark, main-frame GET navigation removes leading language prefixes, including the post-login redirect. Cookies and the selected bookmark are preserved. POST requests, subresources and unrelated origins are not rewritten. Verify QR login and subsequent page navigation on a device.

