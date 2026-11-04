package online.geddo.sahrah;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

/** 1.3: a shared https://sahrah.geddo.online/... link (App Links) opens that page inside the app's web view. */
public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
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
