package com.example.timbya.ai;

import com.example.timbya.model.GeminiRequest;
import com.example.timbya.model.GeminiResponse;

import retrofit2.*;

public class GeminiManager {

    private GeminiRepository repository;

    public GeminiManager(){

        repository = new GeminiRepository();

    }

    public void ask(
            String prompt,
            Callback<GeminiResponse> callback){

        GeminiRequest request =
                new GeminiRequest(
                        PromptManager.buildPrompt(prompt));

        repository.getApi()
                .generateContent(
                        repository.getApiKey(),
                        request)
                .enqueue(callback);

    }

}