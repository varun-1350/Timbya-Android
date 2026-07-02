package com.example.timbya.actions;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ActionExecutor {

    private final Context context;
    private final ContactResolver contactResolver;

    private static final Pattern WHATSAPP_PATTERN = Pattern.compile(
            "message\\s+(.+?)\\s+to\\s+(.+?)\\s+on\\s+whatsapp", Pattern.CASE_INSENSITIVE);

    public ActionExecutor(Context context) {
        this.context = context;
        this.contactResolver = new ContactResolver(context);
    }

    public ActionResult execute(String originalCommand) {

        // WhatsApp: match against the ORIGINAL text so the message keeps its casing.
        Matcher m = WHATSAPP_PATTERN.matcher(originalCommand.trim());
        if (m.find()) {
            String message = m.group(1).trim();
            String contactName = m.group(2).trim();
            return sendWhatsAppMessage(contactName, message);
        }

        String command = originalCommand.toLowerCase().trim();

        if (command.startsWith("open ")) {
            String appName = command.substring(5).trim();

            if (appName.contains("music")) {
                ActionResult musicResult = openAnyMusicApp();
                if (musicResult != null) return musicResult;
            }

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

    private ActionResult sendWhatsAppMessage(String contactName, String message) {
        String phoneNumber = contactResolver.findPhoneNumber(contactName);

        if (phoneNumber == null) {
            return new ActionResult(true,
                    "I couldn't find " + contactName + " in your contacts.");
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://api.whatsapp.com/send?phone="
                    + phoneNumber + "&text=" + Uri.encode(message)));
            intent.setPackage("com.whatsapp");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return new ActionResult(true,
                    "Opened WhatsApp with your message to " + contactName + " ready — just tap send.");
        } catch (Exception e) {
            return new ActionResult(true, "WhatsApp doesn't seem to be installed.");
        }
    }

    private ActionResult openAnyMusicApp() {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_APP_MUSIC);

        List<ResolveInfo> musicApps = pm.queryIntentActivities(intent, 0);
        if (musicApps.isEmpty()) return null;

        ResolveInfo chosen = musicApps.get(0);
        Intent launchIntent = pm.getLaunchIntentForPackage(chosen.activityInfo.packageName);
        if (launchIntent == null) return null;

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(launchIntent);
        return new ActionResult(true, "Opening " + chosen.loadLabel(pm).toString());
    }

    private ActionResult openAppByName(String appName) {
        PackageManager pm = context.getPackageManager();

        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);

        for (ResolveInfo app : apps) {
            String label = app.loadLabel(pm).toString();
            if (label.equalsIgnoreCase(appName)) {
                Intent launchIntent = pm.getLaunchIntentForPackage(app.activityInfo.packageName);
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