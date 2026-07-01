package com.example.timbya.overlay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
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
    private WindowManager.LayoutParams params;

    private View panelContainer;
    private ImageButton bubble;

    private TextView status;
    private TextView reply;
    private ImageButton mic;
    private ImageButton btnShrink;
    private ImageButton btnClose;
    private ImageButton btnPowerOff;

    private boolean shrunk = false;

    public OverlayController(Context context, OverlayCallbacks callbacks) {
        this.context = context;
        this.callbacks = callbacks;
        this.windowManager =
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void show() {

        if (overlayView != null) return;

        overlayView = LayoutInflater.from(context)
                .inflate(R.layout.overlay_layout, null);

        panelContainer = overlayView.findViewById(R.id.panelContainer);
        bubble = overlayView.findViewById(R.id.bubble);
        status = overlayView.findViewById(R.id.status);
        reply = overlayView.findViewById(R.id.reply);
        mic = overlayView.findViewById(R.id.mic);
        btnShrink = overlayView.findViewById(R.id.btnShrink);
        btnClose = overlayView.findViewById(R.id.btnClose);
        btnPowerOff = overlayView.findViewById(R.id.btnPowerOff);

        mic.setOnClickListener(v -> { if (callbacks != null) callbacks.onMicToggle(); });
        btnShrink.setOnClickListener(v -> { if (callbacks != null) callbacks.onShrinkToggle(); });
        bubble.setOnClickListener(v -> { if (callbacks != null) callbacks.onShrinkToggle(); });
        btnClose.setOnClickListener(v -> { if (callbacks != null) callbacks.onClose(); });
        btnPowerOff.setOnClickListener(v -> { if (callbacks != null) callbacks.onPowerOff(); });

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.END | Gravity.CENTER_VERTICAL;

        windowManager.addView(overlayView, params);
    }

    public void setStatus(String text) { if (status != null) status.setText(text); }

    public void setReply(String text) { if (reply != null) reply.setText(text); }

    public void setMicActive(boolean active) {
        int color = active ? Color.parseColor("#4DE5FF") : Color.parseColor("#B0B0B0");
        if (mic != null) mic.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        if (bubble != null) bubble.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    public void setShrunk(boolean shrunk) {
        this.shrunk = shrunk;
        if (panelContainer == null || bubble == null) return;
        panelContainer.setVisibility(shrunk ? View.GONE : View.VISIBLE);
        bubble.setVisibility(shrunk ? View.VISIBLE : View.GONE);
        if (overlayView != null && params != null) {
            windowManager.updateViewLayout(overlayView, params);
        }
    }

    public boolean isShrunk() { return shrunk; }

    public void hide() {
        if (overlayView == null) return;
        windowManager.removeView(overlayView);
        overlayView = null; panelContainer = null; bubble = null;
        status = null; reply = null; mic = null;
        btnShrink = null; btnClose = null; btnPowerOff = null; params = null;
    }
}