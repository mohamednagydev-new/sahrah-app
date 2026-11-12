package online.geddo.sahrah;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * 1.8: where the RECORD_AUDIO permission stands, in the three words the web page needs (see SahrahNativePlugin.micState):
 *   "granted"        Android lets the app use the mic;
 *   "prompt"         not granted, but Android will still show its «Allow» popup when asked;
 *   "denied_forever" Android will not show the popup any more (refused twice, or «Don't ask again»): only the phone's settings
 *                    can allow it.
 *
 * Android has no direct "won't ask any more" API. shouldShowRequestPermissionRationale() is true after ONE refusal and false
 * both before the first ask and after the final refusal, so the answer is recorded when a request comes back refused
 * (recordResult, called by MainActivity.MicOnlyChromeClient right after Android's answer, before the page hears it):
 *   - refused and the rationale went from true to false → the final refusal (Android 11+: the 2nd «Don't allow»; Android 6-10:
 *     «Don't ask again» ticked);
 *   - refused within FAST_MS with no rationale → no popup showed at all (already refused for good, e.g. in 1.7);
 *   - refused slowly with no rationale before or after → the very first popup was dismissed (tap outside / back): still "prompt".
 * The flag is cleared whenever the permission is seen granted or the rationale is true again (the player changed it in settings).
 * Everything here is cheap, never shows anything, and never throws.
 */
final class MicPermission {
  private MicPermission() { }

  static final String GRANTED = "granted";
  static final String PROMPT = "prompt";
  static final String DENIED_FOREVER = "denied_forever";

  /** a refusal faster than this had no popup (no person answers that fast); same idea as the web's FAST_MS */
  static final long FAST_MS = 700;

  private static final String PREFS = "sahrah_mic";
  private static final String KEY_FOREVER = "denied_forever";

  static boolean granted(Context ctx) {
    try {
      return ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    } catch (Throwable t) {
      return false;
    }
  }

  static boolean rationale(Activity activity) {
    try {
      return activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO);
    } catch (Throwable t) {
      return false;
    }
  }

  private static SharedPreferences prefs(Context ctx) {
    return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  /**
   * Android answered a mic request. `rationaleBefore` = shouldShowRequestPermissionRationale just before the popup, `elapsedMs`
   * = how long Android took to answer.
   */
  static void recordResult(Activity activity, boolean grantedNow, boolean rationaleBefore, long elapsedMs) {
    try {
      SharedPreferences.Editor e = prefs(activity).edit();
      if (grantedNow) {
        e.putBoolean(KEY_FOREVER, false);
      } else {
        boolean rationaleAfter = rationale(activity);
        boolean forever = !rationaleAfter && (rationaleBefore || elapsedMs < FAST_MS);
        e.putBoolean(KEY_FOREVER, forever);
      }
      e.apply();
    } catch (Throwable ignored) { }
  }

  /** "granted" | "prompt" | "denied_forever" (see the class comment). `activity` may be null (then no rationale is known). */
  static String state(Context ctx, Activity activity) {
    try {
      if (granted(ctx)) {
        prefs(ctx).edit().putBoolean(KEY_FOREVER, false).apply();
        return GRANTED;
      }
      if (rationale(activity)) {
        // one refusal so far: Android will ask again
        prefs(ctx).edit().putBoolean(KEY_FOREVER, false).apply();
        return PROMPT;
      }
      return prefs(ctx).getBoolean(KEY_FOREVER, false) ? DENIED_FOREVER : PROMPT;
    } catch (Throwable t) {
      return PROMPT;
    }
  }
}
