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
    FileOpener fileOpener;
    private List<FileOpener.FileMatch> pendingFileMatches;


    private static final Pattern WHATSAPP_PATTERN = Pattern.compile(
            "message\\s+(.+?)\\s+to\\s+(.+?)\\s+on\\s+whatsapp", Pattern.CASE_INSENSITIVE);
    private static final Pattern YOUTUBE_SUFFIX_PATTERN = Pattern.compile(
            "(?:search|play)\\s+(.+?)\\s+(?:on|in)\\s+youtube", Pattern.CASE_INSENSITIVE);

    private static final Pattern YOUTUBE_PREFIX_PATTERN = Pattern.compile(
            "open youtube and search\\s+(.+)", Pattern.CASE_INSENSITIVE);

    public ActionExecutor(Context context) {
        this.context = context.getApplicationContext();
        this.contactResolver = new ContactResolver(this.context);
        this.fileOpener = new FileOpener(this.context);
    }
    private ActionResult searchYouTube(String query) {
        String url = "https://www.youtube.com/results?search_query=" + Uri.encode(query);

        try {
            Intent appIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            appIntent.setPackage("com.google.android.youtube");
            appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(appIntent);
            return new ActionResult(true, "Searching YouTube for " + query);
        } catch (Exception e) {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(browserIntent);
                return new ActionResult(true, "YouTube app isn't installed, searching in your browser instead");
            } catch (Exception e2) {
                return new ActionResult(true, "I couldn't open YouTube.");
            }
        }
    }
    /** All our patterns' group 1/2 are mandatory (no '?' quantifier), so a match
     *  guarantees a non-null group — this just makes that explicit for the analyzer
     *  and protects against a future pattern change that adds an optional group. */
    private static String safeGroup(Matcher m, int group) {
        String value = m.group(group);
        return value == null ? "" : value.trim();
    }

    public ActionResult execute(String originalCommand) {
        if (pendingFileMatches != null) {
            ActionResult resolved = resolvePendingFileChoice(originalCommand);
            if (resolved != null) return resolved;
        }
        // WhatsApp: match against the ORIGINAL text so the message keeps its casing.
        Matcher m = WHATSAPP_PATTERN.matcher(originalCommand.trim());
        if (m.find()) {
            String message = safeGroup(m, 1);
            String contactName = safeGroup(m, 2);
            return sendWhatsAppMessage(contactName, message);
        }
        Matcher yPrefix = YOUTUBE_PREFIX_PATTERN.matcher(originalCommand.trim());
        if (yPrefix.find()) {
            return searchYouTube(safeGroup(yPrefix, 1));
        }

        Matcher ySuffix = YOUTUBE_SUFFIX_PATTERN.matcher(originalCommand.trim());
        if (ySuffix.find()) {
            return searchYouTube(safeGroup(ySuffix, 1));
        }

        String command = originalCommand.toLowerCase().trim();

        if (command.startsWith("open ")) {
            String target = command.substring(5).trim().replace(" from file manager", "").trim();

            if (looksLikeFileName(target)) {
                return openFileByName(target);
            }

            if (target.contains("music")) {
                ActionResult musicResult = openAnyMusicApp();
                if (musicResult != null) return musicResult;
            }

            return openAppByName(target);
        }

        if (command.contains("settings")) {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return new ActionResult(true, "Opening Settings");
        }

        return new ActionResult(false, "");
    }
    private boolean looksLikeFileName(String text) {
        return text.matches(".*\\.[a-zA-Z0-9]{2,5}$");
    }

    private ActionResult openFileByName(String fileName) {
        List<FileOpener.FileMatch> matches = fileOpener.search(fileName);

        if (matches.isEmpty()) {
            return new ActionResult(true, "I couldn't find a file named " + fileName + " on this device.");
        }

        if (matches.size() == 1) {
            fileOpener.open(matches.get(0));
            return new ActionResult(true, "Opening " + matches.get(0).displayName);
        }

        pendingFileMatches = matches;
        StringBuilder sb = new StringBuilder("I found " + matches.size() + " files named " + fileName + ". Say a number: ");
        for (int i = 0; i < matches.size(); i++) {
            sb.append(i + 1).append(") ").append(matches.get(i).relativePath).append(" ");
        }
        return new ActionResult(true, sb.toString().trim());
    }

    private ActionResult resolvePendingFileChoice(String originalCommand) {
        Integer choice = parseOrdinal(originalCommand.trim().toLowerCase());

        if (choice == null || choice < 1 || choice > pendingFileMatches.size()) {
            pendingFileMatches = null; // don't get stuck waiting forever on an unrelated reply
            return null;
        }

        FileOpener.FileMatch chosen = pendingFileMatches.get(choice - 1);
        pendingFileMatches = null;
        fileOpener.open(chosen);
        return new ActionResult(true, "Opening " + chosen.displayName);
    }

    private Integer parseOrdinal(String text) {
        if (text.matches("\\d+")) return Integer.parseInt(text);
        switch (text) {
            case "one": case "first": return 1;
            case "two": case "second": return 2;
            case "three": case "third": return 3;
            case "four": case "fourth": return 4;
            case "five": case "fifth": return 5;
            default: return null;
        }
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
        return new ActionResult(true, "Opening " + chosen.loadLabel(pm));
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