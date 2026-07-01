package com.example.timbya.services;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.timbya.actions.ActionExecutor;
import com.example.timbya.ai.GeminiManager;
import com.example.timbya.core.TimbyaEngine;
import com.example.timbya.core.TimbyaListener;
import com.example.timbya.core.TimbyaState;
import com.example.timbya.memory.MemoryManager;
import com.example.timbya.overlay.OverlayCallbacks;
import com.example.timbya.overlay.OverlayController;
import com.example.timbya.speech.Speaker;
import com.example.timbya.speech.SpeechListener;
import com.example.timbya.speech.SpeechManager;

public class OverlayService extends Service {

    private static final String TAG = "TIMBYA_OVERLAY";
    private static final long SPEAKING_WATCHDOG_MS = 10_000L;

    private OverlayController controller;
    private Speaker speaker;
    private SpeechManager speechManager;
    private TimbyaEngine engine;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TimbyaState state = TimbyaState.IDLE;
    private Runnable speakingWatchdog;
    private SpeechListener speechListener;

    @Override
    public void onCreate() {
        super.onCreate();

        speaker = new Speaker(this);
        speechManager = new SpeechManager(this);
        MemoryManager memoryManager = new MemoryManager(this);
        GeminiManager geminiManager = new GeminiManager();
        ActionExecutor actionExecutor = new ActionExecutor(this);

        engine = new TimbyaEngine(actionExecutor, geminiManager, memoryManager);

        speechListener = new SpeechListener() {
            @Override public void onSpeechResult(String text) {
                OverlayService.this.onSpeechResult(text);
            }
            @Override public void onSpeechError(String error) {
                Log.e(TAG, "Speech error: " + error);
            }
            @Override public void onMicStatus(String status) {
                Log.d(TAG, "Mic status: " + status);
            }
            @Override public void onSpeechDetected() {
                if (state == TimbyaState.LISTENING) {
                    controller.setStatus("Listening (speech detected)...");
                }
            }
            @Override public void onPartialResult(String partialText) {
                controller.setReply(partialText);
            }
        };

        controller = new OverlayController(this, new OverlayCallbacks() {

            @Override
            public void onMicToggle() {
                switch (state) {
                    case LISTENING:
                        speechManager.stopListening();
                        setState(TimbyaState.IDLE);
                        break;
                    case SPEAKING:
                        cancelSpeakingWatchdog();
                        speaker.stop();
                        setState(TimbyaState.LISTENING);
                        speechManager.resume();
                        break;
                    case IDLE:
                    case OFF:
                    case PROCESSING:
                    default:
                        setState(TimbyaState.LISTENING);
                        speechManager.startListening(speechListener);
                        break;
                }
            }

            @Override
            public void onShrinkToggle() {
                controller.setShrunk(!controller.isShrunk());
            }

            @Override
            public void onPowerOff() {
                cancelSpeakingWatchdog();
                speechManager.stopListening();
                speaker.stop();
                setState(TimbyaState.OFF);
                controller.setReply("");
            }

            @Override
            public void onClose() {
                controller.hide();
            }
        });

        controller.show();

        setState(TimbyaState.LISTENING);
        speechManager.startListening(speechListener);
    }

    public void onSpeechResult(String text){

        cancelSpeakingWatchdog();
        setState(TimbyaState.PROCESSING);

        engine.process(text, new TimbyaListener() {

            @Override
            public void onReply(String reply) {
                controller.setReply(reply);
                setState(TimbyaState.SPEAKING);

                speechManager.mute();
                armSpeakingWatchdog();

                speaker.say(reply, () -> {
                    cancelSpeakingWatchdog();
                    setState(TimbyaState.LISTENING);
                    speechManager.resume();
                });
            }

            @Override
            public void onError(String error) {
                controller.setReply(error);
                setState(TimbyaState.LISTENING);
            }
        });
    }

    private void setState(TimbyaState newState) {
        state = newState;
        Log.d(TAG, "STATE -> " + newState);
        controller.setStatus(newState.label());
        controller.setMicActive(newState == TimbyaState.LISTENING);
    }

    private void armSpeakingWatchdog() {
        cancelSpeakingWatchdog();
        speakingWatchdog = () -> {
            if (state == TimbyaState.SPEAKING) {
                Log.e(TAG, "WATCHDOG: stuck in SPEAKING, forcing reset to LISTENING");
                speaker.stop();
                setState(TimbyaState.LISTENING);
                speechManager.resume();
            }
        };
        mainHandler.postDelayed(speakingWatchdog, SPEAKING_WATCHDOG_MS);
    }

    private void cancelSpeakingWatchdog() {
        if (speakingWatchdog != null) {
            mainHandler.removeCallbacks(speakingWatchdog);
            speakingWatchdog = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        cancelSpeakingWatchdog();
        controller.hide();
        speaker.shutdown();
        speechManager.destroy();
        super.onDestroy();
    }
}