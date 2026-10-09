package com.spark.app.ui;

/**
 * Central palette. Every colour in the app consults this, so the whole look is
 * one switch rather than a hundred scattered if-else branches.
 *
 * Two styles:
 *
 *   NOTHING - Nothing OS 5: true monochrome, ONE red accent, hard contrast,
 *             hairline rules, no frosted glass and no gradient washes. Default.
 *   SOFT    - the earlier pastel frosted-glass look, kept selectable because
 *             it is gentler on the eyes at 3am.
 *
 * The Nothing rules that actually matter here:
 *   1. Black is black (#000000), not dark grey. Contrast is the point.
 *   2. Red is a signal, not decoration. It appears only where the eye must go:
 *      the live value, the active tab, a medication that is due.
 *   3. Type is dot-matrix or mono. No humanist sans, no soft weights.
 *   4. Edges are hairlines. Nothing floats on a blurred pane.
 */
public final class Theme {

    /** Nothing's signature red. Reserved for signal, never for surface. */
    public static final int RED = 0xFFD71921;

    public static final int STYLE_NOTHING = 0;
    public static final int STYLE_SOFT = 1;

    /** Active style. Set once at startup from Settings. */
    public static int style = STYLE_NOTHING;

    /** Dark mode. In NOTHING style this is white-on-black vs black-on-white. */
    public static boolean dark = true;

    /**
     * Night window - after the user's own (shifted) wind-down.
     *
     * Nothing does not switch to a separate "night theme" here; it shows the
     * SAME palette, just less of it. So night dims the ink, pulls the red back
     * to an ember, and fades the grid. The point is to stop the screen being
     * the most stimulating object in the room at 1am.
     */
    public static boolean night = false;

    private Theme() {}

    public static boolean isNothing() { return style == STYLE_NOTHING; }

    /** Style name, for the settings row. */
    public static String styleName() {
        return style == STYLE_NOTHING ? "Nothing" : "Soft";
    }

    // --------------------------------------------------------------- surfaces

    public static int bg() {
        if (isNothing()) return dark ? 0xFF000000 : 0xFFFFFFFF;
        return dark ? 0xFF0B0B10 : 0xFFE9EEF2;
    }

    public static int ink() {
        if (isNothing()) {
            if (!dark) return night ? 0xFF1F1F1F : 0xFF000000;
            if (night) return 0xFFCFCFCF;   // full white is too loud at 1am
            return 0xFFFFFFFF;
        }
        return dark ? 0xFFF2F1F6 : 0xFF141318;
    }

    public static int inkSoft() {
        if (isNothing()) {
            if (dark) return night ? 0xFF5E5E5E : 0xFF8A8A8A;
            return night ? 0xFF9A9A9A : 0xFF6B6B6B;
        }
        return dark ? 0xFFA7A3BC : 0xFF5B5B66;
    }

    /** Hairline rules and card borders. */
    public static int line() {
        if (isNothing()) return dark ? 0x2EFFFFFF : 0x24000000;
        return dark ? 0x3DE8E4F4 : 0x45222233;
    }

    /** Card fill. Solid in NOTHING style - nothing floats on a blur. */
    public static int card() {
        if (isNothing()) return dark ? 0xFF0A0A0A : 0xFFFFFFFF;
        return dark ? 0xC22A2536 : 0xC6FFFFFF;
    }

    /** The 1px highlight along a card's top/left edge. */
    public static int cardLine() {
        if (isNothing()) return dark ? 0x1FFFFFFF : 0x18000000;
        return dark ? 0x55FFFFFF : 0x99FFFFFF;
    }

    /** Bottom navigation pill. */
    public static int navGlass() {
        if (isNothing()) return dark ? 0xFF000000 : 0xFFFFFFFF;
        return dark ? 0xD6181822 : 0xD6F4F2EC;
    }

    /** Text fields and input wells. */
    public static int field() {
        if (isNothing()) return dark ? 0xFF121212 : 0xFFF4F4F4;
        return dark ? 0xFF2A2440 : 0xFFFFFFFF;
    }

    // ---------------------------------------------------------------- signals

    /**
     * The accent. In NOTHING style this is Nothing red and it is the only
     * saturated colour anywhere in the interface.
     */
    public static int accent() {
        if (isNothing()) {
            // An ember rather than a flare overnight: still findable, no longer
            // the brightest thing on the screen.
            if (night) return dark ? 0xFF8E2126 : 0xFFAA1418;
            return RED;
        }
        return dark ? 0xFF9D8CFF : 0xFF6E5BE0;
    }

    /** A dimmed accent, for fills sitting behind accent text. */
    public static int accentDim() {
        if (isNothing()) return dark ? 0x33D71921 : 0x22D71921;
        return dark ? 0x339D8CFF : 0x226E5BE0;
    }

    /** Section labels and eyebrows - small, mono, uppercase, quiet. */
    public static int label() {
        if (isNothing()) return dark ? 0xFF6E6E6E : 0xFF8A8A8A;
        return dark ? 0xFF7A7392 : 0xFF9B93AD;
    }

    /** Success / completed. Monochrome in NOTHING style. */
    public static int ok() {
        if (isNothing()) return dark ? 0x33FFFFFF : 0x22000000;
        return dark ? 0x3327D26A : 0x22119933;
    }

    public static int dialogBg() {
        if (isNothing()) return dark ? 0xFF0A0A0A : 0xFFFFFFFF;
        return dark ? 0xFF1D1930 : 0xFFFFFFFF;
    }

    // ------------------------------------------------------------ backgrounds

    /**
     * The soft colour washes behind the UI. NOTHING style has none: the
     * background is a flat field plus a dot grid (see SparkBackgroundView), so
     * this returns fully transparent entries of the SAME LENGTH - keeping every
     * existing caller index-safe rather than handing back an empty array.
     */
    public static int[] blobs() {
        if (isNothing()) return new int[]{0, 0, 0, 0, 0};
        return dark
                ? new int[]{0x2E7C6AFF, 0x2E9D5FDC, 0x26346AFF, 0x2A5B3FD6, 0x1E9C4ACC}
                : new int[]{0x2E00A96B, 0x33FF7AA2, 0x30346AFF, 0x33B57EEF, 0x22A6E24C};
    }

    /**
     * Background gradient. NOTHING style is a flat field: three identical stops,
     * so any LinearGradient built from it renders as a solid colour.
     */
    public static int[] gradient() {
        if (isNothing()) {
            int flat = dark ? 0xFF000000 : 0xFFFFFFFF;
            return new int[]{flat, flat, flat};
        }
        return dark
                ? new int[]{0xFF101018, 0xFF0B0B10, 0xFF12101A}
                : new int[]{0xFFE4EBF1, 0xFFE9EEF2, 0xFFF3EEE3};
    }

    // ---------------------------------------------------------------- dot grid

    /** Colour of the background dot grid (NOTHING style only). */
    public static int dot() {
        if (!isNothing()) return 0x00000000;
        if (dark) return night ? 0x0AFFFFFF : 0x14FFFFFF;
        return night ? 0x08000000 : 0x12000000;
    }

    /** Dot grid spacing, in dp. */
    public static float dotSpacingDp() { return 22f; }

    /** Dot radius, in dp. */
    public static float dotRadiusDp() { return 1.05f; }
}
