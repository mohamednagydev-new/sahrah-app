# Release 2.0 (Android): the web view stops drawing while it's covered (ads, background, screen off)

Why (seenjeem-egypt docs/QUEUE.md CR3, owner 15 Nov 2026: "i agree to permanently fix this"): Play vitals, last 28 days: crash
1.72% (Google's line 1.09%), ANR 1.57% (line 0.47%). The traces are inside the app's own process, not the web view's renderer
(renderer death is already recovered since 1.7):
1. `abort` in `libhwui.so SkiaOpenGLPipeline::swapBuffers` → `CanvasContext::draw` on the RenderThread (Galaxy A23 Android 14,
   Redmi 10C Android 13: Snapdragon 680 / Adreno 610);
2. SIGSEGV in `libGLESv2_adreno.so` via `libwebviewchromium_plat_support.so draw_gl` → `WebViewFunctor::drawGl` →
   `GLFunctorDrawable::onDraw`;
3. abort on a `libwebviewchromium.so` worker thread;
4. ANRs «Unresponsive GPU» / `__futex_wait_ex` (the main thread waiting for the render thread / GPU).
Our own data: sessions end abruptly right after an AdMob ad starts (rewarded 11%, forced 3.7%) vs 1.6% after a plain game end.
AdMob shows interstitial / rewarded ads in Google's own `com.google.android.gms.ads.AdActivity`, which has a TRANSLUCENT theme:
our activity is only paused under it, never stopped, so until 1.9 the web view kept drawing frames (3D games, animations) behind
the ad: two GPU-heavy screens at once on phones whose GL driver is the one crashing.

What 2.0 changes, compared with 1.9 (all in `MainActivity.java`):

1. **The web view is paused while the screen is covered** (`pauseWeb` = `webView.onPause()`, Android's documented "this web
   view is not on screen" call; Chromium hides the page: no frames, no `requestAnimationFrame`, `visibilitychange` → hidden;
   JavaScript keeps running, audio keeps playing):
   - `onStop` → paused (app in the background, screen off, the sign-in Custom Tab on top);
   - an AdMob ad screen created / resumed on top (`AdWatch`, an `Application.ActivityLifecycleCallbacks` that matches activities
     whose class starts with `com.google.android.gms.ads.`, never ours) → paused at once, and it stays paused while the ad is on
     top even if the app goes to the background and comes back (`adScreens` count);
   - resumed: `onResume` (Capacitor already calls `webView.onResume()` there, see below; our own call is a safety net only when
     we paused it, and Chromium ignores it when the view is already running) and `onStart` when no ad is on top (covers being
     visible without being resumed: Android 9- split screen, a translucent popup still on top), so the page is never left
     frozen on screen.
   - NOT on `onPause` alone: Android's mic / notification permission popup and the share sheet also pause the activity
     without stopping it; hiding the page there would trigger the web's «back on screen» mic retry (`voiceLive.ts`) right after
     a refusal. Under those popups the page behaves exactly as in 1.9.
   - NOT the global `pauseTimers()`: it freezes the JavaScript of every web view in the app, including AdMob's own ad web views.
2. **Low memory → a tiny event for the page**: `onTrimMemory(RUNNING_LOW / RUNNING_CRITICAL)` and `onLowMemory` →
   `window` event `sahrahLowMemory` with `e.level` = `'low' | 'critical'` (Capacitor's `triggerWindowJSEvent`). Native → page
   only, only after the first page showed, at most once every 30 s, never while the web view is paused. Nothing listens yet;
   optional web follow-up (CR4 "graphics diet"): `window.addEventListener('sahrahLowMemory', …)` → drop the 3D scene's
   textures / switch the running game to light. Android 14+ no longer sends the RUNNING_* levels; `onLowMemory` still comes.
3. User agent `SahrahApp/2.0`, versionName `2.0`, versionCode = max(111, Codemagic BUILD_NUMBER + 100) (same rule as 1.8/1.9).

## What Capacitor already did (Capacitor android 8.5.3, read in node_modules)
- `BridgeActivity.onPause` → `Bridge.onPause` → plugins' `handleOnPause` + `MockCordovaWebViewImpl.handlePause(keepRunning)`:
  it pauses the web view (`onPause` + global `pauseTimers`) ONLY when `KeepRunning` is false; the default is true and this app
  doesn't change it, so until 1.9 nothing ever paused the web view.
- `BridgeActivity.onResume` → `handleResume` → `setPaused(false)` = `webView.onResume()` + `resumeTimers()` on EVERY resume.
  So the resume side was already there; 2.0 adds the pause side. `onStart` / `onStop` only notify plugins.

## Voice and push
- Push (FCM) is native (Firebase service + the PushNotifications plugin), independent of the web view: unchanged. A push that
  arrives while the page is paused still reaches its JavaScript (JS isn't paused).
- Live voice (LiveKit / WebRTC in the page): `webView.onPause()` doesn't stop media or JavaScript, so the other person's voice
  keeps playing and the room socket stays connected. Screen lock / background was already "page hidden" before 2.0 (Chromium
  hides a web view whose window is invisible) and Android 9+ already silences the mic of an app in the background; so voice
  with the screen locked should behave as in 1.9. During an ad the page is now hidden (it was "visible" behind the ad before):
  the friend's room shows this player as «away» while the ad is on screen (FriendGate's away report), which is true.
  Trade-off accepted for crash safety; if internal testing shows voice dropping on screen lock, the fallback is to skip the
  `onStop` pause while a voice call is live (the ad pause stays).
- Not changed: `android:hardwareAccelerated` stays on (the default; software drawing would make everything slow); no
  `largeHeap` (the web view's memory is in its renderer process, not the Java heap); no change to the 1.7 renderer recovery,
  the mic queue, the Facebook start, the install source. Nothing native runs because the page loads (31 Oct lesson): the page
  never calls native for any of this.

## Not compiled locally
Still no Android SDK / JDK 17 on the laptop (no `java` on PATH), so 2.0 compiles for the first time on Codemagic. Watch
`Build signed release bundle`. APIs used are all plain Android SDK (`Application.ActivityLifecycleCallbacks`,
`WebView.onPause/onResume`, `ComponentCallbacks2`) plus Capacitor's public `Bridge.triggerWindowJSEvent`; the app's whole
package is already kept by R8.

## Steps (owner)
1. Review, then push the branch and the tag (only when you decide to publish):
   `git push origin app-2.0` then `git tag android-v2.0 app-2.0 && git push origin android-v2.0`
2. Codemagic `android-release` builds the `.aab` (check the versionCode in the log is higher than 1.9's).
3. Play Console → Testing → **Internal testing** → Create release → upload the `.aab` → notes below → roll out.
4. On a real phone from the internal track (ideally a weak one, e.g. a Redmi / Galaxy A):
   - finish a game so a forced ad shows, and watch a rewarded ad to the end: the reward arrives, back in the game everything
     moves again (no frozen picture), music/sounds come back;
   - press Home during an ad, come back: the ad is still there, then closing it brings the game back moving;
   - voice in a room / 1 ضد 1: talk, lock the screen 20 s, unlock: the call is still on and both hear each other (if voice drops,
     tell the lead: see the fallback above);
   - home + a 3D street game: send the app to the background and back, screen off and on: the game resumes, nothing frozen;
   - the mic popup on a fresh install («Allow» and «Don't allow»): same behaviour as 1.9;
   - plus the 1.9 checks: sign in with Google and Facebook (Custom Tab and back), «فعّل» notifications + a test push, share, an
     `https://sahrah.geddo.online/...` link opens inside the app, airplane mode → offline screen → back by itself.
5. Only if all pass: Production → Create release → add from library (the same build) → staged rollout 20% → watch Android
   vitals (crash, ANR, by device) 3–7 days against 1.9 (CR2) → 100%.
6. When 2.0 is live on Play, tell the lead to set admin «نسخة التطبيق» (`app.latest`) to 2.0.

## Release notes for Play (ar)
Copy from `RELEASE-NOTES-2.0-ar.txt` (each line starts with an invisible right-to-left mark).
