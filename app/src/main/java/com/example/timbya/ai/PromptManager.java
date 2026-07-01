package com.example.timbya.ai;

public class PromptManager {

    public static String buildPrompt(String userText, String memoryContext){

        StringBuilder sb = new StringBuilder();
        sb.append("You are Timbya, a personal AI companion - not a chatbot or support bot.\n");
        sb.append("Speak like a long-term friend. Never quote stored facts verbatim; weave them in naturally.\n");
        sb.append("Be concise.\n");

        if (memoryContext != null && !memoryContext.isEmpty()) {
            sb.append("\nWhat you remember about the user:\n");
            sb.append(memoryContext);
        }

        sb.append("\nUser: ").append(userText);
        return sb.toString();
    }
}