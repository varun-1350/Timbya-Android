package com.example.timbya.overlay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.TextView;

import com.example.timbya.R;
import com.example.timbya.utils.Constants;

public class OverlayController {

    private static final int CORNER_MARGIN_DP = 16;
    private static final int TOUCH_SLOP_PX = 16;

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


    // Last known / default shrunk-bubble corner position, in TOP|START
    // window coordinates. Integer.MIN_VALUE means "not set yet".
    private int bubbleX = Integer.MIN_VALUE;
    private int bubbleY = Integer.MIN_VALUE;
    private static final String PREFS_NAME  = "timbya_overlay_prefs";
    private static final String PREF_CORNER = "corner";      // 0=TR 1=TL 2=BR 3=BL
    private static final int    CORNER_TR   = 0;
    private static final int    CORNER_TL   = 1;
    private static final int    CORNER_BR   = 2;
    private static final int    CORNER_BL   = 3;

    // drag state
    private float touchStartRawX, touchStartRawY;
    private int paramsStartX, paramsStartY;
    private boolean isDragging = false;

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

        windowManager.addView(overlayView, params);
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
                    callbacks.onShrinkToggle(); // plain tap = expand
                }
                return true;

            default:
                return false;
        }
    }

    /** Only reachable while shrunk (bubble is the only visible/draggable
     *  view), so gravity is always TOP|START by the time this runs. */
    private void snapToNearestCorner() {
        if (overlayView == null || params == null) return;

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int screenWidth  = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        int margin       = dpToPx(CORNER_MARGIN_DP);
        int bubbleSize   = bubble.getWidth() > 0 ? bubble.getWidth() : dpToPx(32);

        boolean left = (params.x + bubbleSize / 2f) < screenWidth / 2f;
        boolean top  = (params.y + bubbleSize / 2f) < screenHeight / 2f;

        int targetX = left ? margin : screenWidth  - bubbleSize - margin;
        int targetY = top  ? margin : screenHeight - bubbleSize - margin;

        bubbleX = targetX;
        bubbleY = targetY;

        // Persist the corner so the next service start restores it
        int corner = top  ? (left ? CORNER_TL : CORNER_TR)
                : (left ? CORNER_BL : CORNER_BR);
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(PREF_CORNER, corner).apply();

        animateTo(targetX, targetY);
    }

    private void animateTo(int targetX, int targetY) {
        final int startX = params.x;
        final int startY = params.y;

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(220);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            params.x = (int) (startX + (targetX - startX) * fraction);
            params.y = (int) (startY + (targetY - startY) * fraction);
            if (overlayView != null) windowManager.updateViewLayout(overlayView, params);
        });
        animator.start();
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

    public void setReply(String text) { if (reply != null) reply.setText(text); }

    public void setMicActive(boolean active) {
        int color = active ? Color.parseColor("#4DE5FF") : Color.parseColor("#B0B0B0");
        if (mic != null) mic.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        if (bubble != null) bubble.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    public void setShrunk(boolean shrunk) {
        this.shrunk = shrunk;
        if (panelContainer == null || bubble == null || overlayView == null || params == null) return;

        panelContainer.setVisibility(shrunk ? View.GONE : View.VISIBLE);
        bubble.setVisibility(shrunk ? View.VISIBLE : View.GONE);

        if (shrunk) {
            params.gravity = Gravity.TOP | Gravity.START;
            if (bubbleX == Integer.MIN_VALUE) {
                // No position set this session — load saved corner, or default to TOP RIGHT
                DisplayMetrics metrics = context.getResources().getDisplayMetrics();
                int margin     = dpToPx(CORNER_MARGIN_DP);
                int bubbleSize = dpToPx(56); // matches overlay_bubble_size
                int savedCorner = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getInt(PREF_CORNER, CORNER_TR); // first install → Top Right

                switch (savedCorner) {
                    case CORNER_TL:
                        bubbleX = margin;
                        bubbleY = margin;
                        break;
                    case CORNER_BR:
                        bubbleX = metrics.widthPixels  - bubbleSize - margin;
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
            // Anchor the expanded panel to the same corner as the bubble.
            // We know which corner from bubbleX/bubbleY, so derive gravity flags
            // and let the panel grow inward from that edge.
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            boolean onLeft = bubbleX < metrics.widthPixels / 2;
            boolean onTop  = bubbleY < metrics.heightPixels / 2;

            int hGravity = onLeft  ? Gravity.START : Gravity.END;
            int vGravity = onTop   ? Gravity.TOP   : Gravity.BOTTOM;
            params.gravity = hGravity | vGravity;

            // Keep the panel edge flush with the screen edge (no offset needed —
            // WRAP_CONTENT + END/START/TOP/BOTTOM gravity does the right thing).
            params.x = 0;
            params.y = 0;
        }

        windowManager.updateViewLayout(overlayView, params);
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