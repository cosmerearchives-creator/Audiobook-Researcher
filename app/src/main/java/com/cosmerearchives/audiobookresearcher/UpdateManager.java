package com.cosmerearchives.audiobookresearcher;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.Executors;

final class UpdateManager {
    private static final String API = "https://api.github.com/repos/cosmerearchives-creator/Audiobook-Researcher/releases/latest";
    private static final String APK = "audiobook-researcher.apk";
    private final Activity activity;
    private final TextView status;
    private final ProgressBar progress;
    private final TextView details;
    private final TextView installButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DownloadManager downloads;
    private long downloadId;
    private boolean checking;
    private boolean installing;
    private String version = "";
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (downloadId < 0) return;
            updateDownloadState();
            if (downloadId >= 0) handler.postDelayed(this, 700);
        }
    };

    UpdateManager(Activity activity, TextView status, ProgressBar progress, TextView details, TextView installButton) {
        this.activity=activity; this.status=status; this.progress=progress;
        this.details=details; this.installButton=installButton;
        downloads=(DownloadManager)activity.getSystemService(Context.DOWNLOAD_SERVICE);
        downloadId=activity.getPreferences(0).getLong("downloadId",-1);
        installButton.setOnClickListener(v->install());
        installButton.setVisibility(View.GONE);
        if(downloadId>=0) {
            status.setText("Checking download progress…");
            startPolling();
        }
    }

    void check() {
        if (downloadId>=0) { startPolling(); return; }
        if (checking) return;
        checking=true;
        status.setText("Checking for updates…");
        details.setText("Contacting GitHub Releases");
        progress.setVisibility(View.VISIBLE); progress.setIndeterminate(true);
        installButton.setVisibility(View.GONE);
        Executors.newSingleThreadExecutor().execute(() -> {
            HttpURLConnection connection=null;
            try {
                connection=(HttpURLConnection)new URL(API).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("Accept","application/vnd.github+json");
                connection.setRequestProperty("User-Agent","Audiobook-Researcher-Android");
                if(connection.getResponseCode()!=200) throw new IllegalStateException("GitHub returned "+connection.getResponseCode());
                byte[] bytes;
                try(java.io.InputStream input=connection.getInputStream()){ bytes=readAll(input); }
                JSONObject release=new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
                String tag=release.getString("tag_name");
                int remote=parseVersion(tag);
                int installed=Build.VERSION.SDK_INT>=28
                        ? (int)activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).getLongVersionCode()
                        : activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionCode;
                if(remote<=installed) {
                    handler.post(()->{
                        checking=false; progress.setVisibility(View.GONE);
                        status.setText("Up to date");
                        details.setText("Installed version: "+tag);
                    });
                    return;
                }
                String url=null;
                for(int i=0;i<release.getJSONArray("assets").length();i++){
                    JSONObject asset=release.getJSONArray("assets").getJSONObject(i);
                    if(APK.equals(asset.optString("name"))) { url=asset.getString("browser_download_url");break; }
                }
                if(url==null) throw new IllegalStateException("No APK attached to the latest release");
                String finalUrl=url;
                handler.post(()->{checking=false;version=tag;download(finalUrl);});
            }catch(Exception ex) {
                handler.post(()->{
                    checking=false;progress.setVisibility(View.GONE);
                    status.setText("Unable to check for updates");
                    details.setText(ex.getMessage());
                });
            }finally{if(connection!=null)connection.disconnect();}
        });
    }

    private static int parseVersion(String tag) {
        if(!tag.matches("v0\\.1\\.[0-9]+")) throw new IllegalArgumentException("Unsupported release tag: "+tag);
        return Integer.parseInt(tag.substring("v0.1.".length()));
    }
    private static byte[] readAll(java.io.InputStream stream) throws java.io.IOException {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        byte[] bytes=new byte[8192];int n;
        while((n=stream.read(bytes))!=-1)out.write(bytes,0,n);
        return out.toByteArray();
    }
    private void download(String url) {
        java.io.File target=new java.io.File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),APK);
        if(target.exists()&&!target.delete()){
            status.setText("Update download couldn't start");
            details.setText("Could not replace the previous APK");
            progress.setVisibility(View.GONE);
            return;
        }
        DownloadManager.Request request=new DownloadManager.Request(Uri.parse(url));
        request.setTitle("Audiobook Researcher update");
        request.setDescription("Downloading "+version);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setMimeType("application/vnd.android.package-archive");
        request.setDestinationInExternalFilesDir(activity,Environment.DIRECTORY_DOWNLOADS,APK);
        try {
            downloadId=downloads.enqueue(request);
            activity.getPreferences(0).edit().putLong("downloadId",downloadId).apply();
            status.setText("Downloading update "+version);
            progress.setVisibility(View.VISIBLE);
            progress.setIndeterminate(true);
            startPolling();
        }catch(Exception ex) {
            status.setText("Update download failed");
            details.setText(ex.getMessage());
            progress.setVisibility(View.GONE);
        }
    }
    private void startPolling() {
        handler.removeCallbacks(poll);
        updateDownloadState();
        if(downloadId>=0)handler.postDelayed(poll,700);
    }
    private void updateDownloadState() {
        if(downloadId<0)return;
        try(Cursor cursor=downloads.query(new DownloadManager.Query().setFilterById(downloadId))) {
            if(cursor==null||!cursor.moveToFirst()){
                clearDownload();
                progress.setVisibility(View.GONE);
                status.setText("Update download not found");
                details.setText("Check for updates to try again.");
                return;
            }
            int state=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            long downloaded=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long total=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            if(state==DownloadManager.STATUS_SUCCESSFUL) {
                status.setText("Update downloaded — ready to install");
                details.setText(size(downloaded)+" downloaded. Android will ask you to confirm installation.");
                progress.setVisibility(View.VISIBLE);
                progress.setIndeterminate(false);progress.setMax(100);progress.setProgress(100);
                installButton.setVisibility(View.VISIBLE);
                handler.removeCallbacks(poll);
            }else if(state==DownloadManager.STATUS_FAILED) {
                status.setText("Update download failed");
                details.setText("Check for updates to retry.");
                progress.setVisibility(View.GONE);
                installButton.setVisibility(View.GONE);
                clearDownload();
            }else{
                status.setText(state==DownloadManager.STATUS_PAUSED?"Download paused":"Downloading update"+(version.isEmpty()?"":" "+version));
                progress.setVisibility(View.VISIBLE);
                installButton.setVisibility(View.GONE);
                if(total>0){
                    int percent=(int)Math.min(100,downloaded*100/total);
                    progress.setIndeterminate(false);progress.setMax(100);progress.setProgress(percent);
                    details.setText(percent+"%  •  "+size(downloaded)+" / "+size(total));
                }else{
                    progress.setIndeterminate(true);
                    details.setText(size(downloaded)+" downloaded • total size not yet available");
                }
            }
        }catch(Exception ex){
            status.setText("Unable to read update progress");
            details.setText(ex.getMessage());
        }
    }
    private static String size(long bytes) {
        if(bytes<0)return "Unknown size";
        return String.format(Locale.US,"%.1f MB",bytes/(1024.0*1024.0));
    }
    private void install() {
        if(downloadId<0||installing)return;
        if(Build.VERSION.SDK_INT>=26&&!activity.getPackageManager().canRequestPackageInstalls()){
            status.setText("Allow installation from this app");
            details.setText("Enable permission in Android settings, then tap Install update again.");
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));
            return;
        }
        Uri uri=downloads.getUriForDownloadedFile(downloadId);
        if(uri==null){status.setText("Downloaded APK unavailable");return;}
        Intent intent=new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri,"application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{
            installing=true;
            activity.startActivity(intent);
            status.setText("Android installer opened");
            details.setText("Confirm the update in Android. Your notes and books will remain saved.");
        }catch(Exception ex){
            installing=false;
            status.setText("Could not open installer");
            details.setText(ex.getMessage());
        }
    }
    private void clearDownload(){
        downloadId=-1;
        activity.getPreferences(0).edit().remove("downloadId").apply();
        handler.removeCallbacks(poll);
    }
    void onResume(){ installing=false;if(downloadId>=0)startPolling(); }
    void onDestroy(){handler.removeCallbacks(poll);}
}
