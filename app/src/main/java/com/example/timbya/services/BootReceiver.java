package com.example.timbya.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import androidx.core.content.ContextCompat;

/** Restarts the overlay after a reboot — but only if the user already granted
 *  overlay + mic before rebooting. Never prompts from a receiver; permission
 *  prompts require an Activity, so if either is missing we simply stay dormant
 *  until the user next taps the launcher icon. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        boolean hasOverlay = Settings.canDrawOverlays(context);
        boolean hasMic = ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.RECORD_AUDIO)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;

        if (hasOverlay && hasMic) {
            Intent serviceIntent = new Intent(context, OverlayService.class);
            serviceIntent.setAction(OverlayService.ACTION_SHOW);
            ContextCompat.startForegroundService(context, serviceIntent);
        }
    }
}