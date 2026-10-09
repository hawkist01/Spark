package com.spark.app.alarm;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.spark.app.data.Store;
import com.spark.app.util.Scheduler;

public class ActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Store.ensure(ctx);
        String action = intent.getAction();
        if (action == null) return;
        int nid = intent.getIntExtra("nid", Integer.MIN_VALUE);
        String data = intent.getStringExtra("data");
        if (data == null) data = "";
        cancel(ctx, nid);
        switch (action) {
            case "snooze":
                Scheduler.snooze(ctx, data);
                toast(ctx, "Snoozed " + Store.data.settings.snoozeMin + " min. Open Spark to see it.");
                break;
            case "med_taken": {
                String[] parts = data.split("\\|", 2);
                String id = parts.length > 0 ? parts[0] : "";
                if (!id.isEmpty()) {
                    Store.markMedTaken(id);
                    toast(ctx, "Logged. Take care.");
                }
                break;
            }
            case "ping_done":
                Store.addPing();
                toast(ctx, "Nice. Body points +1. Open Spark to see it.");
                break;
            case "quest_done":
                if (!data.isEmpty()) {
                    Store.markQuestDone(data);
                    toast(ctx, "A spark. Good. Open Spark to see it.");
                }
                break;
            case "med_snooze": {
                // data = medId|time|name — reschedule that exact dose, not a blank one
                String[] parts = data.split("\\|", 3);
                String id = parts.length > 0 ? parts[0] : "";
                String time = parts.length > 1 ? parts[1] : "";
                String name = parts.length > 2 ? parts[2] : "Medication";
                Scheduler.snoozeMed(ctx, id, time, name);
                toast(ctx, "Reminded later.");
                break;
            }
            case "open":
                break;
        }
    }

    private static void cancel(Context ctx, int nid) {
        if (nid == Integer.MIN_VALUE) return;
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(nid);
        } catch (Exception ignored) {}
    }

    private static void toast(Context ctx, String msg) {
        try { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show(); } catch (Exception ignored) {}
    }
}
