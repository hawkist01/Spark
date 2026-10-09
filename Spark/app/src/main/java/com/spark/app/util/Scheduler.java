package com.spark.app.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.spark.app.alarm.AlarmReceiver;
import com.spark.app.data.AppData;
import com.spark.app.data.Dates;
import com.spark.app.data.Med;
import com.spark.app.data.Settings;
import com.spark.app.data.Store;

import java.util.Calendar;

public final class Scheduler {
    private Scheduler() {}

    public static void rescheduleAll(Context ctx, AppData d) {
        Notifier.ensureChannels(ctx);
        scheduleSeed(ctx, d.settings.seedEnabled ? d.settings : null);
        scheduleMeds(ctx, d.meds);
        if (d.settings.pingEnabled) schedulePing(ctx, d.settings); else cancelPing(ctx);
        scheduleWinddown(ctx, d.settings);
        scheduleWeekly(ctx);
        scheduleQuests(ctx, d.quests);
    }

    public static void scheduleWinddown(Context ctx, Settings s) {
        PendingIntent p = pi(ctx, "wind", 300, i -> {});
        am(ctx).cancel(p);
        if (s == null || !s.winddownEnabled) return;
        // windDownHourEff() applies the rhythm shift, so a night-leaning day
        // gets its wind-down at its own 21:30 rather than wall-clock 21:30.
        Calendar c = calFor(s.windDownHourEff(), s.windDownMin);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1);
        setExact(ctx, c.getTimeInMillis(), p);
    }

    public static void scheduleWeekly(Context ctx) {
        PendingIntent p = pi(ctx, "weekly", 400, i -> {});
        am(ctx).cancel(p);
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SATURDAY);
        c.set(java.util.Calendar.HOUR_OF_DAY, 21);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(java.util.Calendar.DAY_OF_YEAR, 7);
        setExact(ctx, c.getTimeInMillis(), p);
    }

    // One alarm per task that has a reminder time. Extras don't affect
    // PendingIntent matching, so cancel-by-code always cleans the stale one.
    public static void scheduleQuests(Context ctx, java.util.List<com.spark.app.data.Quest> quests) {
        Store.ensure(ctx);
        Settings s = Store.data.settings;
        for (com.spark.app.data.Quest q : quests) {
            int code = ("quest_" + q.id).hashCode();
            PendingIntent p = pi(ctx, "quest", code, i -> i.putExtra("qid", q.id));
            am(ctx).cancel(p);
            if (q.remindAt == null || q.remindAt.isEmpty()) continue;
            // Quest reminders slide with the rest of the day, so a cue written
            // as "after dinner" still lands after the user's actual dinner.
            int[] hm = Dates.parseHm(q.remindAt);
            int mod = s.shiftMinutes(hm[0] * 60 + hm[1]);
            Calendar c = calFor(mod / 60, mod % 60);
            if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1);
            setExact(ctx, c.getTimeInMillis(), p);
        }
    }

    public static void cancelQuest(Context ctx, String qid) {
        try { am(ctx).cancel(pi(ctx, "quest", ("quest_" + qid).hashCode(), i -> {})); }
        catch (Exception ignored) {}
    }

    public static void rescheduleAfterChange(Context ctx) {
        Store.ensure(ctx);
        rescheduleAll(ctx, Store.data);
    }

    private static AlarmManager am(Context ctx) { return (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE); }

    private static boolean canExact(Context ctx) {
        AlarmManager a = am(ctx);
        return Build.VERSION.SDK_INT >= 31 ? a.canScheduleExactAlarms() : true;
    }

    private static void setExact(Context ctx, long at, PendingIntent pi) {
        AlarmManager a = am(ctx);
        if (canExact(ctx)) a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        else a.set(AlarmManager.RTC_WAKEUP, at, pi);
    }

    private static PendingIntent pi(Context ctx, String type, int code, java.util.function.Consumer<Intent> fill) {
        Intent i = new Intent(ctx, AlarmReceiver.class).putExtra("type", type);
        fill.accept(i);
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Calendar calFor(int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour); c.set(Calendar.MINUTE, minute); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    public static void scheduleSeed(Context ctx, Settings s) {
        PendingIntent p = pi(ctx, "seed", 100, i -> {});
        am(ctx).cancel(p);
        if (s == null) return;
        Calendar c = calFor(s.seedHourEff(), s.seedMinute);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1);
        setExact(ctx, c.getTimeInMillis(), p);
    }

    /**
     * One pair of alarms per dose: a reminder at the start of the dose window,
     * and a gentle follow-up at the end of it.
     *
     * The follow-up is the part that actually matters. Forgetting a dose is not
     * a timing failure, it is a memory failure, so nagging at exactly 21:00 and
     * then going silent is the wrong shape. The window gives the dose somewhere
     * to live, and the second nudge catches the case where the first one
     * arrived while the user was driving, showering or asleep.
     *
     * That second nudge is skipped for any medication already marked taken
     * today, so it can never nag about something that is done.
     */
    public static void scheduleMeds(Context ctx, java.util.List<Med> meds) {
        Store.ensure(ctx);
        Settings s = Store.data.settings;

        for (Med med : meds) {
            for (String time : med.times) {
                // Shift the dose onto the user's clock. A prescribed "night"
                // dose should land in the user's night, not at 21:00 sharp.
                int[] hm = Dates.parseHm(time);
                int mod = s.shiftMinutes(hm[0] * 60 + hm[1]);
                int eh = mod / 60, em = mod % 60;

                int code = (med.id + "|" + time).hashCode();
                PendingIntent p = pi(ctx, "med", code, i -> {
                    i.putExtra("name", med.name).putExtra("time", time).putExtra("medId", med.id);
                });
                am(ctx).cancel(p);
                Calendar c = calFor(eh, em);
                if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1);
                setExact(ctx, c.getTimeInMillis(), p);

                // End-of-window follow-up.
                int endCode = (med.id + "|" + time + "|end").hashCode();
                PendingIntent pe = pi(ctx, "med_end", endCode, i -> {
                    i.putExtra("name", med.name).putExtra("time", time).putExtra("medId", med.id);
                });
                am(ctx).cancel(pe);
                if (s.medNudgeRepeat) {
                    Calendar ce = calFor(eh, em);
                    ce.add(Calendar.MINUTE, Math.max(15, s.medWindowMin));
                    if (ce.getTimeInMillis() <= System.currentTimeMillis()) ce.add(Calendar.DAY_OF_YEAR, 1);
                    setExact(ctx, ce.getTimeInMillis(), pe);
                }
            }
        }
    }

    public static void schedulePing(Context ctx, Settings s) {
        PendingIntent p = pi(ctx, "ping", 200, i -> {});
        am(ctx).cancel(p);
        setExact(ctx, nextPing(s), p);
    }

    public static void cancelPing(Context ctx) { am(ctx).cancel(pi(ctx, "ping", 200, i -> {})); }

    private static long nextPing(Settings s) {
        Calendar now = Calendar.getInstance();
        // The movement ping follows the user's day, not the wall clock: the
        // active window slides with the rhythm shift so a 1am-sharp day still
        // gets its pings inside the hours it is actually awake for.
        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, s.pingStartEff()); start.set(Calendar.MINUTE, 0); start.set(Calendar.SECOND, 0);
        Calendar end = Calendar.getInstance();
        end.set(Calendar.HOUR_OF_DAY, s.pingEndEff()); end.set(Calendar.MINUTE, 0); end.set(Calendar.SECOND, 0);
        long interval = s.pingIntervalMin * 60_000L;
        Calendar tomorrowStart = Calendar.getInstance();
        tomorrowStart.setTimeInMillis(start.getTimeInMillis()); tomorrowStart.add(Calendar.DAY_OF_YEAR, 1);
        // A shifted window can wrap past midnight (e.g. 12:00 -> 01:00); in that
        // case "after end" is normal and the next ping is tomorrow's start.
        boolean wraps = !end.after(start);
        if (now.before(start)) return start.getTimeInMillis() + interval;
        if (!wraps && now.after(end)) return tomorrowStart.getTimeInMillis() + interval;
        Calendar cand = (Calendar) now.clone();
        cand.add(Calendar.MINUTE, s.pingIntervalMin);
        if (!wraps && cand.after(end)) return tomorrowStart.getTimeInMillis() + interval;
        return cand.getTimeInMillis();
    }

    public static void snooze(Context ctx, String what) {
        Store.ensure(ctx);
        Settings s = Store.data.settings;
        long at = System.currentTimeMillis() + s.snoozeMin * 60_000L + 1000L;
        int code = ("snooze_" + what).hashCode();
        PendingIntent p = pi(ctx, what.equals("med") ? "med" : what, code, i -> {});
        setExact(ctx, at, p);
    }

    // Snooze one exact dose (keeps its name/time so the re-fire is not blank).
    public static void snoozeMed(Context ctx, String medId, String time, String name) {
        Store.ensure(ctx);
        Settings s = Store.data.settings;
        long at = System.currentTimeMillis() + s.snoozeMin * 60_000L + 1000L;
        int code = ("snooze_med_" + medId + time).hashCode();
        PendingIntent p = pi(ctx, "med", code, i -> {
            i.putExtra("name", name != null && !name.isEmpty() ? name : "Medication");
            i.putExtra("time", time != null ? time : "");
            i.putExtra("medId", medId != null ? medId : "");
        });
        setExact(ctx, at, p);
    }
}
