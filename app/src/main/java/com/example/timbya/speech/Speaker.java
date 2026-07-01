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
    public void say(String text, Runnable onDone){

        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { }

            @Override public void onDone(String utteranceId) {
                if (onDone != null) mainHandler.post(onDone);
            }

            @Override public void onError(String utteranceId) {
                if (onDone != null) mainHandler.post(onDone);
            }
        });

        tts.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "TIMBYA");

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