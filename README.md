# WebShelf

A compact Android WebView app by [Jacob7179](https://github.com/Jacob7179).

## Features

- Save, edit, remove, and switch between multiple websites.
- The Sites panel separates Current page, Selected home site, and Saved sites. Current-site and selected-home badges are independent. Home returns to the selected saved URL; following links does not change that selection.
- Persistent cookies, localStorage, and IndexedDB, separated by website origin.
- One top toolbar with Home, address/reload, Sites, and a menu for the other actions.
- English, Simplified Chinese, and Bahasa Melayu.
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

**Output:** `WebShelf-debug.apk` in the project folder. The script compiles resources and Java, creates DEX files, aligns the APK, signs it, and verifies the signature. It does not require Gradle, Python, Node.js, or network downloads once the SDK and JDK are installed. Failures return a nonzero exit code.

### Signing and app updates

The SDK build generates a local debug key at `build/sdk/debug.keystore` on its first run and reuses it thereafter. Keep a private backup to install future APKs as updates without uninstalling and losing app data. The key is excluded from the source package and Git.

A fresh clone generates a different debug key. Its APK cannot update an existing installation signed with another key. The supplied build is for testing/direct installation; use your own release signing configuration for production distribution.

### Android Studio / Gradle

Open this folder in Android Studio. The project uses Android Gradle Plugin 8.7.2, compile/target SDK 35, and Java 17 source compatibility. With Gradle 8.11.1 installed:

```text
gradle assembleDebug lintDebug
```

There is no bundled Gradle wrapper. The Windows `build.bat` route is the independently tested build path. Android Studio/Gradle normally uses a different debug key from the SDK script.

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
| `wip-language` | `en`, `zh-cn`, `ms` |
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

For manual storage testing, serve `tests/storage.html` from a computer, open it in WebShelf, save a value, switch sites, restart the app, and check the value again. `tests/appearance.html` checks website color preference. Also check navigation, all three languages, dark modes, About, and ngrok language changes on a device.

## Storage and permissions

Only INTERNET and ACCESS_NETWORK_STATE permissions are declared. JavaScript and DOM storage are enabled. File/content URL access is disabled, and HTTPS pages cannot load mixed insecure content. User-entered HTTP intranet URLs are allowed. Removing a bookmark does not clear website data; uninstalling or clearing app storage does.

Websites requiring uploads, downloads, camera, location, popups, or external-browser login may require additional integration.

## Credits

Author: [Jacob7179](https://github.com/Jacob7179). The bundled profile image was supplied from the author's GitHub avatar URL.
