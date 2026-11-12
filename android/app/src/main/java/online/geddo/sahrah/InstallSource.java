package online.geddo.sahrah;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import com.android.installreferrer.api.InstallReferrerClient;
import com.android.installreferrer.api.InstallReferrerStateListener;
import com.android.installreferrer.api.ReferrerDetails;

/**
 * 1.7: where this install came from (Google Play Install Referrer), handed to the web page ONCE as query parameters.
 *
 * - Read on the first launch(es) only, off the main thread; nothing waits for it. A definite answer (or "not supported") ends
 *   it for good; a Play service hiccup is retried on the next launches, at most {@link #MAX_TRIES} times.
 * - The answer is stored; {@link #pendingUrl()} returns the start URL with it until {@link #markDelivered()} is called.
 * - Every failure is swallowed: this must never crash or slow the app.
 *
 * What the web receives (exact):
 *   https://sahrah.geddo.online/?utm_source=<s>&utm_medium=<m>&utm_campaign=<c>&utm_content=<x>&utm_term=<t>&gclid=<g>&src=play_referrer&ref=<raw>
 *   - utm_* : copied from the Play referrer when present (each only if present). No utm_source in it → utm_source=google
 *     + utm_medium=cpc when it has a gclid (Google Ads), otherwise utm_source=google-play.
 *   - gclid : only when the referrer has one (a Google Ads install).
 *   - src=play_referrer : always (marks this landing as the Play install source).
 *   - ref : the raw referrer string, URL-encoded, at most 400 characters.
 *   These replace the usual ?utm_source=app&utm_medium=native of that one load (the user agent still says SahrahApp/<version>).
 */
final class InstallSource {
  static final String START = "https://sahrah.geddo.online/";
  private static final String PREFS = "sahrah_install_source";
  private static final int MAX_TRIES = 3;
  private static final String[] UTM = { "utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term" };

  interface Ready { void onReady(); }

  private InstallSource() {}

  private static SharedPreferences prefs(Context c) {
    return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  /** the start URL carrying the install source, or null (none known, or already handed to the web) */
  static String pendingUrl(Context c) {
    try {
      SharedPreferences p = prefs(c);
      if (p.getBoolean("delivered", false)) return null;
      String raw = p.getString("raw", null);
      if (raw == null) return null;
      return START + "?" + query(raw);
    } catch (Throwable t) {
      return null;
    }
  }

  static void markDelivered(Context c) {
    try { prefs(c).edit().putBoolean("delivered", true).apply(); } catch (Throwable ignored) { }
  }

  /** reads the referrer once (background thread); {@code ready} runs on the main thread only when a new referrer was stored */
  static void fetchOnce(Context context, final Ready ready) {
    final Context app = context.getApplicationContext();
    final SharedPreferences p;
    try {
      p = prefs(app);
      if (p.getBoolean("done", false) || p.getInt("tries", 0) >= MAX_TRIES) return;
      p.edit().putInt("tries", p.getInt("tries", 0) + 1).apply();
    } catch (Throwable t) {
      return;
    }
    final Handler main = new Handler(Looper.getMainLooper());
    Thread t = new Thread(() -> {
      try {
        final InstallReferrerClient client = InstallReferrerClient.newBuilder(app).build();
        client.startConnection(new InstallReferrerStateListener() {
          @Override
          public void onInstallReferrerSetupFinished(final int code) {
            // the callback comes on the main thread: the binder call itself goes back to a background thread
            new Thread(() -> {
              try {
                if (code == InstallReferrerClient.InstallReferrerResponse.OK) {
                  ReferrerDetails d = client.getInstallReferrer();
                  String raw = d != null ? d.getInstallReferrer() : null;
                  p.edit().putString("raw", raw == null ? "" : raw).putBoolean("done", true).apply();
                  main.post(() -> { try { if (ready != null) ready.onReady(); } catch (Throwable ignored) { } });
                } else if (code == InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED
                    || code == InstallReferrerClient.InstallReferrerResponse.DEVELOPER_ERROR) {
                  // no Play Store / not a Play install: nothing to ask again
                  p.edit().putBoolean("done", true).apply();
                }
                // SERVICE_UNAVAILABLE / PERMISSION_ERROR / others: try again on a later launch (tries is capped)
              } catch (Throwable ignored) {
              } finally {
                try { client.endConnection(); } catch (Throwable ignored) { }
              }
            }, "sahrah-referrer-read").start();
          }

          @Override
          public void onInstallReferrerServiceDisconnected() {
            // nothing to do: a later launch tries again
          }
        });
      } catch (Throwable ignored) {
      }
    }, "sahrah-referrer");
    t.setPriority(Thread.MIN_PRIORITY);
    t.start();
  }

  /** utm_* (+ gclid) parsed from the Play referrer, then src=play_referrer and the raw referrer */
  static String query(String raw) {
    Uri q;
    try { q = Uri.parse("https://x/?" + raw); } catch (Throwable t) { q = null; }
    StringBuilder b = new StringBuilder();
    boolean hasSource = false, hasMedium = false;
    String gclid = q != null ? safeParam(q, "gclid") : null;
    for (String k : UTM) {
      String v = q != null ? safeParam(q, k) : null;
      if (v == null) continue;
      add(b, k, v);
      if (k.equals("utm_source")) hasSource = true;
      if (k.equals("utm_medium")) hasMedium = true;
    }
    if (!hasSource) {
      if (gclid != null) { add(b, "utm_source", "google"); if (!hasMedium) add(b, "utm_medium", "cpc"); }
      else add(b, "utm_source", "google-play");
    }
    if (gclid != null) add(b, "gclid", gclid);
    add(b, "src", "play_referrer");
    add(b, "ref", raw.length() > 400 ? raw.substring(0, 400) : raw);
    return b.toString();
  }

  private static String safeParam(Uri q, String k) {
    try {
      String v = q.getQueryParameter(k);
      if (v == null) return null;
      v = v.trim();
      return v.isEmpty() ? null : (v.length() > 200 ? v.substring(0, 200) : v);
    } catch (Throwable t) {
      return null;
    }
  }

  private static void add(StringBuilder b, String k, String v) {
    if (b.length() > 0) b.append('&');
    b.append(k).append('=').append(Uri.encode(v));
  }
}
