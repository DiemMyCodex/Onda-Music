package com.onda.music.feature.settings

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.onda.music.core.design.BlurDirection
import com.onda.music.core.design.BlurEffectCache
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.OndaBackdropState
import com.onda.music.core.design.OndaMonogramMark
import com.onda.music.core.design.OndaSwitch
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.ProgressiveBlurBackground
import com.onda.music.core.design.RoundedHighlight
import com.onda.music.core.design.SmoothCornerShape
import com.onda.music.core.design.pressBounce
import java.io.File

internal object SettingsOledColors {
    val CanvasBlack = Color(0xFF000000)
    val MatteCard = Color(0xFF141414)
    val SearchFocused = Color(0xFF1E1E1E)
    val IconBadge = Color(0xFF242424)
    val InactiveTrack = Color(0xFF2E2E2E)
    val CheckedTrack = Color(0xFF525252)
    val InsetDivider = Color(0xFF1F1F1F)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9E9E9E)
    val TextMuted = Color(0xFF8A8A8A)
    val ErrorTint = Color(0xFFFF6B7A)
    val SuccessTint = Color(0xFF4ADE80)
}

private val TopBarClearance = 8.dp
private val TitleBlockHeight = 82.dp
private val TitleSearchGap = 8.dp
private val SearchFieldHeight = 48.dp
private val BottomHeaderPadding = 12.dp
private val AvatarTapSize = 44.dp
private val AvatarVisualSize = 34.dp
private val AvatarReserveWidth = 52.dp

@Composable
fun rememberSettingsTopBarExpandedHeight(): Dp {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return statusTop + TopBarClearance + TitleBlockHeight + TitleSearchGap + SearchFieldHeight + BottomHeaderPadding
}

@Composable
fun rememberSettingsTopBarCollapsedHeight(): Dp {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return statusTop + TopBarClearance + SearchFieldHeight + BottomHeaderPadding
}

@Composable
fun rememberSettingsDetailTopBarHeight(): Dp {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return statusTop + 10.dp + DockMetrics.MinTapTarget + 10.dp
}

@Composable
fun OndaWaveMark(modifier: Modifier = Modifier) {
    val plateShape = remember { SmoothCornerShape(9.dp) }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 30.dp, minHeight = 30.dp)
            .clip(plateShape)
            .background(Color(0xFF1A1A1A)),
        contentAlignment = Alignment.Center
    ) {
        OndaMonogramMark(
            modifier = Modifier.matchParentSize(),
            tint = SettingsOledColors.TextPrimary,
            cutoutColor = Color(0xFF1A1A1A),
            filled = true
        )
    }
}

@Composable
fun SettingsCollapsingTopBar(
    title: String,
    avatarPath: String?,
    backLabel: String,
    profileLabel: String,
    searchQuery: String,
    searchPlaceholder: String,
    clearSearchLabel: String,
    isSearchFocused: Boolean,
    collapseProgressProvider: () -> Float,
    backdropState: OndaBackdropState,
    glassBlurEnabled: Boolean,
    onBack: () -> Unit,
    onAvatarClick: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current
    val reducedMotion = OndaTheme.reducedMotion
    val titleTravelPx = with(density) { (TitleBlockHeight + TitleSearchGap).toPx() }
    val avatarReservePx = with(density) { AvatarReserveWidth.toPx() }
    val avatarDropPx = with(density) { ((SearchFieldHeight - AvatarTapSize) * 0.5f).toPx() }

    val avatarFile = remember(avatarPath) {
        avatarPath?.let { File(it) }?.takeIf { it.exists() }
    }
    val showGlass by remember {
        derivedStateOf { collapseProgressProvider() > 0.01f }
    }
    val collapsedBackdropHeight = statusTop + TopBarClearance + SearchFieldHeight + BottomHeaderPadding + 10.dp

    Box(modifier = modifier.fillMaxWidth()) {
        if (showGlass) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(collapsedBackdropHeight)
                    .graphicsLayer {
                        alpha = collapseProgressProvider().coerceIn(0f, 1f)
                    }
            ) {
                ProgressiveBlurBackground(
                    backdropState = backdropState,
                    direction = BlurDirection.BOTTOM_TO_TOP,
                    maxBlurRadius = { 34f * collapseProgressProvider().coerceIn(0f, 1f) },
                    foundationColor = SettingsOledColors.CanvasBlack,
                    blurEnabled = glassBlurEnabled,
                    steps = 3,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusTop + TopBarClearance)
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TitleBlockHeight)
                    .graphicsLayer {
                        val p = collapseProgressProvider().coerceIn(0f, 1f)
                        alpha = (1f - p * 1.65f).coerceIn(0f, 1f)
                        translationY = -p * titleTravelPx
                        if (!reducedMotion) {
                            val s = 1f - p * 0.14f
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                renderEffect = BlurEffectCache.get(p * 12f)
                            }
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val backInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(AvatarTapSize)
                        .pressBounce(backInteraction, pressedScale = 0.90f)
                        .clip(SmoothCornerShape(12.dp))
                        .clickable(
                            interactionSource = backInteraction,
                            indication = RoundedHighlight(cornerRadius = 12.dp),
                            role = Role.Button,
                            onClick = {
                                if (collapseProgressProvider() < 0.5f) {
                                    onBack()
                                }
                            }
                        ),
                    contentAlignment = Alignment.CenterStart
                ) {
                    OndaWaveMark(modifier = Modifier.size(30.dp))
                }

                Text(
                    text = title,
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SettingsOledColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(TitleSearchGap))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = collapseProgressProvider().coerceIn(0f, 1f)
                        translationY = -p * titleTravelPx
                    }
            ) {
                Box(
                    modifier = Modifier.layout { measurable, constraints ->
                        val p = collapseProgressProvider().coerceIn(0f, 1f)
                        val reserve = (avatarReservePx * p).toInt()
                        val targetWidth = (constraints.maxWidth - reserve).coerceAtLeast(0)
                        val placeable = measurable.measure(
                            constraints.copy(minWidth = targetWidth, maxWidth = targetWidth)
                        )
                        layout(constraints.maxWidth, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
                ) {
                    SettingsSearchPill(
                        query = searchQuery,
                        placeholder = searchPlaceholder,
                        clearLabel = clearSearchLabel,
                        onQueryChange = onSearchQueryChange,
                        focused = isSearchFocused,
                        onFocusChanged = onSearchFocusChanged
                    )
                }
            }
        }

        val avatarInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = statusTop + TopBarClearance, end = 16.dp)
                .graphicsLayer {
                    val p = collapseProgressProvider().coerceIn(0f, 1f)
                    translationY = p * avatarDropPx
                }
                .size(AvatarTapSize)
                .pressBounce(avatarInteraction, pressedScale = 0.90f)
                .clip(CircleShape)
                .clickable(
                    interactionSource = avatarInteraction,
                    indication = RoundedHighlight(cornerRadius = 22.dp),
                    role = Role.Button,
                    onClick = onAvatarClick
                ),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .size(AvatarVisualSize)
                    .clip(CircleShape)
                    .background(SettingsOledColors.MatteCard)
                    .border(1.dp, SettingsOledColors.IconBadge, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (avatarFile != null) {
                    AsyncImage(
                        model = avatarFile,
                        contentDescription = profileLabel,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = backLabel,
                        tint = SettingsOledColors.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsHubHeader(
    title: String,
    avatarPath: String?,
    backLabel: String,
    profileLabel: String,
    onBack: () -> Unit,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val avatarFile = remember(avatarPath) {
        avatarPath?.let { File(it) }?.takeIf { it.exists() }
    }
    val backInteraction = remember { MutableInteractionSource() }
    val avatarInteraction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                    .pressBounce(backInteraction, pressedScale = 0.90f)
                    .clip(SmoothCornerShape(12.dp))
                    .clickable(
                        interactionSource = backInteraction,
                        indication = RoundedHighlight(cornerRadius = 12.dp),
                        role = Role.Button,
                        onClick = onBack
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                OndaWaveMark(modifier = Modifier.size(30.dp))
            }

            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                    .pressBounce(avatarInteraction, pressedScale = 0.90f)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = avatarInteraction,
                        indication = RoundedHighlight(cornerRadius = 22.dp),
                        role = Role.Button,
                        onClick = onAvatarClick
                    ),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(SettingsOledColors.MatteCard)
                        .border(1.dp, SettingsOledColors.IconBadge, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarFile != null) {
                        AsyncImage(
                            model = avatarFile,
                            contentDescription = profileLabel,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = backLabel,
                            tint = SettingsOledColors.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Text(
            text = title,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            color = SettingsOledColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SettingsSearchPill(
    query: String,
    placeholder: String,
    clearLabel: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    onFocusChanged: ((Boolean) -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val pillBackground by animateColorAsState(
        targetValue = if (focused) SettingsOledColors.SearchFocused else SettingsOledColors.MatteCard,
        animationSpec = tween(durationMillis = 150),
        label = "SearchPillBackground"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SearchFieldHeight)
            .clip(RoundedCornerShape(999.dp))
            .background(pillBackground)
            .padding(start = 16.dp, end = if (query.isNotEmpty()) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = if (focused) SettingsOledColors.TextPrimary else SettingsOledColors.TextMuted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsOledColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = SettingsOledColors.TextPrimary
                ),
                cursorBrush = SolidColor(SettingsOledColors.TextPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { onFocusChanged?.invoke(it.isFocused) }
            )
        }
        if (query.isNotEmpty()) {
            val clearInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(DockMetrics.MinTapTarget)
                    .pressBounce(clearInteraction, pressedScale = 0.90f)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = clearInteraction,
                        indication = RoundedHighlight(cornerRadius = 24.dp),
                        role = Role.Button,
                        onClick = { onQueryChange("") }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = clearLabel,
                    tint = SettingsOledColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsDetailTopBar(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrollOffsetPx: () -> Float = { 0f }
) {
    val backInteraction = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val fadeRangePx = with(density) { 40.dp.toPx() }.coerceAtLeast(1f)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(DockMetrics.MinTapTarget)
                    .pressBounce(backInteraction, pressedScale = 0.92f)
                    .clip(CircleShape)
                    .background(SettingsOledColors.MatteCard)
                    .clickable(
                        interactionSource = backInteraction,
                        indication = RoundedHighlight(cornerRadius = 24.dp),
                        role = Role.Button,
                        onClick = onBack
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = backLabel,
                    tint = SettingsOledColors.TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
                color = SettingsOledColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.75.dp)
                .graphicsLayer {
                    alpha = (scrollOffsetPx() / fadeRangePx).coerceIn(0f, 1f)
                }
                .background(SettingsOledColors.InsetDivider)
        )
    }
}

@Composable
fun SettingsMatteCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = SettingsOledColors.MatteCard,
    cornerRadius: Int = 24,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SmoothCornerShape(cornerRadius.dp))
            .background(backgroundColor),
        content = content
    )
}

@Composable
fun SettingsSuggestedRow(
    icon: ImageVector,
    title: String,
    breadcrumb: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 54.dp)
            .pressBounce(interactionSource, pressedScale = 0.97f)
            .clip(SmoothCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = RoundedHighlight(cornerRadius = 16.dp),
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(SettingsOledColors.IconBadge),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SettingsOledColors.TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                color = SettingsOledColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = breadcrumb,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = SettingsOledColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SettingsCategoryPill(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingBadge: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = SmoothCornerShape(24.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .pressBounce(interactionSource, pressedScale = 0.97f)
            .clip(shape)
            .background(SettingsOledColors.MatteCard)
            .clickable(
                interactionSource = interactionSource,
                indication = RoundedHighlight(cornerRadius = 24.dp),
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SettingsOledColors.IconBadge),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SettingsOledColors.TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = SettingsOledColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!trailingBadge.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = trailingBadge,
                style = MaterialTheme.typography.labelMedium,
                color = SettingsOledColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(SettingsOledColors.IconBadge)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
fun SettingsSearchResultRow(
    icon: ImageVector,
    title: String,
    breadcrumb: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = SmoothCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .pressBounce(interactionSource, pressedScale = 0.97f)
            .clip(shape)
            .background(SettingsOledColors.MatteCard)
            .clickable(
                interactionSource = interactionSource,
                indication = RoundedHighlight(cornerRadius = 20.dp),
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(SettingsOledColors.IconBadge),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SettingsOledColors.TextPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = SettingsOledColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = breadcrumb,
                style = MaterialTheme.typography.bodySmall,
                color = SettingsOledColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = SettingsOledColors.TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun SettingsGroupCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                color = SettingsOledColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        SettingsMatteCard(
            backgroundColor = SettingsOledColors.MatteCard,
            cornerRadius = 24,
            content = content
        )
    }
}

@Composable
fun SettingsRowDivider(
    modifier: Modifier = Modifier,
    color: Color = SettingsOledColors.InsetDivider
) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 16.dp),
        thickness = 1.dp,
        color = color
    )
}

@Composable
fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
    badgeAccent: Boolean = false,
    destructive: Boolean = false,
    iconBadgeColor: Color = SettingsOledColors.IconBadge
) {
    val titleColor = if (destructive) SettingsOledColors.ErrorTint else SettingsOledColors.TextPrimary
    val iconTint = if (destructive) SettingsOledColors.ErrorTint else SettingsOledColors.TextPrimary
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .pressBounce(interactionSource, pressedScale = 0.98f)
            .clip(SmoothCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = RoundedHighlight(cornerRadius = 18.dp),
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconBadgeColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SettingsOledColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!badgeText.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelMedium,
                color = if (badgeAccent) SettingsOledColors.TextPrimary else SettingsOledColors.TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(SettingsOledColors.IconBadge)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = SettingsOledColors.TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    iconBadgeColor: Color = SettingsOledColors.IconBadge,
    hapticsEnabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val dispatchToggle: (Boolean) -> Unit = { next ->
        if (hapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        onCheckedChange(next)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .pressBounce(interactionSource, pressedScale = 0.985f)
            .clip(SmoothCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = RoundedHighlight(cornerRadius = 18.dp),
                role = Role.Switch,
                onClick = { dispatchToggle(!checked) }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconBadgeColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SettingsOledColors.TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = SettingsOledColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SettingsOledColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        OndaSwitch(
            checked = checked,
            onCheckedChange = dispatchToggle
        )
    }
}

@Composable
fun SettingsPillButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unselectedColor: Color = SettingsOledColors.IconBadge
) {
    val shape = RoundedCornerShape(999.dp)
    val bgColor = if (selected) SettingsOledColors.TextPrimary else unselectedColor
    val textColor = if (selected) SettingsOledColors.CanvasBlack else SettingsOledColors.TextSecondary
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
            .pressBounce(interactionSource, pressedScale = 0.96f)
            .clip(shape)
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = RoundedHighlight(cornerRadius = 999.dp),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SettingsInputDialog(
    title: String,
    initialValue: String,
    placeholder: String,
    saveLabel: String,
    cancelLabel: String,
    clearLabel: String?,
    singleLine: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SettingsOledColors.MatteCard,
        titleContentColor = SettingsOledColors.TextPrimary,
        textContentColor = SettingsOledColors.TextSecondary,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = singleLine,
                maxLines = if (singleLine) 1 else 5,
                shape = RoundedCornerShape(16.dp),
                placeholder = {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SettingsOledColors.TextMuted
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = SettingsOledColors.TextPrimary,
                    unfocusedTextColor = SettingsOledColors.TextPrimary,
                    focusedContainerColor = SettingsOledColors.IconBadge,
                    unfocusedContainerColor = SettingsOledColors.IconBadge,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = SettingsOledColors.TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(value) },
                modifier = Modifier.defaultMinSize(minHeight = DockMetrics.MinTapTarget)
            ) {
                Text(text = saveLabel, color = SettingsOledColors.TextPrimary)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (clearLabel != null && initialValue.isNotBlank()) {
                    TextButton(
                        onClick = { onSave("") },
                        modifier = Modifier.defaultMinSize(minHeight = DockMetrics.MinTapTarget),
                        colors = ButtonDefaults.textButtonColors(contentColor = SettingsOledColors.ErrorTint)
                    ) {
                        Text(text = clearLabel)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                ) {
                    Text(text = cancelLabel, color = SettingsOledColors.TextSecondary)
                }
            }
        }
    )
}

@Composable
fun SettingsConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    cancelLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SettingsOledColors.MatteCard,
        titleContentColor = SettingsOledColors.TextPrimary,
        textContentColor = SettingsOledColors.TextSecondary,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text = body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.defaultMinSize(minHeight = DockMetrics.MinTapTarget)
            ) {
                Text(text = confirmLabel, color = SettingsOledColors.ErrorTint)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = DockMetrics.MinTapTarget)
            ) {
                Text(text = cancelLabel, color = SettingsOledColors.TextSecondary)
            }
        }
    )
}
