package com.cosmerearchives.audiobookresearcher;

import android.app.Activity;
import android.app.ComponentCaller;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import java.text.DateFormat;
import java.util.Date;

public class CodexLauncherActivity extends Activity {
    static final String LAST_LAUNCH = "lastLauncherDelivery";
    private static final String LAUNCH_COUNT = "launcherDeliveryCount";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state == null) {
            ComponentCaller caller = Build.VERSION.SDK_INT >= 35 ? getInitialCaller() : null;
            recordLaunch(getIntent(), caller, getReferrer());
        }
        openCodex(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        ComponentCaller caller = Build.VERSION.SDK_INT >= 35 ? getCurrentCaller() : null;
        recordLaunch(intent, caller, null);
        openCodex(intent);
    }

    private void recordLaunch(Intent intent, ComponentCaller caller, Uri referrer) {
        SharedPreferences prefs = getSharedPreferences("library", MODE_PRIVATE);
        int count = prefs.getInt(LAUNCH_COUNT, 0) + 1;
        String callerPackage = caller == null ? null : caller.getPackage();
        String referrerPackage = referrer != null && "android-app".equals(referrer.getScheme())
            ? referrer.getHost() : null;
        String action = intent.getAction();
        String report = "Launch #" + count + " • "
            + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(new Date())
            + "\nCaller: " + (callerPackage == null ? "Unavailable" : callerPackage)
            + "\nReferrer (unverified): " + (referrerPackage == null ? "Unavailable" : referrerPackage)
            + "\nAction: " + (action == null ? "None" : action);
        prefs.edit().putInt(LAUNCH_COUNT, count).putString(LAST_LAUNCH, report).apply();
    }

    private void openCodex(Intent intent) {

        PlaybackService playback = PlaybackService.instance;
        boolean noteRequested = MainActivity.ACTION_NOTE.equals(intent.getAction());
        boolean captureOnOpen = getSharedPreferences("library", MODE_PRIVATE)
            .getBoolean(MainActivity.OPEN_TO_DICTATE, false);
        if (captureOnOpen && playback != null && playback.wasPlayingBeforeNote()) {
            noteRequested = true;
        }

        Intent destination = new Intent(this, MainActivity.class);
        if (noteRequested) {
            destination.setAction(MainActivity.ACTION_NOTE);
            destination.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        }
        startActivity(destination);
        finish();
    }
}
