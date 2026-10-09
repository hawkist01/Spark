package com.spark.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import com.spark.app.util.SoundManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Miso. Loose in the app.
 *
 * She is a full-screen overlay that sits ABOVE the interface, roams anywhere
 * on it, sits on your cards, and purrs when you touch her. There is no box:
 * the view measures to the whole window and she is positioned in absolute
 * coordinates.
 *
 * Touch: this view is on top of everything, so it MUST NOT swallow taps meant
 * for the UI underneath. onTouchEvent returns false unless the touch actually
 * landed on her body, which lets the event fall through to the view below.
 *
 * Purr: a real purr is a continuous low rumble, not a one-shot sound, so it
 * loops while you are petting her and tails off a few seconds after you stop.
 */
public class CatView extends View {

    private final Paint fur = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint heart = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random();

    private final float d;

    // Position in absolute view coordinates. She starts low-left.
    private float cx, cy;
    private float tx, ty;
    private int faceDir = 1;
    private boolean resting;
    private float restTimer;

    private float phase;        // seconds clock
    private float squish;       // tap squash 0..1
    private float jump;         // tap jump 0..1
    private float purrGlow;     // 0..1 visual purr indicator
    private long lastTick;
    private boolean dragging;

    private final List<float[]> bubbles = new ArrayList<>(); // x,y,r,vy,alpha
    private final List<float[]> hearts = new ArrayList<>();  // x,y,vy,alpha

    private MediaPlayer purrPlayer;
    private long purrUntil;
    private String purrText = "";
    private long purrTextUntil;
    private final Paint word = new Paint(Paint.ANTI_ALIAS_FLAG);

    private OnPurrListener purrListener;

    public interface OnPurrListener { void onPurr(String text); }

    private final String[] purrs = {
            "mrrp.", "purrrrrr...", "*making biscuits*", "mrr-ow?",
            "soft.", "here with you.", "*slow blink*", "brrp!",
            "prrt.", "*headbutt*", "mew.", "*kneading the blanket*",
    };

    public CatView(Context c, AttributeSet a) {
        super(c, a);
        d = c.getResources().getDisplayMetrics().density;
        applyPalette();
        setClickable(false);
        setFocusable(false);
        setHapticFeedbackEnabled(true);
    }

    /** Re-read the palette. Called on every rebuild so she follows the theme. */
    public void applyPalette() {
        boolean night = Theme.night;
        boolean dk = Theme.dark;
        if (Theme.isNothing()) {
            // Monochrome cat with a Nothing-red collar: she belongs to the app
            // rather than being a pastel sticker on top of it.
            fur.setColor(dk ? (night ? 0xFF2A2A2A : 0xFF3A3A3A) : 0xFFE8E8E8);
            dark.setColor(dk ? (night ? 0xFFBFBFBF : 0xFFF0F0F0) : 0xFF1A1A1A);
            pink.setColor(Theme.accent());
            bub.setColor(dk ? 0xFF8A8A8A : 0xFF9A9A9A);
        } else {
            fur.setColor(dk ? 0xFF3A3352 : 0xFFF3E8DC);
            dark.setColor(dk ? 0xFFE8E4F4 : 0xFF3A3348);
            pink.setColor(0xFFF0A6B8);
            bub.setColor(dk ? 0xFF9D8CFF : 0xFF6E5BE0);
        }
        shadow.setColor(dk ? 0x40000000 : 0x30000000);
        heart.setColor(Theme.isNothing() ? Theme.accent() : 0xFFF0A6B8);
        word.setColor(Theme.inkSoft());
        word.setTextSize(12 * d);
        word.setTypeface(android.graphics.Typeface.MONOSPACE);
        word.setTextAlign(Paint.Align.CENTER);
        invalidate();
    }

    public void setOnPurrListener(OnPurrListener l) { purrListener = l; }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (cx == 0 && cy == 0) {
            cx = w * 0.25f;
            cy = h * 0.72f;
            pickTarget();
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastTick = System.currentTimeMillis();
        post(tick);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(tick);
        releasePurr();
    }

    // ------------------------------------------------------------------ roaming

    /** Pick somewhere new to be. Keeps her clear of the very edges. */
    private void pickTarget() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        tx = w * (0.10f + rnd.nextFloat() * 0.80f);
        ty = h * (0.18f + rnd.nextFloat() * 0.70f);
        resting = false;
    }

    private final Runnable tick = new Runnable() {
        public void run() {
            long now = System.currentTimeMillis();
            float dt = Math.min(0.1f, (now - lastTick) / 1000f);
            lastTick = now;
            phase += dt;
            squish = Math.max(0, squish - dt * 3.4f);
            jump = Math.max(0, jump - dt * 1.8f);

            if (purrUntil > 0 && now > purrUntil) stopPurr();
            purrGlow = purrUntil > now ? Math.min(1f, purrGlow + dt * 3f)
                    : Math.max(0f, purrGlow - dt * 1.2f);

            if (!dragging) {
                if (resting) {
                    restTimer -= dt;
                    if (restTimer <= 0) pickTarget();
                } else {
                    float dx = tx - cx, dy = ty - cy;
                    float dist = (float) Math.hypot(dx, dy);
                    if (dist < 6 * d) {
                        // Arrived. Sit for a while - a cat that never stops
                        // moving is a screensaver, not a cat.
                        resting = true;
                        restTimer = 2.5f + rnd.nextFloat() * 5f;
                    } else {
                        float speed = 46 * d * dt;
                        cx += dx / dist * speed;
                        cy += dy / dist * speed;
                        if (Math.abs(dx) > 2) faceDir = dx > 0 ? 1 : -1;
                    }
                }
            }

            Iterator<float[]> bi = bubbles.iterator();
            while (bi.hasNext()) {
                float[] b = bi.next();
                b[1] -= b[3] * dt; b[4] -= dt * 0.55f;
                if (b[4] <= 0) bi.remove();
            }
            Iterator<float[]> hi = hearts.iterator();
            while (hi.hasNext()) {
                float[] p = hi.next();
                p[1] -= p[2] * dt; p[3] -= dt * 0.5f;
                if (p[3] <= 0) hi.remove();
            }

            invalidate();
            postDelayed(this, 40);
        }
    };

    // --------------------------------------------------------------------- purr

    private void startPurr() {
        try {
            if (purrPlayer == null) {
                purrPlayer = new MediaPlayer();
                purrPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_MEDIA).build());
                purrPlayer.setDataSource(getContext(), SoundManager.resolve(getContext(), "miso"));
                purrPlayer.setLooping(true);
                purrPlayer.prepare();
            }
            if (!purrPlayer.isPlaying()) purrPlayer.start();
            purrUntil = System.currentTimeMillis() + 4500;
        } catch (Exception ignored) {}
    }

    private void stopPurr() {
        purrUntil = 0;
        try { if (purrPlayer != null && purrPlayer.isPlaying()) purrPlayer.pause(); }
        catch (Exception ignored) {}
    }

    private void releasePurr() {
        try { if (purrPlayer != null) { purrPlayer.release(); purrPlayer = null; } }
        catch (Exception ignored) {}
    }

    // -------------------------------------------------------------------- touch

    /** True if a point is inside a generous box around her. */
    private boolean hitCat(float x, float y) {
        float r = 54 * d;
        return x > cx - r && x < cx + r && y > cy - r * 1.6f && y < cy + r * 0.8f;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                // Critical: if the touch missed her, refuse it so the tap reaches
                // the button underneath. Without this a full-screen overlay
                // would make the entire app dead to touch.
                if (!hitCat(e.getX(), e.getY())) return false;
                dragging = true;
                squish = 1f;
                jump = 0.7f;
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                startPurr();
                for (int i = 0; i < 7; i++) {
                    bubbles.add(new float[]{
                            e.getX() + (rnd.nextFloat() - 0.5f) * 40 * d,
                            e.getY() - rnd.nextFloat() * 26 * d,
                            (4 + rnd.nextFloat() * 7) * d,
                            (46 + rnd.nextFloat() * 60) * d, 0.9f});
                }
                for (int i = 0; i < 3; i++) {
                    hearts.add(new float[]{
                            e.getX() + (rnd.nextFloat() - 0.5f) * 46 * d,
                            e.getY() - rnd.nextFloat() * 20 * d,
                            (34 + rnd.nextFloat() * 26) * d, 1f});
                }
                if (purrListener != null) {
                    purrText = purrs[rnd.nextInt(purrs.length)];
                    purrTextUntil = System.currentTimeMillis() + 1600;
                    purrListener.onPurr(purrText);
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!dragging) return false;
                // Petting: she follows your finger.
                cx = e.getX();
                cy = e.getY();
                if (rnd.nextFloat() < 0.06f) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                startPurr();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!dragging) return false;
                dragging = false;
                squish = 0.8f;
                pickTarget();
                return true;
            }
        }
        return false;
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        // Full bleed: she is an overlay, not a widget.
        setMeasuredDimension(resolveSize(MeasureSpec.getSize(wSpec), wSpec),
                resolveSize(MeasureSpec.getSize(hSpec), hSpec));
    }

    // --------------------------------------------------------------------- draw

    @Override protected void onDraw(Canvas cv) {
        if (getWidth() == 0) return;

        float walkHop = resting ? 0 : Math.abs((float) Math.sin(phase * 1.7)) * 7 * d;
        float jumpY = jump * jump * 46 * d;
        float breath = ((float) Math.sin(phase * (resting ? 1.6f : 1.05f)) * 0.5f + 0.5f);
        float sq = squish * squish;
        float sx = 1 + sq * 0.22f, sy = 1 - sq * 0.28f;

        float bw = 30 * d * sx * (1 + breath * 0.03f);
        float bh = 26 * d * sy * (1 + breath * 0.05f);
        float groundY = cy;
        float by = groundY - walkHop - jumpY - bh;

        float px = cx;

        cv.save();
        if (faceDir < 0) {
            cv.translate(px * 2, 0);
            cv.scale(-1, 1);
        }

        // shadow on whatever she is standing "on"
        float shScale = 1 - jump * 0.35f;
        shadow.setAlpha((int) (26 + 18 * (1 - jump)));
        cv.drawOval(px - bw * 1.1f * shScale, groundY + 3 * d,
                px + bw * 1.1f * shScale, groundY + 9 * d, shadow);

        // tail
        float wag = (float) Math.sin(phase * (resting ? 1.1f : 2.3f)) * 9 * d;
        dark.setStyle(Paint.Style.STROKE);
        dark.setStrokeWidth(5.5f * d);
        dark.setStrokeCap(Paint.Cap.ROUND);
        cv.drawLine(px - bw * 0.85f, by + bh * 0.75f,
                px - bw * 1.55f, by + bh * 0.15f + wag, dark);

        // body
        RectF body = new RectF(px - bw, by, px + bw, by + bh * 1.85f);
        dark.setStyle(Paint.Style.FILL);
        cv.drawRoundRect(body, bw * 0.85f, bw * 0.85f, fur);

        // stripes
        for (int i = -1; i <= 1; i++) {
            cv.drawRect(px + i * bw * 0.40f - 1.6f * d, by + 4 * d,
                    px + i * bw * 0.40f + 1.6f * d, by + 12 * d, dark);
        }

        // head
        float hr = 19 * d * sx;
        float hx = px, hy = by - hr * 0.5f;
        Path earL = new Path();
        earL.moveTo(hx - hr * 0.85f, hy - hr * 0.3f);
        earL.lineTo(hx - hr * 0.58f, hy - hr * 1.22f);
        earL.lineTo(hx - hr * 0.08f, hy - hr * 0.48f);
        earL.close();
        Path earR = new Path();
        earR.moveTo(hx + hr * 0.85f, hy - hr * 0.3f);
        earR.lineTo(hx + hr * 0.58f, hy - hr * 1.22f);
        earR.lineTo(hx + hr * 0.08f, hy - hr * 0.48f);
        earR.close();
        cv.drawPath(earL, fur);
        cv.drawPath(earR, fur);
        cv.drawCircle(hx, hy, hr, fur);

        // eyes: blink now and then
        boolean blink = (phase % 4.3f) < 0.16f;
        dark.setStrokeWidth(1.9f * d);
        dark.setStyle(Paint.Style.STROKE);
        float ex = hr * 0.36f, ey = hy - hr * 0.04f;
        if (blink) {
            cv.drawLine(hx - ex - 3.5f * d, ey, hx - ex + 3.5f * d, ey, dark);
            cv.drawLine(hx + ex - 3.5f * d, ey, hx + ex + 3.5f * d, ey, dark);
        } else {
            cv.drawArc(hx - ex - 4.5f * d, ey - 3.5f * d, hx - ex + 4.5f * d, ey + 3.5f * d,
                    200, 140, false, dark);
            cv.drawArc(hx + ex - 4.5f * d, ey - 3.5f * d, hx + ex + 4.5f * d, ey + 3.5f * d,
                    200, 140, false, dark);
        }
        dark.setStyle(Paint.Style.FILL);

        // nose + muzzle blush
        cv.drawCircle(hx, ey + hr * 0.34f, 1.8f * d, pink);
        pink.setAlpha(70);
        cv.drawCircle(hx - hr * 0.55f, ey + hr * 0.4f, 3 * d, pink);
        cv.drawCircle(hx + hr * 0.55f, ey + hr * 0.4f, 3 * d, pink);
        pink.setAlpha(255);

        // whiskers
        dark.setStrokeWidth(1f * d);
        dark.setStyle(Paint.Style.STROKE);
        for (int s = -1; s <= 1; s += 2) {
            cv.drawLine(hx + s * hr * 0.3f, ey + hr * 0.3f,
                    hx + s * hr * 1.15f, ey + hr * (0.18f + 0.12f * s * s), dark);
            cv.drawLine(hx + s * hr * 0.3f, ey + hr * 0.42f,
                    hx + s * hr * 1.1f, ey + hr * 0.58f, dark);
        }
        dark.setStyle(Paint.Style.FILL);

        // collar: the one red thing on her
        pink.setStyle(Paint.Style.STROKE);
        pink.setStrokeWidth(2.4f * d);
        cv.drawArc(hx - hr * 0.72f, hy + hr * 0.5f, hx + hr * 0.72f, hy + hr * 1.15f,
                200, 140, false, pink);
        pink.setStyle(Paint.Style.FILL);
        cv.drawCircle(hx, hy + hr * 0.92f, 2.2f * d, pink);

        cv.restore();

        // purr indicator: three arcs of "sound" while she rumbles
        if (purrGlow > 0.01f) {
            bub.setAlpha((int) (purrGlow * 150));
            bub.setStyle(Paint.Style.STROKE);
            bub.setStrokeWidth(1.6f * d);
            for (int i = 1; i <= 3; i++) {
                float rr = (10 + i * 7) * d * (0.8f + purrGlow * 0.4f);
                float a = (float) Math.sin(phase * 6 + i) * 0.25f;
                cv.drawArc(px + hr * 1.1f, hy - rr + a * 10 * d,
                        px + hr * 1.1f + rr, hy + rr + a * 10 * d, -60, 120, false, bub);
            }
            bub.setStyle(Paint.Style.FILL);
            bub.setAlpha(255);
        }

        for (float[] b : bubbles) {
            bub.setAlpha((int) (b[4] * 200));
            cv.drawCircle(b[0], b[1], b[2], bub);
        }
        bub.setAlpha(255);

        // hearts
        for (float[] p : hearts) {
            heart.setAlpha((int) (p[3] * 210));
            float s = 3.2f * d;
            float hxp = p[0], hyp = p[1];
            cv.drawCircle(hxp - s * 0.5f, hyp - s * 0.3f, s * 0.62f, heart);
            cv.drawCircle(hxp + s * 0.5f, hyp - s * 0.3f, s * 0.62f, heart);
            Path hp = new Path();
            hp.moveTo(hxp - s * 1.05f, hyp - s * 0.1f);
            hp.lineTo(hxp, hyp + s * 1.15f);
            hp.lineTo(hxp + s * 1.05f, hyp - s * 0.1f);
            hp.close();
            cv.drawPath(hp, heart);
        }
        heart.setAlpha(255);

        // her word, floating briefly above her head
        if (purrTextUntil > System.currentTimeMillis() && !purrText.isEmpty()) {
            float fade = Math.min(1f, (purrTextUntil - System.currentTimeMillis()) / 400f);
            word.setAlpha((int) (fade * 220));
            cv.drawText(purrText, px, by - 34 * d, word);
        }
    }
}
