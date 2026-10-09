package com.spark.app.util;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;

import com.spark.app.data.Store;

import java.util.Arrays;
import java.util.List;

public final class SoundManager {
    public static final List<String> TASKS = Arrays.asList("jog", "med", "bath", "doom", "sleep", "spark", "wonder", "miso");
    private SoundManager() {}

    public static String builtInUri(String task) {
        return "android.resource://com.spark.app/raw/task_" + task;
    }

    public static Uri resolve(Context ctx, String task) {
        Store.ensure(ctx);
        String stored = Store.data.soundMap.get(task);
        if (stored != null && !stored.isEmpty()) {
            try { return Uri.parse(stored); } catch (Exception ignored) {}
        }
        return Uri.parse(builtInUri(task));
    }

    public static String label(String task) {
        switch (task) {
            case "jog": return "Move / jog";
            case "med": return "Medication";
            case "bath": return "Bath / wind-down";
            case "doom": return "Guardian (scroll too long)";
            case "sleep": return "Sleep wind-down";
            case "spark": return "Spark / gentle nudge";
            case "wonder": return "Wonder / creative spark";
            case "miso": return "Miso the cat";
            default: return task;
        }
    }

    public static void setCustom(Context ctx, String task, String uri) {
        Store.ensure(ctx);
        if (uri == null || uri.isEmpty()) Store.data.soundMap.remove(task); else Store.data.soundMap.put(task, uri);
        Store.persist();
        Notifier.ensureChannels(ctx); // channel sound is immutable - rebuild with new id
    }

    public static void reset(Context ctx, String task) {
        Store.ensure(ctx); Store.data.soundMap.remove(task); Store.persist();
        Notifier.ensureChannels(ctx);
    }

    public static void preview(Context ctx, Uri uri) {
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
            mp.setDataSource(ctx, uri);
            mp.setOnCompletionListener(MediaPlayer::release);
            mp.prepare();
            mp.start();
        } catch (Exception ignored) {}
    }
}
