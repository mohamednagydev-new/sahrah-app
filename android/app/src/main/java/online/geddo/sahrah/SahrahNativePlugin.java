package online.geddo.sahrah;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * 1.8: the app's own tiny bridge for the web page's mic help (registered in MainActivity before the bridge starts).
 * Web side: window.Capacitor.Plugins.SahrahNative, present only in app 1.8+ (Capacitor.isPluginAvailable('SahrahNative')).
 * Called by the web ONLY from a tap (never at page load), always with a .catch:
 *
 *   SahrahNative.micState()        → Promise<{ state: 'granted' | 'prompt' | 'denied_forever' }>
 *                                     no popup, nothing shown; see MicPermission for what each word means.
 *   SahrahNative.openAppSettings() → Promise<{ opened: boolean }>
 *                                     opens Android's «App info» screen for this app (Permissions → Microphone is one tap away);
 *                                     falls back to the phone's main settings if that screen can't open. When the player comes
 *                                     back the page sees `visibilitychange` (visible) and can call micState() again.
 *
 * Being a Capacitor plugin (not an addJavascriptInterface object) means only the app's own allowed pages can call it.
 */
@CapacitorPlugin(name = "SahrahNative")
public class SahrahNativePlugin extends Plugin {

  @PluginMethod
  public void micState(PluginCall call) {
    JSObject ret = new JSObject();
    try {
      ret.put("state", MicPermission.state(getContext(), getActivity()));
    } catch (Throwable t) {
      ret.put("state", MicPermission.PROMPT);
    }
    call.resolve(ret);
  }

  @PluginMethod
  public void openAppSettings(final PluginCall call) {
    final Activity activity = getActivity();
    if (activity == null || activity.isFinishing()) {
      JSObject ret = new JSObject();
      ret.put("opened", false);
      call.resolve(ret);
      return;
    }
    // plugin methods run on Capacitor's plugin thread: start the screen from the main thread
    activity.runOnUiThread(() -> {
      boolean opened = false;
      try {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.getPackageName(), null));
        activity.startActivity(i);
        opened = true;
      } catch (Throwable t) {
        try {
          activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
          opened = true;
        } catch (Throwable ignored) { }
      }
      JSObject ret = new JSObject();
      ret.put("opened", opened);
      call.resolve(ret);
    });
  }
}
