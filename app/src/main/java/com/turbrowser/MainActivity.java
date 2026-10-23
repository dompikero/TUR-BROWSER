package com.turbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String HOME_URL = "file:///android_asset/home.html";
    private static final String PREFS = "tur_browser";
    private final int ink = Color.rgb(24, 34, 52);
    private final int muted = Color.rgb(119, 129, 149);
    private final int cyan = Color.rgb(24, 190, 207);
    private final List<String> blockedHosts = Arrays.asList(
            "doubleclick.net", "googlesyndication.com", "google-analytics.com",
            "googletagmanager.com", "adservice.google.com", "connect.facebook.net",
            "facebook.net", "scorecardresearch.com", "adnxs.com", "taboola.com",
            "outbrain.com", "criteo.com", "ads-twitter.com"
    );

    private android.content.SharedPreferences prefs;
    private LinearLayout root;
    private EditText addressBar;
    private ProgressBar progress;
    private WebView webView;
    private TextView modeLabel;
    private ArrayList<String> tabs = new ArrayList<>();
    private int activeTab = 0;
    private boolean privateMode = false;
    private boolean blockAds = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 26) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        blockAds = prefs.getBoolean("block_ads", true);
        tabs = readList("tabs");
        if (tabs.isEmpty()) {
            tabs.add(HOME_URL);
            activeTab = 0;
        } else {
            activeTab = Math.max(0, Math.min(prefs.getInt("active_tab", tabs.size() - 1), tabs.size() - 1));
        }
        buildUi();
        setupWebView();
        loadTab(activeTab);
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(244, 246, 250));
        root.setClipToPadding(false);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(9), dp(16), dp(8));
        header.setBackgroundColor(Color.WHITE);

        ImageView logo = new ImageView(this);
        logo.setImageResource(com.turbrowser.R.drawable.ic_t_logo);
        header.addView(logo, new LinearLayout.LayoutParams(dp(39), dp(39)));

        LinearLayout brandColumn = new LinearLayout(this);
        brandColumn.setOrientation(LinearLayout.VERTICAL);
        brandColumn.setPadding(dp(10), 0, 0, 0);
        TextView brand = text("TUR Browser", 16, ink, true);
        TextView subtitle = text("Keşfetmeye başla", 10, muted, false);
        brandColumn.addView(brand);
        brandColumn.addView(subtitle);
        header.addView(brandColumn, new LinearLayout.LayoutParams(0, -2, 1));

        modeLabel = text("HIZLI GEZİN", 10, Color.rgb(7, 91, 180), true);
        modeLabel.setPadding(dp(10), dp(7), dp(10), dp(7));
        modeLabel.setBackground(round(Color.rgb(228, 249, 252), 18));
        header.addView(modeLabel);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(60)));

        LinearLayout addressRow = new LinearLayout(this);
        addressRow.setOrientation(LinearLayout.HORIZONTAL);
        addressRow.setGravity(Gravity.CENTER_VERTICAL);
        addressRow.setPadding(dp(14), dp(7), dp(12), dp(7));
        addressRow.setBackground(round(Color.WHITE, 20));
        LinearLayout.LayoutParams addressRowLp = new LinearLayout.LayoutParams(-1, dp(49));
        addressRowLp.setMargins(dp(14), dp(9), dp(14), dp(7));
        addressRow.setElevation(dp(2));

        TextView searchIcon = text("⌕", 27, Color.rgb(65, 148, 200), false);
        searchIcon.setGravity(Gravity.CENTER);
        addressRow.addView(searchIcon, new LinearLayout.LayoutParams(dp(31), -1));

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setTextSize(14);
        addressBar.setTextColor(ink);
        addressBar.setHintTextColor(Color.rgb(150, 159, 175));
        addressBar.setHint("Web adresi veya ara");
        addressBar.setPadding(dp(5), 0, dp(4), 0);
        addressBar.setBackgroundColor(Color.TRANSPARENT);
        addressBar.setSelectAllOnFocus(false);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        addressBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigateInput(addressBar.getText().toString());
                return true;
            }
            return false;
        });
        addressRow.addView(addressBar, new LinearLayout.LayoutParams(0, -1, 1));

        TextView reload = text("↻", 24, Color.rgb(71, 137, 178), false);
        reload.setGravity(Gravity.CENTER);
        reload.setOnClickListener(v -> {
            if (webView == null || HOME_URL.equals(webView.getUrl())) loadHome();
            else webView.reload();
        });
        addressRow.addView(reload, new LinearLayout.LayoutParams(dp(34), -1));
        root.addView(addressRow, addressRowLp);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(18, 181, 211)));
        progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Color.rgb(18, 181, 211)));
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(2)));

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(7), dp(5), dp(7), dp(5));
        bottom.setBackgroundColor(Color.WHITE);
        bottom.setElevation(dp(8));

        bottom.addView(navItem("‹", "Geri", () -> {
            if (webView.canGoBack()) webView.goBack();
            else Toast.makeText(this, "Önceki sayfa yok", Toast.LENGTH_SHORT).show();
        }), new LinearLayout.LayoutParams(0, dp(53), 1));
        bottom.addView(navItem("›", "İleri", () -> {
            if (webView.canGoForward()) webView.goForward();
        }), new LinearLayout.LayoutParams(0, dp(53), 1));
        bottom.addView(homeButton(), new LinearLayout.LayoutParams(0, dp(53), 1.15f));
        bottom.addView(navItem("▣", "Sekmeler", this::showTabsDialog), new LinearLayout.LayoutParams(0, dp(53), 1));
        bottom.addView(navItem("⋯", "Menü", this::showMoreMenu), new LinearLayout.LayoutParams(0, dp(53), 1));
        root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(64)));
        setContentView(root);
        updateModeLabel();
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSafeBrowsingEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setCacheMode(privateMode ? WebSettings.LOAD_NO_CACHE : WebSettings.LOAD_DEFAULT);

        webView.setBackgroundColor(Color.rgb(244, 246, 250));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
                progress.setVisibility(value >= 100 ? View.GONE : View.VISIBLE);
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("tur".equalsIgnoreCase(uri.getScheme())) {
                    String action = uri.getHost();
                    if ("home".equals(action)) loadHome();
                    else if ("bookmarks".equals(action)) showSavedPages("bookmarks", "Yer imleri", true);
                    else if ("history".equals(action)) showSavedPages("history", "Geçmiş", false);
                    return true;
                }
                return false;
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (blockAds && isBlocked(request.getUrl())) {
                    return new WebResourceResponse("text/plain", "UTF-8", 204, "Blocked",
                            Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
                if (url != null && !url.startsWith("file:")) addressBar.setText(url);
            }

            @Override public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                if (HOME_URL.equals(url) || (url != null && url.startsWith("file:///android_asset/home.html"))) {
                    addressBar.setText("");
                } else if (url != null) {
                    addressBar.setText(url);
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        if (!privateMode) addHistory(url);
                    }
                }
                if (activeTab >= 0 && activeTab < tabs.size()) {
                    tabs.set(activeTab, HOME_URL.equals(url) ? HOME_URL : url);
                    saveList("tabs", tabs);
                    prefs.edit().putInt("active_tab", activeTab).apply();
                }
            }
        });
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                enqueueDownload(url, userAgent, contentDisposition, mimeType));
    }

    private boolean isBlocked(Uri uri) {
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        for (String blocked : blockedHosts) {
            if (host.equals(blocked) || host.endsWith("." + blocked)) return true;
        }
        return false;
    }

    private void navigateInput(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return;
        hideKeyboard();
        if (value.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) {
            loadRequestedUrl(value);
        } else if (!value.contains(" ") && value.contains(".")) {
            loadRequestedUrl("https://" + value);
        } else {
            String engine = prefs.getString("search_engine", "Google");
            String base;
            switch (engine) {
                case "Bing": base = "https://www.bing.com/search?q="; break;
                case "DuckDuckGo": base = "https://duckduckgo.com/?q="; break;
                case "Startpage": base = "https://www.startpage.com/sp/search?query="; break;
                default: base = "https://www.google.com/search?q="; break;
            }
            try {
                loadRequestedUrl(base + URLEncoder.encode(value, "UTF-8"));
            } catch (Exception e) {
                loadRequestedUrl("https://www.google.com/search?q=" + value.replace(" ", "+"));
            }
        }
    }

    private void loadRequestedUrl(String url) {
        if (tabs.isEmpty()) tabs.add(HOME_URL);
        if (activeTab < 0 || activeTab >= tabs.size()) activeTab = tabs.size() - 1;
        tabs.set(activeTab, url);
        saveList("tabs", tabs);
        addressBar.setText(url);
        webView.loadUrl(url);
    }

    private void loadHome() {
        if (webView == null) return;
        addressBar.setText("");
        webView.loadUrl(HOME_URL);
        if (activeTab >= 0 && activeTab < tabs.size()) {
            tabs.set(activeTab, HOME_URL);
            saveList("tabs", tabs);
        }
    }

    private void newTab() {
        tabs.add(HOME_URL);
        activeTab = tabs.size() - 1;
        saveList("tabs", tabs);
        prefs.edit().putInt("active_tab", activeTab).apply();
        loadHome();
        Toast.makeText(this, "Yeni sekme açıldı", Toast.LENGTH_SHORT).show();
    }

    private void loadTab(int index) {
        if (tabs.isEmpty()) tabs.add(HOME_URL);
        activeTab = Math.max(0, Math.min(index, tabs.size() - 1));
        prefs.edit().putInt("active_tab", activeTab).apply();
        String url = tabs.get(activeTab);
        if (url == null || url.isEmpty() || HOME_URL.equals(url)) loadHome();
        else {
            addressBar.setText(url);
            webView.loadUrl(url);
        }
    }

    private View navItem(String icon, String label, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        TextView glyph = text(icon, 23, Color.rgb(80, 100, 126), false);
        glyph.setGravity(Gravity.CENTER);
        TextView caption = text(label, 9, muted, false);
        caption.setGravity(Gravity.CENTER);
        item.addView(glyph, new LinearLayout.LayoutParams(-1, dp(29)));
        item.addView(caption);
        item.setOnClickListener(v -> action.run());
        return item;
    }

    private View homeButton() {
        FrameLayout frame = new FrameLayout(this);
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(20, 217, 221), Color.rgb(7, 79, 211)});
        gradient.setShape(GradientDrawable.OVAL);
        frame.setBackground(gradient);
        frame.setElevation(dp(4));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_t_logo);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(39), dp(39), Gravity.CENTER);
        frame.addView(icon, lp);
        frame.setOnClickListener(v -> loadHome());
        frame.setContentDescription("TUR ana sayfa");
        return frame;
    }

    private void showMoreMenu() {
        String engine = prefs.getString("search_engine", "Google");
        String[] options = {
                "Yer imleri",
                "Geçmiş",
                "İndirilenler",
                privateMode ? "Gizli modu kapat" : "Gizli modu aç",
                "Reklam/izleyici engeli: " + (blockAds ? "Açık" : "Kapalı"),
                "Arama motoru: " + engine,
                "Ayarlar"
        };
        new AlertDialog.Builder(this)
                .setTitle("TUR Browser")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: showSavedPages("bookmarks", "Yer imleri", true); break;
                        case 1: showSavedPages("history", "Geçmiş", false); break;
                        case 2: showDownloads(); break;
                        case 3: togglePrivateMode(); break;
                        case 4:
                            blockAds = !blockAds;
                            prefs.edit().putBoolean("block_ads", blockAds).apply();
                            Toast.makeText(this, "İstek engelleme " + (blockAds ? "açık" : "kapalı"), Toast.LENGTH_SHORT).show();
                            break;
                        case 5: showSearchEngineDialog(); break;
                        case 6: showSettings(); break;
                    }
                }).show();
    }

    private void showSettings() {
        String[] options = {"Arama motorunu seç", "Reklam ve izleyici engelleme", "Geçmişi temizle", "Önbelleği temizle", "TUR Browser hakkında"};
        new AlertDialog.Builder(this)
                .setTitle("Ayarlar")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: showSearchEngineDialog(); break;
                        case 1:
                            blockAds = !blockAds;
                            prefs.edit().putBoolean("block_ads", blockAds).apply();
                            Toast.makeText(this, "Engelleme " + (blockAds ? "açık" : "kapalı"), Toast.LENGTH_SHORT).show();
                            break;
                        case 2:
                            saveList("history", new ArrayList<>());
                            Toast.makeText(this, "Geçmiş temizlendi", Toast.LENGTH_SHORT).show();
                            break;
                        case 3:
                            webView.clearCache(true);
                            Toast.makeText(this, "Önbellek temizlendi", Toast.LENGTH_SHORT).show();
                            break;
                        case 4:
                            new AlertDialog.Builder(this).setTitle("TUR Browser")
                                    .setMessage("Turkuaz-mavi T tasarımıyla hafif Android tarayıcısı. VPN özelliği içermez. Reklam/izleyici engelleme temel bir alan adı listesini kullanır.")
                                    .setPositiveButton("Tamam", null).show();
                            break;
                    }
                }).show();
    }

    private void showSearchEngineDialog() {
        String[] engines = {"Google", "Bing", "DuckDuckGo", "Startpage"};
        String selected = prefs.getString("search_engine", "Google");
        int checked = Arrays.asList(engines).indexOf(selected);
        new AlertDialog.Builder(this)
                .setTitle("Varsayılan arama motoru")
                .setSingleChoiceItems(engines, checked, (dialog, which) -> {
                    prefs.edit().putString("search_engine", engines[which]).apply();
                    dialog.dismiss();
                    Toast.makeText(this, engines[which] + " seçildi", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void showSavedPages(String key, String title, boolean canSaveCurrent) {
        ArrayList<String> items = readList(key);
        String[] labels = new String[items.size()];
        for (int i = 0; i < items.size(); i++) labels[i] = items.get(i);
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(title);
        if (items.isEmpty()) {
            builder.setMessage("Henüz kayıt yok.");
        } else {
            builder.setItems(labels, (dialog, which) -> loadRequestedUrl(items.get(which)));
        }
        if (canSaveCurrent) {
            builder.setPositiveButton("Bu sayfayı kaydet", (dialog, which) -> saveCurrentBookmark());
        }
        if (!items.isEmpty()) {
            builder.setNeutralButton("Temizle", (dialog, which) -> {
                saveList(key, new ArrayList<>());
                Toast.makeText(this, title + " temizlendi", Toast.LENGTH_SHORT).show();
            });
        }
        builder.setNegativeButton("Kapat", null).show();
    }

    private void saveCurrentBookmark() {
        String url = webView.getUrl();
        if (privateMode) {
            Toast.makeText(this, "Gizli modda yer imi kaydedilmez", Toast.LENGTH_SHORT).show();
            return;
        }
        if (url == null || !url.startsWith("http")) {
            Toast.makeText(this, "Önce bir web sayfası aç", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<String> list = readList("bookmarks");
        list.remove(url);
        list.add(0, url);
        saveList("bookmarks", list);
        Toast.makeText(this, "Yer imlerine eklendi", Toast.LENGTH_SHORT).show();
    }

    private void showDownloads() {
        ArrayList<String> items = readList("downloads");
        String[] labels = new String[items.size()];
        for (int i = 0; i < items.size(); i++) labels[i] = items.get(i);
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle("İndirilenler");
        if (items.isEmpty()) builder.setMessage("Henüz indirme başlatılmadı.");
        else builder.setItems(labels, (dialog, which) -> Toast.makeText(this, items.get(which), Toast.LENGTH_LONG).show());
        builder.setNegativeButton("Kapat", null).show();
    }

    private void showTabsDialog() {
        String[] labels = new String[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) {
            String url = tabs.get(i);
            labels[i] = (i == activeTab ? "●  " : "○  ") +
                    (url == null || HOME_URL.equals(url) ? "Yeni sekme" : shortUrl(url));
        }
        new AlertDialog.Builder(this)
                .setTitle("Sekmeler (" + tabs.size() + ")")
                .setItems(labels, (dialog, which) -> loadTab(which))
                .setPositiveButton("Yeni sekme +", (dialog, which) -> newTab())
                .setNeutralButton("Diğerlerini kapat", (dialog, which) -> {
                    String keep = tabs.get(activeTab);
                    tabs.clear();
                    tabs.add(keep == null ? HOME_URL : keep);
                    activeTab = 0;
                    saveList("tabs", tabs);
                    loadTab(0);
                })
                .setNegativeButton("Kapat", null)
                .show();
    }

    private String shortUrl(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            return host == null ? url : host;
        } catch (Exception e) { return url; }
    }

    private void togglePrivateMode() {
        privateMode = !privateMode;
        CookieManager cookieManager = CookieManager.getInstance();
        if (privateMode) {
            cookieManager.removeAllCookies(null);
            cookieManager.flush();
            cookieManager.setAcceptCookie(false);
            webView.clearCache(true);
            webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
            Toast.makeText(this, "Gizli mod açık · Geçmiş kaydedilmeyecek", Toast.LENGTH_LONG).show();
        } else {
            cookieManager.setAcceptCookie(true);
            webView.clearCache(true);
            webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
            Toast.makeText(this, "Normal gezinme açık", Toast.LENGTH_SHORT).show();
        }
        updateModeLabel();
        loadHome();
    }

    private void updateModeLabel() {
        if (modeLabel == null) return;
        if (privateMode) {
            modeLabel.setText("GİZLİ MOD");
            modeLabel.setTextColor(Color.rgb(105, 66, 180));
            modeLabel.setBackground(round(Color.rgb(243, 235, 255), 18));
        } else {
            modeLabel.setText(blockAds ? "KORUMA AÇIK" : "HIZLI GEZİN");
            modeLabel.setTextColor(Color.rgb(7, 91, 180));
            modeLabel.setBackground(round(Color.rgb(228, 249, 252), 18));
        }
    }

    private void enqueueDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(filename);
            request.setDescription("TUR Browser indiriyor");
            request.setMimeType(mimeType);
            if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null && !privateMode) request.addRequestHeader("Cookie", cookie);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "TUR-Browser/" + filename);
            request.setAllowedOverMetered(true);
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            manager.enqueue(request);
            ArrayList<String> list = readList("downloads");
            list.remove(filename);
            list.add(0, filename);
            saveList("downloads", list);
            Toast.makeText(this, "İndirme başladı: " + filename, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "İndirme başlatılamadı", Toast.LENGTH_SHORT).show();
        }
    }

    private void addHistory(String url) {
        ArrayList<String> list = readList("history");
        list.remove(url);
        list.add(0, url);
        while (list.size() > 100) list.remove(list.size() - 1);
        saveList("history", list);
    }

    private ArrayList<String> readList(String key) {
        ArrayList<String> out = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(key, "[]"));
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "");
                if (!value.isEmpty()) out.add(value);
            }
        } catch (Exception ignored) { }
        return out;
    }

    private void saveList(String key, ArrayList<String> values) {
        JSONArray array = new JSONArray();
        for (String item : values) array.put(item);
        prefs.edit().putString(key, array.toString()).apply();
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(addressBar.getWindowToken(), 0);
            addressBar.clearFocus();
        } catch (Exception ignored) { }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else if (webView != null && !HOME_URL.equals(webView.getUrl())) loadHome();
        else super.onBackPressed();
    }

    @Override protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
