package local.position.helper;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public final class MapActivity extends Activity {
    private WebView web;
    private static final String ORIGIN = "https://appassets.androidplatform.net";
    private static final Set<String> FILES = new HashSet<>(Arrays.asList("map.html", "map.js", "map.css", "leaflet.js", "leaflet.css"));
    @Override @SuppressLint("SetJavaScriptEnabled") public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); MainActivity.inset(root);
        Ui ui = new Ui(this); root.setBackgroundColor(Ui.BG); ui.toolbar(root, "地图选点", true, null);
        web = new WebView(this); root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root);
        WebSettings settings = web.getSettings(); settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setGeolocationEnabled(false); settings.setSupportMultipleWindows(false);
        settings.setUserAgentString(settings.getUserAgentString() + " DingDianHelper/1.0 (local.position.helper)");
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equals(uri.getScheme()) && "appassets.androidplatform.net".equals(uri.getHost())) {
                    String name = uri.getLastPathSegment();
                    if (FILES.contains(name) && ("/assets/" + name).equals(uri.getPath())) {
                        try {
                            String mime = name.endsWith(".js") ? "application/javascript" : name.endsWith(".css") ? "text/css" : "text/html";
                            return new WebResourceResponse(mime, "UTF-8", getAssets().open(name));
                        } catch (IOException ignored) { }
                    }
                    return denied();
                }
                if ("https".equals(uri.getScheme()) && "tile.openstreetmap.org".equals(uri.getHost())) return null;
                return denied();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (request.isForMainFrame() && "locationpicker".equals(uri.getScheme()) && "select".equals(uri.getHost())) {
                    try {
                        GeoPoint point = GeoPoint.parse(uri.getQueryParameter("lat"), uri.getQueryParameter("lng"));
                        setResult(RESULT_OK, new Intent().putExtra("latitude", point.latitude).putExtra("longitude", point.longitude));
                        finish();
                    } catch (IllegalArgumentException error) { Toast.makeText(MapActivity.this, error.getMessage(), Toast.LENGTH_LONG).show(); }
                    return true;
                }
                // Only the bundled picker runs inside this WebView; no page receives a native bridge.
                if (request.isForMainFrame() && "https".equals(uri.getScheme()) && "www.openstreetmap.org".equals(uri.getHost()) && "/copyright".equals(uri.getPath())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (RuntimeException ignored) { }
                }
                return true;
            }
        });
        String page = ORIGIN + "/assets/map.html";
        if (getIntent().hasExtra("latitude")) {
            try {
                GeoPoint p = new GeoPoint(getIntent().getDoubleExtra("latitude", Double.NaN), getIntent().getDoubleExtra("longitude", Double.NaN));
                page += "?lat=" + p.latitude + "&lng=" + p.longitude;
            } catch (IllegalArgumentException ignored) { }
        }
        web.loadUrl(page);
    }
    private WebResourceResponse denied() {
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
    }
    @Override protected void onDestroy() {
        if (web != null) { web.stopLoading(); web.destroy(); }
        super.onDestroy();
    }
}
