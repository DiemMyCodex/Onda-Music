package com.onda.music.feature.player.styles

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OdometerText
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.model.PlaybackStatus
import com.onda.music.feature.player.LyricsPane
import com.onda.music.feature.player.OndaLikeSlot
import com.onda.music.feature.player.PlayerSeekSection
import com.onda.music.feature.player.PlayerTransportControls
import com.onda.music.feature.player.TrackArtworkImage
import com.onda.music.feature.player.TrackMetaHeader
import com.onda.music.feature.player.formatDurationMs
import com.onda.music.feature.player.playerPressBounce
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val TURNTABLE_PIVOT_X = 0.87f
private const val TURNTABLE_PIVOT_Y = 0.14f
private const val TURNTABLE_REST_ANGLE = 105f
private const val TURNTABLE_END_ANGLE = 141f
private const val TURNTABLE_PARK_ANGLE = 92f

private fun isOnTurntableArm(
    point: Offset,
    pivot: Offset,
    angleDeg: Float,
    armLengthPx: Float,
    slopPx: Float
): Boolean {
    val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
    val dir = Offset(cos(rad), sin(rad))
    val tip = pivot + dir * armLengthPx
    if (hypot(point.x - tip.x, point.y - tip.y) <= slopPx * 1.35f) return true
    val rel = point - pivot
    val along = rel.x * dir.x + rel.y * dir.y
    if (along < -16f || along > armLengthPx) return false
    val perp = abs(rel.y * dir.x - rel.x * dir.y)
    return perp <= slopPx
}

@Composable
fun TurntablePlayerStyle(
    ctx: PlayerStageContext,
    modifier: Modifier = Modifier
) {
    val reducedMotion = OndaTheme.reducedMotion
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val state = ctx.playbackState
    val track = state.currentTrack
    val isPlaying = state.status == PlaybackStatus.PLAYING

    val durationMs = state.durationMs.coerceAtLeast(1L)
    val trackProgress = (state.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    var draggingTonearmProgress by remember { mutableStateOf<Float?>(null) }
    val activeTonearmProgress = draggingTonearmProgress ?: trackProgress

    var discAngleDeg by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying, reducedMotion) {
        if (!isPlaying || reducedMotion) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = (frameNanos - lastNanos).coerceIn(0L, 50_000_000L) / 1_000_000_000f
                    discAngleDeg = (discAngleDeg + dt * 42f) % 360f
                }
                lastNanos = frameNanos
            }
        }
    }

    val held = remember { Animatable(0f) }
    val peek = remember { Animatable(0f) }
    val peekGrow = remember { Animatable(0f) }

    LaunchedEffect(isPlaying, track?.id, reducedMotion) {
        if (!isPlaying || track == null || reducedMotion) {
            if (reducedMotion) {
                peek.snapTo(0f)
                peekGrow.snapTo(0f)
            }
            return@LaunchedEffect
        }
        if (peek.value < 0.05f) {
            peekGrow.snapTo(0f)
        }
        launch { peek.animateTo(1f, tween(240)) }
        peekGrow.animateTo(1f, tween(760, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)))
        delay(2_240L)
        peek.animateTo(0f, tween(420))
    }

    val shadowBlurEffect: RenderEffect? = remember(density) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurPx = with(density) { 5.dp.toPx() }
            android.graphics.RenderEffect.createBlurEffect(
                blurPx,
                blurPx,
                android.graphics.Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        } else {
            null
        }
    }

    val measurer = rememberTextMeasurer(cacheSize = 16)
    val arcLabelStyle = remember {
        TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = Color.White
        )
    }

    val tonearmAngleDeg = if (isPlaying || draggingTonearmProgress != null) {
        lerp(TURNTABLE_REST_ANGLE, TURNTABLE_END_ANGLE, activeTonearmProgress)
    } else {
        TURNTABLE_PARK_ANGLE
    }
    val currentTonearmAngle by rememberUpdatedState(tonearmAngleDeg)
    val currentDurationMs by rememberUpdatedState(state.durationMs)
    val currentHaptics by rememberUpdatedState(ctx.settings.hapticsEnabled)
    val currentReducedMotion by rememberUpdatedState(reducedMotion)
    val currentOnSeekToFraction by rememberUpdatedState(ctx.onSeekToFraction)

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                val discSize = minOf(maxWidth, maxHeight) * 0.86f
                Box(
                    modifier = Modifier
                        .size(discSize)
                        .graphicsLayer {
                            rotationZ = discAngleDeg
                        }
                        .clip(CircleShape)
                        .background(Color(0xFF141414)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width * 0.5f, size.height * 0.5f)
                        val maxR = size.minDimension * 0.49f
                        for (ring in 1..10) {
                            val r = maxR * (0.44f + ring * 0.052f)
                            drawCircle(
                                color = Color.White.copy(alpha = if (ring % 2 == 0) 0.09f else 0.04f),
                                radius = r,
                                center = center,
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.14f),
                                    Color.Transparent,
                                    Color(0xFF9E9E9E).copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            ),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = maxR * 0.45f)
                        )
                    }

                    TrackArtworkImage(
                        artworkUrl = track?.artworkUrl,
                        contentDescription = track?.title,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxSize(0.44f)
                    )

                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF000000))
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            val slopPx = with(density) { 28.dp.toPx() }
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val pivot = Offset(size.width * TURNTABLE_PIVOT_X, size.height * TURNTABLE_PIVOT_Y)
                                val armLength = size.minDimension * 0.56f
                                if (!isOnTurntableArm(down.position, pivot, currentTonearmAngle, armLength, slopPx)) {
                                    return@awaitEachGesture
                                }
                                val dur = currentDurationMs.coerceAtLeast(1L)
                                down.consume()
                                if (currentHaptics) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                scope.launch {
                                    if (currentReducedMotion) held.snapTo(1f) else held.animateTo(1f, tween(180))
                                }

                                val initialRad = atan2(down.position.y - pivot.y, down.position.x - pivot.x)
                                val initialDeg = Math.toDegrees(initialRad.toDouble()).toFloat()
                                val initialNorm = ((initialDeg - TURNTABLE_REST_ANGLE) / (TURNTABLE_END_ANGLE - TURNTABLE_REST_ANGLE)).coerceIn(0f, 1f)
                                draggingTonearmProgress = initialNorm
                                var lastTick = (dur * initialNorm).toLong() / 10_000L

                                val finishedDrag = drag(down.id) { change ->
                                    change.consume()
                                    val rad = atan2(change.position.y - pivot.y, change.position.x - pivot.x)
                                    val deg = Math.toDegrees(rad.toDouble()).toFloat()
                                    val normalized = ((deg - TURNTABLE_REST_ANGLE) / (TURNTABLE_END_ANGLE - TURNTABLE_REST_ANGLE)).coerceIn(0f, 1f)
                                    draggingTonearmProgress = normalized
                                    val ms = (dur * normalized).toLong()
                                    val tick = ms / 10_000L
                                    if (tick != lastTick) {
                                        lastTick = tick
                                        if (currentHaptics) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                }

                                if (finishedDrag) {
                                    draggingTonearmProgress?.let(currentOnSeekToFraction)
                                }
                                draggingTonearmProgress = null
                                scope.launch {
                                    if (!currentReducedMotion) {
                                        delay(450L)
                                        held.animateTo(0f, tween(320))
                                    } else {
                                        held.snapTo(0f)
                                    }
                                }
                            }
                        }
                ) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val lift = held.value
                                val shift = size.width * (0.012f + 0.024f * lift)
                                translationX = shift * 0.55f
                                translationY = shift * 1.1f
                                alpha = (if (shadowBlurEffect != null) 0.72f else 0.36f) - 0.18f * lift
                                renderEffect = shadowBlurEffect
                            }
                            .drawBehind {
                                drawTurntableArmGeometry(
                                    pivot = Offset(size.width * TURNTABLE_PIVOT_X, size.height * TURNTABLE_PIVOT_Y),
                                    armLength = size.minDimension * 0.56f,
                                    angleDeg = tonearmAngleDeg,
                                    silhouette = true
                                )
                            }
                    )

                    Spacer(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (!reducedMotion) {
                                    val s = 1f + 0.016f * held.value
                                    scaleX = s
                                    scaleY = s
                                    transformOrigin = TransformOrigin(TURNTABLE_PIVOT_X, TURNTABLE_PIVOT_Y)
                                }
                            }
                            .drawBehind {
                                drawTurntableArmGeometry(
                                    pivot = Offset(size.width * TURNTABLE_PIVOT_X, size.height * TURNTABLE_PIVOT_Y),
                                    armLength = size.minDimension * 0.56f,
                                    angleDeg = tonearmAngleDeg,
                                    silhouette = false
                                )
                            }
                    )

                    Spacer(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val lift = maxOf(held.value, peek.value)
                                if (lift > 0.001f) {
                                    val grow = if (held.value >= peek.value) 1f else peekGrow.value
                                    drawTurntableTimeArc(
                                        pivot = Offset(size.width * TURNTABLE_PIVOT_X, size.height * TURNTABLE_PIVOT_Y),
                                        arcRadius = size.minDimension * 0.58f,
                                        progress = activeTonearmProgress,
                                        alpha = lift,
                                        grow = grow,
                                        positionMs = (durationMs * activeTonearmProgress).toLong(),
                                        durationMs = state.durationMs,
                                        measurer = measurer,
                                        labelStyle = arcLabelStyle
                                    )
                                }
                            }
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlassTokens.RadiusXl))
                .background(Color(0xFF141414))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TrackMetaHeader(
                track = track,
                liked = ctx.liked,
                hapticsEnabled = ctx.settings.hapticsEnabled,
                onToggleLike = ctx.onLikeFromButton ?: ctx.onToggleLike,
                onLikePositioned = ctx.onLikePositioned,
                downloadItem = ctx.downloadItem,
                onDownloadClick = ctx.onDownloadClick,
                likeBurstState = ctx.likeBurstState,
                likeSlot = OndaLikeSlot.PLAYER
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OdometerText(
                    text = formatDurationMs((durationMs * activeTonearmProgress).toLong()),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
                Text(
                    text = "Drag tonearm to seek",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8A8A8A)
                )
                Text(
                    text = formatDurationMs(state.durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF9E9E9E)
                )
            }

            PlayerTransportControls(
                playbackState = state,
                hapticsEnabled = ctx.settings.hapticsEnabled,
                onToggleShuffle = ctx.onToggleShuffle,
                onSkipPrevious = ctx.onSkipPrevious,
                onTogglePlayPause = ctx.onTogglePlayPause,
                onSkipNext = ctx.onSkipNext,
                onToggleRepeat = ctx.onToggleRepeat
            )
        }
    }
}

private fun DrawScope.drawTurntableArmGeometry(
    pivot: Offset,
    armLength: Float,
    angleDeg: Float,
    silhouette: Boolean
) {
    val pivotColor = if (silhouette) Color.Black else Color(0xFF242424)
    val armColor = if (silhouette) Color.Black else Color.White.copy(alpha = 0.92f)
    val headColor = if (silhouette) Color.Black else Color.White

    drawCircle(
        color = pivotColor,
        radius = 18.dp.toPx(),
        center = pivot
    )

    rotate(degrees = angleDeg, pivot = pivot) {
        drawLine(
            color = armColor,
            start = Offset(pivot.x - 16.dp.toPx(), pivot.y),
            end = Offset(pivot.x + armLength, pivot.y),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = headColor,
            radius = 6.dp.toPx(),
            center = Offset(pivot.x + armLength, pivot.y)
        )
    }
}

private fun DrawScope.drawTurntableTimeArc(
    pivot: Offset,
    arcRadius: Float,
    progress: Float,
    alpha: Float,
    grow: Float,
    positionMs: Long,
    durationMs: Long,
    measurer: TextMeasurer,
    labelStyle: TextStyle
) {
    val totalSweep = TURNTABLE_END_ANGLE - TURNTABLE_REST_ANGLE
    val reachSweep = (totalSweep * grow.coerceIn(0f, 1f)).coerceAtLeast(0.01f)
    val activeSweep = (totalSweep * progress.coerceIn(0f, 1f)).coerceIn(0.01f, reachSweep)
    val topLeft = pivot - Offset(arcRadius, arcRadius)
    val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
    val stroke = 4.5.dp.toPx()

    drawArc(
        color = Color.White.copy(alpha = 0.28f * alpha),
        startAngle = TURNTABLE_REST_ANGLE,
        sweepAngle = reachSweep,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = stroke, cap = StrokeCap.Round)
    )
    drawArc(
        color = Color.White.copy(alpha = 0.95f * alpha),
        startAngle = TURNTABLE_REST_ANGLE,
        sweepAngle = activeSweep,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = stroke, cap = StrokeCap.Round)
    )

    if (durationMs <= 0L) return

    drawArcTimeLabel(
        text = formatDurationMs(positionMs),
        pivot = pivot,
        angleDeg = TURNTABLE_REST_ANGLE - 5.5f,
        radius = arcRadius,
        alpha = alpha,
        measurer = measurer,
        style = labelStyle
    )

    val endLabelAlpha = alpha * ((grow - 0.7f) / 0.3f).coerceIn(0f, 1f)
    if (endLabelAlpha > 0.01f) {
        drawArcTimeLabel(
            text = formatDurationMs(durationMs),
            pivot = pivot,
            angleDeg = TURNTABLE_END_ANGLE + 5.5f,
            radius = arcRadius,
            alpha = endLabelAlpha,
            measurer = measurer,
            style = labelStyle
        )
    }
}

private fun DrawScope.drawArcTimeLabel(
    text: String,
    pivot: Offset,
    angleDeg: Float,
    radius: Float,
    alpha: Float,
    measurer: TextMeasurer,
    style: TextStyle
) {
    val layout = measurer.measure(text, style)
    val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
    val center = pivot + Offset(cos(rad) * radius, sin(rad) * radius)
    rotate(degrees = angleDeg - 90f, pivot = center) {
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(center.x - layout.size.width * 0.5f, center.y - layout.size.height * 0.5f),
            alpha = alpha
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerStyle(
    ctx: PlayerStageContext,
    modifier: Modifier = Modifier
) {
    val state = ctx.playbackState
    val track = state.currentTrack
    val player = ctx.activePlayer

    var hasVideoFrames by remember(player, track?.id) {
        val currentSize = player?.videoSize
        mutableStateOf(currentSize != null && currentSize.width > 0 && currentSize.height > 0)
    }

    DisposableEffect(player) {
        if (player == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    hasVideoFrames = videoSize.width > 0 && videoSize.height > 0
                }
            }
            val initial = player.videoSize
            hasVideoFrames = initial.width > 0 && initial.height > 0
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(GlassTokens.RadiusLg))
                .background(Color(0xFF141414)),
            contentAlignment = Alignment.Center
        ) {
            TrackArtworkImage(
                artworkUrl = track?.artworkUrl,
                contentDescription = track?.title,
                shape = RoundedCornerShape(GlassTokens.RadiusLg),
                modifier = Modifier.fillMaxSize()
            )

            if (player != null) {
                AndroidView(
                    factory = { context ->
                        PlayerView(context).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            this.player = player
                        }
                    },
                    update = { playerView ->
                        if (playerView.player !== player) {
                            playerView.player = player
                        }
                    },
                    onRelease = { playerView ->
                        playerView.player = null
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = if (hasVideoFrames) 1f else 0f
                        }
                )
            }

            AnimatedVisibility(
                visible = !hasVideoFrames,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Text(
                    text = "Audio Fallback",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                        .background(Color(0xCC141414))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlassTokens.RadiusLg))
                .background(Color(0xFF141414))
        ) {
            LyricsPane(
                lyrics = ctx.lyrics,
                positionMs = state.positionMs,
                lyricsOffsetMs = ctx.lyricsOffsetMs,
                isPlaying = state.status == PlaybackStatus.PLAYING,
                isLoading = ctx.isLyricsLoading,
                errorMessage = ctx.lyricsError,
                lyricsMotionEnabled = ctx.settings.lyricsMotionEnabled,
                lyricsFontScale = ctx.settings.lyricsFontScale,
                hapticsEnabled = ctx.settings.hapticsEnabled,
                onSeekToMs = ctx.onSeekToMs,
                onRefreshLyrics = ctx.onRefreshLyrics,
                onAdjustOffsetMs = ctx.onAdjustLyricsOffset,
                centered = false,
                showControls = true
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlassTokens.RadiusXl))
                .background(Color(0xFF141414))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TrackMetaHeader(
                track = track,
                liked = ctx.liked,
                hapticsEnabled = ctx.settings.hapticsEnabled,
                onToggleLike = ctx.onLikeFromButton ?: ctx.onToggleLike,
                onLikePositioned = ctx.onLikePositioned,
                downloadItem = ctx.downloadItem,
                onDownloadClick = ctx.onDownloadClick,
                likeBurstState = ctx.likeBurstState,
                likeSlot = OndaLikeSlot.PLAYER
            )
            PlayerSeekSection(
                playbackState = state,
                onSeekToFraction = ctx.onSeekToFraction,
                accentColor = Color.White
            )
            PlayerTransportControls(
                playbackState = state,
                hapticsEnabled = ctx.settings.hapticsEnabled,
                onToggleShuffle = ctx.onToggleShuffle,
                onSkipPrevious = ctx.onSkipPrevious,
                onTogglePlayPause = ctx.onTogglePlayPause,
                onSkipNext = ctx.onSkipNext,
                onToggleRepeat = ctx.onToggleRepeat,
                compact = true
            )
        }
    }
}

@Composable
fun IpodPlayerStyle(
    ctx: PlayerStageContext,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val reducedMotion = OndaTheme.reducedMotion
    val state = ctx.playbackState
    val track = state.currentTrack
    val displayTracks = remember(state.queue, track) {
        when {
            state.queue.isNotEmpty() -> state.queue
            track != null -> listOf(track)
            else -> emptyList()
        }
    }
    val safeIndex = state.currentIndex.coerceIn(0, (displayTracks.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = safeIndex,
        pageCount = { displayTracks.size.coerceAtLeast(1) }
    )

    LaunchedEffect(safeIndex, displayTracks.size) {
        if (displayTracks.isNotEmpty() && pagerState.currentPage != safeIndex && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(safeIndex)
        }
    }

    LaunchedEffect(pagerState, displayTracks.size) {
        snapshotFlow { pagerState.settledPage to pagerState.isScrollInProgress }
            .filter { (_, scrolling) -> !scrolling }
            .distinctUntilChanged()
            .collect { (settled, _) ->
                if (displayTracks.isNotEmpty() && settled != safeIndex && settled in displayTracks.indices) {
                    ctx.onSelectQueueIndex(settled)
                }
            }
    }

    var wheelScrubFraction by remember { mutableStateOf<Float?>(null) }
    val durationMs = state.durationMs.coerceAtLeast(1L)
    val baseProgress = (state.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    val shownProgress = wheelScrubFraction ?: baseProgress

    val centerInteraction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlassTokens.RadiusXl))
                .background(Color(0xFF141414))
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ONDA POD",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = if (displayTracks.isNotEmpty()) "${safeIndex + 1} / ${displayTracks.size}" else "0 / 0",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9E9E9E)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (ctx.showInlineLyrics) {
                    LyricsPane(
                        lyrics = ctx.lyrics,
                        positionMs = state.positionMs,
                        lyricsOffsetMs = ctx.lyricsOffsetMs,
                        isPlaying = state.status == PlaybackStatus.PLAYING,
                        isLoading = ctx.isLyricsLoading,
                        errorMessage = ctx.lyricsError,
                        lyricsMotionEnabled = ctx.settings.lyricsMotionEnabled,
                        lyricsFontScale = ctx.settings.lyricsFontScale,
                        hapticsEnabled = ctx.settings.hapticsEnabled,
                        onSeekToMs = ctx.onSeekToMs,
                        onRefreshLyrics = ctx.onRefreshLyrics,
                        onAdjustOffsetMs = ctx.onAdjustLyricsOffset,
                        showControls = false
                    )
                } else if (displayTracks.isNotEmpty()) {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 56.dp),
                        pageSpacing = 8.dp,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val item = displayTracks[page]
                        val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                        val absOff = abs(offset).coerceIn(0f, 1.5f)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 12.dp)
                                .graphicsLayer {
                                    rotationY = offset.coerceIn(-1f, 1f) * 34f
                                    scaleX = lerp(1f, 0.78f, absOff)
                                    scaleY = lerp(1f, 0.78f, absOff)
                                    alpha = lerp(1f, 0.55f, absOff)
                                    cameraDistance = 14f * density
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            TrackArtworkImage(
                                artworkUrl = item.artworkUrl,
                                contentDescription = item.title,
                                shape = RoundedCornerShape(GlassTokens.RadiusMd),
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .fillMaxSize()
                            )
                        }
                    }
                } else {
                    TrackArtworkImage(
                        artworkUrl = null,
                        contentDescription = null,
                        modifier = Modifier.size(140.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = track?.title ?: "Nothing playing",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track?.artist ?: "Select a track",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9E9E9E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                        .background(Color.White.copy(alpha = 0.14f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(shownProgress)
                            .height(6.dp)
                            .background(Color.White)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OdometerText(
                        text = formatDurationMs((durationMs * shownProgress).toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF9E9E9E)
                    )
                    Text(
                        text = formatDurationMs(state.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8A8A8A)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .size(228.dp)
                .clip(CircleShape)
                .background(Color(0xFF141414))
                .pointerInput(durationMs, ctx.settings.hapticsEnabled) {
                    var lastAngleRad = 0f
                    var workingFraction = baseProgress
                    var accumulatedForHaptic = 0f
                    androidx.compose.foundation.gestures.detectDragGestures(
                        onDragStart = { start ->
                            val cx = size.width * 0.5f
                            val cy = size.height * 0.5f
                            lastAngleRad = atan2(start.y - cy, start.x - cx)
                            workingFraction = wheelScrubFraction ?: baseProgress
                            accumulatedForHaptic = 0f
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val cx = size.width * 0.5f
                            val cy = size.height * 0.5f
                            val currentAngle = atan2(change.position.y - cy, change.position.x - cx)
                            var delta = currentAngle - lastAngleRad
                            if (delta > PI.toFloat()) delta -= (2f * PI.toFloat())
                            if (delta < -PI.toFloat()) delta += (2f * PI.toFloat())
                            lastAngleRad = currentAngle

                            workingFraction = (workingFraction + (delta / (2f * PI.toFloat())) * 0.35f).coerceIn(0f, 1f)
                            wheelScrubFraction = workingFraction
                            accumulatedForHaptic += abs(delta)
                            if (accumulatedForHaptic >= 0.45f) {
                                accumulatedForHaptic = 0f
                                if (ctx.settings.hapticsEnabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        },
                        onDragEnd = {
                            wheelScrubFraction?.let(ctx.onSeekToFraction)
                            wheelScrubFraction = null
                        },
                        onDragCancel = {
                            wheelScrubFraction = null
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = ctx.onOpenStylePicker,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
            ) {
                Text(
                    text = "MENU",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            IconButton(
                onClick = ctx.onSkipPrevious,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
                    .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White
                )
            }

            IconButton(
                onClick = ctx.onSkipNext,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp)
                    .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White
                )
            }

            IconButton(
                onClick = ctx.onTogglePlayPause,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
            ) {
                Icon(
                    imageVector = if (state.status == PlaybackStatus.PLAYING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Play or pause",
                    tint = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .size(78.dp)
                    .playerPressBounce(
                        interactionSource = centerInteraction,
                        pressedScale = 0.88f,
                        reducedMotion = reducedMotion
                    )
                    .clip(CircleShape)
                    .background(Color(0xFF242424))
                    .clickable(
                        interactionSource = centerInteraction,
                        indication = null
                    ) {
                        if (ctx.settings.hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        ctx.onToggleInlineLyrics()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (ctx.showInlineLyrics) Icons.Rounded.Style else Icons.Rounded.Lyrics,
                    contentDescription = "Toggle lyrics",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
