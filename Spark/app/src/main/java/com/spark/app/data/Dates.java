package com.spark.app.data;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class Dates {
    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private Dates() {}

    public static String today() { return FMT.format(new Date()); }
    public static String todayLong() { return new SimpleDateFormat("EEE, d MMM", Locale.US).format(new Date()); }
    public static int dayOfYear() { return Calendar.getInstance().get(Calendar.DAY_OF_YEAR); }

    public static String weekStart(String date) {
        Calendar c = Calendar.getInstance();
        try { c.setTime(FMT.parse(date)); } catch (Exception ignored) {}
        int diff = (c.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7;
        c.add(Calendar.DAY_OF_YEAR, -diff);
        return FMT.format(c.getTime());
    }

    public static String hhmm(int h, int m) { return String.format(Locale.US, "%02d:%02d", h, m); }

    public static int[] parseHm(String s) {
        String[] p = s.split(":");
        int h = p.length > 0 ? parseInt(p[0], 9) : 9;
        int m = p.length > 1 ? parseInt(p[1], 0) : 0;
        return new int[]{h, m};
    }

    private static int parseInt(String s, int d) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return d; }
    }
}
