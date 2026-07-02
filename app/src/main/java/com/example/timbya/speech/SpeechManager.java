package com.example.timbya.speech;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Continuous listening speech manager.
 *
 * IMPORTANT: a fresh SpeechRecognizer is created for every turn instead of
 * reusing one instance across cancel()/startListening() cycles. Reusing a
 * single instance is what corrupts the client state and throws
 * ERROR_CLIENT (5) starting on turn 2 — this is a known Android quirk, not
 * a bug in the calling code. destroy()+recreate per session avoids it.
 *
 * All entry points are guarded to run on the main thread, since
 * SpeechRecognizer is bound to the Looper that creates it.
 */
public class SpeechManager {

    private static final String TAG = "TIMBYA_VOICE";
    private static final long RESTART_DELAY_MS = 350;
    private static final long BEEP_MUTE_MS = 700;

    private static final int[] BEEP_STREAMS = {
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_SYSTEM
    };

    private final Context appContext;
    private final AudioManager audioManager;
    private final Handler mainHandler;
    private final boolean recognitionAvailable;

    private SpeechRecognizer recognizer; // created fresh per session, not final
    private SpeechListener listener;
    private AudioFocusRequest focusRequest; // API 26+

    private boolean isListening = false;
    private boolean shouldRestart = false;
    private boolean isMuted = false;

    public SpeechManager(Context context) {
        appContext = context.getApplicationContext();
        audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        mainHandler = new Handler(Looper.getMainLooper());
        recognitionAvailable = SpeechRecognizer.isRecognitionAvailable(appContext);
        if (!recognitionAvailable) {
            Log.e(TAG, "Speech recognition not available on this device");
        }
    }

    /** Begin continuous listening. Safe to call repeatedly. */
    public void startListening(SpeechListener listener) {
        this.listener = listener;
        this.shouldRestart = true;

        if (!recognitionAvailable) {
            notifyError("Speech recognizer unavailable on this device");
            return;
        }
        if (isListening || isMuted) return;

        runOnMain(this::beginSession);
    }

    /** Fully stop continuous listening (e.g. power button off). */
    public void stopListening() {
        shouldRestart = false;
        runOnMain(() -> {
            teardownRecognizer();
            isListening = false;
        });
        notifyMicStatus("Mic off");
    }

    /** Pause listening while Timbya speaks, and release the recognizer +
     *  its audio focus so TTS gets a clean run at the mic/speaker. */
    public void mute() {
        isMuted = true;
        runOnMain(() -> {
            teardownRecognizer();
            abandonAudioFocus();
            isListening = false;
        });
        notifyMicStatus("Mic muted (speaking)");
    }

    /** Called once per turn when it's time to start listening again. */
    public void resume() {
        isMuted = false;
        if (shouldRestart) scheduleRestart();
    }

    public void destroy() {
        shouldRestart = false;
        runOnMain(this::teardownRecognizer);
        abandonAudioFocus();
    }

    // ---- internals ----

    private void runOnMain(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else mainHandler.post(r);
    }

    private void scheduleRestart() {
        mainHandler.postDelayed(this::beginSession, RESTART_DELAY_MS);
    }

    /** Creates a brand-new SpeechRecognizer for this turn. Must run on the
     *  main thread — SpeechRecognizer is bound to the Looper that creates
     *  it; calling it off that thread is a separate common cause of
     *  ERROR_CLIENT. */
    private void beginSession() {
        if (isListening) return;

        teardownRecognizer(); // safety net, guarantees no stale instance

        if (!requestAudioFocus()) {
            Log.w(TAG, "Audio focus not granted yet, retrying shortly");
            mainHandler.postDelayed(this::beginSession, RESTART_DELAY_MS);
            return;
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext);
        recognizer.setRecognitionListener(new InternalListener());

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra("android.speech.extra.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 15000);
        intent.putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 1500);
        intent.putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 1500);

        silenceStartupBeep();
        recognizer.startListening(intent);
        isListening = true;
        notifyMicStatus("Listening...");
    }

    /** cancel() then destroy() — cancel() releases the AudioRecord cleanly
     *  on some OEM services before the unbind, destroy() unbinds the
     *  recognition service connection entirely. */
    private void teardownRecognizer() {
        if (recognizer != null) {
            try {
                recognizer.cancel();
            } catch (Exception ignored) { }
            recognizer.destroy();
            recognizer = null;
        }
    }

    // ---- audio focus ----

    private boolean requestAudioFocus() {
        if (audioManager == null) return true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();

            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .build();

            return audioManager.requestAudioFocus(focusRequest)
                    == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        } else {
            return audioManager.requestAudioFocus(
                    null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
        } else {
            audioManager.abandonAudioFocus(null);
        }
    }

    /** Best-effort duck of the streams the recognizer beep is commonly
     *  routed on (varies by OEM/Android version). Full removal isn't
     *  possible from app code — this is the least-intrusive available fix. */
    private void silenceStartupBeep() {
        if (audioManager == null) return;
        for (int stream : BEEP_STREAMS) {
            try {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0);
            } catch (Exception e) {
                Log.w(TAG, "Could not mute stream " + stream + " for mic beep", e);
            }
        }
        mainHandler.postDelayed(() -> {
            for (int stream : BEEP_STREAMS) {
                try {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0);
                } catch (Exception ignored) { }
            }
        }, BEEP_MUTE_MS);
    }

    private void notifyMicStatus(String status) {
        Log.d(TAG, "MIC_STATUS: " + status);
        if (listener != null) listener.onMicStatus(status);
    }

    private void notifyError(String error) {
        Log.e(TAG, "ERROR: " + error);
        if (listener != null) listener.onSpeechError(error);
    }

    private class InternalListener implements RecognitionListener {

        @Override public void onReadyForSpeech(Bundle params) { notifyMicStatus("Ready"); }

        @Override public void onBeginningOfSpeech() {
            Log.d(TAG, "SPEECH_DETECTED");
            if (listener != null) listener.onSpeechDetected();
        }

        @Override public void onRmsChanged(float rmsdB) {
            if (listener != null) listener.onAudioLevel(rmsdB);
        }

        @Override public void onBufferReceived(byte[] buffer) { }

        @Override public void onEndOfSpeech() { notifyMicStatus("Processing speech..."); }

        @Override public void onError(int error) {
            isListening = false;
            String message = describeError(error);

            if (error == SpeechRecognizer.ERROR_NO_MATCH
                    || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                Log.d(TAG, "No speech (idle): " + message);
                restartIfNeeded();
                return;
            }

            Log.e(TAG, "RECOGNIZER_ERROR: " + message);
            notifyError(message);
            restartIfNeeded();
        }

        @Override public void onResults(Bundle results) {
            isListening = false;
            ArrayList<String> matches =
                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String transcript = matches.get(0);
                Log.d(TAG, "TRANSCRIPT: " + transcript);
                if (listener != null) listener.onSpeechResult(transcript);
            }
            restartIfNeeded();
        }

        @Override public void onPartialResults(Bundle partialResults) {
            ArrayList<String> matches =
                    partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty() && listener != null) {
                listener.onPartialResult(matches.get(0));
            }
        }

        @Override public void onEvent(int eventType, Bundle params) { }

        private void restartIfNeeded() {
            if (shouldRestart && !isMuted) scheduleRestart();
        }

        private String describeError(int error) {
            switch (error) {
                case SpeechRecognizer.ERROR_AUDIO: return "Audio recording error";
                case SpeechRecognizer.ERROR_CLIENT: return "Client side error";
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Missing RECORD_AUDIO permission";
                case SpeechRecognizer.ERROR_NETWORK: return "Network error";
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Network timeout";
                case SpeechRecognizer.ERROR_NO_MATCH: return "No speech match";
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "Recognizer busy";
                case SpeechRecognizer.ERROR_SERVER: return "Server error";
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "No speech input";
                default: return "Unknown recognizer error (" + error + ")";
            }
        }
    }
}