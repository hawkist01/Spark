package com.spark.app.util;

import android.content.Context;

import com.spark.app.data.Dates;
import com.spark.app.data.DayStat;
import com.spark.app.data.Med;
import com.spark.app.data.Store;
import com.spark.app.data.WeeklyReport;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

// Weekend algorithm: every Saturday 21:00 the week gets a kind report.
// Pillars: water, food, movement, medication, sleep, phone-rest. Study adds bonus.
// Untracked pillars are simply left out — a gap is information, never a verdict.
public final class Weekly {
    private Weekly() {}

    public static final class Pillar {
        public final String name, detail;
        public final int score; // 0..100
        public Pillar(String name, int score, String detail) {
            this.name = name; this.score = score; this.detail = detail;
        }
    }

    public static final class Result {
        public final int score;
        public final String summary;
        public final List<Pillar> pillars;
        public final int studyMin;
        public Result(int score, String summary, List<Pillar> pillars, int studyMin) {
            this.score = score; this.summary = summary; this.pillars = pillars; this.studyMin = studyMin;
        }
    }

    private static String dateDaysAgo(int ago) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -ago);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    }

    // Live DayStat for today (not yet snapshotted).
    private static DayStat todayLive(Context ctx) {
        Store.ensure(ctx);
        DayStat d = new DayStat(Dates.today());
        d.water = Store.data.waterToday; d.food = Store.data.foodToday;
        d.pings = Store.data.pingsToday; d.sparks = Store.data.sparksToday;
        d.studyMin = Store.data.studyMinToday + Store.studyElapsedMin();
        d.sleepHalf = Store.data.sleepHalfToday;
        int taken = 0, planned = 0;
        for (Med m : Store.data.meds) {
            planned += m.times.size();
            taken += Store.data.takenToday.getOrDefault(m.id, 0);
        }
        d.medsTaken = taken; d.medsPlanned = planned;
        try {
            UsageStatsHelper.UsageDay u = UsageStatsHelper.today(ctx);
            d.screenMin = u != null ? (int) u.totalMinutes : -1;
        } catch (Exception ignored) {}
        return d;
    }

    public static Result compute(Context ctx) {
        Store.ensure(ctx);
        List<DayStat> days = new ArrayList<>();
        for (int ago = 6; ago >= 1; ago--) {
            DayStat h = Store.data.dayHistory.get(dateDaysAgo(ago));
            if (h != null) days.add(h);
        }
        days.add(todayLive(ctx));

        double wSum = 0, fSum = 0, pSum = 0;
        int wDays = 0, fDays = 0, pDays = 0;
        int medTaken = 0, medPlanned = 0, studyMin = 0;
        double sleepSum = 0;
        int sleepDays = 0, screenSum = 0, screenDays = 0;
        for (DayStat d : days) {
            boolean anyLog = d.water + d.food + d.pings + d.sparks + d.studyMin > 0
                    || d.sleepHalf > 0 || d.medsTaken > 0;
            if (!anyLog && d.screenMin < 0) continue; // untouched day: skip, don't punish
            wSum += Math.min(d.water, 8); fSum += Math.min(d.food, 5); pSum += Math.min(d.pings, 8);
            wDays++; fDays++; pDays++;
            medTaken += Math.min(d.medsTaken, d.medsPlanned); medPlanned += d.medsPlanned;
            studyMin += d.studyMin;
            if (d.sleepHalf > 0) { sleepSum += d.sleepHalf / 2.0; sleepDays++; }
            if (d.screenMin >= 0) { screenSum += Math.min(d.screenMin, 1440); screenDays++; }
        }

        List<Pillar> pillars = new ArrayList<>();
        if (wDays > 0) {
            double avg = wSum / 7.0;
            pillars.add(new Pillar("Water", (int) Math.min(100, avg / 5 * 100),
                    String.format(Locale.US, "%.1f/5 glasses a day", avg)));
        }
        if (fDays > 0) {
            double avg = fSum / 7.0;
            pillars.add(new Pillar("Food", (int) Math.min(100, avg / 3 * 100),
                    String.format(Locale.US, "%.1f/3 meals a day", avg)));
        }
        if (pDays > 0) {
            double avg = pSum / 7.0;
            pillars.add(new Pillar("Movement", (int) Math.min(100, avg / 3 * 100),
                    String.format(Locale.US, "%.1f movement moments a day", avg)));
        }
        if (medPlanned > 0) {
            int s = (int) Math.min(100, medTaken * 100.0 / medPlanned);
            pillars.add(new Pillar("Medication", s, medTaken + "/" + medPlanned + " doses taken"));
        }
        if (sleepDays > 0) {
            double avg = sleepSum / sleepDays;
            int s = (int) Math.max(0, 100 - Math.abs(avg - 8) / 8 * 120);
            pillars.add(new Pillar("Sleep", Math.min(100, s),
                    String.format(Locale.US, "%.1f h avg (8 h is the sweet spot)", avg)));
        }
        if (screenDays > 0) {
            double avg = screenSum / (double) screenDays;
            int s = (int) Math.max(0, Math.min(100, 100 - avg / 3));
            double rest = Math.max(0, 24 - avg / 60.0);
            pillars.add(new Pillar("Phone rest", s,
                    String.format(Locale.US, "%.1f h screen/day, %.1f h rest", avg / 60.0, rest)));
        }

        int total = 0;
        if (!pillars.isEmpty()) {
            int sum = 0;
            for (Pillar p : pillars) sum += p.score;
            total = Math.round(sum / (float) pillars.size());
        }
        total = Math.min(100, total + Math.min(10, studyMin / 30)); // study bonus, max +10

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pillars.size(); i++) {
            Pillar p = pillars.get(i);
            if (i > 0) sb.append(" | ");
            sb.append(p.name).append(" ").append(p.score);
        }
        if (studyMin > 0) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("Study ").append(studyMin).append(" min");
        }
        String kind = total >= 70 ? "A warm week. Keep the rhythm."
                : total >= 40 ? "A human week. Small steps still count."
                : pillars.isEmpty() ? "A quiet week. Logging one glass of water is a fine start."
                : "A rough week. Rest counts too — one tiny spark tomorrow.";
        String summary = (sb.length() > 0 ? sb.toString() + ". " : "") + kind;
        return new Result(total, summary, pillars, studyMin);
    }

    public static WeeklyReport buildReport(Context ctx) {
        Result r = compute(ctx);
        return new WeeklyReport(Dates.weekStart(Dates.today()), r.score, r.summary);
    }
}
