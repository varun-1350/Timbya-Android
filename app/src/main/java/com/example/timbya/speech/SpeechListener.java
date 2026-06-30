package com.example.timbya.speech;

public interface SpeechListener {

    void onSpeechResult(String text);

    void onSpeechError(String error);

}