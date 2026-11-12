package online.geddo.sahrah;

import android.app.Activity;
import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import com.facebook.FacebookSdk;
import com.facebook.appevents.AppEventsLogger;
import com.facebook.appevents.internal.ActivityLifecycleTracker;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 1.8: the Facebook SDK (core 17.0.0) starts AFTER the first page, off the main thread.
 *
 * Why: from 1.6 (the version that added it) Play shows startup ANRs in the dynamic linker (dl_iterate_phdr / dlsym) and
 * "Input dispatching timed out (No focused window)". Until 1.7 the SDK started itself in its FacebookInitProvider
 * (a ContentProvider that runs FacebookSdk.sdkInitialize on the MAIN thread before MainActivity even exists: manifest
 * reads, shared-preferences disk reads, class loading of the SDK, lifecycle tracking). The manifest now removes that
 * provider and turns AutoInit / AutoLogAppEvents off; this class does the same work later, once per process.
 *
 * When: the first of (the web view's first page finished loading, 3 s after MainActivity.onCreate). Both triggers call
 * {@link #startOnce}; only the first one runs.
 *
 * What (on a background thread, exactly the SDK's documented "delayed initialisation" path):
 *   1. FacebookSdk.sdkInitialize(app)            – what the removed provider did (reads app id + client token);
 *   2. FacebookSdk.setAutoInitEnabled(true)      – = fullyInitialize();
 *   3. FacebookSdk.setAutoLogAppEventsEnabled(true) – registers the SDK's activity-lifecycle tracker (app opens / sessions);
 *   4. AppEventsLogger.activateApp(app)          – sends the install ping (MOBILE_APP_INSTALL, only once per install:
 *                                                  the SDK keeps a "ping" timestamp) and makes sure tracking is on.
 * Then, on the main thread: the lifecycle tracker registered after MainActivity was created and resumed, so it missed
 * this launch's resume (= this launch's app open, fb_mobile_activate_app). If MainActivity is on screen and the tracker
 * has not seen it, we hand it the two events it missed with ActivityLifecycleTracker.onActivityCreated/onActivityResumed
 * (public in 17.0.0, "to allow unity sdk to correctly log app events"): the same code the lifecycle callback would have
 * run, so this launch's app open is logged exactly once and the later pause/stop balance it.
 *
 * Anything that fails here is only logged: the game never depends on the Facebook SDK.
 */
final class FacebookStart {
  private static final String TAG = "SahrahFb";
  /** fallback when the first page takes long (or never finishes): start anyway this long after onCreate */
  static final long FALLBACK_MS = 3_000;

  private static final AtomicBoolean started = new AtomicBoolean(false);
  /** the MainActivity that is resumed right now (main thread only) */
  private static WeakReference<Activity> resumed = new WeakReference<>(null);

  private FacebookStart() { }

  /** MainActivity.onResume (main thread) */
  static void onResume(Activity a) { resumed = new WeakReference<>(a); }

  /** MainActivity.onPause (main thread) */
  static void onPause(Activity a) { if (resumed.get() == a) resumed = new WeakReference<>(null); }

  /** first page finished / 3 s fallback (main thread). Only the first call in this process does anything. */
  static void startOnce(Activity activity) {
    if (activity == null || !started.compareAndSet(false, true)) return;
    final Application app = activity.getApplication();
    final Handler main = new Handler(Looper.getMainLooper());
    Thread t = new Thread(() -> {
      try { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); } catch (Throwable ignored) { }
      try {
        initSdk(app);
        AppEventsLogger.activateApp(app);
        main.post(FacebookStart::replayMissedResume);
      } catch (Throwable e) {
        // e.g. an empty client token (Codemagic refuses to build without one) – the app goes on without the SDK
        Log.w(TAG, "Facebook SDK not started", e);
      }
    }, "sahrah-fb-init");
    t.start();
  }

  @SuppressWarnings("deprecation") // sdkInitialize is "deprecated" only because the provider normally calls it for you
  private static void initSdk(Application app) {
    FacebookSdk.sdkInitialize(app);
    FacebookSdk.setAutoInitEnabled(true);
    FacebookSdk.setAutoLogAppEventsEnabled(true);
  }

  /** main thread: give the tracker this launch's create + resume if it registered too late to see them */
  private static void replayMissedResume() {
    try {
      Activity a = resumed.get();
      if (a == null || a.isFinishing() || a.isDestroyed()) return; // not on screen: its next resume is logged normally
      if (!ActivityLifecycleTracker.isTracking()) return;
      if (ActivityLifecycleTracker.getCurrentActivity() == a) return; // the tracker already saw this activity resume
      ActivityLifecycleTracker.onActivityCreated(a);
      ActivityLifecycleTracker.onActivityResumed(a);
    } catch (Throwable e) {
      Log.w(TAG, "Facebook app open not logged", e);
    }
  }
}
