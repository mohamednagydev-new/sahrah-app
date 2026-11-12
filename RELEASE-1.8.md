# Release 1.8 (Android): the mic works on more phones, the page can open the app's settings, the app opens faster

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
5. **The Facebook SDK starts after the first page, off the main thread** (`FacebookStart.java`, manifest, 13 Nov 2026).
   Why: Play Console shows startup ANRs since 1.6, the version that added the SDK (`[linker64] __dl_do_dl_iterate_phdr`,
   `__dl_dlsym_handle_lookup_impl`, `Input dispatching timed out (No focused window)`, plus GPU ones); 1.6 bad-behaviour
   rate 2.00% vs 0.63% on 1.5. The SDK (facebook-core 17.0.0) started itself in its `FacebookInitProvider`, a ContentProvider
   that runs `FacebookSdk.sdkInitialize` on the MAIN thread at process start, before the app's window exists (SDK class
   loading, manifest + shared-preferences disk reads, lifecycle tracking, feature checks).
   - Manifest: `AutoInitEnabled=false`, `AutoLogAppEventsEnabled=false` (app id, client token, advertiser id unchanged) and
     the SDK's `com.facebook.internal.FacebookInitProvider` removed (`tools:node="remove"`). Its two token broadcast
     receivers stay (they only react to login token changes; the app has no Facebook login).
   - `FacebookStart.startOnce`: the first of (the web view's first page finished, 3 s after `MainActivity.onCreate`), once per
     process, on a background-priority thread: `FacebookSdk.sdkInitialize(app)` → `setAutoInitEnabled(true)` (=
     `fullyInitialize()`) → `setAutoLogAppEventsEnabled(true)` (registers the SDK's activity-lifecycle tracker) →
     `AppEventsLogger.activateApp(app)` (Meta's documented call when auto-logging is off: sends the install ping, starts
     tracking). Any failure is only logged; the game never needs the SDK.
   - Install + app open still reach Meta (the campaign's attribution):
     - install: `activateApp` → `FacebookSdk.publishInstallAsync` → one `MOBILE_APP_INSTALL` per install (the SDK keeps a
       "ping" timestamp in its prefs; sent again only if it never succeeded). Meta attributes it by advertiser id / anon id
       and its own Play referrer data, which do not depend on how early in the launch it is sent. A phone that kills the app
       within ~3 s of the first launch sends it on the next launch (events are not lost, only later).
     - app open (`fb_mobile_activate_app`): the tracker registers after MainActivity already resumed, so on the main thread
       `FacebookStart.replayMissedResume` hands it the missed `onActivityCreated` + `onActivityResumed`
       (`ActivityLifecycleTracker`, public in 17.0.0 "to allow unity sdk to correctly log app events") when MainActivity is
       on screen and the tracker has not seen it: the same code the lifecycle callback runs, so this launch's app open is
       logged once and the later pause/stop balance it. Later resumes/backgrounding are tracked normally.
     - `setAutoInitEnabled` / `setAutoLogAppEventsEnabled` are saved by the SDK; harmless, since nothing starts it early any
       more.
   - Still on the main thread at startup (needed there or tiny): `registerPlugin`, Capacitor's `BridgeActivity.onCreate`
     (creating the WebView, loading plugins: their `load()`s only keep references; AdMob only adds a web message listener),
     text-zoom cap, the mic chrome client (+ its activity-result launcher), the install source's prefs read (a few bytes; the
     Play referrer itself is read on a background thread since 1.7), the splash. Before MainActivity, still at process
     start: Firebase's `FirebaseInitProvider` (`FirebaseApp.initializeApp`, light; the FCM token work waits for
     `register()` since 1.7; kept because push needs it) and Google Ads' init provider (its start-up work moved off the main
     thread by the 1.7 `OPTIMIZE_INITIALIZATION` flag; `MobileAds.initialize` runs only when the web asks for an ad).
   - R8: explicit keep rules for `FacebookSdk`, `AppEventsLogger`, `ActivityLifecycleTracker` public statics.

Web side (seenjeem-egypt master 6e715fee, deployed separately): with app ≥ 1.8, «app.native» on and the plugin present, a tap's
mic refusal asks `micState()` to choose the help sheet (denied_forever → «افتح المايك» with a gold «⚙️ افتح الإعدادات»;
prompt → the «اسمح بالمايك» sheet; granted → a short «دوس عليه تاني»). Older apps keep the timing guess and the steps.
`voice_mic_failed` carries `ns` (the native state) when known, to compare 1.7 and 1.8 in admin analytics.

Not changed: everything else from 1.7 (R8, install source, renderer recovery, FCM waiting for register(), camera removed).

Risks of item 5: (a) an existing 1.6/1.7 phone sends one `MOBILE_APP_INSTALL` ping on its first 1.8 start (1.6/1.7 never
called `activateApp`, so the SDK's ping timestamp is empty); Meta matches it to the same device/anon id, so it should not
count as a new paid install, but watch Ads Manager installs for a jump right after the rollout. (b) this launch's app open is
sent up to ~3 s later than before (or on the next launch if the app is closed sooner). (c) the ANRs may also come from the
web view's own start (GPU traces): compare the ANR rate of 1.8 with 1.7 before drawing conclusions. (d) first compile on
Codemagic (APIs checked against the facebook-core 17.0.0 source: `FacebookSdk.sdkInitialize(Context)`,
`setAutoInitEnabled`, `setAutoLogAppEventsEnabled`, `AppEventsLogger.activateApp(Application)`,
`ActivityLifecycleTracker.isTracking/getCurrentActivity/onActivityCreated/onActivityResumed`).

## Not compiled locally
Still no Android SDK / JDK 17 on the laptop (no `java` at all on PATH), so 1.8 compiles for the first time on Codemagic. Watch
`Build signed release bundle`. The Java uses only APIs already in the project (AndroidX activity-result + core, Capacitor 8
`Plugin` / `registerPlugin`) plus, for item 5, the facebook-core 17.0.0 calls listed under its risks.

Note (13 Nov): the tag `android-v1.8` was first pushed at 715a330 (mic work only). The Play build must come from the commit
with item 5 (move the tag or build from a new one); a Codemagic build of 715a330 does NOT contain the Facebook change.

## Steps (owner)
1. Review, then push the branch and the tag (only when you decide to publish):
   `git push origin app-1.8` then `git tag android-v1.8 app-1.8 && git push origin android-v1.8`
2. Codemagic `android-release` builds the `.aab` (check the versionCode in the log is 111 or higher).
3. Play Console → Testing → **Internal testing** → Create release → upload the `.aab` → notes below → roll out.
4. On a real phone from the internal track, a fresh install (or: App info → Permissions → Microphone → «Don't allow» first):
   - room / 1 ضد 1: Android asks once, «Allow» → voice works;
   - «Don't allow» twice → the next tap on the mic shows «افتح المايك» with «⚙️ افتح الإعدادات» → it opens the app's settings →
     allow the mic → back in the game the mic opens by itself;
   - Meta Events Manager → the app «سهرة وتسالي» → Test events (or the overview, it lags up to ~20 min): a fresh install
     shows the app install and «Activate app»; an app reopened after > 60 s in the background shows another «Activate app»;
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
• التطبيق بيفتح أسرع
• تحسينات في الثبات
```
