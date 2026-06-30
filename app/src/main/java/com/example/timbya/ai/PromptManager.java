package com.example.timbya.ai;

public class PromptManager {

    public static String buildPrompt(String userText){

        return "You are Timbya.\nYou are an Android AI Assistant.\nBe concise.\nUser: "+ userText;

    }

}