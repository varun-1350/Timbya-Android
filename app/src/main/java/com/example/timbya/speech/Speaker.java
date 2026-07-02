package com.example.timbya.speech;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

public class Speaker {

    private  TextToSpeech tts = null;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public Speaker(Context context) {

        tts = new TextToSpeech(context, status -> {

            if(status == TextToSpeech.SUCCESS){

                tts.setLanguage(Locale.US);

                tts.setSpeechRate(0.90f);   // Slightly slower

                tts.setPitch(0.85f);        // Slightly deeper

            }

        });

    }
    public void say(String text){
        say(text, null);
    }

    /**
     * Speak with a completion callback, so the mic can be muted while
     * speaking and resumed the instant TTS finishes (prevents Timbya
     * hearing and reacting to its own voice).
     */
    private final java.util.Random random = new java.util.Random();
    private static final float BASE_RATE = 0.90f;
    private static final float BASE_PITCH = 0.85f;

    public void say(String text, Runnable onDone) {

        java.util.List<String> sentences = splitIntoSentences(text);
        if (sentences.isEmpty()) {
            if (onDone != null) mainHandler.post(onDone);
            return;
        }

        String batchId = "TIMBYA_" + System.currentTimeMillis();
        String finalUtteranceId = batchId + "_" + (sentences.size() - 1);

        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { }

            @Override public void onDone(String utteranceId) {
                if (finalUtteranceId.equals(utteranceId) && onDone != null) {
                    mainHandler.post(onDone);
                }
            }

            @Override public void onError(String utteranceId) {
                if (finalUtteranceId.equals(utteranceId) && onDone != null) {
                    mainHandler.post(onDone);
                }
            }
        });

        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            applyNaturalVariation(sentence);

            String utteranceId = batchId + "_" + i;
            int queueMode = (i == 0) ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            tts.speak(sentence, queueMode, null, utteranceId);

            if (i < sentences.size() - 1) {
                tts.playSilentUtterance(pauseAfter(sentence), TextToSpeech.QUEUE_ADD, batchId + "_pause_" + i);
            }
        }
    }

    /** Small per-sentence pitch/rate jitter, plus a slight slow-down on short
     *  or number-bearing sentences (a lightweight stand-in for word-level
     *  emphasis — Android's default TTS engine doesn't reliably support true
     *  SSML emphasis tags across devices). */
    private void applyNaturalVariation(String sentence) {
        float rateJitter = (random.nextFloat() - 0.5f) * 0.08f;   // ±0.04
        float pitchJitter = (random.nextFloat() - 0.5f) * 0.06f;  // ±0.03

        float rate = BASE_RATE + rateJitter;
        float pitch = BASE_PITCH + pitchJitter;

        boolean looksImportant = sentence.matches(".*\\d.*") || sentence.split("\\s+").length <= 5;
        if (looksImportant) rate -= 0.05f;

        tts.setSpeechRate(Math.max(0.75f, rate));
        tts.setPitch(Math.max(0.75f, pitch));
    }

    private long pauseAfter(String sentence) {
        char last = sentence.charAt(sentence.length() - 1);
        long base;
        switch (last) {
            case '?': base = 380; break;
            case '!': base = 300; break;
            case '.': base = 260; break;
            default:  base = 150;
        }
        long jitter = (long) ((random.nextFloat() - 0.5f) * 80); // ±40ms
        return Math.max(80, base + jitter);
    }

    private java.util.List<String> splitIntoSentences(String text) {
        java.util.List<String> result = new java.util.ArrayList<>();
        if (text == null || text.trim().isEmpty()) return result;
        for (String s : text.trim().split("(?<=[.!?])\\s+")) {
            if (!s.trim().isEmpty()) result.add(s.trim());
        }
        return result;
    }
    public void speak(String text){

        tts.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "TIMBYA"
        );

    }

    public void stop(){

        if(tts != null){
            tts.stop();
        }

    }

    public void shutdown(){

        if(tts != null){
            tts.stop();
            tts.shutdown();
        }

    }


}