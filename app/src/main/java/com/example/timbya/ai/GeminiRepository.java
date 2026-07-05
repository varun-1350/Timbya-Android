package com.example.timbya.ai;

import android.util.Log;

import com.example.timbya.BuildConfig;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import okhttp3.OkHttpClient;
import java.util.concurrent.TimeUnit;

public class GeminiRepository {

    private final GeminiApi api;



    public GeminiRepository() {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)   // Gemini flash can take up to 25s
                .writeTimeout(10, TimeUnit.SECONDS);

        if (BuildConfig.DEBUG) {
            // Deliberately NOT using HttpLoggingInterceptor's BASIC/HEADERS
            // level here: generateContent()'s API key travels as a ?key=
            // query parameter, and HttpLoggingInterceptor logs the full
            // request URL verbatim, which would print the key to Logcat on
            // every request — in debug builds too. This logs method, path
            // (no query string), response code, and timing only, and is
            // compiled out of release builds entirely via BuildConfig.DEBUG.
            clientBuilder.addInterceptor(chain -> {
                okhttp3.Request request = chain.request();
                long startNs = System.nanoTime();
                okhttp3.Response response = chain.proceed(request);
                long tookMs = (System.nanoTime() - startNs) / 1_000_000;
                Log.d("TIMBYA_NETWORK", request.method() + " " + request.url().encodedPath()
                        + " -> " + response.code() + " (" + tookMs + "ms)");
                return response;
            });
        }

        OkHttpClient client = clientBuilder.build();

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