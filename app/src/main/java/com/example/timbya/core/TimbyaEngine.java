package com.example.timbya.core;

import com.example.timbya.actions.ActionExecutor;
import com.example.timbya.actions.ActionResult;
import com.example.timbya.ai.GeminiManager;
import com.example.timbya.model.GeminiResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TimbyaEngine {

    private final ActionExecutor actionExecutor;
    private final GeminiManager geminiManager;

    public TimbyaEngine(ActionExecutor executor,
                        GeminiManager geminiManager) {

        this.actionExecutor = executor;
        this.geminiManager = geminiManager;

    }

    public void process(String command,
                        TimbyaListener listener) {

        ActionResult result = actionExecutor.execute(command);

        if (result.isHandled()) {

            listener.onReply(result.getReply());
            return;

        }

        geminiManager.ask(command, new Callback<GeminiResponse>() {

            @Override
            public void onResponse(Call<GeminiResponse> call,
                                   Response<GeminiResponse> response) {

                if(response.isSuccessful()
                        && response.body()!=null
                        && response.body().candidates!=null
                        && !response.body().candidates.isEmpty()){

                    String reply = response.body()
                            .candidates
                            .get(0)
                            .content
                            .parts
                            .get(0)
                            .text;

                    listener.onReply(reply);

                }else{

                    listener.onError("No response from Gemini.");

                }

            }

            @Override
            public void onFailure(Call<GeminiResponse> call,
                                  Throwable t) {

                listener.onError(t.getMessage());

            }

        });

    }

}