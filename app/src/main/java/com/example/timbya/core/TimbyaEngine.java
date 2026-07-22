package com.example.timbya.core;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.timbya.actions.ActionExecutor;
import com.example.timbya.actions.ActionResult;
import com.example.timbya.ai.GeminiManager;
import com.example.timbya.ai.PromptManager;
import com.example.timbya.memory.MemoryManager;
import com.example.timbya.model.GeminiResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TimbyaEngine {

    private final ActionExecutor actionExecutor;
    private final GeminiManager geminiManager;
    private final MemoryManager memoryManager;
    private static final long SCREEN_CONTEXT_TTL_MS = 5 * 60 * 1000L;
    private static final int SCREEN_CONTEXT_TURNS = 3;

    private String activeScreenContext = "";
    private long screenContextExpiresAt = 0L;
    private int screenContextTurnsRemaining = 0;

    public TimbyaEngine(ActionExecutor executor,
                        GeminiManager geminiManager,
                        MemoryManager memoryManager) {
        this.actionExecutor = executor;
        this.geminiManager = geminiManager;
        this.memoryManager = memoryManager;
    }
    private String extractReply(GeminiResponse body) {
        try {
            if (body == null
                    || body.candidates == null
                    || body.candidates.isEmpty()) return null;

            GeminiResponse.Candidate candidate = body.candidates.get(0);
            if (candidate == null
                    || candidate.content == null
                    || candidate.content.parts == null
                    || candidate.content.parts.isEmpty()) return null;

            GeminiResponse.Part part = candidate.content.parts.get(0);
            return (part == null) ? null : part.text;
        } catch (Exception e) {
            Log.e("TIMBYA_AI", "Failed to extract reply from Gemini response", e);
            return null;
        }
    }

    public void summarizeScreen(String screenText, TimbyaListener listener) {
        String trimmedScreenText = screenText == null ? "" : screenText.trim();

        if (trimmedScreenText.isEmpty()) {
            listener.onError("I couldn't find readable text on this screen.");
            return;
        }

        String prompt = PromptManager.buildScreenSummaryPrompt(trimmedScreenText);

        geminiManager.askRaw(prompt, new Callback<>() {
            @Override
            public void onResponse(
                    @NonNull Call<GeminiResponse> call,
                    @NonNull Response<GeminiResponse> response) {

                if (!response.isSuccessful() || response.body() == null) {
                    listener.onError("I couldn't summarize this screen right now.");
                    return;
                }

                String summary = extractReply(response.body());

                if (summary == null || summary.trim().isEmpty()) {
                    listener.onError("I couldn't create a summary for this screen.");
                    return;
                }

                synchronized (TimbyaEngine.this) {
                    activeScreenContext = trimmedScreenText;
                    screenContextExpiresAt =
                            System.currentTimeMillis() + SCREEN_CONTEXT_TTL_MS;
                    screenContextTurnsRemaining = SCREEN_CONTEXT_TURNS;
                }

                listener.onReply(summary);
            }

            @Override
            public void onFailure(
                    @NonNull Call<GeminiResponse> call,
                    @NonNull Throwable error) {
                listener.onError("I couldn't summarize this screen right now.");
            }
        });
    }

    private synchronized String consumeScreenContext() {
        if (screenContextTurnsRemaining <= 0
                || System.currentTimeMillis() > screenContextExpiresAt) {
            activeScreenContext = "";
            screenContextTurnsRemaining = 0;
            return "";
        }

        screenContextTurnsRemaining--;

        String context = activeScreenContext;

        if (screenContextTurnsRemaining == 0) {
            activeScreenContext = "";
        }

        return context;
    }

    private final java.util.concurrent.ExecutorService actionExecutorPool =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    public void shutdown() {
        actionExecutorPool.shutdownNow();
    }
    private final android.os.Handler mainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    public void process(String command, TimbyaListener listener) {
        memoryManager.handleMemoryCommand(command, memoryReply -> {
            if (memoryReply != null) {
                listener.onReply(memoryReply);
                return;
            }

            actionExecutorPool.execute(() -> {
                ActionResult result = actionExecutor.execute(command);

                mainHandler.post(() -> {
                    if (result.isHandled()) {
                        listener.onReply(result.getReply());
                        return;
                    }

                    continueWithGemini(command, listener);
                });
            });
        });
    }

    private static final int MAX_TURNS = 4; // last 4 exchanges, ~keeps prompt small
    private final java.util.ArrayDeque<String> recentTurns = new java.util.ArrayDeque<>();

    private void recordTurn(String user, String reply) {
        recentTurns.addLast("User: " + user + "\nTimbya: " + reply);
        while (recentTurns.size() > MAX_TURNS) recentTurns.pollFirst();
    }

    private String recentTurnsText() {
        return String.join("\n", recentTurns);
    }
    private void continueWithGemini(String command, TimbyaListener listener) {
        memoryManager.getRelevantMemories(command, memoryContext -> {

            String prompt = PromptManager.buildPrompt(
                    command,
                    memoryContext,
                    recentTurnsText(),
                    consumeScreenContext());

            geminiManager.askRaw(prompt, new Callback<>() {

                @Override
                public void onResponse(@NonNull Call<GeminiResponse> call, @NonNull Response<GeminiResponse> response) {
                    if (response.isSuccessful() && response.body() != null
                            && response.body().candidates != null
                            && !response.body().candidates.isEmpty()) {

                        GeminiResponse body = response.body();
                        String reply = extractReply(body);
                        if (reply == null || reply.trim().isEmpty()) {
                            listener.onError("Gemini returned an empty response. This can happen when the message was safety-filtered.");
                            return;
                        }
                        listener.onReply(reply);
                        recordTurn(command, reply);
                        memoryManager.extractAndStore(command);

                    } else {
                        listener.onError(describeApiError(response));
                    }

                }


                @Override
                public void onFailure(@NonNull Call<GeminiResponse> call, @NonNull Throwable t) {
                    if (t instanceof java.io.IOException) {
                        listener.onError("Looks like you're offline. Check your internet connection and try again.");
                    } else {
                        listener.onError("Something went wrong reaching the AI. Please try again.");
                    }
                }

                private String describeApiError(Response<GeminiResponse> response) {
                    int code = response.code();
                    String errorBody = "";
                    try (okhttp3.ResponseBody body = response.errorBody()) {
                        if (body != null) errorBody = body.string();
                    } catch (Exception ignored) { }

                    String lower = errorBody.toLowerCase();

                    if (code == 429 || lower.contains("resource_exhausted") || lower.contains("quota")) {
                        return "I've reached my Gemini usage limit for now. Please try again later, or switch to another available AI model.";
                    }
                    if (code == 401 || code == 403 || lower.contains("api_key_invalid") || lower.contains("permission_denied")) {
                        return "There's a problem with the Gemini API key — it may be missing or invalid.";
                    }
                    if (code >= 500) {
                        return "Gemini's servers are having trouble right now. Please try again in a moment.";
                    }
                    return "I hit an error talking to Gemini. Please try again.";
                }
            });
        });
    }
}