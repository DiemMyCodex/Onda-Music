package com.onda.music.feature.library

import androidx.compose.runtime.Immutable
import com.onda.music.core.database.ArtistPlayStatRow
import com.onda.music.core.database.TrackPlayStatRow
import com.onda.music.core.model.DownloadItem
import com.onda.music.core.model.Playlist
import com.onda.music.core.model.Track
import com.onda.music.core.sources.LocalMediaFolder

enum class StatsRange(val label: String, val days: Int?) {
    WEEK("7 Days", 7),
    MONTH("30 Days", 30),
    ALL_TIME("All Time", null)
}

enum class LibraryTab(val label: String) {
    OVERVIEW("Overview"),
    PLAYLISTS("Playlists"),
    LIKED("Liked"),
    LOCAL("Local"),
    DOWNLOADS("Downloads"),
    HISTORY("History & Stats")
}

@Immutable
data class DownloadEntryUi(
    val item: DownloadItem,
    val track: Track?
)

@Immutable
data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.OVERVIEW,
    val searchQuery: String = "",
    val playlists: List<Playlist> = emptyList(),
    val playlistCoversById: Map<String, List<String>> = emptyMap(),
    val selectedPlaylistId: String? = null,
    val selectedPlaylist: Playlist? = null,
    val selectedPlaylistTracks: List<Track> = emptyList(),
    val likedSongs: List<Track> = emptyList(),
    val librarySongs: List<Track> = emptyList(),
    val localSongs: List<Track> = emptyList(),
    val songsNeedingRelink: List<Track> = emptyList(),
    val localFolders: List<LocalMediaFolder> = emptyList(),
    val localTrackFolders: Map<String, String> = emptyMap(),
    val selectedLocalFolderPath: String? = null,
    val hasLocalPermission: Boolean = false,
    val isScanningLocal: Boolean = false,
    val lastScanMessage: String? = null,
    val downloads: List<DownloadEntryUi> = emptyList(),
    val recentSongs: List<Track> = emptyList(),
    val statsRange: StatsRange = StatsRange.MONTH,
    val periodPlayCount: Int = 0,
    val periodPlayDurationMs: Long = 0L,
    val topTracks: List<TrackPlayStatRow> = emptyList(),
    val topArtists: List<ArtistPlayStatRow> = emptyList(),
    val totalPlayCount: Int = 0,
    val totalPlayDurationMs: Long = 0L,
    val activeTrackId: String? = null,
    val hasMiniPlayer: Boolean = false,
    val isPlaying: Boolean = false,
    val languageCode: String = "en",
    val glassBlurEnabled: Boolean = true
)

@Immutable
data class LibraryStrings(
    val isVi: Boolean,
    val libraryTitle: String,
    val newPlaylistDesc: String,
    val settingsDesc: String,
    val searchPlaceholder: String,
    val clearSearchDesc: String,
    val tabOverview: String,
    val tabPlaylists: String,
    val tabLiked: String,
    val tabLocal: String,
    val tabDownloads: String,
    val tabHistory: String,
    val rangeWeek: String,
    val rangeMonth: String,
    val rangeAllTime: String,
    val hubPlaylistsTitle: String,
    val hubLocalTitle: String,
    val hubDownloadsTitle: String,
    val hubHistoryTitle: String,
    val likedSongsTitle: String,
    val shuffleLikedDesc: String,
    val recentlyPlayedTitle: String,
    val shuffleLabel: String,
    val playLabel: String,
    val playlistActionsDesc: String,
    val renameLabel: String,
    val deleteLabel: String,
    val downloadedDesc: String,
    val likeLabel: String,
    val unlikeLabel: String,
    val trackActionsDesc: String,
    val addToPlaylistLabel: String,
    val removeDownloadLabel: String,
    val cancelDownloadLabel: String,
    val retryDownloadLabel: String,
    val downloadOfflineLabel: String,
    val savedTrackFallbackArtist: String,
    val retryDownloadDesc: String,
    val cancelDownloadDesc: String,
    val deleteDownloadDesc: String,
    val localScannerTitle: String,
    val localScannerReadyDesc: String,
    val localScannerPermissionDesc: String,
    val scanLabel: String,
    val allowLabel: String,
    val needsRelinkDesc: String,
    val relinkLabel: String,
    val playlistNameFieldLabel: String,
    val descriptionOptionalLabel: String,
    val cancelLabel: String,
    val deletePlaylistTitle: String,
    val addToPlaylistSheetTitle: String,
    val newLabel: String,
    val noPlaylistsYetTitle: String,
    val noPlaylistsSheetSubtitle: String,
    val noSavedTracksTitle: String,
    val noSavedTracksSubtitle: String,
    val addedDesc: String,
    val addTrackDesc: String,
    val noMatchingLikedTitle: String,
    val noLikedYetTitle: String,
    val noLikedYetSubtitle: String,
    val createPlaylistTitle: String,
    val createLabel: String,
    val renamePlaylistTitle: String,
    val saveLabel: String,
    val noMatchesFoundTitle: String,
    val matchingPlaylistsHeader: String,
    val matchingTracksHeader: String,
    val yourPlaylistsHeader: String,
    val noPlaylistsOverviewSubtitle: String,
    val createPlaylistActionLabel: String,
    val noMatchingPlaylistsTitle: String,
    val noPlaylistsTabSubtitle: String,
    val noMatchingLocalTitle: String,
    val noLocalIndexedTitle: String,
    val noLocalIndexedSubtitle: String,
    val scanningFoldersTitle: String,
    val scanningFoldersSubtitle: String,
    val emptyFolderTitle: String,
    val emptyFolderSubtitle: String,
    val editLocalTagsDesc: String,
    val noMatchingDownloadsTitle: String,
    val noDownloadsYetTitle: String,
    val noDownloadsYetSubtitle: String,
    val shareOledRecapLabel: String,
    val noMatchingHistoryTitle: String,
    val noHistoryYetTitle: String,
    val noHistoryYetSubtitle: String,
    val removeSwipeLabel: String,
    val downloadSwipeLabel: String,
    val backToPlaylistsDesc: String,
    val renamePlaylistDesc: String,
    val deletePlaylistDesc: String,
    val addLabel: String,
    val playlistEmptyTitle: String,
    val playlistEmptySubtitle: String,
    val addTracksLabel: String,
    val removeFromPlaylistDesc: String,
    val reorderTrackDesc: String,
    val tagNoLocalUriError: String,
    val audioPermissionRequiredMsg: String,
    val localScanFailedMsg: String,
    val tagTitleEmptyError: String,
    val unknownArtistLabel: String,
    val tagUpdateFailedError: String,
    val tagWritePermissionError: String,
    val editAudioTagsTitle: String,
    val editAudioTagsSubtitle: String,
    val closeLabel: String,
    val coverArtDesc: String,
    val coverSelectedLabel: String,
    val embedCoverArtLabel: String,
    val titleFieldLabel: String,
    val artistFieldLabel: String,
    val albumFieldLabel: String,
    val trackNumberFieldLabel: String,
    val tagNoWritableUriError: String,
    val writingTagsLabel: String,
    val saveTagsAtomicallyLabel: String
) {
    fun tabLabel(tab: LibraryTab): String = when (tab) {
        LibraryTab.OVERVIEW -> tabOverview
        LibraryTab.PLAYLISTS -> tabPlaylists
        LibraryTab.LIKED -> tabLiked
        LibraryTab.LOCAL -> tabLocal
        LibraryTab.DOWNLOADS -> tabDownloads
        LibraryTab.HISTORY -> tabHistory
    }

    fun statsRangeLabel(range: StatsRange): String = when (range) {
        StatsRange.WEEK -> rangeWeek
        StatsRange.MONTH -> rangeMonth
        StatsRange.ALL_TIME -> rangeAllTime
    }

    fun playlistCountText(count: Int): String = if (isVi) {
        "$count danh sách"
    } else {
        if (count == 1) "1 list" else "$count lists"
    }

    fun trackCountText(count: Int): String = if (isVi) {
        "$count bài hát"
    } else {
        if (count == 1) "1 track" else "$count tracks"
    }

    fun tracksLabelText(count: Int): String = if (isVi) "$count bài hát" else "$count tracks"

    fun localTracksCountText(count: Int): String = if (isVi) {
        "$count bài hát cục bộ"
    } else {
        "$count local tracks"
    }

    fun songCountText(count: Int): String = if (isVi) {
        "$count bài hát"
    } else {
        if (count == 1) "1 song" else "$count songs"
    }

    fun offlineCountText(count: Int): String = if (isVi) "$count ngoại tuyến" else "$count offline"

    fun recentCountText(count: Int): String = if (isVi) "$count gần đây" else "$count recent"

    fun playsHoursText(playCount: Int, hoursText: String): String = if (isVi) {
        "$playCount lượt phát | $hoursText"
    } else {
        "$playCount plays | $hoursText"
    }

    fun downloadQueuedText(artist: String): String = if (isVi) {
        "$artist  |  Đang chờ"
    } else {
        "$artist  |  Queued"
    }

    fun downloadingPercentText(artist: String, pct: Int): String = if (isVi) {
        "$artist  |  Đang tải $pct%"
    } else {
        "$artist  |  Downloading $pct%"
    }

    fun downloadFailedText(artist: String): String = if (isVi) {
        "$artist  |  Tải thất bại"
    } else {
        "$artist  |  Download failed"
    }

    fun needsRelinkTitle(count: Int): String = if (isVi) {
        "Cần liên kết lại ($count)"
    } else {
        "Needs Relink ($count)"
    }

    fun deletePlaylistConfirmText(playlistName: String): String = if (isVi) {
        "Xóa \"$playlistName\" khỏi thư viện? Các bài hát vẫn được giữ lại."
    } else {
        "Remove \"$playlistName\" from your library? Tracks remain in your library."
    }

    fun addTracksToPlaylistTitle(playlistName: String): String = if (isVi) {
        "Thêm bài hát vào $playlistName"
    } else {
        "Add Tracks to $playlistName"
    }

    fun noMatchingLikedSubtitle(query: String): String = if (isVi) {
        "Không có bài đã thích nào khớp \"$query\"."
    } else {
        "No liked tracks match \"$query\"."
    }

    fun noMatchesOverviewSubtitle(query: String): String = if (isVi) {
        "Không có playlist hoặc bài hát nào khớp \"$query\"."
    } else {
        "No playlists or tracks match \"$query\"."
    }

    fun playlistsSectionTitle(query: String, count: Int): String = if (isVi) {
        if (query.isNotEmpty()) "Playlist ($count)" else "Bộ sưu tập ($count)"
    } else {
        if (query.isNotEmpty()) "Playlists ($count)" else "Collections ($count)"
    }

    fun noMatchingPlaylistsSubtitle(query: String): String = if (isVi) {
        "Không có playlist nào khớp \"$query\"."
    } else {
        "No playlists match \"$query\"."
    }

    fun noMatchingLocalSubtitle(query: String): String = if (isVi) {
        "Không có bài cục bộ nào khớp \"$query\"."
    } else {
        "No local tracks match \"$query\"."
    }

    fun downloadsSummaryText(completed: Int, total: Int): String = if (isVi) {
        "$completed sẵn sàng ngoại tuyến  |  $total tổng"
    } else {
        "$completed offline ready  |  $total total"
    }

    fun noMatchingDownloadsSubtitle(query: String): String = if (isVi) {
        "Không có bản tải nào khớp \"$query\"."
    } else {
        "No downloads match \"$query\"."
    }

    fun oledRecapHeader(range: StatsRange): String = if (isVi) {
        "BÁO CÁO OLED · ${statsRangeLabel(range).uppercase()}"
    } else {
        "OLED RECAP · ${statsRangeLabel(range).uppercase()}"
    }

    fun hoursListenedText(formattedHours: String): String = if (isVi) {
        "Đã nghe $formattedHours"
    } else {
        "$formattedHours listened"
    }

    fun periodPlaysSummary(periodPlays: Int, totalPlays: Int, downloadedCount: Int): String = if (isVi) {
        "$periodPlays lượt trong kỳ · $totalPlays tổng cộng ($downloadedCount ngoại tuyến)"
    } else {
        "$periodPlays plays in period · $totalPlays lifetime ($downloadedCount offline)"
    }

    fun topTracksHeader(range: StatsRange): String = if (isVi) {
        "BÀI HÁT HÀNG ĐẦU (${statsRangeLabel(range)})"
    } else {
        "TOP TRACKS (${statsRangeLabel(range)})"
    }

    fun artistTrackPlaysText(artist: String, playCount: Int): String = if (isVi) {
        "$artist · $playCount lượt"
    } else {
        "$artist · $playCount plays"
    }

    fun topArtistsHeader(range: StatsRange): String = if (isVi) {
        "NGHỆ SĨ HÀNG ĐẦU (${statsRangeLabel(range)})"
    } else {
        "TOP ARTISTS (${statsRangeLabel(range)})"
    }

    fun recentlyPlayedHeader(count: Int): String = if (isVi) {
        "Nghe gần đây ($count)"
    } else {
        "Recently played ($count)"
    }

    fun noMatchingHistorySubtitle(query: String): String = if (isVi) {
        "Không có bài nghe gần đây nào khớp \"$query\"."
    } else {
        "No recent tracks match \"$query\"."
    }

    fun playlistDetailSubtitle(count: Int): String = if (isVi) {
        "$count bài hát  |  Vuốt hoặc kéo tay nắm"
    } else {
        "$count tracks  |  Swipe or drag handle"
    }

    fun updatedTagsMessage(title: String): String = if (isVi) {
        "Đã cập nhật thẻ cho $title"
    } else {
        "Updated tags for $title"
    }

    fun scanCompletedMessage(trackCount: Int, folderCount: Int, relinkedCount: Int): String = if (isVi) {
        val relinkSuffix = if (relinkedCount > 0) ", đã liên kết lại $relinkedCount bài hát" else ""
        "Đã quét $trackCount bài hát trong $folderCount thư mục$relinkSuffix"
    } else {
        val relinkSuffix = if (relinkedCount > 0) ", relinked $relinkedCount tracks" else ""
        "Scanned $trackCount tracks across $folderCount folders$relinkSuffix"
    }
}

internal fun resolveLibraryStrings(languageCode: String): LibraryStrings {
    val isVi = languageCode.startsWith("vi", ignoreCase = true)
    return if (isVi) {
        LibraryStrings(
            isVi = true,
            libraryTitle = "Thư viện",
            newPlaylistDesc = "Tạo playlist mới",
            settingsDesc = "Cài đặt",
            searchPlaceholder = "Tìm trong thư viện...",
            clearSearchDesc = "Xóa tìm kiếm",
            tabOverview = "Tổng quan",
            tabPlaylists = "Playlist",
            tabLiked = "Đã thích",
            tabLocal = "Nội bộ",
            tabDownloads = "Tải xuống",
            tabHistory = "Lịch sử & Thống kê",
            rangeWeek = "7 ngày",
            rangeMonth = "30 ngày",
            rangeAllTime = "Tất cả",
            hubPlaylistsTitle = "Playlist",
            hubLocalTitle = "Nội bộ",
            hubDownloadsTitle = "Tải xuống",
            hubHistoryTitle = "Lịch sử",
            likedSongsTitle = "Bài hát đã thích",
            shuffleLikedDesc = "Phát ngẫu nhiên bài đã thích",
            recentlyPlayedTitle = "Nghe gần đây",
            shuffleLabel = "Ngẫu nhiên",
            playLabel = "Phát",
            playlistActionsDesc = "Tùy chọn playlist",
            renameLabel = "Đổi tên",
            deleteLabel = "Xóa",
            downloadedDesc = "Đã tải",
            likeLabel = "Thích",
            unlikeLabel = "Bỏ thích",
            trackActionsDesc = "Tùy chọn bài hát",
            addToPlaylistLabel = "Thêm vào playlist",
            removeDownloadLabel = "Xóa bản tải",
            cancelDownloadLabel = "Hủy tải xuống",
            retryDownloadLabel = "Thử tải lại",
            downloadOfflineLabel = "Tải nghe ngoại tuyến",
            savedTrackFallbackArtist = "Bài hát đã lưu",
            retryDownloadDesc = "Thử tải lại",
            cancelDownloadDesc = "Hủy tải xuống",
            deleteDownloadDesc = "Xóa bản tải",
            localScannerTitle = "Trình quét nhạc cục bộ",
            localScannerReadyDesc = "Quét thư mục MediaStore để tìm nhạc ngoại tuyến",
            localScannerPermissionDesc = "Cấp quyền âm thanh để tìm bài hát trên máy",
            scanLabel = "Quét",
            allowLabel = "Cho phép",
            needsRelinkDesc = "Các bài hát cục bộ đã nhập bị thiếu tệp gốc. Hãy quét lại bộ nhớ hoặc chọn tệp thủ công.",
            relinkLabel = "Liên kết",
            playlistNameFieldLabel = "Tên playlist",
            descriptionOptionalLabel = "Mô tả (tùy chọn)",
            cancelLabel = "Hủy",
            deletePlaylistTitle = "Xóa playlist",
            addToPlaylistSheetTitle = "Thêm vào playlist",
            newLabel = "Tạo mới",
            noPlaylistsYetTitle = "Chưa có playlist nào",
            noPlaylistsSheetSubtitle = "Tạo playlist để lưu các bài hát vào bộ sưu tập.",
            noSavedTracksTitle = "Chưa có bài hát đã lưu",
            noSavedTracksSubtitle = "Hãy thích bài hát, quét nhạc cục bộ hoặc phát nhạc trước.",
            addedDesc = "Đã thêm",
            addTrackDesc = "Thêm bài hát",
            noMatchingLikedTitle = "Không tìm thấy bài đã thích phù hợp",
            noLikedYetTitle = "Chưa có bài hát đã thích",
            noLikedYetSubtitle = "Nhấn biểu tượng tim trên bài hát bất kỳ để lưu tại đây.",
            createPlaylistTitle = "Tạo playlist",
            createLabel = "Tạo",
            renamePlaylistTitle = "Đổi tên playlist",
            saveLabel = "Lưu",
            noMatchesFoundTitle = "Không tìm thấy kết quả",
            matchingPlaylistsHeader = "Playlist phù hợp",
            matchingTracksHeader = "Bài hát phù hợp",
            yourPlaylistsHeader = "Playlist của bạn",
            noPlaylistsOverviewSubtitle = "Tạo playlist để sắp xếp các bài hát yêu thích.",
            createPlaylistActionLabel = "Tạo playlist",
            noMatchingPlaylistsTitle = "Không có playlist phù hợp",
            noPlaylistsTabSubtitle = "Tạo playlist và kéo thả bài hát theo thứ tự bất kỳ.",
            noMatchingLocalTitle = "Không tìm thấy bài cục bộ phù hợp",
            noLocalIndexedTitle = "Chưa quét nhạc cục bộ",
            noLocalIndexedSubtitle = "Nhấn Quét ở trên sau khi cấp quyền truy cập bộ nhớ âm thanh.",
            scanningFoldersTitle = "Đang quét thư mục nhạc...",
            scanningFoldersSubtitle = "Đang lập chỉ mục thư mục...",
            emptyFolderTitle = "Không có bài hát trong thư mục này",
            emptyFolderSubtitle = "Nhấn lại thẻ thư mục đang chọn để xem toàn bộ nhạc cục bộ.",
            editLocalTagsDesc = "Sửa thẻ nhạc cục bộ",
            noMatchingDownloadsTitle = "Không có bản tải phù hợp",
            noDownloadsYetTitle = "Chưa có bản tải xuống nào",
            noDownloadsYetSubtitle = "Tải bài hát từ Tìm kiếm, Trang chủ hoặc Đã thích để nghe ngoại tuyến.",
            shareOledRecapLabel = "Chia sẻ báo cáo OLED đen thuần",
            noMatchingHistoryTitle = "Không có lịch sử phù hợp",
            noHistoryYetTitle = "Chưa có lịch sử phát nhạc",
            noHistoryYetSubtitle = "Phát một bài hát để hiển thị thống kê nghe nhạc tại đây.",
            removeSwipeLabel = "Gỡ",
            downloadSwipeLabel = "Tải về",
            backToPlaylistsDesc = "Quay lại danh sách playlist",
            renamePlaylistDesc = "Đổi tên playlist",
            deletePlaylistDesc = "Xóa playlist",
            addLabel = "Thêm",
            playlistEmptyTitle = "Playlist đang trống",
            playlistEmptySubtitle = "Thêm bài hát từ danh sách đã lưu, đã thích hoặc nhạc cục bộ.",
            addTracksLabel = "Thêm bài hát",
            removeFromPlaylistDesc = "Xóa khỏi playlist",
            reorderTrackDesc = "Sắp xếp lại bài hát",
            tagNoLocalUriError = "Bài hát không có đường dẫn cục bộ",
            audioPermissionRequiredMsg = "Cần quyền truy cập bộ nhớ âm thanh để quét tệp cục bộ",
            localScanFailedMsg = "Quét nhạc cục bộ thất bại",
            tagTitleEmptyError = "Tiêu đề không được để trống",
            unknownArtistLabel = "Nghệ sĩ không rõ",
            tagUpdateFailedError = "Không thể cập nhật thẻ",
            tagWritePermissionError = "Cần quyền ghi để chỉnh sửa tệp âm thanh cục bộ",
            editAudioTagsTitle = "Chỉnh sửa thẻ âm thanh",
            editAudioTagsSubtitle = "MP3 ID3v2 · FLAC Vorbis · M4A Atoms",
            closeLabel = "Đóng",
            coverArtDesc = "Ảnh bìa",
            coverSelectedLabel = "Đã chọn ảnh bìa",
            embedCoverArtLabel = "Nhúng ảnh bìa",
            titleFieldLabel = "Tiêu đề",
            artistFieldLabel = "Nghệ sĩ",
            albumFieldLabel = "Album",
            trackNumberFieldLabel = "Số thứ tự bài",
            tagNoWritableUriError = "Bài hát không có đường dẫn cục bộ có thể ghi",
            writingTagsLabel = "Đang ghi thẻ...",
            saveTagsAtomicallyLabel = "Lưu thẻ an toàn"
        )
    } else {
        LibraryStrings(
            isVi = false,
            libraryTitle = "Library",
            newPlaylistDesc = "New playlist",
            settingsDesc = "Settings",
            searchPlaceholder = "Search library...",
            clearSearchDesc = "Clear search",
            tabOverview = "Overview",
            tabPlaylists = "Playlists",
            tabLiked = "Liked",
            tabLocal = "Local",
            tabDownloads = "Downloads",
            tabHistory = "History & Stats",
            rangeWeek = "7 Days",
            rangeMonth = "30 Days",
            rangeAllTime = "All Time",
            hubPlaylistsTitle = "Playlists",
            hubLocalTitle = "Local",
            hubDownloadsTitle = "Downloads",
            hubHistoryTitle = "History",
            likedSongsTitle = "Liked Songs",
            shuffleLikedDesc = "Shuffle liked songs",
            recentlyPlayedTitle = "Recently Played",
            shuffleLabel = "Shuffle",
            playLabel = "Play",
            playlistActionsDesc = "Playlist actions",
            renameLabel = "Rename",
            deleteLabel = "Delete",
            downloadedDesc = "Downloaded",
            likeLabel = "Like",
            unlikeLabel = "Unlike",
            trackActionsDesc = "Track actions",
            addToPlaylistLabel = "Add to playlist",
            removeDownloadLabel = "Remove download",
            cancelDownloadLabel = "Cancel download",
            retryDownloadLabel = "Retry download",
            downloadOfflineLabel = "Download offline",
            savedTrackFallbackArtist = "Saved track",
            retryDownloadDesc = "Retry download",
            cancelDownloadDesc = "Cancel download",
            deleteDownloadDesc = "Delete download",
            localScannerTitle = "Local Audio Scanner",
            localScannerReadyDesc = "Scan MediaStore folders for offline audio",
            localScannerPermissionDesc = "Grant audio permission to discover device tracks",
            scanLabel = "Scan",
            allowLabel = "Allow",
            needsRelinkDesc = "Imported local tracks missing a file URI. Scan local storage or pick an audio file manually.",
            relinkLabel = "Relink",
            playlistNameFieldLabel = "Playlist name",
            descriptionOptionalLabel = "Description (optional)",
            cancelLabel = "Cancel",
            deletePlaylistTitle = "Delete Playlist",
            addToPlaylistSheetTitle = "Add to Playlist",
            newLabel = "New",
            noPlaylistsYetTitle = "No playlists yet",
            noPlaylistsSheetSubtitle = "Create a playlist to save tracks into collections.",
            noSavedTracksTitle = "No saved tracks found",
            noSavedTracksSubtitle = "Like songs, scan local audio, or play tracks first.",
            addedDesc = "Added",
            addTrackDesc = "Add track",
            noMatchingLikedTitle = "No matching liked songs",
            noLikedYetTitle = "No liked songs yet",
            noLikedYetSubtitle = "Tap the heart on any track to save it here.",
            createPlaylistTitle = "Create Playlist",
            createLabel = "Create",
            renamePlaylistTitle = "Rename Playlist",
            saveLabel = "Save",
            noMatchesFoundTitle = "No matches found",
            matchingPlaylistsHeader = "Matching Playlists",
            matchingTracksHeader = "Matching Tracks",
            yourPlaylistsHeader = "Your Playlists",
            noPlaylistsOverviewSubtitle = "Create a playlist to organize your favorite tracks.",
            createPlaylistActionLabel = "Create Playlist",
            noMatchingPlaylistsTitle = "No matching playlists",
            noPlaylistsTabSubtitle = "Create a playlist and drag tracks into any order.",
            noMatchingLocalTitle = "No matching local tracks",
            noLocalIndexedTitle = "No local audio indexed",
            noLocalIndexedSubtitle = "Tap Scan above after granting audio storage permission.",
            scanningFoldersTitle = "Scanning local folders...",
            scanningFoldersSubtitle = "Matching folder index...",
            emptyFolderTitle = "No tracks in this folder",
            emptyFolderSubtitle = "Tap the selected folder chip to show all local tracks.",
            editLocalTagsDesc = "Edit local tags",
            noMatchingDownloadsTitle = "No matching downloads",
            noDownloadsYetTitle = "No downloads yet",
            noDownloadsYetSubtitle = "Download tracks from Search, Home, or Liked songs for offline playback.",
            shareOledRecapLabel = "Share Pure-Black OLED Recap",
            noMatchingHistoryTitle = "No matching history",
            noHistoryYetTitle = "No playback history yet",
            noHistoryYetSubtitle = "Play any song and your recent listening recap will appear here.",
            removeSwipeLabel = "Remove",
            downloadSwipeLabel = "Download",
            backToPlaylistsDesc = "Back to playlists",
            renamePlaylistDesc = "Rename playlist",
            deletePlaylistDesc = "Delete playlist",
            addLabel = "Add",
            playlistEmptyTitle = "Playlist is empty",
            playlistEmptySubtitle = "Add tracks from your saved, liked, or local songs.",
            addTracksLabel = "Add Tracks",
            removeFromPlaylistDesc = "Remove from playlist",
            reorderTrackDesc = "Reorder track",
            tagNoLocalUriError = "Track has no local URI",
            audioPermissionRequiredMsg = "Audio storage permission is required to scan local files",
            localScanFailedMsg = "Local media scan failed",
            tagTitleEmptyError = "Title cannot be empty",
            unknownArtistLabel = "Unknown Artist",
            tagUpdateFailedError = "Failed to update tags",
            tagWritePermissionError = "Write permission required to edit local audio file",
            editAudioTagsTitle = "Edit Audio Tags",
            editAudioTagsSubtitle = "MP3 ID3v2 · FLAC Vorbis · M4A Atoms",
            closeLabel = "Close",
            coverArtDesc = "Cover art",
            coverSelectedLabel = "Cover Selected",
            embedCoverArtLabel = "Embed Cover Art",
            titleFieldLabel = "Title",
            artistFieldLabel = "Artist",
            albumFieldLabel = "Album",
            trackNumberFieldLabel = "Track Number",
            tagNoWritableUriError = "Track does not have a writable local URI",
            writingTagsLabel = "Writing Tags...",
            saveTagsAtomicallyLabel = "Save Tags Atomically"
        )
    }
}

internal fun filterLocalTracksByFolder(
    tracks: List<Track>,
    trackFolders: Map<String, String>,
    folderPath: String?
): List<Track> {
    if (folderPath.isNullOrBlank()) return tracks
    return tracks.filter { track -> trackFolders[track.id] == folderPath }
}
