package online.geddo.sahrah;

import android.Manifest;
import android.app.Activity;
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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebChromeClient;
import com.getcapacitor.Logger;
import com.getcapacitor.WebViewListener;
import java.util.ArrayList;

/**
 * 1.8: mic requests from the page are queued and each one answered exactly once (MicOnlyChromeClient); the SahrahNative plugin
 *      tells the page whether Android will still ask for the mic and opens the app's settings screen.
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
    // 1.8: the app's own plugin (micState / openAppSettings for the web's mic help). Must be registered before
    // super.onCreate, which builds the bridge.
    registerPlugin(SahrahNativePlugin.class);
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
   * The page only ever needs the microphone (voice chat). 1.7: Capacitor's default client asks for the CAMERA permission when a
   * page wants VIDEO_CAPTURE, and grants anything else (protected media, MIDI) without asking; so a request for exactly the
   * microphone is the only one ever granted, every other request (camera, camera+mic, protected media id, MIDI) is denied.
   *
   * 1.8 (12 Nov 2026 mic investigation: 10 of 25 phones on 1.7 failed the mic): no longer hands mic requests to Capacitor's
   * client. That client keeps ONE pending listener: a second getUserMedia while Android's popup was open replaced it, so the
   * first web request was never answered (the page waited forever), and Android's empty answer for an overlapping popup
   * counted as "granted" (granted the page without RECORD_AUDIO). Now:
   *   - RECORD_AUDIO already granted → granted at once (no popup);
   *   - otherwise the request waits in a queue; only ONE Android popup is shown at a time, and its answer goes to every waiting
   *     request (a stale popup with no answer after STALE_MS lets a new one show);
   *   - "granted" is decided by checkSelfPermission after Android answers, never by the result map;
   *   - each PermissionRequest is answered exactly once (taken off the queue first), with only RESOURCE_AUDIO_CAPTURE;
   *     a request the web view cancels meanwhile (onPermissionRequestCanceled) is dropped without an answer;
   *   - every answer is recorded for SahrahNative.micState() (MicPermission.recordResult) BEFORE the page hears the refusal,
   *     so the page's help sheet can ask right after.
   * Everything runs on the main thread (WebChromeClient and activity-result callbacks both do), so no locking.
   */
  static final class MicOnlyChromeClient extends BridgeWebChromeClient {
    /** a popup whose answer never came (activity torn down meanwhile) stops blocking new popups after this */
    private static final long STALE_MS = 60_000;
    private static final String[] MIC = new String[] { PermissionRequest.RESOURCE_AUDIO_CAPTURE };

    private final Activity activity;
    private final ActivityResultLauncher<String> micLauncher;
    private final ArrayList<PermissionRequest> waiting = new ArrayList<>();
    private boolean asking = false;
    private long askedAt = 0;
    private boolean rationaleBefore = false;

    MicOnlyChromeClient(Bridge bridge) {
      super(bridge);
      this.activity = bridge.getActivity();
      // registered in MainActivity.onCreate (before the activity starts), like Capacitor's own launchers
      this.micLauncher = bridge.registerForActivityResult(new ActivityResultContracts.RequestPermission(), (Boolean ignored) -> onAndroidAnswer());
    }

    @Override
    public void onPermissionRequest(final PermissionRequest request) {
      try {
        if (!micOnly(request)) { answer(request, false); return; }
        if (MicPermission.granted(activity)) { answer(request, true); return; }
        waiting.add(request);
        if (asking && SystemClock.elapsedRealtime() - askedAt < STALE_MS) return; // the popup on screen answers this one too
        askAndroid();
      } catch (Throwable t) {
        waiting.remove(request);
        answer(request, false);
      }
    }

    @Override
    public void onPermissionRequestCanceled(PermissionRequest request) {
      // the page went away / the request was withdrawn: it must not be answered any more
      try { waiting.remove(request); } catch (Throwable ignored) { }
    }

    private void askAndroid() {
      asking = true;
      askedAt = SystemClock.elapsedRealtime();
      rationaleBefore = MicPermission.rationale(activity);
      try {
        micLauncher.launch(Manifest.permission.RECORD_AUDIO);
      } catch (Throwable t) {
        asking = false;
        answerAll(false);
      }
    }

    /** Android's popup was answered (or refused with no popup) */
    private void onAndroidAnswer() {
      long took = SystemClock.elapsedRealtime() - askedAt;
      asking = false;
      boolean ok = MicPermission.granted(activity);
      MicPermission.recordResult(activity, ok, rationaleBefore, took);
      answerAll(ok);
    }

    private void answerAll(boolean ok) {
      ArrayList<PermissionRequest> now = new ArrayList<>(waiting);
      waiting.clear();
      for (PermissionRequest r : now) answer(r, ok);
    }

    private static boolean micOnly(PermissionRequest request) {
      String[] res = request.getResources();
      if (res == null || res.length == 0) return false;
      for (String r : res) {
        if (!PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) return false;
      }
      return true;
    }

    private static void answer(PermissionRequest request, boolean ok) {
      try {
        if (ok) request.grant(MIC); else request.deny();
      } catch (Throwable t) {
        try { request.deny(); } catch (Throwable ignored) { }
      }
    }
  }
}
