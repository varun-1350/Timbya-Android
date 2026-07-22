package com.example.timbya.utils;

public class Constants {

    public static final String PREFS_NAME = "timbya_settings";

    public static final String SHOW_STATUS_TEXT = "show_status_text";
    public static final String SHOW_LISTENING_LABEL = "show_listening_label";
    public static final String SHOW_AI_STATE = "show_ai_state";
    public static final String SHOW_DEBUG_TEXT = "show_debug_text";
    public static final String START_MINIMIZED = "start_minimized";

    private static boolean getBoolean(android.content.Context context,
                                      String key,
                                      boolean defaultValue) {
        return context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .getBoolean(key, defaultValue);
    }

    public static boolean showStatusText(android.content.Context context) {
        return getBoolean(context, SHOW_STATUS_TEXT, true);
    }

    public static boolean showListeningLabel(android.content.Context context) {
        return getBoolean(context, SHOW_LISTENING_LABEL, true);
    }

    public static boolean showAiState(android.content.Context context) {
        return getBoolean(context, SHOW_AI_STATE, true);
    }

    public static boolean showDebugText(android.content.Context context) {
        return getBoolean(context, SHOW_DEBUG_TEXT, false);
    }

    public static boolean startMinimized(android.content.Context context) {
        return getBoolean(context, START_MINIMIZED, true);
    }
}