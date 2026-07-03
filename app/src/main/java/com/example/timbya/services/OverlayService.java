package com.example.timbya.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.timbya.R;
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
import com.example.timbya.ui.MainActivity;
import com.example.timbya.utils.Constants;

public class OverlayService extends Service {

    private static final String TAG = "TIMBYA_OVERLAY";
    private static final long SPEAKING_WATCHDOG_MS = 10_000L;

    public static final String ACTION_SHOW = "com.example.timbya.action.SHOW_OVERLAY";

    private static final String NOTIFICATION_CHANNEL_ID = "timbya_listening";
    private static final int NOTIFICATION_ID = 1001;

    private OverlayController controller;
    private Speaker speaker;
    private SpeechManager speechManager;
    private TimbyaEngine engine;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TimbyaState state = TimbyaState.IDLE;
    private Runnable speakingWatchdog;
    private SpeechListener speechListener;
    private boolean resumeHandled = false;

    @Override
    public void onCreate() {
        super.onCreate();

        // MUST happen before anything touches the microphone: a background
        // process (no visible Activity) is denied mic/audio-focus access on
        // Android 9+. Promoting to a foreground service with a persistent
        // notification is what makes SpeechManager's requestAudioFocus()
        // actually succeed instead of looping forever.
        startForeground(NOTIFICATION_ID, buildNotification());

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
                if (Constants.SHOW_DEBUG_TEXT) {
                    controller.setReply(partialText);
                }
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
                stopSelf();
            }
        });

        controller.show();
        controller.setShrunk(true);
        setState(TimbyaState.IDLE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_SHOW.equals(intent.getAction())) {
            controller.show();
        }
        return START_STICKY;
    }

    public void onSpeechResult(String text) {

        resumeHandled = false;
        cancelSpeakingWatchdog();
        setState(TimbyaState.PROCESSING);

        engine.process(text, new TimbyaListener() {

            @Override
            public void onReply(String reply) {
                controller.setReply(reply);
                setState(TimbyaState.SPEAKING);

                speechManager.mute();
                armSpeakingWatchdog();

                speaker.say(reply, () -> resumeOnce("tts-done"));
            }

            @Override
            public void onError(String error) {
                controller.setReply(error);
                setState(TimbyaState.SPEAKING);

                speechManager.mute();
                armSpeakingWatchdog();

                speaker.say(error, () -> resumeOnce("tts-error-done"));
            }
        });
    }

    private void resumeOnce(String source) {
        if (resumeHandled) {
            Log.d(TAG, "resume already handled this turn, ignoring: " + source);
            return;
        }
        resumeHandled = true;
        cancelSpeakingWatchdog();
        setState(TimbyaState.LISTENING);
        speechManager.resume();
    }

    private void setState(TimbyaState newState) {
        state = newState;
        Log.d(TAG, "STATE -> " + newState);

        String label = newState.label();
        if (newState == TimbyaState.LISTENING && !Constants.SHOW_LISTENING_LABEL) {
            label = "";
        }
        if (newState == TimbyaState.PROCESSING && !Constants.SHOW_AI_STATE) {
            label = "";
        }

        controller.setStatus(label);
        controller.setMicActive(newState == TimbyaState.LISTENING);
        controller.setAiState(newState);
    }

    private void armSpeakingWatchdog() {
        cancelSpeakingWatchdog();
        speakingWatchdog = () -> {
            if (state == TimbyaState.SPEAKING) {
                Log.e(TAG, "WATCHDOG: stuck in SPEAKING, forcing reset to LISTENING");
                speaker.stop();
                resumeOnce("watchdog");
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

    private Notification buildNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Timbya listening",
                NotificationManager.IMPORTANCE_LOW); // low = no sound/heads-up, just persistent icon
        channel.setDescription("Timbya is running and ready to listen");
        if (nm != null) nm.createNotificationChannel(channel);

        Intent tapIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Timbya")
                .setContentText("Listening in the background")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
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
        stopForeground(true);
        super.onDestroy();
    }
}