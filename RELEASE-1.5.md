# Release 1.5 (Android): Google Play in-app rating

What 1.5 adds, compared with 1.4 (1.3: App Links; 1.4: text zoom capped at 110%, user agent fixed):

- **Google Play's own rating card inside the app** (In-App Review API) through `@capacitor-community/in-app-review` 8.0.0
  (Capacitor 8; Android uses `com.google.android.play:review` 2.0.2). The web page reaches it as
  `window.Capacitor.Plugins.InAppReview.requestReview()`.
- User agent `SahrahApp/1.5`, versionName `1.5` (versionCode = Codemagic's BUILD_NUMBER).
- Nothing else changes in the shell. `npx cap sync android` was run on the laptop (the gradle files list the new plugin);
  Codemagic runs `npm ci` + `npx cap sync android` again before the build.

## When the rating card appears (web side: seenjeem-egypt `apps/web/src/lib/rating.ts`)
Only inside app 1.5+ (by the user agent), with admin «المفاتيح» `app.native` ON and `app.rating` ON (new, default ON), after a
happy moment (a team win, a won 1 ضد 1 / board match, a 3+ day streak, finishing «سهرة الليلة», the crown), never at load,
never within 60 s of an ad, only after the app was opened on 3+ different days, at most once every 45 days per phone.
Google decides on its own whether the card really shows (quota): often nothing appears, and that is normal. In a sideloaded
build (not from Play) the card never shows; use Play's internal app sharing or the internal track to see it.
No question before it, no reward, never «5 stars» (Play policy). App 1.4 and older: the call is a no-op.

## Steps (owner)
1. Deploy the web side first (it does nothing in 1.4 and older, or on the website).
2. Push this repo and the tag: `git push origin main` and `git tag android-v1.5 && git push origin android-v1.5`.
3. Codemagic `android-release` builds the `.aab`; upload it in Play Console (internal → production).
4. When 1.5 is live on Play, raise `LATEST` in seenjeem-egypt `components/AppUpdate.tsx` (still `[1, 2]`) so older apps see
   «فيه تحديث جديد».
