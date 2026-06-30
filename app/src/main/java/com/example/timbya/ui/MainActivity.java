package com.example.timbya.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
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
import com.example.timbya.services.OverlayService;
import com.example.timbya.speech.Speaker;

import java.util.ArrayList;
import java.util.Locale;

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
                            startSpeechRecognition();
                        } else {
                            Toast.makeText(
                                    this,
                                    "Microphone permission denied",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    });

    /*
     * Speech Result
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

                        txtSpeech.setText(text);

                        engine.process(text, new TimbyaListener() {

                            @Override
                            public void onReply(String reply) {

                                runOnUiThread(() -> {

                                    txtSpeech.setText(reply);

                                    speaker.say(reply);

                                });

                            }

                            @Override
                            public void onError(String error) {

                                runOnUiThread(() ->
                                        txtSpeech.setText(error));

                            }

                        });

                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();

        initializeEngine();

        btnSpeak.setOnClickListener(v -> checkPermissionAndSpeak());
        btnSpeak.setOnClickListener(v->{

            startService(
                    new Intent(
                            this,
                            OverlayService.class
                    ));

        });

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

        engine = new TimbyaEngine(
                actionExecutor,
                geminiManager
        );

    }

    /*
     * Check Microphone Permission
     */
    private void checkPermissionAndSpeak() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {

            startSpeechRecognition();

        } else {

            permissionLauncher.launch(
                    Manifest.permission.RECORD_AUDIO);

        }

    }

    /*
     * Start Speech Recognition
     */
    private void startSpeechRecognition() {

        Intent intent =
                new Intent(
                        RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault());

        intent.putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Speak to Timbya");

        speechLauncher.launch(intent);

    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        speaker.shutdown();

    }

}