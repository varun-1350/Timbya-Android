package com.example.timbya.speech;

public interface SpeechListener {

    void onSpeechResult(String text);

    void onSpeechError(String error);

    // --- Diagnostics: Voice Debugging Priority ---

    default void onMicStatus(String status) { }

    default void onSpeechDetected() { }

    default void onAudioLevel(float rmsdB) { }

    default void onPartialResult(String partialText) { }
}