package com.example.timbya.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import android.provider.Settings;
import android.text.TextUtils;
import android.os.Handler;
import android.os.Looper;

public class ScreenReadingAccessibilityService extends AccessibilityService {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static volatile FocusEventListener focusEventListener;
    public static void setFocusEventListener(FocusEventListener listener) {
        focusEventListener = listener;
    }

    public interface ScreenReadCallback {
        void onScreenRead(String text);
    }
    public boolean goBack() {
        mainHandler.post(() ->
                performGlobalAction(GLOBAL_ACTION_BACK));
        return true;
    }

    private static volatile ScreenReadingAccessibilityService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;

        AccessibilityServiceInfo info = getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        FocusEventListener listener = focusEventListener;

        if (listener == null || event.getPackageName() == null) {
            return;
        }

        int eventType = event.getEventType();

        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return;
        }

        String packageName = event.getPackageName().toString();
        String className = event.getClassName() == null
                ? ""
                : event.getClassName().toString();

        boolean isShorts = "com.google.android.youtube".equals(packageName)
                && containsShortsPlayer(event.getSource());

        listener.onForegroundContentChanged(packageName, className, isShorts);
    }
    private boolean containsShortsPlayer(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }

        try {
            String viewId = node.getViewIdResourceName();
            String className = node.getClassName() == null
                    ? ""
                    : node.getClassName().toString();

            String signal = (viewId == null ? "" : viewId)
                    + " "
                    + className;

            signal = signal.toLowerCase(java.util.Locale.ROOT);

            if (signal.contains("shorts") || signal.contains("reel")) {
                return true;
            }

            for (int index = 0; index < node.getChildCount(); index++) {
                AccessibilityNodeInfo child = node.getChild(index);

                if (child != null) {
                    try {
                        if (containsShortsPlayer(child)) {
                            return true;
                        }
                    } finally {
                        child.recycle();
                    }
                }
            }
        } catch (Exception ignored) {
            // YouTube can replace its view hierarchy during navigation.
        }

        return false;
    }

    @Override
    public void onInterrupt() {
        // No continuous speech or screen monitoring exists in this service.
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    public static boolean isEnabled(Context context) {
        ComponentName expected = new ComponentName(
                context,
                ScreenReadingAccessibilityService.class);

        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

        if (enabledServices == null || enabledServices.isEmpty()) {
            return false;
        }

        TextUtils.SimpleStringSplitter splitter =
                new TextUtils.SimpleStringSplitter(':');

        splitter.setString(enabledServices);

        while (splitter.hasNext()) {
            ComponentName enabled = ComponentName.unflattenFromString(
                    splitter.next());

            if (expected.equals(enabled)) {
                return true;
            }
        }

        return false;
    }

    public static ScreenReadingAccessibilityService getInstance() {
        return instance;
    }
    public interface FocusEventListener {
        void onForegroundContentChanged(
                String packageName,
                String className,
                boolean isYouTubeShorts);
    }

    public void readCurrentScreen(ScreenReadCallback callback) {
        AccessibilityNodeInfo root = getRootInActiveWindow();

        if (root == null) {
            callback.onScreenRead("");
            return;
        }

        try {
            Set<String> visibleText = new LinkedHashSet<>();
            collectVisibleText(root, visibleText, new int[]{0});

            StringBuilder result = new StringBuilder();

            for (String item : visibleText) {
                if (result.length() + item.length() + 1 > 4500) {
                    break;
                }

                if (result.length() > 0) {
                    result.append("\n");
                }

                result.append(item);
            }

            callback.onScreenRead(result.toString());
        } finally {
            root.recycle();
        }
    }

    private void collectVisibleText(
            AccessibilityNodeInfo node,
            Set<String> output,
            int[] itemCount) {

        if (node == null || itemCount[0] >= 80) {
            return;
        }

        try {
            if (node.isVisibleToUser() && !node.isPassword()) {
                addText(output, node.getText());
                addText(output, node.getContentDescription());
                itemCount[0] = output.size();
            }

            for (int index = 0; index < node.getChildCount(); index++) {
                AccessibilityNodeInfo child = node.getChild(index);

                if (child != null) {
                    try {
                        collectVisibleText(child, output, itemCount);
                    } finally {
                        child.recycle();
                    }
                }
            }
        } catch (Exception ignored) {
            // A target app may replace its view hierarchy while we read it.
        }
    }

    private void addText(Set<String> output, CharSequence text) {
        if (text == null) {
            return;
        }

        String value = text.toString().trim();

        if (value.length() < 2) {
            return;
        }

        if (value.length() > 220) {
            value = value.substring(0, 220) + "...";
        }

        output.add(value);
    }
}