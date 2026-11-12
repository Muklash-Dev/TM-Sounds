package com.muklashdev.tmsounds;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TM Sounds - a no-root sound enhancer for Android 8.0+ built only on
 * Android's public audiofx APIs. Premium dark UI with three pages:
 * Home (profiles, per-device memory), Equalizer (curve + sliders + band
 * tuner) and Advanced (Bass/Mid/Treble enhancers, Volume Boost, Volume
 * Leveler). Every profile keeps its own editable settings.
 *
 * Developed by MD. Muklash Mia
 * Telegram: https://t.me/muklash_dev
 * GitHub:   https://github.com/Muklash-Dev
 */
public class MainActivity extends Activity {

    private SharedPreferences prefs;
    private boolean uiSync = false;
    private int tunerBand = 0;

    // home
    private WaveView waveView;
    private Switch masterSwitch;
    private TextView masterStatus, deviceStatus;
    private LinearLayout carousel;
    private Switch deviceMemorySwitch;
    // eq
    private Spinner presetSpinner;
    private CurveView curveView;
    private View curveCard, slidersCard;
    private Button viewCurveBtn, viewSlidersBtn;
    private LinearLayout eqContainer;
    private TextView eqHint, tunerLabel;
    private SeekBar tunerSeek;
    private final List<SeekBar> bandSeeks = new ArrayList<>();
    private final List<TextView> bandValueLabels = new ArrayList<>();
    // advanced
    private Switch bassOnSwitch, midOnSwitch, trebleOnSwitch, levelerSwitch;
    private SeekBar bassSeek, midSeek, trebleSeek, virtSeek, boostSeek;
    private TextView bassValue, midValue, trebleValue, virtValue, boostValue;
    private TextView levelerStatus, engineStatusText;
    // nav
    private View homePanel, eqPanel, advPanel;
    private LinearLayout navHome, navEq, navAdv;
    private ImageView navHomeIcon, navEqIcon, navAdvIcon;
    private TextView navHomeLabel, navEqLabel, navAdvLabel;

    private String activePreset() {
        return prefs.getString("preset", "Flat");
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Edge.pad(findViewById(R.id.main_root));
        prefs = FxEngine.prefs(this);

        // ---- bind views ----
        waveView = findViewById(R.id.waveView);
        masterSwitch = findViewById(R.id.masterSwitch);
        masterStatus = findViewById(R.id.masterStatus);
        carousel = findViewById(R.id.carousel);
        deviceMemorySwitch = findViewById(R.id.deviceMemorySwitch);
        deviceStatus = findViewById(R.id.deviceStatus);
        presetSpinner = findViewById(R.id.presetSpinner);
        curveView = findViewById(R.id.curveView);
        curveCard = findViewById(R.id.curveCard);
        slidersCard = findViewById(R.id.slidersCard);
        viewCurveBtn = findViewById(R.id.viewCurveBtn);
        viewSlidersBtn = findViewById(R.id.viewSlidersBtn);
        eqContainer = findViewById(R.id.eqContainer);
        eqHint = findViewById(R.id.eqHint);
        tunerLabel = findViewById(R.id.tunerLabel);
        tunerSeek = findViewById(R.id.tunerSeek);
        bassOnSwitch = findViewById(R.id.bassOnSwitch);
        midOnSwitch = findViewById(R.id.midOnSwitch);
        trebleOnSwitch = findViewById(R.id.trebleOnSwitch);
        levelerSwitch = findViewById(R.id.levelerSwitch);
        bassSeek = findViewById(R.id.bassSeek);
        midSeek = findViewById(R.id.midSeek);
        trebleSeek = findViewById(R.id.trebleSeek);
        virtSeek = findViewById(R.id.virtSeek);
        boostSeek = findViewById(R.id.boostSeek);
        bassValue = findViewById(R.id.bassValue);
        midValue = findViewById(R.id.midValue);
        trebleValue = findViewById(R.id.trebleValue);
        virtValue = findViewById(R.id.virtValue);
        boostValue = findViewById(R.id.boostValue);
        levelerStatus = findViewById(R.id.levelerStatus);
        engineStatusText = findViewById(R.id.engineStatusText);
        homePanel = findViewById(R.id.homePanel);
        eqPanel = findViewById(R.id.eqPanel);
        advPanel = findViewById(R.id.advPanel);
        navHome = findViewById(R.id.navHome);
        navEq = findViewById(R.id.navEq);
        navAdv = findViewById(R.id.navAdv);
        navHomeIcon = findViewById(R.id.navHomeIcon);
        navEqIcon = findViewById(R.id.navEqIcon);
        navAdvIcon = findViewById(R.id.navAdvIcon);
        navHomeLabel = findViewById(R.id.navHomeLabel);
        navEqLabel = findViewById(R.id.navEqLabel);
        navAdvLabel = findViewById(R.id.navAdvLabel);

        // ---- master switch ----
        masterSwitch.setChecked(prefs.getBoolean("enabled", false));
        updateMasterUi();
        masterSwitch.setOnCheckedChangeListener((btn, on) -> {
            prefs.edit().putBoolean("enabled", on).apply();
            updateMasterUi();
            if (on) {
                maybeRequestNotificationPermission();
                AudioService.start(this);
                Toast.makeText(this, "TM Sounds ON", Toast.LENGTH_SHORT).show();
                new Handler().postDelayed(this::refreshDynamic, 900);
            } else {
                AudioService.stop(this);
                Toast.makeText(this, "TM Sounds OFF", Toast.LENGTH_SHORT).show();
            }
        });

        // ---- per-device memory ----
        deviceMemorySwitch.setChecked(prefs.getBoolean("per_device_memory", false));
        deviceMemorySwitch.setOnCheckedChangeListener((btn, on) -> {
            prefs.edit().putBoolean("per_device_memory", on).apply();
            if (on) saveDeviceMapping();
            updateDeviceStatus();
            Toast.makeText(this, on
                    ? "Per-device memory ON"
                    : "Per-device memory OFF", Toast.LENGTH_SHORT).show();
        });

        // ---- preset spinner ----
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, FxEngine.PRESETS) {
            @Override public View getView(int pos, View cv, ViewGroup parent) {
                TextView t = (TextView) super.getView(pos, cv, parent);
                t.setTextColor(getColor(R.color.text_primary));
                t.setTextSize(16);
                return t;
            }
            @Override public View getDropDownView(int pos, View cv, ViewGroup parent) {
                TextView t = (TextView) super.getDropDownView(pos, cv, parent);
                t.setTextColor(getColor(R.color.text_primary));
                t.setBackgroundColor(getColor(R.color.card_hi));
                t.setPadding(dp(14), dp(12), dp(14), dp(12));
                return t;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        presetSpinner.setAdapter(adapter);
        presetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View v,
                                                 int pos, long id) {
                if (uiSync) return;
                String name = FxEngine.PRESETS[pos];
                if (!name.equals(activePreset())) selectPreset(name);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // ---- eq view toggle ----
        viewCurveBtn.setOnClickListener(v -> setEqView(true));
        viewSlidersBtn.setOnClickListener(v -> setEqView(false));
        setEqView(true);

        // ---- curve ----
        curveView.setOnBandChanged((band, mb) -> {
            tunerBand = band;
            setBandLevel(band, mb);
        });

        // ---- band tuner ----
        findViewById(R.id.tunerPrev).setOnClickListener(v -> {
            int n = prefs.getInt("band_count", 0);
            if (n > 0) { tunerBand = (tunerBand + n - 1) % n; updateTuner(); }
        });
        findViewById(R.id.tunerNext).setOnClickListener(v -> {
            int n = prefs.getInt("band_count", 0);
            if (n > 0) { tunerBand = (tunerBand + 1) % n; updateTuner(); }
        });
        tunerSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int prog, boolean user) {
                if (!user || uiSync) return;
                int min = prefs.getInt("band_min", -1500);
                setBandLevel(tunerBand, prog + min);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        findViewById(R.id.tunerMinus).setOnClickListener(v ->
                stepBand(-10));
        findViewById(R.id.tunerPlus).setOnClickListener(v ->
                stepBand(10));
        findViewById(R.id.tunerZero).setOnClickListener(v ->
                setBandLevel(tunerBand, 0));
        findViewById(R.id.resetPresetBtn).setOnClickListener(v -> {
            String name = activePreset();
            FxEngine.seedPreset(this, name, true);
            FxEngine.loadPresetToLive(this, name);
            syncSoundUi();
            applyIfOn();
            Toast.makeText(this, name + " reset to defaults",
                    Toast.LENGTH_SHORT).show();
        });

        // ---- advanced: enhancers ----
        bassOnSwitch.setOnCheckedChangeListener((btn, on) -> {
            if (uiSync) return;
            prefs.edit().putBoolean("bass_on", on)
                    .putBoolean("p_" + activePreset() + "_bass_on", on).apply();
            applyIfOn();
        });
        midOnSwitch.setOnCheckedChangeListener((btn, on) -> {
            if (uiSync) return;
            prefs.edit().putBoolean("mid_on", on)
                    .putBoolean("p_" + activePreset() + "_mid_on", on).apply();
            applyIfOn();
        });
        trebleOnSwitch.setOnCheckedChangeListener((btn, on) -> {
            if (uiSync) return;
            prefs.edit().putBoolean("treble_on", on)
                    .putBoolean("p_" + activePreset() + "_treble_on", on).apply();
            applyIfOn();
        });
        bassSeek.setMax(1000);
        bassSeek.setOnSeekBarChangeListener(simpleListener(() -> {
            int v = bassSeek.getProgress();
            prefs.edit().putInt("bass", v)
                    .putInt("p_" + activePreset() + "_bass", v).apply();
            bassValue.setText((v / 10) + "%");
            applyIfOn();
        }));
        midSeek.setMax(100);
        midSeek.setOnSeekBarChangeListener(simpleListener(() -> {
            int v = midSeek.getProgress();
            prefs.edit().putInt("mid", v)
                    .putInt("p_" + activePreset() + "_mid", v).apply();
            midValue.setText(v + "%");
            applyIfOn();
        }));
        trebleSeek.setMax(100);
        trebleSeek.setOnSeekBarChangeListener(simpleListener(() -> {
            int v = trebleSeek.getProgress();
            prefs.edit().putInt("treble", v)
                    .putInt("p_" + activePreset() + "_treble", v).apply();
            trebleValue.setText(v + "%");
            applyIfOn();
        }));
        virtSeek.setMax(1000);
        virtSeek.setOnSeekBarChangeListener(simpleListener(() -> {
            int v = virtSeek.getProgress();
            prefs.edit().putInt("virt", v)
                    .putInt("p_" + activePreset() + "_virt", v).apply();
            virtValue.setText((v / 10) + "%");
            applyIfOn();
        }));

        // ---- advanced: volume ----
        boostSeek.setMax(1200);
        boostSeek.setOnSeekBarChangeListener(simpleListener(() -> {
            prefs.edit().putInt("boost_mb", boostSeek.getProgress()).apply();
            updateBoostLabel();
            saveDeviceBoost();
            applyIfOn();
        }));
        levelerSwitch.setChecked(prefs.getBoolean("leveler_on", false));
        levelerSwitch.setOnCheckedChangeListener((btn, on) -> {
            if (uiSync) return;
            prefs.edit().putBoolean("leveler_on", on).apply();
            applyIfOn();
            new Handler().postDelayed(
                    () -> levelerStatus.setText("Leveler: "
                            + prefs.getString("leveler_status", "…")), 800);
        });

        // ---- about / links ----
        findViewById(R.id.aboutBtn).setOnClickListener(v -> showAbout());
        findViewById(R.id.telegramBtn).setOnClickListener(v ->
                openUrl("https://t.me/muklash_dev"));
        findViewById(R.id.githubBtn).setOnClickListener(v ->
                openUrl("https://github.com/Muklash-Dev"));

        // ---- nav ----
        navHome.setOnClickListener(v -> showPanel(0));
        navEq.setOnClickListener(v -> showPanel(1));
        navAdv.setOnClickListener(v -> showPanel(2));
        showPanel(0);

        buildCarousel();
        refreshDynamic();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDynamic();
    }

    // ---------- navigation ----------

    private void showPanel(int which) {
        homePanel.setVisibility(which == 0 ? View.VISIBLE : View.GONE);
        eqPanel.setVisibility(which == 1 ? View.VISIBLE : View.GONE);
        advPanel.setVisibility(which == 2 ? View.VISIBLE : View.GONE);
        styleNav(navHome, navHomeIcon, navHomeLabel, which == 0);
        styleNav(navEq, navEqIcon, navEqLabel, which == 1);
        styleNav(navAdv, navAdvIcon, navAdvLabel, which == 2);
    }

    private void styleNav(LinearLayout item, ImageView icon, TextView label,
                          boolean selected) {
        if (selected) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(getColor(R.color.accent));
            bg.setCornerRadius(dp(24));
            item.setBackground(bg);
            icon.setColorFilter(0xFF23233A);
            label.setVisibility(View.VISIBLE);
            label.setTextColor(0xFF23233A);
        } else {
            item.setBackground(null);
            icon.setColorFilter(getColor(R.color.text_secondary));
            label.setVisibility(View.GONE);
        }
    }

    // ---------- home ----------

    private void updateMasterUi() {
        boolean on = prefs.getBoolean("enabled", false);
        masterStatus.setText(on ? "On" : "Off");
        waveView.setActive(on);
    }

    private int iconFor(String preset) {
        switch (preset) {
            case "Music": return R.drawable.ic_prof_music;
            case "Movie": return R.drawable.ic_prof_movie;
            case "Voice": return R.drawable.ic_prof_voice;
            case "Bass": return R.drawable.ic_stat_sound;
            case "Custom": return R.drawable.ic_prof_spark;
            default: return R.drawable.ic_prof_flat;
        }
    }

    private void buildCarousel() {
        carousel.removeAllViews();
        String active = activePreset();
        int accent = getColor(R.color.accent);
        int cardHi = getColor(R.color.card_hi);
        for (String name : FxEngine.PRESETS) {
            boolean sel = name.equals(active);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    dp(118), ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.setMarginEnd(dp(10));
            card.setLayoutParams(clp);
            card.setOnClickListener(v -> selectPreset(name));

            FrameLayout iconBox = new FrameLayout(this);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(sel ? accent : cardHi);
            bg.setCornerRadius(dp(18));
            iconBox.setBackground(bg);
            iconBox.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(92)));

            ImageView icon = new ImageView(this);
            icon.setImageResource(iconFor(name));
            icon.setColorFilter(sel ? 0xFF23233A : accent);
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(
                    dp(38), dp(38), Gravity.CENTER);
            icon.setLayoutParams(ilp);
            iconBox.addView(icon);

            if (sel) {
                TextView check = new TextView(this);
                check.setText("✓");
                check.setTextColor(0xFF23233A);
                check.setTextSize(15);
                FrameLayout.LayoutParams chlp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.END);
                chlp.setMargins(0, dp(6), dp(10), 0);
                check.setLayoutParams(chlp);
                iconBox.addView(check);
            }
            card.addView(iconBox);

            TextView label = new TextView(this);
            label.setText(name);
            label.setGravity(Gravity.CENTER);
            label.setTextSize(14);
            label.setTextColor(sel
                    ? getColor(R.color.text_primary)
                    : getColor(R.color.text_secondary));
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.topMargin = dp(7);
            label.setLayoutParams(llp);
            card.addView(label);

            carousel.addView(card);
        }
    }

    private void updateDeviceStatus() {
        String key = prefs.getString("current_device_key", null);
        if (!prefs.getBoolean("per_device_memory", false)) {
            deviceStatus.setText("");
            return;
        }
        deviceStatus.setText("Current output: " + deviceLabel(key));
    }

    private String deviceLabel(String key) {
        if (key == null) return "speaker";
        if (key.startsWith("bt_")) return "Bluetooth · " + key.substring(3).replace('_', ' ');
        if (key.startsWith("usb_")) return "USB · " + key.substring(4).replace('_', ' ');
        if (key.equals("wired")) return "Wired headphones";
        return "Speaker";
    }

    private void saveDeviceMapping() {
        String key = prefs.getString("current_device_key", null);
        if (key == null) return;
        prefs.edit().putString("dev_preset_" + key, activePreset()).apply();
    }

    private void saveDeviceBoost() {
        if (!prefs.getBoolean("per_device_memory", false)) return;
        String key = prefs.getString("current_device_key", null);
        if (key == null) return;
        prefs.edit().putInt("dev_boost_" + key,
                prefs.getInt("boost_mb", 0)).apply();
    }

    // ---------- presets / sound sync ----------

    private void selectPreset(String name) {
        prefs.edit().putString("preset", name).apply();
        FxEngine.seedPreset(this, name, false);
        FxEngine.loadPresetToLive(this, name);
        if (prefs.getBoolean("per_device_memory", false)) saveDeviceMapping();
        buildCarousel();
        syncSoundUi();
        applyIfOn();
        Toast.makeText(this, name + " profile", Toast.LENGTH_SHORT).show();
    }

    private void refreshDynamic() {
        engineStatusText.setText(prefs.getString("engine_status",
                "Engine not started yet - switch TM Sounds ON once."));
        levelerStatus.setText("Leveler: "
                + prefs.getString("leveler_status", "Off"));
        String active = activePreset();
        FxEngine.seedPreset(this, active, false);
        FxEngine.loadPresetToLive(this, active);
        buildCarousel();
        syncSoundUi();
        updateDeviceStatus();
        uiSync = true;
        boostSeek.setProgress(prefs.getInt("boost_mb", 0));
        levelerSwitch.setChecked(prefs.getBoolean("leveler_on", false));
        uiSync = false;
        updateBoostLabel();
    }

    private void syncSoundUi() {
        uiSync = true;
        int idx = 0;
        for (int i = 0; i < FxEngine.PRESETS.length; i++) {
            if (FxEngine.PRESETS[i].equals(activePreset())) idx = i;
        }
        presetSpinner.setSelection(idx);
        bassOnSwitch.setChecked(prefs.getBoolean("bass_on", false));
        midOnSwitch.setChecked(prefs.getBoolean("mid_on", false));
        trebleOnSwitch.setChecked(prefs.getBoolean("treble_on", false));
        bassSeek.setProgress(prefs.getInt("bass", 0));
        midSeek.setProgress(prefs.getInt("mid", 0));
        trebleSeek.setProgress(prefs.getInt("treble", 0));
        virtSeek.setProgress(prefs.getInt("virt", 0));
        uiSync = false;
        bassValue.setText((prefs.getInt("bass", 0) / 10) + "%");
        midValue.setText(prefs.getInt("mid", 0) + "%");
        trebleValue.setText(prefs.getInt("treble", 0) + "%");
        virtValue.setText((prefs.getInt("virt", 0) / 10) + "%");
        buildEqSliders();
        feedCurve();
        updateTuner();
    }

    // ---------- equalizer ----------

    private void setEqView(boolean curve) {
        curveCard.setVisibility(curve ? View.VISIBLE : View.GONE);
        slidersCard.setVisibility(curve ? View.GONE : View.VISIBLE);
        styleChip(viewCurveBtn, curve);
        styleChip(viewSlidersBtn, !curve);
    }

    private void styleChip(Button b, boolean selected) {
        if (selected) {
            b.setBackgroundTintList(
                    ColorStateList.valueOf(getColor(R.color.primary)));
            b.setTextColor(Color.WHITE);
        } else {
            b.setBackgroundTintList(
                    ColorStateList.valueOf(getColor(R.color.chip_bg)));
            b.setTextColor(getColor(R.color.text_primary));
        }
    }

    private void feedCurve() {
        int count = prefs.getInt("band_count", 0);
        if (count == 0) return;
        int[] centers = new int[count];
        int[] levels = new int[count];
        for (int b = 0; b < count; b++) {
            centers[b] = prefs.getInt("band_center_" + b, 0);
            levels[b] = prefs.getInt("band_" + b, 0);
        }
        curveView.setData(centers, levels,
                prefs.getInt("band_min", -1500), prefs.getInt("band_max", 1500));
        curveView.setSelected(tunerBand);
    }

    private void buildEqSliders() {
        eqContainer.removeAllViews();
        bandSeeks.clear();
        bandValueLabels.clear();
        boolean ready = prefs.getBoolean("meta_ready", false);
        int count = prefs.getInt("band_count", 0);
        if (!ready || count == 0) {
            eqHint.setVisibility(View.VISIBLE);
            return;
        }
        eqHint.setVisibility(View.GONE);
        int min = prefs.getInt("band_min", -1500);
        int max = prefs.getInt("band_max", 1500);
        int accent = getColor(R.color.accent);
        for (int b = 0; b < count; b++) {
            final int band = b;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView label = new TextView(this);
            label.setText(formatHz(prefs.getInt("band_center_" + b, 0)));
            label.setTextSize(13);
            label.setTextColor(getColor(R.color.text_primary));
            label.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(label);
            TextView value = new TextView(this);
            value.setText(formatDb(prefs.getInt("band_" + b, 0)));
            value.setTextSize(13);
            value.setTextColor(accent);
            row.addView(value);
            bandValueLabels.add(value);
            eqContainer.addView(row);

            SeekBar sb = new SeekBar(this);
            sb.setMax(max - min);
            sb.setProgress(prefs.getInt("band_" + b, 0) - min);
            sb.setProgressTintList(ColorStateList.valueOf(accent));
            sb.setProgressBackgroundTintList(
                    ColorStateList.valueOf(0xFF3A3A4C));
            sb.setThumbTintList(ColorStateList.valueOf(accent));
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int prog, boolean user) {
                    if (!user) return;
                    tunerBand = band;
                    setBandLevel(band, prog + min);
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
            bandSeeks.add(sb);
            eqContainer.addView(sb);
        }
    }

    /** Single write path for a band change: prefs (live + preset), all UIs. */
    private void setBandLevel(int band, int millibels) {
        int min = prefs.getInt("band_min", -1500);
        int max = prefs.getInt("band_max", 1500);
        if (millibels < min) millibels = min;
        if (millibels > max) millibels = max;
        prefs.edit()
                .putInt("band_" + band, millibels)
                .putInt("p_" + activePreset() + "_band_" + band, millibels)
                .apply();
        curveView.setLevel(band, millibels);
        curveView.setSelected(band);
        if (band < bandSeeks.size()) {
            bandSeeks.get(band).setProgress(millibels - min);
        }
        if (band < bandValueLabels.size()) {
            bandValueLabels.get(band).setText(formatDb(millibels));
        }
        if (band == tunerBand) updateTuner();
        applyIfOn();
    }

    private void stepBand(int deltaMb) {
        int cur = prefs.getInt("band_" + tunerBand, 0);
        setBandLevel(tunerBand, cur + deltaMb);
    }

    private void updateTuner() {
        int count = prefs.getInt("band_count", 0);
        if (count == 0) {
            tunerLabel.setText("No bands yet - switch TM Sounds ON once");
            return;
        }
        if (tunerBand >= count) tunerBand = 0;
        int hz = prefs.getInt("band_center_" + tunerBand, 0);
        int level = prefs.getInt("band_" + tunerBand, 0);
        tunerLabel.setText(String.format(Locale.US,
                "Band %d of %d · %s · %s",
                tunerBand + 1, count, formatHz(hz), formatDb(level)));
        int min = prefs.getInt("band_min", -1500);
        int max = prefs.getInt("band_max", 1500);
        uiSync = true;
        tunerSeek.setMax(max - min);
        tunerSeek.setProgress(level - min);
        uiSync = false;
        curveView.setSelected(tunerBand);
    }

    // ---------- about / helpers ----------

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("About TM Sounds")
                .setMessage("Version 0.1\n\n"
                        + "Developed by MD. Muklash Mia\n\n"
                        + "Telegram: @muklash_dev\n"
                        + "GitHub: github.com/Muklash-Dev\n\n"
                        + "Open-source app (MIT License).\n"
                        + "No root, no ads, no tracking.")
                .setNeutralButton("Telegram", (d, w) ->
                        openUrl("https://t.me/muklash_dev"))
                .setPositiveButton("GitHub", (d, w) ->
                        openUrl("https://github.com/Muklash-Dev"))
                .setNegativeButton("Close", null)
                .show();
    }

    private SeekBar.OnSeekBarChangeListener simpleListener(Runnable onChange) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user && !uiSync) onChange.run();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        };
    }

    private void updateBoostLabel() {
        boostValue.setText(String.format(Locale.US, "+%.1f dB",
                prefs.getInt("boost_mb", 0) / 100.0));
    }

    private void applyIfOn() {
        if (prefs.getBoolean("enabled", false)) AudioService.apply(this);
    }

    private String formatHz(int hz) {
        if (hz <= 0) return "Band";
        if (hz < 1000) return hz + " Hz";
        return String.format(Locale.US, "%.1f kHz", hz / 1000.0);
    }

    private String formatDb(int millibels) {
        return String.format(Locale.US, "%+.1f dB", millibels / 100.0);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, url, Toast.LENGTH_LONG).show();
        }
    }

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
    }
}
