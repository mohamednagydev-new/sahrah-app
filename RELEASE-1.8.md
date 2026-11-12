# Release 1.8 (Android): the mic works on more phones, and the page can open the app's settings

Why: the web team's mic investigation (seenjeem-egypt 72bcc5d3, 12 Nov 2026): 98 of 116 phones using voice are in the app, and on
1.7 10 of 25 phones failed the mic. Part of it was the web (fixed there: one ask per tap). The shell's part:
- Capacitor's `BridgeWebChromeClient` keeps ONE pending permission listener. When two `getUserMedia` calls overlapped, the second
  replaced the first, so the first web request was never answered (the page waited forever), and Android's empty answer for the
  overlapping popup looped over zero entries and counted as "granted" (the page was granted with RECORD_AUDIO still refused).
- The page could not tell "Android will ask again" from "Android won't ask any more"; it guessed from how fast the refusal came.

What 1.8 changes, compared with 1.7:

1. **Mic requests are queued, each answered exactly once** (`MainActivity.MicOnlyChromeClient`; no longer calls Capacitor's
   `onPermissionRequest`):
   - only a request for exactly `RESOURCE_AUDIO_CAPTURE` can ever be granted; everything else is denied (as in 1.7);
   - RECORD_AUDIO already granted → granted at once, no popup;
   - otherwise the request waits; only ONE Android popup at a time (its own `RequestPermission` launcher), and its answer goes
     to every waiting request. A popup with no answer after 60 s stops blocking a new one;
   - "granted" = `checkSelfPermission(RECORD_AUDIO)` after Android answers, never the result map;
   - requests are taken off the queue before being answered (never twice); one the web view cancels is dropped;
   - only `RESOURCE_AUDIO_CAPTURE` is granted (not the request's whole resource list).
2. **`SahrahNative` plugin** (`SahrahNativePlugin.java`, registered in `MainActivity.onCreate` before the bridge starts) for the
   web's mic help, called by the web ONLY from a tap:
   - `micState()` → `{ state: 'granted' | 'prompt' | 'denied_forever' }`, no popup (`MicPermission.java`). Android has no direct
     "won't ask any more" API, so each refused popup is recorded right after Android answers (before the page hears it):
     rationale true→false = the final refusal; refused under 0.7 s with no rationale = no popup showed (refused for good
     earlier, e.g. in 1.7); a slow refusal with no rationale before or after = the first popup was dismissed (still 'prompt').
     The flag clears when the mic is seen granted or the rationale is true again;
   - `openAppSettings()` → `{ opened: boolean }`: Android's «App info» screen for the app (`ACTION_APPLICATION_DETAILS_SETTINGS`),
     falling back to the phone's main settings. Back in the app, the page sees `visibilitychange` and tries the mic again by
     itself (web code from 12 Nov).
   A Capacitor plugin (not `addJavascriptInterface`), so only the app's own allowed pages reach it.
3. R8: explicit keep rules for `SahrahNativePlugin` (+ its two `@PluginMethod`s), `MicPermission` and `MicOnlyChromeClient`
   (the whole `online.geddo.sahrah.**` package was already kept).
4. User agent `SahrahApp/1.8`, versionName `1.8`, versionCode = max(111, Codemagic BUILD_NUMBER + 100).

Web side (seenjeem-egypt master 6e715fee, deployed separately): with app ≥ 1.8, «app.native» on and the plugin present, a tap's
mic refusal asks `micState()` to choose the help sheet (denied_forever → «افتح المايك» with a gold «⚙️ افتح الإعدادات»;
prompt → the «اسمح بالمايك» sheet; granted → a short «دوس عليه تاني»). Older apps keep the timing guess and the steps.
`voice_mic_failed` carries `ns` (the native state) when known, to compare 1.7 and 1.8 in admin analytics.

Not changed: everything else from 1.7 (R8, install source, renderer recovery, FCM waiting for register(), camera removed).

## Not compiled locally
Still no Android SDK / JDK 17 on the laptop (no `java` at all on PATH), so 1.8 compiles for the first time on Codemagic. Watch
`Build signed release bundle`. The Java uses only APIs already in the project (AndroidX activity-result + core, Capacitor 8
`Plugin` / `registerPlugin`).

## Steps (owner)
1. Review, then push the branch and the tag (only when you decide to publish):
   `git push origin app-1.8` then `git tag android-v1.8 app-1.8 && git push origin android-v1.8`
2. Codemagic `android-release` builds the `.aab` (check the versionCode in the log is 111 or higher).
3. Play Console → Testing → **Internal testing** → Create release → upload the `.aab` → notes below → roll out.
4. On a real phone from the internal track, a fresh install (or: App info → Permissions → Microphone → «Don't allow» first):
   - room / 1 ضد 1: Android asks once, «Allow» → voice works;
   - «Don't allow» twice → the next tap on the mic shows «افتح المايك» with «⚙️ افتح الإعدادات» → it opens the app's settings →
     allow the mic → back in the game the mic opens by itself;
   - plus the 1.7 checks: the app opens, sign-in in the Custom Tab and back, «فعّل» notifications + a test push, share, an ad,
     an `https://sahrah.geddo.online/...` link opens inside the app.
5. Production → Create release → add from library (the same build) → staged rollout 20% → watch Android vitals and
   `voice_mic_failed` (by `app`, `ns`) 2–3 days → 100%.
6. When 1.8 is live, raise `LATEST` in seenjeem-egypt `components/AppUpdate.tsx` to `[1, 8]`.

## Release notes for Play (ar)
Copy from `RELEASE-NOTES-1.8-ar.txt` (each line starts with an invisible right-to-left mark, so it pastes right-to-left):
```
• المايك في الغرف و«1 ضد 1» بقى يشتغل على موبايلات أكتر
• لو المايك مقفول على التطبيق، زرار واحد بيفتح إعدادات التطبيق على طول
• تحسينات في الثبات
```
