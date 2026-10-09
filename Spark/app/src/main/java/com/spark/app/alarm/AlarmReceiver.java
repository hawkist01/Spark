package com.spark.app.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.spark.app.data.Quest;
import com.spark.app.data.Store;
import com.spark.app.util.Notifier;
import com.spark.app.util.Scheduler;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Store.ensure(ctx);
        Store.resetDailyIfNewDay(ctx);
        String type = intent.getStringExtra("type");
        if (type == null) return;
        switch (type) {
            case "seed": {
                if (isQuiet(ctx)) { Scheduler.scheduleSeed(ctx, Store.data.settings); break; }
                Quest q = Store.firstPending();
                String title = q != null ? q.title : "All seeds tended this week";
                String text = q != null ? ("Your one seed: " + q.tiny + (q.cue != null && !q.cue.isEmpty() ? " [" + q.cue + "]" : ""))
                        : "Nice - nothing waiting this week. Add a new idea when you are ready.";
                Notifier.seed(ctx, title, text, q != null ? ("Why: " + q.why) : "");
                break;
            }
            case "med": {
                String name = intent.getStringExtra("name") != null ? intent.getStringExtra("name") : "Medication";
                String time = intent.getStringExtra("time") != null ? intent.getStringExtra("time") : "";
                String medId = intent.getStringExtra("medId") != null ? intent.getStringExtra("medId") : "";
                Notifier.med(ctx, name, time, medId);
                break;
            }
            case "med_end": {
                // End of the dose window. Fire once more, but ONLY if nothing
                // has been marked taken today for this medication - so this can
                // never nag about a dose that is already done.
                String medId = intent.getStringExtra("medId") != null ? intent.getStringExtra("medId") : "";
                int taken = 0;
                try { taken = Store.data.takenToday.getOrDefault(medId, 0); } catch (Exception ignored) {}
                if (taken > 0) break;
                String name = intent.getStringExtra("name") != null ? intent.getStringExtra("name") : "Medication";
                String time = intent.getStringExtra("time") != null ? intent.getStringExtra("time") : "";
                Notifier.med(ctx, name, time, medId);
                break;
            }
            case "ping": {
                if (isQuiet(ctx)) { Scheduler.schedulePing(ctx, Store.data.settings); break; }
                // Count only when the user taps "Did it" (ActionReceiver ping_done),
                // so the number on screen always matches what they did.
                Notifier.ping(ctx, "10 squats, a stretch, or a glass of water.");
                Scheduler.schedulePing(ctx, Store.data.settings);
                break;
            }
            case "wind": {
                Notifier.winddown(ctx, "Slow down. Meds, a shower, breathe - your phone can wait until morning.");
                break;
            }
            case "quest": {
                if (isQuiet(ctx)) { Scheduler.scheduleQuests(ctx, Store.data.quests); break; }
                String qid = intent.getStringExtra("qid");
                Quest q = null;
                if (qid != null) for (Quest cand : Store.data.quests) if (cand.id.equals(qid)) { q = cand; break; }
                if (q == null) q = Store.firstPending();
                if (q != null) Notifier.taskDue(ctx, q);
                Scheduler.scheduleQuests(ctx, Store.data.quests); // roll forward to tomorrow
                break;
            }
            case "weekly": {
                try {
                    com.spark.app.data.WeeklyReport r = com.spark.app.util.Weekly.buildReport(ctx);
                    Store.saveWeeklyReport(r);
                    Notifier.weekly(ctx, r.score, r.summary);
                } catch (Exception ignored) {}
                Scheduler.scheduleWeekly(ctx); // next Saturday 21:00
                break;
            }
        }
    }

    private boolean isQuiet(Context ctx) {
        try {
            com.spark.app.data.Settings s = Store.data.settings;
            if (s.calmMode) return true; // calm = only medications notify
            if (!s.quietEnabled) return false;
            int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            // Quiet hours follow the shifted day too, otherwise the quiet
            // window would sit in the middle of a night-leaning user's
            // afternoon and muffle every reminder that actually lands.
            int sh = s.quietStartEff(), eh = s.quietEndEff();
            if (sh <= eh) return h >= sh && h < eh;
            return h >= sh || h < eh;
        } catch (Exception e) { return false; }
    }
}
