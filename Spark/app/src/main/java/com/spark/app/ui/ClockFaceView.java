package com.spark.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;

/**
 * Interactive analog clock picker: drag the hand around the dial.
 * Hour mode first; after release it flips to minute mode; tap toggles back.
 * AM/PM handled by the host dialog via the ampm() flag.
 */
public class ClockFaceView extends View {
    public interface Listener { void onTimeChanged(int hour24, int minute, boolean hourMode); }

    private final Paint faceP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selArcP = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int hour24 = 9, minute = 0;
    private boolean hourMode = true;
    private boolean dark;
    private Listener listener;

    private float cx, cy, R;

    public ClockFaceView(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        faceP.setStyle(Paint.Style.FILL);
        tickP.setStrokeWidth(2.2f * d);
        numP.setTextAlign(Paint.Align.CENTER);
        numP.setTextSize(14 * d);
        numP.setFakeBoldText(true);
        handP.setStrokeWidth(3.4f * d);
        handP.setStrokeCap(Paint.Cap.ROUND);
        hubP.setStyle(Paint.Style.FILL);
        selArcP.setStyle(Paint.Style.STROKE);
        selArcP.setStrokeWidth(5f * d);
        selArcP.setStrokeCap(Paint.Cap.ROUND);
        setHapticFeedbackEnabled(true);
    }

    public void setDark(boolean d) { dark = d; invalidate(); }
    public void setListener(Listener l) { listener = l; }

    public void set(int h24, int m) { hour24 = h24 % 24; minute = m % 60; invalidate(); }
    public void setHourMode(boolean hm) { hourMode = hm; invalidate(); }
    public int hour() { return hour24; }
    public int minute() { return minute; }
    public boolean isHourMode() { return hourMode; }

    private int ink() { return dark ? 0xFFE8E4F4 : 0xFF17161C; }
    private int soft() { return dark ? 0xFF9C96B4 : 0xFF9B93AD; }
    private int faceC() { return dark ? 0xFF241F33 : 0xFFFFFFFF; }
    private int accent() { return 0xFF6E5BE0; }

    @Override protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        cx = w / 2f; cy = h / 2f;
        R = Math.min(w, h) / 2f - 10f;

        faceP.setColor(faceC());
        cv.drawCircle(cx, cy, R, faceP);

        // hour ticks + full 1-12 numbers (Nothing OS dotted-dial style)
        int ink = ink(), soft = soft();
        float rIn = R * 0.80f, rNum = R * 0.62f;
        int selH = hourMode ? ((hour24 % 12 == 0) ? 12 : hour24 % 12) : -1;
        int selM = !hourMode ? (minute / 5) % 12 : -1;
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(i * 30 - 90);
            float x1 = cx + (float) Math.cos(a) * (R - 6f);
            float y1 = cy + (float) Math.sin(a) * (R - 6f);
            float x2 = cx + (float) Math.cos(a) * (R - (i % 3 == 0 ? 18f : 11f));
            float y2 = cy + (float) Math.sin(a) * (R - (i % 3 == 0 ? 18f : 11f));
            boolean sel = hourMode ? (i == selH % 12) : (i == selM);
            if (sel) {
                // glyph-style selected dot: accent halo behind the number
                hubP.setColor(accent());
                float nx0 = cx + (float) Math.cos(a) * rNum;
                float ny0 = cy + (float) Math.sin(a) * rNum;
                cv.drawCircle(nx0, ny0, 15f * getResources().getDisplayMetrics().density / 2.2f, hubP);
            }
            tickP.setColor(i % 3 == 0 ? ink : soft);
            cv.drawLine(x1, y1, x2, y2, tickP);
            float nx = cx + (float) Math.cos(a) * rNum;
            float ny = cy + (float) Math.sin(a) * rNum;
            numP.setColor(sel ? 0xFFFFFFFF : (hourMode ? ink : soft));
            if (hourMode) {
                cv.drawText(String.valueOf(i == 0 ? 12 : i), nx, ny + numP.getTextSize() / 3f, numP);
            } else {
                // minute mode: every 5-minute value, selected one lit
                String label = String.format(java.util.Locale.US, "%02d", (i * 5) % 60);
                float ts = numP.getTextSize();
                numP.setTextSize(ts * 0.82f);
                cv.drawText(label, nx, ny + numP.getTextSize() / 3f, numP);
                numP.setTextSize(ts);
            }
        }

        // accent arc from 12 to current hand + hand
        float ang = angleOfHand();
        RectF arc = new RectF(cx - rIn, cy - rIn, cx + rIn, cy + rIn);
        selArcP.setColor(accent());
        cv.drawArc(arc, -90, ang, false, selArcP);

        double ra = Math.toRadians(ang - 90);
        float hx = cx + (float) Math.cos(ra) * rIn;
        float hy = cy + (float) Math.sin(ra) * rIn;
        handP.setColor(ink);
        cv.drawLine(cx, cy, hx, hy, handP);
        // precision tip dot: exactly where the hand points
        hubP.setColor(accent());
        cv.drawCircle(hx, hy, 8f * getResources().getDisplayMetrics().density / 3f, hubP);
        hubP.setColor(0xFFFFFFFF);
        cv.drawCircle(hx, hy, 3f * getResources().getDisplayMetrics().density / 3f, hubP);
        hubP.setColor(accent());
        cv.drawCircle(cx, cy, 7f, hubP);
        hubP.setColor(0xFFFFFFFF);
        cv.drawCircle(cx, cy, 2.6f, hubP);

        // center readout
        numP.setColor(soft);
        numP.setTextSize(numP.getTextSize()); // keep
    }

    private float angleOfHand() {
        if (hourMode) {
            float hf = (hour24 % 12) + minute / 60f;
            return hf * 30f;
        }
        return minute * 6f;
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int want = (int) (300 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(resolveSize(want, wSpec), resolveSize(want, hSpec));
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX() - cx, y = e.getY() - cy;
        float dist = (float) Math.hypot(x, y);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (dist > R * 1.15f) return false;
                apply(x, y);
                return true;
            case MotionEvent.ACTION_MOVE:
                apply(x, y);
                return true;
            case MotionEvent.ACTION_UP:
                if (!hourMode) {
                    // after setting minutes, hand back to hour mode
                    hourMode = true;
                    invalidate();
                    if (listener != null) listener.onTimeChanged(hour24, minute, true);
                } else {
                    hourMode = false;
                    invalidate();
                    if (listener != null) listener.onTimeChanged(hour24, minute, false);
                }
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void apply(float x, float y) {
        double deg = Math.toDegrees(Math.atan2(y, x)) + 90;
        if (deg < 0) deg += 360;
        int before = hourMode ? hour24 : minute;
        if (hourMode) {
            int h = (int) Math.round(deg / 30.0);
            h = ((h % 12) + 12) % 12; // 0..11, 0 = twelve
            int base = hour24 < 12 ? 0 : 12; // AM/PM kept by dialog pills
            hour24 = (base == 0) ? (h == 0 ? 0 : h) : (h == 0 ? 12 : 12 + h);
            if (hour24 >= 24) hour24 -= 24;
        } else {
            // Minute mode snaps to 5-minute steps while dragging (precise +
            // calm); the -/+ steppers below do exact 1-minute nudges.
            int m = (int) Math.round(deg / 6.0);
            m = ((m % 60) + 60) % 60;
            minute = (m + 2) / 5 * 5 % 60;
        }
        int after = hourMode ? hour24 : minute;
        if (after != before) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        invalidate();
        if (listener != null) listener.onTimeChanged(hour24, minute, hourMode);
    }

    public static String fmt(int h24, int m) {
        int h = h24 % 24;
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return String.format(Locale.US, "%d:%02d %s", h12, m, h < 12 ? "AM" : "PM");
    }

    public static int[] now() {
        Calendar c = Calendar.getInstance();
        return new int[]{c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)};
    }
}
