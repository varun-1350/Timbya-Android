package com.example.timbya.speech;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class Speaker {

    private static final String TAG = "TIMBYA_TTS";

    private TextToSpeech tts = null;
    private volatile boolean ttsReady = false;
    private String pendingText = null;
    private Runnable pendingOnDone = null;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final java.util.Random random = new java.util.Random();

    private static final float BASE_RATE = 0.90f;
    private static final float BASE_PITCH = 0.85f;

    private Locale currentLocale = LanguageSegmenter.ENGLISH;
    private final Set<Locale> warnedUnavailableLocales = new HashSet<>();
    // Belt-and-suspenders alongside setAudioAttributes(): some engines still
    // honor the legacy per-utterance Bundle param over the AudioAttributes
    // set on the engine instance. Explicitly pinning STREAM_MUSIC here too
    // costs nothing and closes that gap on engines that prefer it.
    private static final Bundle SPEECH_PARAMS = new Bundle();
    static {
        SPEECH_PARAMS.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
    }

    public Speaker(Context context) {
        // Capture the instance in a local variable BEFORE the lambda closes over it.
        // This breaks the write-before-read race: the local variable is assigned
        // at construction time, visible to the callback regardless of when it fires.
        TextToSpeech[] holder = new TextToSpeech[1];
        holder[0] = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                // Use holder[0], not the field 'tts' — holder[0] is guaranteed
                // to be visible here because it was written before startListening
                // was called on the TTS service.
                holder[0].setLanguage(LanguageSegmenter.ENGLISH);
                holder[0].setSpeechRate(BASE_RATE);
                holder[0].setPitch(BASE_PITCH);

                // Explicitly attribute speech output as media playback. Without
                // this, some OEM audio stacks (notably Xiaomi HyperOS/MIUI)
                // apply a quieter internal gain path to untagged TTS output,
                // even though it nominally shares the same volume slider as
                // other media apps. This does NOT boost gain/amplitude — it
                // only tells the OS which stream/policy category this audio
                // belongs to, so it's treated the same as any other media app.
                holder[0].setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());

                tts = holder[0];   // assign to field only after configuration is done
                ttsReady = true;
                Log.d(TAG, "TTS engine ready");
                if (pendingText != null) {
                    String text = pendingText;
                    Runnable done = pendingOnDone;
                    pendingText = null;
                    pendingOnDone = null;
                    mainHandler.post(() -> say(text, done));
                }
            } else {
                Log.e(TAG, "TTS init failed, status=" + status);
                ttsReady = false;
            }
        });
        // Do NOT assign tts here. It's assigned inside the callback once
        // the engine is confirmed ready. All say() calls check ttsReady first.
    }

    /*public void say(String text) {

        say(text, null);
    }*/

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
        if (!ttsReady || tts == null) {
            Log.w(TAG, "TTS not ready yet, queuing utterance");
            pendingText = text;
            pendingOnDone = onDone;
            return;
        }

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
            tts.speak(chunk.text, queueMode, SPEECH_PARAMS, utteranceId);

            if (chunk.pauseAfterMs > 0 && i < chunks.size() - 1) {
                tts.playSilentUtterance(chunk.pauseAfterMs, TextToSpeech.QUEUE_ADD, batchId + "_pause_" + i);
            }
        }
    }

    /** Switches the active TTS voice, falling back to English if the device
     *  doesn't have a usable voice for the requested locale.
     *  Uses setVoice(Voice) rather than the legacy setLanguage(Locale): on
     *  many engines (this was confirmed to be the actual root cause of the
     *  "always English accent" bug) setLanguage() can report success while
     *  silently leaving the previously active voice in place, especially
     *  when switching languages repeatedly within one long-lived
     *  TextToSpeech instance. Selecting a concrete Voice object is the
     *  reliable path recommended by the TextToSpeech API since API 21. */
    private void ensureLocale(Locale locale) {
        if (locale.equals(currentLocale)) return;

        Voice voice = findBestVoice(locale);
        if (voice != null) {
            try {
                if (tts.setVoice(voice) == TextToSpeech.SUCCESS) {
                    currentLocale = locale;
                    return;
                }
            } catch (Exception e) {
                Log.w(TAG, "setVoice failed for " + locale + ", falling back", e);
            }
        }

        // No usable voice for this locale (not installed, network-only and
        // offline, or setVoice failed) — fall back to English rather than
        // silently mispronouncing in the wrong accent.
        if (!warnedUnavailableLocales.contains(locale)) {
            Log.w(TAG, "No usable TTS voice for " + locale
                    + " — falling back to English. User may need to install "
                    + "this language's voice data under Settings > Accessibility > Text-to-speech.");
            warnedUnavailableLocales.add(locale);
        }
        Voice englishVoice = findBestVoice(LanguageSegmenter.ENGLISH);
        try {
            if (englishVoice != null) tts.setVoice(englishVoice);
            else tts.setLanguage(LanguageSegmenter.ENGLISH);
        } catch (Exception e) {
            Log.w(TAG, "English fallback voice selection failed", e);
        }
        currentLocale = LanguageSegmenter.ENGLISH;
    }

    /** Picks the best installed, offline-capable voice for a locale:
     *  exact locale match preferred, same-language-any-country as a second
     *  choice. Skips voices that require a network connection (avoids
     *  silent failures/latency mid-conversation) and voices whose data
     *  isn't actually downloaded yet. Returns null (never throws) if the
     *  engine's voice list is unavailable or nothing usable is found —
     *  callers must handle null as "fall back to English." */
    private Voice findBestVoice(Locale locale) {
        if (tts == null) return null;
        Set<Voice> voices;
        try {
            voices = tts.getVoices();
        } catch (Exception e) {
            Log.w(TAG, "getVoices() failed", e);
            return null;
        }
        if (voices == null) return null;

        Voice languageMatch = null;
        for (Voice v : voices) {
            if (v == null || v.getLocale() == null) continue;
            if (v.isNetworkConnectionRequired()) continue;
            if (v.getFeatures() != null
                    && v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) continue;

            if (v.getLocale().equals(locale)) {
                return v; // exact match, best possible — stop looking
            }
            if (languageMatch == null && v.getLocale().getLanguage().equals(locale.getLanguage())) {
                languageMatch = v;
            }
        }
        return languageMatch;
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

    /*public void speak(String text) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TIMBYA");
    }*/

    public void stop() {
        if (ttsReady && tts != null) tts.stop();
        pendingText = null;
        pendingOnDone = null;
    }

    public void shutdown() {
        ttsReady = false;
        pendingText = null;
        pendingOnDone = null;
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }
}