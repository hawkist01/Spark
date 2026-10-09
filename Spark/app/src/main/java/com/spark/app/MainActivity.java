package com.spark.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.spark.app.data.Curiosity;
import com.spark.app.data.Dates;
import com.spark.app.data.GuardianApps;
import com.spark.app.data.Med;
import com.spark.app.data.Quest;
import com.spark.app.data.Settings;
import com.spark.app.data.Store;
import com.spark.app.data.WeeklyReport;
import com.spark.app.ui.ClockFaceView;
import com.spark.app.ui.SparkBackgroundView;
import com.spark.app.ui.Theme;
import com.spark.app.util.Export;
import com.spark.app.util.Scheduler;
import com.spark.app.util.SoundManager;
import com.spark.app.util.UsageStatsHelper;
import com.spark.app.util.Weekly;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_SOUND = 110;
    private static final int REQ_EXPORT = 111;
    private static final int REQ_MD = 112;
    private String pendingMd = null;
    private String pendingMdName = "spark_entries.md";

    // palette (mutated by dark mode, refreshed every rebuild)
    private int Ink, InkSoft, LINE, LABEL;

    private static final String[] WONDERS = {
            "Why do stars twinkle?",
            "If clouds had flavors, what would today's taste like?",
            "Why is the sky blue and not violet?",
            "Invent a tiny holiday. What do people do on it?",
            "Why does water freeze from the top?",
            "What would a museum about your childhood put in the first glass case?",
            "If colors had sounds, what does green sound like?",
            "Why does the moon have phases?",
            "You get one useless superpower. Make it sound epic.",
            "Why does a magnet pull iron but not wood?"
    };

    private FrameLayout content;
    /** Miso. Full-screen overlay, created once and reused across rebuilds. */
    private com.spark.app.ui.CatView cat;
    private View indicator;
    private int tab = 0;
    private int lastTab = 0;
    private Typeface dotFont, mono, monoBold;
    private TextView clock;
    private String curCard = Curiosity.daily();
    private String pendingSoundTask = null;
    private final List<TextView> navBtns = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        public void run() { if (clock != null) clock.setText(nowHm()); handler.postDelayed(this, 20000); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Store.init(this);
        Scheduler.rescheduleAll(this, Store.data);
        Theme.dark = Store.data.settings.darkMode;
        // Style must be applied before any view is built: the background layer
        // decides dot-grid vs frosted washes at construction time.
        Theme.style = Store.data.settings.themeStyle;
        Theme.night = isNightNow(Store.data.settings);
        dotFont = getResources().getFont(R.font.dot_gothic16);
        mono = getResources().getFont(R.font.space_mono);
        monoBold = getResources().getFont(R.font.space_mono_bold);
        int t = getIntent().getIntExtra("tab", -1);
        if (t >= 0 && t <= 5) tab = t;
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        // Android 14+ exact-alarm gate: without this, seed/med/ping fall back to inexact.
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
                if (am != null && !am.canScheduleExactAlarms()) {
                    try { startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)); }
                    catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }
        setContentView(buildRoot());
        rebuild();
        // Android 13+ predictive back: route back gestures through us so the
        // edge-swipe closes a detail / returns to Tasks instead of killing the app.
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                        android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                        this::handleBack);
            } catch (Exception ignored) {}
        }
    }

    // Back = go inward, not quit. Close detail -> Tasks tab -> background.
    private void handleBack() {
        if (inDetail) { closeDetail(); return; }
        if (tab != 0) {
            inDetail = false; detailBuilder = null; tab = 0; rebuild();
            return;
        }
        try { moveTaskToBack(true); } catch (Exception ignored) { finish(); }
    }

    private void applyPalette() {
        Ink = Theme.ink(); InkSoft = Theme.inkSoft(); LINE = Theme.line(); LABEL = Theme.label();
    }

    private void applySystemBars() {
        Window w = getWindow();
        if (w == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                // Modern edge-to-edge (Android 11-17): draw behind transparent bars.
                w.setDecorFitsSystemWindows(false);
                w.setStatusBarColor(Color.TRANSPARENT);
                w.setNavigationBarColor(Color.TRANSPARENT);
                if (Build.VERSION.SDK_INT >= 29) w.setNavigationBarContrastEnforced(false);
                android.view.WindowInsetsController ic = w.getInsetsController();
                if (ic != null) {
                    int app = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    if (!Theme.dark) ic.setSystemBarsAppearance(app, app);
                    else ic.setSystemBarsAppearance(0, app);
                }
            } else {
                w.setStatusBarColor(Theme.bg());
                w.setNavigationBarColor(Theme.bg());
                View d = w.getDecorView();
                int flags = d.getSystemUiVisibility();
                flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
                int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= 26) light |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                if (!Theme.dark) flags |= light; else flags &= ~light;
                d.setSystemUiVisibility(flags);
            }
        } catch (Exception ignored) {}
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        int t = i.getIntExtra("tab", -1);
        if (t >= 0 && t <= 5) { tab = t; inDetail = false; detailBuilder = null; rebuild(); }
    }

    @Override protected void onResume() {
        super.onResume();
        // Refresh counts/dots after notification actions (Done/Snooze happen
        // in the shade while we are paused).
        try { Store.resetDailyIfNewDay(this); rebuild(); } catch (Exception ignored) {}
        handler.post(ticker);
    }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(ticker); }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_SOUND && res == RESULT_OK && data != null && data.getData() != null) {
            Uri u = data.getData();
            try { getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
            String task = pendingSoundTask != null ? pendingSoundTask : "spark";
            SoundManager.setCustom(this, task, u.toString());
            toast("Sound set for " + SoundManager.label(task) + ".");
            pendingSoundTask = null;
        }
        if (req == REQ_EXPORT && res == RESULT_OK && data != null && data.getData() != null) {
            boolean ok = Export.writeToUri(this, data.getData());
            toast(ok ? "Backup saved where you picked it." : "Couldn't write there.");
        }
        if (req == REQ_MD && res == RESULT_OK && data != null && data.getData() != null) {
            boolean ok = Export.writeTextToUri(this, data.getData(),
                    pendingMd != null ? pendingMd : "");
            pendingMd = null;
            toast(ok ? "Journal saved as Markdown." : "Couldn't write there.");
        }
    }

    // ---------- root ----------
    private View buildRoot() {
        FrameLayout root = new FrameLayout(this);
        root.addView(new SparkBackgroundView(this, null), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        try { shell.setFitsSystemWindows(true); } catch (Exception ignored) {}
        content = new SwipeFrame(this);
        shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        shell.addView(buildNav());
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        /* Miso lives ABOVE the interface, not inside a card.
           She is added last so she draws over everything, measures to the whole
           window, and roams in absolute coordinates. CatView refuses touches
           that miss her body, so the UI underneath still receives every tap -
           that is what makes a full-screen overlay safe here. */
        if (cat == null) cat = new com.spark.app.ui.CatView(this, null);
        cat.applyPalette();
        root.addView(cat, new FrameLayout.LayoutParams(-1, -1));
        return root;
    }

    private class SwipeFrame extends FrameLayout {
        private float downX, downY;
        private boolean dragging;

        SwipeFrame(Context c) { super(c); }

        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX(); downY = e.getY(); dragging = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getX() - downX, dy = e.getY() - downY;
                    if (!dragging && Math.abs(dx) > dp(22) && Math.abs(dx) > Math.abs(dy) * 1.4f) {
                        dragging = true;
                        View cur = getChildAt(0);
                        if (cur != null) { cur.animate().cancel(); return true; }
                    }
                    break;
            }
            return dragging;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getX() - downX;
                    if ((dx > 0 && tab == 0) || (dx < 0 && tab == 5)) dx *= 0.32f;
                    View cur = getChildAt(0);
                    if (cur != null) cur.setTranslationX(dx);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    View cur = getChildAt(0);
                    float dx = e.getX() - downX;
                    if (cur != null) {
                        float w = Math.max(1, getWidth());
                        boolean edge = (dx > 0 && tab == 0) || (dx < 0 && tab == 5);
                        if (!edge && Math.abs(dx) > w * 0.26f) {
                            int nt = dx < 0 ? tab + 1 : tab - 1;
                            cur.animate().translationX(dx > 0 ? w : -w).setDuration(150).start();
                            final int target = nt;
                            postDelayed(() -> { tab = target; inDetail = false; detailBuilder = null; rebuild(); }, 110);
                        } else {
                            cur.animate().translationX(0f).setDuration(210)
                                    .setInterpolator(new android.view.animation.OvershootInterpolator(0.8f)).start();
                        }
                    }
                    dragging = false;
                    return true;
                }
            }
            return super.onTouchEvent(e);
        }
    }

    private View buildNav() {
        FrameLayout navWrap = new FrameLayout(this);
        navWrap.setPadding(dp(14), 0, dp(14), dp(10));
        // Frosted pill bar, like the NOS 5.0 bottom search pill.
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable barBg = new GradientDrawable();
        barBg.setCornerRadius(dp(30));
        barBg.setColor(Theme.navGlass());
        barBg.setStroke(dp(1), Theme.cardLine());
        bar.setBackground(barBg);
        try { bar.setElevation(dp(6)); } catch (Exception ignored) {}
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(4), dp(8), dp(4));
        String[] labels = {"Tasks", "Week", "Daily", "Wonder", "Guide"};
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView btn = new TextView(this);
            btn.setText(labels[i]);
            btn.setTextSize(12);
            btn.setTextColor(Ink);
            btn.setTypeface(monoBold);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(0, dp(10), 0, dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(30));
            bg.setColor(0x00000000);
            btn.setBackground(bg);
            btn.setOnClickListener(v -> { if (tab != idx) { tab = idx; selectTab(tab); } });
            nav.addView(btn, new LinearLayout.LayoutParams(0, dp(44), 1f));
            navBtns.add(btn);
        }
        // sliding indicator pill under the selected tab
        indicator = new View(this);
        GradientDrawable ig = new GradientDrawable();
        ig.setCornerRadius(dp(2));
        ig.setColor(Theme.accent());
        indicator.setBackground(ig);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(28), dp(3), Gravity.BOTTOM | Gravity.START);
        ilp.leftMargin = dp(14);
        ilp.bottomMargin = dp(14);
        indicator.setLayoutParams(ilp);
        bar.addView(nav, new LinearLayout.LayoutParams(-1, -2));
        navWrap.addView(bar, new FrameLayout.LayoutParams(-1, -2));
        navWrap.addView(indicator);
        navWrap.post(this::moveIndicator);
        return navWrap;
    }

    private void moveIndicator() {
        if (indicator == null || navBtns.size() != 6 || content == null) return;
        View target = navBtns.get(tab);
        int x = (int) (dp(14) + target.getX() + target.getWidth() / 2f - dp(14));
        indicator.animate().x(x).setDuration(260)
                .setInterpolator(new android.view.animation.OvershootInterpolator(0.9f)).start();
    }

    private void rebuild() {
        applyPalette();
        applySystemBars();
        if (inDetail && detailBuilder != null) {
            renderDetail();
            tintNav();
            return;
        }
        content.removeAllViews();
        View v = screenView(tab);
        content.addView(v, new FrameLayout.LayoutParams(-1, -1));
        int dir = tab >= lastTab ? 1 : -1;
        /* Entrance animations belong to CHANGING SCREENS.
           Every counter tap (water, sleep, med taken, a delete, a toggle) calls
           rebuild(), and each one was replaying the full tab entrance: content
           flew out, faded to zero and sprang back in. Thirty-nine call sites
           meant the whole app looked like it was resetting itself on every
           touch. Rebuilding the SAME screen now builds it plainly, so a tap
           just updates the number. */
        boolean animate = (tab != lastTab);
        if (animate) {
        switch (tab) {
            case 0: // Seed — glyph breathe: rise like charging dots, soft fade
                v.setAlpha(0f);
                v.setTranslationY(dp(34));
                v.setTranslationX(0f);
                v.animate().alpha(1f).translationY(0f).setDuration(380)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f)).start();
                break;
            case 1: // Compass — directional slide, dot-matrix march
                v.setAlpha(0f);
                v.setTranslationY(0f);
                v.setTranslationX(dp(dir * 64));
                v.animate().alpha(1f).translationX(0f).setDuration(340)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(0.7f)).start();
                break;
            case 2: // Body — energetic pop
                v.setAlpha(0f);
                v.setTranslationX(0f);
                v.setTranslationY(0f);
                v.setScaleX(0.96f); v.setScaleY(0.96f);
                v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(360)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f)).start();
                break;
            default: // Wonder — dreamy fade + settle
                v.setAlpha(0f);
                v.setTranslationX(0f);
                v.setScaleX(0.98f); v.setScaleY(0.98f);
                v.setTranslationY(dp(14));
                v.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(420)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
                break;
        }
        // staggered card entrance, varied per tab
        if (animate && v instanceof ScrollView) {
            View col = ((ScrollView) v).getChildAt(0);
            if (col instanceof LinearLayout) {
                LinearLayout c = (LinearLayout) col;
                for (int i = 0; i < c.getChildCount(); i++) {
                    View ch = c.getChildAt(i);
                    long delay = 40 + i * 45;
                    if (tab == 2) { // Body: pop each card like a heartbeat
                        ch.setAlpha(0f);
                        ch.setScaleX(0.93f); ch.setScaleY(0.93f);
                        ch.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(delay)
                                .setDuration(300)
                                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
                                .start();
                    } else if (tab == 1) { // Compass: march in from the side
                        ch.setAlpha(0f);
                        ch.setTranslationX(dp(dir * 40));
                        ch.animate().alpha(1f).translationX(0f).setStartDelay(delay)
                                .setDuration(300)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                                .start();
                    } else if (tab == 3) { // Wonder: slow bloom
                        ch.setAlpha(0f);
                        ch.setScaleX(0.97f); ch.setScaleY(0.97f);
                        ch.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(delay)
                                .setDuration(340)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator(2.0f))
                                .start();
                    } else { // Seed: charging dots rise
                        ch.setAlpha(0f);
                        ch.setTranslationY(dp(30));
                        ch.animate().alpha(1f).translationY(0f).setStartDelay(delay)
                                .setDuration(320)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                                .start();
                    }
                }
            }
        }
        }
        for (int i = 0; i < navBtns.size(); i++) {
            TextView b = navBtns.get(i);
            b.setTextColor(tab == i ? Ink : InkSoft);
        }
        moveIndicator();
        lastTab = tab;
    }

    private void tintNav() {
        for (int i = 0; i < navBtns.size(); i++) {
            TextView b = navBtns.get(i);
            b.setTextColor(tab == i ? Ink : InkSoft);
        }
        moveIndicator();
    }

    private void selectTab(int t) { inDetail = false; detailBuilder = null; rebuild(); }

    private View screenView(int t) {
        switch (t) {
            case 1: return weekView();
            case 2: return dailyView();
            case 3: return wonderView();
            case 4: return guideView();
            case 5: return menuView();
            default: return tasksView();
        }
    }

    // ---------- tasks: one clean hub (add, time, remove, cue, notes) ----------
    private View tasksView() {
        ScrollView sv = scroller();
        LinearLayout col = col(sv);
        col.addView(section("Today  |  " + Dates.todayLong()));
        col.addView(taskSpotlight());
        col.addView(gap(6));
        col.addView(section("All tasks"));
        if (Store.data.quests.isEmpty()) {
            LinearLayout hint = card();
            hint.addView(text("A clean page. Create your first task below — give it a time, a sound, and an if-then plan. Or steal one from the Guide tab.", 14, InkSoft, mono));
            col.addView(hint);
        }
        for (Quest q : Store.data.quests) col.addView(questRow(q));
        col.addView(gap(6));
        LinearLayout row = row();
        row.addView(pill("+ New task", Theme.accent(), Color.WHITE, () -> showQuestDialog(null)));
        row.addView(pill("Thought dump", 0x00000000, Ink, this::showCaptureDialog));
        col.addView(row);
        col.addView(gap(60));
        return sv;
    }

    private View taskSpotlight() {
        LinearLayout c = card();
        Quest q = Store.firstPending();
        if (q == null) {
            c.addView(text("All clear *", 17, Ink, monoBold));
            c.addView(gap(4));
            c.addView(text("Add a fresh task below when something matters.", 13, InkSoft, mono));
            return c;
        }
        c.addView(tinyLabel("Up next"));
        c.addView(gap(4));
        c.addView(text(q.title, 18, Ink, monoBold));
        c.addView(gap(10));
        c.addView(tinyLabel("2-minute step"));
        c.addView(text(q.tiny, 15, Ink, mono));
        c.addView(gap(8));
        c.addView(tinyLabel("15-minute deep-dive"));
        c.addView(text(q.deep, 15, InkSoft, mono));
        if (q.cue != null && !q.cue.isEmpty()) {
            c.addView(gap(8));
            c.addView(text("Plan: " + q.cue, 13, InkSoft, mono));
        }
        if (q.remindAt != null && !q.remindAt.isEmpty()) {
            c.addView(gap(4));
            c.addView(text("Reminds at " + ClockFaceView.fmt(
                    Dates.parseHm(q.remindAt)[0], Dates.parseHm(q.remindAt)[1]), 13, InkSoft, mono));
        }
        c.addView(gap(16));
        com.spark.app.ui.FlowLayout row = flowRow();
        row.addView(pill("Did the 2-min", Theme.accent(), Color.WHITE, () -> {
            Store.markQuestDone(q.id); toast("A spark. Good.");
            successPulse(c);
            content.postDelayed(this::rebuild, 180);
        }));
        row.addView(pill("Open", 0x00000000, Ink, () -> openDetail(() -> questDetail(q.id))));
        row.addView(pill("Snooze", 0x00000000, Ink, () -> {
            Scheduler.snooze(this, "seed"); toast("Gentle nudges pushed " + Store.data.settings.snoozeMin + " min.");
        }));
        c.addView(row);
        return c;
    }

    private View wonderCard() {
        LinearLayout c = card();
        c.addView(text("A 3-minute spark", 13, InkSoft, monoBold));
        c.addView(gap(6));
        c.addView(text(curCard, 17, Ink, mono));
        c.addView(gap(14));
        LinearLayout row = row();
        row.addView(pill("Give it 3 min", Theme.accent(), Color.WHITE, this::showCuriosity));
        row.addView(pill("New one", 0x00000000, Ink, () -> {
            String c0 = curCard; int tries = 0;
            do { curCard = Curiosity.random(); tries++; } while (curCard.equals(c0) && tries < 8);
            rebuild();
        }));
        c.addView(row);
        return c;
    }

    private View guardianCard() {
        LinearLayout c = card();
        c.addView(text("Guardian", 13, InkSoft, monoBold));
        c.addView(gap(6));
        boolean a = overlayAllowed(), g = guardianServiceEnabled();
        c.addView(text((a ? "[x]" : "[ ]") + "  draw over apps      " + (a ? "" : "(enable it)"), 13, a ? Ink : InkSoft, mono));
        c.addView(text((g ? "[x]" : "[ ]") + "  Guardian service     " + (!g ? "(enable it)" : ""), 13, g ? Ink : InkSoft, mono));
        c.addView(gap(12));
        LinearLayout row = row();
        if (!a) row.addView(pill("Allow overlay", Theme.accent(), Color.WHITE, this::openOverlaySettings));
        if (!g) row.addView(pill("Enable Guardian", Theme.accent(), Color.WHITE, this::openAccessibilitySettings));
        else row.addView(pill("Settings", 0x00000000, Ink, this::showGuardianDialog));
        row.addView(pill("Apps (" + Store.data.guardedApps.size() + ")", 0x00000000, Ink, this::showGuardianAppsDialog));
        c.addView(row);
        return c;
    }

    private View captureCard() {
        LinearLayout c = card();
        c.addView(text("Thought capture", 13, InkSoft, monoBold));
        c.addView(gap(4));
        c.addView(text("A thought spinning in your head? Dump it here so it stops looping.", 13, InkSoft, mono));
        c.addView(gap(10));
        c.addView(pill("Dump it now", Theme.accent(), Color.WHITE, this::showCaptureDialog));
        return c;
    }

    private View exportCard() {
        LinearLayout c = card();
        c.addView(text("Backup & export", 13, InkSoft, monoBold));
        c.addView(gap(4));
        c.addView(text("Everything lives on this phone. Mail it to your Gmail, copy it, or save the file to Downloads / Drive.", 13, InkSoft, mono));
        c.addView(gap(10));
        LinearLayout row = row();
        row.addView(pill("To Gmail", Theme.accent(), Color.WHITE, () -> {
            try { startActivity(Intent.createChooser(Export.gmailIntent(this), "Send backup")); }
            catch (Exception e) { toast("No email app found - copied to clipboard instead."); Export.toClipboard(this); }
        }));
        row.addView(pill("Copy", 0x00000000, Ink, () -> { Export.toClipboard(this); toast("Backup copied - paste into Drive/Keep/Notes."); }));
        row.addView(pill("Save as file", 0x00000000, Ink, this::createExportDoc));
        c.addView(row);
        return c;
    }

    private void createExportDoc() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TITLE, "spark_backup.txt");
        try { startActivityForResult(i, REQ_EXPORT); } catch (Exception e) { toast("Couldn't open the file picker."); }
    }

    private View bodyCard() {
        LinearLayout c = card();
        c.addView(text("Body & movement", 13, InkSoft, monoBold));
        c.addView(gap(10));
        LinearLayout p1 = row();
        p1.addView(text("Body pings", 14, Ink, mono), new LinearLayout.LayoutParams(0, -2, 1f));
        p1.addView(new DotRow(this, Store.data.pingsToday, 5, Ink, 0x55808080), new LinearLayout.LayoutParams(-2, dp(20)));
        p1.addView(text("  " + Store.data.pingsToday, 14, InkSoft, mono));
        c.addView(p1);
        c.addView(gap(8));
        LinearLayout p2 = row();
        p2.addView(text("Water", 14, Ink, mono), new LinearLayout.LayoutParams(0, -2, 1f));
        p2.addView(new DotRow(this, Store.data.waterToday, 5, 0xFF2E9BD6, 0x55808080), new LinearLayout.LayoutParams(-2, dp(20)));
        p2.addView(text("  " + Store.data.waterToday, 14, InkSoft, mono));
        c.addView(p2);
        c.addView(gap(16));
        LinearLayout row = row();
        row.addView(pill("Move now", Theme.accent(), Color.WHITE, () -> { Store.addPing(); toast("Spark! Body awake."); successPulse(c); content.postDelayed(this::rebuild, 180); }));
        row.addView(pill("+ water", 0x00000000, Ink, () -> { Store.addWater(); successPulse(c); content.postDelayed(this::rebuild, 180); }));
        row.addView(pill("Ping every " + Store.data.settings.pingIntervalMin + "m", 0x00000000, Ink, this::showSettings));
        c.addView(row);
        return c;
    }

    private View medsCard() {
        LinearLayout c = card();
        c.addView(text("Medication rhythm", 13, InkSoft, monoBold));
        c.addView(gap(4));
        for (Med m : Store.data.meds) {
            int taken = Store.data.takenToday.getOrDefault(m.id, 0);
            boolean all = m.times.size() > 0 && taken >= m.times.size();
            LinearLayout row = row();
            row.setPadding(0, dp(8), 0, dp(8));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.addView(text(m.name, 15, Ink, monoBold));
            info.addView(text(String.join(" | ", m.times) + (m.note.isEmpty() ? "" : "  |  " + m.note), 12, InkSoft, mono));
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(pill(all ? "Took" : "Take now", all ? Theme.ok() : Theme.accent(),
                    all ? Ink : Color.WHITE, () -> { Store.markMedTaken(m.id); Scheduler.rescheduleAfterChange(this); toast(m.name + " logged."); successPulse(c); content.postDelayed(this::rebuild, 180); }));
            c.addView(row);
        }
        c.addView(gap(8));
        c.addView(pill("+ Add medication", 0x00000000, Ink, () -> showMedDialog(null)));
        return c;
    }

    private View focusCard() {
        LinearLayout c = card();
        c.addView(text("Your focus today", 13, InkSoft, monoBold));
        c.addView(gap(6));
        if (!hasUsageAccess()) {
            c.addView(text("Turn on usage access to see how your screen time actually flows. Gentle, not a score.", 14, Ink, mono));
            c.addView(gap(10));
            c.addView(pill("Enable usage access", Theme.accent(), Color.WHITE, this::openUsageSettings));
        } else {
            UsageStatsHelper.UsageDay u = UsageStatsHelper.today(this);
            if (u != null) {
                c.addView(text("Screen time: " + u.totalMinutes + " min", 18, Ink, monoBold));
                for (String[] a : u.topApps) c.addView(text("-  " + a[0] + "  |  " + a[1] + " min", 14, InkSoft, mono));
                c.addView(gap(6));
                c.addView(text("Reframe: pick ONE interesting thing you saw and give it a 3-min spark above.", 13, InkSoft, mono));
            } else {
                c.addView(text("No screen data yet today. Keep going at your own pace.", 14, Ink, mono));
            }
        }
        return c;
    }

    // ---------- week: achievements, auto-updated every Saturday 21:00 ----------
    private View weekView() {
        ScrollView sv = scroller();
        LinearLayout col = col(sv);
        col.addView(section("This week"));
        col.addView(weekStrip());
        col.addView(gap(6));
        col.addView(weekScoreCard());
        col.addView(gap(6));
        col.addView(reflectionCard());
        col.addView(gap(6));
        col.addView(section("Past weeks"));
        if (Store.data.weeklyReports.isEmpty()) {
            LinearLayout c = card();
            c.addView(text("No weekend report yet. Every Saturday at 9pm the week scores itself — kindly.", 13, InkSoft, mono));
            col.addView(c);
        } else {
            List<WeeklyReport> reps = Store.data.weeklyReports;
            for (int i = Math.max(0, reps.size() - 4); i < reps.size(); i++) {
                WeeklyReport r = reps.get(i);
                LinearLayout c = card();
                c.addView(text(r.weekStart + "  |  " + r.score + " / 100", 15, Ink, monoBold));
                c.addView(gap(4));
                c.addView(text(r.summary, 13, InkSoft, mono));
                col.addView(c);
            }
        }
        col.addView(gap(60));
        return sv;
    }

    private View pillarDetail(String name, int score, String detail) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout c = card();
        c.addView(text(name + "  |  " + score, 20, Ink, monoBold));
        c.addView(gap(6));
        c.addView(text(detail, 14, InkSoft, mono));
        c.addView(gap(10));
        c.addView(text(pillarGuide(name), 14, Ink, mono));
        c.addView(gap(14));
        c.addView(pill("Log it in Daily", Theme.accent(), Color.WHITE, () -> {
            inDetail = false; detailBuilder = null; tab = 2; rebuild();
        }));
        body.addView(c);
        return detailShell("Week", body);
    }

    private String pillarGuide(String name) {
        if (name.equals("Food")) return "Regular meals steady blood sugar and mood. Eat without a screen when you can — attention is a nutrient too.";
        if (name.equals("Movement")) return "Brief movement raises dopamine and norepinephrine, the brain's own focus chemistry. Studies link even 2-10 minute bouts to better attention. Rule: never zero — 10 squats counts.";
        if (name.equals("Medication")) return "Consistency is the whole game with prescribed meds. Same time, same cue (after brushing teeth, with dinner). Never adjust doses without your doctor.";
        if (name.equals("Sleep")) return "Sleep regularity predicts health better than duration alone. Anchor a wind-down time, screens off 30 min before, aim 7-9 h. The wind-down reminder exists for exactly this.";
        if (name.equals("Phone rest")) return "Friction beats willpower: launch delays cut sessions ~11%, opt-out caps ~18%. The Guardian is friction with manners — a breath and a choice, not a lock.";
        return "Small, attached to an existing moment, daily. That is the whole method.";
    }

    private View weekScoreCard() {
        Weekly.Result r;
        try { r = com.spark.app.util.Weekly.compute(this); }
        catch (Exception e) { r = null; }
        LinearLayout c = card();
        c.addView(text("Weekend score (live)", 13, InkSoft, monoBold));
        c.addView(gap(4));
        if (r == null || r.pillars.isEmpty()) {
            c.addView(text("-- / 100", 40, Ink, dotFont));
            c.addView(gap(4));
            c.addView(text("Log a glass of water or a meal in Daily and this wakes up.", 13, InkSoft, mono));
        } else {
            c.addView(text(r.score + " / 100", 40, Ink, dotFont));
            c.addView(gap(8));
            for (Weekly.Pillar p : r.pillars) {
                final Weekly.Pillar fp = p;
                LinearLayout wrap = new LinearLayout(this);
                wrap.setOrientation(LinearLayout.VERTICAL);
                wrap.setClickable(true);
                wrap.setFocusable(true);
                LinearLayout prow = row();
                prow.addView(text(p.name, 13, Ink, mono), new LinearLayout.LayoutParams(0, -2, 1f));
                prow.addView(new DotRow(this, Math.round(p.score / 20f), 5, Ink, 0x55808080),
                        new LinearLayout.LayoutParams(-2, dp(20)));
                prow.addView(text("  " + p.score + "  >", 13, InkSoft, mono));
                wrap.addView(prow);
                wrap.addView(text(p.detail, 11, InkSoft, mono));
                wrap.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    openDetail(() -> pillarDetail(fp.name, fp.score, fp.detail));
                });
                c.addView(wrap);
                c.addView(gap(6));
            }
            if (r.studyMin > 0) {
                c.addView(text("Study focus: " + r.studyMin + " min this week", 13, Ink, monoBold));
                c.addView(gap(4));
            }
            c.addView(text(r.summary, 12, InkSoft, mono));
        }
        c.addView(gap(10));
        c.addView(text("Auto-updates every Saturday at 9pm. Untracked days are skipped, never punished.", 11, InkSoft, mono));
        return c;
    }

    private View weekStrip() {
        LinearLayout row = row();
        Calendar c = Calendar.getInstance();
        int diff = (c.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7;
        c.add(Calendar.DAY_OF_YEAR, -diff);
        SimpleDateFormat d = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String[] letters = {"M", "T", "W", "T", "F", "S", "S"};
        for (int i = 0; i < 7; i++) {
            String date = d.format(c.getTime());
            boolean any = false;
            for (Quest q : Store.data.quests) if (q.doneDays.contains(date)) { any = true; break; }
            LinearLayout day = new LinearLayout(this);
            day.setOrientation(LinearLayout.VERTICAL);
            day.setGravity(Gravity.CENTER);
            day.addView(text(letters[i], 12, InkSoft, mono));
            day.addView(new DotRow(this, any ? 1 : 0, 1, Ink, 0x55808080), new LinearLayout.LayoutParams(-2, dp(18)));
            row.addView(day, new LinearLayout.LayoutParams(0, -2, 1f));
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return row;
    }

    private View reflectionCard() {
        LinearLayout c = card();
        c.addView(text("What lit up this week?", 15, Ink, monoBold));
        c.addView(gap(6));
        c.addView(text(Store.data.reflection.isEmpty()
                ? "A one-sentence log. This is memory, not a report card."
                : Store.data.reflection, 14, Store.data.reflection.isEmpty() ? InkSoft : Ink, mono));
        c.addView(gap(10));
        c.addView(pill("Write / update", Theme.accent(), Color.WHITE, this::showReflectionDialog));
        return c;
    }

    // ---------- drill-down: tap a thing, it opens its own window ----------
    private boolean inDetail = false;
    private java.util.function.Supplier<View> detailBuilder = null;

    private void openDetail(java.util.function.Supplier<View> builder) {
        inDetail = true;
        detailBuilder = builder;
        renderDetail();
    }

    private void renderDetail() {
        if (detailBuilder == null) { inDetail = false; rebuild(); return; }
        content.removeAllViews();
        View v = detailBuilder.get();
        content.addView(v, new FrameLayout.LayoutParams(-1, -1));
        v.setAlpha(0f);
        v.setTranslationX(dp(64));
        v.animate().alpha(1f).translationX(0f).setDuration(300)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        if (v instanceof ScrollView) {
            View col = ((ScrollView) v).getChildAt(0);
            if (col instanceof LinearLayout) {
                LinearLayout c = (LinearLayout) col;
                for (int i = 0; i < c.getChildCount(); i++) {
                    View ch = c.getChildAt(i);
                    ch.setAlpha(0f);
                    ch.setTranslationY(dp(24));
                    ch.animate().alpha(1f).translationY(0f).setStartDelay(40 + i * 45)
                            .setDuration(300)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                            .start();
                }
            }
        }
    }

    private void closeDetail() {
        inDetail = false;
        detailBuilder = null;
        rebuild();
    }

    @Override public void onBackPressed() {
        if (Build.VERSION.SDK_INT < 33) handleBack();
        else super.onBackPressed();
    }

    private View detailShell(String title, LinearLayout body) {
        ScrollView sv = scroller();
        LinearLayout col = col(sv);
        LinearLayout top = row();
        top.addView(pill("< Back", 0x00000000, Ink, this::closeDetail, 104, 44));
        top.addView(text(title, 15, Ink, monoBold));
        col.addView(top);
        col.addView(gap(10));
        for (int i = 0; i < body.getChildCount(); i++) {
            View ch = body.getChildAt(i);
            body.removeViewAt(i);
            col.addView(ch);
            i--;
        }
        col.addView(gap(60));
        return sv;
    }

    // Compact task row: one glance, tap for the full window.
    private View questRow(Quest q) {
        LinearLayout c = card();
        c.setClickable(true);
        c.setFocusable(true);
        boolean done = q.doneToday(Dates.today());
        c.addView(text((done ? "[done]  " : "") + q.title, 16, Ink, monoBold));
        c.addView(gap(4));
        StringBuilder meta = new StringBuilder();
        if (q.remindAt != null && !q.remindAt.isEmpty()) {
            int[] hm = Dates.parseHm(q.remindAt);
            meta.append(ClockFaceView.fmt(hm[0], hm[1]));
        }
        if (q.cue != null && !q.cue.isEmpty()) {
            if (meta.length() > 0) meta.append("  |  ");
            String cue = q.cue.length() > 42 ? q.cue.substring(0, 42) + "..." : q.cue;
            meta.append(cue);
        }
        if (meta.length() == 0) meta.append("Tap to open");
        else meta.append("  >");
        c.addView(text(meta.toString(), 12, InkSoft, mono));
        final String qid = q.id;
        c.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            openDetail(() -> questDetail(qid));
        });
        return c;
    }

    private Quest findQuest(String qid) {
        for (Quest q : Store.data.quests) if (q.id.equals(qid)) return q;
        return null;
    }

    private View questDetail(String qid) {
        Quest q = findQuest(qid);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        if (q == null) {
            body.addView(text("That task is gone.", 15, InkSoft, mono));
            return detailShell("Task", body);
        }
        final Quest fq = q;
        LinearLayout c = card();
        boolean done = q.doneToday(Dates.today());
        c.addView(text(q.title.isEmpty() ? "(untitled task)" : q.title, 20, Ink, monoBold));
        c.addView(gap(8));
        if (!q.tiny.isEmpty()) {
            c.addView(tinyLabel("2-minute step"));
            c.addView(text(q.tiny, 15, Ink, mono));
            c.addView(gap(8));
        }
        if (!q.deep.isEmpty()) {
            c.addView(tinyLabel("15-minute deep-dive"));
            c.addView(text(q.deep, 15, InkSoft, mono));
            c.addView(gap(8));
        }
        if (q.cue != null && !q.cue.isEmpty()) {
            c.addView(tinyLabel("If-then plan"));
            c.addView(text(q.cue, 14, Ink, mono));
            c.addView(gap(8));
        }
        c.addView(tinyLabel("Reminder"));
        String rem = (q.remindAt == null || q.remindAt.isEmpty()) ? "No time set"
                : "Daily at " + ClockFaceView.fmt(Dates.parseHm(q.remindAt)[0], Dates.parseHm(q.remindAt)[1]);
        c.addView(text(rem + "  |  Sound: " + SoundManager.label(
                fq.sound == null || fq.sound.isEmpty() ? "spark" : fq.sound), 14, InkSoft, mono));
        c.addView(gap(16));
        com.spark.app.ui.FlowLayout actions = flowRow();
        actions.addView(pill(done ? "Done today" : "Did the 2-min", done ? Theme.ok() : Theme.accent(),
                done ? Ink : Color.WHITE, () -> {
                    Store.markQuestDone(fq.id); toast("A spark. Good.");
                    successPulse(c);
                    content.postDelayed(() -> renderDetail(), 180);
                }));
        actions.addView(pill("Notes", 0x00000000, Ink, () -> showQuestNotes(fq)));
        actions.addView(pill("Time", 0x00000000, Ink, () -> showQuestTime(fq)));
        actions.addView(pill("Sound", 0x00000000, Ink, () -> showQuestSound(fq)));
        actions.addView(pill("Edit", 0x00000000, Ink, () -> showQuestDialog(fq)));
        actions.addView(pill("Delete", 0x00000000, Ink, () -> {
            Store.deleteQuest(fq.id);
            Scheduler.cancelQuest(this, fq.id);
            toast("Task removed, reminder cleared.");
            closeDetail();
        }));
        c.addView(actions);
        body.addView(c);
        if (q.notes != null && !q.notes.isEmpty()) {
            LinearLayout n = card();
            n.addView(tinyLabel("Notes"));
            n.addView(text(q.notes, 14, Ink, mono));
            body.addView(n);
        }
        return detailShell("Task", body);
    }

    private void showQuestSound(final Quest q) {
        LinearLayout box = dialogBox();
        box.addView(text("Which chime reminds you of this task?", 13, InkSoft, mono));
        box.addView(gap(8));
        final String[] keys = {"spark", "wonder", "med", "jog", "sleep", "doom"};
        final String[] picked = {q.sound == null || q.sound.isEmpty() ? "spark" : q.sound};
        final com.spark.app.ui.FlowLayout flow = flowRow();
        final TextView[] pills = new TextView[keys.length];
        for (int i = 0; i < keys.length; i++) {
            final String k = keys[i];
            TextView p = pill(SoundManager.label(k), k.equals(picked[0]) ? Theme.accent() : 0x00000000,
                    k.equals(picked[0]) ? Color.WHITE : Ink, null);
            final int idx = i;
            p.setOnClickListener(v -> {
                picked[0] = k;
                for (int j = 0; j < keys.length; j++) {
                    GradientDrawable g = (GradientDrawable) pills[j].getBackground();
                    boolean on = keys[j].equals(k);
                    g.setColor(on ? Theme.accent() : 0x00000000);
                    if (!on) g.setStroke(dp(1), LINE);
                    pills[j].setTextColor(on ? Color.WHITE : Ink);
                }
                SoundManager.preview(this, SoundManager.resolve(this, k));
            });
            pills[i] = p;
            flow.addView(p);
        }
        box.addView(flow);
        AlertDialog d = alert("Task sound", box)
                .setPositiveButton("Save", (dd, ww) -> {
                    q.sound = picked[0];
                    Store.upsertQuest(q);
                    Scheduler.rescheduleAfterChange(this);
                    toast("Sound set: " + SoundManager.label(picked[0]) + ".");
                    if (inDetail) renderDetail(); else rebuild();
                }).setNegativeButton("Cancel", null).show();
        styleDialog(d);
    }

    // Visual clock dial for one task's reminder. Dial pops in, spring-settles.
    private void showQuestTime(final Quest q) {
        int h = 9, m = 0;
        boolean has = q.remindAt != null && !q.remindAt.isEmpty();
        if (has) { int[] hm = Dates.parseHm(q.remindAt); h = hm[0]; m = hm[1]; }
        final int[] picked = {h, m};
        final boolean[] want = {true};
        LinearLayout box = dialogBox();
        box.addView(text("Tap the dial or HOUR / MIN, then AM / PM. Daily reminder.", 12, InkSoft, mono));
        box.addView(gap(8));
        final TextView[] readout = new TextView[1];
        final ClockFaceView[] face = new ClockFaceView[1];
        final TextView[] hourBtn = new TextView[1];
        final TextView[] minBtn = new TextView[1];
        final TextView[] amBtn = new TextView[1];
        final TextView[] pmBtn = new TextView[1];
        // Build with the shared dial builder, then Save wires to this quest.
        showTimePickerInto(box, "Task time", picked[0], picked[1], readout, face,
                hourBtn, minBtn, amBtn, pmBtn, (nh, nm) -> { picked[0] = nh; picked[1] = nm; });
        AlertDialog.Builder b = alert("Task time", box);
        if (has) b.setNeutralButton("Remove", (d, w) -> {
            q.remindAt = "";
            Store.upsertQuest(q);
            Scheduler.rescheduleAfterChange(this);
            toast("Reminder removed.");
            rebuild();
        });
        b.setPositiveButton("Set", (d, w) -> {
            q.remindAt = Dates.hhmm(picked[0], picked[1]);
            Store.upsertQuest(q);
            Scheduler.rescheduleAfterChange(this);
            toast("Reminds daily at " + ClockFaceView.fmt(picked[0], picked[1]) + ".");
            rebuild();
        }).setNegativeButton("Cancel", null);
        AlertDialog dlg = b.show();
        styleDialog(dlg);
        springShow(box);
    }

    // ---------- daily: water, food, movement, sleep, meds, study, phone rest ----------
    private View dailyView() {
        ScrollView sv = scroller();
        LinearLayout col = col(sv);
        col.addView(section("Today's essentials"));
        col.addView(essentialsCard());
        col.addView(gap(6));
        col.addView(section("Study focus"));
        col.addView(studyCard());
        col.addView(gap(6));
        col.addView(section("Movement pings"));
        LinearLayout pingCard = card();
        pingCard.addView(text("A gentle nudge every " + Store.data.settings.pingIntervalMin + " minutes during your awake window.", 14, Ink, mono));
        pingCard.addView(gap(8));
        LinearLayout pv = new LinearLayout(this);
        pv.setOrientation(LinearLayout.HORIZONTAL);
        int[] intervals = {45, 60, 90, 120};
        for (int i : intervals) {
            final int iv = i;
            boolean active = Store.data.settings.pingIntervalMin == i;
            TextView seg = new TextView(this);
            seg.setText(i + "m");
            seg.setTextSize(13);
            seg.setTypeface(monoBold);
            seg.setTextColor(active ? Color.WHITE : Ink);
            seg.setGravity(Gravity.CENTER);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(24));
            bg.setColor(active ? Theme.accent() : 0x00000000);
            if (!active) bg.setStroke(dp(1), LINE);
            seg.setBackground(bg);
            seg.setPadding(dp(14), 0, dp(14), 0);
            seg.setOnClickListener(v -> { Store.data.settings.pingIntervalMin = iv; Store.persist(); Scheduler.rescheduleAfterChange(this); rebuild(); });
            pv.addView(seg, new LinearLayout.LayoutParams(0, dp(46), 1f));
            pv.addView(gap(4));
        }
        pingCard.addView(pv);
        pingCard.addView(gap(8));
        LinearLayout prow = row();
        prow.addView(pill(Store.data.settings.pingEnabled ? "Pings ON" : "Pings OFF", Store.data.settings.pingEnabled ? Theme.accent() : 0x00000000,
                Store.data.settings.pingEnabled ? Color.WHITE : Ink, () -> { Store.data.settings.pingEnabled = !Store.data.settings.pingEnabled; Store.persist(); Scheduler.rescheduleAfterChange(this); rebuild(); }));
        prow.addView(pill("Test ping", 0x00000000, Ink, () -> com.spark.app.util.Notifier.ping(this, "10 squats, a stretch, or a glass of water.")));
        pingCard.addView(prow);
        col.addView(pingCard);
        col.addView(gap(6));

        col.addView(section("Medications"));
        LinearLayout mc = card();
        if (Store.data.meds.isEmpty()) {
            mc.addView(text("No medications yet. Add yours with its own times — each gets its own sound.", 14, InkSoft, mono));
            mc.addView(gap(8));
        }
        for (Med m : Store.data.meds) {
            LinearLayout row = row();
            row.setPadding(0, dp(8), 0, dp(8));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.addView(text(m.name, 15, Ink, monoBold));
            info.addView(text(String.join(" | ", m.times), 12, InkSoft, mono));
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(pill("Edit", 0x00000000, Ink, () -> showMedDialog(m)));
            row.addView(pill("x", 0x00000000, Ink, () -> { Store.deleteMed(m.id); Scheduler.rescheduleAfterChange(this); rebuild(); }, 44, 44));
            mc.addView(row);
        }
        mc.addView(gap(8));
        mc.addView(pill("+ Add medication", 0x00000000, Ink, () -> showMedDialog(null)));
        col.addView(mc);
        col.addView(gap(6));
        col.addView(section("Phone rest"));
        col.addView(restCard());
        col.addView(gap(60));
        return sv;
    }

    private View essentialsCard() {
        LinearLayout c = card();
        c.addView(essentialRow("Water", Store.data.waterToday + " / 5 glasses",
                () -> { Store.addWater(); rebuild(); }));
        c.addView(essentialRow("Food", Store.data.foodToday + " / 3 meals",
                () -> { Store.addFood(); rebuild(); }));
        c.addView(essentialRow("Movement", Store.data.pingsToday + " moments",
                () -> { Store.addPing(); toast("Spark! Body awake."); successPulse(c); content.postDelayed(this::rebuild, 180); }));
        // sleep stepper
        LinearLayout srow = row();
        srow.setPadding(0, dp(8), 0, dp(8));
        srow.addView(text("Sleep", 15, Ink, monoBold), new LinearLayout.LayoutParams(0, -2, 1f));
        srow.addView(pill("-", 0x00000000, Ink, () -> {
            Store.setSleepHalf(Store.data.sleepHalfToday - 1); rebuild();
        }, 48, 44));
        String sh = Store.data.sleepHalfToday <= 0 ? "--"
                : String.format(java.util.Locale.US, "%.1f h", Store.data.sleepHalfToday / 2.0);
        TextView mid = text(sh, 15, Ink, mono);
        mid.setGravity(Gravity.CENTER);
        mid.setMinWidth(dp(64));
        srow.addView(mid);
        srow.addView(pill("+", 0x00000000, Ink, () -> {
            Store.setSleepHalf(Store.data.sleepHalfToday + 1); rebuild();
        }, 48, 44));
        c.addView(srow);
        c.addView(gap(4));
        c.addView(text("Tap + for each glass, meal, or moment. Sleep in half hours.", 11, InkSoft, mono));
        return c;
    }

    private View essentialRow(String label, String value, Runnable onAdd) {
        LinearLayout r = row();
        r.setPadding(0, dp(8), 0, dp(8));
        r.addView(text(label, 15, Ink, monoBold), new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(text(value, 13, InkSoft, mono));
        r.addView(pill("+", Theme.accent(), Color.WHITE, onAdd, 52, 44));
        return r;
    }

    private View studyCard() {
        LinearLayout c = card();
        boolean studying = Store.studying();
        int mins = Store.data.studyMinToday + Store.studyElapsedMin();
        c.addView(text(studying ? "Studying now..." : "Study mode", 15, Ink, monoBold));
        c.addView(gap(4));
        c.addView(text(studying
                ? "Do Not Disturb is on. Your minutes are counting."
                : "One tap: silences pings, starts the timer. Nothing's own Study Mode counts too if you tap here as well — Android keeps OEM modes private, so this is how the week learns.",
                13, InkSoft, mono));
        c.addView(gap(6));
        c.addView(text("Today: " + mins + " min", 18, Ink, monoBold));
        c.addView(gap(10));
        LinearLayout r = row();
        r.addView(pill(studying ? "Stop + log" : "Start studying",
                Theme.accent(), Color.WHITE, () -> {
                    if (Store.studying()) stopStudySession();
                    else startStudySession();
                    rebuild();
                }));
        c.addView(r);
        return c;
    }

    private void startStudySession() {
        try {
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && !nm.isNotificationPolicyAccessGranted()) {
                toast("Allow Do Not Disturb access so study stays quiet.");
                try { startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)); }
                catch (Exception ignored) {}
            } else if (nm != null) {
                try { nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS); }
                catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        Store.startStudySession();
        toast("Studying. Alarms still ring, pings stay quiet.");
    }

    private void stopStudySession() {
        int mins = Store.stopStudySession();
        try {
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                try { nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL); }
                catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        toast("Logged " + mins + " min of focus. Gentle work.");
    }

    private View restCard() {
        LinearLayout c = card();
        if (!hasUsageAccess()) {
            c.addView(text("Turn on usage access to see screen vs rest hours. Gentle, not a score.", 14, Ink, mono));
            c.addView(gap(10));
            c.addView(pill("Enable usage access", Theme.accent(), Color.WHITE, this::openUsageSettings));
        } else {
            UsageStatsHelper.UsageDay u = UsageStatsHelper.today(this);
            if (u != null) {
                double rest = Math.max(0, 24 - u.totalMinutes / 60.0);
                c.addView(text("Screen: " + u.totalMinutes + " min", 18, Ink, monoBold));
                c.addView(text(String.format(java.util.Locale.US, "Rest: %.1f h", rest), 14, InkSoft, mono));
                for (String[] a : u.topApps) c.addView(text("-  " + a[0] + "  |  " + a[1] + " min", 14, InkSoft, mono));
            } else {
                c.addView(text("No screen data yet today. Keep going at your own pace.", 14, Ink, mono));
            }
        }
        return c;
    }

    // ---------- wonder: Miso the cat + journal that saves to the phone ----------
    private View wonderView() {
        ScrollView sv = scroller();
        LinearLayout col = col(sv);
        col.addView(section("Miso is here"));
        // She is not in this card any more - she is loose on top of the whole
        // app (see buildRoot). This is just the note that says so.
        col.addView(text("Miso is wandering somewhere on this screen. Tap her, or drag her around - she purrs.",
                13, InkSoft, mono));
        col.addView(gap(6));
        col.addView(section("Today's spark"));
        col.addView(wonderCard());
        col.addView(gap(6));

        col.addView(section("Your sparks"));
        LinearLayout sc = card();
        if (Store.data.wonderSparks.isEmpty() && Store.data.creativitySparks.isEmpty()) {
            sc.addView(text("Nothing logged yet. Every 3-minute spark you write lands here.", 14, InkSoft, mono));
        } else {
            List<String> list = new ArrayList<>(Store.data.wonderSparks);
            list.addAll(Store.data.creativitySparks);
            for (int i = Math.max(0, list.size() - 8); i < list.size(); i++) {
                sc.addView(text(list.get(i), 13, Ink, mono));
                sc.addView(gap(8));
            }
        }
        sc.addView(gap(4));
        com.spark.app.ui.FlowLayout jf = flowRow();
        jf.addView(pill("Write in journal", Theme.accent(), Color.WHITE, this::showJournalWrite));
        jf.addView(pill("Download .md", 0x00000000, Ink, () -> downloadEntriesMd(Dates.today())));
        sc.addView(jf);
        col.addView(sc);
        col.addView(gap(6));

        col.addView(section("Spark deck"));
        LinearLayout dc = card();
        for (String w : WONDERS) {
            LinearLayout row = row();
            row.addView(text(w, 14, Ink, mono), new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(pill("Try", 0x00000000, Ink, () -> showWonderExplain(w), 72, 44));
            dc.addView(row);
            dc.addView(gap(8));
        }
        col.addView(dc);
        col.addView(gap(60));
        return sv;
    }

    private void showJournalWrite() {
        LinearLayout box = dialogBox();
        box.addView(text("Ask Miso anything, or empty your head. Saved on this phone with today's date.", 13, InkSoft, mono));
        box.addView(gap(8));
        EditText ans = field(box, "Dear Miso...", "");
        ans.setSingleLine(false);
        ans.setMinLines(3);
        AlertDialog d = alert("Journal", box)
                .setPositiveButton("Save", (dd, ww) -> {
                    String t = ans.getText().toString().trim();
                    if (!t.isEmpty()) {
                        Store.addWonderSpark("journal  ->  " + t);
                        Store.addSpark();
                        toast("Saved. Miso keeps it safe.");
                    }
                    rebuild();
                }).setNegativeButton("Cancel", null).show();
        styleDialog(d);
    }

    // ---------- guide: the research manual, each bit becomes a task ----------
    private static final String[][] GUIDE = {
        {"Move first, not last",
         "Exercise raises dopamine and norepinephrine within minutes — the same chemistry ADHD meds target. Even 10 minutes of moderate movement measurably improves focus for 1-2 hours (Wheeler et al., 2020; Mehren et al., 2019).",
         "How: right before you need to study, do 10 squats or 2 minutes of stairs. Keep it embarrassingly small on bad days — the rule is never zero.",
         "10 squats before studying"},
        {"Two-minute rule",
         "Starting is the hard part; motivation follows action, not the other way (Fogg, 2019). Shrinking the first step removes the dread that triggers avoidance.",
         "How: define every task by its first 2 minutes. Open the book. Write one line. You are allowed to stop after 2 minutes — you usually won't.",
         "Do a 2-minute start"},
        {"If-then plans (implementation intentions)",
         "Gollwitzer's meta-analysis of 94 studies (~8,000 people): specifying when+where+how roughly doubles follow-through. It hands control from 'deciding you' to 'the situation'.",
         "How: write plans as 'If it is 7pm after dinner, then I open notes at my desk for 2 min.' Put the cue where the behavior happens.",
         "Write one if-then plan"},
        {"Self-compassion after slipping",
         "Sirois & Pychyl: procrastination is short-term mood repair. Shame prolongs avoidance; self-forgiveness after a procrastination episode reduced it next time (Wohl et al., 2010).",
         "How: after a wasted day, say 'that was human, what is the next 2-minute step?' Then do it. No penalties, no streak-guilt.",
         "Write a kind reset note"},
        {"Sleep regularity beats duration",
         "Windred et al. (2024), 60,000 people: irregular sleep predicts mortality risk stronger than hours slept. The body rewards rhythm, not heroics.",
         "How: same wind-down time nightly, screens off 30 min before, bed at a fixed anchor. Use the Daily sleep stepper to keep the rhythm visible.",
         "Set my wind-down anchor"},
        {"Friction against doomscrolling",
         "RCTs: a 3-second launch delay cut Shorts sessions ~11%; opt-out caps cut use ~18%. Willpower alone loses to infinite scroll — architecture wins.",
         "How: keep social apps off the home screen, use the Guardian so the takeover offers a breath + choice. Log phone-rest in Daily to watch the trend.",
         "Tune my Guardian"},
        {"Study in spaced sprints",
         "Distributed practice beats cramming (Cepeda et al., 2006, meta-analysis of 254 studies). Retrieval practice (testing yourself) beats rereading (Roediger & Karpicke, 2006).",
         "How: 15-25 min sprint, then recall from memory for 2 min, then a real break. Repeat later in the day. Log sprints via Study mode.",
         "One 15-min study sprint"},
        {"Mental contrasting (MCII)",
         "Oettingen: picture the win, then honestly name the obstacle in you, then the if-then for it. Beats positive fantasizing in trials across grades, health, relationships.",
         "How: before a big goal, write the best outcome, your #1 inner obstacle, and one if-then against it. Takes 5 minutes.",
         "Run a 5-min MCII"},
        {"Move your alarm out of reach",
         "Bedtime procrastination is emotion-driven, not laziness (Kroese et al.). Making the snooze costlier works better than promises at midnight.",
         "How: charge the phone across the room; use the wind-down notification as the last phone moment of the day.",
         "Charge phone across room"},
        {"Water before caffeine",
         "Mild dehydration (1-2%) already degrades attention and working memory (Ganio et al., 2011; Armstrong et al., 2012). Anchor glasses to existing moments.",
         "How: one glass on waking, one with each meal, one mid-afternoon. The Daily + button tracks it — five wins a day.",
         "Drink a glass now"},
    };

    private View guideView() {
        ScrollView sv = scroller();
        LinearLayout col = col(sv);
        col.addView(section("The manual"));
        LinearLayout intro = card();
        intro.addView(text("Why before what. Every card here is research-backed (psychology + philosophy), with a how-to and a button that turns it into a real task with its own time and sound.", 13, InkSoft, mono));
        col.addView(intro);
        col.addView(gap(6));
        for (String[] g : GUIDE) {
            LinearLayout c = card();
            c.setClickable(true);
            c.setFocusable(true);
            c.addView(text(g[0], 15, Ink, monoBold));
            c.addView(gap(4));
            c.addView(text(g[1], 12, InkSoft, mono));
            c.addView(gap(6));
            c.addView(text(g[3] + "  >", 12, Theme.accent(), monoBold));
            c.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                openDetail(() -> guideDetail(g[0], g[1], g[2], g[3]));
            });
            col.addView(c);
        }
        col.addView(gap(60));
        return sv;
    }

    private View guideDetail(String title, String research, String how, String preset) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout r = card();
        r.addView(tinyLabel("Why it works"));
        r.addView(text(research, 14, Ink, mono));
        body.addView(r);
        LinearLayout h = card();
        h.addView(tinyLabel("How to do it"));
        h.addView(text(how, 14, Ink, mono));
        body.addView(h);
        LinearLayout a = card();
        a.addView(text("Make it yours: set your own time, sound, and plan. Nothing is imposed.", 13, InkSoft, mono));
        a.addView(gap(10));
        a.addView(pill("Make it a task: " + preset, Theme.accent(), Color.WHITE, () -> showQuestDialogPrefilled(preset, how)));
        body.addView(a);
        return detailShell("Guide", body);
    }

    private void showQuestDialogPrefilled(String presetTitle, String presetHow) {
        inDetail = false;
        detailBuilder = null;
        LinearLayout box = dialogBox();
        box.addView(text("Keep it weekly, tiny, kind. Example plan: If it is 7pm after dinner, then I open notes for 2 min.", 12, InkSoft, mono));
        box.addView(gap(8));
        EditText title = field(box, "Title", presetTitle);
        EditText tiny = field(box, "2-minute step", presetHow);
        EditText deep = field(box, "15-minute deep-dive", "");
        EditText cue = field(box, "If-then plan (when + where)", "");
        AlertDialog.Builder b = alert("New task from guide", box);
        b.setPositiveButton("Save", (d, w) -> {
            String id = "q" + System.currentTimeMillis();
            Quest q = new Quest(id, title.getText().toString(), "", tiny.getText().toString(), deep.getText().toString());
            q.cue = cue.getText().toString().trim();
            q.sound = "spark";
            Store.upsertQuest(q);
            Scheduler.rescheduleAfterChange(this);
            tab = 0;
            toast("Task created. Give it a time and sound on the Tasks tab.");
            rebuild();
        }).setNegativeButton("Cancel", null);
        styleDialog(b.show());
    }

    // ---------- menu: guardian, reminders, appearance, data ----------
    private View menuView() {
        ScrollView sv = scroller();
        LinearLayout col = col(sv);
        col.addView(section("Guardian"));
        col.addView(guardianCard());
        col.addView(gap(6));
        col.addView(section("Reminders & sound"));
        LinearLayout rc = card();
        rc.addView(text("Seed, pings, wind-down, quiet hours, snooze length.", 13, InkSoft, mono));
        rc.addView(gap(10));
        rc.addView(pill("Reminder settings", Theme.accent(), Color.WHITE, this::showSettings));
        rc.addView(gap(10));
        rc.addView(pill("Sounds per task", 0x00000000, Ink, this::showSoundsDialog));
        col.addView(rc);
        col.addView(gap(6));
        col.addView(section("Appearance"));
        LinearLayout ac = card();
        final Switch darkSw = new Switch(this);
        darkSw.setText("Dark mode");
        darkSw.setTextSize(14);
        darkSw.setTypeface(mono);
        darkSw.setTextColor(Ink);
        darkSw.setChecked(Store.data.settings.darkMode);
        darkSw.setPadding(0, dp(8), 0, dp(8));
        darkSw.setOnCheckedChangeListener((b, on) -> {
            Store.data.settings.darkMode = on;
            Store.persist();
            Theme.dark = on;
            try { recreate(); } catch (Exception ignored) { rebuild(); }
        });
        ac.addView(darkSw);
        col.addView(ac);
        col.addView(gap(6));
        col.addView(section("Backup & export"));
        col.addView(exportCard());
        col.addView(gap(6));
        col.addView(section("Journal download"));
        LinearLayout jc = card();
        jc.addView(text("Save one day's entries as Markdown.", 13, InkSoft, mono));
        jc.addView(gap(10));
        final EditText dateF = new EditText(this);
        dateF.setText(Dates.today());
        dateF.setTextSize(14);
        dateF.setTypeface(mono);
        dateF.setTextColor(Ink);
        dateF.setHintTextColor(LABEL);
        dateF.setPadding(dp(8), dp(10), dp(8), dp(10));
        GradientDrawable dbg = new GradientDrawable();
        dbg.setCornerRadius(dp(14));
        dbg.setColor(Theme.field());
        dbg.setStroke(dp(1), LINE);
        dateF.setBackground(dbg);
        dateF.setSingleLine(true);
        jc.addView(dateF, new LinearLayout.LayoutParams(-1, -2));
        jc.addView(gap(10));
        jc.addView(pill("Download .md", Theme.accent(), Color.WHITE,
                () -> downloadEntriesMd(dateF.getText().toString().trim())));
        col.addView(jc);
        col.addView(gap(60));
        return sv;
    }

    private void downloadEntriesMd(String date) {
        if (date.isEmpty()) date = Dates.today();
        pendingMd = com.spark.app.util.Export.entriesMarkdown(this, date);
        pendingMdName = "spark_" + date + ".md";
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/markdown");
        i.putExtra(Intent.EXTRA_TITLE, pendingMdName);
        try { startActivityForResult(i, REQ_MD); } catch (Exception e) { toast("Couldn't open the file picker."); }
    }

    private void showWonderExplain(String w) {
        LinearLayout box = dialogBox();
        box.addView(text(w, 15, Ink, mono));
        box.addView(gap(8));
        EditText ans = field(box, "Your 2-4 line take, your own words", "");
        AlertDialog d = alert("Wonder", box)
                .setPositiveButton("Save spark", (dd, ww) -> {
                    String t = ans.getText().toString().trim();
                    if (!t.isEmpty()) { Store.addWonderSpark(w + "  ->  " + t); Store.addSpark(); }
                    rebuild();
                }).setNegativeButton("Cancel", null).show();
        styleDialog(d);
    }

    // ---------- interactive clock picker ----------
    private interface TimeCb { void onPick(int h24, int m); }

    private void showTimePicker(String title, int initH, int initM, TimeCb cb) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final int[] picked = {initH, initM};
        showTimePickerInto(box, title, initH, initM, null, null, null, null, null, null,
                (nh, nm) -> { picked[0] = nh; picked[1] = nm; });
        AlertDialog d = alert(title, box)
                .setPositiveButton("Set", (dd, ww) -> cb.onPick(picked[0], picked[1]))
                .setNegativeButton("Cancel", null).show();
        styleDialog(d);
        springShow(box);
    }

    // Android-17-style dial builder: big expressive face, tonal pills, live readout.
    // Optional out-params let callers (task time) read the live selection.
    private void showTimePickerInto(LinearLayout box, String title, int initH, int initM,
            final TextView[] readoutOut, final ClockFaceView[] faceOut,
            final TextView[] hourBtnOut, final TextView[] minBtnOut,
            final TextView[] amBtnOut, final TextView[] pmBtnOut, final TimeCb live) {
        final ClockFaceView face = new ClockFaceView(this);
        face.setDark(Theme.dark);
        face.set(initH, initM);
        face.setHourMode(true);

        // Segmented HH | MM boxes, NOS 5.0 clock-face sheet style. Tap a box = mode.
        LinearLayout segRow = new LinearLayout(this);
        segRow.setOrientation(LinearLayout.HORIZONTAL);
        segRow.setGravity(Gravity.CENTER);
        final TextView boxH = segBox(String.format(java.util.Locale.US, "%02d", initH));
        final TextView boxM = segBox(String.format(java.util.Locale.US, "%02d", initM));
        final TextView ampmTag = new TextView(this);
        ampmTag.setText(initH < 12 ? "AM" : "PM");
        ampmTag.setTextSize(13);
        ampmTag.setTypeface(monoBold);
        ampmTag.setTextColor(InkSoft);
        ampmTag.setGravity(Gravity.CENTER);
        ampmTag.setPadding(dp(10), 0, 0, 0);
        segRow.addView(boxH, new LinearLayout.LayoutParams(dp(108), dp(72)));
        segRow.addView(gapW(dp(10)));
        segRow.addView(boxM, new LinearLayout.LayoutParams(dp(108), dp(72)));
        segRow.addView(ampmTag);

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setGravity(Gravity.CENTER);
        final TextView hourBtn = miniPill("HOUR", true);
        final TextView minBtn = miniPill("MIN", false);
        modes.addView(hourBtn); modes.addView(gapW(dp(8))); modes.addView(minBtn);

        LinearLayout ampm = new LinearLayout(this);
        ampm.setOrientation(LinearLayout.HORIZONTAL);
        ampm.setGravity(Gravity.CENTER);
        final TextView amBtn = miniPill("AM", initH < 12);
        final TextView pmBtn = miniPill("PM", initH >= 12);
        ampm.addView(amBtn); ampm.addView(gapW(dp(8))); ampm.addView(pmBtn);

        final int[] picked = {initH, initM};
        final Runnable paintSeg = new Runnable() {
            public void run() {
                boxH.setText(String.format(java.util.Locale.US, "%02d", picked[0]));
                boxM.setText(String.format(java.util.Locale.US, "%02d", picked[1]));
                ampmTag.setText(picked[0] < 12 ? "AM" : "PM");
                boolean hm = face.isHourMode();
                paintSegBox(boxH, hm);
                paintSegBox(boxM, !hm);
                hourBtn.setAlpha(hm ? 1f : 0.4f);
                minBtn.setAlpha(hm ? 0.4f : 1f);
            }
        };
        paintSeg.run();
        boxH.setOnClickListener(v -> { face.setHourMode(true); paintSeg.run(); });
        boxM.setOnClickListener(v -> { face.setHourMode(false); paintSeg.run(); });
        face.setListener((h, m, hourMode) -> {
            picked[0] = h; picked[1] = m;
            paintSeg.run();
            boxH.animate().scaleX(1.05f).scaleY(1.05f).setDuration(90)
                    .withEndAction(() -> {
                        try { boxH.animate().scaleX(1f).scaleY(1f).setDuration(160).start(); }
                        catch (Exception ignored) {}
                    }).start();
            if (live != null) live.onPick(h, m);
        });
        hourBtn.setOnClickListener(v -> { face.setHourMode(true); paintSeg.run(); });
        minBtn.setOnClickListener(v -> { face.setHourMode(false); paintSeg.run(); });
        amBtn.setOnClickListener(v -> {
            if (picked[0] >= 12) { picked[0] -= 12; face.set(picked[0], picked[1]); paintSeg.run(); if (live != null) live.onPick(picked[0], picked[1]); }
            amBtn.setAlpha(1f); pmBtn.setAlpha(0.4f);
        });
        pmBtn.setOnClickListener(v -> {
            if (picked[0] < 12) { picked[0] += 12; face.set(picked[0], picked[1]); paintSeg.run(); if (live != null) live.onPick(picked[0], picked[1]); }
            amBtn.setAlpha(0.4f); pmBtn.setAlpha(1f);
        });

        box.addView(segRow);
        box.addView(gap(4));
        box.addView(modes);
        box.addView(gap(6));
        box.addView(ampm);
        box.addView(gap(6));
        LinearLayout step = new LinearLayout(this);
        step.setOrientation(LinearLayout.HORIZONTAL);
        step.setGravity(Gravity.CENTER);
        final TextView minus = miniPill("- 1 MIN", false);
        final TextView plus = miniPill("+ 1 MIN", false);
        step.addView(minus); step.addView(gapW(dp(8))); step.addView(plus);
        minus.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (face.isHourMode()) picked[0] = (picked[0] + 23) % 24;
            else picked[1] = (picked[1] + 59) % 60;
            face.set(picked[0], picked[1]);
            paintSeg.run();
            if (live != null) live.onPick(picked[0], picked[1]);
        });
        plus.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (face.isHourMode()) picked[0] = (picked[0] + 1) % 24;
            else picked[1] = (picked[1] + 1) % 60;
            face.set(picked[0], picked[1]);
            paintSeg.run();
            if (live != null) live.onPick(picked[0], picked[1]);
        });
        box.addView(step);
        box.addView(gap(8));
        box.addView(face, new LinearLayout.LayoutParams(dp(320), dp(320)));

        if (readoutOut != null && readoutOut.length > 0) readoutOut[0] = boxH;
        if (faceOut != null && faceOut.length > 0) faceOut[0] = face;
        if (hourBtnOut != null && hourBtnOut.length > 0) hourBtnOut[0] = hourBtn;
        if (minBtnOut != null && minBtnOut.length > 0) minBtnOut[0] = minBtn;
        if (amBtnOut != null && amBtnOut.length > 0) amBtnOut[0] = amBtn;
        if (pmBtnOut != null && pmBtnOut.length > 0) pmBtnOut[0] = pmBtn;
    }

    // Dial pops in like a glyph lighting up: scale spring + fade.
    private void springShow(View v) {
        try {
            v.setScaleX(0.9f); v.setScaleY(0.9f); v.setAlpha(0.4f);
            v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(340)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.0f)).start();
        } catch (Exception ignored) {}
    }

    // Segmented time box: frosted field, dot-matrix numerals, tap = mode.
    private TextView segBox(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(32);
        t.setTypeface(dotFont);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Ink);
        t.setIncludeFontPadding(false);
        t.setPadding(0, dp(8), 0, dp(8));
        paintSegBox(t, false);
        return t;
    }

    private void paintSegBox(TextView t, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(Theme.field());
        bg.setStroke(dp(selected ? 2 : 1), selected ? Theme.accent() : LINE);
        t.setBackground(bg);
    }

    private TextView miniPill(String s, boolean active) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12);
        t.setTypeface(monoBold);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(active ? Color.WHITE : Ink);
        t.setAlpha(active ? 1f : 0.4f);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(active ? Theme.accent() : 0x00000000);
        if (!active) bg.setStroke(dp(1), LINE);
        t.setBackground(bg);
        t.setPadding(dp(14), 0, dp(14), 0);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(84), dp(36)));
        return t;
    }

    private View gapW(int px) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(px, 1));
        return v;
    }

    // ---------- dialogs ----------
    private AlertDialog.Builder alert(String title, View view) {
        return new AlertDialog.Builder(this).setTitle(title).setView(view);
    }

    private void styleDialog(AlertDialog d) {
        try {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Theme.dialogBg()));
            int ink = Theme.ink();
            d.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(Theme.accent());
            d.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(ink);
            d.getButton(DialogInterface.BUTTON_NEUTRAL).setTextColor(ink);
            int titleId = getResources().getIdentifier("alertTitle", "id", getPackageName());
            if (titleId != 0) {
                View tv = d.findViewById(titleId);
                if (tv instanceof TextView) ((TextView) tv).setTextColor(ink);
            }
        } catch (Exception ignored) {}
    }

    private void showQuestDialog(Quest existing) {
        LinearLayout box = dialogBox();
        box.addView(text("Keep it weekly, tiny, kind. Example plan: If it is 7pm after dinner, then I open notes for 2 min.", 12, InkSoft, mono));
        box.addView(gap(8));
        EditText title = field(box, "Title", existing != null ? existing.title : "");
        EditText tiny = field(box, "2-minute step", existing != null ? existing.tiny : "");
        EditText deep = field(box, "15-minute deep-dive", existing != null ? existing.deep : "");
        EditText cue = field(box, "If-then plan (when + where)", existing != null ? existing.cue : "");
        AlertDialog.Builder b = alert(existing != null ? "Edit task" : "New task", box);
        if (existing != null) b.setNeutralButton("Delete", (d, w) -> {
            Store.deleteQuest(existing.id);
            Scheduler.cancelQuest(this, existing.id);
            toast("Task removed, reminder cleared.");
            rebuild();
        });
        b.setPositiveButton("Save", (d, w) -> {
            String id = existing != null ? existing.id : ("q" + System.currentTimeMillis());
            Quest q = new Quest(id, title.getText().toString(), "", tiny.getText().toString(), deep.getText().toString());
            q.cue = cue.getText().toString().trim();
            if (existing != null) { q.doneDays.addAll(existing.doneDays); q.notes = existing.notes; q.remindAt = existing.remindAt; q.sound = existing.sound; }
            Store.upsertQuest(q);
            Scheduler.rescheduleAfterChange(this);
            toast("Saved. Its reminder is set if you gave it a time.");
            rebuild();
        }).setNegativeButton("Cancel", null);
        styleDialog(b.show());
    }

    private void showQuestNotes(Quest q) {
        if (q == null) { toast("Add a quest first."); return; }
        LinearLayout box = dialogBox();
        EditText notes = field(box, "Ideas / notes", q.notes);
        AlertDialog d = alert("Notes", box)
                .setPositiveButton("Save spark", (dd, ww) -> {
                    Store.setQuestNotes(q.id, notes.getText().toString());
                    Store.addSpark();
                    toast("Saved. That's a genuine spark.");
                    rebuild();
                }).setNegativeButton("Cancel", null).show();
        styleDialog(d);
    }

    private void showCuriosity() {
        LinearLayout box = dialogBox();
        box.addView(text(curCard, 15, Ink, mono));
        box.addView(gap(8));
        EditText ans = field(box, "Your 2-3 lines - anything goes", "");
        AlertDialog d = alert("3-minute spark", box)
                .setPositiveButton("Save", (dd, ww) -> {
                    String t = ans.getText().toString().trim();
                    if (!t.isEmpty()) { Store.addCreativitySpark(curCard + "  ->  " + t); Store.addSpark(); }
                    rebuild();
                }).setNegativeButton("Cancel", null).show();
        styleDialog(d);
    }

    private void showCaptureDialog() {
        LinearLayout box = dialogBox();
        box.addView(text("Let it out. It'll be saved here, safely.", 14, InkSoft, mono));
        box.addView(gap(8));
        EditText ans = field(box, "The thought in your head", "");
        AlertDialog d = alert("Thought capture", box)
                .setPositiveButton("Capture", (dd, ww) -> {
                    String t = ans.getText().toString().trim();
                    if (!t.isEmpty()) { Store.addCapture(t); toast("Captured. Out of your head, into the app."); }
                    rebuild();
                }).setNegativeButton("Cancel", null).show();
        styleDialog(d);
    }

    private void showMedDialog(Med existing) {
        LinearLayout box = dialogBox();
        EditText name = field(box, "Name", existing != null ? existing.name : "");
        EditText note = field(box, "Note", existing != null ? existing.note : "");
        box.addView(gap(2));
        box.addView(tinyLabel("Times - tap to set with the clock"));
        box.addView(gap(6));
        final List<String> times = new ArrayList<>();
        if (existing != null) times.addAll(existing.times);
        final LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.VERTICAL);
        final Med ref = existing;
        final Runnable[] renderChips = new Runnable[1];
        renderChips[0] = () -> {
            chips.removeAllViews();
            LinearLayout wrap = new LinearLayout(this);
            wrap.setOrientation(LinearLayout.HORIZONTAL);
            for (final String t : times) {
                TextView chip = pill(ClockFaceView.fmt(Dates.parseHm(t)[0], Dates.parseHm(t)[1]), 0x00000000, Ink,
                        () -> showTimePicker("Change time", Dates.parseHm(t)[0], Dates.parseHm(t)[1], (h, m) -> {
                            times.set(times.indexOf(t), Dates.hhmm(h, m));
                            renderChips[0].run();
                        }), -2, 40);
                chips.addView(wrap);
                wrap.addView(chip);
                TextView x = pill("x", 0x00000000, InkSoft, () -> { times.remove(t); renderChips[0].run(); }, 36, 40);
                wrap.addView(x);
            }
        };
        renderChips[0].run();
        box.addView(chips);
        box.addView(gap(4));
        box.addView(pill("+ Add a time", 0x00000000, Ink, () -> showTimePicker("New time", 9, 0, (h, m) -> {
            String t = Dates.hhmm(h, m);
            if (!times.contains(t)) times.add(t);
            renderChips[0].run();
        })));

        AlertDialog.Builder b = alert(existing != null ? "Edit medication" : "Add medication", box);
        if (existing != null) b.setNeutralButton("Delete", (d, w) -> { Store.deleteMed(existing.id); Scheduler.rescheduleAfterChange(this); rebuild(); });
        b.setPositiveButton("Save", (d, w) -> {
            String id = existing != null ? existing.id : ("m" + System.currentTimeMillis());
            Med m = new Med(id, name.getText().toString(), note.getText().toString());
            java.util.Collections.sort(times);
            for (String t : times) m.times.add(t);
            Store.upsertMed(m);
            Scheduler.rescheduleAfterChange(this);
            rebuild();
        }).setNegativeButton("Cancel", null);
        styleDialog(b.show());
    }

    private void showReflectionDialog() {
        LinearLayout box = dialogBox();
        EditText txt = field(box, "One kind sentence about this week.", Store.data.reflection);
        AlertDialog d = alert("Weekly reflection", box)
                .setPositiveButton("Save", (dd, ww) -> { Store.saveReflectionWeek(txt.getText().toString()); rebuild(); })
                .setNegativeButton("Cancel", null).show();
        styleDialog(d);
    }

    private void showGuardianDialog() {
        final Settings s = Store.data.settings;
        LinearLayout box = dialogBox();
        final Switch on = sw(box, "Guardian on", s.guardEnabled);
        final EditText thr = field(box, "Trigger after (minutes)", String.valueOf(s.guardThresholdMin));
        final EditText allow = field(box, "Allow me back for (minutes)", String.valueOf(s.guardAllowMin));
        box.addView(gap(4));
        final int[] picked = {s.guardTheme};
        box.addView(tinyLabel("Takeover screen theme"));
        box.addView(gap(6));
        LinearLayout themes = new LinearLayout(this);
        themes.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"Dawn", "Ocean", "Dusk"};
        int[][] previews = {{0xFFF6F4FF, 0xFFEDF6EE}, {0xFFEAF4FF, 0xFFE8FBF6}, {0xFF201C38, 0xFF2A2447}};
        final TextView[] pills = new TextView[3];
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView p = new TextView(this);
            p.setText(names[i]);
            p.setTextSize(13);
            p.setTypeface(monoBold);
            p.setGravity(Gravity.CENTER);
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{previews[i][0], previews[i][1]});
            bg.setCornerRadius(dp(24));
            if (picked[0] == idx) bg.setStroke(dp(2), Theme.accent()); else bg.setStroke(dp(1), LINE);
            p.setBackground(bg);
            p.setTextColor(idx == 2 ? 0xFFF1EEFA : Ink);
            p.setPadding(0, dp(10), 0, dp(10));
            p.setOnClickListener(v -> {
                picked[0] = idx;
                for (int j = 0; j < 3; j++) {
                    GradientDrawable g = (GradientDrawable) pills[j].getBackground();
                    g.setStroke(dp(j == idx ? 2 : 1), j == idx ? Theme.accent() : LINE);
                }
            });
            themes.addView(p, new LinearLayout.LayoutParams(0, dp(48), 1f));
            if (i < 2) themes.addView(gap(6));
            pills[i] = p;
        }
        box.addView(themes);
        AlertDialog d = alert("Guardian", box)
                .setPositiveButton("Save", (dd, ww) -> {
                    s.guardEnabled = on.isChecked();
                    s.guardThresholdMin = parseInt(thr.getText().toString(), 15);
                    s.guardAllowMin = parseInt(allow.getText().toString(), 5);
                    s.guardTheme = picked[0];
                    Store.persist(); toast("Guardian updated."); rebuild();
                }).setNegativeButton("Cancel", null).show();
        styleDialog(d);
    }

    private void showGuardianAppsDialog() {
        LinearLayout box = dialogBox();
        box.addView(text("Packages to watch. One per line. Social apps are pre-filled.", 13, InkSoft, mono));
        box.addView(gap(8));
        EditText list = field(box, "Package names", "");
        StringBuilder sb = new StringBuilder();
        for (String p : Store.data.guardedApps) sb.append(p).append("\n");
        list.setText(sb.toString().trim());
        list.setSingleLine(false);
        AlertDialog d = alert("Guardian apps", box)
                .setPositiveButton("Save", (dd, ww) -> {
                    Store.data.guardedApps.clear();
                    for (String line : list.getText().toString().split("\\s+")) {
                        String p = line.trim();
                        if (!p.isEmpty() && !Store.data.guardedApps.contains(p)) Store.data.guardedApps.add(p);
                    }
                    if (Store.data.guardedApps.isEmpty()) Store.data.guardedApps.addAll(GuardianApps.defaults());
                    Store.persist(); toast("Guardian apps updated."); rebuild();
                }).setNegativeButton("Cancel", null).show();
        styleDialog(d);
    }

    private void showSoundsDialog() {
        ScrollView sv = scroller();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(24);
        box.setPadding(p, dp(8), p, 0);
        for (String task : SoundManager.TASKS) {
            boolean custom = Store.data.soundMap.containsKey(task);
            LinearLayout row = row();
            row.setPadding(0, dp(8), 0, dp(8));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.addView(text(SoundManager.label(task), 14, Ink, monoBold));
            info.addView(text(custom ? "custom sound" : "built-in", 11, InkSoft, mono));
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(pill("Play", 0x00000000, Ink, () -> SoundManager.preview(this, SoundManager.resolve(this, task)), 68, 44));
            row.addView(pill("Choose", 0x00000000, Ink, () -> pickSound(task), 92, 44));
            row.addView(pill("Reset", 0x00000000, Ink, () -> { SoundManager.reset(this, task); rebuild(); }, 88, 44));
            box.addView(row);
        }
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        AlertDialog d = alert("Sound per task", sv).setPositiveButton("Done", null).show();
        styleDialog(d);
    }

    private void pickSound(String task) {
        pendingSoundTask = task;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        try { startActivityForResult(i, REQ_SOUND); } catch (Exception e) { toast("Couldn't open file picker."); }
    }

    private void showSettings() {
        LinearLayout box = dialogBox();
        final Settings s = Store.data.settings;

        final EditText name = field(box, "What should I call you?", s.userName);
        box.addView(gap(4));

        // seed reminder
        final Switch seedOn = sw(box, "Daily gentle seed reminder", s.seedEnabled);
        final int[] seed = {s.seedHour, s.seedMinute};
        final TextView seedPill = timePill(box, "Seed time", seed[0], seed[1], (h, m) -> { seed[0] = h; seed[1] = m; });
        box.addView(gap(4));

        // movement pings
        final Switch pingOn = sw(box, "Body movement pings", s.pingEnabled);
        final EditText pingInt = field(box, "Ping every (minutes)", String.valueOf(s.pingIntervalMin));
        box.addView(gap(4));

        final EditText snooze = field(box, "Snooze length (minutes)", String.valueOf(s.snoozeMin));
        box.addView(gap(4));

        // wind-down
        final Switch windOn = sw(box, "Sleep wind-down reminder", s.winddownEnabled);
        final int[] wind = {s.windDownHour, s.windDownMin};
        final TextView windPill = timePill(box, "Wind-down time", wind[0], wind[1], (h, m) -> { wind[0] = h; wind[1] = m; });
        box.addView(gap(4));

        // quiet hours
        final Switch quietOn = sw(box, "Quiet hours (only meds notify)", s.quietEnabled);
        final int[] qs = {s.quietStartHour, 0};
        final int[] qe = {s.quietEndHour, 0};
        box.addView(gap(2));
        box.addView(tinyLabel("Quiet hours - tap each end"));
        box.addView(gap(4));
        LinearLayout qRow = row();
        final TextView[] qStart = new TextView[1];
        final TextView[] qEnd = new TextView[1];
        qStart[0] = pill("From " + ClockFaceView.fmt(qs[0], 0), 0x00000000, Ink,
                () -> showTimePicker("Quiet from", qs[0], 0, (h, m) -> { qs[0] = h; qStart[0].setText("From " + ClockFaceView.fmt(h, m)); }), -2, 42);
        qEnd[0] = pill("To " + ClockFaceView.fmt(qe[0], 0), 0x00000000, Ink,
                () -> showTimePicker("Quiet until", qe[0], 0, (h, m) -> { qe[0] = h; qEnd[0].setText("To " + ClockFaceView.fmt(h, m)); }), -2, 42);
        qRow.addView(qStart[0]); qRow.addView(qEnd[0]);
        box.addView(qRow);
        box.addView(gap(4));

        final Switch calm = sw(box, "Calm mode (only medications notify)", s.calmMode);
        box.addView(gap(4));

        final Switch dark = sw(box, "Dark mode", s.darkMode);
        box.addView(gap(4));

        // Nothing OS look (monochrome + red) vs the old soft pastel look.
        final Switch nothing = sw(box, "Nothing OS look", s.themeStyle == Theme.STYLE_NOTHING);
        box.addView(gap(4));

        // Rhythm: the whole day slides with the user instead of the wall clock.
        final Switch rhythm = sw(box, "Follow my day, not the clock", s.rhythmAdaptive);
        box.addView(gap(4));
        final int[] shiftMin = {s.chronoShiftMin};
        final TextView[] shiftRef = new TextView[1];
        shiftRef[0] = pill("Day shift " + shiftLabel(shiftMin[0]), 0x00000000, Ink,
                () -> {
                    // cycle 0 -> +1h -> +2h -> +3h -> +4h -> 0
                    shiftMin[0] = shiftMin[0] >= 240 ? 0 : shiftMin[0] + 60;
                    shiftRef[0].setText("Day shift " + shiftLabel(shiftMin[0]));
                }, -2, 42);
        box.addView(shiftRef[0]);
        box.addView(gap(4));

        // Medication dose window. A dose lives across a span of time; the
        // second nudge fires at the end of it and only if the dose is still
        // unmarked, so this can never nag about something already done.
        final int[] medWin = {s.medWindowMin};
        final TextView[] medWinRef = new TextView[1];
        medWinRef[0] = pill("Med window " + medWinLabel(medWin[0]), 0x00000000, Ink,
                () -> {
                    // cycle 30 -> 60 -> 90 -> 120 -> 180 -> 30
                    int w = medWin[0];
                    w = w >= 180 ? 30 : (w >= 120 ? 180 : (w >= 90 ? 120 : (w >= 60 ? 90 : 60)));
                    medWin[0] = w;
                    medWinRef[0].setText("Med window " + medWinLabel(w));
                }, -2, 42);
        box.addView(medWinRef[0]);
        box.addView(gap(4));

        AlertDialog d = alert("Settings", box)
                .setPositiveButton("Save", (dd, ww) -> {
                    s.userName = name.getText().toString();
                    s.seedEnabled = seedOn.isChecked();
                    s.seedHour = seed[0]; s.seedMinute = seed[1];
                    s.pingEnabled = pingOn.isChecked();
                    s.pingIntervalMin = parseInt(pingInt.getText().toString(), 90);
                    s.snoozeMin = parseInt(snooze.getText().toString(), 30);
                    s.winddownEnabled = windOn.isChecked();
                    s.windDownHour = wind[0]; s.windDownMin = wind[1];
                    s.quietEnabled = quietOn.isChecked();
                    s.quietStartHour = qs[0]; s.quietEndHour = qe[0];
                    s.calmMode = calm.isChecked();
                    boolean wasDark = s.darkMode;
                    int wasStyle = s.themeStyle;
                    s.darkMode = dark.isChecked();
                    s.themeStyle = nothing.isChecked() ? Theme.STYLE_NOTHING : Theme.STYLE_SOFT;
                    s.rhythmAdaptive = rhythm.isChecked();
                    s.chronoShiftMin = shiftMin[0];
                    s.medWindowMin = medWin[0];
                    Store.persist();
                    Scheduler.rescheduleAfterChange(this);
                    toast("Saved. No pressure.");
                    if (wasDark != s.darkMode || wasStyle != s.themeStyle) {
                        // Full recreate: the background layer (dot grid vs frosted)
                        // is created once in buildRoot, so a plain rebuild keeps the
                        // old look. Recreate redraws background + bars + cards.
                        Theme.dark = s.darkMode;
                        Theme.style = s.themeStyle;
                        try { recreate(); } catch (Exception ignored) { rebuild(); }
                        return;
                    }
                    rebuild();
                })
                .setNeutralButton("Sounds...", (dd, ww) -> showSoundsDialog())
                .setNegativeButton("Cancel", null).show();
        styleDialog(d);
    }

    private TextView timePill(LinearLayout parent, String label, int h, int m, TimeCb cb) {
        parent.addView(gap(2));
        parent.addView(tinyLabel(label));
        parent.addView(gap(4));
        final int[] cur = {h, m};
        final TextView[] pillRef = new TextView[1];
        pillRef[0] = pill(ClockFaceView.fmt(h, m), Theme.dark ? 0xFF2A2440 : 0xFFFFFFFF, Ink,
                () -> showTimePicker(label, cur[0], cur[1], (nh, nm) -> {
                    cur[0] = nh; cur[1] = nm;
                    pillRef[0].setText(ClockFaceView.fmt(nh, nm));
                    cb.onPick(nh, nm);
                }), -2, 42);
        parent.addView(pillRef[0]);
        return pillRef[0];
    }

    // ---------- helpers ----------
    private int parseInt(String s, int d) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return d; } }

    /** Human label for the rhythm-shift dial. */
    private String shiftLabel(int minutes) {
        if (minutes == 0) return "same as clock";
        int h = Math.abs(minutes / 60);
        return (minutes > 0 ? "+" : "-") + h + "h";
    }

    /** Human label for the medication dose window. */
    private String medWinLabel(int minutes) {
        if (minutes < 60) return minutes + " min";
        int h = minutes / 60, m = minutes % 60;
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }

    /**
     * True during the user's own night: from their SHIFTED wind-down, for eight
     * hours. Using the shifted hour matters - a +3h day gets its dim window at
     * 00:30-08:30, which is when that person is actually asleep, instead of the
     * wall-clock 21:30-05:30 they have never once been asleep for.
     */
    private boolean isNightNow(Settings s) {
        try {
            int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            int start = s.winddownEnabled ? s.windDownHourEff() : s.quietStartEff();
            int end = (start + 8) % 24;
            if (start <= end) return h >= start && h < end;
            return h >= start || h < end;
        } catch (Exception e) {
            return false;
        }
    }

    private ScrollView scroller() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        return sv;
    }

    private LinearLayout col(ScrollView sv) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(22), dp(16), dp(22), 0);
        sv.addView(c, new ScrollView.LayoutParams(-1, -2));
        return c;
    }

    private LinearLayout dialogBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(24);
        box.setPadding(p, dp(8), p, 0);
        return box;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    // Wrapping button row: pills flow to the next line on narrow screens.
    private com.spark.app.ui.FlowLayout flowRow() {
        return new com.spark.app.ui.FlowLayout(this);
    }

    // Gap sized for FlowLayout children (they ignore margins).
    private View gapFlow(int d) {
        View v = new View(this);
        v.setLayoutParams(new android.view.ViewGroup.LayoutParams(dp(d), 1));
        return v;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.card());
        bg.setCornerRadius(dp(30));
        bg.setStroke(dp(1), Theme.cardLine());
        c.setBackground(bg);
        try { c.setElevation(dp(3)); } catch (Exception ignored) {}
        int p = dp(20);
        c.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        c.setLayoutParams(lp);
        return c;
    }

    private View gap(int d) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(d)));
        return v;
    }

    private TextView section(String s) {
        TextView t = text(s.toUpperCase(Locale.US), 11, LABEL, monoBold);
        t.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.bottomMargin = dp(10);
        lp.topMargin = dp(20);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView tinyLabel(String s) {
        return text(s.toUpperCase(Locale.US), 10, LABEL, monoBold);
    }

    private TextView text(String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setLineSpacing(dp(2), 1f);
        return t;
    }

    private TextView pill(String s, int fill, int txt, Runnable onR) { return pill(s, fill, txt, onR, -2, 46); }

    private TextView pill(String s, int fill, int txt, Runnable onR, int widthDp, int heightDp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(txt);
        t.setTextSize(13);
        t.setTypeface(monoBold);
        t.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(40));
        bg.setColor(fill);
        if (fill == 0x00000000) bg.setStroke(dp(1), LINE);
        t.setBackground(bg);
        t.setPadding(dp(16), 0, dp(16), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(widthDp == -2 ? -2 : dp(widthDp), dp(heightDp));
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        final boolean primary = fill != 0x00000000;
        t.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // Primary = glyph-press: deep spring + strong haptic.
                    // Ghost = fingerprint-dot: shallow breathe + light tick.
                    v.performHapticFeedback(primary
                            ? HapticFeedbackConstants.VIRTUAL_KEY
                            : HapticFeedbackConstants.CLOCK_TICK);
                    v.animate().scaleX(primary ? 0.86f : 0.93f).scaleY(primary ? 0.86f : 0.93f)
                            .setDuration(primary ? 90 : 70)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(primary ? 320 : 220)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(primary ? 3.2f : 2.0f)).start();
                    break;
            }
            return false;
        });
        if (onR != null) t.setOnClickListener(v -> onR.run());
        return t;
    }

    // Glyph-matrix success flash: the card breathes once when something is done.
    private void successPulse(View card) {
        if (card == null) return;
        try {
            card.animate().scaleX(1.025f).scaleY(1.025f).setDuration(120)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(2f))
                    .withEndAction(() -> {
                        try {
                            card.animate().scaleX(1f).scaleY(1f).setDuration(280)
                                    .setInterpolator(new android.view.animation.OvershootInterpolator(2.4f)).start();
                        } catch (Exception ignored) {}
                    }).start();
        } catch (Exception ignored) {}
    }

    private EditText field(LinearLayout parent, String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextSize(14);
        e.setTypeface(mono);
        e.setTextColor(Ink);
        e.setHintTextColor(LABEL);
        e.setPadding(dp(8), dp(10), dp(8), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(Theme.field());
        bg.setStroke(dp(1), LINE);
        e.setBackground(bg);
        e.setSingleLine(true);
        parent.addView(e, new LinearLayout.LayoutParams(-1, -2));
        parent.addView(gap(10));
        return e;
    }

    private Switch sw(LinearLayout parent, String label, boolean on) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextSize(14);
        sw.setTypeface(mono);
        sw.setTextColor(Ink);
        sw.setChecked(on);
        sw.setPadding(0, dp(8), 0, dp(8));
        parent.addView(sw);
        return sw;
    }

    private String nowHm() {
        Calendar c = Calendar.getInstance();
        return String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    private int dp(int d) { return (int) (d * getResources().getDisplayMetrics().density); }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    private boolean hasUsageAccess() {
        try {
            AppOpsManager aom = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int mode = aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) { return false; }
    }

    private void openUsageSettings() {
        try { startActivity(new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)); }
        catch (Exception e) { toast("Couldn't open that screen."); }
    }

    private boolean overlayAllowed() {
        return android.provider.Settings.canDrawOverlays(this);
    }

    private boolean guardianServiceEnabled() {
        try {
            String enabled = android.provider.Settings.Secure.getString(getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) return false;
            return enabled.contains("com.spark.app");
        } catch (Exception e) { return false; }
    }

    private void openOverlaySettings() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            try { startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)); }
            catch (Exception e2) { toast("Couldn't open overlay settings."); }
        }
    }

    private void openAccessibilitySettings() {
        try { startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
        catch (Exception e) { toast("Couldn't open accessibility settings."); }
    }

    // drawn progress dots - no font glyphs needed, so no tofu boxes
    static class DotRow extends View {
        private final int filled, total, onC, offC;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;

        DotRow(Context c, int filled, int total, int onColor, int offColor) {
            super(c);
            this.filled = filled; this.total = total; this.onC = onColor; this.offC = offColor;
            d = c.getResources().getDisplayMetrics().density;
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(1.4f * d);
        }

        @Override protected void onDraw(Canvas cv) {
            float r = 3.6f * d, gap = 20f * d;
            float cx = r, cy = getHeight() / 2f;
            for (int i = 0; i < total; i++) {
                if (i < filled) { fill.setColor(onC); cv.drawCircle(cx, cy, r, fill); }
                else { ring.setColor(offC); cv.drawCircle(cx, cy, r, ring); }
                cx += gap;
            }
        }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            float r = 3.6f * d, gap = 20f * d;
            setMeasuredDimension((int) (2 * r + (total - 1) * gap + 1), (int) (r * 2 + 2));
        }
    }
}
