package com.usama.dailytracker;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Purse & Pulse: the whole app runs from files inside the APK.
 * No internet is used. Data is kept in the WebView's local storage on this phone.
 */
public class MainActivity extends Activity {
    private static final String HOME = "https://appassets.androidplatform.net/assets/www/index.html";
    private static final int PICK_FILE = 42;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private long updateDownloadId = -1;
    private String updateFileName = null;
    private BroadcastReceiver downloadReceiver;
    private String pendingOpen = null;
    private boolean pageReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setTextZoom(100);
        s.setMediaPlaybackRequiresUserGesture(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                deliverOpen();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "Open backup file"), PICK_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "Android");

        takeOpen(getIntent());
        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(HOME);
        }
        Reminders.scheduleAll(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        takeOpen(intent);
        deliverOpen();
    }

    /** A notification or the widget asked to open a certain screen. */
    private void takeOpen(Intent intent) {
        if (intent == null) return;
        String o = intent.getStringExtra("open");
        if (o != null && !o.isEmpty()) pendingOpen = o;
        intent.removeExtra("open");
    }

    private void deliverOpen() {
        if (!pageReady || pendingOpen == null || web == null) return;
        String o = pendingOpen.replaceAll("[^A-Za-z0-9:_-]", "");
        pendingOpen = null;
        web.evaluateJavascript("setTimeout(function(){window.ppOpen&&ppOpen('" + o + "')},300)", null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override
    protected void onDestroy() {
        if (downloadReceiver != null) {
            try { unregisterReceiver(downloadReceiver); } catch (Exception ignored) { }
        }
        super.onDestroy();
    }

    private void js(String code) {
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript(code, null); });
    }

    private void installDownloaded(File f) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 77 && web != null) {
            boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            web.evaluateJavascript("window.ppNotifResult&&ppNotifResult(" + ok + ")", null);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    /** Phone back button: go back one screen inside the app; leave the app only from the first screen. */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    /** Small helpers the page can call as window.Android.*. */
    private class Bridge {
        /** Save the reminder schedule (two JSON lists) and set the alarms. */
        @JavascriptInterface
        public void setReminders(String dailyJson, String onceJson) {
            Reminders.save(MainActivity.this, dailyJson == null ? "[]" : dailyJson, onceJson == null ? "[]" : onceJson);
        }

        /** "granted", "denied" or "ask" (Android 13+ asks the person once). */
        @JavascriptInterface
        public String notifState() {
            if (Build.VERSION.SDK_INT < 33) {
                android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                return nm.areNotificationsEnabled() ? "granted" : "denied";
            }
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED) return "granted";
            return getSharedPreferences("pp_reminders", MODE_PRIVATE).getBoolean("asked", false) && !shouldShowRequestPermissionRationale("android.permission.POST_NOTIFICATIONS") ? "denied" : "ask";
        }

        @JavascriptInterface
        public void askNotif() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    getSharedPreferences("pp_reminders", MODE_PRIVATE).edit().putBoolean("asked", true).apply();
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 77);
                } else {
                    openNotifSettings();
                }
            });
        }

        @JavascriptInterface
        public void openNotifSettings() {
            runOnUiThread(() -> {
                try {
                    Intent i = new Intent("android.settings.APP_NOTIFICATION_SETTINGS");
                    i.putExtra("android.provider.extra.APP_PACKAGE", getPackageName());
                    startActivity(i);
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
                    } catch (Exception ignored) { }
                }
            });
        }

        @JavascriptInterface
        public void testNotif(String title, String text, String open) {
            ReminderReceiver.show(MainActivity.this, 999, title, text, open);
        }

        @JavascriptInterface
        public void updateWidget(String balance, String sub) {
            try { BalanceWidget.refresh(MainActivity.this, balance, sub); } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public String appVersionName() {
            try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
            catch (Exception e) { return "?"; }
        }

        @JavascriptInterface
        @SuppressWarnings("deprecation")
        public int appVersionCode() {
            try {
                PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
                return Build.VERSION.SDK_INT >= 28 ? (int) pi.getLongVersionCode() : pi.versionCode;
            } catch (Exception e) { return 0; }
        }

        @JavascriptInterface
        public void openUrl(String url) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception ignored) { }
        }

        /** Download the new APK inside the app, then open Android's installer. */
        @JavascriptInterface
        public void downloadUpdate(String url, String fileName) {
            runOnUiThread(() -> {
                try {
                    String name = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
                    File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    File old = new File(dir, name);
                    if (old.exists()) old.delete();
                    DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                    r.setTitle("Purse & Pulse update");
                    r.setMimeType("application/vnd.android.package-archive");
                    r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    r.setDestinationInExternalFilesDir(MainActivity.this, Environment.DIRECTORY_DOWNLOADS, name);
                    updateFileName = name;
                    if (downloadReceiver == null) {
                        downloadReceiver = new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context c, Intent intent) {
                                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                                if (id != updateDownloadId) return;
                                File f = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), updateFileName);
                                if (f.exists() && f.length() > 0) {
                                    js("window.ppUpdateReady&&ppUpdateReady()");
                                    try { installDownloaded(f); } catch (Exception e) { js("window.ppUpdateFailed&&ppUpdateFailed()"); }
                                } else {
                                    js("window.ppUpdateFailed&&ppUpdateFailed()");
                                }
                            }
                        };
                        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
                        if (Build.VERSION.SDK_INT >= 33) {
                            registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED);
                        } else {
                            registerReceiver(downloadReceiver, filter);
                        }
                    }
                    updateDownloadId = dm.enqueue(r);
                } catch (Exception e) {
                    js("window.ppUpdateFailed&&ppUpdateFailed()");
                }
            });
        }

        @JavascriptInterface
        public void copyText(String text) {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Purse & Pulse backup", text));
        }

        @JavascriptInterface
        public String pasteText() {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip().getItemCount() == 0) return "";
            CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(MainActivity.this);
            return t == null ? "" : t.toString();
        }

        @JavascriptInterface
        public void shareFile(String fileName, String content) {
            try {
                File dir = new File(getCacheDir(), "backups");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, fileName.replaceAll("[^A-Za-z0-9._-]", "_"));
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(content.getBytes(StandardCharsets.UTF_8));
                }
                Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", f);
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("application/json");
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.putExtra(Intent.EXTRA_SUBJECT, fileName);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(send, "Save or send backup"));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Could not share the backup: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }
    }
}
