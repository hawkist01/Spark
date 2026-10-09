package com.spark.app;

import android.app.Application;

import com.spark.app.data.Store;
import com.spark.app.util.Scheduler;

public class SparkApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Store.init(this);
        Scheduler.rescheduleAll(this, Store.data);
    }
}
