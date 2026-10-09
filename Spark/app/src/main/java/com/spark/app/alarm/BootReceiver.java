package com.spark.app.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.spark.app.data.Store;
import com.spark.app.util.Scheduler;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Store.ensure(ctx);
        Scheduler.rescheduleAll(ctx, Store.data);
    }
}
