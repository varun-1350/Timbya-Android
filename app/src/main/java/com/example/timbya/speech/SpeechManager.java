package com.example.timbya.speech;

import android.app.Activity;
import android.content.Intent;
import android.speech.RecognizerIntent;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.util.ArrayList;
import java.util.Locale;

public class SpeechManager {

    private Activity activity;

    private SpeechListener listener;

    private ActivityResultLauncher<Intent> launcher;

    public SpeechManager(Activity activity){

        this.activity=activity;

        launcher=((androidx.activity.ComponentActivity)activity)
                .registerForActivityResult(

                        new ActivityResultContracts.StartActivityForResult(),

                        result->{

                            if(result.getResultCode()!=Activity.RESULT_OK)
                                return;

                            if(result.getData()==null)
                                return;

                            ArrayList<String> speech=
                                    result.getData()
                                            .getStringArrayListExtra(
                                                    RecognizerIntent.EXTRA_RESULTS);

                            if(speech==null || speech.isEmpty()){

                                listener.onSpeechError("Nothing recognized");

                                return;

                            }

                            listener.onSpeechResult(
                                    speech.get(0));

                        });

    }

    public void startListening(SpeechListener listener){

        this.listener=listener;

        Intent intent=
                new Intent(
                        RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault());

        launcher.launch(intent);

    }

}