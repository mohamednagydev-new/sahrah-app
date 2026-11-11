package online.geddo.sahrah;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.ViewGroup;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebView;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebChromeClient;
import com.getcapacitor.Logger;
import com.getcapacitor.WebViewListener;

/**
 * 1.7: web view renderer crash → a fresh web view instead of an app crash; only the microphone is ever granted to the page;
 *      install source (Play referrer) handed to the web once. 1.4: text size capped at 110% of normal.
 * 1.3: a shared https://sahrah.geddo.online/... link (App Links) opens that page inside the app's web view.
 */
public class MainActivity extends BridgeActivity {
  /** renderer deaths in a row (process-wide): stop recreating after 3 within a minute (a page that kills it every time) */
  private static int rendererDeaths = 0;
  private static long lastRendererDeath = 0;

  /** the first page has shown something (after that the install source waits for the next app start) */
  private boolean pageShown = false;
  /** this start opened a shared link: never replace it with the install-source URL */
  private boolean openedLink = false;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (bridge == null || bridge.getWebView() == null) return;
    WebView wv = bridge.getWebView();

    // 1.4: the phone's «font size» setting made the web view's text up to 2x and stretched the cards; follow it only up to 110%
    android.webkit.WebSettings ws = wv.getSettings();
    ws.setTextZoom(Math.min(ws.getTextZoom(), 110));

    // 1.7: the page may only ever get the microphone (see MicOnlyChromeClient)
    wv.setWebChromeClient(new MicOnlyChromeClient(bridge));

    // 1.7: renderer crash handling + "first page shown" for the install source
    bridge.addWebViewListener(new WebViewListener() {
      @Override
      public void onPageCommitVisible(WebView view, String url) { pageShown = true; }

      @Override
      public void onPageLoaded(WebView view) { pageShown = true; }

      @Override
      public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) { return rendererGone(view, detail); }
    });

    // the launch link itself was already opened: BridgeActivity.load() hands the launch intent to onNewIntent (before 1.7 it was
    // opened a second time from here)
    openedLink = isOurLink(getIntent());

    // 1.7: install source. Known from an earlier start and not handed over yet → this start's first load carries it.
    if (!openedLink) {
      final String withSource = InstallSource.pendingUrl(this);
      if (withSource != null) {
        InstallSource.markDelivered(this);
        wv.post(() -> { if (bridge != null && bridge.getWebView() != null) bridge.getWebView().loadUrl(withSource); });
      }
    }
    // first launch: read it in the background; if it arrives before the first page shows anything (usually: Play answers in a
    // fraction of a second, the splash stays 1.2 s), restart that first load with it; otherwise the next app start carries it
    InstallSource.fetchOnce(this, () -> {
      if (isFinishing() || openedLink || pageShown || bridge == null || bridge.getWebView() == null) return;
      String withSource = InstallSource.pendingUrl(this);
      if (withSource == null) return;
      InstallSource.markDelivered(this);
      bridge.getWebView().loadUrl(withSource);
    });
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    openLink(intent);
  }

  private static boolean isOurLink(Intent intent) {
    Uri u = intent != null ? intent.getData() : null;
    return u != null && "https".equals(u.getScheme()) && "sahrah.geddo.online".equals(u.getHost());
  }

  private boolean openLink(Intent intent) {
    if (!isOurLink(intent)) return false;
    String url = intent.getData().toString();
    final String go = url + (url.contains("?") ? "&" : "?") + "utm_source=app&utm_medium=native";
    if (bridge == null || bridge.getWebView() == null) return false;
    bridge.getWebView().post(() -> { if (bridge != null && bridge.getWebView() != null) bridge.getWebView().loadUrl(go); });
    return true;
  }

  /**
   * 1.7: the web view's renderer process died (GPU driver crash, out of memory). Without this the whole app is killed and Play
   * counts a crash. Android's rule: the dead WebView can't be used again; take it out of the screen, and Capacitor destroys it
   * in onDestroy. Then the activity is recreated with a brand-new web view (the game keeps its saved state in the page's own
   * storage). 3 deaths within a minute: close quietly instead of looping.
   */
  private boolean rendererGone(WebView view, RenderProcessGoneDetail detail) {
    try {
      boolean crashed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && detail != null && detail.didCrash();
      Logger.warn("Sahrah", "web view renderer gone (" + (crashed ? "crash" : "killed for memory") + "), recreating");
      if (bridge == null || view != bridge.getWebView()) return true; // defensive: this listener only sees the bridge's web view
      long now = SystemClock.elapsedRealtime();
      rendererDeaths = (now - lastRendererDeath < 60_000) ? rendererDeaths + 1 : 1;
      lastRendererDeath = now;
      ViewGroup parent = (ViewGroup) view.getParent();
      if (parent != null) parent.removeView(view);
      final boolean giveUp = rendererDeaths >= 3;
      new Handler(Looper.getMainLooper()).post(() -> {
        if (isFinishing() || isDestroyed()) return;
        if (giveUp) finish(); else recreate();
      });
    } catch (Throwable t) {
      try { finish(); } catch (Throwable ignored) { }
    }
    return true;
  }

  /**
   * 1.7: the page only ever needs the microphone (voice chat). Capacitor's default client asks for the CAMERA permission when
   * a page wants VIDEO_CAPTURE, and grants anything else (protected media, MIDI) without asking. Here: a request for exactly
   * the microphone goes to Capacitor's normal flow (Android's «Allow» popup, granted only if the player says yes); every other
   * request (camera, camera+mic, protected media id, MIDI) is denied.
   */
  static final class MicOnlyChromeClient extends BridgeWebChromeClient {
    MicOnlyChromeClient(Bridge bridge) { super(bridge); }

    @Override
    public void onPermissionRequest(final PermissionRequest request) {
      try {
        String[] res = request.getResources();
        boolean micOnly = res != null && res.length > 0;
        if (res != null) {
          for (String r : res) {
            if (!PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) { micOnly = false; break; }
          }
        }
        if (micOnly) super.onPermissionRequest(request);
        else request.deny();
      } catch (Throwable t) {
        try { request.deny(); } catch (Throwable ignored) { }
      }
    }
  }
}
