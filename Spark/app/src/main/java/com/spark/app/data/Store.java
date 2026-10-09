package com.spark.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Store {
    private static final String PREFS = "spark";
    private static final String KEY = "data";
    private static SharedPreferences prefs;
    private static boolean inited;
    public static volatile AppData data = new AppData();

    private Store() {}

    public static void init(Context ctx) {
        if (inited) return;
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        data = load();
        inited = true;
        resetDailyIfNewDay(ctx);
    }

    public static void ensure(Context ctx) { if (!inited) init(ctx); }

    public static void addWater() { data.waterToday++; persist(); }
    public static void addFood() { data.foodToday++; persist(); }
    public static void addPing() { data.pingsToday++; persist(); }
    public static void setSleepHalf(int halfHours) {
        data.sleepHalfToday = Math.max(0, Math.min(24, halfHours)); persist();
    }
    public static void addSpark() {
        data.sparksToday++;
        String t = Dates.today();
        data.sparksHistory.put(t, data.sparksHistory.getOrDefault(t, 0) + 1);
        persist();
    }
    public static void addCreativitySpark(String text) {
        data.creativitySparks.add(Dates.today() + "  |  " + text);
        persist();
    }
    public static void addCapture(String text) {
        data.captures.add(Dates.today() + "  |  " + text);
        persist();
    }
    public static void addWonderSpark(String text) {
        data.wonderSparks.add(Dates.today() + "  |  " + text);
        persist();
    }
    public static void markQuestDone(String id) {
        String t = Dates.today();
        for (Quest q : data.quests) if (q.id.equals(id) && !q.doneDays.contains(t)) q.doneDays.add(t);
        persist();
    }
    public static void undoQuest(String id) {
        String t = Dates.today();
        for (Quest q : data.quests) if (q.id.equals(id)) q.doneDays.remove(t);
        persist();
    }
    public static void setQuestNotes(String id, String notes) {
        for (Quest q : data.quests) if (q.id.equals(id)) q.notes = notes;
        persist();
    }
    public static void upsertQuest(Quest q) {
        for (int i = 0; i < data.quests.size(); i++) if (data.quests.get(i).id.equals(q.id)) { data.quests.set(i, q); persist(); return; }
        data.quests.add(q); persist();
    }
    public static void addQuest(Quest q) { data.quests.add(q); persist(); }
    public static void deleteQuest(String id) {
        for (int i = 0; i < data.quests.size(); i++) if (data.quests.get(i).id.equals(id)) { data.quests.remove(i); break; }
        persist();
    }
    public static void markMedTaken(String medId) {
        String t = Dates.today(); String key = medId + "#" + t;
        data.takenToday.put(medId, data.takenToday.getOrDefault(medId, 0) + 1);
        data.takenHistory.put(key, data.takenHistory.getOrDefault(key, 0) + 1);
        persist();
    }
    public static void upsertMed(Med m) {
        for (int i = 0; i < data.meds.size(); i++) if (data.meds.get(i).id.equals(m.id)) { data.meds.set(i, m); persist(); return; }
        data.meds.add(m); persist();
    }
    public static void addMed(Med m) { data.meds.add(m); persist(); }
    public static void deleteMed(String id) {
        for (int i = 0; i < data.meds.size(); i++) if (data.meds.get(i).id.equals(id)) { data.meds.remove(i); break; }
        persist();
    }
    public static void setReflection(String text) { data.reflection = text; persist(); }
    public static void saveReflectionWeek(String text) {
        String start = Dates.weekStart(Dates.today());
        for (int i = 0; i < data.reflections.size(); i++)
            if (data.reflections.get(i).weekStart.equals(start)) { data.reflections.set(i, new Reflection(start, text)); data.reflection = text; persist(); return; }
        data.reflections.add(new Reflection(start, text));
        data.reflection = text;
        persist();
    }

    public static void resetDailyIfNewDay(android.content.Context ctx) {
        String t = Dates.today();
        String last = prefs.getString("lastDay", "");
        if (last.isEmpty()) { prefs.edit().putString("lastDay", t).apply(); return; }
        if (!last.equals(t)) {
            snapshotDay(ctx, last);
            prefs.edit().putString("lastDay", t).apply();
            int prev = data.sparksToday;
            data.sparksToday = 0; data.pingsToday = 0; data.waterToday = 0;
            data.foodToday = 0; data.studyMinToday = 0; data.sleepHalfToday = 0;
            data.takenToday.clear();
            if (data.lastWeekSpark == 0) data.lastWeekSpark = prev;
            persist();
        }
    }

    public static void resetDailyIfNewDay() { resetDailyIfNewDay(null); }

    // Freeze yesterday's essentials into history before counters clear.
    // Gaps stay gaps — never zeroes held against anyone.
    private static void snapshotDay(android.content.Context ctx, String date) {
        try {
            DayStat d = new DayStat(date);
            d.water = data.waterToday; d.food = data.foodToday; d.pings = data.pingsToday;
            d.sparks = data.sparksToday; d.studyMin = data.studyMinToday; d.sleepHalf = data.sleepHalfToday;
            int taken = 0, planned = 0;
            for (Med m : data.meds) {
                planned += m.times.size();
                taken += data.takenToday.getOrDefault(m.id, 0);
            }
            d.medsTaken = taken; d.medsPlanned = planned;
            if (ctx != null) {
                try {
                    java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
                    java.util.Calendar s = java.util.Calendar.getInstance();
                    s.setTime(f.parse(date));
                    s.set(java.util.Calendar.HOUR_OF_DAY, 0); s.set(java.util.Calendar.MINUTE, 0); s.set(java.util.Calendar.SECOND, 0);
                    java.util.Calendar e = (java.util.Calendar) s.clone();
                    e.set(java.util.Calendar.HOUR_OF_DAY, 23); e.set(java.util.Calendar.MINUTE, 59); e.set(java.util.Calendar.SECOND, 59);
                    long mins = com.spark.app.util.UsageStatsHelper.minutesForRange(ctx, s.getTimeInMillis(), e.getTimeInMillis());
                    d.screenMin = mins >= 0 ? (int) Math.min(mins, 1440) : -1;
                } catch (Exception ignored) {}
            }
            data.dayHistory.put(date, d);
        } catch (Exception ignored) {}
    }

    // ---- study sessions (in-app Study mode mirrors Nothing's Study Mode) ----
    public static void startStudySession() {
        prefs.edit().putLong("studyStart", System.currentTimeMillis()).apply();
    }
    public static int stopStudySession() {
        long start = prefs.getLong("studyStart", 0);
        prefs.edit().remove("studyStart").apply();
        if (start <= 0) return 0;
        int mins = (int) Math.max(1, (System.currentTimeMillis() - start) / 60_000L);
        data.studyMinToday += mins; persist();
        return mins;
    }
    public static boolean studying() { return prefs.getLong("studyStart", 0) > 0; }
    public static int studyElapsedMin() {
        long start = prefs.getLong("studyStart", 0);
        if (start <= 0) return 0;
        return (int) ((System.currentTimeMillis() - start) / 60_000L);
    }

    public static void saveWeeklyReport(WeeklyReport r) {
        for (int i = 0; i < data.weeklyReports.size(); i++) {
            if (data.weeklyReports.get(i).weekStart.equals(r.weekStart)) {
                data.weeklyReports.set(i, r); persist(); return;
            }
        }
        data.weeklyReports.add(r); persist();
    }

    public static void persist() { prefs.edit().putString(KEY, serialize(data)).apply(); }

    private static String serialize(AppData a) {
        try {
            JSONObject o = new JSONObject();
            JSONArray qa = new JSONArray();
            for (Quest q : a.quests) {
                JSONObject jo = new JSONObject();
                jo.put("id", q.id); jo.put("title", q.title); jo.put("why", q.why);
                jo.put("tiny", q.tiny); jo.put("deep", q.deep); jo.put("notes", q.notes); jo.put("cue", q.cue);
                jo.put("remindAt", q.remindAt); jo.put("sound", q.sound);
                jo.put("doneDays", new JSONArray(q.doneDays));
                qa.put(jo);
            }
            o.put("quests", qa);
            JSONArray ma = new JSONArray();
            for (Med m : a.meds) {
                JSONObject jo = new JSONObject();
                jo.put("id", m.id); jo.put("name", m.name); jo.put("note", m.note); jo.put("times", new JSONArray(m.times));
                ma.put(jo);
            }
            o.put("meds", ma);
            Settings s = a.settings;
            JSONObject so = new JSONObject();
            so.put("seedHour", s.seedHour); so.put("seedMinute", s.seedMinute); so.put("seedEnabled", s.seedEnabled);
            so.put("pingEnabled", s.pingEnabled); so.put("pingIntervalMin", s.pingIntervalMin);
            so.put("pingStartHour", s.pingStartHour); so.put("pingEndHour", s.pingEndHour);
            so.put("snoozeMin", s.snoozeMin); so.put("quietEnabled", s.quietEnabled);
            so.put("quietStartHour", s.quietStartHour); so.put("quietEndHour", s.quietEndHour);
            so.put("calmMode", s.calmMode); so.put("userName", s.userName);
            so.put("winddownEnabled", s.winddownEnabled); so.put("windDownHour", s.windDownHour); so.put("windDownMin", s.windDownMin);
            so.put("guardEnabled", s.guardEnabled); so.put("guardThresholdMin", s.guardThresholdMin); so.put("guardAllowMin", s.guardAllowMin);
            so.put("guardTheme", s.guardTheme);
            so.put("darkMode", s.darkMode);
            so.put("themeStyle", s.themeStyle);
            so.put("rhythmAdaptive", s.rhythmAdaptive);
            so.put("chronoShiftMin", s.chronoShiftMin);
            so.put("medWindowMin", s.medWindowMin);
            so.put("medNudgeRepeat", s.medNudgeRepeat);
            o.put("settings", so);
            o.put("takenToday", new JSONObject(a.takenToday));
            o.put("takenHistory", new JSONObject(a.takenHistory));
            o.put("sparksHistory", new JSONObject(a.sparksHistory));
            o.put("sparksToday", a.sparksToday); o.put("pingsToday", a.pingsToday);
            o.put("waterToday", a.waterToday); o.put("reflection", a.reflection);
            o.put("foodToday", a.foodToday); o.put("studyMinToday", a.studyMinToday);
            o.put("sleepHalfToday", a.sleepHalfToday);
            o.put("lastWeekSpark", a.lastWeekSpark);
            JSONObject hist = new JSONObject();
            for (Map.Entry<String, DayStat> e : a.dayHistory.entrySet()) {
                try { hist.put(e.getKey(), e.getValue().toJson()); } catch (Exception ignored) {}
            }
            o.put("dayHistory", hist);
            JSONArray wr = new JSONArray();
            for (WeeklyReport r : a.weeklyReports) wr.put(r.toJson());
            o.put("weeklyReports", wr);
            JSONArray ra = new JSONArray();
            for (Reflection r : a.reflections) {
                JSONObject jo = new JSONObject(); jo.put("weekStart", r.weekStart); jo.put("text", r.text); ra.put(jo);
            }
            o.put("reflections", ra);
            o.put("creativitySparks", new JSONArray(a.creativitySparks));
            o.put("guardedApps", new JSONArray(a.guardedApps));
            o.put("captures", new JSONArray(a.captures));
            o.put("wonderSparks", new JSONArray(a.wonderSparks));
            o.put("soundMap", new JSONObject(a.soundMap));
            return o.toString();
        } catch (Exception e) { return "{}"; }
    }

    private static AppData load() {
        String raw = prefs.getString(KEY, null);
        if (raw == null) return new AppData();
        try {
            JSONObject o = new JSONObject(raw);
            AppData a = new AppData();
            a.quests.clear();
            JSONArray qa = o.optJSONArray("quests");
            if (qa != null) {
                for (int i = 0; i < qa.length(); i++) {
                    JSONObject jo = qa.getJSONObject(i);
                    Quest q = new Quest(jo.optString("id", "q" + i), jo.optString("title", ""),
                            jo.optString("why", ""), jo.optString("tiny", ""), jo.optString("deep", ""));
                    q.notes = jo.optString("notes", ""); q.cue = jo.optString("cue", "");
                    q.remindAt = jo.optString("remindAt", ""); q.sound = jo.optString("sound", "spark");
                    JSONArray dd = jo.optJSONArray("doneDays");
                    if (dd != null) for (int j = 0; j < dd.length(); j++) q.doneDays.add(dd.optString(j));
                    a.quests.add(q);
                }
            }
            a.meds.clear();
            JSONArray ma = o.optJSONArray("meds");
            if (ma != null) {
                for (int i = 0; i < ma.length(); i++) {
                    JSONObject jo = ma.getJSONObject(i);
                    Med m = new Med(jo.optString("id", "m" + i), jo.optString("name", ""), jo.optString("note", ""));
                    JSONArray tt = jo.optJSONArray("times");
                    if (tt != null) for (int j = 0; j < tt.length(); j++) m.times.add(tt.optString(j));
                    a.meds.add(m);
                }
            }
            JSONObject so = o.optJSONObject("settings");
            if (so != null) {
                Settings s = a.settings;
                s.seedHour = so.optInt("seedHour", 9); s.seedMinute = so.optInt("seedMinute", 0);
                s.seedEnabled = so.optBoolean("seedEnabled", true);
                s.pingEnabled = so.optBoolean("pingEnabled", true); s.pingIntervalMin = so.optInt("pingIntervalMin", 90);
                s.pingStartHour = so.optInt("pingStartHour", 9); s.pingEndHour = so.optInt("pingEndHour", 22);
                s.snoozeMin = so.optInt("snoozeMin", 30); s.quietEnabled = so.optBoolean("quietEnabled", false);
                s.quietStartHour = so.optInt("quietStartHour", 23); s.quietEndHour = so.optInt("quietEndHour", 8);
                s.calmMode = so.optBoolean("calmMode", false); s.userName = so.optString("userName", "friend");
                s.winddownEnabled = so.optBoolean("winddownEnabled", true); s.windDownHour = so.optInt("windDownHour", 21); s.windDownMin = so.optInt("windDownMin", 30);
                s.guardEnabled = so.optBoolean("guardEnabled", true); s.guardThresholdMin = so.optInt("guardThresholdMin", 15); s.guardAllowMin = so.optInt("guardAllowMin", 5);
                s.guardTheme = so.optInt("guardTheme", 0);
                s.darkMode = so.optBoolean("darkMode", false);
                s.themeStyle = so.optInt("themeStyle", 0);
                s.rhythmAdaptive = so.optBoolean("rhythmAdaptive", true);
                s.chronoShiftMin = so.optInt("chronoShiftMin", 180);
                s.medWindowMin = so.optInt("medWindowMin", 90);
                s.medNudgeRepeat = so.optBoolean("medNudgeRepeat", true);
            }
            a.sparksToday = o.optInt("sparksToday", 0);
            a.pingsToday = o.optInt("pingsToday", 0);
            a.waterToday = o.optInt("waterToday", 0);
            a.foodToday = o.optInt("foodToday", 0);
            a.studyMinToday = o.optInt("studyMinToday", 0);
            a.sleepHalfToday = o.optInt("sleepHalfToday", 0);
            a.lastWeekSpark = o.optInt("lastWeekSpark", 0);
            JSONObject hist = o.optJSONObject("dayHistory");
            if (hist != null) {
                java.util.Iterator<String> it = hist.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    try { a.dayHistory.put(k, DayStat.fromJson(hist.getJSONObject(k))); } catch (Exception ignored) {}
                }
            }
            JSONArray wra = o.optJSONArray("weeklyReports");
            if (wra != null) for (int i = 0; i < wra.length(); i++) {
                try { a.weeklyReports.add(WeeklyReport.fromJson(wra.getJSONObject(i))); } catch (Exception ignored) {}
            }
            a.reflection = o.optString("reflection", "");
            copyIntMap(o.optJSONObject("takenToday"), a.takenToday);
            copyIntMap(o.optJSONObject("takenHistory"), a.takenHistory);
            copyIntMap(o.optJSONObject("sparksHistory"), a.sparksHistory);
            JSONArray ra = o.optJSONArray("reflections");
            if (ra != null) for (int i = 0; i < ra.length(); i++) {
                JSONObject jo = ra.getJSONObject(i);
                a.reflections.add(new Reflection(jo.optString("weekStart", ""), jo.optString("text", "")));
            }
            JSONArray ca = o.optJSONArray("creativitySparks");
            if (ca != null) for (int i = 0; i < ca.length(); i++) a.creativitySparks.add(ca.optString(i));
            a.guardedApps.clear();
            JSONArray ga = o.optJSONArray("guardedApps");
            if (ga != null) for (int i = 0; i < ga.length(); i++) a.guardedApps.add(ga.optString(i));
            if (a.guardedApps.isEmpty()) a.guardedApps.addAll(GuardianApps.defaults());
            JSONArray cap = o.optJSONArray("captures");
            if (cap != null) for (int i = 0; i < cap.length(); i++) a.captures.add(cap.optString(i));
            JSONArray ws = o.optJSONArray("wonderSparks");
            if (ws != null) for (int i = 0; i < ws.length(); i++) a.wonderSparks.add(ws.optString(i));
            JSONObject sm = o.optJSONObject("soundMap");
            if (sm != null) { java.util.Iterator<String> it = sm.keys(); while (it.hasNext()) { String k = it.next(); a.soundMap.put(k, sm.optString(k)); } }
            return a;
        } catch (Exception e) { return new AppData(); }
    }

    private static void copyIntMap(JSONObject src, Map<String, Integer> dst) {
        if (src == null) return;
        java.util.Iterator<String> it = src.keys();
        while (it.hasNext()) { String k = it.next(); dst.put(k, src.optInt(k, 0)); }
    }

    public static Quest firstPending() {
        String t = Dates.today();
        for (Quest q : data.quests) if (!q.doneToday(t)) return q;
        return null;
    }
}
