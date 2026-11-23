# Spec: Onda Music (v1.0 Flagship Core)

## 1. Goal & Non-Goals
- **Goal:** Build **Onda Music** (`com.onda.music`) from scratch in Pure Kotlin + Jetpack Compose (`minSdk = 29`, `targetSdk = 35`) to surpass Mochy Music in UI craft and 120fps motion fidelity: Smoked Glass + AGSL Dynamic Mesh UI, gesture-driven morphing, full Settings with search, YouTube Music + Monochrome FLAC + Local MediaStore + Link Importer, and direct **Mochy/Momi Music (`momi-transfer` v1) + Onda backup ZIP** import/export.
- **Non-Goals (v1.1+):** Android TV, Android Auto, Discord RPC, Last.fm scrobbling, Shazam audio recognition, Apple Music Widevine DRM, third-party ZIP/JS plugin bridge, raw USB C++ drivers, and local proot Android SDK installation.

## 2. File Impact Map (Modular Clean Architecture)
| Module / File | Action | Single Responsibility |
|---|---|---|
| `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml` | create | Multi-module Gradle catalog (Kotlin 2.x, Compose, Media3, Hilt, Room, Ktor) |
| `.github/workflows/ci.yml` | create | CI gate: unit tests + arm64/universal APK build + artifact upload |
| `core/model/...` | create | Immutable domain models (`Track`, `SourceModels`, `PlayerStyle`, `EqConfig`, `LyricsModels`, `TransferModels`, `PlaybackModels`) |
| `core/design/.../OndaTheme.kt`, `GlassKit.kt`, `AgslMesh.kt`, `AppMotion.kt` | create | Smoked glass tokens (`#050609`), `RenderNode`/progressive blur, AGSL mesh, `Press3D`, `Odometer`, `DockMetrics` |
| `core/database/.../OndaDatabase.kt`, `Dao.kt` | create | Room 0ms library (`songs`, `artists`, `albums`, `playlists`, `play_events`, `search_history`, `lyrics`) |
| `core/sources/.../YtMusicClient.kt`, `MonochromeClient.kt`, `LinkImporter.kt`, `LocalScanner.kt` | create | InnerTube Ktor client + NewPipe fallback, Monochrome FLAC, Spotify/Apple/Deezer/YT link match, MediaStore |
| `core/lyrics/.../LyricsRepository.kt` | create | Synced LRC + word-level timing (LRCLib -> YT Music fallback) + translation/romanization cache |
| `core/transfer/.../MomiTransferImporter.kt`, `OndaBackupManager.kt` | create | Parse `momi-transfer` v1 ZIP (`manifest.json`, `library.db`, `settings.json`, `audio_lab.json`, avatar) & Onda ZIP backup |
| `core/playback/.../OndaMediaService.kt`, `StreamResolver.kt`, `AudioEffects.kt` | create | Media3 `MediaLibraryService`, gapless/crossfade, 10-band parametric EQ, normalize, sleep timer |
| `feature/home/.../HomeScreen.kt` | create | Dynamic Hero (`Press3D` tilt), Continue/Recent/QuickPicks/Charts shelves, `MorphingCornerClip` shared transitions |
| `feature/search/.../SearchScreen.kt` | create | Instant search across YT Music, Monochrome FLAC, Local + Link Importer sheet |
| `feature/library/.../LibraryScreen.kt` | create | Local folders/songs, Liked, Playlists, Downloads, Play History & Stats |
| `feature/player/.../UnifiedDock.kt`, `FullPlayerSheet.kt`, `SongCarousel.kt`, `LyricsPane.kt` | create | 3-tab glass dock, lockstep swipe carousel, 1-1 drag morph, wavy rubber-band seekbar, `LikeBurst`, staggered word lyrics + interlude dots |
| `feature/settings/.../SettingsScreen.kt`, `TransferSheet.kt`, `EqScreen.kt` | create | Searchable 5-group Settings (Audio/EQ, Motion/Lyrics, Appearance/Lang, Sources/Import, Storage/Transfer) + Momi ZIP import UI |
| `app/.../MainActivity.kt`, `OndaApp.kt`, `res/mipmap-*` | create | Hilt entry, `SharedTransitionLayout` root, 3-tab nav host, Adaptive Icon (`onda-icon-1-glass-wave`) |

## 3. Data Contracts & State
- **Track & Stream:**
  - `Track(id: String, title: String, artist: String, album: String?, durationMs: Long, artworkUrl: String?, source: SourceKind, localUri: String?, ytId: String?, monochromeId: String?, liked: Boolean, inLibrary: Boolean, totalPlayTimeMs: Long, lyricsOffsetMs: Long)`
  - `enum class SourceKind { YOUTUBE, MONOCHROME, LOCAL }`
  - `StreamOption(url: String, mimeType: String, bitrateKbps: Int, codec: String, qualityLabel: String, expiresAtMs: Long?)`
- **Motion & Morph State (Surpassing Mochy):**
  - `PlayerStyle` (10 native built-in modes): `FLAGSHIP`, `CLASSIC`, `POSTER`, `TURNTABLE`, `VIDEO`, `IPOD`, `TIMELINE`, `LYRICS`, `BEDSIDE`, `BEDSIDE_TILES` (sharing one `120fps` state & `SongCarousel` backbone; external ZIP `MODULE` skin deferred to v1.1+).
  - `PlayerMorphState`: continuous `fraction: Float` (`0f` Docked -> `1f` FullPlayer) via `AnchoredDraggableState` + `spring(DampingRatioNoBouncy, StiffnessMediumLow)`.
  - `HomeRecede`: root content scales to `0.92f` + `0.45f` alpha behind opening album/player shared transitions (`MorphingCornerClip`).
  - `LyricsPane`: `LyricsStagger` (`55ms` per-row critically damped lag), word-level fill sweep, `LyricsInterludeDots` 3-dot spring bob.
  - `DockMetrics`: bottom content padding = `navBarHeight + miniPlayerHeight + navigationBarsPadding + 16.dp` (zero text clipping).
- **Mochy/Momi Transfer Contract (`momi-transfer` v1 verified against `momi-export-2026-11-23.zip`):**
  - Reads `manifest.json` (`format == "momi-transfer"`, `version == 1`), opens `database/library.db` read-only via SQLite cursor to import `song` + `artist` + `song_artist_map` + `album` + `playlist` + `playlist_song_map` + `event` (play history) + `search_history` + `lyrics`.
  - Reads `settings/settings.json` (`username`, `profilePicUri` -> extracts `files/profile_pic_*.jpg`), `settings/prefs/audio_lab.json` (10-band `eq.nodes`, `normalize`, `playback`), `settings/prefs/source_modules.json`.
  - Merge policy: `IdempotentUpsert` (`ON CONFLICT` merge play counts, liked status, playlists without wiping existing Onda data unless user picks Replace).
- **Playback Resolution Chain:**
  - `LocalFile -> DownloadedCache -> MonochromeFlac (if enabled/matched) -> YtInnerTube -> NewPipeFallback -> Error`.

## 4. Edge Cases & Constraints
- **120fps Render Budget:** Apply `RenderEffect`/progressive blur + AGSL mesh on static/dock/FullPlayer layers (API 31+/33+); fallback to smooth alpha-gradient scrim on API 29-30 and inside scrolling `LazyColumn` cards.
- **Momi Transfer Safety:** Never swap raw SQLite files directly; stream-validate ZIP entries against zip-slip (`canonicalPath` check) and import via transactional Room batching with progress flow (`0f..1f`). Missing local `localUri` tracks import as metadata with `needsRelink = true`.
- **YouTube 403 / Cipher Break:** `StreamResolver` clears stale URL cache on 403/410 and rotates InnerTube client / NewPipe extractor once before non-blocking snackbar + queue advance.
- **HyperOS / MIUI Background Kill:** `OndaMediaService` holds `FOREGROUND_SERVICE_MEDIA_PLAYBACK` + `WakeLock`/`WifiLock` and checkpoints queue/position every 5s.
- **Adaptive Icon & UI Hygiene:** `onda-icon-1-glass-wave` scaled to inner `64%` foreground safe zone on `#050609`; tested at `375dp` narrow viewport, `>= 48.dp` tap targets, WCAG AA contrast, zero emoji in UI.

## 5. Verification Gate
- **Zero-Comment Invariant:** Every Kotlin and Gradle file under `app/`, `core/`, and `feature/` must contain zero comments (`//`, `/*`, `*` block comments), verified locally before push via `! grep -rnE '^\s*(//|/\*|\*)' app/src core feature`.
- **Local Pre-Push Check:** Validate manifests, module graph, `wc -l`, and static imports before committing.
- **CI Build & Unit Test Gate (GitHub Actions `.github/workflows/ci.yml`):**
  1. `./gradlew testDebugUnitTest --no-daemon` -> PASS (must cover `MomiTransferImporter` parsing `momi-transfer` v1 fixture, `TrackMatcher`, `LinkImporter`, `LyricsParser`, `StreamResolver`).
  2. `./gradlew assembleArm64Debug --no-daemon` -> PASS and uploads APK artifact.
  - Monitor via `gh run list` / `gh run watch` until `conclusion == success`.
