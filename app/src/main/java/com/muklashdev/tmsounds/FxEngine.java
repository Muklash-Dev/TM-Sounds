package com.muklashdev.tmsounds;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.BassBoost;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
import android.os.Build;

/**
 * TM Sounds engine: wraps Android's built-in audio effects (public
 * android.media.audiofx APIs) on the global output session (session 0).
 * Every effect is created defensively - on devices whose audio HAL does
 * not offer one of them, that effect is simply marked unavailable.
 *
 * Presets: each preset stores its own EQ bands, bass, mid/treble enhancer
 * levels and surround; slider changes are written back into the active
 * preset, so every preset keeps the user's own version of its defaults.
 *
 * Developed by MD. Muklash Mia - https://github.com/Muklash-Dev
 */
public class FxEngine {

    public static final String PREFS = "tm_sounds_prefs";
    public static final String[] PRESETS =
            {"Flat", "Music", "Movie", "Voice", "Bass", "Custom"};

    private Equalizer equalizer;
    private BassBoost bassBoost;
    private Virtualizer virtualizer;
    private LoudnessEnhancer loudness;
    private DynamicsProcessing leveler;

    public boolean hasEq, hasBass, hasVirtualizer, hasLoudness, hasLeveler;

    public FxEngine() {
        try { equalizer = new Equalizer(0, 0); hasEq = true; }
        catch (Exception e) { equalizer = null; }
        try { bassBoost = new BassBoost(0, 0); hasBass = true; }
        catch (Exception e) { bassBoost = null; }
        try { virtualizer = new Virtualizer(0, 0); hasVirtualizer = true; }
        catch (Exception e) { virtualizer = null; }
        try { loudness = new LoudnessEnhancer(0); hasLoudness = true; }
        catch (Exception e) { loudness = null; }
    }

    // ---------- basic metadata ----------

    public int bandCount() {
        return hasEq ? equalizer.getNumberOfBands() : 0;
    }

    public int bandCenterHz(short band) {
        return hasEq ? equalizer.getCenterFreq(band) / 1000 : 0;
    }

    public short[] bandRange() {
        return hasEq ? equalizer.getBandLevelRange() : new short[]{-1500, 1500};
    }

    // ---------- preset storage (shared by activity + service) ----------

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Factory EQ gain in dB for a band by its center frequency. */
    public static double factoryGainDb(String preset, int hz) {
        switch (preset) {
            case "Music":
                if (hz < 250) return 3; if (hz < 1000) return 1;
                if (hz < 4000) return 0; if (hz < 8000) return 2; return 3;
            case "Movie":
                if (hz < 250) return 4; if (hz < 1000) return 1;
                if (hz < 4000) return -1; if (hz < 8000) return 2; return 2;
            case "Voice":
                if (hz < 250) return -3; if (hz < 1000) return 0;
                if (hz < 4000) return 4; if (hz < 8000) return 3; return 1;
            case "Bass":
                if (hz < 250) return 6; if (hz < 1000) return 3; return 0;
            default: return 0; // Flat & Custom start flat
        }
    }

    public static int factoryBass(String name) {
        switch (name) {
            case "Music": return 300; case "Movie": return 400;
            case "Bass": return 700; default: return 0;
        }
    }

    public static int factoryVirt(String name) {
        switch (name) {
            case "Music": return 300; case "Movie": return 600;
            case "Bass": return 200; default: return 0;
        }
    }

    public static boolean factoryBassOn(String name) {
        return name.equals("Music") || name.equals("Movie") || name.equals("Bass");
    }

    public static int factoryMid(String name) {
        switch (name) {
            case "Music": return 30; case "Movie": return 40;
            case "Voice": return 60; case "Bass": return 10; default: return 0;
        }
    }

    public static boolean factoryMidOn(String name) {
        return factoryMid(name) > 0;
    }

    public static int factoryTreble(String name) {
        switch (name) {
            case "Music": return 20; case "Movie": return 30;
            case "Voice": return 40; default: return 0;
        }
    }

    public static boolean factoryTrebleOn(String name) {
        return factoryTreble(name) > 0;
    }

    /** Create a preset's stored settings from factory defaults if needed. */
    public static void seedPreset(Context ctx, String name, boolean force) {
        SharedPreferences p = prefs(ctx);
        int count = p.getInt("band_count", 0);
        if (count == 0) return; // bands unknown until the engine ran once
        if (!force && p.getBoolean("p_" + name + "_init", false)) return;
        int min = p.getInt("band_min", -1500);
        int max = p.getInt("band_max", 1500);
        SharedPreferences.Editor e = p.edit();
        for (int b = 0; b < count; b++) {
            int hz = p.getInt("band_center_" + b, 0);
            int v = (int) (factoryGainDb(name, hz) * 100);
            if (v < min) v = min;
            if (v > max) v = max;
            e.putInt("p_" + name + "_band_" + b, v);
        }
        e.putInt("p_" + name + "_bass", factoryBass(name));
        e.putBoolean("p_" + name + "_bass_on", factoryBassOn(name));
        e.putInt("p_" + name + "_mid", factoryMid(name));
        e.putBoolean("p_" + name + "_mid_on", factoryMidOn(name));
        e.putInt("p_" + name + "_treble", factoryTreble(name));
        e.putBoolean("p_" + name + "_treble_on", factoryTrebleOn(name));
        e.putInt("p_" + name + "_virt", factoryVirt(name));
        e.putBoolean("p_" + name + "_init", true);
        e.apply();
    }

    /** Copy a preset's stored settings into the live keys the engine reads. */
    public static void loadPresetToLive(Context ctx, String name) {
        SharedPreferences p = prefs(ctx);
        int count = p.getInt("band_count", 0);
        SharedPreferences.Editor e = p.edit();
        for (int b = 0; b < count; b++) {
            e.putInt("band_" + b, p.getInt("p_" + name + "_band_" + b, 0));
        }
        e.putInt("bass", p.getInt("p_" + name + "_bass", 0));
        e.putBoolean("bass_on", p.getBoolean("p_" + name + "_bass_on", false));
        e.putInt("mid", p.getInt("p_" + name + "_mid", 0));
        e.putBoolean("mid_on", p.getBoolean("p_" + name + "_mid_on", false));
        e.putInt("treble", p.getInt("p_" + name + "_treble", 0));
        e.putBoolean("treble_on", p.getBoolean("p_" + name + "_treble_on", false));
        e.putInt("virt", p.getInt("p_" + name + "_virt", 0));
        e.apply();
    }

    // ---------- applying ----------

    /** Effective band level = preset band + mid/treble enhancer offsets. */
    private int effectiveLevel(SharedPreferences p, int band) {
        int level = p.getInt("band_" + band, 0);
        int hz = p.getInt("band_center_" + band, 0);
        if (p.getBoolean("mid_on", false) && hz >= 400 && hz < 4000) {
            level += p.getInt("mid", 0) * 6;      // 0..100% -> 0..+6 dB
        }
        if (p.getBoolean("treble_on", false) && hz >= 6000) {
            level += p.getInt("treble", 0) * 6;
        }
        short[] r = bandRange();
        if (level < r[0]) level = r[0];
        if (level > r[1]) level = r[1];
        return level;
    }

    public void applyFromPrefs(Context ctx) {
        SharedPreferences p = prefs(ctx);
        boolean on = p.getBoolean("enabled", false);
        if (hasEq) {
            int n = bandCount();
            for (short b = 0; b < n; b++) {
                try { equalizer.setBandLevel(b, (short) effectiveLevel(p, b)); }
                catch (Exception ignored) {}
            }
            try { equalizer.setEnabled(on); } catch (Exception ignored) {}
        }
        if (hasBass) {
            try { bassBoost.setStrength((short) p.getInt("bass", 0)); }
            catch (Exception ignored) {}
            try { bassBoost.setEnabled(on && p.getBoolean("bass_on", false)); }
            catch (Exception ignored) {}
        }
        if (hasVirtualizer) {
            try { virtualizer.setStrength((short) p.getInt("virt", 0)); }
            catch (Exception ignored) {}
            try { virtualizer.setEnabled(on); } catch (Exception ignored) {}
        }
        if (hasLoudness) {
            try { loudness.setTargetGain(p.getInt("boost_mb", 0)); }
            catch (Exception ignored) {}
            try { loudness.setEnabled(on); } catch (Exception ignored) {}
        }
        applyLeveler(ctx, p, on);
    }

    /** Volume Leveler via DynamicsProcessing (Android 9+): a gentle
     *  multiband compressor + limiter on the output session. */
    private void applyLeveler(Context ctx, SharedPreferences p, boolean masterOn) {
        boolean want = masterOn && p.getBoolean("leveler_on", false);
        String status;
        if (!p.getBoolean("leveler_on", false)) {
            status = "Off";
        } else if (Build.VERSION.SDK_INT < 28) {
            status = "Needs Android 9 or above";
        } else if (ensureLeveler()) {
            status = "On - keeping volume consistent";
        } else {
            status = "Not supported on this device";
        }
        if (leveler != null) {
            try { leveler.setEnabled(want && hasLeveler); }
            catch (Exception ignored) {}
        }
        p.edit().putString("leveler_status", status)
                .putBoolean("leveler_available", hasLeveler).apply();
    }

    private boolean ensureLeveler() {
        if (leveler != null) return hasLeveler;
        if (Build.VERSION.SDK_INT < 28) { hasLeveler = false; return false; }
        try {
            DynamicsProcessing.Mbc mbc =
                    new DynamicsProcessing.Mbc(true, true, 1);
            mbc.setBand(0, new DynamicsProcessing.MbcBand(
                    true, 20000f, 10f, 120f, 2.5f, -18f, 6f, -45f, 1f, 0f, 0f));
            DynamicsProcessing.Limiter limiter =
                    new DynamicsProcessing.Limiter(
                            true, true, 0, 4f, 60f, 12f, -2f, 0f);
            DynamicsProcessing.Config config =
                    new DynamicsProcessing.Config.Builder(
                            DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION,
                            2, false, 0, true, 1, false, 0, true)
                            .setMbcAllChannelsTo(mbc)
                            .setLimiterAllChannelsTo(limiter)
                            .build();
            leveler = new DynamicsProcessing(0, 0, config);
            hasLeveler = true;
        } catch (Exception e) {
            leveler = null;
            hasLeveler = false;
        }
        return hasLeveler;
    }

    public void setEnabled(boolean on) {
        try { if (equalizer != null) equalizer.setEnabled(on); } catch (Exception ignored) {}
        try { if (virtualizer != null) virtualizer.setEnabled(on); } catch (Exception ignored) {}
        try { if (loudness != null) loudness.setEnabled(on); } catch (Exception ignored) {}
        if (!on) {
            try { if (bassBoost != null) bassBoost.setEnabled(false); } catch (Exception ignored) {}
            try { if (leveler != null) leveler.setEnabled(false); } catch (Exception ignored) {}
        }
    }

    /** Publish band metadata + availability so the activity can build its UI. */
    public void publishMetadata(Context ctx) {
        SharedPreferences.Editor e = prefs(ctx).edit();
        e.putBoolean("meta_ready", hasEq);
        e.putInt("band_count", bandCount());
        short[] r = bandRange();
        e.putInt("band_min", r[0]);
        e.putInt("band_max", r[1]);
        for (short b = 0; b < bandCount(); b++) {
            e.putInt("band_center_" + b, bandCenterHz(b));
        }
        e.putString("engine_status",
                "Equalizer " + tick(hasEq)
                        + "  -  Bass Boost " + tick(hasBass)
                        + "  -  Surround " + tick(hasVirtualizer)
                        + "  -  Volume Boost " + tick(hasLoudness));
        e.apply();
    }

    private String tick(boolean b) { return b ? "OK" : "not on this device"; }

    public void release() {
        try { if (equalizer != null) equalizer.release(); } catch (Exception ignored) {}
        try { if (bassBoost != null) bassBoost.release(); } catch (Exception ignored) {}
        try { if (virtualizer != null) virtualizer.release(); } catch (Exception ignored) {}
        try { if (loudness != null) loudness.release(); } catch (Exception ignored) {}
        try { if (leveler != null) leveler.release(); } catch (Exception ignored) {}
        equalizer = null; bassBoost = null; virtualizer = null;
        loudness = null; leveler = null;
    }
}
