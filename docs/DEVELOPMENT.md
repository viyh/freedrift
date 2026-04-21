# Development

Everything runs inside containers. You don't need a JDK, Android SDK, or Gradle on your host — only a Docker-compatible runtime with the `docker` CLI on `$PATH`.

## Prerequisites

- A Docker-compatible runtime (Colima, Rancher Desktop, OrbStack, Podman with the docker shim, Docker Engine, etc.)
- `docker` and `docker compose` available on your shell
- An Android phone (for installing), paired via Wireless Debugging (see below)

R8 minification during `./freedrift release` needs roughly 3 GB of JVM heap inside the container. Colima and Rancher Desktop default to 2–4 GB of VM memory; bump to at least 6 GB if release builds get OOM-killed (e.g. `colima stop && colima start --memory 6`).

## The wrapper

Every workflow goes through `./freedrift`. Run it with no arguments to see the full list. The common ones:

| Command | What it does |
|---|---|
| `./freedrift build` | Debug APK → `app/build/outputs/apk/debug/app-debug.apk` |
| `./freedrift release` | Release APK (R8-minified, signed with the shared debug key) → `app/build/outputs/apk/release/app-release.apk` |
| `./freedrift install` / `release-install` | `adb install -r` the corresponding APK |
| `./freedrift run` / `release-run` | build + install + launch in one shot |
| `./freedrift start` | Launch the already-installed app |
| `./freedrift uninstall` | Remove the app from the device |
| `./freedrift clean` | `gradle clean` |
| `./freedrift shell` | Bash shell inside the tools container |
| `./freedrift down` | Stop the long-running tools container |
| `./freedrift ffmpeg <args...>` | Run ffmpeg inside a container with `$HOME` mounted |
| `./freedrift convert <file> [name]` | Re-encode an audio file as OGG Vorbis q5 and drop it into `assets/sounds/` |
| `./freedrift clean-assets` | Strip cover art + metadata from existing assets |

## Pairing a phone (one-time, Wireless Debugging)

1. On the phone: `Settings → System → Developer options → Wireless debugging`, turn it on.
2. Tap **Pair device with pairing code**. The phone shows an `IP:PORT` and a 6-digit code.
3. On your machine:
   ```
   ./freedrift pair 192.168.x.x:PAIR_PORT 123456
   ```
4. Back on the phone's Wireless Debugging screen, note the *connection* `IP:PORT` (a different port than the pairing one):
   ```
   ./freedrift connect 192.168.x.x:CONN_PORT
   ./freedrift devices   # confirm the phone shows up
   ```

Pairing persists across reboots. The connection port usually changes after the phone reboots, so on subsequent sessions you'll just re-run `./freedrift connect …`.

## Daily loop

```
./freedrift run      # rebuild, install, launch
./freedrift logcat   # stream filtered logs (our tag + Media3 + foreground service)
```

Variants of `logcat`:

| Command | Filter |
|---|---|
| `logcat` | Our `FreeDrift` tag + Media3 session/notification + crashes |
| `logcat-all` | Everything. Noisy — keep a prompt open in a second terminal |
| `logcat-pid` | Everything from the app process only |

## Adding sounds

Drop audio files into `app/src/main/assets/sounds/`. Supported formats: `.ogg`, `.opus`, `.flac`, `.mp3`, `.wav`. The app auto-discovers them on launch. File names get prettified into display names (underscores → spaces, parens capitalised, digits separated from letters).

OGG Vorbis is strongly preferred for continuous-loop sounds — it's the one format where ExoPlayer can seamlessly loop without a perceptible click. To re-encode something you already have:

```
./freedrift convert rain.mp3                      # becomes Rain.ogg
./freedrift convert rain.mp3 rain_(heavy).ogg     # rename on the way in
```

Or the raw ffmpeg path:

```
./freedrift ffmpeg -i rain.mp3 -c:a libvorbis -q:a 5 app/src/main/assets/sounds/rain.ogg
```

Quality 5 (~160 kbps VBR) is the safe default. Drop to q3 (~112 kbps) if you want smaller files and your source is ambient/noise-heavy — indistinguishable for most sleep content.

### Starter scenes

The initial set of scenes is defined in [`app/src/main/java/io/github/viyh/freedrift/audio/StarterScenes.kt`](../app/src/main/java/io/github/viyh/freedrift/audio/StarterScenes.kt). Each `Spec` references layer sounds by their normalised compare-key (lowercase alphanumerics only), so `"rain_(loud, trees).ogg"` matches `"rainloudtrees"`.

Bumping `STARTERS_VERSION` causes the seeder to upsert any starter scenes whose name already exists, pushing out tuning changes to existing installs without clobbering user-created scenes.

## Release build

`./freedrift release` builds an R8-minified APK and signs it with the shared Android SDK debug keystore. This keystore is the public default that ships with every Android SDK install — not a distribution secret — which means:

- The same APK can replace a debug install with no uninstall needed (same signing key).
- Play Protect will still flag the app as "not scanned" on first install; that's expected for any sideloaded APK.
- Do **not** use this key for any public distribution on Play or similar. If you want to distribute properly, generate a real keystore and swap the `debugShared` `signingConfigs` entry in `app/build.gradle.kts`.

ProGuard rules live in [`app/proguard-rules.pro`](../app/proguard-rules.pro). If R8 strips something at runtime (crash usually mentions a missing class or `NoSuchMethodError`), add a `-keep` rule for the affected package and rebuild.

## Project layout

```
Dockerfile, docker-compose.yml      build environment
freedrift                           dev workflow wrapper
settings.gradle.kts, build.gradle.kts
app/
  build.gradle.kts                  module config, signing, minification
  proguard-rules.pro                R8 keep rules
  src/main/
    AndroidManifest.xml             permissions, service registration
    assets/sounds/                  bundled sounds (*.ogg et al)
    assets/sound_defaults.json      per-sound default playback settings
    java/io/github/viyh/freedrift/
      FreeDriftApp.kt               Application + notification channel
      MainActivity.kt               Activity, service binding, permissions
      audio/
        PlaybackService.kt          ExoPlayer pool, fades, sleep timer,
                                      audio focus, scene layer mixing
        Scene.kt                    Scene data + repository
        Playlist.kt                 Playlist data + repository
        SoundSource.kt              Bundled + user-file sources
        SoundSettings.kt            Per-sound Continuous/Intermittent config
        SoundDefaults.kt            Asset-shipped sound_defaults.json loader
        StarterScenes.kt            First-run seeded scene presets
        AppSettings.kt              Global user settings (fade durations,
                                      haptics, etc.) in SharedPreferences
      ui/
        HomeScreen.kt               Compose: tabs, mini-player, Now Playing
        SceneEditor.kt              Scene edit screen + shared slider bank
        PlaylistEditor.kt           Playlist edit screen
        About.kt                    About card + sound credits dialog
        theme/Theme.kt              Pure-black Material 3 theme
    res/                            icons, strings, themes
docs/
  DEVELOPMENT.md                    this file
  SCREENSHOTS.md                    annotated screenshot tour
  screenshots/                      raw PNGs (EXIF stripped)
sounds-originals/                   pre-q3 source OGGs (gitignored)
```

## Package / app id

`io.github.viyh.freedrift`. If you fork and want a different package name, search-and-replace the string in:

- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts` (both `namespace` and `applicationId`)
- Every `.kt` file's `package` declaration
- The `freedrift` script's `PKG` variable

## Gotchas

- **Colima memory ceiling.** 4 GB isn't enough for R8. `colima stop && colima start --memory 6` is the one-liner.
- **Colima `system_profiler` hangs.** Unrelated to this project — macOS-side Activation Lock check issue; try toggling Find My off and back on, or log out/in of iCloud.
- **Bind-mounted build I/O.** Gradle up-to-date checks hash every input file on every build, and hashing through bind mounts (gRPC-FUSE / VirtioFS) is noticeably slower than native. Switch the runtime to VirtioFS in settings if your build feels sluggish.
- **Debug keystore reuse for release.** Convenient for personal sideload (same signing key = `install -r` works between debug/release without uninstall) but not a real signing setup. Do not publish this APK.
