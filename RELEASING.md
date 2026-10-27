# Releasing OmniAI-OS

## 1. Create a signing key (once)
```bash
keytool -genkeypair -v -keystore release.jks -alias omniai \
  -keyalg RSA -keysize 2048 -validity 10000
```
**Back up `release.jks` and its passwords somewhere safe.** If you lose the key you can never
update the app for existing users (and Google Play will not accept updates signed with another key).

## 2. Build locally
Copy `keystore.properties.example` to `keystore.properties`, fill it in, then either use
Android Studio (**Build → Generate Signed App Bundle / APK**) or run:

```bash
./gradlew assembleRelease   # signed APK  → app/build/outputs/apk/release/
./gradlew bundleRelease     # signed AAB  → app/build/outputs/bundle/release/ (for Google Play)
```
(If `gradlew` is missing, run `gradle wrapper` once or open the project in Android Studio.)

Test the release APK on a real phone before publishing; minification (R8) can hide problems
that debug builds do not show. If something crashes, check `app/proguard-rules.pro`.

## 3. Build on GitHub (automatic)
1. Encode the key: `base64 -w0 release.jks` (macOS: `base64 -i release.jks`).
2. In the repo go to **Settings → Secrets and variables → Actions** and add:
   `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
3. Bump `versionCode` / `versionName` in `app/build.gradle.kts`, commit, then tag:
   ```bash
   git tag v0.6.0 && git push origin v0.6.0
   ```
   The **Release** workflow builds a signed APK + AAB and attaches them to a GitHub Release.

## Notes
- Every release needs a higher `versionCode` than the previous one.
- Keep `mapping.txt` (uploaded as a build artifact) to de-obfuscate crash reports.
- The app stores the user's API keys on the device only; `allowBackup` is off so they are not copied to cloud backups.
