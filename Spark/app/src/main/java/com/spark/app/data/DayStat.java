package com.spark.app.data;

import org.json.JSONObject;

// One day of gentle essentials. Missing data = "not tracked", never a failure.
public class DayStat {
    public String date = "";
    public int water, food, pings, medsTaken, medsPlanned, sparks, studyMin, sleepHalf, screenMin = -1;

    public DayStat(String date) { this.date = date; }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("date", date); o.put("water", water); o.put("food", food);
            o.put("pings", pings); o.put("medsTaken", medsTaken); o.put("medsPlanned", medsPlanned);
            o.put("sparks", sparks); o.put("studyMin", studyMin);
            o.put("sleepHalf", sleepHalf); o.put("screenMin", screenMin);
            return o;
        } catch (Exception e) { return new JSONObject(); }
    }

    public static DayStat fromJson(JSONObject o) {
        DayStat d = new DayStat(o.optString("date", ""));
        d.water = o.optInt("water", 0); d.food = o.optInt("food", 0);
        d.pings = o.optInt("pings", 0);
        d.medsTaken = o.optInt("medsTaken", 0); d.medsPlanned = o.optInt("medsPlanned", 0);
        d.sparks = o.optInt("sparks", 0); d.studyMin = o.optInt("studyMin", 0);
        d.sleepHalf = o.optInt("sleepHalf", 0); d.screenMin = o.optInt("screenMin", -1);
        return d;
    }
}
