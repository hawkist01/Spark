package com.spark.app.data;

import java.util.ArrayList;
import java.util.List;

public class Quest {
    public String id, title, why, tiny, deep, notes = "", cue = "", remindAt = "", sound = "spark";
    public final List<String> doneDays = new ArrayList<>();

    public Quest(String id, String title, String why, String tiny, String deep) {
        this.id = id; this.title = title; this.why = why; this.tiny = tiny; this.deep = deep;
    }

    public boolean doneToday(String t) { return doneDays.contains(t); }
}
