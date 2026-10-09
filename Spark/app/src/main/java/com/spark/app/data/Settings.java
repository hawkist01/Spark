package com.spark.app.data;

/**
 * All user settings.
 *
 * Note on RHYTHM: the app deliberately does not ask "what time will you do
 * this?". Fixed clock times are the thing that makes a plan feel like a
 * cage, so every hour below is a *preference* that gets shifted to match the
 * user's actual day. The raw fields keep their names for backwards
 * compatibility with saved backups; the `*Eff()` helpers are what the
 * scheduler actually reads.
 */
public class Settings {
    public int seedHour = 9, seedMinute = 0;
    public boolean seedEnabled = true;
    public boolean pingEnabled = true;
    public int pingIntervalMin = 90, pingStartHour = 9, pingEndHour = 22;
    public int snoozeMin = 30;
    public boolean quietEnabled = false;
    public int quietStartHour = 23, quietEndHour = 8;
    public boolean winddownEnabled = true;
    public int windDownHour = 21, windDownMin = 30;
    public boolean calmMode = false;
    public String userName = "friend";
    public boolean guardEnabled = true;
    public int guardThresholdMin = 15;
    public int guardAllowMin = 5;
    public int guardTheme = 0; // 0 Dawn, 1 Ocean, 2 Dusk
    public boolean darkMode = false;

    /** Visual style: Theme.STYLE_NOTHING (0) or Theme.STYLE_SOFT (1). */
    public int themeStyle = 0;

    // ------------------------------------------------------------------ rhythm

    /**
     * When on, the whole day slides with the user instead of being pinned to
     * wall-clock hours. Someone who is flat at 2pm and sharp at 1am should not
     * be told their day starts at 9.
     */
    public boolean rhythmAdaptive = true;

    /**
     * How far the day is shifted, in minutes. Positive = later.
     * +180 means "my 9am is really noon". This is the single dial that makes
     * the app stop fighting a night-leaning chronotype.
     */
    public int chronoShiftMin = 180;

    /**
     * A medication dose is due across a window rather than at a single minute.
     * The reminder fires at the start, and once more near the end if the dose
     * is still unmarked - which is the actual failure mode (forgetting), not
     * being one minute late.
     */
    public int medWindowMin = 90;

    /** Whether the end-of-window nudge fires for still-untaken doses. */
    public boolean medNudgeRepeat = true;

    /** How many whole hours the day is shifted by. */
    public int shiftHours() {
        if (!rhythmAdaptive) return 0;
        return Math.round(chronoShiftMin / 60f);
    }

    /** Shift one wall-clock hour onto the user's clock. */
    public int shift(int hour) {
        int h = hour + shiftHours();
        return ((h % 24) + 24) % 24;
    }

    /** Shift a minute-of-day value, wrapping at midnight. */
    public int shiftMinutes(int minutesOfDay) {
        if (!rhythmAdaptive) return ((minutesOfDay % 1440) + 1440) % 1440;
        int m = minutesOfDay + chronoShiftMin;
        return ((m % 1440) + 1440) % 1440;
    }

    // Effective (shifted) hours - these are what the scheduler uses.

    public int seedHourEff()      { return shift(seedHour); }
    public int pingStartEff()     { return shift(pingStartHour); }
    public int pingEndEff()       { return shift(pingEndHour); }
    public int quietStartEff()    { return shift(quietStartHour); }
    public int quietEndEff()      { return shift(quietEndHour); }
    public int windDownHourEff()  { return shift(windDownHour); }

    /** Human summary of the current rhythm, for the settings row. */
    public String rhythmSummary() {
        if (!rhythmAdaptive) return "Fixed times";
        int h = shiftHours();
        if (h == 0) return "Follows your day";
        return (h > 0 ? "+" : "") + h + "h later";
    }
}
