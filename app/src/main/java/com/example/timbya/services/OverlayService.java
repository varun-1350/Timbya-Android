package com.example.timbya.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.example.timbya.actions.ActionExecutor;
import com.example.timbya.ai.GeminiManager;
import com.example.timbya.core.TimbyaEngine;
import com.example.timbya.core.TimbyaListener;
import com.example.timbya.overlay.OverlayCallbacks;
import com.example.timbya.overlay.OverlayController;
import com.example.timbya.speech.Speaker;
import com.example.timbya.speech.SpeechManager;
import com.example.timbya.speech.SpeechListener;
import android.util.Log;

public class OverlayService extends Service {

    private static final String TAG = "TIMBYA_OVERLAY";

    private OverlayController controller;

    private Speaker speaker;

    private SpeechManager speechManager;

    private TimbyaEngine engine;

    @Override
    public void onCreate() {

        super.onCreate();

        speaker = new Speaker(this);
        speechManager = new SpeechManager(this);

        GeminiManager geminiManager =
                new GeminiManager();

        ActionExecutor actionExecutor =
                new ActionExecutor(this);

        engine = new TimbyaEngine(
                actionExecutor,
                geminiManager
        );

        controller = new OverlayController(
                this,
                new OverlayCallbacks() {

                    @Override
                    public void onMicPressed() {

                        controller.setStatus("Listening...");

                        speechManager.startListening(new SpeechListener() {

                            @Override
                            public void onSpeechResult(String text) {
                                OverlayService.this.onSpeechResult(text);
                            }

                            @Override
                            public void onSpeechError(String error) {
                                Log.e(TAG, "Speech error: " + error);
                                controller.setStatus("Ready");
                            }

                            @Override
                            public void onMicStatus(String status) {
                                Log.d(TAG, "Mic status: " + status);
                            }

                            @Override
                            public void onSpeechDetected() {
                                controller.setStatus("Listening (speech detected)...");
                            }

                            @Override
                            public void onPartialResult(String partialText) {
                                controller.setReply(partialText);
                            }

                        });

                    }

                });

        controller.show();

        controller.setStatus("Ready");

    }

    public void onSpeechResult(String text){

        controller.setStatus("Thinking...");

        engine.process(text,
                new TimbyaListener() {

                    @Override
                    public void onReply(String reply) {

                        controller.setReply(reply);

                        controller.setStatus("Speaking...");

                        speechManager.mute();

                        speaker.say(reply, () -> {
                            controller.setStatus("Ready");
                            speechManager.resume();
                        });

                    }

                    @Override
                    public void onError(String error) {

                        controller.setReply(error);

                        controller.setStatus("Ready");

                    }

                });

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {

        return null;

    }

    @Override
    public void onDestroy() {

        controller.hide();

        speaker.shutdown();

        speechManager.destroy();

        super.onDestroy();

    }

}