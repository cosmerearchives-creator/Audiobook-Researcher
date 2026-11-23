package com.cosmerearchives.audiobookresearcher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.media.MediaPlayer;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.function.Consumer;

/** One foreground speech recognition session for a timestamped bookmark. */
final class BookmarkDictation {
    private final Context context;
    private final Handler handler;
    private final Consumer<String> finish;
    private final AlertDialog dialog;
    private final TextView status;
    private SpeechRecognizer recognizer;
    private MediaPlayer cuePlayer;
    private final Runnable cueTimeout = this::finishCue;
    private final Runnable timeout = () -> showError("Dictation timed out. Try again.");

    BookmarkDictation(Context context, Handler handler, String timestamp, Consumer<String> finish) {
        this.context = context;
        this.handler = handler;
        this.finish = finish;
        status = new TextView(context);
        int padding = Math.round(24 * context.getResources().getDisplayMetrics().density);
        status.setPadding(padding, padding, padding, padding);
        status.setTextSize(22);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        dialog = new AlertDialog.Builder(context)
            .setTitle("Dictate note at " + timestamp)
            .setView(status)
            .setPositiveButton("Try again", null)
            .setNegativeButton("Cancel", (d, w) -> complete(""))
            .setOnCancelListener(d -> complete(""))
            .create();
    }

    void show() {
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> announceAndListen());
        announceAndListen();
    }

    private void announceAndListen() {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.GONE);
        status.setText("Starting microphone…");
        try {
            cuePlayer = MediaPlayer.create(context, R.raw.begin_dictation);
            if (cuePlayer == null) {
                finishCue();
                return;
            }
            cuePlayer.setOnCompletionListener(player -> {
                if (cuePlayer == player) finishCue();
            });
            cuePlayer.setOnErrorListener((player, what, extra) -> {
                if (cuePlayer == player) finishCue();
                return true;
            });
            handler.postDelayed(cueTimeout, 3000);
            cuePlayer.start();
        } catch (RuntimeException error) {
            finishCue();
        }
    }

    private void finishCue() {
        releaseCue();
        if (dialog.isShowing()) startListening();
    }

    private void releaseCue() {
        handler.removeCallbacks(cueTimeout);
        if (cuePlayer != null) {
            MediaPlayer player = cuePlayer;
            cuePlayer = null;
            player.release();
        }
    }

    private void startListening() {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.GONE);
        status.setText("Starting microphone…");
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            showError("No speech recognition service is available on this phone.");
            return;
        }

        try {
            SpeechRecognizer session = SpeechRecognizer.createSpeechRecognizer(context);
            recognizer = session;
            session.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    if (recognizer == session) status.setText("Listening… Speak now");
                }

                @Override public void onBeginningOfSpeech() {
                    if (recognizer == session) status.setText("Listening…");
                }

                @Override public void onEndOfSpeech() {
                    if (recognizer == session) status.setText("Transcribing…");
                }

                @Override public void onError(int error) {
                    if (recognizer != session) return;
                    switch (error) {
                        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        case SpeechRecognizer.ERROR_NO_MATCH:
                            showError("No speech was recognized. Try again.");
                            break;
                        case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                            showError("Speech recognition is busy. Try again after Gemini closes.");
                            break;
                        default:
                            showError("Dictation could not finish. Try again. (Error " + error + ")");
                    }
                }

                @Override public void onResults(Bundle results) {
                    if (recognizer != session) return;
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String text = matches == null || matches.isEmpty() || matches.get(0) == null
                        ? "" : matches.get(0).trim();
                    if (text.isEmpty()) showError("No speech was recognized. Try again.");
                    else complete(text);
                }

                @Override public void onRmsChanged(float rmsDb) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            handler.postDelayed(timeout, 30000);
            session.startListening(intent);
        } catch (RuntimeException error) {
            showError("The microphone could not start. Try again.");
        }
    }

    private void showError(String message) {
        releaseRecognizer();
        status.setText(message);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.VISIBLE);
    }

    private void complete(String transcript) {
        close();
        finish.accept(transcript);
    }

    void close() {
        releaseCue();
        releaseRecognizer();
        dialog.dismiss();
    }

    private void releaseRecognizer() {
        handler.removeCallbacks(timeout);
        if (recognizer != null) {
            SpeechRecognizer session = recognizer;
            recognizer = null;
            session.destroy();
        }
    }
}
