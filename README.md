# «سهرة وتسالي» app (Android + iOS)

A Capacitor shell around the live game at https://sahrah.geddo.online, built in the cloud by **Codemagic** exactly like
PULSE (`F:\FIT_IT`). Every web deploy updates the app; the stores only see a new build when the shell itself changes.

- App id / bundle id: `online.geddo.sahrah`
- The shell adds «SahrahApp/1.1» to the user agent (1.0 before); the web app uses it to hide «install» prompts inside the app.
- Plugins the web page calls through `window.Capacitor.Plugins`: App (deep link `online.geddo.sahrah://auth` back from
  sign-in), Browser (sign-in in a Custom Tab), Share + Filesystem (share sheet), AdMob. Mic/vibrate permissions since 1.1.
  What changed in 1.1 and how to release it: `RELEASE-1.1.md`.
- `www/offline.html` is the «مفيش إنترنت» screen; `assets/` holds the icon/splash sources (`npm run assets` regenerates).

## Release
- iOS → TestFlight: push a tag `ios-v1.0.0` (workflow `ios-testflight`).
- Android → `.aab`: push a tag `android-v1.0.0` (workflow `android-release`), then upload the bundle in Play Console.

## One-time setup
1. Codemagic: add this repo to the same team as PULSE.
2. iOS: create the app in App Store Connect (bundle `online.geddo.sahrah`, name «سهرة وتسالي»). Put its Apple ID in
   `APP_APPLE_ID` in `codemagic.yaml`. The `appstore-key` integration and automatic signing are the same as PULSE.
3. Android: run the `android-keystore` workflow once. Keep the keystore and its passwords safe, outside git. Upload it in
   Codemagic → Code signing identities → Android keystores as `sahrah`. Create the app in Play Console and upload
   the first `.aab` by hand (Google's rule); later uploads can be automatic.
4. Android app links (optional, opens our links in the app): add `/.well-known/assetlinks.json` on the site with the
   keystore's SHA-256 fingerprint.

## Store review notes
Apple's guideline 4.2 rejects apps that are "just a website". This shell has a native splash/icon, offline screen, status
bar and back handling. If Apple asks for more, the next steps are native push notifications (APNs) and haptics, both through
Capacitor plugins.
