package com.onda.music.feature.player.styles

import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import com.onda.music.core.database.OndaUserSettings
import com.onda.music.core.model.DownloadItem
import com.onda.music.core.model.EqConfig
import com.onda.music.core.model.LyricsDocument
import com.onda.music.core.model.PlayerStudioConfig
import com.onda.music.core.model.Track
import com.onda.music.feature.player.OndaLikeBurstState

@Immutable
data class PlayerStageContext(
    val settings: OndaUserSettings,
    val liked: Boolean,
    val downloadItem: DownloadItem?,
    val lyrics: LyricsDocument?,
    val lyricsOffsetMs: Long,
    val isLyricsLoading: Boolean,
    val lyricsError: String?,
    val showInlineLyrics: Boolean,
    val onToggleInlineLyrics: () -> Unit,
    val onTogglePlayPause: () -> Unit,
    val onSeekToMs: (Long) -> Unit,
    val onSeekToFraction: (Float) -> Unit,
    val onSkipNext: () -> Unit,
    val onSkipPrevious: () -> Unit,
    val onSelectQueueIndex: (Int) -> Unit,
    val onToggleLike: () -> Unit,
    val onLikePositioned: (Offset) -> Unit,
    val onDownloadClick: () -> Unit,
    val onToggleShuffle: () -> Unit,
    val onToggleRepeat: () -> Unit,
    val onOpenQueue: () -> Unit,
    val onOpenSleepTimer: () -> Unit,
    val onOpenStylePicker: () -> Unit,
    val onRefreshLyrics: () -> Unit,
    val onAdjustLyricsOffset: (Long) -> Unit,
    val onAttachSurfaceView: (SurfaceView?) -> Unit,
    val onAttachTextureView: (TextureView?) -> Unit,
    val onOpenOverflowMenu: () -> Unit = {},
    val likeBurstState: OndaLikeBurstState? = null,
    val onLikeFromButton: (() -> Unit)? = null,
    val onLikeFromDoubleTap: ((Offset) -> Unit)? = null,
    val eqConfig: EqConfig = EqConfig(),
    val onOpenStudio: () -> Unit = {},
    val onOpenShare: (Int?) -> Unit = {},
    val onOpenSpeedSheet: () -> Unit = {},
    val onOpenStreamInfo: () -> Unit = {},
    val onOpenLyricsOffset: () -> Unit = {},
    val onOpenSongMenu: (Track, Int?) -> Unit = { _, _ -> },
    val onPlayNext: (Track) -> Unit = {},
    val onAddToQueue: (Track) -> Unit = {},
    val onUpdateStudioConfig: (PlayerStudioConfig) -> Unit = {},
    val isPreview: Boolean = false
)
