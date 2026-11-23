package com.cosmerearchives.audiobookresearcher;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.function.BooleanSupplier;

public class AssistantOverlayDiagnostics extends AccessibilityService {
    static final String HISTORY = "assistantOverlayDiagnosticHistory";
    static final String HANDOFF_STATUS = "assistantHandoffStatus";
    static final String DICTATE_AFTER_GEMINI = "dictateAfterGemini";
    private static final String GEMINI_OVERLAY =
        "com.google.android.apps.search.assistant.surfaces.voice.robin.ui.floaty.activity.FloatyActivity";
    private static final int MAX_EVENTS = 12;
    private static final long OVERLAY_HANDOFF_MS = 8000;
    private static final long DUPLICATE_EVENT_MS = 1000;
    private static long overlayAt = -1;
    private static boolean overlayConsumed = true;

    static void checkHandoff(Activity activity, Handler handler, BooleanSupplier ready, Runnable startNote) {
        if (!activity.getSharedPreferences("library", MODE_PRIVATE)
                .getBoolean(DICTATE_AFTER_GEMINI, false)) return;
        handler.postDelayed(new Runnable() {
            private int checksRemaining = 5;

            @Override public void run() {
                if (activity.isFinishing() || activity.isDestroyed()
                        || !activity.getSharedPreferences("library", MODE_PRIVATE)
                            .getBoolean(DICTATE_AFTER_GEMINI, false)) return;
                if (activity.hasWindowFocus() && ready.getAsBoolean() && consumeOverlay(activity)) {
                    startNote.run();
                } else if (--checksRemaining > 0) {
                    handler.postDelayed(this, 300);
                }
            }
        }, 300);
    }

    private static boolean consumeOverlay(Context context) {
        if (overlayConsumed || SystemClock.uptimeMillis() - overlayAt > OVERLAY_HANDOFF_MS) return false;
        PlaybackService playback = PlaybackService.instance;
        if (playback == null || playback.player == null || playback.uri == null) {
            recordHandoff(context, "Gemini detected, waiting for a loaded audiobook");
            return false;
        }
        overlayConsumed = true;
        recordHandoff(context, "Gemini detected, handing off to Notes");
        return true;
    }

    private static void recordHandoff(Context context, String status) {
        String timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
        context.getSharedPreferences("library", MODE_PRIVATE).edit()
            .putString(HANDOFF_STATUS, timestamp + " • " + status).apply();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        String packageName = String.valueOf(event.getPackageName());
        if (!"com.google.android.googlequicksearchbox".equals(packageName)
                && !"com.google.android.apps.bard".equals(packageName)) return;

        CharSequence className = event.getClassName();
        String entry = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date())
            + " • " + packageName + " / " + (className == null ? "Unknown window class" : className);
        var prefs = getSharedPreferences("library", MODE_PRIVATE);
        if ("com.google.android.googlequicksearchbox".equals(packageName)
                && GEMINI_OVERLAY.contentEquals(className == null ? "" : className)
                && prefs.getBoolean(DICTATE_AFTER_GEMINI, false)) {
            long now = SystemClock.uptimeMillis();
            if (overlayAt < 0 || now - overlayAt > DUPLICATE_EVENT_MS) {
                overlayConsumed = false;
                recordHandoff(this, "Gemini detected, waiting for Codex to regain focus");
            }
            overlayAt = now;
        }
        String history = prefs.getString(HISTORY, "");
        String[] previous = history.isEmpty() ? new String[0] : history.split("\n");
        StringBuilder updated = new StringBuilder(entry);
        for (int i = 0; i < Math.min(previous.length, MAX_EVENTS - 1); i++) {
            updated.append('\n').append(previous[i]);
        }
        prefs.edit().putString(HISTORY, updated.toString()).apply();
    }

    @Override public void onDestroy() {
        overlayAt = -1;
        overlayConsumed = true;
        super.onDestroy();
    }

    @Override public void onInterrupt() {
    }
}
