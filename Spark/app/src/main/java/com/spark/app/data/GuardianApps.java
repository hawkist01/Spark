package com.spark.app.data;

import java.util.Arrays;
import java.util.List;

public final class GuardianApps {
    private GuardianApps() {}

    private static final String[] DEFAULTS = {
            "com.instagram.android", "com.ss.android.ugc.aweme", "com.snapchat.android",
            "com.twitter.android", "com.twitter.android.lite", "org.telegram.messenger",
            "com.whatsapp", "com.facebook.katana", "com.facebook.orca", "com.reddit.frontpage",
            "com.reddit.news", "tv.twitch.android.app", "com.discord", "com.pinterest",
            "com.google.android.youtube", "com.google.android.apps.youtube.music",
            "com.zhiliaoapp.musically", "com.instagram.alt"
    };

    public static boolean isSocial(String pkg) {
        if (pkg == null) return false;
        for (String d : DEFAULTS) if (d.equals(pkg)) return true;
        return false;
    }

    public static List<String> defaults() { return Arrays.asList(DEFAULTS); }
}
