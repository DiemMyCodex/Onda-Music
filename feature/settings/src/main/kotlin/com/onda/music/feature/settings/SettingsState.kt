package com.onda.music.feature.settings

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.onda.music.core.database.OndaUserSettings
import com.onda.music.core.model.AudioOutputDevice
import com.onda.music.core.model.LinkEntityKind
import com.onda.music.core.model.LinkProvider
import com.onda.music.core.model.MergePolicy
import com.onda.music.core.model.TransferManifest
import com.onda.music.core.model.TransferProgress
import com.onda.music.core.model.TransferSummary
import com.onda.music.core.transfer.MomiArchivePeek
import java.util.Locale
import kotlin.math.abs

enum class SettingsGroup(val id: String) {
    AUDIO_EQ("audio_eq"),
    NOW_PLAYING_MOTION("now_playing_motion"),
    APPEARANCE_LANGUAGE("appearance_language"),
    SOURCES_LINK_IMPORT("sources_link_import"),
    STORAGE_TRANSFER("storage_transfer")
}

enum class SettingsEntryId {
    EQUALIZER_EDITOR,
    SMART_OUTPUT_PROFILES,
    LOUDNESS_NORMALIZE,
    GAPLESS_PLAYBACK,
    CROSSFADE_DURATION,
    SLEEP_TIMER,
    SKIP_SILENCE,
    RESUME_ON_HEADSET,
    KEEP_PLAYING_ON_SWIPE,
    PLAYER_STYLE_PICKER,
    GLASS_BLUR,
    DYNAMIC_MESH,
    LYRICS_MOTION,
    LYRICS_FONT_SCALE,
    HAPTIC_FEEDBACK,
    KEEP_SCREEN_ON,
    PROFILE_CARD,
    LANGUAGE_TOGGLE,
    REPLAY_ONBOARDING,
    MONOCHROME_FLAC,
    YT_COOKIE,
    PROXY_URL,
    LINK_IMPORTER,
    TRANSFER_HUB,
    CACHE_CLEAR,
    CACHE_LIMIT,
    DOWNLOAD_FOLDER,
    RESET_DEFAULTS
}

@Immutable
data class SettingsSearchEntry(
    val id: SettingsEntryId,
    val group: SettingsGroup,
    val title: String,
    val subtitle: String,
    val keywords: List<String>
)

@Immutable
data class TransferUiState(
    val selectedMomiUri: Uri? = null,
    val selectedMomiFileName: String? = null,
    val momiPeek: MomiArchivePeek? = null,
    val momiHasAudioLab: Boolean = false,
    val momiMergePolicy: MergePolicy = MergePolicy.IDEMPOTENT_UPSERT,
    val selectedOndaUri: Uri? = null,
    val selectedOndaFileName: String? = null,
    val ondaManifest: TransferManifest? = null,
    val ondaMergePolicy: MergePolicy = MergePolicy.IDEMPOTENT_UPSERT,
    val isPeeking: Boolean = false,
    val isRunning: Boolean = false,
    val progress: TransferProgress? = null,
    val lastSummary: TransferSummary? = null,
    val errorMessage: String? = null
)

@Immutable
data class LinkImportUiState(
    val inputUrl: String = "",
    val detectedProvider: LinkProvider? = null,
    val detectedKind: LinkEntityKind? = null,
    val isImporting: Boolean = false,
    val lastImportedTitle: String? = null,
    val lastImportedCount: Int = 0,
    val errorMessage: String? = null
)

@Immutable
data class SettingsUiState(
    val settings: OndaUserSettings = OndaUserSettings(),
    val searchQuery: String = "",
    val cacheSizeBytes: Long = 0L,
    val downloadsSizeBytes: Long = 0L,
    val cachedStreamCount: Int = 0,
    val completedDownloadsCount: Int = 0,
    val isClearingCache: Boolean = false,
    val transfer: TransferUiState = TransferUiState(),
    val linkImport: LinkImportUiState = LinkImportUiState(),
    val statusMessage: String? = null,
    val activeOutputDevice: AudioOutputDevice = AudioOutputDevice(),
    val savedOutputProfileCount: Int = 0,
    val sleepTimerRemainingMs: Long? = null,
    val sleepTimerEndOfTrack: Boolean = false,
    val sleepTimerFading: Boolean = false
)

fun buildSettingsSearchEntries(strings: SettingsStrings): List<SettingsSearchEntry> = listOf(
    SettingsSearchEntry(
        id = SettingsEntryId.EQUALIZER_EDITOR,
        group = SettingsGroup.AUDIO_EQ,
        title = strings.eqTitle,
        subtitle = strings.eqSubtitle,
        keywords = listOf("eq", "equalizer", "10-band", "31hz", "16khz", "preamp", "preset", "audio", "am thanh")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.SMART_OUTPUT_PROFILES,
        group = SettingsGroup.AUDIO_EQ,
        title = strings.smartOutputTitle,
        subtitle = strings.smartOutputSubtitle,
        keywords = listOf("output", "profile", "bluetooth", "headset", "speaker", "dac", "auto", "thiet bi", "tai nghe")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.LOUDNESS_NORMALIZE,
        group = SettingsGroup.AUDIO_EQ,
        title = strings.normalizeTitle,
        subtitle = strings.normalizeSubtitle,
        keywords = listOf("normalize", "loudness", "replaygain", "volume", "can bang am luong")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.GAPLESS_PLAYBACK,
        group = SettingsGroup.AUDIO_EQ,
        title = strings.gaplessTitle,
        subtitle = strings.gaplessSubtitle,
        keywords = listOf("gapless", "transition", "continuous", "lien mach")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.CROSSFADE_DURATION,
        group = SettingsGroup.AUDIO_EQ,
        title = strings.crossfadeTitle,
        subtitle = strings.crossfadeSubtitle,
        keywords = listOf("crossfade", "fade", "seconds", "chuyen bai")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.SLEEP_TIMER,
        group = SettingsGroup.AUDIO_EQ,
        title = strings.sleepTimerTitle,
        subtitle = strings.sleepTimerSubtitle,
        keywords = listOf("sleep", "timer", "fade", "bedtime", "hen gio", "ngu", "15m", "30m", "60m")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.SKIP_SILENCE,
        group = SettingsGroup.AUDIO_EQ,
        title = strings.skipSilenceTitle,
        subtitle = strings.skipSilenceSubtitle,
        keywords = listOf("skip", "silence", "trim", "quiet", "khoang lang", "im lang")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.RESUME_ON_HEADSET,
        group = SettingsGroup.AUDIO_EQ,
        title = strings.resumeHeadsetTitle,
        subtitle = strings.resumeHeadsetSubtitle,
        keywords = listOf("resume", "headset", "bluetooth", "connect", "tu phat", "tai nghe")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.KEEP_PLAYING_ON_SWIPE,
        group = SettingsGroup.AUDIO_EQ,
        title = strings.keepPlayingTitle,
        subtitle = strings.keepPlayingSubtitle,
        keywords = listOf("background", "swipe", "task", "keep playing", "chay nen", "vuot tat")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.PLAYER_STYLE_PICKER,
        group = SettingsGroup.NOW_PLAYING_MOTION,
        title = strings.playerStyleTitle,
        subtitle = strings.playerStyleSubtitle,
        keywords = listOf(
            "player", "style", "flagship", "classic", "poster", "turntable",
            "video", "ipod", "timeline", "lyrics", "bedside", "tiles", "giao dien phat"
        )
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.GLASS_BLUR,
        group = SettingsGroup.NOW_PLAYING_MOTION,
        title = strings.glassBlurTitle,
        subtitle = strings.glassBlurSubtitle,
        keywords = listOf("blur", "smoked glass", "rendereffect", "mo kinh")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.DYNAMIC_MESH,
        group = SettingsGroup.NOW_PLAYING_MOTION,
        title = strings.dynamicMeshTitle,
        subtitle = strings.dynamicMeshSubtitle,
        keywords = listOf("mesh", "agsl", "shader", "gradient", "background", "nen dong")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.LYRICS_MOTION,
        group = SettingsGroup.NOW_PLAYING_MOTION,
        title = strings.lyricsMotionTitle,
        subtitle = strings.lyricsMotionSubtitle,
        keywords = listOf("lyrics", "stagger", "karaoke", "motion", "loi bai hat")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.LYRICS_FONT_SCALE,
        group = SettingsGroup.NOW_PLAYING_MOTION,
        title = strings.lyricsFontScaleTitle,
        subtitle = strings.lyricsFontScaleSubtitle,
        keywords = listOf("lyrics", "font", "size", "scale", "co chu")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.HAPTIC_FEEDBACK,
        group = SettingsGroup.NOW_PLAYING_MOTION,
        title = strings.hapticsTitle,
        subtitle = strings.hapticsSubtitle,
        keywords = listOf("haptics", "vibration", "touch", "rung")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.KEEP_SCREEN_ON,
        group = SettingsGroup.NOW_PLAYING_MOTION,
        title = strings.keepScreenOnTitle,
        subtitle = strings.keepScreenOnSubtitle,
        keywords = listOf("keep screen", "awake", "display", "sleep", "man hinh", "luon sang")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.PROFILE_CARD,
        group = SettingsGroup.APPEARANCE_LANGUAGE,
        title = strings.profileTitle,
        subtitle = strings.profileSubtitle,
        keywords = listOf("profile", "username", "avatar", "photo", "ho so", "anh dai dien")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.LANGUAGE_TOGGLE,
        group = SettingsGroup.APPEARANCE_LANGUAGE,
        title = strings.languageTitle,
        subtitle = strings.languageSubtitle,
        keywords = listOf("language", "vietnamese", "english", "vi", "en", "ngon ngu", "tieng viet", "tiếng việt")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.REPLAY_ONBOARDING,
        group = SettingsGroup.APPEARANCE_LANGUAGE,
        title = strings.replayOnboardingTitle,
        subtitle = strings.replayOnboardingSubtitle,
        keywords = listOf("welcome", "onboarding", "intro", "tour", "chao mung", "gioi thieu")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.MONOCHROME_FLAC,
        group = SettingsGroup.SOURCES_LINK_IMPORT,
        title = strings.monochromeTitle,
        subtitle = strings.monochromeSubtitle,
        keywords = listOf("monochrome", "flac", "lossless", "hi-res", "hifi", "source")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.YT_COOKIE,
        group = SettingsGroup.SOURCES_LINK_IMPORT,
        title = strings.ytCookieTitle,
        subtitle = strings.ytCookieSubtitle,
        keywords = listOf("youtube", "cookie", "auth", "sapisid", "login", "yt music")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.PROXY_URL,
        group = SettingsGroup.SOURCES_LINK_IMPORT,
        title = strings.proxyTitle,
        subtitle = strings.proxySubtitle,
        keywords = listOf("proxy", "socks", "http", "network", "url")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.LINK_IMPORTER,
        group = SettingsGroup.SOURCES_LINK_IMPORT,
        title = strings.linkImporterTitle,
        subtitle = strings.linkImporterSubtitle,
        keywords = listOf("link", "import", "spotify", "apple music", "deezer", "youtube", "playlist")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.TRANSFER_HUB,
        group = SettingsGroup.STORAGE_TRANSFER,
        title = strings.transferTitle,
        subtitle = strings.transferSubtitle,
        keywords = listOf("transfer", "momi", "mochy", "zip", "backup", "export", "restore", "import", "sao luu")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.CACHE_CLEAR,
        group = SettingsGroup.STORAGE_TRANSFER,
        title = strings.cacheSizeTitle,
        subtitle = strings.cacheSizeSubtitle,
        keywords = listOf("cache", "storage", "clear", "stream", "bo nho dem", "xoa")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.CACHE_LIMIT,
        group = SettingsGroup.STORAGE_TRANSFER,
        title = strings.cacheLimitTitle,
        subtitle = strings.cacheLimitSubtitle,
        keywords = listOf("cache", "limit", "mb", "quota", "gioi han")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.DOWNLOAD_FOLDER,
        group = SettingsGroup.STORAGE_TRANSFER,
        title = strings.downloadFolderTitle,
        subtitle = strings.downloadFolderSubtitle,
        keywords = listOf("download", "folder", "saf", "tree", "offline", "thu muc tai")
    ),
    SettingsSearchEntry(
        id = SettingsEntryId.RESET_DEFAULTS,
        group = SettingsGroup.STORAGE_TRANSFER,
        title = strings.resetDefaultsTitle,
        subtitle = strings.resetDefaultsSubtitle,
        keywords = listOf("reset", "default", "factory", "dat lai")
    )
)

fun filterSettingsEntries(
    entries: List<SettingsSearchEntry>,
    query: String
): List<SettingsSearchEntry> {
    val trimmed = query.trim().lowercase(Locale.ROOT)
    if (trimmed.isEmpty()) return entries
    val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
    return entries.filter { entry ->
        val haystack = buildString {
            append(entry.title.lowercase(Locale.ROOT))
            append(' ')
            append(entry.subtitle.lowercase(Locale.ROOT))
            append(' ')
            append(entry.group.id.lowercase(Locale.ROOT))
            entry.keywords.forEach { kw ->
                append(' ')
                append(kw.lowercase(Locale.ROOT))
            }
        }
        tokens.all { token -> haystack.contains(token) }
    }
}

fun formatByteSize(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    if (safe < 1024L) return "$safe B"
    val kb = safe / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

fun formatFrequencyHz(hz: Int): String {
    return if (hz >= 1000) "${hz / 1000}k" else hz.toString()
}

fun formatDb(db: Float): String {
    val rounded = kotlin.math.round(db * 2f) / 2f
    val sign = if (rounded > 0.05f) "+" else if (rounded < -0.05f) "-" else ""
    return String.format(Locale.US, "%s%.1f dB", sign, abs(rounded))
}

fun formatShortDb(db: Float): String {
    val rounded = kotlin.math.round(db * 2f) / 2f
    val sign = if (rounded > 0.05f) "+" else if (rounded < -0.05f) "-" else ""
    return String.format(Locale.US, "%s%.1f", sign, abs(rounded))
}

fun formatSettingsDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSec / 3600L
    val minutes = (totalSec % 3600L) / 60L
    val seconds = totalSec % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun computeCollapseLatch(
    currentCollapsed: Boolean,
    firstVisibleIndex: Int,
    firstVisibleOffsetPx: Float,
    enterPx: Float,
    exitPx: Float
): Boolean {
    return when {
        firstVisibleIndex > 0 -> true
        firstVisibleOffsetPx > enterPx -> true
        firstVisibleOffsetPx < exitPx -> false
        else -> currentCollapsed
    }
}
