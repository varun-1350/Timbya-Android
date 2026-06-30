package com.example.timbya.speech;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

public class Speaker {

    private  TextToSpeech tts = null;

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