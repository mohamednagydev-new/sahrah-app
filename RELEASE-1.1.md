# Release 1.1 (Android first)

What 1.1 fixes, compared with 1.0 (live on Google Play as `online.geddo.sahrah`):

- **Voice works.** 1.0 had only the INTERNET permission, so the mic was always refused. 1.1 declares `RECORD_AUDIO` +
  `MODIFY_AUDIO_SETTINGS` (and `VIBRATE`). The first tap on the mic shows Android's «Allow» popup (Capacitor's
  BridgeWebChromeClient asks for the runtime permission when the page calls getUserMedia). No code in MainActivity is needed.
- **Sharing works.** The Android web view has no `navigator.share`; 1.1 ships `@capacitor/share` (+ `@capacitor/filesystem`
  for sharing the result pictures). The website uses them by itself inside the app.
- **Sign-in works.** Google refuses sign-in inside a web view and Facebook doesn't support it. In 1.1 the buttons open the
  sign-in in a Chrome Custom Tab (`@capacitor/browser`); when it's done the site sends the player back to the app through the
  link `online.geddo.sahrah://auth?...` (intent filter in AndroidManifest, URL type in iOS Info.plist) and the app is signed in.
- User agent `SahrahApp/1.1`, versionName `1.1` (versionCode = Codemagic's BUILD_NUMBER, always higher).

## Before the app: deploy the web

The web side of 1.1 (sign-in hand-off `/api/auth/app`, `app=1` in the Google/Facebook/email sign-in, the share helper) must
be live on sahrah.geddo.online **before** 1.1 reaches phones. It is safe for 1.0 and for the website (they never send `app=1`).
After that deploy, app 1.0 shows «سجّل دخول من التحديث الجاي للتطبيق، أو من المتصفح» instead of sign-in buttons that fail.

## Steps (owner)

1. Merge the branch `app-1.1` into `main` and push it to GitHub (`mohamednagydev-new/sahrah-app`).
2. In Codemagic, run the workflow **«سهرة Android → Play bundle (.aab)»** (`android-release`): push a tag `android-v1.1.0`
   (or start it by hand on `main`). It runs `npm ci`, `npx cap sync android` and builds the signed `.aab`.
3. Download `app-release.aab` from the build's artifacts.
4. Play Console → «سهرة وتسالي» → **Test and release → Production → Create new release** → upload the `.aab`.
   (Optional but safer: first to **Internal testing**, try voice, share and Google sign-in on a real phone, then promote.)
5. Release name: `1.1`. Release notes (ar):

   ```
   <ar>
   • المايك اشتغل: اتكلم بصوتك مع صحابك في الغرف و1 ضد 1
   • شارك نتيجتك وصورة السؤال مع صحابك على طول
   • تسجيل الدخول بجوجل وفيسبوك والإيميل بقى شغال جوه التطبيق
   </ar>
   ```
6. **App content → Data safety** (update before or with the release, else review can stop it):
   - Data types → **Audio → Voice or sound recordings**. Voice leaves the phone (streamed live through our LiveKit server to
     the other players), so Google counts it as collected even though nothing is stored:
     - Collected: **Yes**; Shared: **No** (it reaches only the players in the same game, as the feature the user turned on,
       through our own server; that is not "sharing with a third party" in Google's definition);
     - Processed **ephemerally**: **Yes** (streamed live, never recorded or stored);
     - Required or optional: **Optional** (the game works without the mic);
     - Purpose: **App functionality**.
   - Nothing else changes (sign-in already declared name/email for accounts in 1.0; if it wasn't, add **Personal info →
     Name, Email address**: collected, not shared, purpose App functionality + Account management).
   - Encryption in transit: Yes. Deletion request: yes (account deletion in «حسابي»).
7. **Permissions:** `RECORD_AUDIO` is a normal runtime («dangerous») permission. Google Play has **no special declaration form**
   for it (the forms are for SMS/Call log, location in background, all-files access, etc.). The Data safety answer above
   covers it. `MODIFY_AUDIO_SETTINGS` and `VIBRATE` are normal permissions with nothing to declare.
8. Send for review. When it's live, test on a phone: Google sign-in → comes back signed in; a mic tap asks «Allow»;
   «شارك» opens Android's share sheet.

## iPhone (later, when the iOS app goes to TestFlight)

Info.plist already has the Arabic `NSMicrophoneUsageDescription` and the `online.geddo.sahrah` URL type. Push a tag
`ios-v1.1.0` (workflow `ios-testflight`). `npx cap sync ios` in Codemagic adds the new plugins to the Swift package.

## Notes for builders

- The Android files `android/capacitor.settings.gradle`, `android/app/capacitor.build.gradle` and
  `ios/App/CapApp-SPM/Package.swift` were updated by hand to what `npx cap sync` writes for the 3 new plugins; Codemagic runs
  `cap sync` again anyway.
- The sign-in hand-off tokens: one-time, 2 minutes, hashed in the server DB (`app_auth_tokens`), bound to a random nonce that
  stays on the phone (localStorage) and is never in the deep link. See the web repo `apps/web/src/lib/signin.ts` and
  `apps/web/src/lib/appSignIn.ts`.
