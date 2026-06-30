package com.example.timbya.services;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.provider.Settings;

import androidx.annotation.Nullable;

import com.example.timbya.overlay.OverlayCallbacks;
import com.example.timbya.overlay.OverlayController;

public class OverlayService extends Service {

    private OverlayController controller;

    @Override
    public void onCreate() {

        super.onCreate();
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } else {
            startService(new Intent(this, OverlayService.class));
        }
        controller = new OverlayController(this, new OverlayCallbacks() {
            @Override
            public void onMicPressed() {

                // Stage 8 will start speech recognition here

            }
        });

        controller.show();

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {

        return null;

    }

    @Override
    public void onDestroy() {

        controller.hide();

        super.onDestroy();

    }

}