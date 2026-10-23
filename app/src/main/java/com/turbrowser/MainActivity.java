package com.turbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.database.Cursor;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.util.Base64;
import android.util.Base64;
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
import android.webkit.JavascriptInterface;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
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
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayOutputStream;
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
    private ArrayList<String> tabGroupNames = new ArrayList<>();
    private ArrayList<String> tabGroupAssignments = new ArrayList<>();
    private String selectedTabGroupFilter = "";
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
    private final ArrayList<View> toolsCells = new ArrayList<>();
    private static final int REQUEST_SITE_ICON = 7814;
    private int pendingSiteIconIndex = -1;
    private TextView adBlockStateLabel;
    private TextView tabCountBadge;
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
        tabGroupNames = readList("tab_group_names");
        tabGroupAssignments = readList("tab_group_assignments");
        while (tabGroupAssignments.size() < tabs.size()) tabGroupAssignments.add("");
        while (tabGroupAssignments.size() > tabs.size()) tabGroupAssignments.remove(tabGroupAssignments.size() - 1);
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
        logo.setImageResource(com.turbrowser.R.drawable.ic_t_brand);
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
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(-1, dp(350), Gravity.BOTTOM);
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
        webView.addJavascriptInterface(new BrowserBridge(), "TURNative");
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
                if (isHomeUrl(url)) view.addJavascriptInterface(new BrowserBridge(), "TURNative");
                else view.removeJavascriptInterface("TURNative");
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
        selectedTabGroupFilter = "";
        tabs.add(HOME_URL);
        tabGroupAssignments.add("");
        activeTab = tabs.size() - 1;
        updateTabCountBadge();
        updateTabCountBadge();
        if (!privateMode) {
            saveList("tabs", tabs);
            saveList("tab_group_assignments", tabGroupAssignments);
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
        tabCountBadge = count;
        stack.addView(count, new FrameLayout.LayoutParams(dp(17), dp(17), Gravity.RIGHT | Gravity.TOP));
        item.addView(stack, new LinearLayout.LayoutParams(dp(38), dp(30)));
        TextView label = text(L("Sekmeler","Tabs"), 9, muted, false);
        label.setGravity(Gravity.CENTER);
        item.addView(label);
        item.setOnClickListener(v -> animateAction(item, this::showTabsDialog));
        return item;
    }

    private void updateTabCountBadge() {
        if (tabCountBadge != null) tabCountBadge.setText(String.valueOf(Math.min(99, tabs.size())));
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
        actionLogo.setImageResource(R.drawable.ic_t_brand);
        actionLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams logoLp = new FrameLayout.LayoutParams(dp(45), dp(45), Gravity.CENTER);
        actionButtonFrame.addView(actionLogo, logoLp);
        actionCloseGlyph = text("×", 36, Color.WHITE, false);
        actionCloseGlyph.setGravity(Gravity.CENTER);
        actionCloseGlyph.setVisibility(View.GONE);
        actionButtonFrame.addView(actionCloseGlyph, new FrameLayout.LayoutParams(-1, -1));
        actionButtonFrame.setContentDescription(L("Menüyü aç", "Open menu"));
        actionButtonFrame.setOnClickListener(v -> {
            actionButtonFrame.animate().scaleX(0.88f).scaleY(0.88f).setDuration(95).withEndAction(() -> {
                toggleToolsPanel();
                actionButtonFrame.animate().scaleX(1f).scaleY(1f).setDuration(170).start();
            }).start();
        });
        return actionButtonFrame;
    }

    private LinearLayout buildToolsPanel() {
        toolsCells.clear();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(5), dp(10), dp(8));
        panel.setBackground(round(Color.WHITE, 27));
        panel.setElevation(dp(18));

        View grabber = new View(this);
        grabber.setBackground(round(Color.rgb(214, 224, 235), 99));
        LinearLayout.LayoutParams grabLp = new LinearLayout.LayoutParams(dp(38), dp(4));
        grabLp.gravity = Gravity.CENTER_HORIZONTAL;
        grabLp.bottomMargin = dp(5);
        panel.addView(grabber, grabLp);

        LinearLayout menuHeading = new LinearLayout(this);
        menuHeading.setOrientation(LinearLayout.HORIZONTAL);
        menuHeading.setGravity(Gravity.CENTER_VERTICAL);
        menuHeading.setPadding(dp(5), dp(2), dp(5), dp(8));
        ImageView brand = new ImageView(this);
        brand.setImageResource(R.drawable.ic_t_brand);
        brand.setScaleType(ImageView.ScaleType.FIT_CENTER);
        brand.setBackground(round(0xFFE6FAFC, 15));
        menuHeading.addView(brand, new LinearLayout.LayoutParams(dp(37), dp(37)));
        LinearLayout headingLabels = new LinearLayout(this);
        headingLabels.setOrientation(LinearLayout.VERTICAL);
        headingLabels.setPadding(dp(10), 0, 0, 0);
        headingLabels.addView(text("TUR", 13, ink, true));
        TextView subtitle = text(L("Hızlı işlemler", "Quick actions"), 10, muted, false);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(-1, -2);
        subtitleLp.topMargin = dp(2);
        headingLabels.addView(subtitle, subtitleLp);
        menuHeading.addView(headingLabels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView closePill = text("×", 23, muted, false);
        closePill.setGravity(Gravity.CENTER);
        closePill.setBackground(round(0xFFF0F4F8, 14));
        menuHeading.addView(closePill, new LinearLayout.LayoutParams(dp(34), dp(34)));
        closePill.setOnClickListener(v -> closeToolsPanel());
        panel.addView(menuHeading, new LinearLayout.LayoutParams(-1, dp(42)));

        LinearLayout rowOne = toolRow();
        rowOne.addView(toolCell("◷", L("Geçmiş", "History"), 0,
                () -> showSavedPages("history", L("Geçmiş", "History"), false)), toolCellParams());
        rowOne.addView(toolCell("☆", L("Yer imleri", "Bookmarks"), 1,
                () -> showSavedPages("bookmarks", L("Yer imleri", "Bookmarks"), true)), toolCellParams());
        rowOne.addView(toolCell("⇩", L("İndirmeler", "Downloads"), 2, this::showDownloads), toolCellParams());
        rowOne.addView(toolCell("◉", L("Gizli mod", "Incognito"), 3, this::togglePrivateMode), toolCellParams());

        LinearLayout rowTwo = toolRow();
        rowTwo.addView(toolCell("↗", L("Paylaş", "Share"), 4, this::shareCurrentPage), toolCellParams());
        rowTwo.addView(toolCell("＋", L("Yer imi ekle", "Add bookmark"), 5, this::saveCurrentBookmark), toolCellParams());
        rowTwo.addView(toolCell("↻", L("Yenile", "Refresh"), 6, () -> {
            if (webView != null) webView.reload();
        }), toolCellParams());
        rowTwo.addView(toolCell("⚙", L("Ayarlar", "Settings"), 7, this::showSettings), toolCellParams());

        LinearLayout rowThree = toolRow();
        LinearLayout adBlockCell = (LinearLayout) toolCell("⊘",
                L(blockAds ? "Reklam engeli açık" : "Reklam engeli kapalı",
                  blockAds ? "Ad blocker on" : "Ad blocker off"), 8, this::toggleAdBlocking);
        adBlockStateLabel = (TextView) adBlockCell.getChildAt(1);
        rowThree.addView(adBlockCell, toolCellParams());
        rowThree.addView(toolCell("⌕", L("Arama motoru", "Search engine"), 9, this::showSearchEngineDialog), toolCellParams());
        rowThree.addView(toolCell("▤", L("Sekme grupları", "Tab groups"), 10, this::showTabsDialog), toolCellParams());
        rowThree.addView(toolCell("◎", L("Gizlilik", "Privacy"), 11, this::showSettings), toolCellParams());

        panel.addView(rowOne, new LinearLayout.LayoutParams(-1, dp(84)));
        panel.addView(rowTwo, new LinearLayout.LayoutParams(-1, dp(84)));
        panel.addView(rowThree, new LinearLayout.LayoutParams(-1, dp(84)));
        return panel;
    }

    private LinearLayout toolRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private LinearLayout.LayoutParams toolCellParams() {
        return new LinearLayout.LayoutParams(0, dp(84), 1);
    }

    private View toolCell(String icon, String label, int index, Runnable action) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(2), dp(4), dp(2), dp(3));
        int[] tileColors = {0xFFE9F8FF, 0xFFEAFBF4, 0xFFFFF3E9, 0xFFF0EAFE,
                0xFFEAF1FF, 0xFFFFF7E2, 0xFFE7FBFC, 0xFFF1F3F8};
        TextView glyph = text(icon, 23,
                index == 3 && privateMode ? Color.rgb(119, 69, 180) : Color.rgb(38, 106, 145), true);
        glyph.setGravity(Gravity.CENTER);
        GradientDrawable iconTile = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{tileColors[Math.max(0, Math.min(index, tileColors.length - 1))], Color.WHITE});
        iconTile.setCornerRadius(dp(15));
        glyph.setBackground(iconTile);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(39), dp(39));
        iconLp.bottomMargin = dp(5);
        cell.addView(glyph, iconLp);
        TextView caption = text(label, 9, ink, true);
        caption.setGravity(Gravity.CENTER);
        caption.setMaxLines(2);
        cell.addView(caption, new LinearLayout.LayoutParams(-1, -2));
        cell.setBackground(round(0xFFF9FBFD, 17));
        cell.setElevation(dp(1));
        cell.setOnClickListener(v -> animateAction(cell, () -> {
            closeToolsPanel();
            action.run();
        }));
        toolsCells.add(cell);
        return cell;
    }

    private void toggleAdBlocking() {
        blockAds = !blockAds;
        prefs.edit().putBoolean("block_ads", blockAds).apply();
        updateModeLabel();
        if (adBlockStateLabel != null) adBlockStateLabel.setText(L(
                blockAds ? "Reklam engeli açık" : "Reklam engeli kapalı",
                blockAds ? "Ad blocker on" : "Ad blocker off"));
        if (webView != null && !isHomeUrl(webView.getUrl())) webView.reload();
        Toast.makeText(this, L("Reklam engeli ", "Ad blocking ") +
                (blockAds ? L("açıldı", "enabled") : L("kapatıldı", "disabled")), Toast.LENGTH_SHORT).show();
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
        toolsPanel.setTranslationY(dp(28));
        toolsPanel.setScaleX(0.975f);
        toolsPanel.setScaleY(0.975f);
        toolsPanel.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(280)
                .setInterpolator(new android.view.animation.PathInterpolator(0.18f, 0.82f, 0.25f, 1f)).start();
        for (int i = 0; i < toolsCells.size(); i++) {
            View cell = toolsCells.get(i);
            cell.setAlpha(0f);
            cell.setTranslationY(dp(7));
            cell.animate().alpha(1f).translationY(0f).setStartDelay(Math.min(i * 18L, 190L))
                    .setDuration(210).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }
    }

    private void closeToolsPanel() {
        if (!toolsOpen || toolsPanel == null) return;
        toolsOpen = false;
        updateActionButton();
        toolsScrim.animate().alpha(0f).setDuration(170).withEndAction(() -> toolsScrim.setVisibility(View.GONE)).start();
        toolsPanel.animate().alpha(0f).translationY(dp(22)).scaleX(0.975f).scaleY(0.975f).setDuration(180).withEndAction(() -> {
            toolsPanel.setVisibility(View.GONE);
            toolsPanel.setTranslationY(dp(28));
            toolsPanel.setScaleX(1f);
            toolsPanel.setScaleY(1f);
        }).start();
    }

    private void updateActionButton() {
        if (actionLogo == null || actionCloseGlyph == null || actionButtonFrame == null) return;
        actionLogo.setVisibility(toolsOpen ? View.GONE : View.VISIBLE);
        actionCloseGlyph.setVisibility(toolsOpen ? View.VISIBLE : View.GONE);
        actionButtonFrame.setContentDescription(toolsOpen ? L("Menüyü kapat", "Close menu") : L("Menüyü aç", "Open menu"));
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
        row.setAlpha(0f);
        row.setTranslationY(dp(8));
        final long revealDelay = Math.min(body.getChildCount() * 22L, 242L);
        row.postDelayed(() -> row.animate().alpha(1f).translationY(0f).setDuration(210)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start(), revealDelay);
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
        body.addView(buildDownloadsOverview(names, ids), new LinearLayout.LayoutParams(-1, -2));

        if (names.isEmpty()) {
            body.addView(buildDownloadsEmpty(), new LinearLayout.LayoutParams(-1, -2));
            downloadsDialog = showModernPage(L("İndirmeler", "Downloads"),
                    L("İndirme merkezine hoş geldin", "Your download centre"), body);
            return;
        }

        int featured = -1;
        for (int i = 0; i < names.size(); i++) {
            DownloadSnapshot snap = getDownloadSnapshot(downloadIdAt(ids, i));
            if (snap.status == DownloadManager.STATUS_PENDING || snap.status == DownloadManager.STATUS_RUNNING ||
                    snap.status == DownloadManager.STATUS_PAUSED) { featured = i; break; }
        }
        if (featured >= 0) {
            sectionLabel(body, L("ŞU ANDA İNDİRİLİYOR", "IN PROGRESS"));
            body.addView(buildDownloadHero(names.get(featured), downloadIdAt(ids, featured)),
                    new LinearLayout.LayoutParams(-1, -2));
        }
        sectionLabel(body, featured >= 0 ? L("DİĞER DOSYALAR", "YOUR FILES") : L("İNDİRME GEÇMİŞİ", "YOUR FILES"));
        for (int i = 0; i < names.size(); i++) {
            if (i == featured) continue;
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(-1, -2);
            itemLp.bottomMargin = dp(9);
            body.addView(buildDownloadRow(names.get(i), downloadIdAt(ids, i)), itemLp);
        }

        LinearLayout clear = new LinearLayout(this);
        clear.setGravity(Gravity.CENTER);
        clear.setPadding(dp(12), dp(13), dp(12), dp(13));
        clear.setBackground(round(0xFFF0F5F9, 16));
        clear.addView(text(L("İndirme listesini temizle", "Clear download list"), 11,
                Color.rgb(91, 111, 135), true));
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(-1, -2);
        clearLp.topMargin = dp(5);
        body.addView(clear, clearLp);
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(L("İndirme listesini temizle?", "Clear the download list?"))
                .setMessage(L("Dosyalar cihazından silinmez; yalnızca listedeki kayıtlar kaldırılır.",
                        "Files stay on your device; only the list entries are removed."))
                .setNegativeButton(L("İptal", "Cancel"), null)
                .setPositiveButton(L("Temizle", "Clear"), (d, which) -> {
                    saveList("downloads", new ArrayList<>());
                    saveList("download_ids", new ArrayList<>());
                    if (downloadsDialog != null) downloadsDialog.dismiss();
                    showDownloads();
                }).show());

        downloadsDialog = showModernPage(L("İndirmeler", "Downloads"),
                L("Dosyaların tek bir yerde", "Your files in one place"), body);
        downloadsDialog.setOnDismissListener(d -> stopDownloadPolling());
        startDownloadPolling(downloadsDialog);
    }

    private LinearLayout buildDownloadsOverview(ArrayList<String> names, ArrayList<String> ids) {
        int complete=0, active=0, failed=0;
        long totalBytes=0;
        for (int i=0; i<names.size(); i++) {
            DownloadSnapshot snap=getDownloadSnapshot(downloadIdAt(ids,i));
            if (snap.status==DownloadManager.STATUS_SUCCESSFUL) complete++;
            else if (snap.status==DownloadManager.STATUS_FAILED) failed++;
            else active++;
            if (snap.bytes>0) totalBytes+=snap.bytes;
        }
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17),dp(17),dp(17),dp(16));
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(13,72,148),Color.rgb(20,143,194),Color.rgb(28,199,207)});
        bg.setCornerRadius(dp(25));
        card.setBackground(bg);
        card.setElevation(dp(3));

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark=new ImageView(this);
        mark.setImageResource(R.drawable.ic_t_brand);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mark.setBackground(round(0x34FFFFFF,15));
        top.addView(mark,new LinearLayout.LayoutParams(dp(40),dp(40)));
        LinearLayout words=new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(10),0,0,0);
        words.addView(text(L("TUR · DOSYA MERKEZİ","TUR · DOWNLOAD CENTRE"),9,0xEFFFFFFF,true));
        TextView title=text(L("İndirmelerin","Your downloads"),19,Color.WHITE,true);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-1,-2);
        titleLp.topMargin=dp(3);
        words.addView(title,titleLp);
        top.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        TextView count=text(String.valueOf(names.size()),14,Color.WHITE,true);
        count.setGravity(Gravity.CENTER);
        count.setBackground(round(0x35FFFFFF,99));
        top.addView(count,new LinearLayout.LayoutParams(dp(38),dp(38)));
        card.addView(top);

        TextView subtitle=text(names.isEmpty()
                ?L("İndirdiğin her şey burada toplanır.","Everything you download, in one place.")
                :L("Dosyalarını takip et ve kolayca aç.","Track progress and open files easily."),
                11,0xEFFFFFFF,false);
        LinearLayout.LayoutParams subLp=new LinearLayout.LayoutParams(-1,-2);
        subLp.topMargin=dp(12);
        card.addView(subtitle,subLp);

        LinearLayout stats=new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout[] chips={
                downloadStatChip(L("Tamamlandı","Completed"),String.valueOf(complete)),
                downloadStatChip(L("Aktif","Active"),String.valueOf(active)),
                downloadStatChip(L("İndirilen","Downloaded"),formatDownloadSize(totalBytes))
        };
        for(int i=0;i<chips.length;i++){
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
            lp.topMargin=dp(13);
            if(i>0)lp.leftMargin=dp(6);
            stats.addView(chips[i],lp);
        }
        card.addView(stats);
        if(failed>0){
            TextView failures=text(failed+" "+L("başarısız","failed"),10,Color.WHITE,true);
            LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2);fp.topMargin=dp(9);
            card.addView(failures,fp);
        }
        LinearLayout.LayoutParams outerLp=new LinearLayout.LayoutParams(-1,-2);
        outerLp.bottomMargin=dp(6);
        card.setLayoutParams(outerLp);
        card.setAlpha(0f);card.setTranslationY(dp(8));
        card.postDelayed(()->card.animate().alpha(1f).translationY(0f).setDuration(280)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start(),30L);
        return card;
    }

    private LinearLayout downloadStatChip(String label,String value) {
        LinearLayout chip=new LinearLayout(this);
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setPadding(dp(8),dp(9),dp(6),dp(9));
        chip.setBackground(round(0x28FFFFFF,14));
        TextView number=text(value,13,Color.WHITE,true);
        number.setMaxLines(1);
        number.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chip.addView(number);
        TextView caption=text(label,8,0xEFFFFFFF,false);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(3);
        chip.addView(caption,lp);
        return chip;
    }

    private LinearLayout buildDownloadsEmpty() {
        LinearLayout empty=new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(22),dp(25),dp(22),dp(25));
        GradientDrawable bg=round(Color.WHITE,24);
        bg.setStroke(dp(1),0xFFE5EDF5);
        empty.setBackground(bg);
        FrameLayout iconPlate=new FrameLayout(this);
        iconPlate.setBackground(round(0xFFE6F8FC,23));
        TextView arrow=text("↓",35,Color.rgb(10,137,174),true);
        arrow.setGravity(Gravity.CENTER);
        iconPlate.addView(arrow,new FrameLayout.LayoutParams(-1,-1));
        empty.addView(iconPlate,new LinearLayout.LayoutParams(dp(78),dp(78)));
        TextView title=text(L("Henüz dosya indirmedin","No downloads yet"),15,ink,true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-1,-2);titleLp.topMargin=dp(14);
        empty.addView(title,titleLp);
        TextView copy=text(L("Web'den indirdiğin dosyalar burada görünür. Hazır olduğunda buradan açabilir ve durumunu takip edebilirsin.",
                "Files you download from the web will appear here. You can open them and track their status."),
                11,muted,false);
        copy.setGravity(Gravity.CENTER);copy.setLineSpacing(dp(2),1f);
        LinearLayout.LayoutParams copyLp=new LinearLayout.LayoutParams(-1,-2);copyLp.topMargin=dp(7);
        empty.addView(copy,copyLp);
        empty.setAlpha(0f);empty.setTranslationY(dp(8));
        empty.postDelayed(()->empty.animate().alpha(1f).translationY(0f).setDuration(300)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start(),65L);
        return empty;
    }

    private GradientDrawable downloadGradient() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(80,194,238),Color.rgb(35,158,221),Color.rgb(16,117,202)});
    }

    private View buildDownloadTypeIcon(String name, boolean darkBackground) {
        return new DownloadTypeIcon(this,fileExtension(name),fileColor(name),darkBackground);
    }

    private LinearLayout buildDownloadHero(String name,long id) {
        DownloadSnapshot snap=getDownloadSnapshot(id);
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15),dp(15),dp(15),dp(13));
        card.setBackground(downloadGradient());
        card.setElevation(dp(3));
        LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(-1,-2);
        cardLp.bottomMargin=dp(9);
        card.setLayoutParams(cardLp);

        LinearLayout top=new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(buildDownloadTypeIcon(name,true),new LinearLayout.LayoutParams(dp(50),dp(50)));
        LinearLayout labels=new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(11),0,dp(5),0);
        TextView title=text(name,13,Color.WHITE,true);
        title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        labels.addView(title);
        TextView state=text(downloadStatus(snap.status),10,Color.WHITE,false);
        state.setAlpha(.94f);
        LinearLayout.LayoutParams st=new LinearLayout.LayoutParams(-1,-2);st.topMargin=dp(4);
        labels.addView(state,st);
        top.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView more=text("⋮",23,Color.WHITE,true);more.setGravity(Gravity.CENTER);
        top.addView(more,new LinearLayout.LayoutParams(dp(27),dp(40)));
        more.setOnClickListener(v->showDownloadItemMenu(name,id));
        card.addView(top);

        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(44,229,170)));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x53FFFFFF));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(5));bp.topMargin=dp(15);
        card.addView(bar,bp);
        LinearLayout stats=new LinearLayout(this);stats.setGravity(Gravity.CENTER_VERTICAL);
        TextView details=text("",9,Color.WHITE,false);details.setAlpha(.92f);
        stats.addView(details,new LinearLayout.LayoutParams(0,-2,1));
        TextView percent=text("",10,Color.WHITE,true);stats.addView(percent);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(7);
        card.addView(stats,sp);

        DownloadVisual visual=new DownloadVisual();
        visual.id=id;visual.name=name;visual.bar=bar;visual.percent=percent;visual.details=details;visual.state=state;
        downloadVisuals.add(visual);updateDownloadVisual(visual,snap);
        card.setAlpha(0f);card.setTranslationY(dp(9));card.setScaleX(.985f);card.setScaleY(.985f);
        card.postDelayed(()->card.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(270)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start(),55L);
        return card;
    }

    private LinearLayout buildDownloadRow(String name,long id) {
        DownloadSnapshot snap=getDownloadSnapshot(id);
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(11),dp(11),dp(9),dp(11));
        GradientDrawable rowBg=round(Color.WHITE,21);
        rowBg.setStroke(dp(1),0xFFEAF0F6);
        row.setBackground(rowBg);
        row.setElevation(dp(1));

        row.addView(buildDownloadTypeIcon(name,false),new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(11),0,dp(6),0);
        TextView title=text(name,12,ink,true);
        title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        content.addView(title);
        LinearLayout meta=new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        TextView status=text(downloadStatus(snap.status),9,
                snap.status==DownloadManager.STATUS_FAILED?Color.rgb(207,73,89):Color.rgb(20,148,111),true);
        meta.addView(status);
        TextView separator=text("  ·  ",9,0xFF9BA9BB,false);meta.addView(separator);
        TextView date=text(downloadDate(snap.date),9,muted,false);meta.addView(date);
        LinearLayout.LayoutParams metaLp=new LinearLayout.LayoutParams(-1,-2);metaLp.topMargin=dp(3);
        content.addView(meta,metaLp);

        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(32,181,139)));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE6EDF2));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(3));bp.topMargin=dp(7);
        content.addView(bar,bp);

        TextView details=text("",9,muted,false);
        LinearLayout.LayoutParams detLp=new LinearLayout.LayoutParams(-1,-2);detLp.topMargin=dp(5);
        content.addView(details,detLp);
        row.addView(content,new LinearLayout.LayoutParams(0,-2,1));

        LinearLayout trailing=new LinearLayout(this);trailing.setOrientation(LinearLayout.VERTICAL);trailing.setGravity(Gravity.CENTER);
        TextView more=text("⋯",21,muted,true);more.setGravity(Gravity.CENTER);
        more.setBackground(round(0xFFF1F5F9,12));
        trailing.addView(more,new LinearLayout.LayoutParams(dp(28),dp(30)));
        TextView percent=text("",9,muted,true);
        LinearLayout.LayoutParams pctLp=new LinearLayout.LayoutParams(-2,-2);pctLp.topMargin=dp(4);
        trailing.addView(percent,pctLp);
        row.addView(trailing,new LinearLayout.LayoutParams(dp(30),-2));
        row.setOnClickListener(v->animateAction(row,()->openDownloadedFile(id)));
        more.setOnClickListener(v->showDownloadItemMenu(name,id));

        DownloadVisual visual=new DownloadVisual();
        visual.id=id;visual.name=name;visual.bar=bar;visual.percent=percent;visual.details=details;
        downloadVisuals.add(visual);updateDownloadVisual(visual,snap);
        row.setAlpha(0f);row.setTranslationY(dp(7));
        row.postDelayed(()->row.animate().alpha(1f).translationY(0f).setDuration(235)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start(),
                Math.min(downloadVisuals.size()*38L,280L));
        return row;
    }

    private class DownloadTypeIcon extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String ext;
        private final int accent;
        private final boolean dark;
        DownloadTypeIcon(Context context,String ext,int accent,boolean dark){super(context);this.ext=ext;this.accent=accent;this.dark=dark;}
        private int pale(int color){return Color.rgb((Color.red(color)+255)/2,(Color.green(color)+255)/2,(Color.blue(color)+255)/2);}
        private void setFill(int color){p.setStyle(Paint.Style.FILL);p.setColor(color);p.setStrokeWidth(1);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float s=Math.min(getWidth(),getHeight());
            float pad=s*.035f;
            setFill(dark?0x3DFFFFFF:pale(accent));
            canvas.drawRoundRect(pad,pad,s-pad,s-pad,s*.29f,s*.29f,p);
            float w=s*.43f,h=s*.55f,x=(s-w)*.5f,y=s*.18f;
            RectF paper=new RectF(x,y,x+w,y+h);
            setFill(Color.WHITE);canvas.drawRoundRect(paper,s*.06f,s*.06f,p);
            Path fold=new Path();fold.moveTo(x+w*.67f,y);fold.lineTo(x+w,y+h*.24f);fold.lineTo(x+w*.67f,y+h*.24f);fold.close();
            setFill(pale(accent));canvas.drawPath(fold,p);
            if(ext.matches("mp3|wav|m4a|ogg")){
                setFill(accent);p.setStrokeWidth(s*.055f);p.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawLine(s*.48f,y+h*.36f,s*.48f,y+h*.65f,p);
                canvas.drawLine(s*.48f,y+h*.36f,s*.68f,y+h*.30f,p);
                canvas.drawLine(s*.68f,y+h*.30f,s*.68f,y+h*.56f,p);
                p.setStyle(Paint.Style.FILL);canvas.drawCircle(s*.42f,y+h*.67f,s*.06f,p);canvas.drawCircle(s*.62f,y+h*.58f,s*.06f,p);
            }else if(ext.matches("png|jpg|jpeg|webp|gif")){
                setFill(accent);canvas.drawCircle(x+w*.73f,y+h*.4f,s*.045f,p);
                Path hill=new Path();hill.moveTo(x+w*.12f,y+h*.82f);hill.lineTo(x+w*.38f,y+h*.52f);hill.lineTo(x+w*.54f,y+h*.66f);hill.lineTo(x+w*.72f,y+h*.49f);hill.lineTo(x+w*.89f,y+h*.82f);hill.close();canvas.drawPath(hill,p);
            }else if(ext.matches("mp4|mov|mkv|avi")){
                setFill(accent);Path play=new Path();play.moveTo(s*.43f,y+h*.34f);play.lineTo(s*.65f,y+h*.5f);play.lineTo(s*.43f,y+h*.66f);play.close();canvas.drawPath(play,p);
            }else if(ext.matches("apk|zip|rar|7z")){
                setFill(accent);p.setStrokeWidth(s*.05f);p.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawLine(s*.54f,y+h*.33f,s*.54f,y+h*.62f,p);canvas.drawLine(s*.45f,y+h*.53f,s*.54f,y+h*.63f,p);canvas.drawLine(s*.63f,y+h*.53f,s*.54f,y+h*.63f,p);
            }else{
                setFill(accent);
                canvas.drawRoundRect(x+w*.2f,y+h*.42f,x+w*.8f,y+h*.48f,s*.025f,s*.025f,p);
                canvas.drawRoundRect(x+w*.2f,y+h*.59f,x+w*.68f,y+h*.65f,s*.025f,s*.025f,p);
            }
        }
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
        while (tabGroupAssignments.size() < tabs.size()) tabGroupAssignments.add("");
        while (tabGroupAssignments.size() > tabs.size()) tabGroupAssignments.remove(tabGroupAssignments.size() - 1);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, dp(16));
        final Dialog[] ref = new Dialog[1];

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(13), dp(10), dp(11), dp(10));
        actions.setBackground(round(Color.WHITE, 20));

        TextView plus = text("+", 26, Color.rgb(10, 145, 189), true);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(round(0xFFE6F9FC, 15));
        actions.addView(plus, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout actionCopy = new LinearLayout(this);
        actionCopy.setOrientation(LinearLayout.VERTICAL);
        actionCopy.setPadding(dp(10), 0, 0, 0);
        actionCopy.addView(text(L("Yeni sekme", "New tab"), 13, ink, true));
        actionCopy.addView(text(L("Yeni bir sayfada gezinmeye başla", "Start a fresh page"), 10, muted, false));
        actions.addView(actionCopy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView groupButton = text("▦  " + L("Yeni grup", "New group"), 10, Color.rgb(7, 121, 159), true);
        groupButton.setGravity(Gravity.CENTER);
        groupButton.setPadding(dp(10), dp(10), dp(10), dp(10));
        groupButton.setBackground(round(0xFFE7F8FC, 13));
        actions.addView(groupButton);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, -2);
        actionsLp.bottomMargin = dp(12);
        body.addView(actions, actionsLp);
        actions.setOnClickListener(v -> animateAction(actions, () -> {
            if (ref[0] != null) ref[0].dismiss();
            newTab();
        }));
        groupButton.setOnClickListener(v -> createTabGroup(-1));

        HorizontalScrollView groupScroll = new HorizontalScrollView(this);
        groupScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(dp(1), dp(2), dp(1), dp(9));
        addTabGroupChip(chips, L("Tümü", "All tabs"), "", ref);
        for (String group : tabGroupNames) addTabGroupChip(chips, group, group, ref);
        groupScroll.addView(chips, new HorizontalScrollView.LayoutParams(-2, -2));
        body.addView(groupScroll, new LinearLayout.LayoutParams(-1, -2));

        int visibleCount = 0;
        for (int i = 0; i < tabs.size(); i++) {
            String g = i < tabGroupAssignments.size() ? tabGroupAssignments.get(i) : "";
            if (selectedTabGroupFilter.isEmpty() || selectedTabGroupFilter.equals(g)) visibleCount++;
        }
        sectionLabel(body, L("Sekmeler", "TABS") + " · " + visibleCount);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setUseDefaultMargins(false);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        int cell = 0;
        for (int i = 0; i < tabs.size(); i++) {
            final int tabIndex = i;
            String url = tabs.get(i);
            String groupName = i < tabGroupAssignments.size() ? tabGroupAssignments.get(i) : "";
            if (!selectedTabGroupFilter.isEmpty() && !selectedTabGroupFilter.equals(groupName)) continue;
            boolean selected = i == activeTab;

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(9), dp(9), dp(9), dp(10));
            GradientDrawable cardBg = round(Color.WHITE, 21);
            cardBg.setStroke(dp(selected ? 2 : 1), selected ? Color.rgb(20, 183, 205) : Color.rgb(231, 235, 242));
            card.setBackground(cardBg);
            card.setElevation(dp(selected ? 3 : 1));

            LinearLayout titleRow = new LinearLayout(this);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            ImageView miniLogo = new ImageView(this);
            miniLogo.setImageResource(R.drawable.ic_t_brand);
            miniLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            titleRow.addView(miniLogo, new LinearLayout.LayoutParams(dp(25), dp(25)));
            LinearLayout titleCol = new LinearLayout(this);
            titleCol.setOrientation(LinearLayout.VERTICAL);
            titleCol.setPadding(dp(6), 0, 0, 0);
            TextView domain = text(isHomeUrl(url) ? "TUR Browser" : shortUrl(url), 10, ink, true);
            domain.setMaxLines(1);
            domain.setEllipsize(android.text.TextUtils.TruncateAt.END);
            titleCol.addView(domain);
            titleCol.addView(text(selected ? L("Aktif", "Active") : L("Açık sekme", "Open tab"), 8,
                    selected ? Color.rgb(8, 147, 174) : muted, false));
            titleRow.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1));
            TextView close = text("×", 21, muted, false);
            close.setGravity(Gravity.CENTER);
            close.setBackground(round(0xFFF1F4F8, 12));
            titleRow.addView(close, new LinearLayout.LayoutParams(dp(27), dp(27)));
            card.addView(titleRow);

            LinearLayout preview = new LinearLayout(this);
            preview.setOrientation(LinearLayout.VERTICAL);
            preview.setPadding(dp(8), dp(7), dp(8), dp(7));
            preview.setBackground(round(selected ? 0xFFE4F8FB : 0xFFF3F6FA, 14));
            LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, dp(68));
            previewLp.topMargin = dp(8);
            previewLp.bottomMargin = dp(7);
            TextView previewHeading = text(isHomeUrl(url) ? L("İnternetin, senin alanın", "Your internet, your space") : shortUrl(url),
                    9, Color.WHITE, true);
            previewHeading.setPadding(dp(7), dp(6), dp(4), dp(6));
            previewHeading.setMaxLines(1);
            previewHeading.setEllipsize(android.text.TextUtils.TruncateAt.END);
            previewHeading.setBackground(round(Color.rgb(43, 167, 221), 9));
            preview.addView(previewHeading, new LinearLayout.LayoutParams(-1, dp(27)));
            LinearLayout lines = new LinearLayout(this);
            lines.setOrientation(LinearLayout.HORIZONTAL);
            lines.setGravity(Gravity.CENTER_VERTICAL);
            for (int p = 0; p < 2; p++) {
                View line = new View(this);
                line.setBackground(round(p == 0 ? 0xFF22B8C9 : 0xFFDDE5EF, 8));
                LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(0, dp(p == 0 ? 13 : 9), p == 0 ? 1.2f : 1f);
                lineLp.setMargins(0, dp(5), dp(5), 0);
                lines.addView(line, lineLp);
            }
            preview.addView(lines);
            card.addView(preview, previewLp);

            TextView group = text("▦  " + (groupName.isEmpty() ? L("Grup seç", "Set group") : groupName), 9,
                    groupName.isEmpty() ? muted : Color.rgb(8, 132, 161), true);
            group.setMaxLines(1);
            group.setEllipsize(android.text.TextUtils.TruncateAt.END);
            group.setPadding(dp(7), dp(6), dp(7), dp(6));
            group.setGravity(Gravity.CENTER_VERTICAL);
            group.setBackground(round(groupName.isEmpty() ? 0xFFF0F3F7 : 0xFFE5F8FB, 10));
            card.addView(group);
            TextView urlLine = text(isHomeUrl(url) ? L("Yeni sekme sayfası", "New tab page") : url, 8, muted, false);
            urlLine.setMaxLines(1);
            urlLine.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams urlLp = new LinearLayout.LayoutParams(-1, -2);
            urlLp.topMargin = dp(6);
            card.addView(urlLine, urlLp);

            int row = cell / 2;
            int column = cell % 2;
            GridLayout.LayoutParams cardLp = new GridLayout.LayoutParams(GridLayout.spec(row, GridLayout.FILL), GridLayout.spec(column, GridLayout.FILL, 1f));
            cardLp.width = 0;
            cardLp.height = -2;
            cardLp.setMargins(dp(column == 0 ? 0 : 5), 0, dp(column == 0 ? 5 : 0), dp(10));
            grid.addView(card, cardLp);
            cell++;

            card.setAlpha(0f);
            card.setScaleX(.96f);
            card.setScaleY(.96f);
            card.postDelayed(() -> card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(230)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start(),
                    Math.min(cell * 35L, 280L));
            card.setOnClickListener(v -> animateAction(card, () -> {
                if (ref[0] != null) ref[0].dismiss();
                loadTab(tabIndex);
            }));
            close.setOnClickListener(v -> animateAction(close, () -> {
                if (ref[0] != null) ref[0].dismiss();
                closeTabAt(tabIndex);
                showTabsDialog();
            }));
            group.setOnClickListener(v -> showTabGroupPicker(tabIndex));
        }
        if (cell == 0) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(25), dp(20), dp(25));
            empty.setBackground(round(Color.WHITE, 18));
            empty.addView(text(L("Bu grupta sekme yok", "No tabs in this group"), 13, ink, true));
            TextView help = text(L("Sekme kartındaki grup alanına dokunarak taşıyabilirsin.", "Tap the group label on a tab card to move it here."), 10, muted, false);
            help.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2); hp.topMargin = dp(5);
            empty.addView(help, hp);
            body.addView(empty);
        } else {
            body.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        }

        settingRow(body, "×", L("Diğer sekmeleri kapat", "Close other tabs"),
                L("Sadece şu anki sekmeyi açık tut", "Keep only the current tab"), "", () -> {
                    int keepIndex = Math.max(0, Math.min(activeTab, tabs.size() - 1));
                    String keep = tabs.get(keepIndex);
                    String group = keepIndex < tabGroupAssignments.size() ? tabGroupAssignments.get(keepIndex) : "";
                    tabs.clear(); tabs.add(keep == null ? HOME_URL : keep);
                    tabGroupAssignments.clear(); tabGroupAssignments.add(group);
                    activeTab = 0;
                    updateTabCountBadge();
                    if (!privateMode) {
                        saveList("tabs", tabs);
                        saveList("tab_group_assignments", tabGroupAssignments);
                    }
                    if (ref[0] != null) ref[0].dismiss();
                    loadTab(0);
                });
        ref[0] = showModernPage(L("Sekmeler", "Tabs") + " · " + tabs.size(),
                L("İkili görünüm ve sekme grupları", "Two-column view and tab groups"), body);
        tabsDialog = ref[0];
    }

    private void addTabGroupChip(LinearLayout parent, String label, String value, Dialog[] ref) {
        boolean active = selectedTabGroupFilter.equals(value);
        TextView chip = text(label, 10, active ? Color.WHITE : Color.rgb(77, 99, 124), true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(13), dp(9), dp(13), dp(9));
        chip.setBackground(round(active ? Color.rgb(10, 138, 176) : Color.WHITE, 99));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(7);
        parent.addView(chip, lp);
        chip.setOnClickListener(v -> {
            selectedTabGroupFilter = value;
            if (ref[0] != null) ref[0].dismiss();
            showTabsDialog();
        });
    }

    private void createTabGroup(int tabIndex) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(L("Örn. İş, Alışveriş, Okul", "e.g. Work, Shopping, Study"));
        input.setTextSize(14);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(round(0xFFF2F6FA, 14));
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(dp(6), dp(4), dp(6), dp(4));
        wrapper.addView(input);
        new AlertDialog.Builder(this).setTitle(L("Yeni sekme grubu", "New tab group"))
                .setMessage(L("Sekmelerini düzenlemek için bir isim ver.", "Give your group a name."))
                .setView(wrapper)
                .setNegativeButton(L("İptal", "Cancel"), null)
                .setPositiveButton(L("Oluştur", "Create"), (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) name = L("Yeni grup", "New group");
                    if (!tabGroupNames.contains(name)) {
                        tabGroupNames.add(name);
                        saveList("tab_group_names", tabGroupNames);
                    }
                    selectedTabGroupFilter = name;
                    if (tabIndex >= 0 && tabIndex < tabGroupAssignments.size()) {
                        tabGroupAssignments.set(tabIndex, name);
                        saveList("tab_group_assignments", tabGroupAssignments);
                    }
                    if (tabsDialog != null && tabsDialog.isShowing()) tabsDialog.dismiss();
                    showTabsDialog();
                }).show();
    }

    private void showTabGroupPicker(int tabIndex) {
        ArrayList<String> choices = new ArrayList<>();
        choices.add(L("Grup yok", "No group"));
        choices.addAll(tabGroupNames);
        choices.add(L("+ Yeni grup oluştur", "+ Create group"));
        String[] items = choices.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle(L("Sekme grubunu seç", "Choose tab group"))
                .setItems(items, (dialog, which) -> {
                    if (which == items.length - 1) {
                        createTabGroup(tabIndex);
                        return;
                    }
                    if (tabIndex >= 0 && tabIndex < tabGroupAssignments.size()) {
                        tabGroupAssignments.set(tabIndex, which == 0 ? "" : tabGroupNames.get(which - 1));
                        saveList("tab_group_assignments", tabGroupAssignments);
                        selectedTabGroupFilter = "";
                    }
                    if (tabsDialog != null && tabsDialog.isShowing()) tabsDialog.dismiss();
                    showTabsDialog();
                })
                .setNegativeButton(L("İptal", "Cancel"), null).show();
    }

    private void closeTabAt(int index) {
        if (index < 0 || index >= tabs.size()) return;
        tabs.remove(index);
        if (index < tabGroupAssignments.size()) tabGroupAssignments.remove(index);
        if (tabs.isEmpty()) {
            tabs.add(HOME_URL);
            tabGroupAssignments.clear();
            tabGroupAssignments.add("");
        }
        if (activeTab > index) activeTab--;
        else if (activeTab >= tabs.size()) activeTab = tabs.size() - 1;
        if (activeTab < 0) activeTab = 0;
        updateTabCountBadge();
        if (!privateMode) {
            saveList("tabs", tabs);
            saveList("tab_group_assignments", tabGroupAssignments);
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
            tabGroupAssignments.clear();
            tabGroupAssignments.add("");
            activeTab = 0;
            updateTabCountBadge();
            Toast.makeText(this, L("Gizli mod açık · Geçmiş kaydedilmeyecek", "Private mode on · History will not be saved"), Toast.LENGTH_LONG).show();
        } else {
            cookieManager.setAcceptCookie(true);
            webView.clearCache(true);
            webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
            tabs.clear();
            tabs.add(HOME_URL);
            tabGroupAssignments.clear();
            tabGroupAssignments.add("");
            activeTab = 0;
            updateTabCountBadge();
            if (!privateMode) {
                saveList("tabs", tabs);
                saveList("tab_group_assignments", tabGroupAssignments);
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


    private class BrowserBridge {
        @JavascriptInterface
        public void pickSiteImage(int index) {
            runOnUiThread(() -> {
                if (webView == null || !isHomeUrl(webView.getUrl())) return;
                pendingSiteIconIndex = index;
                Intent picker = new Intent(Intent.ACTION_GET_CONTENT);
                picker.setType("image/*");
                picker.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(Intent.createChooser(picker, L("Hızlı erişim fotoğrafı seç", "Choose shortcut image")), REQUEST_SITE_ICON);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, L("Fotoğraf seçici açılamadı.", "Image picker could not be opened."), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SITE_ICON) return;
        int index = pendingSiteIconIndex;
        pendingSiteIconIndex = -1;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(input, null, bounds);
            }
            int max = Math.max(bounds.outWidth, bounds.outHeight);
            int sample = 1;
            while (max / sample > 640) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            Bitmap bitmap;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(input, null, options);
            }
            if (bitmap == null) throw new IllegalArgumentException("Unsupported image");
            int w = bitmap.getWidth(), h = bitmap.getHeight();
            float scale = Math.min(1f, 480f / Math.max(w, h));
            Bitmap resized = scale < 1f
                    ? Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)), true)
                    : bitmap;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            resized.compress(Bitmap.CompressFormat.JPEG, 82, output);
            byte[] bytes = output.toByteArray();
            if (resized != bitmap) resized.recycle();
            bitmap.recycle();
            String dataUri = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
            if (webView != null && isHomeUrl(webView.getUrl())) {
                webView.evaluateJavascript("window.onCustomSiteImageSelected && window.onCustomSiteImageSelected("
                        + index + "," + JSONObject.quote(dataUri) + ");", null);
            } else {
                Toast.makeText(this, L("Önce ana sayfaya dön.", "Return to the home page first."), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, L("Fotoğraf işlenemedi. Başka bir fotoğraf dene.", "Could not process this image. Try another photo."), Toast.LENGTH_SHORT).show();
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
