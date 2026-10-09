package com.spark.app.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppData {
    public final List<Quest> quests;
    public final List<Med> meds;
    public final Settings settings = new Settings();
    public final Map<String, Integer> takenToday = new HashMap<>();
    public final Map<String, Integer> takenHistory = new HashMap<>();
    public final Map<String, Integer> sparksHistory = new HashMap<>();
    public final List<Reflection> reflections = new ArrayList<>();
    public final List<String> creativitySparks = new ArrayList<>();
    public final List<String> guardedApps = new ArrayList<>();
    public final Map<String, String> soundMap = new HashMap<>();
    public final List<String> captures = new ArrayList<>();
    public final List<String> wonderSparks = new ArrayList<>();
    public final Map<String, DayStat> dayHistory = new HashMap<>();
    public final List<WeeklyReport> weeklyReports = new ArrayList<>();
    public int sparksToday, pingsToday, waterToday, foodToday, studyMinToday, lastWeekSpark;
    public int sleepHalfToday;
    public String reflection = "";

    public AppData() {
        this.quests = defaultQuests(); this.meds = defaultMeds();
        this.guardedApps.addAll(GuardianApps.defaults());
    }

    public static List<Quest> defaultQuests() {
        // Blank slate: the user creates everything from scratch.
        return new ArrayList<>();
    }

    public static List<Med> defaultMeds() {
        // Blank slate: the user adds their own medications with own times.
        return new ArrayList<>();
    }
}
