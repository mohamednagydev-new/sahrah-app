package online.geddo.sahrah;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

/** 1.4: text size capped at 110% of normal. 1.3: a shared https://sahrah.geddo.online/... link (App Links) opens that page inside the app's web view. */
public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // 1.4: the phone's «font size» setting made the web view's text up to 2x and stretched the cards; follow it only up to 110%
    if (bridge != null && bridge.getWebView() != null) {
      android.webkit.WebSettings ws = bridge.getWebView().getSettings();
      ws.setTextZoom(Math.min(ws.getTextZoom(), 110));
    }
    openLink(getIntent());
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    openLink(intent);
  }

  private void openLink(Intent intent) {
    if (intent == null) return;
    Uri u = intent.getData();
    if (u == null || !"https".equals(u.getScheme()) || !"sahrah.geddo.online".equals(u.getHost())) return;
    String url = u.toString();
    final String go = url + (url.contains("?") ? "&" : "?") + "utm_source=app&utm_medium=native";
    if (bridge == null || bridge.getWebView() == null) return;
    bridge.getWebView().post(() -> bridge.getWebView().loadUrl(go));
  }
}
