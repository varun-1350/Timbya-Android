package com.example.timbya.ai;

import com.example.timbya.BuildConfig;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class GeminiRepository {

    private final GeminiApi api;

    public GeminiRepository() {

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://generativelanguage.googleapis.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        api = retrofit.create(GeminiApi.class);
    }

    public GeminiApi getApi() {
        return api;
    }

    public String getApiKey() {
        return BuildConfig.GEMINI_API_KEY;
    }
}