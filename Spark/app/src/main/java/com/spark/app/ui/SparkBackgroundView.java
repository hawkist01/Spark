package com.spark.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * The app background. Two personalities, driven by Theme.style:
 *
 *   NOTHING - a flat black (or white) field with Nothing's dot-matrix grid
 *             ruled across it. No gradient, no blur, no colour. This is the
 *             wallpaper of the whole design language, so it does the least
 *             possible: the dots sit at ~8% alpha and exist only to give the
 *             eye a scale reference.
 *   SOFT    - the earlier pastel frosted washes, unchanged.
 */
public class SparkBackgroundView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    public SparkBackgroundView(Context c, AttributeSet a) {
        super(c, a);
        density = getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        if (Theme.isNothing()) drawNothing(canvas, w, h);
        else drawSoft(canvas, w, h);
    }

    // ----------------------------------------------------------------- NOTHING

    private void drawNothing(Canvas canvas, int w, int h) {
        // Flat field. Theme.gradient() returns three identical stops here, so
        // this stays one solid colour rather than becoming a wash.
        int[] g = Theme.gradient();
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(g[0]);
        canvas.drawRect(0, 0, w, h, paint);

        int dotColor = Theme.dot();
        if (Color.alpha(dotColor) == 0) return;

        float spacing = Theme.dotSpacingDp() * density;
        float radius = Theme.dotRadiusDp() * density;
        if (spacing < 2f) return;

        paint.setColor(dotColor);

        // Offset by half a cell so the grid reads as a field rather than as
        // something that starts in the corner.
        float startX = spacing * 0.5f;
        float startY = spacing * 0.5f;
        for (float y = startY; y < h + radius; y += spacing) {
            for (float x = startX; x < w + radius; x += spacing) {
                canvas.drawCircle(x, y, radius, paint);
            }
        }
    }

    // -------------------------------------------------------------------- SOFT

    private void drawSoft(Canvas canvas, int w, int h) {
        int[] g = Theme.gradient();
        LinearGradient lg = new LinearGradient(0, 0, w * 0.25f, h, g, null, Shader.TileMode.CLAMP);
        paint.setShader(lg);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        if (Theme.dark) {
            glow(canvas, w * 0.5f, -h * 0.06f, w * 0.75f, 0x2B3B4DFF); // indigo sky
            glow(canvas, w * 0.12f, h * 0.55f, w * 0.5f, 0x1E4D3AFF);  // deep mint
            glow(canvas, w * 0.9f, h * 0.85f, w * 0.55f, 0x22354DFF);  // ember blue
        } else {
            glow(canvas, w * 0.5f, -h * 0.08f, w * 0.8f, 0x59BFD4FF);  // sky wash
            glow(canvas, w * 0.02f, h * 0.30f, w * 0.55f, 0x4FD6B4FF); // mint wash
            glow(canvas, w * 0.98f, h * 0.52f, w * 0.6f, 0x55C9A8FF);  // sea wash
            glow(canvas, w * 0.30f, h * 1.02f, w * 0.85f, 0x5AE8C77F); // warm sand glow
            glow(canvas, w * 0.95f, h * 0.05f, w * 0.4f, 0x3FC3C3AA);  // lilac hint
        }
    }

    private void glow(Canvas canvas, float cx, float cy, float radius, int argb) {
        int a = Color.alpha(argb);
        int rgb = argb & 0x00FFFFFF;
        // Layered radial glows = cheap frosted blur, soft edges, no banding.
        for (int i = 4; i >= 1; i--) {
            float r = radius * i / 4f;
            int alpha = (int) (a * (0.32f / i));
            RadialGradient rg = new RadialGradient(cx, cy, r,
                    new int[]{alpha << 24 | rgb, Color.TRANSPARENT},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP);
            paint.setShader(rg);
            canvas.drawCircle(cx, cy, r, paint);
        }
        paint.setShader(null);
    }
}
