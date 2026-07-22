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
import com.example.timbya.ui.SettingsActivity;
import android.provider.Settings;
import com.example.timbya.actions.ActionResult;
import com.example.timbya.focus.FocusManager;

import com.example.timbya.services.ScreenReadingAccessibilityService;

public class OverlayService extends Service {
    private boolean listenAfterScreenSummary = false;

    private static final String TAG = "TIMBYA_OVERLAY";
    private static final long MIN_SPEAKING_WATCHDOG_MS = 15_000L;
    private static final long MAX_SPEAKING_WATCHDOG_MS = 90_000L;

    public static final String ACTION_SHOW = "com.example.timbya.action.SHOW_OVERLAY";

    private static final String NOTIFICATION_CHANNEL_ID = "timbya_listening";
    private static final int NOTIFICATION_ID = 1001;
    private ActionExecutor actionExecutor;
    private FocusManager focusManager;

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

        // Guard against a system-triggered restart (START_STICKY) happening
        // after the user has revoked RECORD_AUDIO from Settings. Starting a
        // foregroundServiceType="microphone" service without that permission
        // throws a SecurityException on Android 13/14 and would otherwise
        // crash-loop the service.
        if (androidx.core.content.ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission missing on service (re)start — stopping self");
            stopSelf();
            return;
        }

        try {
            startForeground(NOTIFICATION_ID, buildNotification());
        } catch (Exception e) {
            // Covers ForegroundServiceStartNotAllowedException (API 31+) and any
            // other platform refusal to promote a background-triggered restart
            // (e.g. from onTaskRemoved's AlarmManager kick) to a foreground
            // service. Fail quiet instead of crash-looping the process.
            Log.e(TAG, "startForeground() refused on restart — giving up for this cycle", e);
            stopSelf();
            return;
        }

        speaker = new Speaker(this);
        speechManager = new SpeechManager(this);
        MemoryManager memoryManager = new MemoryManager(this);
        GeminiManager geminiManager = new GeminiManager();
        actionExecutor = new ActionExecutor(this);

        focusManager = new FocusManager(
                this,
                message -> mainHandler.post(() -> speakFocusNudge(message)));

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
                if (Constants.showDebugText(OverlayService.this)) {
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
                        // Mark this turn's resume as already handled BEFORE
                        // stopping TTS: some OEM TextToSpeech engines still
                        // deliver a late onDone/onError callback for the
                        // interrupted utterance, which would otherwise call
                        // resumeOnce() a second time for the same turn.
                        resumeHandled = true;
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
            public void onOpenSettings() {
                Intent intent = new Intent(OverlayService.this, SettingsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
            @Override
            public void onReadScreen() {
                readAndSummarizeCurrentScreen();
            }
            @Override
            public void onUndoAction() {
                ActionResult result = actionExecutor.undoLastAction();

                controller.setUndoAvailable(false);
                speakFocusNudge(result.getReply());
            }


            @Override
            public void onClose() {
                controller.hide();
                stopSelf();
            }
        });

        controller.show();

        controller.setShrunk(Constants.startMinimized(this));

        setState(TimbyaState.IDLE);



    }



    private void readAndSummarizeCurrentScreen() {
        if (!ScreenReadingAccessibilityService.isEnabled(this)) {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);

            controller.setReply(
                    "Enable Timbya in Accessibility settings, then return and press Read Screen.");
            return;
        }

        ScreenReadingAccessibilityService screenReader =
                ScreenReadingAccessibilityService.getInstance();

        if (screenReader == null) {
            controller.setReply(
                    "Timbya's screen reader is still starting. Please try again in a moment.");
            return;
        }

        resumeHandled = false;
        speechManager.stopListening();
        setState(TimbyaState.PROCESSING);

        screenReader.readCurrentScreen(screenText -> {
            if (screenText == null || screenText.trim().isEmpty()) {
                speakScreenResult(
                        "I couldn't find readable text on this screen. "
                                + "This can happen in apps that do not expose their content.");
                return;
            }

            engine.summarizeScreen(screenText, new TimbyaListener() {
                @Override
                public void onReply(String reply) {
                    speakScreenResult(reply);
                }

                @Override
                public void onError(String error) {
                    speakScreenResult(error);
                }
            });
        });
    }

    private void speakScreenResult(String reply) {
        listenAfterScreenSummary = true;

        controller.setReply(reply);
        setState(TimbyaState.SPEAKING);

        speechManager.mute();
        armSpeakingWatchdog(reply);

        speaker.say(reply, () -> resumeOnce("screen-summary-done"));
    }



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_SHOW.equals(intent.getAction()) && controller != null) {
            controller.show();
        }
        return START_STICKY;
    }
    private void refreshUndoButton() {
        if (actionExecutor == null || controller == null) {
            return;
        }

        controller.setUndoAvailable(actionExecutor.hasUndoAvailable());

        mainHandler.postDelayed(
                () -> {
                    if (actionExecutor != null && controller != null) {
                        controller.setUndoAvailable(
                                actionExecutor.hasUndoAvailable());
                    }
                },
                ActionExecutor.UNDO_WINDOW_MS);
    }

    public void onSpeechResult(String text) {
        ActionResult focusResult = focusManager.handleVoiceCommand(text);

        if (focusResult.isHandled()) {
            speakFocusNudge(focusResult.getReply());
            return;
        }

        resumeHandled = false;
        cancelSpeakingWatchdog();
        setState(TimbyaState.PROCESSING);

        engine.process(text, new TimbyaListener() {

            @Override
            public void onReply(String reply) {
                controller.setReply(reply);
                setState(TimbyaState.SPEAKING);

                speechManager.mute();
                armSpeakingWatchdog(reply);
                refreshUndoButton();

                speaker.say(reply, () -> resumeOnce("tts-done"));
            }

            @Override
            public void onError(String error) {
                controller.setReply(error);
                setState(TimbyaState.SPEAKING);

                speechManager.mute();
                armSpeakingWatchdog(error);
                refreshUndoButton();

                speaker.say(error, () -> resumeOnce("tts-error-done"));
            }
        });
    }
    private void speakFocusNudge(String reply) {
        if (state == TimbyaState.SPEAKING
                || state == TimbyaState.PROCESSING) {
            return;
        }

        resumeHandled = false;
        cancelSpeakingWatchdog();

        controller.setReply(reply);
        setState(TimbyaState.SPEAKING);

        speechManager.mute();
        armSpeakingWatchdog(reply);

        speaker.say(reply, () -> resumeOnce("focus-mode"));
    }

    private void resumeOnce(String source) {
        if (resumeHandled) {
            Log.d(TAG, "resume already handled this turn, ignoring: " + source);
            return;
        }

        resumeHandled = true;
        cancelSpeakingWatchdog();
        setState(TimbyaState.LISTENING);

        if (listenAfterScreenSummary) {
            listenAfterScreenSummary = false;
            speechManager.startListening(speechListener);
        } else {
            speechManager.resume();
        }
    }

    private void setState(TimbyaState newState) {
        state = newState;
        Log.d(TAG, "STATE -> " + newState);

        String label = newState.label();
        if (newState == TimbyaState.LISTENING
                && !Constants.showListeningLabel(this)) {
            label = "";
        }
        if (newState == TimbyaState.PROCESSING
                && !Constants.showAiState(this)) {
            label = "";
        }

        controller.setStatus(label);
        controller.setMicActive(newState == TimbyaState.LISTENING);
        controller.setAiState(newState);
    }

    private void armSpeakingWatchdog(String spokenText) {
        cancelSpeakingWatchdog();

        int characterCount = spokenText == null ? 0 : spokenText.length();

        long estimatedSpeechTimeMs = 4_000L + (characterCount * 85L);

        long watchdogDelayMs = Math.max(
                MIN_SPEAKING_WATCHDOG_MS,
                Math.min(MAX_SPEAKING_WATCHDOG_MS, estimatedSpeechTimeMs));

        speakingWatchdog = () -> {
            if (state == TimbyaState.SPEAKING) {
                Log.e(TAG, "WATCHDOG: TTS did not finish in time, forcing reset");
                speaker.stop();
                resumeOnce("watchdog");
            }
        };

        mainHandler.postDelayed(speakingWatchdog, watchdogDelayMs);
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
    public void onTaskRemoved(Intent rootIntent) {
        // HyperOS/MIUI frequently kill this process on task-swipe regardless
        // of foreground-service status — this is a real OEM behavior no
        // in-app fix fully prevents. Best available mitigation: schedule a
        // near-immediate restart so the service comes back on its own rather
        // than staying dead silently.
        Log.w(TAG, "onTaskRemoved: scheduling restart in case the OS kills this process");

        android.app.AlarmManager alarmManager =
                (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null) {
            Intent restartIntent = new Intent(this, OverlayService.class);
            restartIntent.setAction(ACTION_SHOW);
            PendingIntent pendingIntent = PendingIntent.getService(
                    this, 0, restartIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            alarmManager.set(
                    android.app.AlarmManager.RTC,
                    System.currentTimeMillis() + 1000,
                    pendingIntent);
        }

        super.onTaskRemoved(rootIntent);
    }
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (controller != null) controller.reclampToScreen();
    }
    @Override
    public void onDestroy() {
        if (focusManager != null) {
            focusManager.stop();
        }
        cancelSpeakingWatchdog();

        if (controller != null) controller.hide();
        if (speaker != null) speaker.shutdown();
        if (speechManager != null) speechManager.destroy();
        if (engine != null) engine.shutdown();
        stopForeground(true);
        super.onDestroy();
    }
}