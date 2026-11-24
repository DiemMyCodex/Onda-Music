package com.onda.music.feature.home

import androidx.compose.runtime.Immutable
import com.onda.music.core.design.OndaArtworkPalette
import com.onda.music.core.model.Album
import com.onda.music.core.model.ContextualShelf
import com.onda.music.core.model.HomeShelf
import com.onda.music.core.model.MoodCategory
import com.onda.music.core.model.Track
import java.util.Calendar

@Immutable
data class CollectionDetailState(
    val sourceId: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isAlbum: Boolean = false
)

@Immutable
data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOfflineFallback: Boolean = false,
    val errorMessage: String? = null,
    val username: String = "",
    val avatarPath: String? = null,
    val languageCode: String = "en",
    val dynamicMeshEnabled: Boolean = true,
    val glassBlurEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val heroTrack: Track? = null,
    val heroSubtitle: String = "",
    val heroPalette: OndaArtworkPalette = OndaArtworkPalette.Default,
    val recentlyPlayed: List<Track> = emptyList(),
    val mostPlayed: List<Track> = emptyList(),
    val quickPicks: List<Track> = emptyList(),
    val contextualShelf: ContextualShelf? = null,
    val shelves: List<HomeShelf> = emptyList(),
    val chartShelves: List<HomeShelf> = emptyList(),
    val newReleases: List<Album> = emptyList(),
    val moodCategories: List<MoodCategory> = emptyList(),
    val localTracks: List<Track> = emptyList(),
    val activeTrackId: String? = null,
    val isPlaying: Boolean = false,
    val hasMiniPlayer: Boolean = false,
    val detailSheet: CollectionDetailState? = null
)

@Immutable
data class HomeStrings(
    val greetingMorning: String,
    val greetingAfternoon: String,
    val greetingEvening: String,
    val listenerFallback: String,
    val continueListening: String,
    val playAction: String,
    val pauseAction: String,
    val playAllAction: String,
    val recentlyPlayed: String,
    val mostPlayed: String,
    val quickPicks: String,
    val becauseYouListenedFormat: String,
    val forYourTasteSubtitle: String,
    val newReleases: String,
    val charts: String,
    val moodsAndGenres: String,
    val offlineLibrary: String,
    val offlineNotice: String,
    val retryAction: String,
    val searchDescription: String,
    val settingsDescription: String,
    val refreshDescription: String,
    val closeDescription: String,
    val emptyDetail: String,
    val tracksSuffix: String,
    val unknownArtist: String,
    val sourceLocal: String,
    val sourceLossless: String,
    val sourceStream: String
)

internal fun resolveHomeStrings(languageCode: String): HomeStrings {
    return if (languageCode == "vi") {
        HomeStrings(
            greetingMorning = "Chào buổi sáng",
            greetingAfternoon = "Chào buổi chiều",
            greetingEvening = "Chào buổi tối",
            listenerFallback = "Người nghe Onda",
            continueListening = "Tiếp tục nghe",
            playAction = "Phát",
            pauseAction = "Tạm dừng",
            playAllAction = "Phát tất cả",
            recentlyPlayed = "Nghe gần đây",
            mostPlayed = "Nghe nhiều nhất",
            quickPicks = "Chọn nhanh",
            becauseYouListenedFormat = "Vì bạn đã nghe %s",
            forYourTasteSubtitle = "Gợi ý theo gu nghe gần đây của bạn",
            newReleases = "Phát hành mới",
            charts = "Bảng xếp hạng",
            moodsAndGenres = "Tâm trạng & Thể loại",
            offlineLibrary = "Thư viện ngoại tuyến",
            offlineNotice = "Đang dùng thư viện ngoại tuyến do không thể kết nối.",
            retryAction = "Thử lại",
            searchDescription = "Tìm kiếm",
            settingsDescription = "Cài đặt",
            refreshDescription = "Làm mới",
            closeDescription = "Đóng",
            emptyDetail = "Không có bài hát nào trong danh sách này.",
            tracksSuffix = "bài hát",
            unknownArtist = "Nghệ sĩ chưa rõ",
            sourceLocal = "Nội bộ",
            sourceLossless = "FLAC",
            sourceStream = "YT Music"
        )
    } else {
        HomeStrings(
            greetingMorning = "Good morning",
            greetingAfternoon = "Good afternoon",
            greetingEvening = "Good evening",
            listenerFallback = "Onda Listener",
            continueListening = "Continue Listening",
            playAction = "Play",
            pauseAction = "Pause",
            playAllAction = "Play All",
            recentlyPlayed = "Recently Played",
            mostPlayed = "Most Played",
            quickPicks = "Quick Picks",
            becauseYouListenedFormat = "Because you listened to %s",
            forYourTasteSubtitle = "Tuned to your listening history",
            newReleases = "New Releases",
            charts = "Charts",
            moodsAndGenres = "Moods & Genres",
            offlineLibrary = "Offline Library",
            offlineNotice = "Offline mode active. Showing your local & cached library.",
            retryAction = "Retry",
            searchDescription = "Search",
            settingsDescription = "Settings",
            refreshDescription = "Refresh",
            closeDescription = "Close",
            emptyDetail = "No tracks found in this collection.",
            tracksSuffix = "tracks",
            unknownArtist = "Unknown Artist",
            sourceLocal = "Local",
            sourceLossless = "FLAC",
            sourceStream = "YT Music"
        )
    }
}

internal fun timeGreeting(strings: HomeStrings): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour in 5..11 -> strings.greetingMorning
        hour in 12..17 -> strings.greetingAfternoon
        else -> strings.greetingEvening
    }
}

internal fun formatDurationMs(durationMs: Long): String {
    if (durationMs <= 0L) return ""
    val totalSeconds = durationMs / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
