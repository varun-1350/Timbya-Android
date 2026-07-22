package com.example.timbya.focus;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.example.timbya.actions.ActionResult;
import com.example.timbya.services.ScreenReadingAccessibilityService;

import java.util.Locale;
import java.util.regex.Pattern;

public class FocusManager implements
        ScreenReadingAccessibilityService.FocusEventListener {

    public interface FocusNudgeListener {
        void onNudge(String message);
    }

    private static final long NUDGE_COOLDOWN_MS = 90_000L;

    private static final Pattern FOCUS_ON = Pattern.compile(
            "^(?:turn |switch |enable |start )?(?:focus|focus companion)"
                    + "(?: mode)? on[.!?]*$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern FOCUS_OFF = Pattern.compile(
            "^(?:turn |switch |disable |stop )?(?:focus|focus companion)"
                    + "(?: mode)? off[.!?]*$",
            Pattern.CASE_INSENSITIVE);

    private final Context context;
    private final FocusNudgeListener nudgeListener;

    private boolean active = false;
    private String lastNudgeKey = "";
    private long lastNudgeAt = 0L;

    public FocusManager(Context context, FocusNudgeListener nudgeListener) {
        this.context = context.getApplicationContext();
        this.nudgeListener = nudgeListener;
    }

    public ActionResult handleVoiceCommand(String command) {
        String text = command == null ? "" : command.trim();

        if (FOCUS_ON.matcher(text).matches()) {
            active = true;
            ScreenReadingAccessibilityService.setFocusEventListener(this);

            if (!ScreenReadingAccessibilityService.isEnabled(context)) {
                return new ActionResult(
                        true,
                        "Focus Mode is on. Enable Timbya Accessibility access so I can notice Shorts and games.");
            }

            return new ActionResult(
                    true,
                    "Focus Mode is on. I'll gently check in when you open YouTube Shorts or a game.");
        }

        if (FOCUS_OFF.matcher(text).matches()) {
            active = false;
            lastNudgeKey = "";
            ScreenReadingAccessibilityService.setFocusEventListener(null);

            return new ActionResult(
                    true,
                    "Focus Mode is off. I'll stop checking in.");
        }

        return new ActionResult(false, "");
    }

    @Override
    public void onForegroundContentChanged(
            String packageName,
            String className,
            boolean isYouTubeShorts) {

        if (!active || packageName == null
                || packageName.equals(context.getPackageName())) {
            return;
        }

        if ("com.google.android.youtube".equals(packageName)
                && isYouTubeShorts) {
            nudgeOnce(
                    "youtube-shorts",
                    "You opened YouTube Shorts. You turned Focus Mode on — want to return to what you were doing?");
            return;
        }

        if (isGame(packageName)) {
            nudgeOnce(
                    "game-" + packageName,
                    "That looks like a game. Want to stay with your current focus task?");
        }
    }

    public void stop() {
        active = false;
        ScreenReadingAccessibilityService.setFocusEventListener(null);
    }

    private boolean isGame(String packageName) {
        try {
            ApplicationInfo appInfo = context.getPackageManager()
                    .getApplicationInfo(packageName, 0);

            return appInfo.category == ApplicationInfo.CATEGORY_GAME;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private void nudgeOnce(String key, String message) {
        long now = System.currentTimeMillis();

        if (key.equals(lastNudgeKey)
                && now - lastNudgeAt < NUDGE_COOLDOWN_MS) {
            return;
        }

        lastNudgeKey = key;
        lastNudgeAt = now;

        if (nudgeListener != null) {
            nudgeListener.onNudge(message);
        }
    }
}