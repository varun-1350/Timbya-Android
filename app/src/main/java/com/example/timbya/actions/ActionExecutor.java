package com.example.timbya.actions;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;

import java.util.List;

public class ActionExecutor {

    private final Context context;

    public ActionExecutor(Context context) {
        this.context = context;
    }

    public ActionResult execute(String command) {

        command = command.toLowerCase().trim();

        if (command.startsWith("open ")) {

            String appName = command.substring(5).trim();

            return openAppByName(appName);
        }

        if (command.contains("settings")) {

            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            return new ActionResult(true, "Opening Settings");
        }

        return new ActionResult(false, "");
    }

    private ActionResult openAppByName(String appName) {

        PackageManager pm = context.getPackageManager();

        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);

        for (ResolveInfo app : apps) {

            String label = app.loadLabel(pm).toString();

            // Print every installed app
            android.util.Log.d("TIMBYA_APPS",
                    "Label: " + label +
                            " | Package: " + app.activityInfo.packageName);

            if (label.equalsIgnoreCase(appName)) {

                Intent launchIntent = pm.getLaunchIntentForPackage(
                        app.activityInfo.packageName);

                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launchIntent);
                    return new ActionResult(true, "Opening " + label);
                }
            }
        }

        return new ActionResult(true, appName + " not found");
    }
}