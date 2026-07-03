package com.example.timbya.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.timbya.services.OverlayService;

/**
 * Pure permission trampoline.
 * Does exactly three things: checks overlay permission, checks mic permission,
 * starts OverlayService, then calls finish(). Nothing else.
 * All engine/speaker/memory resources live in OverlayService only.
 * android:noHistory="true" and android:excludeFromRecents="true" in the
 * manifest ensure finish() here does NOT trigger onTaskRemoved() on the service.
 */
public class MainActivity extends AppCompatActivity {

    private final ActivityResultLauncher<String> micPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            launchOverlayAndFinish();
                        } else {
                            Toast.makeText(this,
                                    "Microphone permission is required for Timbya",
                                    Toast.LENGTH_LONG).show();
                        }
                    });

    private final ActivityResultLauncher<Intent> overlayPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (Settings.canDrawOverlays(this)) {
                            checkMicAndLaunch();
                        } else {
                            Toast.makeText(this,
                                    "\"Display over other apps\" permission is required for Timbya",
                                    Toast.LENGTH_LONG).show();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // No layout, no views, no engine. This activity's only job is
        // permission gating. It finishes in under 100ms on happy path.
        checkOverlayAndLaunch();
    }

    private void checkOverlayAndLaunch() {
        if (!Settings.canDrawOverlays(this)) {
            overlayPermissionLauncher.launch(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } else {
            checkMicAndLaunch();
        }
    }

    private void checkMicAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            launchOverlayAndFinish();
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void launchOverlayAndFinish() {
        startService(new Intent(this, OverlayService.class));
        finish();
        overridePendingTransition(0, 0);
    }
}