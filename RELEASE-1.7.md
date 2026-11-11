# Release 1.7 (Android): fewer crashes and freezes, install source

Why: Play Console for 1.6 showed user-perceived crash rate 1.26% (bad behaviour threshold 1.09%) and ANR rate 1.15% (threshold
0.47%). The top ANR is the main thread waiting for the web view's GPU drawing (`nSyncAndDrawFrame` → `draw_gl`, Mali / PowerVR /
Adreno drivers); the top crash is a native crash in the Adreno GL driver. Most of the GPU load is the web page itself (the web's
light mode ships separately); 1.7 fixes what the shell can fix.

What 1.7 changes, compared with 1.6:

1. **R8 for the release build** (`android/app/build.gradle`: `minifyEnabled true`, `shrinkResources true`,
   `proguard-android-optimize.txt`). Rules in `android/app/proguard-rules.pro`: Capacitor core, every plugin, Cordova layer, all
   `@JavascriptInterface` methods and this app's own classes are kept whole (the JS bridge finds plugins and methods by name);
   Firebase messaging, Install Referrer and Play review are kept explicitly. `res/raw/keep.xml` keeps the resources looked up by
   name (splash, notification icon, launcher icons, Cordova config.xml, file provider paths). R8 runs in compatibility mode
   (`android.enableR8.fullMode=false` in `gradle.properties`) because Capacitor relies on reflection. The mapping file is packed in
   the `.aab`, so Play's crash stacks stay readable; Codemagic also keeps `mapping.txt` as an artifact.
2. **Install source** (`InstallSource.java`, `com.android.installreferrer:installreferrer:2.2`). Read once, in the background, on
   the first launch (a Play hiccup is retried on the next 2 launches at most). It is handed to the web page ONCE:
   - if Play answers before the first page shows anything (the usual case: the splash stays 1.2 s), that first load is restarted
     with it;
   - otherwise the next app start's first load carries it.
   The URL the web receives (only that one time; every other start stays `/?utm_source=app&utm_medium=native`):
   `https://sahrah.geddo.online/?utm_source=…&utm_medium=…&utm_campaign=…&utm_content=…&utm_term=…&gclid=…&src=play_referrer&ref=<raw, url-encoded, ≤400 chars>`
   - `utm_*` straight from the Play referrer, each only when present. Organic Play installs usually give
     `utm_source=google-play&utm_medium=organic`. Meta app ads give `utm_source=apps.facebook.com` (+ `utm_campaign`,
     `utm_content` = Meta's encrypted blob).
   - no `utm_source` in the referrer → `utm_source=google` + `utm_medium=cpc` when there is a `gclid`, else `utm_source=google-play`.
   - `src=play_referrer` always; `ref` = the raw referrer.
   The web's existing `captureLanding()` (lib/tracking.ts) already records the `utm_*` and `gclid` as the `attribution` event and
   as this device's first touch. Optional web work: also log `src`/`ref` (e.g. `track('attribution', {...tags, src:'play_referrer'})`)
   so the admin can tell a Play-referrer landing from a link landing.
3. **Web permissions: microphone only** (`MainActivity.MicOnlyChromeClient`). Capacitor's default client asked for CAMERA when a
   page wanted video and silently granted everything else (protected media id, MIDI). Now a request for exactly the microphone goes
   through the normal Android «Allow» popup; anything else is denied. The manifest also removes CAMERA should any library add it.
4. **Renderer crash → new web view instead of an app crash** (`MainActivity.rendererGone`). When the web view's renderer process
   dies (GPU driver crash, out of memory) Android used to kill the whole app (a Play "crash"). Now the dead web view is removed and
   the screen is rebuilt with a fresh one (the page reloads; game state kept in the page's storage survives). Three deaths within a
   minute → the app closes quietly instead of looping.
5. **Lighter start**: Firebase no longer fetches an FCM token at every app start (`firebase_messaging_auto_init_enabled=false`);
   it starts when the web calls `PushNotifications.register()` after the player's «فعّل». Phones that already allowed
   notifications keep them (Firebase saved auto-init = on for them). Google's Ads SDK flags `OPTIMIZE_INITIALIZATION` /
   `OPTIMIZE_AD_LOADING` move its start-up work off the main thread.
6. A shared link that opens the app is loaded once (it was loaded twice since 1.3).
7. User agent `SahrahApp/1.7`, versionName `1.7`, versionCode = max(110, Codemagic BUILD_NUMBER + 100).

Not changed, on purpose: hardware acceleration stays on (software drawing is slower); no `largeHeap` (the web view's memory is in
its own renderer process, not the app's Java heap); renderer priority stays Android's default (lowering it gets the renderer
killed more often in the background); offscreen pre-raster stays off (Capacitor's default); the splash is the AndroidX splash
screen, removed after 1.2 s; the Facebook SDK still starts by itself (moving it later could lose Meta's install/app-open events
for the running campaign; candidate for 1.8 after a test with Meta Events Manager). The text-size cap (branch `app-1.2.1`) has
been in since 1.4 (110%).

## Camera threads in the 1.6 ANR traces
Nothing native opens the camera: there is no camera plugin, no CAMERA permission, and the web only calls
`getUserMedia({ audio })`. `VideoCaptureThr`, `CameraManager…`, `AudioThread` are the web view's own (Chromium) media threads,
which live inside the app process: they start the first time the page touches `navigator.mediaDevices` (LiveKit voice, the mic
probe), and device listing walks the cameras through Android's CameraManager even for an audio-only call. They sit idle and are
not what blocks the main thread (that is the GPU draw). 1.7 makes sure no camera access can ever be granted.

## Not compiled locally
There is no Android SDK / JDK 17+ on the laptop (only JDK 8), so R8 and lint run for the first time on Codemagic. Watch the
`Build signed release bundle` step: an R8 "Missing class" error needs one more `-dontwarn` line in `proguard-rules.pro`.

## Steps (owner)
1. Push the branch and the tag (only when you decide to publish):
   `git push origin app-1.7` then `git tag android-v1.7 app-1.7 && git push origin android-v1.7`
   (or merge `app-1.7` into `main` first and tag `main`).
2. Codemagic `android-release` builds the `.aab` (check the versionCode in the log is 110 or higher).
3. Play Console → Testing → **Internal testing** → Create release → upload the `.aab` → notes below → roll out.
4. On a real phone from the internal track (ideally a weak one): the app opens, the home page loads, sign-in in the Custom Tab
   and back works, «فعّل» notifications works and a test push arrives, the mic works in a room / 1 ضد 1 (Android asks once), share
   works, an ad shows, an `https://sahrah.geddo.online/...` link opens inside the app. In admin analytics an `attribution` event
   with `src=play_referrer`-tagged utm values should appear for the fresh install (internal-track installs often give
   `utm_source=google-play&utm_medium=organic`).
5. Production → Create release → add from library (the same build) → staged rollout 20% → watch Android vitals 2–3 days → 100%.
6. When 1.7 is live, raise `LATEST` in seenjeem-egypt `components/AppUpdate.tsx` to `[1, 7]`.

## Release notes for Play (ar)
```
• التطبيق بقى أخف وأسرع في الفتح، خصوصًا على الموبايلات البسيطة
• إصلاح مشاكل كانت بتقفل التطبيق فجأة أو بتخليه يهنّج وقت اللعب
• لو حصلت مشكلة في عرض اللعبة، التطبيق بيرجع لوحده بدل ما يقفل
```
