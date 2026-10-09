package com.spark.app.alarm;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.spark.app.MainActivity;
import com.spark.app.R;
import com.spark.app.data.GuardianApps;
import com.spark.app.data.Store;
import com.spark.app.util.Notifier;
import com.spark.app.util.SoundManager;

import java.util.HashMap;
import java.util.Locale;

public class GuardianAccessibilityService extends AccessibilityService {
    public static GuardianAccessibilityService instance;

    private static final int[][] THEMES = {
            {0xFFF6F4FF, 0xFFEDF6EE, 0xFFFAF0F4},  // Dawn (default pastel)
            {0xFFEAF4FF, 0xFFE8FBF6, 0xFFF2F0FF},  // Ocean
            {0xFF201C38, 0xFF2A2447, 0xFF352A55},  // Dusk (dark purple for 2am)
    };

    private WindowManager wm;
    private LinearLayout overlay;
    private boolean showing;
    private String currentPkg;
    private long sessionStart;
    private final HashMap<String, Long> allowUntil = new HashMap<>();
    private Typeface dot, mono, monoBold;
    private final int Ink = Color.parseColor("#17161C");
    private final int InkSoft = Color.parseColor("#565664");
    private final int LINE = 0x45222233;

    @Override public void onCreate() {
        super.onCreate(); instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        dot = getResources().getFont(R.font.dot_gothic16);
        mono = getResources().getFont(R.font.space_mono);
        monoBold = getResources().getFont(R.font.space_mono_bold);
    }

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 100;
        setServiceInfo(info);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        String pkg = e.getPackageName() != null ? e.getPackageName().toString() : null;
        handle(pkg);
    }

    @Override public void onInterrupt() {}

    private boolean isSystemy(String p) {
        if (p == null) return true;
        if (p.equals("com.spark.app")) return true;
        if (p.startsWith("com.android.systemui") || p.startsWith("com.android.settings") || p.startsWith("com.android.launcher")) return true;
        return false;
    }

    private void handle(String pkg) {
        Store.ensure(this);
        if (isSystemy(pkg)) {
            if (showing) hide();
            currentPkg = null; sessionStart = 0;
            return;
        }
        long now = System.currentTimeMillis();
        if (!pkg.equals(currentPkg)) { currentPkg = pkg; sessionStart = now; }
        boolean guarded = Store.data.settings.guardEnabled &&
                (Store.data.guardedApps.contains(pkg) || GuardianApps.isSocial(pkg));
        if (!guarded) { if (showing) hide(); return; }
        long continuous = now - sessionStart;
        long thr = Store.data.settings.guardThresholdMin * 60_000L;
        long allowed = allowUntil.getOrDefault(pkg, 0L);
        if (!showing && continuous >= thr && now > allowed) show(pkg, continuous);
    }

    private void show(String pkg, long ms) {
        if (showing) return;
        if (!Settings.canDrawOverlays(this)) {
            Notifier.guard(this, "You've been scrolling for a while. Take a breath or do a spark.");
            allowUntil.put(pkg, System.currentTimeMillis() + Store.data.settings.guardAllowMin * 60_000L);
            return;
        }
        overlay = buildOverlay(pkg, ms);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        try {
            wm.addView(overlay, lp);
            showing = true;
            SoundManager.preview(this, SoundManager.resolve(this, "doom"));
            animateIn(overlay);
        } catch (Exception ignored) {}
    }

    // Nothing-OS-style entrance: fade + slide-up with a spring overshoot
    private void animateIn(View root) {
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(220).start();
        final View card = root.findViewWithTag("guardCard");
        if (card != null) {
            card.setTranslationY(dp(70));
            card.setScaleX(0.92f); card.setScaleY(0.92f);
            AnimatorSet set = new AnimatorSet();
            set.playTogether(
                    ObjectAnimator.ofFloat(card, "translationY", dp(70), 0f),
                    ObjectAnimator.ofFloat(card, "scaleX", 0.92f, 1f),
                    ObjectAnimator.ofFloat(card, "scaleY", 0.92f, 1f));
            set.setDuration(430);
            set.setInterpolator(new OvershootInterpolator(0.85f));
            set.start();
        }
    }

    public void hide() {
        if (overlay != null && showing) {
            try {
                overlay.animate().alpha(0f).setDuration(160).withEndAction(() -> {
                    try { wm.removeView(overlay); } catch (Exception ignored) {}
                }).start();
            } catch (Exception ignored) { try { wm.removeView(overlay); } catch (Exception ignored2) {} }
        }
        overlay = null; showing = false;
    }

    private void allowNow(String pkg, long mins) {
        if (pkg != null) allowUntil.put(pkg, System.currentTimeMillis() + mins * 60_000L);
        hide();
    }

    private String label(String pkg) {
        try {
            android.content.pm.ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
            CharSequence l = getPackageManager().getApplicationLabel(ai);
            if (l != null) return l.toString();
        } catch (Exception ignored) {}
        return "that app";
    }

    // ---- overlay UI ----
    private LinearLayout buildOverlay(String pkg, long ms) {
        int[] theme = THEMES[Math.max(0, Math.min(THEMES.length - 1, Store.data.settings.guardTheme))];
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(26), dp(26), dp(26), dp(46));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{theme[0], theme[1], theme[2]});
        bg.setCornerRadius(0f);
        root.setBackground(bg);

        TextView title = txt("You've been scrolling", 22, Ink, monoBold);
        title.setGravity(Gravity.CENTER);
        TextView mins = txt(String.format(Locale.US, "%d min straight", ms / 60000L), 30, Ink, dot);
        mins.setGravity(Gravity.CENTER);
        TextView sub = txt(guardSub(), 13, InkSoft, mono);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(8), 0, dp(20));

        LinearLayout card = new LinearLayout(this);
        card.setTag("guardCard");
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(0xE6FFFFFF); cbg.setCornerRadius(dp(28)); cbg.setStroke(dp(1), LINE);
        card.setBackground(cbg);
        card.setPadding(dp(22), dp(22), dp(22), dp(22));

        card.addView(title);
        card.addView(mins);
        card.addView(sub);
        card.addView(btn("Breathe with me | 30s", Ink, Color.WHITE, () -> breathing()));
        card.addView(btn("Do a Spark instead", Ink, Color.WHITE, () -> { hide(); openApp(3); }));
        card.addView(btn("Move my body now", Ink, Color.WHITE, () -> { Store.addPing(); Toast2(); hide(); }));
        card.addView(btn("I need this app | 5 min", 0x00000000, Ink, () -> allowNow(pkg, Store.data.settings.guardAllowMin)));
        card.addView(btn("Close", 0x00000000, Ink, () -> allowNow(pkg, 2)));

        root.addView(card);
        return root;
    }

    private String guardSub() {
        Store.ensure(this);
        return "The Guardian is just a gentle nudge. Pick one, or keep going if you really need it.";
    }

    private TextView btn(String s, int fill, int txtColor, Runnable r) {
        TextView t = txt(s, 14, txtColor, monoBold);
        t.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(40)); bg.setColor(fill);
        if (fill == 0x00000000) bg.setStroke(dp(1), LINE);
        t.setBackground(bg);
        t.setPadding(dp(16), 0, dp(16), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.topMargin = dp(10);
        t.setLayoutParams(lp);
        if (r != null) t.setOnClickListener(v -> r.run());
        return t;
    }

    private void openApp(int tab) {
        Intent i = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.putExtra("tab", tab);
        try { startActivity(i); } catch (Exception ignored) {}
    }

    private void Toast2() {
        try {
            android.widget.Toast.makeText(this, "Spark! Your body moved.", android.widget.Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {}
    }

    // breathing overlay
    private void breathing() {
        if (overlay == null) return;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int[] theme = THEMES[Math.max(0, Math.min(THEMES.length - 1, Store.data.settings.guardTheme))];
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{theme[0], theme[1], theme[2]});
        root.setBackground(bg);

        TextView head = txt("Breathe with me", 18, Ink, monoBold);
        head.setGravity(Gravity.CENTER);
        root.addView(head);

        final TextView circle = new TextView(this);
        circle.setBackground(oval(0xEEFFFFFF));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(190), dp(190));
        clp.topMargin = dp(30); clp.bottomMargin = dp(30);
        root.addView(circle, clp);

        final TextView hint = txt("In...  out...", 18, Ink, mono);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint);

        final int[] count = {30};
        final android.os.Handler h = new android.os.Handler(getMainLooper());
        final Runnable tick = new Runnable() {
            public void run() {
                count[0]--;
                if (count[0] <= 0) { hide(); return; }
                String phase = count[0] % 8 < 4 ? "Breathe in" : "Breathe out";
                hint.setText(String.format(Locale.US, "%s | %ds", phase, count[0]));
                h.postDelayed(this, 1000);
            }
        };
        h.postDelayed(tick, 1000);

        // swap overlay content to the breathing screen
        overlay.removeAllViews();
        overlay.addView(root, new LinearLayout.LayoutParams(-1, -1));

        ValueAnimator anim = ValueAnimator.ofFloat(1f, 1.22f);
        anim.setDuration(4000);
        anim.setRepeatMode(ValueAnimator.REVERSE);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            circle.setScaleX(v); circle.setScaleY(v);
        });
        anim.start();
    }

    private GradientDrawable oval(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL); g.setColor(color); return g;
    }

    private TextView txt(String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setTypeface(tf);
        t.setLineSpacing(0, 1f);
        return t;
    }

    private int dp(int d) { return (int) (d * getResources().getDisplayMetrics().density); }
}
