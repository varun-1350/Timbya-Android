package com.example.timbya.utils;

public class Constants {

    public static final String GEMINI_BASE_URL =
            "https://generativelanguage.googleapis.com/";

    public static final String MODEL =
            "gemini-2.5-flash";

    public static final String PROMPT =
            "Speak to Timbya";

    // ---- Centralized UI text visibility ----
    // status line master switch (Listening / Processing / Speaking labels)
    public static final boolean SHOW_STATUS_TEXT = true;
    // "Listening..." label specifically (mic icon color already shows this state)
    public static final boolean SHOW_LISTENING_LABEL = true;
    // internal AI-processing state label ("Processing...")
    public static final boolean SHOW_AI_STATE = true;
    // live partial transcript preview in the reply box while listening
    public static final boolean SHOW_DEBUG_TEXT = true;
}