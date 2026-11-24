package com.onda.music.core.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

@Immutable
data class OndaToastMessage(
    val id: Long,
    val title: String,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val durationMs: Long = 2_800L
)

@Stable
class OndaToastState {
    private val nextId = AtomicLong(0L)

    var current: OndaToastMessage? by mutableStateOf(null)
        private set

    fun show(
        title: String,
        subtitle: String? = null,
        artworkUrl: String? = null,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        durationMs: Long = 2_800L
    ) {
        current = OndaToastMessage(
            id = nextId.incrementAndGet(),
            title = title,
            subtitle = subtitle,
            artworkUrl = artworkUrl,
            actionLabel = actionLabel,
            onAction = onAction,
            durationMs = durationMs
        )
    }

    fun dismiss() {
        current = null
    }
}

val LocalOndaToastState = staticCompositionLocalOf<OndaToastState?> { null }

@Composable
fun rememberOndaToastState(): OndaToastState = remember { OndaToastState() }

@Composable
fun OndaTopToastHost(
    state: OndaToastState,
    modifier: Modifier = Modifier
) {
    val currentMessage = state.current
    var renderedMessage by remember { mutableStateOf<OndaToastMessage?>(null) }
    if (currentMessage != null) {
        renderedMessage = currentMessage
    }

    val reducedMotion = OndaTheme.reducedMotion
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dismissThresholdPx = remember(density) { with(density) { 28.dp.toPx() } }
    val maxDownRubberPx = remember(density) { with(density) { 10.dp.toPx() } }

    LaunchedEffect(currentMessage?.id) {
        val active = currentMessage ?: return@LaunchedEffect
        delay(active.durationMs.coerceAtLeast(1L))
        if (state.current?.id == active.id) {
            state.dismiss()
        }
    }

    val activeId = renderedMessage?.id
    val dragOffsetY = remember(activeId) { Animatable(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = currentMessage != null,
            enter = if (reducedMotion) {
                fadeIn(animationSpec = tween(durationMillis = 150))
            } else {
                slideInVertically(
                    animationSpec = spring<IntOffset>(
                        dampingRatio = 0.74f,
                        stiffness = 420f
                    )
                ) { fullHeight -> -fullHeight - fullHeight / 2 } + fadeIn(
                    animationSpec = tween(durationMillis = 180)
                )
            },
            exit = if (reducedMotion) {
                fadeOut(animationSpec = tween(durationMillis = 120))
            } else {
                slideOutVertically(
                    animationSpec = spring<IntOffset>(
                        dampingRatio = 0.92f,
                        stiffness = 520f
                    )
                ) { fullHeight -> -fullHeight - fullHeight / 2 } + fadeOut(
                    animationSpec = tween(durationMillis = 140)
                )
            }
        ) {
            val toast = renderedMessage ?: return@AnimatedVisibility
            val pillShape = remember { SmoothCornerShape(24.dp) }
            val thumbShape = remember { SmoothCornerShape(10.dp) }
            val actionShape = remember { SmoothCornerShape(999.dp) }
            val context = LocalContext.current
            val resolvedArt = remember(toast.artworkUrl) {
                ArtworkUrlResolver.upgradeHighRes(toast.artworkUrl)
            }
            val actionLabel = toast.actionLabel
            val onAction = toast.onAction
            val hasAction = !actionLabel.isNullOrBlank() && onAction != null
            val hasSubtitle = !toast.subtitle.isNullOrBlank()

            val snapBackDrag: () -> Unit = {
                scope.launch {
                    if (reducedMotion) {
                        dragOffsetY.snapTo(0f)
                    } else {
                        dragOffsetY.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = 0.74f,
                                stiffness = 420f
                            )
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .widthIn(min = 180.dp, max = 420.dp)
                    .heightIn(min = 52.dp)
                    .graphicsLayer {
                        val y = dragOffsetY.value
                        translationY = y
                        if (y < 0f) {
                            val fadeFraction = (-y / (dismissThresholdPx * 2.2f)).coerceIn(0f, 0.55f)
                            alpha = 1f - fadeFraction
                        }
                    }
                    .clip(pillShape)
                    .background(Color(0xFF18181A), pillShape)
                    .border(1.dp, Color(0x22FFFFFF), pillShape)
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                    }
                    .pointerInput(toast.id, reducedMotion) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val nextY = (dragOffsetY.value + dragAmount).coerceAtMost(maxDownRubberPx)
                                scope.launch {
                                    dragOffsetY.snapTo(nextY)
                                }
                            },
                            onDragEnd = {
                                if (dragOffsetY.value <= -dismissThresholdPx) {
                                    state.dismiss()
                                } else {
                                    snapBackDrag()
                                }
                            },
                            onDragCancel = {
                                snapBackDrag()
                            }
                        )
                    }
                    .padding(
                        start = 10.dp,
                        end = if (hasAction) 6.dp else 16.dp,
                        top = 6.dp,
                        bottom = 6.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!resolvedArt.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(resolvedArt)
                            .crossfade(!reducedMotion)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(thumbShape)
                            .background(Color(0xFF242424), thumbShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(thumbShape)
                            .background(Color(0xFF242424), thumbShape),
                        contentAlignment = Alignment.Center
                    ) {
                        OndaMonogramMark(
                            modifier = Modifier.size(18.dp),
                            tint = Color.White,
                            filled = false
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(vertical = 2.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = toast.title,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color.White,
                        maxLines = if (hasSubtitle) 1 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (hasSubtitle) {
                        Text(
                            text = toast.subtitle.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassTokens.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (hasAction) {
                    val interactionSource = remember(toast.id) { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .pressBounce(
                                interactionSource = interactionSource,
                                enabled = !reducedMotion
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.Button,
                                onClick = {
                                    onAction.invoke()
                                    state.dismiss()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 36.dp)
                                .clip(actionShape)
                                .background(Color(0x1FFFFFFF), actionShape)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = actionLabel.orEmpty(),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
