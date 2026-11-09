# Release 1.6 (Android): Facebook SDK for the Meta install campaign

What 1.6 adds, compared with 1.5:

- **The Facebook SDK (core only: `com.facebook.android:facebook-core:17.0.0`)**, added natively. No plugin, no login, no UI and
  **no JavaScript touches it** (the 31 Oct lesson: nothing native at page load). It starts by itself and logs the install and every
  app open (`AutoLogAppEventsEnabled`), so Meta's campaign SAHRAH-APP-INSTALLS (App Promotion, optimised for installs) sees who
  installed. Meta app «سهرة وتسالي» id `1909940596360916` (already linked to Google Play `online.geddo.sahrah`).
- The client token is NOT in the repo: Codemagic injects it from the secure variable `FB_CLIENT_TOKEN` (group `facebook`);
  the build stops if it is missing (an app without it would crash at start).
- User agent `SahrahApp/1.6`, versionName `1.6` (versionCode = Codemagic's BUILD_NUMBER).

## Steps (owner)
1. developers.facebook.com → app «سهرة وتسالي» → **App settings → Advanced → Security → Client token**: copy it.
2. Codemagic → this app → **Environment variables**: add `FB_CLIENT_TOKEN` = the token, **Secure** ✓, group **`facebook`**.
3. Push this repo and the tag: `git push origin app-1.6` (or merge into main and push), then
   `git tag android-v1.6 && git push origin android-v1.6`.
4. Codemagic `android-release` builds the `.aab`; upload it in Play Console → **Internal testing** first, open it on a phone
   (it must open normally), then Production.
5. Play Console → App content → **Advertising ID**: already declared «yes» for AdMob; keep it (the SDK also uses it).
6. In Meta Events Manager → the app → you should see «App Install» / «App Activate» events within an hour of opening 1.6.
7. When 1.6 is live on Play, raise `LATEST` in seenjeem-egypt `components/AppUpdate.tsx` to `[1, 6]`.

Release notes for Play: «تحسينات في الأداء وتجربة أسرع».
