package com.cosmerearchives.audiobookresearcher;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AssistantOverlayDiagnostics extends AccessibilityService {
    static final String HISTORY = "assistantOverlayDiagnosticHistory";
    private static final int MAX_EVENTS = 12;

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        String packageName = String.valueOf(event.getPackageName());
        if (!"com.google.android.googlequicksearchbox".equals(packageName)
                && !"com.google.android.apps.bard".equals(packageName)) return;

        CharSequence className = event.getClassName();
        String entry = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date())
            + " • " + packageName + " / " + (className == null ? "Unknown window class" : className);
        var prefs = getSharedPreferences("library", MODE_PRIVATE);
        String history = prefs.getString(HISTORY, "");
        String[] previous = history.isEmpty() ? new String[0] : history.split("\n");
        StringBuilder updated = new StringBuilder(entry);
        for (int i = 0; i < Math.min(previous.length, MAX_EVENTS - 1); i++) {
            updated.append('\n').append(previous[i]);
        }
        prefs.edit().putString(HISTORY, updated.toString()).apply();
    }

    @Override public void onInterrupt() {
    }
}
