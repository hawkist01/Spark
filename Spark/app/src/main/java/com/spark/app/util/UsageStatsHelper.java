package com.spark.app.util;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class UsageStatsHelper {
    public static final class UsageDay {
        public final long totalMinutes;
        public final List<String[]> topApps; // [label, minutes]
        public UsageDay(long totalMinutes, List<String[]> topApps) { this.totalMinutes = totalMinutes; this.topApps = topApps; }
    }

    private UsageStatsHelper() {}

    // Total foreground minutes inside any window. -1 when unavailable.
    public static long minutesForRange(Context ctx, long startMs, long endMs) {
        try {
            UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return -1;
            List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startMs, endMs);
            if (stats == null || stats.isEmpty()) return -1;
            long total = 0;
            for (UsageStats s : stats) {
                try { total += Math.max(0, s.getTotalTimeInForeground()); } catch (Exception ignored) {}
            }
            return total / 60_000L;
        } catch (Exception e) { return -1; }
    }

    public static UsageDay today(Context ctx) {
        try {
            UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            Calendar start = Calendar.getInstance();
            start.set(Calendar.HOUR_OF_DAY, 0); start.set(Calendar.MINUTE, 0); start.set(Calendar.SECOND, 0);
            Calendar end = Calendar.getInstance();
            List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start.getTimeInMillis(), end.getTimeInMillis());
            if (stats == null || stats.isEmpty()) return null;
            long total = 0;
            Map<String, Long> map = new HashMap<>();
            for (UsageStats s : stats) {
                long d = s.getTotalTimeInForeground();
                if (d <= 0) continue;
                total += d;
                String pkg = s.getPackageName();
                if (pkg == null) continue;
                map.put(pkg, (map.containsKey(pkg) ? map.get(pkg) : 0L) + d);
            }
            if (total <= 0) return null;
            List<Map.Entry<String, Long>> entries = new ArrayList<>(map.entrySet());
            entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            List<String[]> top = new ArrayList<>();
            for (int i = 0; i < entries.size() && i < 3; i++) {
                Map.Entry<String, Long> e = entries.get(i);
                String label = appLabel(ctx, e.getKey());
                top.add(new String[]{label, String.valueOf(Math.round(e.getValue() / 60_000.0))});
            }
            return new UsageDay(total / 60_000L, top);
        } catch (Exception e) { return null; }
    }

    private static String appLabel(Context ctx, String pkg) {
        try {
            android.content.pm.ApplicationInfo ai = ctx.getPackageManager().getApplicationInfo(pkg, 0);
            CharSequence label = ctx.getPackageManager().getApplicationLabel(ai);
            return label != null ? label.toString() : pkg;
        } catch (Exception e) { return pkg; }
    }
}
