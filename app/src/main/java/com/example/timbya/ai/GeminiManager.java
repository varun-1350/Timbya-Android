package com.example.timbya.ai;

import com.example.timbya.model.GeminiRequest;
import com.example.timbya.model.GeminiResponse;

import retrofit2.*;

public class GeminiManager {

    public GeminiRepository repository;

    public GeminiManager(){

        repository = new GeminiRepository();

    }

    /** Send an already-built prompt (already includes memory context). */
    public void askRaw(
            String fullPrompt,
            Callback<GeminiResponse> callback){

        GeminiRequest request = new GeminiRequest(fullPrompt);

        repository.getApi()
                .generateContent(
                        repository.getApiKey(),
                        request)
                .enqueue(callback);

    }

}