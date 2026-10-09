package com.spark.app.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

import com.spark.app.MainActivity;
import com.spark.app.R;
import com.spark.app.alarm.ActionReceiver;
import com.spark.app.data.Store;

public final class Notifier {
    // One channel per task identity - sound lives on the channel (Android 8+ rule).
    public static final String CH_SPARK = "spark2";
    public static final String CH_MED = "med2";
    public static final String CH_BODY = "body2";
    public static final String CH_GUARD = "guard2";
    public static final String CH_WIND = "wind2";
    public static final String CH_WONDER = "wonder2";
    public static final String CH_WEEK = "week";

    private Notifier() {}

    private static class Chan {
        final String id, name, task; final int importance;
        Chan(String id, String name, int importance, String task) {
            this.id = id; this.name = name; this.importance = importance; this.task = task;
        }
    }

    private static final Chan[] CHANNELS = {
        new Chan(CH_SPARK,  "Gentle nudges",       NotificationManager.IMPORTANCE_HIGH, "spark"),
        new Chan(CH_MED,    "Medication rhythm",   NotificationManager.IMPORTANCE_HIGH, "med"),
        new Chan(CH_BODY,   "Body & movement",     NotificationManager.IMPORTANCE_HIGH, "jog"),
        new Chan(CH_GUARD,  "Guardian",            NotificationManager.IMPORTANCE_HIGH, "doom"),
        new Chan(CH_WIND,   "Sleep wind-down",     NotificationManager.IMPORTANCE_HIGH, "sleep"),
        new Chan(CH_WONDER, "Wonder & study",      NotificationManager.IMPORTANCE_HIGH, "wonder"),
        new Chan(CH_WEEK,   "Weekly report",       NotificationManager.IMPORTANCE_HIGH, "wonder"),
    };

    public static void ensureChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return;
        Store.ensure(ctx);
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        // Channels are immutable on Android 8+ - the only way a sound change (or an old
        // broken channel from v2.0) takes effect is delete-then-recreate. Wipe everything
        // that belongs to us, then rebuild with the current sound per task.
        String[] prefixes = {"spark", "med", "body", "guard", "wind", "wonder", "seed", "week"};
        for (NotificationChannel existing : nm.getNotificationChannels()) {
            String id = existing.getId();
            for (String p : prefixes) {
                if (id.startsWith(p)) {
                    try { nm.deleteNotificationChannel(id); } catch (Exception ignored) {}
                    break;
                }
            }
        }
        for (Chan c : CHANNELS) {
            NotificationChannel ch = new NotificationChannel(c.id, c.name, c.importance);
            Uri u = SoundManager.resolve(ctx, c.task);
            ch.setSound(u, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 170, 90, 130});
            ch.enableLights(true);
            ch.setLightColor(0xFF6E5BE0);
            try { nm.createNotificationChannel(ch); } catch (Exception ignored) {}
        }
    }

    private static PendingIntent contentIntent(Context ctx) {
        Intent i = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent action(Context ctx, String act, String data, int code, int nid) {
        Intent i = new Intent(ctx, ActionReceiver.class)
                .setAction(act)
                .putExtra("data", data)
                .putExtra("nid", nid);
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Icon icon(Context ctx) { return Icon.createWithResource(ctx, R.drawable.ic_notif); }

    private static Notification.Builder base(Context ctx, String channel) {
        Notification.Builder b = new Notification.Builder(ctx, channel)
                .setSmallIcon(R.drawable.ic_notif)
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(contentIntent(ctx));
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            try { b.setCategory(Notification.CATEGORY_REMINDER); } catch (Exception ignored) {}
        }
        return b;
    }

    private static void show(Context ctx, int id, Notification n) {
        try { ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id, n); } catch (Exception ignored) {}
    }

    public static void seed(Context ctx, String title, String text, String extra) {
        String body = (extra == null || extra.isEmpty()) ? text : text + "\n\n" + extra;
        Notification.Builder b = base(ctx, CH_SPARK)
                .setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .addAction(new Notification.Action.Builder(icon(ctx), "I'll try", action(ctx, "open", "", 41, 1001)).build())
                .addAction(new Notification.Action.Builder(icon(ctx), "Snooze", action(ctx, "snooze", "seed", 42, 1001)).build());
        show(ctx, 1001, b.build());
    }

    public static void med(Context ctx, String name, String time, String medId) {
        int nid = (medId + time).hashCode();
        if (nid == Integer.MIN_VALUE) nid = 1;
        Notification.Builder b = base(ctx, CH_MED)
                .setContentTitle(name).setContentText("Scheduled dose " + time)
                .addAction(new Notification.Action.Builder(icon(ctx), "Took it", action(ctx, "med_taken", medId + "|" + time, 51, nid)).build())
                .addAction(new Notification.Action.Builder(icon(ctx), "Later", action(ctx, "med_snooze", medId + "|" + time + "|" + name, 52, nid)).build());
        show(ctx, nid, b.build());
    }

    public static void ping(Context ctx, String text) {
        Notification.Builder b = base(ctx, CH_BODY)
                .setContentTitle("Move time").setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText("Stand up. " + text))
                .addAction(new Notification.Action.Builder(icon(ctx), "Did it", action(ctx, "ping_done", "", 61, 1002)).build())
                .addAction(new Notification.Action.Builder(icon(ctx), "Snooze", action(ctx, "snooze", "ping", 62, 1002)).build());
        show(ctx, 1002, b.build());
    }

    public static void curiosity(Context ctx, String text) {
        Notification.Builder b = base(ctx, CH_WONDER)
                .setContentTitle("A 3-min spark").setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text));
        show(ctx, 1003, b.build());
    }

    public static void winddown(Context ctx, String text) {
        Notification.Builder b = base(ctx, CH_WIND)
                .setContentTitle("Wind down time").setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .addAction(new Notification.Action.Builder(icon(ctx), "Good night", action(ctx, "open", "", 71, 1004)).build());
        show(ctx, 1004, b.build());
    }

    // One task's own reminder time fired. Channel follows the task's own sound.
    public static void taskDue(Context ctx, com.spark.app.data.Quest q) {
        String extra = q.tiny + ((q.cue != null && !q.cue.isEmpty()) ? "\n\nPlan: " + q.cue : "");
        String ch = CH_SPARK;
        String s = q.sound != null ? q.sound : "spark";
        if (s.equals("wonder")) ch = CH_WONDER;
        else if (s.equals("med")) ch = CH_MED;
        else if (s.equals("jog")) ch = CH_BODY;
        else if (s.equals("sleep")) ch = CH_WIND;
        else if (s.equals("doom")) ch = CH_GUARD;
        Notification.Builder b = base(ctx, ch)
                .setContentTitle(q.title).setContentText(q.tiny)
                .setStyle(new Notification.BigTextStyle().bigText(extra))
                .addAction(new Notification.Action.Builder(icon(ctx), "Did the 2-min", action(ctx, "quest_done", q.id, 91, 1006)).build())
                .addAction(new Notification.Action.Builder(icon(ctx), "Snooze", action(ctx, "snooze", "seed", 92, 1006)).build());
        show(ctx, 1006, b.build());
    }

    // Saturday 21:00 weekend report. Tapping the body opens the Week tab.
    public static void weekly(Context ctx, int score, String summary) {
        android.content.Intent i = new android.content.Intent(ctx, MainActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.putExtra("tab", 1);
        android.app.PendingIntent weekTap = android.app.PendingIntent.getActivity(ctx, 94, i,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = base(ctx, CH_WEEK)
                .setContentTitle("Your week: " + score + " / 100")
                .setContentText(summary)
                .setContentIntent(weekTap)
                .setStyle(new Notification.BigTextStyle().bigText(summary + "\n\nTap to open the Week tab."))
                .addAction(new Notification.Action.Builder(icon(ctx), "See my week", action(ctx, "open", "", 93, 1007)).build());
        show(ctx, 1007, b.build());
    }

    // Guardian - plays the doom sound via its channel; full-screen takeover as backup
    public static void guard(Context ctx, String text) {
        Notification.Builder b = base(ctx, CH_GUARD)
                .setContentTitle("Guardian").setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setFullScreenIntent(contentIntent(ctx), true)
                .addAction(new Notification.Action.Builder(icon(ctx), "Breathe", action(ctx, "open", "", 81, 1005)).build());
        show(ctx, 1005, b.build());
    }
}
