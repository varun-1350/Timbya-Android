package com.example.timbya.overlay;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.example.timbya.core.TimbyaState;
import androidx.annotation.NonNull;



/**
 * The "living AI Core" — replaces the old mic icon as Timbya's visual
 * identity. Purely a rendering component with no app logic; driven
 * entirely by setState() calls from OverlayController.
 * pulseMemory() and setVisionActive() are implemented but intentionally
 * left unwired — see design notes in the accompanying response. They're
 * ready for whenever a memory-write hook / vision system exists.
 */
public class AiCoreView extends View {

    public enum CoreState { IDLE, LISTENING, THINKING, SPEAKING }

    private static final int ACCENT = Color.parseColor("#3B82F6");

    private CoreState state = CoreState.IDLE;

    private float coreScale = 1f;
    private ValueAnimator coreAnimator;

    private float rippleProgress = 0f;
    private ValueAnimator rippleAnimator;

    private float ringRotation = 0f;
    private ValueAnimator ringAnimator;

    float sparkleAlpha = 0f;
    boolean visionActive = false;

    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sparklePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ringRect = new RectF();


    public AiCoreView(Context context, AttributeSet attrs) {
        super(context, attrs);
        ringPaint.setStyle(Paint.Style.STROKE);
        haloPaint.setStyle(Paint.Style.STROKE);
        haloPaint.setColor(ACCENT);
        sparklePaint.setColor(Color.parseColor("#7DE0FF"));
        startBreathing();
    }

    public void setState(CoreState newState) {
        if (state == newState) return;
        state = newState;
        stopAllAnimators();

        switch (state) {
            case IDLE: startBreathing(); break;
            case LISTENING: startListeningPulse(); startRipple(); break;
            case THINKING: startRingRotation(); break;
            case SPEAKING: startSpeakingMotion(); break;
        }
    }

    /** Lets OverlayController pass the app's existing TimbyaState directly. */
    public void setState(TimbyaState timbyaState) {
        switch (timbyaState) {
            case LISTENING: setState(CoreState.LISTENING); break;
            case PROCESSING: setState(CoreState.THINKING); break;
            case SPEAKING: setState(CoreState.SPEAKING); break;
            default: setState(CoreState.IDLE); break;
        }
    }
    /*
    If needed:

    public void pulseMemory() {


        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f, 0f);
        anim.setDuration(600);
        anim.addUpdateListener(a -> { sparkleAlpha = (float) a.getAnimatedValue(); invalidate(); });
        anim.start();
    }public void setVisionActive(boolean active) {
        this.visionActive = active;
        invalidate();
    }*/

    private void startBreathing() {
        coreAnimator = ValueAnimator.ofFloat(0.90f, 1.0f);
        coreAnimator.setDuration(2000);
        coreAnimator.setRepeatMode(ValueAnimator.REVERSE);
        coreAnimator.setRepeatCount(ValueAnimator.INFINITE);
        coreAnimator.addUpdateListener(a -> { coreScale = (float) a.getAnimatedValue(); invalidate(); });
        coreAnimator.start();
    }

    private void startListeningPulse() {
        coreAnimator = ValueAnimator.ofFloat(1.05f, 1.15f);
        coreAnimator.setDuration(900);
        coreAnimator.setRepeatMode(ValueAnimator.REVERSE);
        coreAnimator.setRepeatCount(ValueAnimator.INFINITE);
        coreAnimator.addUpdateListener(a -> { coreScale = (float) a.getAnimatedValue(); invalidate(); });
        coreAnimator.start();
    }

    private void startRipple() {
        rippleAnimator = ValueAnimator.ofFloat(0f, 1f);
        rippleAnimator.setDuration(1200);
        rippleAnimator.setRepeatCount(ValueAnimator.INFINITE);
        rippleAnimator.addUpdateListener(a -> { rippleProgress = (float) a.getAnimatedValue(); invalidate(); });
        rippleAnimator.start();
    }

    private void startRingRotation() {
        ringAnimator = ValueAnimator.ofFloat(0f, 360f);
        ringAnimator.setDuration(1400);
        ringAnimator.setInterpolator(new LinearInterpolator());
        ringAnimator.setRepeatCount(ValueAnimator.INFINITE);
        ringAnimator.addUpdateListener(a -> { ringRotation = (float) a.getAnimatedValue(); invalidate(); });
        ringAnimator.start();
    }

    /** Stylistic simulation, not real TTS audio amplitude — see design notes. */
    private void startSpeakingMotion() {
        coreAnimator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        coreAnimator.setDuration(1600);
        coreAnimator.setInterpolator(new LinearInterpolator());
        coreAnimator.setRepeatCount(ValueAnimator.INFINITE);
        coreAnimator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            float wave = (float) (Math.sin(t) * 0.5 + Math.sin(t * 2.3f) * 0.3);
            coreScale = 1.05f + wave * 0.12f;
            invalidate();
        });
        coreAnimator.start();
    }
    private float lastGlowRadius = -1f;
    private float lastGlowCx = -1f, lastGlowCy = -1f;

    private void updateGlowShaderIfNeeded(float cx, float cy, float radius) {
        // Rebuild the shader only when geometry actually changed (radius pulses
        // during animation, but cx/cy only change on layout) instead of every frame.
        if (radius == lastGlowRadius && cx == lastGlowCx && cy == lastGlowCy) return;
        lastGlowRadius = radius; lastGlowCx = cx; lastGlowCy = cy;
        glowPaint.setShader(new RadialGradient(cx, cy, radius * 1.8f,
                new int[]{ withAlpha(110), withAlpha(0) },
                null, Shader.TileMode.CLAMP));
    }
    public void pause() {
        stopAllAnimators();
    }

    private void stopAllAnimators() {
        if (coreAnimator != null) coreAnimator.cancel();
        if (rippleAnimator != null) rippleAnimator.cancel();
        if (ringAnimator != null) ringAnimator.cancel();
    }


    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float maxRadius = Math.min(getWidth(), getHeight()) / 2f;
        float baseRadius = maxRadius * 0.55f;
        float radius = baseRadius * coreScale;

        if (visionActive) {
            haloPaint.setStrokeWidth(maxRadius * 0.08f);
            haloPaint.setAlpha(70);
            canvas.drawCircle(cx, cy, maxRadius * 0.92f, haloPaint);
        }

        if (state == CoreState.LISTENING) {
            ripplePaint.setColor(ACCENT);
            ripplePaint.setStyle(Paint.Style.STROKE);
            ripplePaint.setStrokeWidth(3f);
            ripplePaint.setAlpha((int) (90 * (1f - rippleProgress)));
            float rippleRadius = baseRadius + (maxRadius - baseRadius) * rippleProgress;
            canvas.drawCircle(cx, cy, rippleRadius, ripplePaint);
        }

        if (state == CoreState.THINKING) {
            ringPaint.setColor(ACCENT);
            ringPaint.setStrokeWidth(maxRadius * 0.06f);
            ringPaint.setAlpha(200);
            ringRect.set(cx - maxRadius * 0.85f, cy - maxRadius * 0.85f,
                    cx + maxRadius * 0.85f, cy + maxRadius * 0.85f);
            canvas.drawArc(ringRect, ringRotation, 90f, false, ringPaint);
        }

        updateGlowShaderIfNeeded(cx, cy, radius);
        canvas.drawCircle(cx, cy, radius * 1.8f, glowPaint);

        corePaint.setColor(state == CoreState.IDLE ? withAlpha(220) : ACCENT);
        canvas.drawCircle(cx, cy, radius, corePaint);

        if (sparkleAlpha > 0f) {
            sparklePaint.setAlpha((int) (255 * sparkleAlpha));
            canvas.drawCircle(cx + radius * 0.7f, cy - radius * 0.7f, maxRadius * 0.08f, sparklePaint);
        }
    }

    private int withAlpha(int alpha) {
        return Color.argb(alpha, Color.red(ACCENT), Color.green(ACCENT), Color.blue(ACCENT));
    }
}