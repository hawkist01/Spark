package com.spark.app.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.spark.app.data.Dates;
import com.spark.app.data.Store;

import java.io.File;
import java.io.FileWriter;
import java.util.Map;

public final class Export {
    private Export() {}

    public static String backupText(Context ctx) {
        Store.ensure(ctx);
        StringBuilder s = new StringBuilder();
        s.append("SPARK BACKUP  |  ").append(Dates.todayLong()).append("\n");
        s.append("================================\n\n");
        s.append("QUESTS\n");
        for (com.spark.app.data.Quest q : Store.data.quests) {
            s.append(" - ").append(q.title).append("  [done ").append(q.doneDays.size()).append(" days]");
            if (q.cue != null && !q.cue.isEmpty()) s.append("  Plan: ").append(q.cue);
            s.append("\n");
            if (q.notes != null && !q.notes.isEmpty()) s.append("   notes: ").append(q.notes).append("\n");
        }
        s.append("\nMEDICATIONS\n");
        for (com.spark.app.data.Med m : Store.data.meds)
            s.append(" - ").append(m.name).append("  @ ").append(android.text.TextUtils.join(" | ", m.times)).append("\n");
        s.append("\nSPARKS / WONDER\n");
        for (String x : Store.data.creativitySparks) s.append(" - ").append(x).append("\n");
        for (String x : Store.data.wonderSparks) s.append(" - ").append(x).append("\n");
        s.append("\nTHOUGHT CAPTURES\n");
        for (String x : Store.data.captures) s.append(" - ").append(x).append("\n");
        s.append("\nTODAY: sparks=").append(Store.data.sparksToday)
                .append("  pings=").append(Store.data.pingsToday)
                .append("  water=").append(Store.data.waterToday)
                .append("  food=").append(Store.data.foodToday)
                .append("  studyMin=").append(Store.data.studyMinToday)
                .append("  sleepH=").append(Store.data.sleepHalfToday / 2.0).append("\n");
        s.append("REFLECTION: ").append(Store.data.reflection).append("\n");
        if (!Store.data.weeklyReports.isEmpty()) {
            com.spark.app.data.WeeklyReport last =
                    Store.data.weeklyReports.get(Store.data.weeklyReports.size() - 1);
            s.append("LAST WEEK: ").append(last.score).append("/100  ").append(last.summary).append("\n");
        }
        s.append("================================\n");
        s.append("Everything above is stored on this phone already. This is just a copy.\n");
        return s.toString();
    }

    public static void toClipboard(Context ctx) {
        String text = backupText(ctx);
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Spark backup", text));
    }

    public static File writeFile(Context ctx) {
        try {
            File f = new File(ctx.getFilesDir(), "spark_backup.txt");
            FileWriter w = new FileWriter(f); w.write(backupText(ctx)); w.flush(); w.close();
            return f;
        } catch (Exception e) { return null; }
    }

    // Writes any text into a user-picked location (ACTION_CREATE_DOCUMENT).
    public static boolean writeToUri(Context ctx, Uri dest) {
        return writeTextToUri(ctx, dest, backupText(ctx));
    }

    public static boolean writeTextToUri(Context ctx, Uri dest, String text) {
        try {
            java.io.OutputStream os = ctx.getContentResolver().openOutputStream(dest);
            if (os == null) return false;
            os.write(text.getBytes("UTF-8"));
            os.flush(); os.close();
            return true;
        } catch (Exception e) { return false; }
    }

    // Wonder journal for one date (yyyy-MM-dd) as Markdown.
    public static String entriesMarkdown(Context ctx, String date) {
        Store.ensure(ctx);
        StringBuilder s = new StringBuilder();
        s.append("# Spark entries - ").append(date).append("\n\n");
        s.append("## Sparks\n");
        boolean any = false;
        for (String x : Store.data.creativitySparks) {
            if (x.startsWith(date)) { s.append("- ").append(x.substring(Math.min(date.length(), x.length())).trim()).append("\n"); any = true; }
        }
        for (String x : Store.data.wonderSparks) {
            if (x.startsWith(date)) { s.append("- ").append(x.substring(Math.min(date.length(), x.length())).trim()).append("\n"); any = true; }
        }
        if (!any) s.append("- (nothing yet)\n");
        s.append("\n## Thought captures\n");
        any = false;
        for (String x : Store.data.captures) {
            if (x.startsWith(date)) { s.append("- ").append(x.substring(Math.min(date.length(), x.length())).trim()).append("\n"); any = true; }
        }
        if (!any) s.append("- (nothing yet)\n");
        s.append("\n_Saved on this phone by Spark._\n");
        return s.toString();
    }

    public static Intent gmailIntent(Context ctx) {
        Intent i = new Intent(Intent.ACTION_SENDTO);
        i.setData(Uri.parse("mailto:"));
        i.putExtra(Intent.EXTRA_SUBJECT, "Spark backup | " + Dates.todayLong());
        i.putExtra(Intent.EXTRA_TEXT, backupText(ctx));
        return i;
    }
}
