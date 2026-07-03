package com.example.timbya.core;

import android.util.Log;

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

    public void process(String command, TimbyaListener listener) {

        ActionResult result = actionExecutor.execute(command);

        if (result.isHandled()) {
            listener.onReply(result.getReply());
            return;
        }

        memoryManager.getRelevantMemories(command, memoryContext -> {

            String prompt = PromptManager.buildPrompt(command, memoryContext);

            geminiManager.askRaw(prompt, new Callback<GeminiResponse>() {

                @Override
                public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
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
                        memoryManager.extractAndStore(command, reply);

                    } else {
                        listener.onError(describeApiError(response));
                    }

                }

                @Override
                public void onFailure(Call<GeminiResponse> call, Throwable t) {
                    if (t instanceof java.net.UnknownHostException
                            || t instanceof java.net.SocketTimeoutException
                            || t instanceof java.io.IOException) {
                        listener.onError("Looks like you're offline. Check your internet connection and try again.");
                    } else {
                        listener.onError("Something went wrong reaching the AI. Please try again.");
                    }
                }

                private String describeApiError(Response<GeminiResponse> response) {
                    int code = response.code();
                    String errorBody = "";
                    try {
                        if (response.errorBody() != null) errorBody = response.errorBody().string();
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