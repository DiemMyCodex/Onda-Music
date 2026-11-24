# Onda

[![Onda CI](https://github.com/DiemMyCodex/Onda-Music/actions/workflows/ci.yml/badge.svg)](https://github.com/DiemMyCodex/Onda-Music/actions/workflows/ci.yml)

Native Android music player built with Kotlin, Jetpack Compose, and Media3 — featuring 120Hz GPU-accelerated motion, 10-band parametric EQ with per-output device profiles, synced lyrics, and a monochrome OLED interface.

Target stack: Android 10+ (`minSdk = 29`, `targetSdk = 35`, `compileSdk = 37`), JDK 21, Multi-Module Gradle.

## Features

- **Audio Lab & Per-Output EQ Memory:** 10-band parametric equalizer (`31 Hz` to `16 kHz`) with preamp gain, loudness normalization, gapless playback, crossfade (`0-12s`), and automatic per-device profile switching across Speaker, Wired Headset, USB Audio/DAC, and Bluetooth routes.
- **Now Playing Studio:** 10 native player styles (`Poster`, `Flagship`, `Classic`, `Turntable`, `Video`, `iPod`, `Timeline`, `Lyrics`, `Bedside`, `Bedside Tiles`), curved-arc and wavy scrubbers, custom backdrop modes, and quick-action studio configuration.
- **Synced Lyrics & Acoustic Sleep Timer:** Word-level and line-synced lyrics with interactive tap-to-seek, adjustable offset (`+/-15s`), lyric card sharing, and a sleep timer (`15m`, `30m`, `45m`, `60m`, or `End of Track`) with a smooth 15-second volume fade-out before pausing.
- **Playback & System Controls:** Skip silence, auto-resume on headset/Bluetooth connection, background playback persistence across task dismissal, and optional keep-screen-on mode.
- **Multi-Source Library & Backup Transfer:** Unified playback pipeline across Local MediaStore, YouTube Music, Monochrome FLAC streams, and link imports, plus portable ZIP backup export/restore (`Onda` and `momi-transfer v1`).

## Download

Prebuilt signed `arm64-v8a` APKs are published on [GitHub Releases](https://github.com/DiemMyCodex/Onda-Music/releases/latest).

## Quick Start

Requires JDK 21 and Android SDK (`compileSdk = 37`, `minSdk = 29`).

```bash
git clone https://github.com/DiemMyCodex/Onda-Music.git
cd Onda-Music
./gradlew testDebugUnitTest :app:assembleArm64Debug --no-daemon
```

To build a release APK locally (optional keystore environment variables):

```bash
./gradlew :app:assembleArm64Release --no-daemon
```

## Architecture

```text
Onda-Music/
  app/                # Application entry, adaptive launcher icons, root shell & navigation
  core/
    model/            # Immutable domain contracts (tracks, EQ, output profiles, lyrics, playback)
    design/           # Monochrome OLED tokens, SmoothCornerShape, launch choreography, blur/motion primitives
    database/         # Room database, DAOs, and DataStore preferences
    sources/          # MediaStore scanner, YouTube Music, Monochrome FLAC, and link importer
    lyrics/           # Synced lyrics fetching, parsing, and offset/cache persistence
    transfer/         # Archive backup export/restore and transfer importers
    playback/         # Media3 service, ExoPlayer DSP pipeline, output monitor, sleep fade curve
  feature/
    home/             # Hero carousel, editorial shelves, quick-jump grid, detail sheet
    search/           # Instant multi-source search and link import flow
    library/          # Overview hub, playlists, liked songs, local folders, downloads, history
    player/           # Unified dock, full-player morph, 10 player styles, queue, share cards
    settings/         # Searchable 5-group settings hub, 10-band EQ sheet, backup UI
```

## Configuration & CI

Continuous integration is defined in `.github/workflows/ci.yml` and runs on pushes and pull requests to `main`.

| Name | Kind | Required | Purpose |
| --- | --- | --- | --- |
| `ONDA_KEYSTORE_BASE64` | GitHub Secret | Release signing | Base64-encoded JKS release keystore |
| `ONDA_KEYSTORE_PASSWORD` | GitHub Secret | Release signing | Keystore password passed to Gradle |
| `ONDA_KEY_ALIAS` | GitHub Secret | Release signing | Signing key alias inside keystore |
| `ONDA_KEY_PASSWORD` | GitHub Secret | Release signing | Key password inside keystore |

## Commands

| Command | Purpose |
| --- | --- |
| `./gradlew testDebugUnitTest --no-daemon` | Run pure-JVM unit tests across all modules |
| `./gradlew :app:assembleArm64Debug --no-daemon` | Build debug APK (`arm64-v8a`) |
| `./gradlew :app:assembleArm64Release --no-daemon` | Build minified release APK (`arm64-v8a`) |
