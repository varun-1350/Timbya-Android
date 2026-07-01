package com.example.timbya.speech;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Continuous listening speech manager. Replaces the old dialog-based
 * RecognizerIntent flow with the headless SpeechRecognizer API, restarted
 * in a loop so the mic effectively stays "always on" while active.
 *
 * NOTE: this does not yet do full-duplex barge-in (listening WHILE Timbya
 * speaks) - that needs acoustic echo cancellation and is a separate task.
 * For now, mute()/resume() are used to pause listening while TTS plays,
 * so Timbya doesn't hear itself and loop.
 */
public class SpeechManager {

    private static final String TAG = "TIMBYA_VOICE";

    private final SpeechRecognizer recognizer;

    private SpeechListener listener;

    private boolean isListening = false;
    private boolean shouldRestart = false;
    private boolean isMuted = false;

    public SpeechManager(Context context) {

        Context appContext = context.getApplicationContext();

        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            Log.e(TAG, "Speech recognition not available on this device");
            recognizer = null;
            return;
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext);
        recognizer.setRecognitionListener(new InternalListener());
    }

    /** Begin continuous listening. Safe to call repeatedly. */
    public void startListening(SpeechListener listener) {

        this.listener = listener;
        this.shouldRestart = true;

        if (recognizer == null) {
            notifyError("Speech recognizer unavailable on this device");
            return;
        }

        if (isListening || isMuted) return;

        beginSession();
    }

    /** Fully stop continuous listening (e.g. power button off). */
    public void stopListening() {
        shouldRestart = false;
        if (recognizer != null) recognizer.stopListening();
        isListening = false;
        notifyMicStatus("Mic off");
    }

    /** Pause listening without ending continuous mode (e.g. while speaking). */
    public void mute() {
        isMuted = true;
        if (recognizer != null) recognizer.stopListening();
        isListening = false;
        notifyMicStatus("Mic muted (speaking)");
    }

    /** Resume listening after mute(). */
    public void resume() {
        isMuted = false;
        if (shouldRestart) beginSession();
    }

    public void destroy() {
        shouldRestart = false;
        if (recognizer != null) recognizer.destroy();
    }

    private void beginSession() {

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        // Don't cut off after ~1s of silence like the default does.
        intent.putExtra("android.speech.extra.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 15000);
        intent.putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 1500);
        intent.putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 1500);

        recognizer.startListening(intent);
        isListening = true;
        notifyMicStatus("Listening...");
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

        @Override public void onReadyForSpeech(Bundle params) {
            notifyMicStatus("Ready");
        }

        @Override public void onBeginningOfSpeech() {
            Log.d(TAG, "SPEECH_DETECTED");
            if (listener != null) listener.onSpeechDetected();
        }

        @Override public void onRmsChanged(float rmsdB) {
            // Crude audio-level diagnostic: confirms mic IS capturing audio
            // and roughly how loud, so a "silent" failure can be pinned to
            // capture vs. recognition vs. network.
            if (listener != null) listener.onAudioLevel(rmsdB);
        }

        @Override public void onBufferReceived(byte[] buffer) { }

        @Override public void onEndOfSpeech() {
            notifyMicStatus("Processing speech...");
        }

        @Override public void onError(int error) {
            isListening = false;
            String message = describeError(error);

            // Silence isn't a failure - keep listening quietly.
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
            if (shouldRestart && !isMuted) beginSession();
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