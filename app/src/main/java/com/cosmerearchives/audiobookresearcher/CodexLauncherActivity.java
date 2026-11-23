package com.cosmerearchives.audiobookresearcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class CodexLauncherActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);

        PlaybackService playback = PlaybackService.instance;
        boolean noteRequested = MainActivity.ACTION_NOTE.equals(getIntent().getAction());
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
