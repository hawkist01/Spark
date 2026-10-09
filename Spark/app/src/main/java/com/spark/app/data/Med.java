package com.spark.app.data;

import java.util.ArrayList;
import java.util.List;

public class Med {
    public String id, name, note = "";
    public final List<String> times = new ArrayList<>();

    public Med(String id, String name, String note) { this.id = id; this.name = name; this.note = note; }
}
