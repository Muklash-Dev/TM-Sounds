# TM Sounds

A **no-root** sound enhancer for Android with a premium dark interface,
built only on Android's own public audio APIs (`android.media.audiofx`).
One APK works on **Android 8.0 (API 26) and above** — phones, custom ROMs,
any manufacturer.

Developed by **MD. Muklash Mia**
- Telegram: [@muklash_dev](https://t.me/muklash_dev)
- GitHub: [Muklash-Dev](https://github.com/Muklash-Dev)

## Features

- **Profiles** — Flat, Music, Movie, Voice, Bass and Custom in a swipeable
  carousel. Every profile ships with factory defaults **and remembers your
  own changes**; a reset button restores the defaults.
- **Equalizer, two ways** — an interactive frequency-response **curve**
  (drag the points) and classic **sliders**, plus a **Band Tuner** with
  0.1 dB fine steps. Band count and ranges are read from your device.
- **Audio tuning** — Bass, Mid and Treble enhancers with their own
  switches and levels, plus a Surround (Virtualizer) control.
- **Volume Boost** — real output gain via `LoudnessEnhancer` (0 to +12 dB).
- **Volume Leveler** — a gentle compressor + limiter via
  `DynamicsProcessing` (Android 9+; shown as unavailable where a device
  does not offer it).
- **Per-device audio memory** — remembers which profile and boost you use
  for each output: wired headphones, every Bluetooth device, USB and the
  speaker, and restores them automatically when you reconnect.
- Lightweight foreground service, optional auto-start after reboot.
- **No root, no ads, no tracking, no internet permission.**

## Honest limitations

- The equalizer can only control as many bands as your device's audio
  hardware offers (commonly 5). The curve draws a smooth line between
  those real bands.
- A few manufacturers restrict *global* audio effects; on those devices
  the enhancement may apply to fewer apps. That is an OEM restriction.
- **Call echo cannot be fixed by any sound app** — it is a microphone /
  telephony routing issue. This app does not claim to fix it.
- TM Sounds is an original app by MD. Muklash Mia. It is not affiliated
  with Dolby; it uses only Android's built-in audio effects.

## Build

Option A — Android Studio: open the project and run the `app` configuration.

Option B — command line (JDK 17 + Android SDK platform 34 / build-tools 34):

```bash
export ANDROID_SDK_ROOT=$HOME/Android/Sdk
./build.sh                      # unsigned APK
KEYSTORE=my.keystore KS_ALIAS=myalias KS_PASS=... KEY_PASS=... ./build.sh
```

## License

MIT — see [LICENSE](LICENSE). Free to use, modify and share with attribution.
