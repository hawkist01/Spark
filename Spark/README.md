# SPARK 6.0 — Nothing OS 5 look + a day that moves with you

Built for your CMF Phone 2 Pro (`GalagaIND` / A001). True Nothing OS 5: pure
black and white, ONE red accent (`#D71921`), dot-matrix grid, hairline rules,
spring-physics everywhere. No frosted pastel, no gradients, no blur.

And the part that matters more than the paint: **the app no longer has a
timetable.** Every hour is a preference that slides to match your actual day.

> Sideloaded, self-signed APK. Not on the Play Store — that's fine and expected.

---

## 0) What's new in 6.0

### The clock is gone
Spark used to pin things to wall-clock hours — seed at 09:00, wind-down at
21:30. That is a cage, and a cage is the thing you avoid.

Now there is **one dial**: `Settings -> Day shift`. Set it to **+3h** and your
whole day moves three hours later. Every scheduled thing follows it:

| What | Before | With +3h |
|------|--------|----------|
| Seed nudge | 09:00 | 12:00 |
| Wind-down | 21:30 | 00:30 |
| Movement pings | 09:00–22:00 | 12:00–01:00 |
| Quiet hours | 23:00–08:00 | 02:00–11:00 |
| Quest reminders | as written | shifted too |
| Medication times | as written | shifted too |

So "after dinner" still lands after *your* dinner. Turn it off in
`Settings -> Follow my day, not the clock` if you ever want the fixed grid back.

### A medication window, built for forgetting
Forgetting a dose is not a timing failure, it is a memory failure — so nagging
once at 21:00 sharp and then going silent was the wrong shape.

Each dose now gets **two** alarms: one at the start of a window, one at the end.
The second checks whether anything has been marked taken today and **stays
silent if it has**, so it can never nag about a dose that is already done.
Window length is yours: `Settings -> Med window` (30 min – 3h).

### Night dimming
After your own *shifted* wind-down, the palette dims itself for eight hours:
ink drops from full white to a soft grey, the red pulls back to an ember, the
dot grid fades. Same design, less of it — so the screen stops being the
brightest thing in the room at 1am.

### The Nothing OS design system
`Theme.java` now has two selectable styles:

- **Nothing** (default) — `#000000`/`#FFFFFF`, one red accent reserved for
  signal (live value, active tab, a due dose), solid cards with hairlines.
- **Soft** — the old pastel frosted glass, for when you want it gentler.

Toggle in `Settings -> Nothing OS look`.

---

## 1) Install it (on the phone)

**The APK:** `Spark/dist/Spark.apk` (also copied to `Spark.apk` at the project root)

1. Get `Spark.apk` onto the phone (email / Google Drive / USB / Quick Share).
2. Tap it → **Settings → allow this source** → back → **Install**.

### If Google Play Protect blocks the install (your exact error)

That warning is **normal** for any sideloaded app — Play Protect doesn't recognize the
self-signed certificate. Nothing is wrong with the file. Two ways through:

**Way A — right on the warning (easiest):**
1. When the "Play Protect / app not scanned" popup appears, do NOT tap Cancel.
2. Tap **More details** (small text).
3. Tap **Install anyway (unsafe)** → Install. One time only; future updates install fine.

**Way B — pause Play Protect scanning:**
1. Open **Play Store** → tap your profile icon → **Play Protect**.
2. Tap the **settings gear** (top right).
3. Turn **off** "Scan apps with Play Protect".
4. Install the APK. Turn scanning back on afterwards — choose **Keep app (unsafe)** /
   "Install anyway" if it re-warns, so Spark isn't removed.

Also check: **Settings → Apps → Special app access → Install unknown apps** → allow the
app you're installing *from* (Chrome / Files / Drive).

### First launch
1. **Allow** notifications. On Android 14+ also allow **Exact alarms** when prompted (Settings > Apps > Spark > Alarms & reminders) or seed/med/pings go inexact.
2. For the Guardian (screen takeover): **Allow overlay** + **Enable Guardian** in the
   Guardian card. Optional: usage access from the focus card.

---

## 2b) What's new in 3.0 (for your journal)

Research base: Sirois + Pychyl (procrastination = emotion repair, not laziness),
Gollwitzer (if-then implementation intentions + MCII), Fogg (tiny habits),
Windred et al (sleep regularity > duration), McGill nudge RCTs (defaults + 3-sec
friction beat guilt; opt-out caps cut ~18%; guilt copy backfires), Kaya et al
(self-compassion buffers doomscrolling). Philosophy: Stoic control dichotomy
(control the next 2 min, not the 6 months), Aristotle (virtue = small repeated acts),
Epicurus (rest is productive).

- **Weekly, not daily pressure:** Seed card is now "This week's seed". 3 quests max
  idea: 1 job + 1 study + 1 body. Done-days still tracked on week strip.
- **If-then plans on every quest:** new "Plan" field, e.g. "If it is 7pm after dinner,
  then I open notes for 2 min." Shown on card + inside the notification (MCII).
- **Sounds v4, all 7 regenerated + more creative:** sunrise marimba (spark),
  curious glass rise (wonder), music-box waltz over warm pad (med), 1.4s pocket
  groove with claps (jog), rain + kalimba droplets (bath), soft wooden warning +
  warm resolve with zero shame (guardian), Cmaj7 pad + lullaby (sleep).
  Settings > ... > Sounds... still lets you Play / Choose any file / Reset per task.
- **First-principles deck stays removed:** Wonder deck is now 100% playful +
  curiosity (no entropy/black-hole theory). 3 min, new thing each time.
- **Symbols fixed for real:** replaced all middle-dots/bullets/em-dashes with
  ASCII `|` `-` so DotGothic/SpaceMono never show tofu boxes. Progress = drawn canvas dots.
- **Android 15/16-style behavior:** edge-to-edge bars, predictive-back opt-in
  (`enableOnBackInvokedCallback`), exact-alarm permission prompt, springy swipe
  between Seed/Compass/Body/Wonder + sliding indicator.
- **Guardian Dusk fixed:** was light by copy-paste bug, now true dark purple
  `201C38/2A2447` for 2am takeovers. Buttons kinder: Breathe | Spark instead |
  Move now | I need this app.
- **Backup includes plans:** export text now includes if-then cues + notes, via
  Gmail / Copy / Save as file to Downloads/Drive.

## 2) What's new in 2.1

### Sounds that actually play — and much better ones
- **Fixed:** on Android 8+, notification sound must be set on the *channel*, not the
  notification. Each task now has its own channel with its own sound + vibration, and
  picking a custom sound rebuilds that channel correctly. (Before, most notifications
  were silently falling back to the default, and body pings were fully silent.)
- **New synth engine**: real bell partials, plucks, pads, filtered noise, reverb.
  - *Spark nudge* — warm marimba run
  - *Wonder/spark* — glassy two-tone chime
  - *Medication* — caring ding-dong bell
  - *Move/jog* — a 1-second groove: kick, ticks, plucks
  - *Bath/wind-down* — water whoosh + droplet plinks
  - *Guardian* — kind-but-urgent pulses, tension dyad, gentle resolve
  - *Sleep* — warm A-minor pad with slow tremolo
- Still fully customisable: **Settings (···) → Sounds...** → Play / Choose any file / Reset.

### First-principles deck — removed
Replaced with light, playful **creative sparks** (3-line stories, invent-a-word,
treasure-map your to-dos...). Zero theory, zero homework pressure.

### Symbol render bugs — fixed
The pixel/mono fonts don't contain glyphs like ✓ ✗ ⚙ ● ○ ▶ ✖ — that's why some symbols
showed as boxes. All icons are now ASCII text or **drawn on canvas** (progress dots,
week strip) so they always render.

### Real sliding
Drag any screen **left/right with your finger** — the page follows, rubber-bands at the
edges, and springs to the next tab on release (fling works too). Tab buttons still work.

### Backup that goes somewhere real
"Save as file" now opens the **system file picker** — save `spark_backup.txt` straight to
**Downloads, Drive, or anywhere**. "To Gmail" and "Copy" unchanged.

### Guardian takeover — customisable + animated
- The takeover card now **springs in** (slide + scale) and fades out gently.
- **3 themes**: Dawn (pastel), Ocean (cool blue-mint), Dusk (dark purple). Pick in
  **Guardian → Settings**. Dark mode matters — the takeover fires at 2am too.
- Breathing screen fixed (clean in/out phase timing).

---

## 3) The four tabs
| Tab | What it's for |
|-----|---------------|
| **Seed** | Clock, one seed, spark card, Guardian status, body/water, meds, focus, capture, export. |
| **Compass** | The week: quests, week strip, weekly reflection. |
| **Body** | Movement pings, medications. |
| **Wonder** | Today's creative spark, your sparks, the idea deck. |

---

## 4) Rebuilding (optional, fully offline)
```
& .\Spark\build.ps1
```
`aapt2 → javac → d8 → zipalign → apksigner`. Output: `Spark\dist\Spark.apk`.
Signing: the keystore lives in `Spark\keystore\` (gitignored — generate your own with
`keytool` and adjust the `--ks-pass` lines in `build.ps1`). Sounds: `node Spark\buildtools\gen_sounds.js Spark\app\src\main\res\raw`.

---

## 5) Honest notes
- The Guardian **intercepts and redirects** — Android doesn't let any normal app hard-lock
  another app at the OS level. It needs the overlay + accessibility toggles; both optional.
- All data stays on your phone. No account, no tracking, no ads. Gmail export only sends
  the backup text to your own email.
