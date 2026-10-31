package com.cosmerearchives.audiobookresearcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.TextView;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

final class UpdateManager {
    private static final String API = "https://api.github.com/repos/cosmerearchives-creator/Audiobook-Researcher/releases/latest";
    private static final String APK = "audiobook-researcher.apk";
    private final Activity activity;
    private final TextView status;
    private final Handler main = new Handler(Looper.getMainLooper());
    private long downloadId = -1;
    private final DownloadManager downloads;

    UpdateManager(Activity activity, TextView status) {
        this.activity = activity;
        this.status = status;
        downloads = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        downloadId = activity.getPreferences(0).getLong("downloadId", -1);
    }

    void check() {
        status.setText("Checking for updates…");
        Executors.newSingleThreadExecutor().execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(API).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "Audiobook-Researcher-Android");
                if (connection.getResponseCode() != 200) throw new IllegalStateException("Release channel unavailable (" + connection.getResponseCode() + ")");
                byte[] bytes;
                try (java.io.InputStream input = connection.getInputStream()) { bytes = readAll(input); }
                JSONObject release = new JSONObject(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                String tag = release.getString("tag_name");
                int remoteCode = parseVersion(tag);
                int currentCode = Build.VERSION.SDK_INT >= 28
                        ? (int) activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).getLongVersionCode()
                        : activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionCode;
                if (remoteCode <= currentCode) { main.post(() -> status.setText("You're up to date (" + tag + ")")); return; }
                String assetUrl = null;
                for (int i = 0; i < release.getJSONArray("assets").length(); i++) {
                    JSONObject asset = release.getJSONArray("assets").getJSONObject(i);
                    if (APK.equals(asset.optString("name"))) { assetUrl = asset.getString("browser_download_url"); break; }
                }
                if (assetUrl == null) throw new IllegalStateException("Update APK missing from release");
                String url = assetUrl;
                main.post(() -> new AlertDialog.Builder(activity)
                        .setTitle("Update available: " + tag)
                        .setMessage("Download the update now? Android may ask for permission before installation.")
                        .setNegativeButton("Later", (d, w) -> status.setText("Update available: " + tag))
                        .setPositiveButton("Download", (d, w) -> download(url))
                        .show());
            } catch (Exception ex) {
                main.post(() -> status.setText("Update check unavailable: " + ex.getMessage()));
            } finally { if (connection != null) connection.disconnect(); }
        });
    }

    private static int parseVersion(String tag) {
        if (!tag.matches("v0\\.1\\.[0-9]+")) throw new IllegalArgumentException("Unexpected release version: " + tag);
        return Integer.parseInt(tag.substring("v0.1.".length()));
    }

    private static byte[] readAll(java.io.InputStream input) throws java.io.IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192]; int count;
        while ((count = input.read(chunk)) != -1) output.write(chunk, 0, count);
        return output.toByteArray();
    }

    private void download(String url) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle("Audiobook Researcher update");
        request.setDescription("Signed app update");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setMimeType("application/vnd.android.package-archive");
        request.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, APK);
        try {
            downloadId = downloads.enqueue(request);
            activity.getPreferences(0).edit().putLong("downloadId", downloadId).apply();
            status.setText("Update downloading. Return to the app to install when ready.");
        } catch (Exception ex) { status.setText("Download failed: " + ex.getMessage()); }
    }

    void onResume() {
        if (downloadId < 0) return;
        try (Cursor cursor = downloads.query(new DownloadManager.Query().setFilterById(downloadId))) {
            if (cursor == null || !cursor.moveToFirst()) return;
            int code = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (code == DownloadManager.STATUS_SUCCESSFUL) {
                if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                    status.setText("Allow app updates in Android settings, then return here.");
                    Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(settings);
                    return;
                }
                Uri uri = downloads.getUriForDownloadedFile(downloadId);
                if (uri != null) {
                    downloadId = -1;
                    activity.getPreferences(0).edit().remove("downloadId").apply();
                    Intent install = new Intent(Intent.ACTION_VIEW);
                    install.setDataAndType(uri, "application/vnd.android.package-archive");
                    install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    activity.startActivity(install);
                }
            } else if (code == DownloadManager.STATUS_FAILED) {
                status.setText("Update download failed. Try again.");
                downloadId = -1;
                activity.getPreferences(0).edit().remove("downloadId").apply();
            }
        } catch (Exception ex) { status.setText("Update installation unavailable: " + ex.getMessage()); }
    }
}
