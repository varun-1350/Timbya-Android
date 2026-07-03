package com.example.timbya.ai;

import android.util.Log;

import com.example.timbya.BuildConfig;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import java.util.concurrent.TimeUnit;

public class GeminiRepository {

    private final GeminiApi api;



    public GeminiRepository() {
        HttpLoggingInterceptor logger = new HttpLoggingInterceptor(
                msg -> Log.d("TIMBYA_NETWORK", msg));
        logger.setLevel(HttpLoggingInterceptor.Level.BASIC);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)   // Gemini flash can take up to 25s
                .writeTimeout(10, TimeUnit.SECONDS)
                .addInterceptor(logger)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://generativelanguage.googleapis.com/")
                .client(client)
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