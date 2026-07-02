package com.example.timbya.speech;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class Speaker {

    private static final String TAG = "TIMBYA_VOICE";

    private TextToSpeech tts = null;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final java.util.Random random = new java.util.Random();

    private static final float BASE_RATE = 0.90f;
    private static final float BASE_PITCH = 0.85f;

    private Locale currentLocale = LanguageSegmenter.ENGLISH;
    private final Set<Locale> warnedUnavailableLocales = new HashSet<>();

    public Speaker(Context context) {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(LanguageSegmenter.ENGLISH);
                tts.setSpeechRate(BASE_RATE);
                tts.setPitch(BASE_PITCH);
            }
        });
    }

    public void say(String text) {
        say(text, null);
    }

    /** One flattened chunk of speech: text, its locale, prosody for the
     *  sentence it belongs to, and whether a pause follows it. */
    private static class SpeechChunk {
        final String text;
        final Locale locale;
        final float rate;
        final float pitch;
        final long pauseAfterMs; // 0 if no pause (mid-sentence segment)

        SpeechChunk(String text, Locale locale, float rate, float pitch, long pauseAfterMs) {
            this.text = text; this.locale = locale;
            this.rate = rate; this.pitch = pitch;
            this.pauseAfterMs = pauseAfterMs;
        }
    }

    /**
     * Speak with a completion callback, so the mic can be muted while
     * speaking and resumed the instant TTS finishes. Each sentence is
     * further split into same-language segments (LanguageSegmenter) so
     * code-switched replies (Hinglish, Marathi+English, German+English)
     * are spoken with the correct native pronunciation per segment,
     * with no pause between segments so the switch sounds fluid rather
     * than fragmented — pauses only happen between whole sentences.
     */
    public void say(String text, Runnable onDone) {

        List<String> sentences = splitIntoSentences(text);
        if (sentences.isEmpty()) {
            if (onDone != null) mainHandler.post(onDone);
            return;
        }

        List<SpeechChunk> chunks = new ArrayList<>();
        for (String sentence : sentences) {
            float rateJitter = (random.nextFloat() - 0.5f) * 0.08f;
            float pitchJitter = (random.nextFloat() - 0.5f) * 0.06f;
            float rate = BASE_RATE + rateJitter;
            float pitch = BASE_PITCH + pitchJitter;
            boolean looksImportant = sentence.matches(".*\\d.*") || sentence.split("\\s+").length <= 5;
            if (looksImportant) rate -= 0.05f;
            rate = Math.max(0.75f, rate);
            pitch = Math.max(0.75f, pitch);

            List<LanguageSegmenter.Segment> segments = LanguageSegmenter.segment(sentence);
            for (int i = 0; i < segments.size(); i++) {
                boolean lastSegmentOfSentence = (i == segments.size() - 1);
                long pause = lastSegmentOfSentence ? pauseAfter(sentence) : 0;
                chunks.add(new SpeechChunk(segments.get(i).text, segments.get(i).locale, rate, pitch, pause));
            }
        }

        if (chunks.isEmpty()) {
            if (onDone != null) mainHandler.post(onDone);
            return;
        }

        String batchId = "TIMBYA_" + System.currentTimeMillis();
        String finalUtteranceId = batchId + "_" + (chunks.size() - 1);

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

        for (int i = 0; i < chunks.size(); i++) {
            SpeechChunk chunk = chunks.get(i);

            ensureLocale(chunk.locale);
            tts.setSpeechRate(chunk.rate);
            tts.setPitch(chunk.pitch);

            String utteranceId = batchId + "_" + i;
            int queueMode = (i == 0) ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            tts.speak(chunk.text, queueMode, null, utteranceId);

            if (chunk.pauseAfterMs > 0 && i < chunks.size() - 1) {
                tts.playSilentUtterance(chunk.pauseAfterMs, TextToSpeech.QUEUE_ADD, batchId + "_pause_" + i);
            }
        }
    }

    /** Switches the active TTS voice locale, falling back to English if the
     *  device doesn't have that language's voice data installed. */
    private void ensureLocale(Locale locale) {
        if (locale.equals(currentLocale)) return;

        int availability = tts.isLanguageAvailable(locale);
        if (availability >= TextToSpeech.LANG_AVAILABLE) {
            tts.setLanguage(locale);
            currentLocale = locale;
        } else {
            if (!warnedUnavailableLocales.contains(locale)) {
                Log.w(TAG, "No TTS voice installed for " + locale
                        + " — falling back to English. User may need to install "
                        + "this language's voice data under Settings > Accessibility > Text-to-speech.");
                warnedUnavailableLocales.add(locale);
            }
            tts.setLanguage(LanguageSegmenter.ENGLISH);
            currentLocale = LanguageSegmenter.ENGLISH;
        }
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
        long jitter = (long) ((random.nextFloat() - 0.5f) * 80);
        return Math.max(80, base + jitter);
    }

    private List<String> splitIntoSentences(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return result;
        for (String s : text.trim().split("(?<=[.!?])\\s+")) {
            if (!s.trim().isEmpty()) result.add(s.trim());
        }
        return result;
    }

    public void speak(String text) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TIMBYA");
    }

    public void stop() {
        if (tts != null) tts.stop();
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}