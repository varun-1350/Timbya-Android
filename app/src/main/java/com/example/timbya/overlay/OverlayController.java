package com.example.timbya.overlay;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import com.example.timbya.R;

public class OverlayController {

    private final Context context;
    private final WindowManager windowManager;
    private final OverlayCallbacks callbacks;

    private View overlayView;

    private TextView status;
    private TextView reply;
    private ImageButton mic;

    public OverlayController(Context context, OverlayCallbacks callbacks) {
        this.context = context;
        this.callbacks = callbacks;
        this.windowManager =
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void show() {

        if (overlayView != null)
            return;

        overlayView = LayoutInflater.from(context)
                .inflate(R.layout.overlay_layout, null);

        // Initialize views AFTER inflating the layout
        status = overlayView.findViewById(R.id.status);
        reply = overlayView.findViewById(R.id.reply);
        mic = overlayView.findViewById(R.id.mic);

        mic.setOnClickListener(v -> {
            if (callbacks != null) {
                callbacks.onMicPressed();
            }
        });

        WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                );

        params.gravity = Gravity.END | Gravity.CENTER_VERTICAL;

        windowManager.addView(overlayView, params);
    }

    public void setStatus(String text) {
        if (status != null) {
            status.setText(text);
        }
    }

    public void setReply(String text) {
        if (reply != null) {
            reply.setText(text);
        }
    }

    public void hide() {

        if (overlayView == null)
            return;

        windowManager.removeView(overlayView);

        overlayView = null;
        status = null;
        reply = null;
        mic = null;
    }
}