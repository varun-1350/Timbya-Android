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

public class OverlayService extends Service {

    private OverlayController controller;

    private Speaker speaker;

    private TimbyaEngine engine;

    @Override
    public void onCreate() {

        super.onCreate();

        speaker = new Speaker(this);

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

                        /*
                         Stage 8.1

                         SpeechManager.startListening()

                         */

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

                        speaker.say(reply);

                        controller.setStatus("Ready");

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

        super.onDestroy();

    }

}