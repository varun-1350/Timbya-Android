package com.example.timbya.overlay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PixelFormat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import com.example.timbya.R;
import com.example.timbya.core.TimbyaState;
import com.example.timbya.utils.Constants;

public class OverlayController {

    private static final int CORNER_MARGIN_DP = 16;
    private static final int TOUCH_SLOP_PX = 16;
    private static final long TRANSITION_MS = 125; // 250ms total (fade out, switch, fade in)

    private final Context context;
    private final WindowManager windowManager;
    private final OverlayCallbacks callbacks;

    private View overlayView;
    private WindowManager.LayoutParams params;

    private View panelContainer;
    private View bubble;

    private AiCoreView aiCore;
    private AiCoreView aiCoreShrunk;

    private TextView status;
    private TextView reply;
    private ImageButton mic;
    private ImageButton btnShrink;
    private ImageButton btnClose;
    private ImageButton btnPowerOff;

    private boolean shrunk = false;

    private int bubbleX = Integer.MIN_VALUE;
    private int bubbleY = Integer.MIN_VALUE;
    private static final String PREFS_NAME  = "timbya_overlay_prefs";
    private static final String PREF_CORNER = "corner";
    private static final int CORNER_TR = 0;
    private static final int CORNER_TL = 1;
    private static final int CORNER_BR = 2;
    private static final int CORNER_BL = 3;

    private float touchStartRawX, touchStartRawY;
    private int paramsStartX, paramsStartY;
    private boolean isDragging = false;

    public OverlayController(Context context, OverlayCallbacks callbacks) {
        this.context = context;
        this.callbacks = callbacks;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void show() {
        if (overlayView != null) return;

        // Pass a throwaway FrameLayout as the inflation root (attachToRoot=false)
        // so the inflater can resolve overlay_layout.xml's root-level
        // layout_width/layout_height/margin attributes correctly, without
        // actually attaching the result to that FrameLayout — it's just
        // there to give LayoutParams something to resolve against, since
        // WindowManager (not a ViewGroup) is the real eventual parent.
        ViewGroup inflationRoot = new FrameLayout(context);
        overlayView = LayoutInflater.from(context).inflate(R.layout.overlay_layout, inflationRoot, false);

        panelContainer = overlayView.findViewById(R.id.panelContainer);
        bubble = overlayView.findViewById(R.id.bubble);
        aiCore = overlayView.findViewById(R.id.aiCore);
        aiCoreShrunk = overlayView.findViewById(R.id.aiCoreShrunk);
        status = overlayView.findViewById(R.id.status);
        reply = overlayView.findViewById(R.id.reply);
        mic = overlayView.findViewById(R.id.mic);
        btnShrink = overlayView.findViewById(R.id.btnShrink);
        btnClose = overlayView.findViewById(R.id.btnClose);
        btnPowerOff = overlayView.findViewById(R.id.btnPowerOff);

        mic.setOnClickListener(v -> { if (callbacks != null) callbacks.onMicToggle(); });
        btnShrink.setOnClickListener(v -> { if (callbacks != null) callbacks.onShrinkToggle(); });
        btnClose.setOnClickListener(v -> { if (callbacks != null) callbacks.onClose(); });
        btnPowerOff.setOnClickListener(v -> { if (callbacks != null) callbacks.onPowerOff(); });

        bubble.setOnTouchListener(this::onBubbleTouch);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.END | Gravity.CENTER_VERTICAL;

        try {
            windowManager.addView(overlayView, params);
        } catch (Exception e) {
            // SYSTEM_ALERT_WINDOW was revoked (or window token is otherwise
            // invalid) — fail safe instead of crashing the service.
            Log.e("TIMBYA_OVERLAY", "Failed to add overlay view, permission likely revoked", e);
            overlayView = null;
            params = null;
        }
    }

    private boolean onBubbleTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchStartRawX = event.getRawX();
                touchStartRawY = event.getRawY();
                paramsStartX = params.x;
                paramsStartY = params.y;
                isDragging = false;
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = event.getRawX() - touchStartRawX;
                float dy = event.getRawY() - touchStartRawY;
                if (!isDragging && (Math.abs(dx) > TOUCH_SLOP_PX || Math.abs(dy) > TOUCH_SLOP_PX)) {
                    isDragging = true;
                }
                if (isDragging) {
                    params.x = paramsStartX + (int) dx;
                    params.y = paramsStartY + (int) dy;
                    windowManager.updateViewLayout(overlayView, params);
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    snapToNearestCorner();
                } else if (callbacks != null) {
                    callbacks.onShrinkToggle();
                }
                return true;

            default:
                return false;
        }
    }

    private void snapToNearestCorner() {
        if (overlayView == null || params == null) return;

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int screenWidth  = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        int margin       = dpToPx(CORNER_MARGIN_DP);
        int bubbleSize   = bubble.getWidth() > 0 ? bubble.getWidth() : dpToPx(40);

        boolean left = (params.x + bubbleSize / 2f) < screenWidth / 2f;
        boolean top  = (params.y + bubbleSize / 2f) < screenHeight / 2f;

        int targetX = left ? margin : screenWidth  - bubbleSize - margin;
        int targetY = top  ? margin : screenHeight - bubbleSize - margin;

        bubbleX = targetX;
        bubbleY = targetY;

        int corner = top ? (left ? CORNER_TL : CORNER_TR) : (left ? CORNER_BL : CORNER_BR);
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(PREF_CORNER, corner).apply();

        animateTo(targetX, targetY);
    }

    private ValueAnimator snapAnimator; // field, so hide() can cancel it

    private void animateTo(int targetX, int targetY) {
        if (snapAnimator != null) snapAnimator.cancel();

        final int startX = params.x;
        final int startY = params.y;

        snapAnimator = ValueAnimator.ofFloat(0f, 1f);
        snapAnimator.setDuration(200);
        snapAnimator.setInterpolator(new DecelerateInterpolator());
        snapAnimator.addUpdateListener(animation -> {
            if (overlayView == null || params == null) {
                animation.cancel();
                return;
            }
            float fraction = (float) animation.getAnimatedValue();
            params.x = (int) (startX + (targetX - startX) * fraction);
            params.y = (int) (startY + (targetY - startY) * fraction);
            windowManager.updateViewLayout(overlayView, params);
        });
        snapAnimator.start();
    }

    private int dpToPx(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    public void setStatus(String text) {
        if (status == null) return;
        if (!Constants.SHOW_STATUS_TEXT || text == null || text.isEmpty()) {
            status.setVisibility(View.GONE);
            return;
        }
        status.setVisibility(View.VISIBLE);
        status.setText(text);
    }

    /** Reply/debug text only ever shows when SHOW_DEBUG_TEXT is on — the
     *  orb + one-word status are the UI otherwise. */
    public void setReply(String text) {
        if (reply == null) return;
        if (!Constants.SHOW_DEBUG_TEXT || text == null || text.isEmpty()) {
            reply.setVisibility(View.GONE);
            return;
        }
        reply.setVisibility(View.VISIBLE);
        reply.setText(text);
    }

    /** Listening/idle color is now shown by the orb itself; kept as a
     *  no-op so OverlayService's existing call site needs no change. */
    @SuppressWarnings("unused")
    public void setMicActive(boolean active) { }

    /** Drives the AI Core's visual state — the one new hook this redesign
     *  needs from OverlayService. */
    public void setAiState(TimbyaState state) {
        if (aiCore != null) aiCore.setState(state);
        if (aiCoreShrunk != null) aiCoreShrunk.setState(state);
    }

    public void setShrunk(boolean shrunk) {
        this.shrunk = shrunk;
        if (overlayView == null || panelContainer == null || bubble == null || params == null) {
            Log.w("TIMBYA_OVERLAY", "setShrunk called before show() — ignoring");
            return;
        }

        View fadeOutView = shrunk ? panelContainer : bubble;
        View fadeInView = shrunk ? bubble : panelContainer;

        fadeOutView.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f)
                .setDuration(TRANSITION_MS)
                .withEndAction(() -> {
                    applyShrunkLayout(shrunk);
                    fadeInView.setAlpha(0f);
                    fadeInView.setScaleX(0.9f);
                    fadeInView.setScaleY(0.9f);
                    fadeInView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(TRANSITION_MS).start();
                })
                .start();
    }

    /** Unchanged positioning/anchoring logic — only wrapped in the fade
     *  transition above, nothing about corner math or gravity is touched. */
    private void applyShrunkLayout(boolean shrunk) {
        panelContainer.setVisibility(shrunk ? View.GONE : View.VISIBLE);
        bubble.setVisibility(shrunk ? View.VISIBLE : View.GONE);

        if (shrunk) {
            params.gravity = Gravity.TOP | Gravity.START;
            if (bubbleX == Integer.MIN_VALUE) {
                DisplayMetrics metrics = context.getResources().getDisplayMetrics();
                int margin = dpToPx(CORNER_MARGIN_DP);
                int bubbleSize = dpToPx(40);
                int savedCorner = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getInt(PREF_CORNER, CORNER_TR);

                switch (savedCorner) {
                    case CORNER_TL: bubbleX = margin; bubbleY = margin; break;
                    case CORNER_BR:
                        bubbleX = metrics.widthPixels - bubbleSize - margin;
                        bubbleY = metrics.heightPixels - bubbleSize - margin;
                        break;
                    case CORNER_BL:
                        bubbleX = margin;
                        bubbleY = metrics.heightPixels - bubbleSize - margin;
                        break;
                    case CORNER_TR:
                    default:
                        bubbleX = metrics.widthPixels - bubbleSize - margin;
                        bubbleY = margin;
                        break;
                }
            }
            params.x = bubbleX;
            params.y = bubbleY;
        } else {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            boolean onLeft = bubbleX < metrics.widthPixels / 2;
            boolean onTop  = bubbleY < metrics.heightPixels / 2;

            params.gravity = (onLeft ? Gravity.START : Gravity.END) | (onTop ? Gravity.TOP : Gravity.BOTTOM);
            params.x = 0;
            params.y = 0;
        }

        windowManager.updateViewLayout(overlayView, params);
    }

    public boolean isShrunk() { return shrunk; }

    public void hide() {
        if (overlayView == null) return;
        if (snapAnimator != null) { snapAnimator.cancel(); snapAnimator = null; }
        try {
            windowManager.removeView(overlayView);
        } catch (Exception e) {
            // View may already be detached (e.g. system tore it down after
            // overlay permission was revoked) — safe to ignore, we're
            // clearing all references below regardless.
            Log.w("TIMBYA_OVERLAY", "removeView failed, view likely already detached", e);
        }
        overlayView = null; panelContainer = null; bubble = null;
        aiCore = null; aiCoreShrunk = null;
        status = null; reply = null; mic = null;
        btnShrink = null; btnClose = null; btnPowerOff = null; params = null;
    }
}