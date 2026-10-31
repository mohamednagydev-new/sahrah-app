# Release 1.2 (Android first): phone notifications

What 1.2 adds, compared with 1.1:

- **Notifications on the phone** through Firebase Cloud Messaging (`@capacitor/push-notifications` 8.1.3, Firebase project
  `sahrah-3cea2`). The same messages the website sends by web push reach the app: «⚡ صاحبك دخل! ارجع نبدأ», a reply to a 1v1
  challenge, a friend's invite or chat, the evening «تحدي النهارده», the weekly challenge, and what admin sends from «الإشعارات».
  A tap on a notification opens its page in the app.
- Android 13+: the «Allow notifications» popup (`POST_NOTIFICATIONS`) shows **only when the player taps «فعّل»** for
  notifications (after a game, or the switch in «حسابي»). Nothing is asked when the app opens.
- Status-bar icon: a white «?» speech bubble (`res/drawable/ic_stat_sahrah.xml`), gold accent `#FFC93C`, channel `default`.
- User agent `SahrahApp/1.2`, versionName `1.2` (versionCode = Codemagic's BUILD_NUMBER).

## Before the app: deploy the web, and add the server key

1. The web side of 1.2 (`/api/push` `subscribe_fcm`, `lib/appPush.ts`, FCM sending in `packages/db/src/fcm.ts`) must be live on
   sahrah.geddo.online. It changes nothing for the website or for app 1.0/1.1 (they never get the native path).
2. Server `.env` (`/srv/sahrah/app/.env`): add **`FIREBASE_SA_B64`** = the service-account JSON (Firebase console → Project
   settings → Service accounts → Generate new private key), base64 on one line. From Git Bash on the laptop, without printing it:
   `base64 -w0 .firebase-sa.json` piped into the server's .env, e.g.
   `printf 'FIREBASE_SA_B64=%s\n' "$(base64 -w0 .firebase-sa.json)" | ssh -i "$HOME/.ssh/sahrah_deploy" root@164.90.237.178 'cat >> /srv/sahrah/app/.env'`.
   Then recreate **web, web2, web3 and rooms** (the rooms server sends «صاحبك دخل», 1v1 replies, the weekly and evening pushes).
   Without the key, app notifications are simply off (admin «الإشعارات» says so); nothing breaks.
3. The native path runs only with the admin switch **`app.native`** on («المفاتيح» → «تطبيق Google Play»), like 1.1.

## google-services.json must be in git

Codemagic builds from the GitHub repo. `android/app/google-services.json` is on the laptop but **not committed**, and
`android/app/build.gradle` applies the Google services plugin only when the file is there: without it the build succeeds but
**no notification ever arrives**. Commit it:

```
git add android/app/google-services.json
```

Firebase says this file is **not a secret** (it holds public identifiers: project id, app id, an API key that is restricted to
the app); it ships inside every APK anyway. Do **not** commit the service-account key (`.firebase-sa.json`): that one IS secret
and lives only in the web repo folder (gitignored) and in the server `.env`.

## Steps (owner)

1. Apply `app-1.2.patch` (in the web repo branch) on `main` of `sahrah-app`: `patch -p1 < app-1.2.patch`. Then
   `npm install` once on the laptop (only to fill `node_modules`; the lock already lists the plugin, so Codemagic's `npm ci`
   works either way). Commit together with `android/app/google-services.json`, push to GitHub.
2. Codemagic: run **«سهرة Android → Play bundle (.aab)»** (`android-release`), tag `android-v1.2.0` or start by hand on `main`.
3. Download `app-release.aab` from the build's artifacts.
4. Play Console → «سهرة وتسالي» → **Test and release** → first **Internal testing** (recommended: check a notification on a
   real phone), then **Production → Create new release** → upload the `.aab`.
5. Release name: `1.2`. Release notes (ar):

   ```
   <ar>
   • الإشعارات وصلت التطبيق: اعرف أول ما صاحبك يدخل الماتش أو يرد على تحديك
   • تذكير بتحدي النهارده وتحدي الأسبوع، وتقدر تقفلها من «حسابي» في أي وقت
   • تحسينات وإصلاحات
   </ar>
   ```
6. **App content → Data safety:**
   - The push token is a **device identifier**. «Device or other IDs» is already declared (AdMob): make sure its purposes include
     **App functionality** (sending the notifications the player turned on), collected **Yes**, shared **No** (it goes to
     Google's Firebase only as our service provider, which Google doesn't count as sharing), required **Optional**.
   - Nothing else changes.
7. Send for review.

## Test on a phone (after release, or on Internal testing)

1. `app.native` ON in admin. Open the app 1.2 → play a game → «تحب نفكّرك بالجديد؟» → «فعّل» → Android asks «Allow» → allow.
   (Or «حسابي» → settings → «الإشعارات 🔔» switch.)
2. Admin → «الإشعارات»: «مشتركين … منهم 1 على التطبيق». Send a test to everyone (or ask a friend to join a 1v1 link while
   the app is in the background): the notification arrives with the «?» icon; a tap opens the page.
3. If anything misbehaves: switch `app.native` OFF (next page load), nothing else needed.

## iPhone (later)

The Swift package already lists `CapacitorPushNotifications`. iOS also needs: the **Push Notifications** capability +
`aps-environment` entitlement in Xcode, an **APNs key** (.p8) uploaded to Firebase (Project settings → Cloud Messaging), the
`GoogleService-Info.plist`, and the two `AppDelegate` forwarding methods from the Capacitor push docs. Not in this patch.

## Notes for builders

- `android/capacitor.settings.gradle`, `android/app/capacitor.build.gradle` and `ios/App/CapApp-SPM/Package.swift` were edited
  by hand to what `npx cap sync` writes for the new plugin; Codemagic runs `cap sync` again anyway.
- `package-lock.json` has the plugin entry by hand (version, tarball URL and sha512 from the npm registry; checked against the
  downloaded tarball). If in doubt: `npm install @capacitor/push-notifications@8.1.3 --save` regenerates the same thing.
- The plugin's manifest does not declare `POST_NOTIFICATIONS`; we declare it ourselves. Firebase reads the default icon /
  colour / channel from the `meta-data` in `AndroidManifest.xml`.
- The web page never touches the native bridge at load: the permission ask, `register()` and the listeners happen only inside
  taps (web repo `apps/web/src/lib/appPush.ts`). A notification tap that opened the app is delivered on the first tap of the
  page (Capacitor keeps it until a listener is added).
