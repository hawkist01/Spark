package com.spark.app.data;

import org.json.JSONObject;

// A saved weekend snapshot. Kind words only — rest counts too.
public class WeeklyReport {
    public String weekStart = "";
    public int score;
    public String summary = "";

    public WeeklyReport(String weekStart, int score, String summary) {
        this.weekStart = weekStart; this.score = score; this.summary = summary;
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("weekStart", weekStart); o.put("score", score); o.put("summary", summary);
            return o;
        } catch (Exception e) { return new JSONObject(); }
    }

    public static WeeklyReport fromJson(JSONObject o) {
        return new WeeklyReport(o.optString("weekStart", ""), o.optInt("score", 0), o.optString("summary", ""));
    }
}
