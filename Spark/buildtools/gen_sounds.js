// Synthesizes short, distinct WAV sounds for per-task notifications.
// Writes mono 44100Hz 16-bit PCM .wav files into Spark/app/src/main/res/raw/
//
// v4 - more creative + kinder identities for Spark 3.0:
//   spark  = sunrise marimba C-D-E-G + sparkle octave (a small win)
//   wonder = curious glass pentatonic rise E-G-A-B (ooh, what is that?)
//   med    = caring music-box waltz G-E-C, warm pad underneath (someone cares)
//   jog    = 1.4s pocket groove: kick + claps + bass plucks A-C-D (stand up!)
//   bath   = rain wash + kalimba droplets E-A-D (water, let go)
//   doom   = soft wooden warning + tension lift + warm resolve (no shame)
//   sleep  = Cmaj7 night pad + music-box lullaby C-A-G-E (safe to rest)
// Reverb + soft clip + normalize. Deterministic (same bytes every build).
const fs = require('fs');
const path = require('path');
const outDir = process.argv[2];
fs.mkdirSync(outDir, { recursive: true });
const SR = 44100;

// ---------- core helpers ----------
const buf = (dur) => new Float64Array(Math.round(SR * dur));

function add(out, s, atSec) {
  const off = Math.round((atSec || 0) * SR);
  for (let i = 0; i < s.length; i++) {
    const j = off + i;
    if (j >= 0 && j < out.length) out[j] += s[i];
  }
}

function normalize(out, target = 0.86) {
  let m = 0;
  for (let i = 0; i < out.length; i++) m = Math.max(m, Math.abs(out[i]));
  if (m > 0) { const g = target / m; for (let i = 0; i < out.length; i++) out[i] *= g; }
  return out;
}

function softclip(out) {
  for (let i = 0; i < out.length; i++) out[i] = Math.tanh(out[i] * 1.35) * 0.95;
  return out;
}

// deterministic noise so builds are reproducible
let seed = 987654321;
function rand() {
  seed = (seed * 1103515245 + 12345) & 0x7fffffff;
  return seed / 0x3fffffff - 1;
}

// additive bell with stretched partials (real-bell like)
function bell(f, dur, vol = 0.5, bright = 1) {
  const out = buf(dur), n = out.length;
  const P = [
    [1.0, 1.0, 3.0],
    [2.756, 0.55 * bright, 4.5],
    [5.404, 0.26 * bright, 7.0],
    [8.933, 0.11 * bright, 10.0],
  ];
  for (const [r, a, k] of P) {
    for (let i = 0; i < n; i++) {
      const t = i / SR;
      out[i] += Math.sin(2 * Math.PI * f * r * t) * a * Math.exp(-k * t) * vol;
    }
  }
  return out;
}

// plucked string: harmonics w/ fast decay + noise transient
function pluck(f, dur, vol = 0.5, damp = 5) {
  const out = buf(dur), n = out.length;
  for (let i = 0; i < n; i++) {
    const t = i / SR;
    const e = Math.exp(-damp * t);
    const v = Math.sin(2 * Math.PI * f * t)
      + 0.45 * Math.sin(4 * Math.PI * f * t) * Math.exp(-2 * damp * t)
      + 0.18 * Math.sin(6 * Math.PI * f * t) * Math.exp(-3 * damp * t);
    out[i] = v * e * vol;
  }
  const na = Math.round(SR * 0.006);
  for (let i = 0; i < na && i < n; i++) out[i] += rand() * (1 - i / na) * vol * 0.55;
  return out;
}

// mallet / marimba strike: short sine + 4th partial + soft noise tick
function mallet(f, dur, vol = 0.5) {
  const out = buf(dur), n = out.length;
  for (let i = 0; i < n; i++) {
    const t = i / SR;
    out[i] = (Math.sin(2 * Math.PI * f * t) * Math.exp(-6 * t)
      + 0.3 * Math.sin(2 * Math.PI * f * 3.9 * t) * Math.exp(-16 * t)) * vol;
  }
  const na = Math.round(SR * 0.004);
  for (let i = 0; i < na && i < n; i++) out[i] += rand() * (1 - i / na) * vol * 0.35;
  return out;
}

// music-box tine: pure + shimmer, long decay
function tine(f, dur, vol = 0.4) {
  const out = buf(dur), n = out.length;
  for (let i = 0; i < n; i++) {
    const t = i / SR;
    out[i] = (Math.sin(2 * Math.PI * f * t) * Math.exp(-3.2 * t)
      + 0.25 * Math.sin(2 * Math.PI * f * 3.01 * t) * Math.exp(-7 * t)
      + 0.1 * Math.sin(2 * Math.PI * f * 9.2 * t) * Math.exp(-12 * t)) * vol;
  }
  return out;
}

// kick drum: pitch sweep sine
function kick(vol = 0.9) {
  const dur = 0.22, out = buf(dur), n = out.length;
  for (let i = 0; i < n; i++) {
    const t = i / SR;
    const f = 42 + 130 * Math.exp(-38 * t);
    out[i] = Math.sin(2 * Math.PI * f * t) * Math.exp(-14 * t) * vol;
  }
  return out;
}

// clap: band of noise bursts
function clap(vol = 0.4) {
  const dur = 0.18, out = buf(dur), n = out.length;
  for (let i = 0; i < n; i++) {
    const t = i / SR;
    const burst = Math.exp(-28 * t) * (0.6 + 0.4 * Math.sin(2 * Math.PI * 1800 * t));
    out[i] = rand() * burst * vol;
  }
  return out;
}

// hi-hat tick: short bright noise
function tick(vol = 0.3) {
  const dur = 0.05, out = buf(dur), n = out.length;
  let lp = 0;
  for (let i = 0; i < n; i++) {
    const x = rand();
    lp += 0.55 * (x - lp);
    out[i] = (x - lp) * Math.exp(-60 * i / n) * vol * 2.2;
  }
  return out;
}

// swelling pad w/ detune + tremolo
function pad(freqs, dur, vol = 0.3, tremHz = 0) {
  const out = buf(dur), n = out.length;
  for (let i = 0; i < n; i++) {
    const t = i / SR, p = i / n;
    const sw = Math.pow(Math.sin(Math.PI * Math.min(1, p * 1.08)), 1.4);
    const trem = tremHz > 0 ? 1 + 0.16 * Math.sin(2 * Math.PI * tremHz * t) : 1;
    let v = 0;
    for (const f of freqs) v += Math.sin(2 * Math.PI * f * t) + 0.32 * Math.sin(2 * Math.PI * f * 1.004 * t);
    out[i] = (v / Math.sqrt(freqs.length)) * sw * trem * vol;
  }
  return out;
}

// filtered-noise sweep (whoosh / rain)
function whoosh(dur, f0, f1, vol = 0.5) {
  const out = buf(dur), n = out.length;
  let lp = 0;
  for (let i = 0; i < n; i++) {
    const p = i / n;
    const f = f0 + (f1 - f0) * p;
    const alpha = 1 - Math.exp(-2 * Math.PI * f / SR);
    lp += alpha * (rand() - lp);
    out[i] = lp * Math.sin(Math.PI * p) * vol * 2.2;
  }
  return out;
}

// gritty low pulse (square through onepole LP)
function pulse(f, dur, vol = 0.5, cut = 0.12) {
  const out = buf(dur), n = out.length;
  let lp = 0;
  for (let i = 0; i < n; i++) {
    const t = i / SR;
    const sq = Math.sin(2 * Math.PI * f * t) >= 0 ? 1 : -1;
    lp += 0.18 * (sq - lp);
    out[i] = lp * Math.exp(-9 * t) * vol;
  }
  return out;
}

// simple feedback-comb reverb
function reverb(out, mix = 0.25) {
  const combs = [1343, 1801, 2687, 3329].map((d) => ({ d, b: new Float64Array(d), i: 0 }));
  const wet = new Float64Array(out.length);
  for (let i = 0; i < out.length; i++) {
    let w = 0;
    for (const c of combs) {
      const v = c.b[c.i];
      w += v;
      c.b[c.i] = (out[i] + v * 0.74) * 0.6;
      c.i = (c.i + 1) % c.d;
    }
    wet[i] = w / 4;
  }
  for (let i = 0; i < out.length; i++) out[i] = out[i] * (1 - mix) + wet[i] * mix;
  return out;
}

/**
 * Write 16-bit mono PCM.
 *
 * `fade` guards against start/end clicks on one-shot sounds. It must be OFF
 * for looped sounds (the cat's purr): fading the edges would put an audible
 * gap at every loop point. A loopable buffer instead has to END mid-cycle
 * exactly where it began, which is arranged by choosing a duration that is a
 * whole number of periods.
 */
function wav(samples, fade = true) {
  const n = samples.length;
  const atk = fade ? Math.round(SR * 0.006) : 0;
  const rel = fade ? Math.round(SR * 0.02) : 0;
  const data = Buffer.alloc(n * 2);
  for (let i = 0; i < n; i++) {
    let s = samples[i];
    if (atk && i < atk) s *= i / atk;
    if (rel && i > n - rel) s *= Math.max(0, (n - i) / rel);
    s = Math.max(-1, Math.min(1, s));
    data.writeInt16LE(Math.round(s * 32767), i * 2);
  }
  const hdr = Buffer.alloc(44);
  hdr.write('RIFF', 0); hdr.writeUInt32LE(36 + data.length, 4); hdr.write('WAVE', 8);
  hdr.write('fmt ', 12); hdr.writeUInt32LE(16, 16); hdr.writeUInt16LE(1, 20); hdr.writeUInt16LE(1, 22);
  hdr.writeUInt32LE(SR, 24); hdr.writeUInt32LE(SR * 2, 28); hdr.writeUInt16LE(2, 32); hdr.writeUInt16LE(16, 34);
  hdr.write('data', 36); hdr.writeUInt32LE(data.length, 40);
  return Buffer.concat([hdr, data]);
}

// ---------- extra voices (v5) ----------

// A droplet: a blip that falls in pitch, like water off a leaf.
function droplet(f, dur, vol = 0.3, drop = 0.45) {
  const out = buf(dur), n = out.length;
  let ph = 0;
  for (let i = 0; i < n; i++) {
    const t = i / SR, p = i / n;
    const fr = f * (1 - drop * p * p);
    ph += 2 * Math.PI * fr / SR;
    out[i] = Math.sin(ph) * Math.exp(-9 * p) * vol;
  }
  return out;
}

// Warm bass note with a soft attack - gives the groove an actual bottom.
function bass(f, dur, vol = 0.5) {
  const out = buf(dur), n = out.length;
  let lp = 0;
  for (let i = 0; i < n; i++) {
    const t = i / SR;
    const env = Math.min(1, t / 0.012) * Math.exp(-3.4 * t);
    const saw = 2 * ((f * t) % 1) - 1;
    lp += 0.10 * (saw - lp);
    out[i] = (lp * 0.8 + Math.sin(2 * Math.PI * f * t) * 0.5) * env * vol;
  }
  return out;
}

// Airy deferred shimmer - a detuned pair that blooms after the attack.
function shimmer(f, dur, vol = 0.18) {
  const out = buf(dur), n = out.length;
  for (let i = 0; i < n; i++) {
    const t = i / SR, p = i / n;
    const env = Math.pow(Math.sin(Math.PI * p), 1.6);
    out[i] = (Math.sin(2 * Math.PI * f * t) * 0.6
      + Math.sin(2 * Math.PI * f * 1.006 * t) * 0.5
      + Math.sin(2 * Math.PI * f * 2.01 * t) * 0.15)
      * env * vol;
  }
  return out;
}

/**
 * THE PURR. This is the one that had to be right.
 *
 * A cat's purr is not a note. It is a low, rough, continuous rumble - roughly
 * 25 Hz - made of strong harmonics, with the whole thing swelling and fading
 * about twice a second as the cat breathes. The roughness comes from the
 * glottal buzz (many harmonics, none dominant) rather than from noise.
 *
 * For seamless looping every component must complete a WHOLE number of cycles
 * in the buffer, otherwise the loop point clicks. With dur = 3.0s:
 *     26 Hz fundamental -> 78 cycles
 *      1 Hz wobble      ->  3 cycles
 *      2 Hz breathing   ->  6 cycles
 * so the waveform at the end lands exactly on its starting phase.
 */
function purr(dur = 3.0) {
  const out = buf(dur), n = out.length;
  const f0 = 26, breathe = 2.0, wob = 1.0;
  // Harmonic stack, tilted down. Odd and even both present = buzzy, not pure.
  const harms = [[1, 1.0], [2, 0.62], [3, 0.46], [4, 0.30], [5, 0.20],
  [6, 0.14], [7, 0.10], [8, 0.07], [9, 0.05], [10, 0.035], [12, 0.02]];
  let lp = 0;
  for (let i = 0; i < n; i++) {
    const t = i / SR;
    // Breathing: never drops to silence, a purr is continuous.
    const br = 0.55 + 0.45 * Math.sin(2 * Math.PI * breathe * t - Math.PI / 2);
    // Slight pitch drift so it sounds alive rather than like a machine.
    const drift = 1 + 0.035 * Math.sin(2 * Math.PI * wob * t);
    let v = 0;
    for (const [h, a] of harms) {
      v += Math.sin(2 * Math.PI * f0 * h * drift * t) * a;
    }
    v /= 3.4;
    // A little filtered noise for the raspy edge; kept low so the loop seam
    // stays inaudible.
    lp += 0.06 * (rand() - lp);
    out[i] = (v + lp * 0.16) * br;
  }
  return normalize(out, 0.82);
}

// ---------- the 8 sound identities (v5) ----------

// SPARK - a small win. Marimba run up, then a bright confetti of two tines.
function sndSpark() {
  const out = buf(2.0);
  add(out, mallet(523.25, 0.8, 0.62), 0.0);
  add(out, mallet(659.25, 0.8, 0.58), 0.10);
  add(out, mallet(783.99, 0.9, 0.56), 0.20);
  add(out, mallet(1046.5, 1.0, 0.60), 0.31);
  add(out, tine(1567.98, 1.0, 0.24), 0.42);
  add(out, shimmer(1318.5, 1.3, 0.16), 0.30);
  add(out, tine(2093.0, 0.7, 0.12), 0.52);
  return softclip(normalize(reverb(out, 0.24)));
}

// WONDER - "oh, what IS that?" A glassy rise with a questioning lift and air.
function sndWonder() {
  const out = buf(3.0);
  add(out, bell(587.33, 1.8, 0.40, 0.75), 0.0);
  add(out, bell(783.99, 1.8, 0.38, 0.70), 0.26);
  add(out, bell(987.77, 2.0, 0.38, 0.65), 0.52);
  add(out, bell(1174.66, 2.2, 0.40, 0.60), 0.80);
  // the lift: a short portamento that makes it feel like a question
  const t0 = Math.round(1.05 * SR), tn = Math.round(0.55 * SR);
  let ph = 0;
  for (let i = 0; i < tn && t0 + i < out.length; i++) {
    const p = i / tn;
    const f = 1174.66 * Math.pow(1.5, p * p);
    ph += 2 * Math.PI * f / SR;
    out[t0 + i] += Math.sin(ph) * Math.exp(-3.2 * p) * 0.20;
  }
  add(out, shimmer(2349.3, 1.8, 0.12), 0.95);
  add(out, whoosh(1.4, 500, 3500, 0.10), 0.0);
  return normalize(reverb(out, 0.34));
}

// MED - someone cares. Warm pad, music-box waltz, and a soft reassuring low C.
function sndMed() {
  const out = buf(3.2);
  add(out, pad([130.81, 196.0, 261.63], 3.0, 0.22, 0), 0.0);
  add(out, bass(65.41, 1.6, 0.30), 0.0);
  add(out, tine(783.99, 1.5, 0.52), 0.0);
  add(out, tine(659.25, 1.6, 0.50), 0.46);
  add(out, tine(523.25, 1.8, 0.52), 0.92);
  add(out, tine(1046.5, 1.4, 0.22), 1.30);
  add(out, tine(1318.5, 1.4, 0.16), 1.55);
  add(out, shimmer(392.0, 2.0, 0.13), 0.30);
  return normalize(reverb(out, 0.27));
}

// JOG - stand up. A real pocket groove: kick, claps, hats, walking bass.
function sndJog() {
  const out = buf(1.8);
  add(out, kick(1.0), 0.0);
  add(out, tick(0.42), 0.13);
  add(out, clap(0.52), 0.29);
  add(out, bass(110.0, 0.40, 0.52), 0.30);
  add(out, kick(0.72), 0.55);
  add(out, tick(0.36), 0.68);
  add(out, bass(146.83, 0.40, 0.48), 0.585);
  add(out, clap(0.46), 0.855);
  add(out, bass(130.81, 0.45, 0.50), 0.87);
  add(out, tick(0.46), 1.10);
  add(out, kick(0.62), 1.10);
  add(out, bass(164.81, 0.50, 0.52), 1.15);
  add(out, clap(0.38), 1.42);
  add(out, tine(880.0, 0.5, 0.20), 1.45);
  add(out, tine(1174.66, 0.5, 0.14), 1.58);
  return softclip(normalize(out));
}

// BATH - water, let go. Rain wash down and real droplets falling in pitch.
function sndBath() {
  const out = buf(2.8);
  add(out, whoosh(2.0, 3000, 300, 0.42), 0.0);
  add(out, whoosh(1.4, 900, 220, 0.16), 0.5);
  add(out, droplet(1760.0, 0.5, 0.32, 0.5), 0.28);
  add(out, droplet(2093.0, 0.5, 0.28, 0.55), 0.63);
  add(out, droplet(1567.98, 0.6, 0.26, 0.5), 0.96);
  add(out, droplet(2637.0, 0.5, 0.22, 0.6), 1.28);
  add(out, shimmer(523.25, 1.6, 0.12), 0.9);
  add(out, tine(880.0, 1.0, 0.18), 1.55);
  return normalize(reverb(out, 0.34));
}

// GUARDIAN - kind, not shaming. A soft wooden double-tap, then a warm landing.
function sndDoom() {
  const out = buf(2.8);
  add(out, mallet(196.0, 0.5, 0.62), 0.0);
  add(out, mallet(196.0, 0.5, 0.58), 0.26);
  add(out, mallet(233.08, 0.5, 0.58), 0.52);
  // the lift: a major third opening up, not a siren
  const t0 = Math.round(0.78 * SR), tn = Math.round(0.60 * SR);
  for (let i = 0; i < tn && t0 + i < out.length; i++) {
    const t = i / SR, p = i / tn;
    const e = Math.min(1, p * 3.5) * Math.max(0, 1 - p * 0.35);
    out[t0 + i] += (Math.sin(2 * Math.PI * 440 * t) * 0.5
      + Math.sin(2 * Math.PI * 554.37 * t) * 0.42
      + Math.sin(2 * Math.PI * 659.25 * t) * 0.30) * 0.16 * e;
  }
  add(out, tine(659.25, 1.4, 0.42), 1.42);
  add(out, tine(523.25, 1.5, 0.34), 1.58);
  add(out, bass(98.0, 1.2, 0.26), 1.40);
  return softclip(normalize(reverb(out, 0.26)));
}

// SLEEP - safe to rest. Deep pad, slow tide, a lullaby that trails off.
function sndSleep() {
  const out = buf(6.0);
  add(out, pad([65.41, 130.81, 164.81, 196.0, 246.94], 5.6, 0.34, 0.35), 0.0);
  add(out, whoosh(5.0, 700, 180, 0.09), 0.3);
  add(out, bass(65.41, 3.0, 0.22), 0.0);
  add(out, tine(523.25, 1.8, 0.28), 0.7);
  add(out, tine(440.0, 1.8, 0.28), 1.35);
  add(out, tine(392.0, 1.9, 0.28), 2.0);
  add(out, tine(329.63, 2.2, 0.30), 2.65);
  add(out, tine(261.63, 2.4, 0.24), 3.4);
  add(out, shimmer(196.0, 2.6, 0.10), 3.0);
  return normalize(reverb(out, 0.40));
}

// MISO - the purr, with a chirp on top. Loops seamlessly.
function sndMiso() {
  const p = purr(3.0);
  const out = buf(3.0);
  for (let i = 0; i < out.length; i++) out[i] += p[i] * 0.85;
  // A single soft greeting over the rumble, well inside the loop so the
  // seam stays clean.
  add(out, tine(1318.5, 0.5, 0.22), 0.55);
  add(out, tine(1760.0, 0.6, 0.18), 0.78);
  return softclip(out);
}

const files = {
  task_jog: sndJog(),
  task_med: sndMed(),
  task_bath: sndBath(),
  task_doom: sndDoom(),
  task_sleep: sndSleep(),
  task_spark: sndSpark(),
  task_wonder: sndWonder(),
  task_miso: sndMiso(),
};
for (const [name, s] of Object.entries(files)) {
  // The purr loops, so it must not have its edges faded.
  const looped = (name === 'task_miso');
  fs.writeFileSync(path.join(outDir, name + '.wav'), wav(s, !looped));
  process.stdout.write('wrote ' + name + '.wav' + (looped ? '  (loop-safe)' : '') + '\n');
}
process.stdout.write('DONE\n');
