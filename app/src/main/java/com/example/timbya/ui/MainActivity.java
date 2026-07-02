package com.example.timbya.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.timbya.R;
import com.example.timbya.actions.ActionExecutor;
import com.example.timbya.ai.GeminiManager;
import com.example.timbya.core.TimbyaEngine;
import com.example.timbya.core.TimbyaListener;
import com.example.timbya.memory.MemoryManager;
import com.example.timbya.services.OverlayService;
import com.example.timbya.speech.Speaker;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Trampoline activity: checks permissions, starts/shows OverlayService,
 * then finishes immediately with no transition animation. Requires
 * android:theme="@style/Theme.Timbya.Launcher" on this activity in the
 * manifest (translucent, windowAnimationStyle=@null) so nothing ever
 * visibly flashes on screen during launch.
 */
public class MainActivity extends AppCompatActivity {

    private TextView txtSpeech;
    private Button btnSpeak;

    private Speaker speaker;
    private TimbyaEngine engine;

    /*
     * Request microphone permission
     */
    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            launchOverlayAndFinish();
                        } else {
                            Toast.makeText(
                                    this,
                                    "Microphone permission denied",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    });

    /*
     * Result of asking the user to enable "Display over other apps".
     * This screen doesn't return a reliable result code, so we re-check
     * Settings.canDrawOverlays() when the user comes back to MainActivity.
     */
    private final ActivityResultLauncher<Intent> overlayPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (Settings.canDrawOverlays(this)) {
                            checkAudioPermissionAndStart();
                        } else {
                            Toast.makeText(
                                    this,
                                    "Timbya needs the \"display over other apps\" permission to show its overlay",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    });

    /*
     * Speech Result (legacy one-shot dictation path, kept for compatibility —
     * not part of the main overlay launch flow)
     */
    private final ActivityResultLauncher<Intent> speechLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {

                        if (result.getResultCode() != RESULT_OK ||
                                result.getData() == null)
                            return;

                        ArrayList<String> speech =
                                result.getData().getStringArrayListExtra(
                                        RecognizerIntent.EXTRA_RESULTS);

                        if (speech == null || speech.isEmpty())
                            return;

                        String text = speech.get(0);

                        if (txtSpeech != null) txtSpeech.setText(text);

                        engine.process(text, new TimbyaListener() {

                            @Override
                            public void onReply(String reply) {
                                runOnUiThread(() -> {
                                    if (txtSpeech != null) txtSpeech.setText(reply);
                                    speaker.say(reply);
                                });
                            }

                            @Override
                            public void onError(String error) {
                                runOnUiThread(() -> {
                                    if (txtSpeech != null) txtSpeech.setText(error);
                                });
                            }

                        });

                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        initializeEngine();

        // Seamless launch: go straight for the overlay without waiting for
        // a button tap. The button stays wired below as a manual fallback
        // in case this activity is ever reopened directly.
        proceedToOverlay();

        btnSpeak.setOnClickListener(v -> proceedToOverlay());
    }

    private void proceedToOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            overlayPermissionLauncher.launch(intent);
            return;
        }
        checkAudioPermissionAndStart();
    }

    /*
     * Find Views
     */
    private void initializeViews() {
        txtSpeech = findViewById(R.id.txtSpeech);
        btnSpeak = findViewById(R.id.btnSpeak);
    }

    /*
     * Initialize Timbya
     */
    private void initializeEngine() {
        speaker = new Speaker(this);

        GeminiManager geminiManager = new GeminiManager();
        ActionExecutor actionExecutor = new ActionExecutor(this);
        MemoryManager memoryManager = new MemoryManager(this);

        engine = new TimbyaEngine(
                actionExecutor,
                geminiManager,
                memoryManager
        );
    }

    /*
     * Gate on RECORD_AUDIO, then finally start/show the overlay service.
     * Only reached once overlay permission is already confirmed.
     */
    private void checkAudioPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {

            launchOverlayAndFinish();

        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    /**
     * Starts (or re-shows, if already running) the overlay service, then
     * closes this activity with no transition — MainActivity is a
     * trampoline, never a screen the user is meant to see.
     */
    private void launchOverlayAndFinish() {
        Intent intent = new Intent(this, OverlayService.class);
        intent.setAction(OverlayService.ACTION_SHOW);
        startService(intent);

        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speaker != null) speaker.shutdown();
    }
}