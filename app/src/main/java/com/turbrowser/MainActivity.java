package com.turbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.database.Cursor;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.util.Xml;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String HOME_URL = "file:///android_asset/home.html";
    private static final String HOME_EN_URL = "file:///android_asset/home_en.html";
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
    private boolean doNotTrack = true;
    private String dntPendingUrl = null;
    private LinearLayout headerLayout;
    private LinearLayout addressRowView;
    private TextView brandTitle;
    private TextView brandSubtitle;
    private FrameLayout webStage;
    private View toolsScrim;
    private LinearLayout toolsPanel;
    private FrameLayout actionButtonFrame;
    private ImageView actionLogo;
    private TextView actionCloseGlyph;
    private boolean toolsOpen = false;
    private Dialog tabsDialog;
    private int newsRequestToken = 0;
    private final Handler downloadUiHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<DownloadVisual> downloadVisuals = new ArrayList<>();
    private Runnable downloadPollRunnable;
    private Dialog downloadsDialog;

    private static class DownloadSnapshot {
        int status = DownloadManager.STATUS_SUCCESSFUL;
        long bytes = 0, total = -1, date = 0;
    }
    private static class DownloadVisual {
        long id; String name; ProgressBar bar; TextView percent, details, state;
    }

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
        doNotTrack = prefs.getBoolean("do_not_track", true);
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
        headerLayout = header;

        ImageView logo = new ImageView(this);
        logo.setImageResource(com.turbrowser.R.drawable.ic_t_logo);
        header.addView(logo, new LinearLayout.LayoutParams(dp(39), dp(39)));

        LinearLayout brandColumn = new LinearLayout(this);
        brandColumn.setOrientation(LinearLayout.VERTICAL);
        brandColumn.setPadding(dp(10), 0, 0, 0);
        brandTitle = text("TUR Browser", 16, ink, true);
        brandSubtitle = text(L("Keşfetmeye başla", "Start exploring"), 10, muted, false);
        brandColumn.addView(brandTitle);
        brandColumn.addView(brandSubtitle);
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
        addressRowView = addressRow;
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
        addressBar.setHint(L("Web adresi veya ara", "Search or enter address"));
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

        webStage = new FrameLayout(this);
        webView = new WebView(this);
        webStage.addView(webView, new FrameLayout.LayoutParams(-1, -1));

        toolsScrim = new View(this);
        toolsScrim.setBackgroundColor(0x260B1425);
        toolsScrim.setAlpha(0f);
        toolsScrim.setVisibility(View.GONE);
        toolsScrim.setOnClickListener(v -> closeToolsPanel());
        webStage.addView(toolsScrim, new FrameLayout.LayoutParams(-1, -1));

        toolsPanel = buildToolsPanel();
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(-1, dp(226), Gravity.BOTTOM);
        panelParams.leftMargin = dp(8);
        panelParams.rightMargin = dp(8);
        panelParams.bottomMargin = dp(6);
        toolsPanel.setVisibility(View.GONE);
        toolsPanel.setAlpha(0f);
        toolsPanel.setTranslationY(dp(26));
        webStage.addView(toolsPanel, panelParams);
        root.addView(webStage, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(7), dp(5), dp(7), dp(5));
        bottom.setBackgroundColor(Color.WHITE);
        bottom.setElevation(dp(8));

        bottom.addView(navItem("‹", L("Geri", "Back"), () -> {
            if (webView.canGoBack()) webView.goBack();
            else Toast.makeText(this, "Önceki sayfa yok", Toast.LENGTH_SHORT).show();
        }), new LinearLayout.LayoutParams(0, dp(53), 1));
        bottom.addView(navItem("›", L("İleri", "Forward"), () -> {
            if (webView.canGoForward()) webView.goForward();
        }), new LinearLayout.LayoutParams(0, dp(53), 1));
        bottom.addView(actionButton(), new LinearLayout.LayoutParams(0, dp(53), 1.15f));
        bottom.addView(tabsButton(), new LinearLayout.LayoutParams(0, dp(53), 1));
        bottom.addView(homeNavItem(), new LinearLayout.LayoutParams(0, dp(53), 1));
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
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setCacheMode(privateMode ? WebSettings.LOAD_NO_CACHE : WebSettings.LOAD_DEFAULT);
        settings.setTextZoom(prefs.getInt("text_zoom", 100));

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
                    else if ("bookmarks".equals(action)) showSavedPages("bookmarks", L("Yer imleri", "Bookmarks"), true);
                    else if ("history".equals(action)) showSavedPages("history", L("Geçmiş", "History"), false);
                    else if ("news-refresh".equals(action)) loadNewsFeed(view);
                    return true;
                }
                String target = uri.toString();
                if (doNotTrack && request.isForMainFrame() &&
                        (target.startsWith("http://") || target.startsWith("https://"))) {
                    if (target.equals(dntPendingUrl)) {
                        dntPendingUrl = null;
                        return false;
                    }
                    Map<String, String> headers = new HashMap<>();
                    headers.put("DNT", "1");
                    headers.put("Sec-GPC", "1");
                    dntPendingUrl = target;
                    view.loadUrl(target, headers);
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
                view.animate().alpha(0.94f).setDuration(120).start();
                if (url != null && !url.startsWith("file:")) addressBar.setText(url);
                if (url != null && url.equals(dntPendingUrl)) dntPendingUrl = null;
                if (doNotTrack && url != null && url.startsWith("http")) {
                    view.evaluateJavascript("(function(){try{Object.defineProperty(navigator,'doNotTrack',{get:function(){return '1'}});Object.defineProperty(navigator,'globalPrivacyControl',{get:function(){return true}});}catch(e){}})();", null);
                }
            }

            @Override public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                view.animate().alpha(1f).setDuration(220).start();
                if (isHomeUrl(url)) {
                    addressBar.setText("");
                    loadNewsFeed(view);
                } else if (url != null) {
                    addressBar.setText(url);
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        if (!privateMode) addHistory(url);
                    }
                }
                if (activeTab >= 0 && activeTab < tabs.size()) {
                    tabs.set(activeTab, isHomeUrl(url) ? HOME_URL : url);
                    if (!privateMode) {
                        saveList("tabs", tabs);
                        prefs.edit().putInt("active_tab", activeTab).apply();
                    }
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
        if (!privateMode) saveList("tabs", tabs);
        addressBar.setText(url);
        if (doNotTrack && (url.startsWith("http://") || url.startsWith("https://"))) {
            Map<String, String> headers = new HashMap<>();
            headers.put("DNT", "1");
            headers.put("Sec-GPC", "1");
            dntPendingUrl = url;
            webView.loadUrl(url, headers);
        } else webView.loadUrl(url);
    }

    private void loadHome() {
        if (webView == null) return;
        addressBar.setText("");
        String home = "English".equals(prefs.getString("language", "Türkçe")) ? HOME_EN_URL : HOME_URL;
        webView.loadUrl(home);
        if (activeTab >= 0 && activeTab < tabs.size()) {
            tabs.set(activeTab, HOME_URL);
            if (!privateMode) saveList("tabs", tabs);
        }
    }

    private void newTab() {
        tabs.add(HOME_URL);
        activeTab = tabs.size() - 1;
        if (!privateMode) {
            saveList("tabs", tabs);
            prefs.edit().putInt("active_tab", activeTab).apply();
        }
        loadHome();
        Toast.makeText(this, L("Yeni sekme açıldı", "New tab opened"), Toast.LENGTH_SHORT).show();
    }

    private void loadTab(int index) {
        if (tabs.isEmpty()) tabs.add(HOME_URL);
        activeTab = Math.max(0, Math.min(index, tabs.size() - 1));
        if (!privateMode) prefs.edit().putInt("active_tab", activeTab).apply();
        String url = tabs.get(activeTab);
        if (url == null || url.isEmpty() || isHomeUrl(url)) loadHome();
        else {
            addressBar.setText(url);
            loadRequestedUrl(url);
        }
    }

    private View tabsButton() {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        FrameLayout stack = new FrameLayout(this);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_tabs_stacked);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        stack.addView(icon, new FrameLayout.LayoutParams(dp(25), dp(25), Gravity.CENTER));
        TextView count = text(String.valueOf(Math.min(99, tabs.size())), 8, Color.WHITE, true);
        count.setGravity(Gravity.CENTER);
        count.setBackground(round(Color.rgb(18,180,199), 20));
        stack.addView(count, new FrameLayout.LayoutParams(dp(17), dp(17), Gravity.RIGHT | Gravity.TOP));
        item.addView(stack, new LinearLayout.LayoutParams(dp(38), dp(30)));
        TextView label = text(L("Sekmeler","Tabs"), 9, muted, false);
        label.setGravity(Gravity.CENTER);
        item.addView(label);
        item.setOnClickListener(v -> animateAction(item, this::showTabsDialog));
        return item;
    }

    private View homeNavItem() {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        ImageView home = new ImageView(this);
        home.setImageResource(R.drawable.ic_home_filled);
        home.setScaleType(ImageView.ScaleType.FIT_CENTER);
        item.addView(home, new LinearLayout.LayoutParams(dp(27), dp(29)));
        TextView label = text(L("Ana sayfa","Home"), 9, muted, false);
        label.setGravity(Gravity.CENTER);
        item.addView(label);
        item.setOnClickListener(v -> animateAction(item, this::loadHome));
        return item;
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
        item.setBackground(round(Color.WHITE, 16));
        item.setOnClickListener(v -> animateAction(item, action));
        return item;
    }

    private View actionButton() {
        actionButtonFrame = new FrameLayout(this);
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(20, 217, 221), Color.rgb(7, 79, 211)});
        gradient.setShape(GradientDrawable.OVAL);
        actionButtonFrame.setBackground(gradient);
        actionButtonFrame.setElevation(dp(5));
        actionLogo = new ImageView(this);
        actionLogo.setImageResource(R.drawable.ic_t_logo);
        actionLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams logoLp = new FrameLayout.LayoutParams(dp(45), dp(45), Gravity.CENTER);
        actionButtonFrame.addView(actionLogo, logoLp);
        actionCloseGlyph = text("×", 36, Color.WHITE, false);
        actionCloseGlyph.setGravity(Gravity.CENTER);
        actionCloseGlyph.setVisibility(View.GONE);
        actionButtonFrame.addView(actionCloseGlyph, new FrameLayout.LayoutParams(-1, -1));
        actionButtonFrame.setContentDescription(L("Araçlar panelini aç", "Open tools panel"));
        actionButtonFrame.setOnClickListener(v -> {
            actionButtonFrame.animate().scaleX(0.88f).scaleY(0.88f).setDuration(95).withEndAction(() -> {
                toggleToolsPanel();
                actionButtonFrame.animate().scaleX(1f).scaleY(1f).setDuration(170).start();
            }).start();
        });
        return actionButtonFrame;
    }

    private LinearLayout buildToolsPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(11), dp(10), dp(8));
        panel.setBackground(round(Color.WHITE, 27));
        panel.setElevation(dp(15));

        LinearLayout rowOne = new LinearLayout(this);
        rowOne.setOrientation(LinearLayout.HORIZONTAL);
        rowOne.setGravity(Gravity.CENTER);
        rowOne.addView(toolCell("◷", L("Geçmiş", "History"), 0, () -> showSavedPages("history", L("Geçmiş", "History"), false)),
                new LinearLayout.LayoutParams(0, dp(91), 1));
        rowOne.addView(toolCell("☆", L("Yer imleri", "Bookmark"), 1, () -> showSavedPages("bookmarks", L("Yer imleri", "Bookmarks"), true)),
                new LinearLayout.LayoutParams(0, dp(91), 1));
        rowOne.addView(toolCell("⇩", L("İndirme", "Download"), 2, this::showDownloads),
                new LinearLayout.LayoutParams(0, dp(91), 1));
        rowOne.addView(toolCell("◉", L("Gizli mod", "Incognito"), 3, this::togglePrivateMode),
                new LinearLayout.LayoutParams(0, dp(91), 1));

        LinearLayout rowTwo = new LinearLayout(this);
        rowTwo.setOrientation(LinearLayout.HORIZONTAL);
        rowTwo.setGravity(Gravity.CENTER);
        rowTwo.addView(toolCell("↗", L("Paylaş", "Share"), 4, this::shareCurrentPage),
                new LinearLayout.LayoutParams(0, dp(91), 1));
        rowTwo.addView(toolCell("＋", L("Yer imi ekle", "Add to"), 5, this::saveCurrentBookmark),
                new LinearLayout.LayoutParams(0, dp(91), 1));
        rowTwo.addView(toolCell("↻", L("Yenile", "Refresh"), 6, () -> {
            if (webView != null) webView.reload();
        }), new LinearLayout.LayoutParams(0, dp(91), 1));
        rowTwo.addView(toolCell("⚙", L("Araçlar", "Tools"), 7, this::showSettings),
                new LinearLayout.LayoutParams(0, dp(91), 1));

        panel.addView(rowOne, new LinearLayout.LayoutParams(-1, dp(91)));
        panel.addView(rowTwo, new LinearLayout.LayoutParams(-1, dp(91)));
        return panel;
    }

    private View toolCell(String icon, String label, int index, Runnable action) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(2), dp(5), dp(2), dp(4));
        int[] tileColors = {0xFFE9F8FF, 0xFFEAFBF4, 0xFFFFF3E9, 0xFFF0EAFE,
                0xFFEAF1FF, 0xFFFFF7E2, 0xFFE7FBFC, 0xFFF1F3F8};
        TextView glyph = text(icon, 23, index == 3 && privateMode ? Color.rgb(119, 69, 190) : Color.rgb(45, 93, 127), true);
        glyph.setGravity(Gravity.CENTER);
        glyph.setBackground(round(tileColors[Math.max(0, Math.min(index, tileColors.length - 1))], 16));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        iconLp.bottomMargin = dp(5);
        cell.addView(glyph, iconLp);
        TextView caption = text(label, 9, ink, false);
        caption.setGravity(Gravity.CENTER);
        cell.addView(caption, new LinearLayout.LayoutParams(-1, -2));
        cell.setOnClickListener(v -> animateAction(cell, () -> {
            closeToolsPanel();
            action.run();
        }));
        return cell;
    }

    private void toggleToolsPanel() {
        if (toolsPanel == null || toolsOpen) {
            if (toolsOpen) closeToolsPanel();
            return;
        }
        toolsOpen = true;
        updateActionButton();
        toolsScrim.setVisibility(View.VISIBLE);
        toolsScrim.setAlpha(0f);
        toolsScrim.animate().alpha(1f).setDuration(220).start();
        toolsPanel.setVisibility(View.VISIBLE);
        toolsPanel.setAlpha(0f);
        toolsPanel.setTranslationY(dp(25));
        toolsPanel.animate().alpha(1f).translationY(0f).setDuration(260).start();
    }

    private void closeToolsPanel() {
        if (!toolsOpen || toolsPanel == null) return;
        toolsOpen = false;
        updateActionButton();
        toolsScrim.animate().alpha(0f).setDuration(170).withEndAction(() -> toolsScrim.setVisibility(View.GONE)).start();
        toolsPanel.animate().alpha(0f).translationY(dp(22)).setDuration(190).withEndAction(() -> {
            toolsPanel.setVisibility(View.GONE);
            toolsPanel.setTranslationY(dp(26));
        }).start();
    }

    private void updateActionButton() {
        if (actionLogo == null || actionCloseGlyph == null || actionButtonFrame == null) return;
        actionLogo.setVisibility(toolsOpen ? View.GONE : View.VISIBLE);
        actionCloseGlyph.setVisibility(toolsOpen ? View.VISIBLE : View.GONE);
        actionButtonFrame.setContentDescription(toolsOpen ? L("Araçlar panelini kapat", "Close tools panel") : L("Araçlar panelini aç", "Open tools panel"));
    }

    private void animateAction(View view, Runnable action) {
        view.animate().cancel();
        view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).withEndAction(() ->
                view.animate().scaleX(1f).scaleY(1f).setDuration(130).withEndAction(action).start()
        ).start();
    }

    private Dialog showModernPage(String title, String subtitle, LinearLayout body) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Material_Light_NoActionBar);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(244, 246, 250));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(12), dp(16), dp(12));
        header.setBackgroundColor(Color.WHITE);
        TextView back = text("‹", 34, ink, false);
        back.setGravity(Gravity.CENTER);
        back.setBackground(round(0xFFF1F5FA, 18));
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
        back.setOnClickListener(v -> dialog.dismiss());

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(12), 0, 0, 0);
        heading.addView(text(title, 18, ink, true));
        TextView sub = text(subtitle, 11, muted, false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(3);
        heading.addView(sub, subLp);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = text("×", 28, muted, false);
        close.setGravity(Gravity.CENTER);
        close.setBackground(round(0xFFF1F5FA, 18));
        header.addView(close, new LinearLayout.LayoutParams(dp(38), dp(38)));
        close.setOnClickListener(v -> dialog.dismiss());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(14), dp(13), dp(14), dp(20));
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        page.addView(header, new LinearLayout.LayoutParams(-1, dp(70)));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        dialog.setContentView(page);
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setLayout(-1, -1);
                page.setAlpha(0f);
                page.setTranslationY(dp(14));
                page.animate().alpha(1f).translationY(0f).setDuration(220).start();
            }
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(-1, -1);
        }
        return dialog;
    }

    private void sectionLabel(LinearLayout body, String label) {
        TextView title = text(label.toUpperCase(Locale.ROOT), 10, muted, true);
        title.setPadding(dp(4), dp(13), dp(4), dp(8));
        body.addView(title);
    }

    private void settingRow(LinearLayout body, String icon, String title, String detail, String value, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackground(round(Color.WHITE, 20));
        row.setElevation(dp(1));

        TextView glyph = text(icon, 20, Color.rgb(38, 110, 157), true);
        glyph.setGravity(Gravity.CENTER);
        glyph.setBackground(round(0xFFE7F8FC, 14));
        row.addView(glyph, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(11), 0, dp(5), 0);
        labels.addView(text(title, 13, ink, true));
        TextView detailView = text(detail, 10, muted, false);
        detailView.setMaxLines(2);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, -2);
        detailLp.topMargin = dp(4);
        labels.addView(detailView, detailLp);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        if (value != null && !value.isEmpty()) {
            TextView val = text(value, 10, Color.rgb(12, 139, 166), true);
            val.setGravity(Gravity.CENTER);
            row.addView(val, new LinearLayout.LayoutParams(-2, -2));
        } else {
            TextView arrow = text("›", 25, muted, false);
            arrow.setGravity(Gravity.CENTER);
            row.addView(arrow, new LinearLayout.LayoutParams(dp(22), -2));
        }
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.bottomMargin = dp(9);
        body.addView(row, rowLp);
        row.setOnClickListener(v -> animateAction(row, action));
    } 

    private void showMoreMenu() {
        toggleToolsPanel();
    }

    private void showSettings() {
        LinearLayout body = new LinearLayout(this);
        sectionLabel(body, L("Genel", "GENERAL"));
        final Dialog[] ref = new Dialog[1];
        settingRow(body, "⌕", L("Arama motoru", "Search engine"),
                L("Arama sonuçları için varsayılan sağlayıcı", "Default provider for search results"),
                prefs.getString("search_engine", "Google"), () -> {
                    ref[0].dismiss(); showSearchEngineDialog();
                });
        settingRow(body, "文", L("Uygulama dili", "App language"),
                L("Türkçe veya İngilizce", "Turkish or English"),
                prefs.getString("language", "Türkçe"), () -> {
                    ref[0].dismiss(); showLanguageDialog();
                });
        settingRow(body, "Aa", L("Yazı boyutu", "Text size"),
                L("Web sayfalarındaki yazıları büyüt veya küçült", "Scale text on webpages"),
                prefs.getInt("text_zoom", 100) + "%", () -> {
                    ref[0].dismiss(); showFontSizeDialog();
                });

        sectionLabel(body, L("Gizlilik ve güvenlik", "PRIVACY & SECURITY"));
        settingRow(body, "◈", L("Reklam ve izleyici engelleme", "Ad and tracker blocking"),
                L("Bilinen bazı izleyici alan adlarını engeller", "Blocks some known tracker domains"),
                blockAds ? L("Açık", "On") : L("Kapalı", "Off"), () -> {
                    blockAds = !blockAds;
                    prefs.edit().putBoolean("block_ads", blockAds).apply();
                    updateModeLabel(); ref[0].dismiss(); showSettings();
                });
        settingRow(body, "◎", L("Do Not Track sinyali", "Do Not Track signal"),
                L("Siteler bu isteği yok sayabilir", "Websites may ignore this signal"),
                doNotTrack ? L("Açık", "On") : L("Kapalı", "Off"), () -> {
                    doNotTrack = !doNotTrack;
                    prefs.edit().putBoolean("do_not_track", doNotTrack).apply();
                    ref[0].dismiss(); showSettings();
                });
        settingRow(body, "✓", L("Güvenlik durumu", "Security status"),
                L("Bağlantı ve tarayıcı koruması hakkında bilgi", "Connection and browser protection details"),
                "", () -> { ref[0].dismiss(); showSecurityDialog(); });

        sectionLabel(body, L("Veriler", "DATA"));
        settingRow(body, "◷", L("Geçmişi temizle", "Clear history"),
                L("Kaydedilmiş gezinme geçmişini sil", "Remove saved browsing history"), "", () -> {
                    saveList("history", new ArrayList<>());
                    Toast.makeText(this, L("Geçmiş temizlendi", "History cleared"), Toast.LENGTH_SHORT).show();
                    ref[0].dismiss(); showSettings();
                });
        settingRow(body, "⌫", L("Önbelleği temizle", "Clear cache"),
                L("Saklanan geçici site verilerini temizle", "Clear temporary site data"), "", () -> {
                    webView.clearCache(true);
                    Toast.makeText(this, L("Önbellek temizlendi", "Cache cleared"), Toast.LENGTH_SHORT).show();
                    ref[0].dismiss(); showSettings();
                });
        settingRow(body, "✦", L("Tarama verilerini temizle", "Clear browsing data"),
                L("Geçmiş, önbellek ve çerezleri temizle", "Clear history, cache and cookies"), "", () -> {
                    saveList("history", new ArrayList<>());
                    webView.clearCache(true);
                    CookieManager.getInstance().removeAllCookies(null);
                    Toast.makeText(this, L("Tarama verileri temizlendi", "Browsing data cleared"), Toast.LENGTH_SHORT).show();
                    ref[0].dismiss(); showSettings();
                });
        ref[0] = showModernPage(L("Ayarlar", "Settings"), L("Tarayıcı deneyimini kendine göre düzenle", "Personalise your browsing experience"), body);
    }    private void showSearchEngineDialog() {
        LinearLayout body = new LinearLayout(this);
        String[] engines = {"Google", "Bing", "DuckDuckGo", "Startpage"};
        String[] descriptions = {
                L("Hızlı ve genel web araması", "Fast general web search"),
                L("Microsoft arama motoru", "Microsoft search engine"),
                L("Gizlilik odaklı arama", "Privacy-focused search"),
                L("Ek gizlilik seçenekleri olan arama", "Search with additional privacy options")
        };
        final Dialog[] ref = new Dialog[1];
        for (int i = 0; i < engines.length; i++) {
            final String engine = engines[i];
            boolean selected = engine.equals(prefs.getString("search_engine", "Google"));
            settingRow(body, selected ? "●" : "○", engine, descriptions[i],
                    selected ? L("Seçili", "Selected") : "", () -> {
                        prefs.edit().putString("search_engine", engine).apply();
                        ref[0].dismiss();
                        Toast.makeText(this, engine + L(" seçildi", " selected"), Toast.LENGTH_SHORT).show();
                    });
        }
        ref[0] = showModernPage(L("Arama motoru", "Search engine"), L("Web araması için bir sağlayıcı seç", "Choose a provider for web searches"), body);
    }

    private void showLanguageDialog() {
        LinearLayout body = new LinearLayout(this);
        String current = prefs.getString("language", "Türkçe");
        final Dialog[] ref = new Dialog[1];
        settingRow(body, "🇹🇷", "Türkçe", L("Menüler ve yeni sekme Türkçe", "Menus and new tab in Turkish"),
                "Türkçe".equals(current) ? L("Seçili", "Selected") : "", () -> {
                    prefs.edit().putString("language", "Türkçe").apply();
                    ref[0].dismiss(); recreate();
                });
        settingRow(body, "EN", "English", L("Menus and new tab in English", "Menus and new tab in English"),
                "English".equals(current) ? L("Seçili", "Selected") : "", () -> {
                    prefs.edit().putString("language", "English").apply();
                    ref[0].dismiss(); recreate();
                });
        ref[0] = showModernPage(L("Uygulama dili", "App language"), L("Kullanmak istediğin dili seç", "Choose your preferred language"), body);
    }

    private void showFontSizeDialog() {
        LinearLayout body = new LinearLayout(this);
        int[] sizes = {80, 90, 100, 110, 120, 130, 140};
        final Dialog[] ref = new Dialog[1];
        int selectedSize = prefs.getInt("text_zoom", 100);
        for (int size : sizes) {
            final int finalSize = size;
            settingRow(body, "Aa", size + "%", L("Web sayfası metin ölçeği", "Webpage text scale"),
                    size == selectedSize ? L("Seçili", "Selected") : "", () -> {
                        prefs.edit().putInt("text_zoom", finalSize).apply();
                        webView.getSettings().setTextZoom(finalSize);
                        ref[0].dismiss();
                        Toast.makeText(this, L("Yazı boyutu güncellendi", "Text size updated"), Toast.LENGTH_SHORT).show();
                    });
        }
        ref[0] = showModernPage(L("Yazı boyutu", "Text size"), L("Okumayı kolaylaştırmak için metni ayarla", "Adjust text for comfortable reading"), body);
    }

        private void showSavedPages(String key, String title, boolean canSaveCurrent) {
        ArrayList<String> items = readList(key);
        LinearLayout body = new LinearLayout(this);
        final Dialog[] ref = new Dialog[1];
        if (canSaveCurrent) {
            settingRow(body, "＋", L("Bu sayfayı yer imlerine ekle", "Bookmark current page"),
                    L("Açık sayfanı sonra kolayca bul", "Keep this page for later"), "", () -> {
                        ref[0].dismiss(); saveCurrentBookmark();
                    });
        }
        if (items.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(24), dp(36), dp(24), dp(36));
            empty.setBackground(round(Color.WHITE, 24));
            TextView icon = text("⌕", 34, Color.rgb(24, 180, 203), true);
            icon.setGravity(Gravity.CENTER);
            empty.addView(icon, new LinearLayout.LayoutParams(-1, dp(54)));
            TextView titleView = text(L("Henüz kayıt yok", "Nothing here yet"), 16, ink, true);
            titleView.setGravity(Gravity.CENTER);
            empty.addView(titleView);
            TextView caption = text(L("Gezinirken sayfalar burada görünür.", "Pages will appear here as you browse."), 11, muted, false);
            caption.setGravity(Gravity.CENTER);
            caption.setPadding(0, dp(6), 0, 0);
            empty.addView(caption);
            body.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        } else {
            sectionLabel(body, L("Kaydedilen sayfalar", "SAVED PAGES"));
            for (int i = 0; i < items.size(); i++) {
                final String url = items.get(i);
                String host = shortUrl(url);
                settingRow(body, "◉", host, url,
                        i == 0 ? L("En son", "Recent") : "", () -> {
                            ref[0].dismiss();
                            loadRequestedUrl(url);
                        });
            }
            settingRow(body, "⌫", L("Listeyi temizle", "Clear list"),
                    L("Bu listedeki kayıtları sil", "Remove all entries in this list"), "", () -> {
                        saveList(key, new ArrayList<>());
                        ref[0].dismiss();
                        showSavedPages(key, title, canSaveCurrent);
                    });
        }
        ref[0] = showModernPage(title, L("Kayıtlarına hızlıca geri dön", "Quickly return to saved pages"), body);
    }

        private void saveCurrentBookmark() {
        String url = webView.getUrl();
        if (privateMode) {
            Toast.makeText(this, "Gizli modda yer imi kaydedilmez", Toast.LENGTH_SHORT).show();
            return;
        }
        if (url == null || !url.startsWith("http")) {
            Toast.makeText(this, L("Önce bir web sayfası aç", "Open a webpage first"), Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<String> list = readList("bookmarks");
        list.remove(url);
        list.add(0, url);
        saveList("bookmarks", list);
        Toast.makeText(this, L("Yer imlerine eklendi", "Bookmark saved"), Toast.LENGTH_SHORT).show();
    }

    private void showDownloads() {
        stopDownloadPolling();
        downloadVisuals.clear();
        ArrayList<String> names = readList("downloads");
        ArrayList<String> ids = readList("download_ids");
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, dp(18));

        if (names.isEmpty()) {
            LinearLayout hero = new LinearLayout(this);
            hero.setOrientation(LinearLayout.VERTICAL);
            hero.setPadding(dp(18), dp(18), dp(18), dp(18));
            hero.setBackground(downloadGradient());
            hero.addView(text(L("İndirmelerin burada", "Your downloads, together"), 18, Color.WHITE, true));
            TextView caption = text(L("Web'den indirdiğin dosyalar burada görünür.", "Files you download from the web will appear here."), 11, Color.WHITE, false);
            caption.setAlpha(.92f);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.topMargin = dp(7);
            hero.addView(caption, cp);
            TextView icon = text("↓", 34, Color.WHITE, true);
            icon.setGravity(Gravity.CENTER); icon.setBackground(round(0x35FFFFFF, 22));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(62), dp(62)); ip.topMargin = dp(17);
            hero.addView(icon, ip);
            body.addView(hero);
            downloadsDialog = showModernPage(L("İndirilenler", "Download"), L("Dosyaların tek yerde", "Your files in one place"), body);
            return;
        }

        int featured = 0;
        for (int i = 0; i < names.size(); i++) {
            DownloadSnapshot snap = getDownloadSnapshot(downloadIdAt(ids, i));
            if (snap.status == DownloadManager.STATUS_PENDING || snap.status == DownloadManager.STATUS_RUNNING ||
                    snap.status == DownloadManager.STATUS_PAUSED) { featured = i; break; }
        }
        body.addView(buildDownloadHero(names.get(featured), downloadIdAt(ids, featured)));
        sectionLabel(body, L("Tamamlanan indirmeler", "Completed"));
        for (int i = 0; i < names.size(); i++) {
            if (i != featured) body.addView(buildDownloadRow(names.get(i), downloadIdAt(ids, i)));
        }
        if (names.size() == 1) {
            TextView note = text(L("Son indirilen dosya yukarıda gösteriliyor.", "Your latest download is shown above."), 11, muted, false);
            note.setPadding(dp(12), dp(14), dp(12), dp(14));
            note.setBackground(round(Color.WHITE, 16));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(5);
            body.addView(note, lp);
        }
        downloadsDialog = showModernPage(L("İndirilenler", "Download"), L("İndirme geçmişin", "Your download history"), body);
        downloadsDialog.setOnDismissListener(d -> stopDownloadPolling());
        startDownloadPolling(downloadsDialog);
    }

    private GradientDrawable downloadGradient() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(90, 201, 239), Color.rgb(42, 165, 224), Color.rgb(18, 126, 208)});
    }

    private LinearLayout buildDownloadHero(String name, long id) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(14));
        card.setBackground(downloadGradient());
        card.setElevation(dp(3));
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon = text(fileGlyph(name), fileGlyph(name).length() > 2 ? 10 : 23, Color.WHITE, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(0x35FFFFFF, 24));
        top.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(11), 0, dp(5), 0);
        TextView title = text(name, 13, Color.WHITE, true);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        labels.addView(title);
        TextView state = text(downloadStatus(getDownloadSnapshot(id).status), 10, Color.WHITE, false);
        LinearLayout.LayoutParams st = new LinearLayout.LayoutParams(-1, -2); st.topMargin = dp(4);
        labels.addView(state, st);
        top.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView more = text("⋮", 24, Color.WHITE, true);
        more.setGravity(Gravity.CENTER);
        top.addView(more, new LinearLayout.LayoutParams(dp(28), dp(40)));
        more.setOnClickListener(v -> showDownloadItemMenu(name, id));
        card.addView(top);

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(42, 227, 171)));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x58FFFFFF));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(5)); bp.topMargin = dp(16);
        card.addView(bar, bp);
        LinearLayout stats = new LinearLayout(this); stats.setGravity(Gravity.CENTER_VERTICAL);
        TextView details = text("", 9, Color.WHITE, false); details.setAlpha(.92f);
        stats.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        TextView percent = text("", 10, Color.WHITE, true);
        stats.addView(percent);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2); sp.topMargin = dp(7);
        card.addView(stats, sp);

        DownloadVisual visual = new DownloadVisual();
        visual.id=id; visual.name=name; visual.bar=bar; visual.percent=percent; visual.details=details; visual.state=state;
        downloadVisuals.add(visual);
        updateDownloadVisual(visual, getDownloadSnapshot(id));
        return card;
    }

    private LinearLayout buildDownloadRow(String name, long id) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(9), dp(10), dp(7), dp(10));
        row.setBackground(round(Color.WHITE, 18));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2); rowLp.bottomMargin = dp(8);

        TextView icon = text(fileGlyph(name), fileGlyph(name).length() > 2 ? 10 : 20, Color.WHITE, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(fileColor(name), 24));
        row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(10), 0, dp(5), 0);
        TextView title = text(name, 12, ink, true);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        content.addView(title);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); bar.setMax(100);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(31, 181, 140)));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(232, 239, 243)));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(3)); bp.topMargin = dp(7);
        content.addView(bar, bp);
        TextView details = text("", 9, muted, false);
        LinearLayout.LayoutParams detLp = new LinearLayout.LayoutParams(-1, -2); detLp.topMargin = dp(5);
        content.addView(details, detLp);
        row.addView(content, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout tail = new LinearLayout(this);
        tail.setOrientation(LinearLayout.VERTICAL); tail.setGravity(Gravity.CENTER);
        TextView more = text("⋮", 20, muted, true); more.setGravity(Gravity.CENTER);
        tail.addView(more, new LinearLayout.LayoutParams(dp(26), dp(29)));
        TextView percent = text("", 9, muted, false); tail.addView(percent);
        row.addView(tail, new LinearLayout.LayoutParams(dp(27), -2));
        row.setOnClickListener(v -> openDownloadedFile(id));
        more.setOnClickListener(v -> showDownloadItemMenu(name, id));

        DownloadVisual visual = new DownloadVisual();
        visual.id=id; visual.name=name; visual.bar=bar; visual.percent=percent; visual.details=details;
        downloadVisuals.add(visual);
        updateDownloadVisual(visual, getDownloadSnapshot(id));
        return row;
    }

    private long downloadIdAt(ArrayList<String> ids, int index) {
        if (index < 0 || index >= ids.size()) return 0;
        try { return Long.parseLong(ids.get(index)); } catch (Exception ignored) { return 0; }
    }

    private DownloadSnapshot getDownloadSnapshot(long id) {
        DownloadSnapshot s = new DownloadSnapshot();
        if (id <= 0) return s;
        Cursor cursor = null;
        try {
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            cursor = manager.query(new DownloadManager.Query().setFilterById(id));
            if (cursor != null && cursor.moveToFirst()) {
                int col = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS); if (col >= 0) s.status = cursor.getInt(col);
                col = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR); if (col >= 0) s.bytes = cursor.getLong(col);
                col = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES); if (col >= 0) s.total = cursor.getLong(col);
                col = cursor.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP); if (col >= 0) s.date = cursor.getLong(col);
            }
        } catch (Exception ignored) { s.status = DownloadManager.STATUS_SUCCESSFUL; }
        finally { if (cursor != null) cursor.close(); }
        return s;
    }

    private int downloadPercentage(DownloadSnapshot s) {
        if (s.status == DownloadManager.STATUS_SUCCESSFUL) return 100;
        return s.total > 0 ? Math.max(0, Math.min(100, (int)(s.bytes * 100 / s.total))) : 0;
    }

    private String downloadStatus(int status) {
        if (status == DownloadManager.STATUS_SUCCESSFUL) return L("Tamamlandı", "Completed");
        if (status == DownloadManager.STATUS_FAILED) return L("İndirme başarısız", "Download failed");
        if (status == DownloadManager.STATUS_PAUSED) return L("Duraklatıldı", "Paused");
        if (status == DownloadManager.STATUS_PENDING) return L("Bağlantı bekleniyor", "Waiting to start");
        if (status == DownloadManager.STATUS_RUNNING) return L("İndiriliyor…", "Downloading…");
        return L("İndirme", "Download");
    }

    private String formatDownloadSize(long bytes) {
        if (bytes < 0) return "--";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1073741824L) return String.format(Locale.US, "%.2f MB", bytes / 1048576.0);
        return String.format(Locale.US, "%.2f GB", bytes / 1073741824.0);
    }

    private String downloadDate(long timestamp) {
        return timestamp <= 0 ? L("Dosya", "File") :
                new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date(timestamp));
    }

    private String fileExtension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot < 0 || dot == name.length()-1 ? "" : name.substring(dot+1).toLowerCase(Locale.ROOT);
    }

    private int fileColor(String name) {
        switch (fileExtension(name)) {
            case "mp4": case "mov": case "mkv": case "avi": return Color.rgb(142, 92, 224);
            case "pdf": case "doc": case "docx": case "txt": return Color.rgb(33, 192, 214);
            case "apk": case "zip": case "rar": case "7z": return Color.rgb(221, 78, 124);
            case "mp3": case "wav": case "m4a": case "ogg": return Color.rgb(238, 180, 16);
            case "png": case "jpg": case "jpeg": case "webp": case "gif": return Color.rgb(77, 112, 164);
            default: return Color.rgb(67, 147, 188);
        }
    }

    private String fileGlyph(String name) {
        switch (fileExtension(name)) {
            case "mp4": case "mov": case "mkv": case "avi": return "▶";
            case "pdf": return "PDF";
            case "doc": case "docx": case "txt": return "D";
            case "apk": return "↓";
            case "zip": case "rar": case "7z": return "ZIP";
            case "mp3": case "wav": case "m4a": case "ogg": return "♫";
            case "png": case "jpg": case "jpeg": case "webp": case "gif": return "◆";
            default: return "↓";
        }
    }

    private void updateDownloadVisual(DownloadVisual v, DownloadSnapshot s) {
        int percent = downloadPercentage(s);
        if (v.bar != null) v.bar.setProgress(percent);
        if (v.percent != null) v.percent.setText(percent + "%");
        if (v.details != null) {
            String size = s.total > 0 ? formatDownloadSize(s.bytes) + " / " + formatDownloadSize(s.total)
                    : formatDownloadSize(s.bytes) + "  ·  " + downloadDate(s.date);
            v.details.setText(size);
        }
        if (v.state != null) v.state.setText(downloadStatus(s.status));
    }

    private void startDownloadPolling(Dialog dialog) {
        downloadPollRunnable = new Runnable() {
            @Override public void run() {
                if (dialog == null || !dialog.isShowing() || downloadsDialog != dialog) return;
                for (DownloadVisual v : downloadVisuals) updateDownloadVisual(v, getDownloadSnapshot(v.id));
                downloadUiHandler.postDelayed(this, 1200);
            }
        };
        downloadUiHandler.postDelayed(downloadPollRunnable, 900);
    }

    private void stopDownloadPolling() {
        if (downloadPollRunnable != null) downloadUiHandler.removeCallbacks(downloadPollRunnable);
        downloadPollRunnable = null; downloadVisuals.clear(); downloadsDialog = null;
    }

    private void showDownloadItemMenu(String name, long id) {
        new AlertDialog.Builder(this).setTitle(name)
            .setItems(new String[]{L("Dosyayı aç", "Open file"), L("Listeden kaldır", "Remove from list")}, (d, which) -> {
                if (which == 0) openDownloadedFile(id);
                else {
                    ArrayList<String> names = readList("downloads");
                    ArrayList<String> ids = readList("download_ids");
                    int index = -1;
                    for (int i = 0; i < names.size(); i++) {
                        if (names.get(i).equals(name) && downloadIdAt(ids, i) == id) { index = i; break; }
                    }
                    if (index < 0) index = names.indexOf(name);
                    if (index >= 0) {
                        names.remove(index); if (index < ids.size()) ids.remove(index);
                        saveList("downloads", names); saveList("download_ids", ids);
                        if (downloadsDialog != null) downloadsDialog.dismiss();
                        showDownloads();
                    }
                }
            }).show();
    }

    private void openDownloadedFile(long id) {
        if (id <= 0) {
            Toast.makeText(this, L("Bu eski kaydın dosya bağlantısı bulunamadı.", "File location is unavailable for this entry."), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            Uri uri = manager.getUriForDownloadedFile(id);
            if (uri == null) { Toast.makeText(this, L("Dosya henüz hazır değil.", "The file is not ready yet."), Toast.LENGTH_SHORT).show(); return; }
            String mime = getContentResolver().getType(uri);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime == null ? "*/*" : mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            try { startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)); }
            catch (Exception ignored) { Toast.makeText(this, L("Dosya açılamadı.", "Could not open file."), Toast.LENGTH_SHORT).show(); }
        }
    }

    private void showTabsDialog() {
        if (tabsDialog != null && tabsDialog.isShowing()) {
            tabsDialog.dismiss();
            return;
        }
        LinearLayout body = new LinearLayout(this);
        body.setPadding(0, 0, 0, dp(16));
        final Dialog[] ref = new Dialog[1];

        LinearLayout addNew = new LinearLayout(this);
        addNew.setOrientation(LinearLayout.HORIZONTAL);
        addNew.setGravity(Gravity.CENTER_VERTICAL);
        addNew.setPadding(dp(16), dp(12), dp(16), dp(12));
        addNew.setBackground(round(Color.WHITE, 20));
        TextView plus = text("＋", 25, Color.rgb(10, 145, 189), true);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(round(0xFFE6F9FC, 15));
        addNew.addView(plus, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout addLabels = new LinearLayout(this);
        addLabels.setOrientation(LinearLayout.VERTICAL);
        addLabels.setPadding(dp(12), 0, 0, 0);
        addLabels.addView(text(L("Yeni sekme", "New tab"), 14, ink, true));
        addLabels.addView(text(L("Yeni bir sayfada gezinmeye başla", "Start browsing a fresh page"), 10, muted, false));
        addNew.addView(addLabels, new LinearLayout.LayoutParams(0, -2, 1));
        addNew.addView(text("›", 26, muted, false));
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(-1, -2);
        addLp.bottomMargin = dp(13);
        body.addView(addNew, addLp);
        addNew.setOnClickListener(v -> animateAction(addNew, () -> {
            if (ref[0] != null) ref[0].dismiss();
            newTab();
        }));

        sectionLabel(body, L("Açık sekmeler", "OPEN TABS"));
        if (tabs.isEmpty()) tabs.add(HOME_URL);
        for (int i = 0; i < tabs.size(); i++) {
            final int tabIndex = i;
            String url = tabs.get(i);
            boolean selected = i == activeTab;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(10), dp(10), dp(10), dp(11));
            GradientDrawable cardBg = round(Color.WHITE, 23);
            cardBg.setStroke(dp(selected ? 2 : 1), selected ? Color.rgb(20, 183, 205) : Color.rgb(231, 235, 242));
            card.setBackground(cardBg);
            card.setElevation(dp(selected ? 3 : 1));

            LinearLayout cardHeader = new LinearLayout(this);
            cardHeader.setOrientation(LinearLayout.HORIZONTAL);
            cardHeader.setGravity(Gravity.CENTER_VERTICAL);
            ImageView miniLogo = new ImageView(this);
            miniLogo.setImageResource(R.drawable.ic_t_logo);
            cardHeader.addView(miniLogo, new LinearLayout.LayoutParams(dp(28), dp(28)));
            LinearLayout labelCol = new LinearLayout(this);
            labelCol.setOrientation(LinearLayout.VERTICAL);
            labelCol.setPadding(dp(8), 0, 0, 0);
            labelCol.addView(text(isHomeUrl(url) ? "TUR Browser" : shortUrl(url), 12, ink, true));
            labelCol.addView(text(selected ? L("Şu an açık", "Currently open") : L("Sekmeye geç", "Switch to tab"), 9,
                    selected ? Color.rgb(8, 147, 174) : muted, false));
            cardHeader.addView(labelCol, new LinearLayout.LayoutParams(0, -2, 1));
            TextView closeTab = text("×", 25, muted, false);
            closeTab.setGravity(Gravity.CENTER);
            closeTab.setBackground(round(0xFFF1F4F8, 15));
            cardHeader.addView(closeTab, new LinearLayout.LayoutParams(dp(34), dp(34)));
            card.addView(cardHeader);

            LinearLayout preview = new LinearLayout(this);
            preview.setOrientation(LinearLayout.VERTICAL);
            preview.setPadding(dp(10), dp(8), dp(10), dp(8));
            preview.setBackground(round(0xFFF3F6FA, 16));
            LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, dp(90));
            previewLp.topMargin = dp(9);
            previewLp.bottomMargin = dp(8);
            TextView previewTop = text(isHomeUrl(url) ? L("İnternetin, senin alanın.", "Your internet, your space.") : shortUrl(url),
                    10, Color.WHITE, true);
            previewTop.setPadding(dp(10), dp(8), dp(10), dp(8));
            previewTop.setBackground(round(Color.rgb(39, 165, 224), 11));
            preview.addView(previewTop, new LinearLayout.LayoutParams(-1, dp(34)));
            LinearLayout bars = new LinearLayout(this);
            bars.setOrientation(LinearLayout.HORIZONTAL);
            bars.setGravity(Gravity.CENTER_VERTICAL);
            for (int k = 0; k < 3; k++) {
                View bar = new View(this);
                bar.setBackground(round(k == 0 ? 0xFF22B8C9 : 0xFFDDE5EF, 8));
                LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(0, dp(k == 0 ? 28 : 18), k == 0 ? 1.1f : 1f);
                barLp.setMargins(0, dp(7), dp(6), 0);
                bars.addView(bar, barLp);
            }
            preview.addView(bars);
            card.addView(preview, previewLp);

            TextView urlLine = text(isHomeUrl(url) ? L("Yeni sekme sayfası", "New tab page") : url, 10, muted, false);
            urlLine.setMaxLines(1);
            urlLine.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(urlLine);

            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
            cardLp.bottomMargin = dp(12);
            body.addView(card, cardLp);
            card.setAlpha(0f);
            card.setScaleX(0.97f);
            card.setScaleY(0.97f);
            final long delay = Math.min(i * 45L, 240L);
            card.postDelayed(() -> card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start(), delay);
            card.setOnClickListener(v -> animateAction(card, () -> {
                if (ref[0] != null) ref[0].dismiss();
                loadTab(tabIndex);
            }));
            closeTab.setOnClickListener(v -> animateAction(closeTab, () -> {
                closeTabAt(tabIndex);
                if (ref[0] != null) ref[0].dismiss();
                showTabsDialog();
            }));
        }

        settingRow(body, "×", L("Diğer sekmeleri kapat", "Close other tabs"),
                L("Sadece şu anki sekmeyi açık tut", "Keep only the current tab"), "", () -> {
                    String keep = tabs.get(Math.max(0, Math.min(activeTab, tabs.size() - 1)));
                    tabs.clear();
                    tabs.add(keep == null ? HOME_URL : keep);
                    activeTab = 0;
                    if (!privateMode) saveList("tabs", tabs);
                    if (ref[0] != null) ref[0].dismiss();
                    loadTab(0);
                });
        ref[0] = showModernPage(L("Sekmeler", "Tabs") + " · " + tabs.size(),
                L("Açık sayfalarını düzenle", "Manage your open pages"), body);
        tabsDialog = ref[0];
    }

    private void closeTabAt(int index) {
        if (index < 0 || index >= tabs.size()) return;
        tabs.remove(index);
        if (tabs.isEmpty()) tabs.add(HOME_URL);
        if (activeTab > index) activeTab--;
        else if (activeTab >= tabs.size()) activeTab = tabs.size() - 1;
        if (activeTab < 0) activeTab = 0;
        if (!privateMode) {
            saveList("tabs", tabs);
            prefs.edit().putInt("active_tab", activeTab).apply();
        }
        loadTab(activeTab);
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
            tabs.clear();
            tabs.add(HOME_URL);
            activeTab = 0;
            Toast.makeText(this, L("Gizli mod açık · Geçmiş kaydedilmeyecek", "Private mode on · History will not be saved"), Toast.LENGTH_LONG).show();
        } else {
            cookieManager.setAcceptCookie(true);
            webView.clearCache(true);
            webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
            tabs.clear();
            tabs.add(HOME_URL);
            activeTab = 0;
            if (!privateMode) {
                saveList("tabs", tabs);
                prefs.edit().putInt("active_tab", activeTab).apply();
            }
            Toast.makeText(this, L("Normal gezinme açık", "Normal browsing enabled"), Toast.LENGTH_SHORT).show();
        }
        updateModeLabel();
        if (privateMode) {
            webView.setAlpha(0f);
            webView.animate().alpha(1f).setDuration(280).start();
        }
        loadHome();
    }

    private void updateModeLabel() {
        if (modeLabel == null) return;
        if (privateMode) {
            int dark = Color.rgb(30, 35, 52);
            int darker = Color.rgb(23, 27, 42);
            if (headerLayout != null) headerLayout.setBackgroundColor(dark);
            if (addressRowView != null) addressRowView.setBackground(round(Color.rgb(43, 49, 68), 20));
            if (brandTitle != null) brandTitle.setTextColor(Color.WHITE);
            if (brandSubtitle != null) brandSubtitle.setTextColor(Color.rgb(187, 194, 211));
            if (addressBar != null) {
                addressBar.setTextColor(Color.WHITE);
                addressBar.setHintTextColor(Color.rgb(173, 181, 198));
            }
            modeLabel.setText(L("GİZLİ MOD", "PRIVATE MODE"));
            modeLabel.setTextColor(Color.rgb(238, 225, 255));
            modeLabel.setBackground(round(Color.rgb(90, 58, 136), 18));
            getWindow().setStatusBarColor(darker);
            getWindow().setNavigationBarColor(Color.WHITE);
            if (Build.VERSION.SDK_INT >= 26) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        } else {
            if (headerLayout != null) headerLayout.setBackgroundColor(Color.WHITE);
            if (addressRowView != null) addressRowView.setBackground(round(Color.WHITE, 20));
            if (brandTitle != null) brandTitle.setTextColor(ink);
            if (brandSubtitle != null) brandSubtitle.setTextColor(muted);
            if (addressBar != null) {
                addressBar.setTextColor(ink);
                addressBar.setHintTextColor(Color.rgb(150, 159, 175));
            }
            modeLabel.setText(blockAds ? L("KORUMA AÇIK", "PROTECTION ON") : L("HIZLI GEZİN", "BROWSE"));
            modeLabel.setTextColor(Color.rgb(7, 91, 180));
            modeLabel.setBackground(round(Color.rgb(228, 249, 252), 18));
            getWindow().setStatusBarColor(Color.WHITE);
            getWindow().setNavigationBarColor(Color.WHITE);
            if (Build.VERSION.SDK_INT >= 26) getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
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
            long downloadId = manager.enqueue(request);
            ArrayList<String> list = readList("downloads");
            ArrayList<String> ids = readList("download_ids");
            int existing = list.indexOf(filename);
            if (existing >= 0) {
                list.remove(existing);
                if (existing < ids.size()) ids.remove(existing);
            }
            list.add(0, filename);
            ids.add(0, String.valueOf(downloadId));
            saveList("downloads", list);
            saveList("download_ids", ids);
            Toast.makeText(this, L("İndirme başladı: ", "Download started: ") + filename, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "İndirme başlatılamadı", Toast.LENGTH_SHORT).show();
        }
    }


    private void loadNewsFeed(WebView target) {
        final int requestId = ++newsRequestToken;
        final boolean english = "English".equals(prefs.getString("language", "Türkçe"));
        Thread worker = new Thread(() -> {
            JSONArray news = new JSONArray();
            HttpURLConnection connection = null;
            InputStream stream = null;
            try {
                String feed = english
                        ? "https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en"
                        : "https://news.google.com/rss?hl=tr&gl=TR&ceid=TR:tr";
                connection = (HttpURLConnection) new URL(feed).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(9000);
                connection.setReadTimeout(9000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36 TURBrowser/1.3");
                connection.setRequestProperty("Accept", "application/rss+xml, application/xml;q=0.9, */*;q=0.8");
                stream = connection.getInputStream();
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(stream, "UTF-8");
                JSONObject item = null;
                int event = parser.getEventType();
                while (event != XmlPullParser.END_DOCUMENT && news.length() < 12) {
                    if (event == XmlPullParser.START_TAG) {
                        String tag = parser.getName();
                        if ("item".equals(tag)) {
                            item = new JSONObject();
                        } else if (item != null && "source".equals(tag)) {
                            String sourceUrl = parser.getAttributeValue(null, "url");
                            if (sourceUrl != null) item.put("sourceUrl", sourceUrl);
                        }
                        if (item != null && ("title".equals(tag) || "link".equals(tag)
                                || "pubDate".equals(tag) || "source".equals(tag))) {
                            String value = parser.nextText();
                            if (value != null) value = value.trim();
                            if (value != null && !value.isEmpty()) {
                                if ("pubDate".equals(tag)) item.put("date", value);
                                else item.put(tag, value);
                            }
                        }
                    } else if (event == XmlPullParser.END_TAG && "item".equals(parser.getName()) && item != null) {
                        String title = item.optString("title", "");
                        String link = item.optString("link", "");
                        if (!title.isEmpty() && !link.isEmpty()) {
                            if (item.optString("source", "").isEmpty()) item.put("source", "Google News");
                            if (item.optString("sourceUrl", "").isEmpty()) item.put("sourceUrl", link);
                            news.put(item);
                        }
                        item = null;
                    }
                    event = parser.next();
                }
            } catch (Exception ignored) {
                // The home screen will show a friendly state when the feed cannot be reached.
            } finally {
                try { if (stream != null) stream.close(); } catch (Exception ignored) { }
                if (connection != null) connection.disconnect();
            }
            final String json = news.toString();
            runOnUiThread(() -> {
                if (requestId == newsRequestToken && target != null && target == webView
                        && isHomeUrl(target.getUrl())) {
                    target.evaluateJavascript("window.renderTurNews && window.renderTurNews(" + json + ");", null);
                }
            });
        }, "TUR-News-Feed");
        worker.setDaemon(true);
        worker.start();
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

    private void showSecurityDialog() {
        String url = webView.getUrl();
        boolean https = url != null && url.startsWith("https://");
        String message = L("Güvenli gezinme: ", "Safe Browsing: ")
                + (Build.VERSION.SDK_INT >= 26 ? L("Android tarafından destekleniyor", "supported by Android") : L("bu Android sürümünde kullanılamıyor", "unavailable on this Android version"))
                + "\\n" + L("Bağlantı: ", "Connection: ")
                + (https ? L("HTTPS şifrelemesi kullanıyor", "uses HTTPS encryption") : L("HTTPS doğrulanmadı", "HTTPS not verified"))
                + "\\n" + L("İzleme sinyali: ", "Do Not Track: ")
                + (doNotTrack ? L("etkin (siteler uymayabilir)", "enabled (sites may ignore it)") : L("kapalı", "off"));
        new AlertDialog.Builder(this).setTitle(L("Güvenlik durumu", "Security status"))
                .setMessage(message).setPositiveButton(L("Tamam", "OK"), null).show();
    }

    private void shareCurrentPage() {
        String url = webView == null ? null : webView.getUrl();
        if (url == null || isHomeUrl(url)) {
            Toast.makeText(this, L("Paylaşmak için önce bir sayfa aç", "Open a page to share it first"), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, url);
        startActivity(Intent.createChooser(intent, L("Sayfayı paylaş", "Share page")));
    }

    private String L(String turkish, String english) {
        return "English".equals(prefs == null ? "Türkçe" : prefs.getString("language", "Türkçe")) ? english : turkish;
    }

    private boolean isHomeUrl(String url) {
        return url != null && (HOME_URL.equals(url) || HOME_EN_URL.equals(url) ||
                url.startsWith("file:///android_asset/home.html") || url.startsWith("file:///android_asset/home_en.html"));
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
