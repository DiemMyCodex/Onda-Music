package com.onda.music.core.design

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.min

private const val BonesFadeOutMs = 240
private const val RevealGapMs = 90
private const val RevealFadeInMs = 360
private const val BonesWaveMs = 1800
private const val BurstWindowMs = 120L
private const val BurstStepMs = 55
private const val BurstMaxSteps = 6
private val RevealSlideDistance = 18.dp

val DefaultBoneColor = Color(0xFFD9D9D9).copy(alpha = 0.16f)

@Stable
class RevealSeenState(initial: Collection<String> = emptyList()) {
    private val seen = HashSet(initial)
    private var burstStartedAt = 0L
    private var burstCount = 0

    fun hasSeen(key: String): Boolean = key in seen

    fun markSeen(key: String) {
        seen.add(key)
    }

    internal fun snapshotKeys(): List<String> = seen.toList()

    internal fun nextBurstDelay(): Int {
        val now = SystemClock.uptimeMillis()
        if (now - burstStartedAt > BurstWindowMs) {
            burstStartedAt = now
            burstCount = 0
        }
        return min(burstCount++, BurstMaxSteps) * BurstStepMs
    }

    companion object {
        val Saver: Saver<RevealSeenState, ArrayList<String>> = Saver(
            save = { ArrayList(it.seen) },
            restore = { RevealSeenState(it) }
        )
    }
}

@Composable
fun rememberRevealSeenState(): RevealSeenState {
    return rememberSaveable(saver = RevealSeenState.Saver) { RevealSeenState() }
}

@Composable
fun <T : Any> RevealWhenLoaded(
    data: T?,
    modifier: Modifier = Modifier,
    bones: @Composable () -> Unit,
    content: @Composable (T) -> Unit
) {
    val reducedMotion = OndaTheme.reducedMotion
    val slidePx = with(LocalDensity.current) { RevealSlideDistance.roundToPx() }

    AnimatedContent(
        targetState = data,
        contentKey = { it != null },
        transitionSpec = {
            if (reducedMotion) {
                fadeIn(tween(durationMillis = 80))
                    .togetherWith(fadeOut(tween(durationMillis = 80)))
                    .using(SizeTransform(clip = false))
            } else if (targetState != null) {
                val delayMs = BonesFadeOutMs + RevealGapMs
                (fadeIn(tween(durationMillis = RevealFadeInMs, delayMillis = delayMs)) +
                    slideInVertically(
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = 440f)
                    ) { slidePx })
                    .togetherWith(fadeOut(tween(durationMillis = BonesFadeOutMs)))
                    .using(SizeTransform(clip = false))
            } else {
                fadeIn(tween(durationMillis = BonesFadeOutMs))
                    .togetherWith(fadeOut(tween(durationMillis = BonesFadeOutMs)))
                    .using(SizeTransform(clip = false))
            }
        },
        label = "OndaRevealWhenLoaded",
        modifier = modifier
    ) { state ->
        if (state == null) bones() else content(state)
    }
}

@Composable
fun Modifier.revealOnAppear(
    key: String,
    seenState: RevealSeenState,
    enabled: Boolean = true,
    delayMillis: Int = 0,
    cascade: Boolean = false
): Modifier {
    val reducedMotion = OndaTheme.reducedMotion
    if (reducedMotion) {
        if (enabled) seenState.markSeen(key)
        return this
    }
    val alreadySeen = remember(key) { seenState.hasSeen(key) }
    if (alreadySeen) return this

    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key, enabled) {
        if (!enabled) return@LaunchedEffect
        seenState.markSeen(key)
        val wait = delayMillis + if (cascade) seenState.nextBurstDelay() else 0
        if (wait > 0) delay(wait.toLong())
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 440f)
        )
    }

    val slidePx = with(LocalDensity.current) { RevealSlideDistance.toPx() }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * slidePx
    }
}

@Composable
fun BonesHost(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val reducedMotion = OndaTheme.reducedMotion
    if (reducedMotion) {
        Column(modifier = modifier, content = content)
        return
    }

    val transition = rememberInfiniteTransition(label = "OndaBonesWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BonesWaveMs, easing = LinearEasing)),
        label = "OndaBonesPhase"
    )

    Column(
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .damageBoundary()
            .drawWithContent {
                drawContent()
                val h = size.height
                val band = h * 0.8f + 1f
                val center = -band + phase * (h + band * 2f)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.42f),
                        0.5f to Color.Black,
                        1f to Color.Black.copy(alpha = 0.42f),
                        startY = center - band,
                        endY = center + band
                    ),
                    blendMode = BlendMode.DstIn
                )
            },
        content = content
    )
}

@Composable
fun Bone(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = SmoothCornerShape(16.dp),
    color: Color = DefaultBoneColor
) {
    Box(
        modifier = modifier
            .size(width, height)
            .background(color, shape)
    )
}

@Composable
fun BoneBlock(
    modifier: Modifier = Modifier,
    shape: Shape = SmoothCornerShape(16.dp),
    color: Color = DefaultBoneColor
) {
    Box(
        modifier = modifier.background(color, shape)
    )
}

@Composable
fun OndaLoadingDots(
    modifier: Modifier = Modifier,
    dotSize: Dp = 7.dp,
    dotColor: Color = GlassTokens.TextPrimary,
    spacing: Dp = 6.dp
) {
    val reducedMotion = OndaTheme.reducedMotion
    if (reducedMotion) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(1f, 0.65f, 0.38f).forEach { fixedAlpha ->
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = fixedAlpha))
                )
            }
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "OndaLoadingDots")
    Row(
        modifier = modifier.damageBoundary(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 820, delayMillis = index * 140),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "OndaLoadingDot$index"
            )
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer {
                        alpha = 0.30f + 0.70f * phase
                        val s = 0.76f + 0.24f * phase
                        scaleX = s
                        scaleY = s
                    }
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}
