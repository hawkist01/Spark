package com.spark.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

// Simple wrapping row: pills flow onto the next line instead of clipping
// off-screen on narrow phones. No libraries needed.
public class FlowLayout extends ViewGroup {
    private final int hGap, vGap;

    public FlowLayout(Context c) { this(c, null); }

    public FlowLayout(Context c, AttributeSet a) {
        super(c, a);
        float d = c.getResources().getDisplayMetrics().density;
        hGap = (int) (8 * d);
        vGap = (int) (8 * d);
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int maxW = MeasureSpec.getSize(wSpec) - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, lineH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if (ch.getVisibility() == GONE) continue;
            measureChild(ch, wSpec, hSpec);
            int cw = ch.getMeasuredWidth(), chh = ch.getMeasuredHeight();
            if (x > 0 && x + cw > maxW) { x = 0; y += lineH + vGap; lineH = 0; }
            x += cw + hGap;
            lineH = Math.max(lineH, chh);
        }
        y += lineH;
        setMeasuredDimension(resolveSize(maxW + getPaddingLeft() + getPaddingRight(), wSpec),
                resolveSize(y + getPaddingTop() + getPaddingBottom(), hSpec));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int maxW = r - l - getPaddingLeft() - getPaddingRight();
        int x = getPaddingLeft(), y = getPaddingTop(), lineH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if (ch.getVisibility() == GONE) continue;
            int cw = ch.getMeasuredWidth(), chh = ch.getMeasuredHeight();
            if (x > getPaddingLeft() && x + cw > getPaddingLeft() + maxW) {
                x = getPaddingLeft(); y += lineH + vGap; lineH = 0;
            }
            ch.layout(x, y, x + cw, y + chh);
            x += cw + hGap;
            lineH = Math.max(lineH, chh);
        }
    }
}
