# FreeDrift

A minimal Android sleep-sounds app. Play a loopable sound for hours, fade in/out, sleep timer. Nothing installed on your Mac except Docker.

## One-time setup

1. **Docker Desktop** installed and running.
2. Build the Docker image + first APK:
   ```
   ./freedrift build
   ```
   First run downloads JDK, Android SDK, Gradle, etc. and takes a while. Subsequent builds are fast.

3. **Add sounds.** Drop `.ogg` files into `app/src/main/assets/sounds/`. The app auto-discovers them on launch. `.opus`, `.flac`, `.mp3`, `.wav` also work, but `.ogg` Vorbis is recommended for clean seamless looping.
   ```
   # convert an mp3
   ffmpeg -i rain.mp3 -c:a libvorbis -q:a 5 app/src/main/assets/sounds/rain.ogg
   ```

## Pair your phone (one-time, Wireless Debugging)

1. Phone: `Settings → System → Developer options → Wireless debugging` → **On**.
2. Tap "Pair device with pairing code". You'll see `IP:PORT` and a 6-digit code.
3. On your Mac:
   ```
   ./freedrift pair 192.168.x.x:PAIR_PORT 123456
   ```
4. Back on the Wireless Debugging screen, note the *connection* `IP:PORT` (different from the pairing port):
   ```
   ./freedrift connect 192.168.x.x:CONN_PORT
   ./freedrift devices   # confirm your phone shows up
   ```

Pairing persists. On subsequent dev sessions only `./freedrift connect …` is needed (the port often changes after reboot).

## Daily dev loop

```
./freedrift run       # build + install + launch
./freedrift logcat    # stream filtered logs
```

Other commands: `./freedrift build`, `./freedrift install`, `./freedrift start`, `./freedrift uninstall`, `./freedrift clean`, `./freedrift shell`, `./freedrift down`.

## What's implemented (v0.5)

- Foreground playback service (survives screen lock, hours-long playback)
- Lockscreen + notification media controls via Media3 MediaSession; play/pause from lockscreen respects fade
- Bundled sounds from `assets/sounds/` + user-added via system file picker
- Bottom-tab UI: **Sounds / Mixes / Playlists / Settings**, with a persistent mini-player above the tabs
- **Expanded now-playing view** — tap the mini-player for a full view with larger controls, track progress (or live mix sliders), app-volume slider, and sleep-timer access
- **Resume last session** — mini-player keeps the last thing you played visible after stopping; tap play to resume
- **Sleep-timer presets** (15/30/60/90 min) plus the full slider in the timer dialog
- Immediate pause/stop on user button press; fade-out reserved for sleep-timer expiry
- Fade-in on play/resume
- Track-position + sleep-timer progress indicators
- **Playlists** with per-track duration and crossfade. **Entries can be sounds or mixes.** Sound→sound uses a true shared-player crossfade; anything involving a mix uses a parallel fade-out + fade-in with separate player pools.
- **Mixes** — up to 8 simultaneous looping layers, each with its own vertical volume slider. Saved as named presets. Tweak levels live while playing. Service-managed audio focus so all layers coexist (no focus stealing within the app).
- **App-level volume** — master multiplier separate from system volume, adjustable in Settings and the expanded player
- **Duck-during-notifications** toggle (Settings) — default off (pause); on ducks to 40%
- Configurable fade-in, crossfade, and sleep-timer fade-out durations
- Notification + notification-permission prompt
- Battery optimization exemption (Settings)
- Pure-black dark theme

## Not yet (roadmap)

- Drag-to-reorder entries in the playlist / mix editors (currently up/down buttons)
- Screen-dim-while-playing

## Project layout

```
Dockerfile, docker-compose.yml      build environment
freedrift                           dev workflow wrapper
settings.gradle.kts, build.gradle.kts
app/
  build.gradle.kts
  src/main/
    AndroidManifest.xml
    assets/sounds/                  drop your .ogg files here
    java/io/github/viyh/freedrift/
      FreeDriftApp.kt                 Application, notification channel
      MainActivity.kt               Activity, service binding, permissions
      audio/
        PlaybackService.kt          ExoPlayer, fade, sleep timer
        SoundSource.kt              bundled + user-file sources
      ui/
        HomeScreen.kt               Compose UI
        theme/Theme.kt              black theme
    res/                            icons, strings, themes
```

## Package name

`io.github.viyh.freedrift`. To change later: search-and-replace the string across `AndroidManifest.xml`, `app/build.gradle.kts`, every `.kt` file's `package` line, and the `freedrift` script's `PKG` variable.

## License

FreeDrift is free software. Copyright (C) 2026 Joe Richards. Licensed under the GNU General Public License, version 3 or later — see [LICENSE](LICENSE) for the full text.

Bundled sounds are under their respective Creative Commons licenses; see the in-app **Settings → Sound credits** for per-clip attribution.
