package com.onda.music.feature.player.styles

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
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
import com.onda.music.core.design.OndaMonogramMark
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.SmoothCornerShape
import com.onda.music.core.model.IpodFinish
import com.onda.music.core.model.PlaybackStatus
import com.onda.music.feature.player.LyricsPane
import com.onda.music.feature.player.OndaLikeSlot
import com.onda.music.feature.player.PlayerSeekSection
import com.onda.music.feature.player.PlayerTransportControls
import com.onda.music.feature.player.TrackArtworkImage
import com.onda.music.feature.player.TrackMetaHeader
import com.onda.music.feature.player.formatDurationMs
import kotlinx.coroutines.Job
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

private const val DIAL_DETENT_RAD = (PI / 12.0).toFloat()
private const val DIAL_CENTER_RADIUS_FRACTION = 0.46f
private const val DIAL_ACCEL_WINDOW_MS = 140L

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
                                val armLength = minOf(size.width, size.height).toFloat() * 0.56f
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

private enum class CinemaDrawerTab {
    LYRICS,
    UP_NEXT
}

private enum class CinemaSeekSide {
    LEFT,
    RIGHT
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerStyle(
    ctx: PlayerStageContext,
    modifier: Modifier = Modifier
) {
    val reducedMotion = OndaTheme.reducedMotion
    val palette = OndaTheme.palette
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val state = ctx.playbackState
    val track = state.currentTrack
    val player = ctx.activePlayer
    val isPlaying = state.status == PlaybackStatus.PLAYING
    val durationMs = state.durationMs.coerceAtLeast(1L)

    val displayTracks = remember(state.queue, track) {
        when {
            state.queue.isNotEmpty() -> state.queue
            track != null -> listOf(track)
            else -> emptyList()
        }
    }
    val safeIndex = state.currentIndex.coerceIn(0, (displayTracks.size - 1).coerceAtLeast(0))

    var smartFillEnabled by remember(ctx.settings.studioConfig.videoSmartFill) {
        mutableStateOf(ctx.settings.studioConfig.videoSmartFill)
    }
    val ambientGlowEnabled = ctx.settings.studioConfig.videoAmbientGlow

    var hudVisible by remember { mutableStateOf(true) }
    var showCinemaDrawer by remember { mutableStateOf(true) }
    var activeDrawerTab by remember { mutableStateOf(CinemaDrawerTab.LYRICS) }

    var seekOverlaySide by remember { mutableStateOf<CinemaSeekSide?>(null) }
    var seekAccumulatedSec by remember { mutableIntStateOf(0) }
    var seekBurstToken by remember { mutableIntStateOf(0) }
    var pendingSeekTargetMs by remember { mutableStateOf<Long?>(null) }
    val seekRippleAnim = remember { Animatable(0f) }

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

    LaunchedEffect(isPlaying, hudVisible, showCinemaDrawer) {
        if (isPlaying && hudVisible && !showCinemaDrawer) {
            delay(3_500L)
            hudVisible = false
        }
    }

    LaunchedEffect(seekBurstToken) {
        if (seekBurstToken == 0) return@LaunchedEffect
        if (reducedMotion) {
            seekRippleAnim.snapTo(1f)
        } else {
            seekRippleAnim.snapTo(0f)
            seekRippleAnim.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
        }
        delay(900L)
        seekOverlaySide = null
        seekAccumulatedSec = 0
        pendingSeekTargetMs = null
    }

    val currentPositionMs by rememberUpdatedState(state.positionMs)
    val currentDurationMs by rememberUpdatedState(durationMs)
    val currentHaptics by rememberUpdatedState(ctx.settings.hapticsEnabled)
    val currentOnSeekToMs by rememberUpdatedState(ctx.onSeekToMs)
    val currentOnTogglePlayPause by rememberUpdatedState(ctx.onTogglePlayPause)

    val glowAlpha by animateFloatAsState(
        targetValue = if (!ambientGlowEnabled) 0f else if (isPlaying) 0.34f else 0.18f,
        animationSpec = if (reducedMotion) snap() else tween(650),
        label = "CinemaAmbientGlow"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                if (glowAlpha > 0.01f) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                palette.primary.copy(alpha = glowAlpha),
                                palette.secondary.copy(alpha = glowAlpha * 0.45f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.28f),
                            radius = size.maxDimension * 0.72f
                        )
                    )
                }
            }
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val wideCinema = maxWidth >= 600.dp

            val cinemaStage: @Composable (Modifier) -> Unit = { stageModifier ->
                val frameShape = SmoothCornerShape(GlassTokens.RadiusLg)
                Box(
                    modifier = stageModifier
                        .clip(frameShape)
                        .background(Color(0xFF0A0A0D))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), frameShape)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    hudVisible = !hudVisible
                                },
                                onDoubleTap = { offset ->
                                    val w = size.width.coerceAtLeast(1).toFloat()
                                    val fracX = offset.x / w
                                    when {
                                        fracX < 0.35f -> {
                                            val baseMs = pendingSeekTargetMs ?: currentPositionMs
                                            val targetMs = (baseMs - 10_000L).coerceIn(0L, currentDurationMs)
                                            pendingSeekTargetMs = targetMs
                                            currentOnSeekToMs(targetMs)
                                            seekAccumulatedSec = if (seekOverlaySide == CinemaSeekSide.LEFT) {
                                                seekAccumulatedSec - 10
                                            } else {
                                                -10
                                            }
                                            seekOverlaySide = CinemaSeekSide.LEFT
                                            seekBurstToken += 1
                                            if (currentHaptics) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        }
                                        fracX > 0.65f -> {
                                            val baseMs = pendingSeekTargetMs ?: currentPositionMs
                                            val targetMs = (baseMs + 10_000L).coerceIn(0L, currentDurationMs)
                                            pendingSeekTargetMs = targetMs
                                            currentOnSeekToMs(targetMs)
                                            seekAccumulatedSec = if (seekOverlaySide == CinemaSeekSide.RIGHT) {
                                                seekAccumulatedSec + 10
                                            } else {
                                                10
                                            }
                                            seekOverlaySide = CinemaSeekSide.RIGHT
                                            seekBurstToken += 1
                                            if (currentHaptics) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        }
                                        else -> {
                                            if (currentHaptics) {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            currentOnTogglePlayPause()
                                        }
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (!hasVideoFrames) {
                        OndaAcousticStage(
                            artworkUrl = track?.artworkUrl,
                            title = track?.title,
                            isPlaying = isPlaying,
                            reducedMotion = reducedMotion,
                            primaryAccent = palette.primary,
                            secondaryAccent = palette.secondary,
                            smartFill = smartFillEnabled
                        )
                    }

                    if (player != null) {
                        AndroidView(
                            factory = { context ->
                                PlayerView(context).apply {
                                    useController = false
                                    resizeMode = if (smartFillEnabled) {
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    } else {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                                    visibility = if (hasVideoFrames) View.VISIBLE else View.INVISIBLE
                                    this.player = player
                                }
                            },
                            update = { playerView ->
                                if (playerView.player !== player) {
                                    playerView.player = player
                                }
                                val targetResize = if (smartFillEnabled) {
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                } else {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                                if (playerView.resizeMode != targetResize) {
                                    playerView.resizeMode = targetResize
                                }
                                val targetVisibility = if (hasVideoFrames) View.VISIBLE else View.INVISIBLE
                                if (playerView.visibility != targetVisibility) {
                                    playerView.visibility = targetVisibility
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

                    seekOverlaySide?.let { side ->
                        val isLeft = side == CinemaSeekSide.LEFT
                        val rippleFraction = seekRippleAnim.value
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawBehind {
                                    val centerX = if (isLeft) size.width * 0.22f else size.width * 0.78f
                                    val centerY = size.height * 0.5f
                                    val maxR = size.minDimension * 0.55f
                                    drawCircle(
                                        color = Color.White.copy(alpha = (1f - rippleFraction) * 0.24f),
                                        radius = maxR * (0.35f + 0.65f * rippleFraction),
                                        center = Offset(centerX, centerY)
                                    )
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .align(if (isLeft) Alignment.CenterStart else Alignment.CenterEnd)
                                    .padding(horizontal = 24.dp)
                                    .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                                    .background(Color(0xD9101014))
                                    .border(
                                        1.dp,
                                        Color.White.copy(alpha = 0.14f),
                                        RoundedCornerShape(GlassTokens.RadiusPill)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isLeft) Icons.Rounded.FastRewind else Icons.Rounded.FastForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (seekAccumulatedSec > 0) "+${seekAccumulatedSec}s" else "${seekAccumulatedSec}s",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = hudVisible,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(240)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.0f to Color(0x99000000),
                                            0.32f to Color.Transparent,
                                            0.68f to Color.Transparent,
                                            1.0f to Color(0xB3000000)
                                        )
                                    )
                                )
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                                        .background(Color(0xB8111115))
                                        .border(
                                            1.dp,
                                            Color.White.copy(alpha = 0.12f),
                                            RoundedCornerShape(GlassTokens.RadiusPill)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OndaMonogramMark(
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "ONDA CINEMA",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        ),
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .defaultMinSize(
                                            minWidth = DockMetrics.MinTapTarget,
                                            minHeight = DockMetrics.MinTapTarget
                                        )
                                        .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                                        .clickable {
                                            if (ctx.settings.hapticsEnabled) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            smartFillEnabled = !smartFillEnabled
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                                            .background(Color(0xCC16161B))
                                            .border(
                                                1.dp,
                                                Color.White.copy(alpha = 0.16f),
                                                RoundedCornerShape(GlassTokens.RadiusPill)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (smartFillEnabled) Icons.Rounded.AspectRatio else Icons.Rounded.FitScreen,
                                            contentDescription = "Toggle fill or fit",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (smartFillEnabled) "FILL" else "FIT",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter),
                                horizontalArrangement = if (hasVideoFrames) Arrangement.SpaceBetween else Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (hasVideoFrames) {
                                    Text(
                                        text = "VIDEO STREAM",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 0.7.sp
                                        ),
                                        color = Color.White.copy(alpha = 0.86f),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                                            .background(Color(0xB8111115))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                Text(
                                    text = "${formatDurationMs(state.positionMs)} / ${formatDurationMs(state.durationMs)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = Color.White.copy(alpha = 0.88f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                                        .background(Color(0xB8111115))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            val cinemaPanel: @Composable (Modifier) -> Unit = { panelModifier ->
                val queueListState = rememberLazyListState()
                LaunchedEffect(activeDrawerTab, safeIndex, displayTracks.size) {
                    if (activeDrawerTab == CinemaDrawerTab.UP_NEXT && displayTracks.isNotEmpty() && safeIndex in displayTracks.indices) {
                        if (reducedMotion) {
                            queueListState.scrollToItem(safeIndex)
                        } else {
                            queueListState.animateScrollToItem(safeIndex)
                        }
                    }
                }

                Column(
                    modifier = panelModifier
                        .clip(RoundedCornerShape(GlassTokens.RadiusLg))
                        .background(Color(0xFF141414))
                        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(GlassTokens.RadiusLg))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CinemaPanelTabChip(
                                label = "Lyrics",
                                selected = activeDrawerTab == CinemaDrawerTab.LYRICS,
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Lyrics,
                                        contentDescription = null,
                                        tint = if (activeDrawerTab == CinemaDrawerTab.LYRICS) Color.Black else Color(0xFF9E9E9E),
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                onClick = { activeDrawerTab = CinemaDrawerTab.LYRICS }
                            )
                            CinemaPanelTabChip(
                                label = "Up Next (${displayTracks.size})",
                                selected = activeDrawerTab == CinemaDrawerTab.UP_NEXT,
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.QueueMusic,
                                        contentDescription = null,
                                        tint = if (activeDrawerTab == CinemaDrawerTab.UP_NEXT) Color.Black else Color(0xFF9E9E9E),
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                onClick = { activeDrawerTab = CinemaDrawerTab.UP_NEXT }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .defaultMinSize(
                                    minWidth = DockMetrics.MinTapTarget,
                                    minHeight = DockMetrics.MinTapTarget
                                )
                                .clip(CircleShape)
                                .clickable { showCinemaDrawer = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ExpandMore,
                                contentDescription = "Collapse panel",
                                tint = Color(0xFF9E9E9E),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (activeDrawerTab == CinemaDrawerTab.LYRICS) {
                            LyricsPane(
                                lyrics = ctx.lyrics,
                                positionMs = state.positionMs,
                                lyricsOffsetMs = ctx.lyricsOffsetMs,
                                isPlaying = isPlaying,
                                isLoading = ctx.isLyricsLoading,
                                errorMessage = ctx.lyricsError,
                                lyricsMotionEnabled = ctx.settings.lyricsMotionEnabled,
                                lyricsFontScale = ctx.settings.lyricsFontScale,
                                hapticsEnabled = ctx.settings.hapticsEnabled,
                                onSeekToMs = ctx.onSeekToMs,
                                onRefreshLyrics = ctx.onRefreshLyrics,
                                onAdjustOffsetMs = ctx.onAdjustLyricsOffset,
                                centered = false,
                                showControls = true,
                                playbackSpeed = state.playbackSpeed,
                                trackKey = track?.id
                            )
                        } else {
                            LazyColumn(
                                state = queueListState,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(
                                    items = displayTracks,
                                    key = { index, item -> "${item.id}_$index" }
                                ) { index, item ->
                                    val isCurrent = index == safeIndex
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                                            .clip(RoundedCornerShape(GlassTokens.RadiusSm))
                                            .background(if (isCurrent) Color(0xFF242428) else Color(0xFF1A1A1D))
                                            .clickable {
                                                if (ctx.settings.hapticsEnabled) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                                ctx.onSelectQueueIndex(index)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        TrackArtworkImage(
                                            artworkUrl = item.artworkUrl,
                                            contentDescription = item.title,
                                            shape = RoundedCornerShape(GlassTokens.RadiusXs),
                                            modifier = Modifier.size(38.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                                ),
                                                color = if (isCurrent) Color.White else Color(0xFFD0D0D4),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = item.artist,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF8A8A8A),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = if (isCurrent) "ACTIVE" else formatDurationMs(item.durationMs),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = if (isCurrent) palette.primary else Color(0xFF8A8A8A)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val bottomTransportCard: @Composable (Modifier) -> Unit = { cardModifier ->
                Column(
                    modifier = cardModifier
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

            if (wideCinema) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1.3f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            cinemaStage(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                            )
                        }
                        if (showCinemaDrawer) {
                            cinemaPanel(
                                Modifier
                                    .weight(0.95f)
                                    .fillMaxHeight()
                            )
                        }
                    }

                    if (!showCinemaDrawer) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CinemaPanelToggleBar(
                                onOpenLyrics = {
                                    activeDrawerTab = CinemaDrawerTab.LYRICS
                                    showCinemaDrawer = true
                                },
                                onOpenUpNext = {
                                    activeDrawerTab = CinemaDrawerTab.UP_NEXT
                                    showCinemaDrawer = true
                                }
                            )
                        }
                    }

                    bottomTransportCard(Modifier)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (showCinemaDrawer) {
                        cinemaStage(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        )
                        cinemaPanel(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            cinemaStage(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                            )
                        }
                        CinemaPanelToggleBar(
                            onOpenLyrics = {
                                activeDrawerTab = CinemaDrawerTab.LYRICS
                                showCinemaDrawer = true
                            },
                            onOpenUpNext = {
                                activeDrawerTab = CinemaDrawerTab.UP_NEXT
                                showCinemaDrawer = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    bottomTransportCard(Modifier)
                }
            }
        }
    }
}

@Composable
private fun CinemaPanelTabChip(
    label: String,
    selected: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                .background(if (selected) Color.White else Color(0xFF222226))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (selected) Color.Black else Color(0xFFB4B4BC),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CinemaPanelToggleBar(
    onOpenLyrics: () -> Unit,
    onOpenUpNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(GlassTokens.RadiusLg))
            .background(Color(0xFF141414))
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CinemaPanelTabChip(
                label = "Lyrics",
                selected = false,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = null,
                        tint = Color(0xFF9E9E9E),
                        modifier = Modifier.size(14.dp)
                    )
                },
                onClick = onOpenLyrics
            )
            CinemaPanelTabChip(
                label = "Up Next",
                selected = false,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = Color(0xFF9E9E9E),
                        modifier = Modifier.size(14.dp)
                    )
                },
                onClick = onOpenUpNext
            )
        }
        Row(
            modifier = Modifier
                .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                .clickable(onClick = onOpenLyrics)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Panel",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF9E9E9E)
            )
            Icon(
                imageVector = Icons.Rounded.ExpandLess,
                contentDescription = "Open panel",
                tint = Color(0xFF9E9E9E),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun OndaAcousticStage(
    artworkUrl: String?,
    title: String?,
    isPlaying: Boolean,
    reducedMotion: Boolean,
    primaryAccent: Color,
    secondaryAccent: Color,
    smartFill: Boolean
) {
    var pulsePhase by remember { mutableFloatStateOf(0.32f) }
    LaunchedEffect(isPlaying, reducedMotion) {
        if (!isPlaying || reducedMotion) {
            pulsePhase = 0.32f
            return@LaunchedEffect
        }
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = (frameNanos - lastNanos).coerceIn(0L, 50_000_000L) / 1_000_000_000f
                    pulsePhase = (pulsePhase + dt * 0.28f) % 1f
                }
                lastNanos = frameNanos
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        TrackArtworkImage(
            artworkUrl = artworkUrl,
            contentDescription = null,
            shape = RectangleShape,
            contentScale = if (smartFill) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.22f }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            primaryAccent.copy(alpha = 0.16f),
                            Color(0xD90A0A0E),
                            Color(0xF5070709)
                        )
                    )
                )
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.5f)
            val minR = size.minDimension * 0.24f
            val maxR = size.maxDimension * 0.56f
            val ringCount = 4
            for (i in 0 until ringCount) {
                val offsetPhase = (pulsePhase + i.toFloat() / ringCount) % 1f
                val radius = lerp(minR, maxR, offsetPhase)
                val ringAlpha = (1f - offsetPhase).coerceIn(0f, 1f) * if (isPlaying) 0.28f else 0.14f
                val ringColor = if (i % 2 == 0) primaryAccent else secondaryAccent
                drawCircle(
                    color = ringColor.copy(alpha = ringAlpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1.4.dp.toPx())
                )
            }
        }

        TrackArtworkImage(
            artworkUrl = artworkUrl,
            contentDescription = title,
            shape = SmoothCornerShape(GlassTokens.RadiusMd),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxHeight(0.64f)
                .aspectRatio(1f)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.18f),
                    shape = SmoothCornerShape(GlassTokens.RadiusMd)
                )
        )

        Text(
            text = "AUDIO STREAM",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.8.sp
            ),
            color = Color.White.copy(alpha = 0.90f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                .background(Color(0xCC111115))
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.12f),
                    RoundedCornerShape(GlassTokens.RadiusPill)
                )
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private enum class DialScreenMode(val badge: String) {
    STAGE("STAGE"),
    NOW_PLAYING("PLAYING"),
    LYRICS("LYRICS")
}

private enum class DialQuadrant(val index: Int) {
    MENU(0),
    NEXT(1),
    PLAY(2),
    PREV(3)
}

@Immutable
private data class DialFinishPalette(
    val shellTop: Color,
    val shellBottom: Color,
    val shellBorder: Color,
    val bezelFrame: Color,
    val wheelSurface: Color,
    val wheelBorder: Color,
    val wheelGlyph: Color,
    val centerButton: Color,
    val centerBorder: Color,
    val centerGlyph: Color,
    val sectorFlash: Color,
    val screenAccent: Color
)

private fun resolveDialFinishPalette(finish: IpodFinish): DialFinishPalette {
    return when (finish) {
        IpodFinish.SILVER -> DialFinishPalette(
            shellTop = Color(0xFFD2D6DC),
            shellBottom = Color(0xFFC8CCD2),
            shellBorder = Color(0xFFE7EAEE),
            bezelFrame = Color(0xFF141518),
            wheelSurface = Color(0xFFECEFF4),
            wheelBorder = Color(0xFFD5D9E0),
            wheelGlyph = Color(0xFF1C1C1E),
            centerButton = Color(0xFF1C1C1E),
            centerBorder = Color(0xFF3A3A3F),
            centerGlyph = Color(0xFFECEFF4),
            sectorFlash = Color(0xFF1C1C1E),
            screenAccent = Color(0xFFECEFF4)
        )
        IpodFinish.CRIMSON -> DialFinishPalette(
            shellTop = Color(0xFF2D141B),
            shellBottom = Color(0xFF261116),
            shellBorder = Color(0xFF52232E),
            bezelFrame = Color(0xFF12090B),
            wheelSurface = Color(0xFF3A1720),
            wheelBorder = Color(0xFF592533),
            wheelGlyph = Color(0xFFF8E8EB),
            centerButton = Color(0xFF1D0B10),
            centerBorder = Color(0xFF662B3A),
            centerGlyph = Color(0xFFF8E8EB),
            sectorFlash = Color(0xFFF8E8EB),
            screenAccent = Color(0xFFF49BB0)
        )
        IpodFinish.MIDNIGHT -> DialFinishPalette(
            shellTop = Color(0xFF131D30),
            shellBottom = Color(0xFF0E1624),
            shellBorder = Color(0xFF273957),
            bezelFrame = Color(0xFF090E17),
            wheelSurface = Color(0xFF172236),
            wheelBorder = Color(0xFF2B3E5E),
            wheelGlyph = Color(0xFFE2ECFC),
            centerButton = Color(0xFF0C121E),
            centerBorder = Color(0xFF31476B),
            centerGlyph = Color(0xFFE2ECFC),
            sectorFlash = Color(0xFF9EC2FF),
            screenAccent = Color(0xFF8FB8FF)
        )
        IpodFinish.OBSIDIAN -> DialFinishPalette(
            shellTop = Color(0xFF1A1A1F),
            shellBottom = Color(0xFF151518),
            shellBorder = Color(0xFF2D2D34),
            bezelFrame = Color(0xFF0B0B0E),
            wheelSurface = Color(0xFF1F1F24),
            wheelBorder = Color(0xFF32323A),
            wheelGlyph = Color(0xFFF2F2F7),
            centerButton = Color(0xFF121216),
            centerBorder = Color(0xFF35353E),
            centerGlyph = Color(0xFFF2F2F7),
            sectorFlash = Color(0xFFFFFFFF),
            screenAccent = Color(0xFFFFFFFF)
        )
    }
}

private fun resolveDialVibrator(context: Context): Vibrator? {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }.getOrNull()
}

private fun triggerDialDetentTick(
    context: Context,
    vibrator: Vibrator?,
    haptics: HapticFeedback,
    hapticsEnabled: Boolean
) {
    if (!hapticsEnabled) return
    val triggeredPrimitive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator != null) {
        runCatching {
            val granted = context.checkSelfPermission(Manifest.permission.VIBRATE) == PackageManager.PERMISSION_GRANTED
            if (
                granted &&
                vibrator.hasVibrator() &&
                vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)
            ) {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.55f)
                    .compose()
                vibrator.vibrate(effect)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    } else {
        false
    }
    if (!triggeredPrimitive) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
}

private fun quadrantForAngleDeg(angleDeg: Float): DialQuadrant {
    return when {
        angleDeg >= -135f && angleDeg < -45f -> DialQuadrant.MENU
        angleDeg >= -45f && angleDeg < 45f -> DialQuadrant.NEXT
        angleDeg >= 45f && angleDeg < 135f -> DialQuadrant.PLAY
        else -> DialQuadrant.PREV
    }
}

@Composable
fun IpodPlayerStyle(
    ctx: PlayerStageContext,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val reducedMotion = OndaTheme.reducedMotion
    val scope = rememberCoroutineScope()
    val vibrator = remember(context) { resolveDialVibrator(context) }

    val state = ctx.playbackState
    val track = state.currentTrack
    val isPlaying = state.status == PlaybackStatus.PLAYING
    val finishPalette = remember(ctx.settings.studioConfig.ipodFinish) {
        resolveDialFinishPalette(ctx.settings.studioConfig.ipodFinish)
    }

    val displayTracks = remember(state.queue, track) {
        when {
            state.queue.isNotEmpty() -> state.queue
            track != null -> listOf(track)
            else -> emptyList()
        }
    }
    val safeIndex = state.currentIndex.coerceIn(0, (displayTracks.size - 1).coerceAtLeast(0))
    val currentSafeIndex by rememberUpdatedState(safeIndex)
    val pagerState = rememberPagerState(
        initialPage = safeIndex,
        pageCount = { displayTracks.size.coerceAtLeast(1) }
    )

    var dialMode by remember {
        mutableStateOf(if (ctx.showInlineLyrics) DialScreenMode.LYRICS else DialScreenMode.STAGE)
    }
    var isSleeveFlipped by remember { mutableStateOf(false) }
    var isScrubMode by remember { mutableStateOf(false) }
    var highlightedQueueIndex by remember { mutableIntStateOf(safeIndex) }
    var wheelScrubFraction by remember { mutableStateOf<Float?>(null) }

    val sleeveListState = rememberLazyListState()
    var coverTargetPage by remember { mutableIntStateOf(safeIndex) }
    var coverScrollJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(safeIndex) {
        highlightedQueueIndex = safeIndex
        coverTargetPage = safeIndex
    }

    LaunchedEffect(safeIndex, displayTracks.size) {
        if (displayTracks.isNotEmpty() && pagerState.currentPage != safeIndex && !pagerState.isScrollInProgress) {
            coverTargetPage = safeIndex
            if (reducedMotion) {
                pagerState.scrollToPage(safeIndex)
            } else {
                pagerState.animateScrollToPage(safeIndex)
            }
        }
    }

    LaunchedEffect(pagerState, displayTracks.size) {
        snapshotFlow { pagerState.settledPage to pagerState.isScrollInProgress }
            .filter { (_, scrolling) -> !scrolling }
            .distinctUntilChanged()
            .collect { (settled, _) ->
                coverTargetPage = settled
                if (displayTracks.isNotEmpty() && settled != currentSafeIndex && settled in displayTracks.indices) {
                    ctx.onSelectQueueIndex(settled)
                }
            }
    }

    LaunchedEffect(isSleeveFlipped, safeIndex) {
        if (isSleeveFlipped && displayTracks.isNotEmpty()) {
            highlightedQueueIndex = safeIndex
            sleeveListState.scrollToItem(safeIndex.coerceIn(0, displayTracks.lastIndex))
        }
    }

    val flipAngleDeg by animateFloatAsState(
        targetValue = if (isSleeveFlipped) 180f else 0f,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.86f, stiffness = 200f),
        label = "DialSleeveFlip"
    )

    val durationMs = state.durationMs.coerceAtLeast(1L)
    val baseProgress = (state.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    val shownProgress = wheelScrubFraction ?: baseProgress

    val sectorFlashMenu = remember { Animatable(0f) }
    val sectorFlashNext = remember { Animatable(0f) }
    val sectorFlashPlay = remember { Animatable(0f) }
    val sectorFlashPrev = remember { Animatable(0f) }
    val centerScale = remember { Animatable(1f) }

    val currentDialMode by rememberUpdatedState(dialMode)
    val currentSleeveFlipped by rememberUpdatedState(isSleeveFlipped)
    val currentScrubMode by rememberUpdatedState(isScrubMode)
    val currentReducedMotion by rememberUpdatedState(reducedMotion)
    val currentHapticsEnabled by rememberUpdatedState(ctx.settings.hapticsEnabled)
    val currentDurationMs by rememberUpdatedState(durationMs)
    val currentPositionMs by rememberUpdatedState(state.positionMs)
    val currentBaseProgress by rememberUpdatedState(baseProgress)
    val currentTrackCount by rememberUpdatedState(displayTracks.size)
    val currentHighlightedIndex by rememberUpdatedState(highlightedQueueIndex)
    val currentCoverPage by rememberUpdatedState(coverTargetPage)
    val currentOnSelectQueueIndex by rememberUpdatedState(ctx.onSelectQueueIndex)
    val currentOnTogglePlayPause by rememberUpdatedState(ctx.onTogglePlayPause)
    val currentOnSkipNext by rememberUpdatedState(ctx.onSkipNext)
    val currentOnSkipPrevious by rememberUpdatedState(ctx.onSkipPrevious)
    val currentOnSeekToFraction by rememberUpdatedState(ctx.onSeekToFraction)
    val currentOnSeekToMs by rememberUpdatedState(ctx.onSeekToMs)
    val currentOnOpenStylePicker by rememberUpdatedState(ctx.onOpenStylePicker)

    fun flashAnimFor(quadrant: DialQuadrant): Animatable<Float, *> {
        return when (quadrant) {
            DialQuadrant.MENU -> sectorFlashMenu
            DialQuadrant.NEXT -> sectorFlashNext
            DialQuadrant.PLAY -> sectorFlashPlay
            DialQuadrant.PREV -> sectorFlashPrev
        }
    }

    fun handleCenterPress() {
        if (currentHapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        when (currentDialMode) {
            DialScreenMode.STAGE -> {
                if (!currentSleeveFlipped) {
                    isSleeveFlipped = true
                } else if (currentTrackCount > 0) {
                    val idx = currentHighlightedIndex.coerceIn(0, currentTrackCount - 1)
                    currentOnSelectQueueIndex(idx)
                }
            }
            DialScreenMode.NOW_PLAYING -> {
                isScrubMode = !isScrubMode
            }
            DialScreenMode.LYRICS -> {
                dialMode = DialScreenMode.STAGE
            }
        }
    }

    fun handleMenuTap() {
        when {
            currentDialMode == DialScreenMode.LYRICS -> {
                dialMode = DialScreenMode.STAGE
            }
            currentDialMode == DialScreenMode.STAGE && currentSleeveFlipped -> {
                isSleeveFlipped = false
            }
            currentDialMode == DialScreenMode.STAGE -> {
                isScrubMode = false
                dialMode = DialScreenMode.NOW_PLAYING
            }
            else -> {
                isScrubMode = false
                currentOnOpenStylePicker()
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        val compactHeight = maxHeight < 610.dp
        val wheelSize = if (compactHeight) 208.dp else 232.dp
        val shellShape = SmoothCornerShape(GlassTokens.RadiusXl)
        val screenShape = SmoothCornerShape(GlassTokens.RadiusLg)

        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxSize()
                .clip(shellShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(finishPalette.shellTop, finishPalette.shellBottom)
                    )
                )
                .border(1.dp, finishPalette.shellBorder, shellShape)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(screenShape)
                    .background(finishPalette.bezelFrame)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), screenShape)
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OndaMonogramMark(
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "ONDA DIAL",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            ),
                            color = Color.White,
                            maxLines = 1
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DialModeBadge(
                            text = when {
                                dialMode == DialScreenMode.STAGE && isSleeveFlipped -> "SLEEVE"
                                dialMode == DialScreenMode.NOW_PLAYING && isScrubMode -> "SCRUB"
                                else -> dialMode.badge
                            },
                            accent = finishPalette.screenAccent,
                            onClick = {
                                if (ctx.settings.hapticsEnabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                dialMode = when (dialMode) {
                                    DialScreenMode.STAGE -> DialScreenMode.NOW_PLAYING
                                    DialScreenMode.NOW_PLAYING -> DialScreenMode.LYRICS
                                    DialScreenMode.LYRICS -> DialScreenMode.STAGE
                                }
                            }
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isPlaying) GlassTokens.SuccessMint else Color(0xFF55555A)
                                )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    when (dialMode) {
                        DialScreenMode.LYRICS -> {
                            LyricsPane(
                                lyrics = ctx.lyrics,
                                positionMs = state.positionMs,
                                lyricsOffsetMs = ctx.lyricsOffsetMs,
                                isPlaying = isPlaying,
                                isLoading = ctx.isLyricsLoading,
                                errorMessage = ctx.lyricsError,
                                lyricsMotionEnabled = ctx.settings.lyricsMotionEnabled,
                                lyricsFontScale = ctx.settings.lyricsFontScale,
                                hapticsEnabled = ctx.settings.hapticsEnabled,
                                onSeekToMs = ctx.onSeekToMs,
                                onRefreshLyrics = ctx.onRefreshLyrics,
                                onAdjustOffsetMs = ctx.onAdjustLyricsOffset,
                                showControls = false,
                                playbackSpeed = state.playbackSpeed,
                                trackKey = track?.id
                            )
                        }

                        DialScreenMode.NOW_PLAYING -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    TrackArtworkImage(
                                        artworkUrl = track?.artworkUrl,
                                        contentDescription = track?.title,
                                        shape = SmoothCornerShape(GlassTokens.RadiusMd),
                                        modifier = Modifier
                                            .size(if (compactHeight) 80.dp else 96.dp)
                                            .border(
                                                1.dp,
                                                finishPalette.screenAccent.copy(alpha = 0.25f),
                                                SmoothCornerShape(GlassTokens.RadiusMd)
                                            )
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = track?.title ?: "Nothing playing",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = Color.White,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = track?.artist ?: "Select a track",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFA0A0A8),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        track?.album?.takeIf { it.isNotBlank() }?.let { albumName ->
                                            Text(
                                                text = albumName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF7A7A82),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isScrubMode) "SCRUB ACTIVE - SPIN WHEEL" else "CENTER: SCRUB | SPIN: SKIP",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = if (isScrubMode) finishPalette.screenAccent else Color(0xFF7E7E86)
                                    )
                                    Row(
                                        modifier = Modifier
                                            .defaultMinSize(
                                                minWidth = DockMetrics.MinTapTarget,
                                                minHeight = DockMetrics.MinTapTarget
                                            )
                                            .clickable { dialMode = DialScreenMode.LYRICS }
                                            .padding(horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "LYRICS",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = finishPalette.screenAccent
                                        )
                                    }
                                }
                            }
                        }

                        DialScreenMode.STAGE -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        rotationY = flipAngleDeg
                                        cameraDistance = 14f * this.density
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (flipAngleDeg <= 90f) {
                                    if (displayTracks.isNotEmpty()) {
                                        HorizontalPager(
                                            state = pagerState,
                                            contentPadding = PaddingValues(horizontal = 54.dp),
                                            pageSpacing = 6.dp,
                                            modifier = Modifier.fillMaxSize()
                                        ) { page ->
                                            val item = displayTracks[page]
                                            val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                                            val absOff = abs(offset).coerceIn(0f, 1.4f)
                                            val isCenter = page == pagerState.currentPage && absOff < 0.25f
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(vertical = 4.dp)
                                                    .graphicsLayer {
                                                        rotationY = (offset * 54f).coerceIn(-54f, 54f)
                                                        val s = lerp(1f, 0.78f, absOff.coerceIn(0f, 1f))
                                                        scaleX = s
                                                        scaleY = s
                                                        alpha = lerp(1f, 0.56f, absOff.coerceIn(0f, 1f))
                                                        cameraDistance = 14f * this.density
                                                    }
                                                    .clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null
                                                    ) {
                                                        if (isCenter) {
                                                            if (ctx.settings.hapticsEnabled) {
                                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            }
                                                            isSleeveFlipped = true
                                                        } else {
                                                            scope.launch {
                                                                if (reducedMotion) {
                                                                    pagerState.scrollToPage(page)
                                                                } else {
                                                                    pagerState.animateScrollToPage(page)
                                                                }
                                                            }
                                                        }
                                                    },
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                TrackArtworkImage(
                                                    artworkUrl = item.artworkUrl,
                                                    contentDescription = item.title,
                                                    shape = SmoothCornerShape(GlassTokens.RadiusMd),
                                                    modifier = Modifier
                                                        .weight(1f, fill = false)
                                                        .aspectRatio(1f)
                                                        .border(
                                                            1.dp,
                                                            Color.White.copy(alpha = if (isCenter) 0.20f else 0.08f),
                                                            SmoothCornerShape(GlassTokens.RadiusMd)
                                                        )
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(0.76f)
                                                        .height(16.dp)
                                                        .graphicsLayer {
                                                            scaleY = -1f
                                                            alpha = 0.28f
                                                            compositingStrategy = CompositingStrategy.Offscreen
                                                        }
                                                        .drawWithContent {
                                                            drawContent()
                                                            drawRect(
                                                                brush = Brush.verticalGradient(
                                                                    colors = listOf(
                                                                        Color.Transparent,
                                                                        Color.Black.copy(alpha = 0.85f)
                                                                    )
                                                                ),
                                                                blendMode = BlendMode.DstIn
                                                            )
                                                        }
                                                ) {
                                                    TrackArtworkImage(
                                                        artworkUrl = item.artworkUrl,
                                                        contentDescription = null,
                                                        shape = SmoothCornerShape(GlassTokens.RadiusSm),
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        TrackArtworkImage(
                                            artworkUrl = null,
                                            contentDescription = null,
                                            modifier = Modifier.size(120.dp)
                                        )
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                rotationY = 180f
                                            }
                                            .clip(RoundedCornerShape(GlassTokens.RadiusMd))
                                            .background(Color(0xFF131317))
                                            .border(
                                                1.dp,
                                                Color.White.copy(alpha = 0.10f),
                                                RoundedCornerShape(GlassTokens.RadiusMd)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "QUEUE SLEEVE",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = finishPalette.screenAccent
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (displayTracks.isNotEmpty()) "${highlightedQueueIndex + 1}/${displayTracks.size}" else "0/0",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = FontFamily.Monospace
                                                    ),
                                                    color = Color(0xFF8A8A8A)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .defaultMinSize(
                                                            minWidth = DockMetrics.MinTapTarget,
                                                            minHeight = DockMetrics.MinTapTarget
                                                        )
                                                        .clickable { dialMode = DialScreenMode.LYRICS },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "LYRICS",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = Color.White,
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                                                            .background(Color(0xFF26262C))
                                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                        }

                                        LazyColumn(
                                            state = sleeveListState,
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            contentPadding = PaddingValues(vertical = 4.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                        ) {
                                            itemsIndexed(
                                                items = displayTracks,
                                                key = { index, item -> "${item.id}_sleeve_$index" }
                                            ) { index, item ->
                                                val isFocused = index == highlightedQueueIndex
                                                val isActiveTrack = index == safeIndex
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                                                        .clip(RoundedCornerShape(GlassTokens.RadiusSm))
                                                        .background(
                                                            when {
                                                                isFocused -> Color(0xFF27272E)
                                                                isActiveTrack -> Color(0xFF1D1D22)
                                                                else -> Color.Transparent
                                                            }
                                                        )
                                                        .clickable {
                                                            highlightedQueueIndex = index
                                                            if (ctx.settings.hapticsEnabled) {
                                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            }
                                                            ctx.onSelectQueueIndex(index)
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = "${index + 1}",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontFamily = FontFamily.Monospace
                                                        ),
                                                        color = if (isFocused) finishPalette.screenAccent else Color(0xFF707078)
                                                    )
                                                    TrackArtworkImage(
                                                        artworkUrl = item.artworkUrl,
                                                        contentDescription = item.title,
                                                        shape = RoundedCornerShape(GlassTokens.RadiusXs),
                                                        modifier = Modifier.size(30.dp)
                                                    )
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = item.title,
                                                            style = MaterialTheme.typography.labelMedium.copy(
                                                                fontWeight = if (isActiveTrack || isFocused) FontWeight.Bold else FontWeight.Medium
                                                            ),
                                                            color = if (isFocused || isActiveTrack) Color.White else Color(0xFFC4C4CC),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = item.artist,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color(0xFF8A8A8A),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    if (isActiveTrack) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(7.dp)
                                                                .clip(CircleShape)
                                                                .background(finishPalette.screenAccent)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track?.title ?: "Nothing playing",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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
                        }
                        Text(
                            text = if (displayTracks.isNotEmpty()) "${safeIndex + 1} / ${displayTracks.size}" else "0 / 0",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = Color(0xFF8A8A8A)
                        )
                    }

                    val railHeight = if (dialMode == DialScreenMode.NOW_PLAYING && isScrubMode) 8.dp else 5.dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(railHeight)
                            .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                            .background(Color.White.copy(alpha = 0.14f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(shownProgress)
                                .fillMaxHeight()
                                .background(
                                    if (dialMode == DialScreenMode.NOW_PLAYING && isScrubMode) {
                                        finishPalette.screenAccent
                                    } else {
                                        Color.White
                                    }
                                )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OdometerText(
                            text = formatDurationMs((durationMs * shownProgress).toLong()),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (wheelScrubFraction != null || isScrubMode) finishPalette.screenAccent else Color(0xFF9E9E9E)
                        )
                        Text(
                            text = formatDurationMs(state.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8A8A8A)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (compactHeight) 10.dp else 14.dp))

            Box(
                modifier = Modifier
                    .size(wheelSize)
                    .clip(CircleShape)
                    .background(finishPalette.wheelSurface)
                    .border(1.5.dp, finishPalette.wheelBorder, CircleShape)
                    .pointerInput(Unit) {
                        val touchSlopPx = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val cx = size.width * 0.5f
                            val cy = size.height * 0.5f
                            val radiusPx = minOf(size.width, size.height) * 0.5f
                            val dx0 = down.position.x - cx
                            val dy0 = down.position.y - cy
                            val normR0 = hypot(dx0, dy0) / radiusPx.coerceAtLeast(1f)
                            if (normR0 > 1.04f) {
                                return@awaitEachGesture
                            }
                            down.consume()

                            if (normR0 <= DIAL_CENTER_RADIUS_FRACTION) {
                                scope.launch {
                                    if (!currentReducedMotion) {
                                        centerScale.animateTo(0.90f, tween(70))
                                    }
                                }
                                var stillInsideCenter = true
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        if (stillInsideCenter) {
                                            handleCenterPress()
                                        }
                                        break
                                    }
                                    val r = hypot(change.position.x - cx, change.position.y - cy) / radiusPx.coerceAtLeast(1f)
                                    stillInsideCenter = r <= DIAL_CENTER_RADIUS_FRACTION + 0.12f
                                }
                                scope.launch {
                                    if (currentReducedMotion) {
                                        centerScale.snapTo(1f)
                                    } else {
                                        centerScale.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = 560f))
                                    }
                                }
                                return@awaitEachGesture
                            }

                            val startAngleRad = atan2(dy0, dx0)
                            val startAngleDeg = Math.toDegrees(startAngleRad.toDouble()).toFloat()
                            val startQuadrant = quadrantForAngleDeg(startAngleDeg)
                            val flashAnim = flashAnimFor(startQuadrant)

                            scope.launch {
                                if (currentReducedMotion) {
                                    flashAnim.snapTo(0.8f)
                                } else {
                                    flashAnim.animateTo(1f, tween(45))
                                }
                            }

                            var isRotating = false
                            var longPressTriggered = false
                            var lastAngleRad = startAngleRad
                            var accumulatedAngle = 0f
                            var lastTickMs = 0L
                            var lastTickDir = 0
                            var sameDirStreak = 0
                            var nowPlayingSkipAccumulator = 0
                            var workingScrub = wheelScrubFraction ?: currentBaseProgress

                            val longPressJob = scope.launch {
                                when (startQuadrant) {
                                    DialQuadrant.MENU -> {
                                        delay(700L)
                                        if (!isRotating) {
                                            longPressTriggered = true
                                            if (currentHapticsEnabled) {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            currentOnOpenStylePicker()
                                        }
                                    }
                                    DialQuadrant.PREV, DialQuadrant.NEXT -> {
                                        delay(450L)
                                        if (!isRotating) {
                                            longPressTriggered = true
                                            val dirMs = if (startQuadrant == DialQuadrant.NEXT) 5_000L else -5_000L
                                            var runningSeekMs = currentPositionMs
                                            if (currentHapticsEnabled) {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            while (true) {
                                                runningSeekMs = (runningSeekMs + dirMs).coerceIn(0L, currentDurationMs)
                                                currentOnSeekToMs(runningSeekMs)
                                                triggerDialDetentTick(context, vibrator, haptics, currentHapticsEnabled)
                                                delay(220L)
                                            }
                                        }
                                    }
                                    DialQuadrant.PLAY -> Unit
                                }
                            }

                            fun emitDetent(dir: Int) {
                                val mode = currentDialMode
                                val isLinear = mode == DialScreenMode.NOW_PLAYING || mode == DialScreenMode.LYRICS
                                val nowMs = System.currentTimeMillis()
                                val dtMs = nowMs - lastTickMs
                                val multiplier = if (isLinear) {
                                    1
                                } else if (dir == lastTickDir && dtMs in 1L..DIAL_ACCEL_WINDOW_MS) {
                                    sameDirStreak = (sameDirStreak + 1).coerceAtMost(4)
                                    when {
                                        dtMs < 48L && sameDirStreak >= 3 -> 4
                                        dtMs < 90L && sameDirStreak >= 2 -> 3
                                        else -> 2
                                    }
                                } else {
                                    sameDirStreak = 0
                                    1
                                }
                                lastTickMs = nowMs
                                lastTickDir = dir
                                triggerDialDetentTick(context, vibrator, haptics, currentHapticsEnabled)

                                when (mode) {
                                    DialScreenMode.STAGE -> {
                                        val count = currentTrackCount
                                        if (count <= 0) return
                                        if (currentSleeveFlipped) {
                                            val nextIdx = (currentHighlightedIndex + dir * multiplier).coerceIn(0, count - 1)
                                            if (nextIdx != currentHighlightedIndex) {
                                                highlightedQueueIndex = nextIdx
                                                scope.launch {
                                                    if (currentReducedMotion) {
                                                        sleeveListState.scrollToItem(nextIdx)
                                                    } else {
                                                        sleeveListState.animateScrollToItem(nextIdx)
                                                    }
                                                }
                                            }
                                        } else {
                                            val nextPage = (currentCoverPage + dir * multiplier).coerceIn(0, count - 1)
                                            if (nextPage != currentCoverPage) {
                                                coverTargetPage = nextPage
                                                coverScrollJob?.cancel()
                                                coverScrollJob = scope.launch {
                                                    if (currentReducedMotion) {
                                                        pagerState.scrollToPage(nextPage)
                                                    } else {
                                                        pagerState.animateScrollToPage(nextPage)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    DialScreenMode.NOW_PLAYING -> {
                                        if (currentScrubMode) {
                                            workingScrub = (workingScrub + dir * (1f / 32f)).coerceIn(0f, 1f)
                                            wheelScrubFraction = workingScrub
                                        } else {
                                            nowPlayingSkipAccumulator += dir
                                            if (nowPlayingSkipAccumulator >= 2) {
                                                nowPlayingSkipAccumulator = 0
                                                currentOnSkipNext()
                                            } else if (nowPlayingSkipAccumulator <= -2) {
                                                nowPlayingSkipAccumulator = 0
                                                currentOnSkipPrevious()
                                            }
                                        }
                                    }

                                    DialScreenMode.LYRICS -> {
                                        val deltaMs = dir * 3_000L
                                        val targetMs = (currentPositionMs + deltaMs).coerceIn(0L, currentDurationMs)
                                        currentOnSeekToMs(targetMs)
                                    }
                                }
                            }

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()

                                val dx = change.position.x - cx
                                val dy = change.position.y - cy
                                val distFromStart = hypot(
                                    change.position.x - down.position.x,
                                    change.position.y - down.position.y
                                )
                                val normR = hypot(dx, dy) / radiusPx.coerceAtLeast(1f)
                                if (!isRotating && distFromStart > touchSlopPx) {
                                    isRotating = true
                                    longPressJob.cancel()
                                    scope.launch { flashAnim.animateTo(0f, tween(140)) }
                                }
                                if (isRotating && normR >= 0.30f) {
                                    val currentAngle = atan2(dy, dx)
                                    var delta = currentAngle - lastAngleRad
                                    while (delta > PI.toFloat()) delta -= 2f * PI.toFloat()
                                    while (delta < -PI.toFloat()) delta += 2f * PI.toFloat()
                                    lastAngleRad = currentAngle
                                    accumulatedAngle += delta

                                    while (accumulatedAngle >= DIAL_DETENT_RAD) {
                                        accumulatedAngle -= DIAL_DETENT_RAD
                                        emitDetent(1)
                                    }
                                    while (accumulatedAngle <= -DIAL_DETENT_RAD) {
                                        accumulatedAngle += DIAL_DETENT_RAD
                                        emitDetent(-1)
                                    }
                                }
                            }

                            longPressJob.cancel()
                            scope.launch {
                                if (currentReducedMotion) {
                                    flashAnim.snapTo(0f)
                                } else {
                                    flashAnim.animateTo(0f, tween(240))
                                }
                            }

                            if (isRotating) {
                                wheelScrubFraction?.let { finalFraction ->
                                    currentOnSeekToFraction(finalFraction)
                                    wheelScrubFraction = null
                                }
                            } else if (!longPressTriggered) {
                                when (startQuadrant) {
                                    DialQuadrant.MENU -> {
                                        if (currentHapticsEnabled) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        handleMenuTap()
                                    }
                                    DialQuadrant.NEXT -> {
                                        if (currentHapticsEnabled) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        currentOnSkipNext()
                                    }
                                    DialQuadrant.PREV -> {
                                        if (currentHapticsEnabled) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        currentOnSkipPrevious()
                                    }
                                    DialQuadrant.PLAY -> {
                                        if (currentHapticsEnabled) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        currentOnTogglePlayPause()
                                    }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width * 0.5f, size.height * 0.5f)
                    val outerR = size.minDimension * 0.5f
                    val innerR = outerR * DIAL_CENTER_RADIUS_FRACTION
                    val ringStroke = (outerR - innerR).coerceAtLeast(1f)
                    val arcR = innerR + ringStroke * 0.5f
                    val arcTopLeft = Offset(center.x - arcR, center.y - arcR)
                    val arcSize = Size(arcR * 2f, arcR * 2f)

                    val flashes = listOf(
                        -135f to sectorFlashMenu.value,
                        -45f to sectorFlashNext.value,
                        45f to sectorFlashPlay.value,
                        135f to sectorFlashPrev.value
                    )
                    for ((startDeg, flash) in flashes) {
                        if (flash > 0.005f) {
                            drawArc(
                                color = finishPalette.sectorFlash.copy(alpha = 0.16f * flash),
                                startAngle = startDeg,
                                sweepAngle = 90f,
                                useCenter = false,
                                topLeft = arcTopLeft,
                                size = arcSize,
                                style = Stroke(width = ringStroke)
                            )
                        }
                    }

                    for (tick in 0 until 24) {
                        val angleRad = (tick * DIAL_DETENT_RAD).toDouble()
                        val r0 = outerR * 0.91f
                        val r1 = outerR * 0.96f
                        drawLine(
                            color = finishPalette.wheelGlyph.copy(alpha = if (tick % 6 == 0) 0.28f else 0.11f),
                            start = Offset(
                                center.x + cos(angleRad).toFloat() * r0,
                                center.y + sin(angleRad).toFloat() * r0
                            ),
                            end = Offset(
                                center.x + cos(angleRad).toFloat() * r1,
                                center.y + sin(angleRad).toFloat() * r1
                            ),
                            strokeWidth = if (tick % 6 == 0) 1.5.dp.toPx() else 1.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                Text(
                    text = "MENU",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = finishPalette.wheelGlyph,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 18.dp)
                )

                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    tint = finishPalette.wheelGlyph,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 18.dp)
                        .size(22.dp)
                )

                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = finishPalette.wheelGlyph,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 18.dp)
                        .size(22.dp)
                )

                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Play or pause",
                    tint = finishPalette.wheelGlyph,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp)
                        .size(24.dp)
                )

                val centerBtnSize = wheelSize * DIAL_CENTER_RADIUS_FRACTION
                Box(
                    modifier = Modifier
                        .size(centerBtnSize)
                        .graphicsLayer {
                            val s = centerScale.value
                            scaleX = s
                            scaleY = s
                        }
                        .clip(CircleShape)
                        .background(finishPalette.centerButton)
                        .border(1.2.dp, finishPalette.centerBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(wheelSize * 0.22f)
                            .clip(CircleShape)
                            .border(
                                1.dp,
                                finishPalette.centerGlyph.copy(alpha = 0.16f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        OndaMonogramMark(
                            tint = finishPalette.centerGlyph.copy(alpha = 0.82f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialModeBadge(
    text: String,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                .background(Color(0xFF1F1F25))
                .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(GlassTokens.RadiusPill))
                .padding(horizontal = 9.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                ),
                color = accent,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
