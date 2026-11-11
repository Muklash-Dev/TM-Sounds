package com.muklashdev.tmsounds;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.IBinder;

/**
 * Foreground service that owns the FxEngine, keeps the effects applied to
 * the global output session, and (optionally) remembers a profile per
 * audio device: headphones, each Bluetooth device, and the speaker each
 * get their own preset + volume boost, restored automatically.
 */
public class AudioService extends Service {

    public static final String ACTION_APPLY = "com.muklashdev.tmsounds.APPLY";
    public static final String ACTION_STOP = "com.muklashdev.tmsounds.STOP";
    private static final String CHANNEL_ID = "tm_sounds";
    private static final int NOTIF_ID = 43;

    private FxEngine engine;
    private AudioManager audioManager;
    private String lastDeviceKey;

    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
            evaluateDevice();
        }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
            evaluateDevice();
        }
    };

    public static void start(Context ctx) {
        ctx.startForegroundService(new Intent(ctx, AudioService.class));
    }

    public static void apply(Context ctx) {
        Intent i = new Intent(ctx, AudioService.class);
        i.setAction(ACTION_APPLY);
        ctx.startForegroundService(i);
    }

    public static void stop(Context ctx) {
        Intent i = new Intent(ctx, AudioService.class);
        i.setAction(ACTION_STOP);
        try { ctx.startService(i); } catch (Exception ignored) {}
        ctx.stopService(new Intent(ctx, AudioService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        engine = new FxEngine();
        engine.publishMetadata(this);
        engine.applyFromPrefs(this);
        startForeground(NOTIF_ID, buildNotification());
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.registerAudioDeviceCallback(deviceCallback, null);
            evaluateDevice();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            if (engine != null) engine.setEnabled(false);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (engine == null) engine = new FxEngine();
        engine.applyFromPrefs(this);
        return START_STICKY;
    }

    // ---------- per-device memory ----------

    /** "bt_MyBuds" / "wired" / "usb_X" / "speaker" for the main output. */
    private String currentDeviceKey() {
        if (audioManager == null) return "speaker";
        AudioDeviceInfo[] devices =
                audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        AudioDeviceInfo bt = null, usb = null, wired = null;
        for (AudioDeviceInfo d : devices) {
            switch (d.getType()) {
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: bt = d; break;
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                case AudioDeviceInfo.TYPE_USB_DEVICE: usb = d; break;
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                case AudioDeviceInfo.TYPE_WIRED_HEADSET: wired = d; break;
                default: break;
            }
        }
        if (bt != null) return "bt_" + sanitize(bt.getProductName());
        if (usb != null) return "usb_" + sanitize(usb.getProductName());
        if (wired != null) return "wired";
        return "speaker";
    }

    private String sanitize(CharSequence name) {
        if (name == null) return "device";
        String s = name.toString().replaceAll("[^a-zA-Z0-9]+", "_");
        return s.length() == 0 ? "device" : s;
    }

    private void evaluateDevice() {
        SharedPreferences p = FxEngine.prefs(this);
        String key = currentDeviceKey();
        p.edit().putString("current_device_key", key).apply();
        if (!p.getBoolean("per_device_memory", false)) {
            lastDeviceKey = key;
            return;
        }
        if (key.equals(lastDeviceKey)) return;

        // remember the outgoing device's choices
        if (lastDeviceKey != null) {
            p.edit()
                    .putString("dev_preset_" + lastDeviceKey,
                            p.getString("preset", "Flat"))
                    .putInt("dev_boost_" + lastDeviceKey,
                            p.getInt("boost_mb", 0))
                    .apply();
        }
        lastDeviceKey = key;

        // restore the incoming device's choices, if it has any
        String saved = p.getString("dev_preset_" + key, null);
        if (saved != null) {
            FxEngine.seedPreset(this, saved, false);
            FxEngine.loadPresetToLive(this, saved);
            SharedPreferences.Editor e = p.edit();
            e.putString("preset", saved);
            if (p.contains("dev_boost_" + key)) {
                e.putInt("boost_mb", p.getInt("dev_boost_" + key, 0));
            }
            e.apply();
            if (engine != null) engine.applyFromPrefs(this);
        }
    }

    // ---------- notification / lifecycle ----------

    private Notification buildNotification() {
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "TM Sounds", NotificationManager.IMPORTANCE_LOW);
        if (nm != null) nm.createNotificationChannel(ch);

        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("TM Sounds")
                .setContentText("Sound enhancement is on - by MD. Muklash Mia")
                .setSmallIcon(R.drawable.ic_stat_sound)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        if (audioManager != null) {
            audioManager.unregisterAudioDeviceCallback(deviceCallback);
        }
        if (engine != null) {
            engine.setEnabled(false);
            engine.release();
            engine = null;
        }
        super.onDestroy();
    }
}
