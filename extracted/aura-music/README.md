# AURA MUSIC — Phases 1 and 2 (audited)

Status: **never compiled or run on a device.** Everything below is the intended procedure.

## Requirements
Node 18+, **JDK 17** (Android Studio's bundled JBR 17 is fine; Gradle 8.2 in Capacitor 6 does not run on JDK 21), Android SDK 34, a phone with USB debugging.

## Build (copy and paste, from the project folder)

macOS / Linux:
```
npm install
npx cap add android
sh ./install-native.sh
npx cap sync android
cd android && ./gradlew assembleDebug
```
Windows (PowerShell):
```
npm install
npx cap add android
node scripts/install-native.js
npx cap sync android
cd android; .\gradlew.bat assembleDebug
```
APK: `android/app/build/outputs/apk/debug/app-debug.apk`. Install: `adb install -r <that file>`, or run `npx cap open android` and press Run.

## About the installer
- It **patches** Capacitor's generated files and never replaces the manifest, so FileProvider, INTERNET and theme entries stay.
- Originals are saved once as `*.aura.bak`; running it again changes nothing.
- It stops with a clear message if `android/` is missing, the app id is not `com.aura.music`, or a file has an unexpected layout (manual steps: `native/gradle-additions.txt`).
- If `android/` is broken, delete it, then rerun the commands above from `npx cap add android`.

## Test checklist (all UNVERIFIED until you run it)
1. First launch: "Allow access" screen -> system dialog -> "Scanning" -> track list.
2. Deny, then enable in Settings and return: the library rescans by itself.
3. Tap a track: audio plays. Pause/Play, Next and the seek slider work.
4. Press Home and lock the screen: music keeps playing; notification (Android 13+: allow the notification prompt) and lock-screen controls appear.
5. Scan again: no duplicate rows. A phone with no music shows "No music found".

Send build errors and logcat (`adb logcat | grep -i aura`) verbatim.
