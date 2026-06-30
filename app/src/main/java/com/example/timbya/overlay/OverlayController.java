package com.example.timbya.overlay;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import com.example.timbya.R;

public class OverlayController {

    private final Context context;

    private final WindowManager windowManager;

    private View overlayView;

    public OverlayController(Context context){

        this.context=context;

        windowManager=(WindowManager)
                context.getSystemService(Context.WINDOW_SERVICE);

    }

    public void show(){

        if(overlayView!=null)
            return;

        overlayView=
                LayoutInflater.from(context)
                        .inflate(
                                R.layout.overlay_layout,
                                null);

        WindowManager.LayoutParams params=
                new WindowManager.LayoutParams(

                        300,

                        WindowManager.LayoutParams.WRAP_CONTENT,

                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,

                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,

                        PixelFormat.TRANSLUCENT);

        params.gravity=
                Gravity.END|Gravity.CENTER_VERTICAL;

        windowManager.addView(
                overlayView,
                params);

    }

    public void hide(){

        if(overlayView==null)
            return;

        windowManager.removeView(overlayView);

        overlayView=null;

    }

}