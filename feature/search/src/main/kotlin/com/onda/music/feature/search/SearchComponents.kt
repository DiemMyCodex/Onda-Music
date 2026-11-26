package com.onda.music.feature.search

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.GlassElevation
import com.onda.music.core.design.GlassSurface
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.smokedGlass
import com.onda.music.core.model.Album
import com.onda.music.core.model.Artist
import com.onda.music.core.model.MoodCategory
import com.onda.music.core.model.Playlist
import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.Track
import kotlinx.coroutines.launch

private val SearchTitleBlockHeight = 62.dp
private val SearchTopInset = 12.dp

@Composable
internal fun searchExpandedTopPadding(hasFilterRow: Boolean): Dp {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return statusBar + SearchTopInset + SearchTitleBlockHeight + 52.dp + (if (hasFilterRow) 62.dp else 14.dp)
}

@Composable
internal fun SearchGlassTopBar(
    collapseProgress: () -> Float,
    onOpenSettings: () -> Unit,
    below: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    glassBlurEnabled: Boolean = true,
    strings: SearchStrings = resolveSearchStrings("en"),
    searchField: @Composable () -> Unit
) {
    val reducedMotion = OndaTheme.reducedMotion
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current
    val titleTravelPx = with(density) { SearchTitleBlockHeight.toPx() }
    val settingsReservePx = with(density) { 56.dp.toPx() }
    val settingsDropPx = with(density) { (52.dp / 2 - SearchTitleBlockHeight / 2).toPx() }

    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    onDrawBehind {
                        val p = collapseProgress().coerceIn(0f, 1f)
                        if (p > 0.01f) {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to GlassTokens.AmoledBlack.copy(alpha = (0.96f * p).coerceIn(0f, 1f)),
                                        0.62f to GlassTokens.AmoledBlack.copy(alpha = (0.82f * p).coerceIn(0f, 1f)),
                                        1.0f to Color.Transparent
                                    )
                                )
                            )
                        }
                    }
                }
        )

        Column(modifier = Modifier.padding(top = statusBar + SearchTopInset)) {
            Box(
                modifier = Modifier
                    .height(SearchTitleBlockHeight)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .graphicsLayer {
                        val p = collapseProgress().coerceIn(0f, 1f)
                        alpha = (1f - p * 1.6f).coerceIn(0f, 1f)
                        translationY = -p * titleTravelPx
                        val s = 1f - p * 0.16f
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        if (!reducedMotion && glassBlurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            renderEffect = if (p > 0.08f) SearchBlurCache.blur(p * 12f) else null
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            renderEffect = null
                        }
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                Column(modifier = Modifier.padding(end = 56.dp)) {
                    Text(
                        text = strings.screenTitle,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 34.sp
                        ),
                        color = OndaTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = strings.screenSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = OndaTheme.colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = -collapseProgress().coerceIn(0f, 1f) * titleTravelPx
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Box(
                        modifier = Modifier.layout { measurable, constraints ->
                            val reserve = (settingsReservePx * collapseProgress().coerceIn(0f, 1f)).toInt()
                            val width = (constraints.maxWidth - reserve).coerceAtLeast(0)
                            val placeable = measurable.measure(
                                constraints.copy(minWidth = width, maxWidth = width)
                            )
                            layout(constraints.maxWidth, placeable.height) {
                                placeable.place(0, 0)
                            }
                        }
                    ) {
                        searchField()
                    }
                }
                if (below != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    below()
                }
            }
        }

        val settingsInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = statusBar + SearchTopInset, end = 16.dp)
                .height(SearchTitleBlockHeight)
                .graphicsLayer {
                    translationY = collapseProgress().coerceIn(0f, 1f) * settingsDropPx
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                    .size(DockMetrics.MinTapTarget)
                    .searchPressBounce(settingsInteraction, pressedScale = 0.90f)
                    .smokedGlass(
                        shape = CircleShape,
                        elevation = GlassElevation.SUBTLE,
                        accentTint = OndaTheme.colors.accentSoft
                    )
                    .clickable(
                        interactionSource = settingsInteraction,
                        indication = SearchHighlightIndication(cornerRadius = 24.dp),
                        onClick = onOpenSettings
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = strings.settingsDescription,
                    tint = OndaTheme.colors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
internal fun SmokedSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    isFocused: Boolean = false,
    onFocusChange: (Boolean) -> Unit = {},
    onDismissFocus: (() -> Unit)? = null,
    strings: SearchStrings = resolveSearchStrings("en")
) {
    val shape = RoundedCornerShape(GlassTokens.RadiusPill)
    val showBackArrow = (isFocused || query.isNotEmpty()) && onDismissFocus != null
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp),
        elevation = if (isFocused) GlassElevation.SHEET else GlassElevation.CARD,
        shape = shape,
        accentTint = OndaTheme.colors.accentSoft
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .padding(start = if (showBackArrow) 4.dp else 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBackArrow) {
                IconButton(
                    onClick = { onDismissFocus?.invoke() },
                    modifier = Modifier.defaultMinSize(
                        minWidth = DockMetrics.MinTapTarget,
                        minHeight = DockMetrics.MinTapTarget
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = strings.backDescription,
                        tint = OndaTheme.colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = OndaTheme.colors.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = strings.searchPlaceholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OndaTheme.colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = OndaTheme.colors.textPrimary
                    ),
                    cursorBrush = SolidColor(OndaTheme.colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { onFocusChange(it.isFocused) }
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.defaultMinSize(
                        minWidth = DockMetrics.MinTapTarget,
                        minHeight = DockMetrics.MinTapTarget
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = strings.clearSearchDescription,
                        tint = OndaTheme.colors.textSecondary
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(10.dp))
            }
        }
    }
}

@Composable
internal fun SourceFilterRow(
    activeFilter: SearchSourceFilter,
    onSelectFilter: (SearchSourceFilter) -> Unit,
    modifier: Modifier = Modifier,
    strings: SearchStrings = resolveSearchStrings("en")
) {
    val reducedMotion = OndaTheme.reducedMotion
    val scrollState = rememberScrollState()
    val chipBounds = remember { mutableStateMapOf<SearchSourceFilter, Pair<Float, Float>>() }
    val pillX = remember { Animatable(0f) }
    val pillWidth = remember { Animatable(0f) }
    var initialized by remember { mutableStateOf(false) }
    val activeBounds = chipBounds[activeFilter]

    LaunchedEffect(activeFilter, activeBounds, reducedMotion) {
        if (activeBounds == null) return@LaunchedEffect
        if (!initialized || reducedMotion) {
            pillX.snapTo(activeBounds.first)
            pillWidth.snapTo(activeBounds.second)
            initialized = true
        } else {
            launch {
                pillX.animateTo(
                    activeBounds.first,
                    spring(dampingRatio = 0.86f, stiffness = 420f)
                )
            }
            launch {
                pillWidth.animateTo(
                    activeBounds.second,
                    spring(dampingRatio = 0.86f, stiffness = 420f)
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp)
    ) {
        Box(
            modifier = Modifier.drawBehind {
                val pillRadius = size.height / 2f
                val borderPx = GlassTokens.BorderHairline.toPx()
                val activeBorderPx = GlassTokens.BorderStandard.toPx()
                chipBounds.forEach { (_, bounds) ->
                    drawRoundRect(
                        color = GlassTokens.SmokedGlassCard,
                        topLeft = Offset(bounds.first, 0f),
                        size = Size(bounds.second, size.height),
                        cornerRadius = CornerRadius(pillRadius, pillRadius)
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.10f),
                        topLeft = Offset(bounds.first, 0f),
                        size = Size(bounds.second, size.height),
                        cornerRadius = CornerRadius(pillRadius, pillRadius),
                        style = Stroke(borderPx)
                    )
                }
                if (pillWidth.value > 0f) {
                    drawRoundRect(
                        color = GlassTokens.IconBadgeSurface,
                        topLeft = Offset(pillX.value, 0f),
                        size = Size(pillWidth.value, size.height),
                        cornerRadius = CornerRadius(pillRadius, pillRadius)
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.20f),
                        topLeft = Offset(pillX.value, 0f),
                        size = Size(pillWidth.value, size.height),
                        cornerRadius = CornerRadius(pillRadius, pillRadius),
                        style = Stroke(activeBorderPx)
                    )
                }
            }
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchSourceFilter.entries.forEach { filter ->
                    val selected = filter == activeFilter
                    val shape = RoundedCornerShape(GlassTokens.RadiusPill)
                    val interactionSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
                            .onGloballyPositioned { coords ->
                                chipBounds[filter] = coords.positionInParent().x to coords.size.width.toFloat()
                            }
                            .searchPressBounce(interactionSource, pressedScale = 0.94f)
                            .clip(shape)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = SearchHighlightIndication(cornerRadius = GlassTokens.RadiusPill),
                                onClick = { onSelectFilter(filter) }
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strings.filterLabel(filter),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) GlassTokens.TextPrimary else GlassTokens.TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LinkImportPromoCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    strings: SearchStrings = resolveSearchStrings("en")
) {
    val shape = RoundedCornerShape(GlassTokens.RadiusLg)
    val interactionSource = remember { MutableInteractionSource() }
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .searchPressBounce(interactionSource, pressedScale = 0.97f)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = SearchHighlightIndication(cornerRadius = GlassTokens.RadiusLg),
                onClick = onClick
            ),
        elevation = GlassElevation.HERO,
        shape = shape,
        accentTint = OndaTheme.colors.accent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 72.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(GlassTokens.RadiusSm))
                    .background(GlassTokens.IconBadgeSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = null,
                    tint = GlassTokens.TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.importLinkTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = OndaTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = strings.importLinkSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = OndaTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = strings.openLinkImporterDescription,
                tint = OndaTheme.colors.textSecondary
            )
        }
    }
}

@Composable
internal fun SearchHistorySection(
    history: List<String>,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    strings: SearchStrings = resolveSearchStrings("en")
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = strings.recentSearchesTitle,
            actionLabel = strings.clearAction,
            onActionClick = onClearAll
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(history, key = { it }) { entry ->
                HistoryChip(
                    query = entry,
                    onClick = { onSelect(entry) },
                    onDelete = { onDelete(entry) },
                    strings = strings
                )
            }
        }
    }
}

@Composable
private fun HistoryChip(
    query: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    strings: SearchStrings = resolveSearchStrings("en")
) {
    val shape = RoundedCornerShape(GlassTokens.RadiusPill)
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
            .searchPressBounce(interactionSource, pressedScale = 0.95f)
            .smokedGlass(shape = shape, elevation = GlassElevation.SUBTLE)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = SearchHighlightIndication(cornerRadius = GlassTokens.RadiusPill),
                onClick = onClick
            )
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.History,
            contentDescription = null,
            tint = OndaTheme.colors.textTertiary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = query,
            style = MaterialTheme.typography.labelLarge,
            color = OndaTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier.defaultMinSize(
                minWidth = DockMetrics.MinTapTarget,
                minHeight = DockMetrics.MinTapTarget
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = strings.removeHistoryDescription(query),
                tint = OndaTheme.colors.textTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
internal fun MoodCategoryRow(
    pair: List<MoodCategory>,
    onSelect: (MoodCategory) -> Unit,
    modifier: Modifier = Modifier,
    coverUrls: List<String?> = emptyList()
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        pair.forEachIndexed { idx, category ->
            val tileCovers = if (coverUrls.isNotEmpty()) {
                listOfNotNull(
                    coverUrls.getOrNull((idx * 2) % coverUrls.size),
                    coverUrls.getOrNull((idx * 2 + 1) % coverUrls.size)
                )
            } else {
                emptyList()
            }
            MoodCategoryCard(
                category = category,
                covers = tileCovers,
                onClick = { onSelect(category) },
                modifier = Modifier.weight(1f)
            )
        }
        if (pair.size == 1) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun MoodCategoryCard(
    category: MoodCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    covers: List<String?> = emptyList()
) {
    val accent = remember(category.accentColorHex) {
        liftMoodTint(category.accentColorHex)
    }
    var titleWraps by remember(category.title) { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val tileSurface = Color(0xFF121214)
    val tileBrush = remember(accent) {
        Brush.linearGradient(
            colors = listOf(
                blendColorOver(accent.copy(alpha = 0.52f), tileSurface),
                blendColorOver(accent.copy(alpha = 0.16f), tileSurface)
            )
        )
    }

    Box(
        modifier = modifier
            .height(76.dp)
            .searchPressBounce(interactionSource, pressedScale = 0.96f)
            .clip(RoundedCornerShape(14.dp))
            .background(brush = tileBrush)
            .border(0.75.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = SearchHighlightIndication(cornerRadius = 14.dp),
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 2.dp)
                .size(width = 84.dp, height = 76.dp)
                .then(
                    if (titleWraps) {
                        Modifier.graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                    } else {
                        Modifier
                    }
                )
                .drawWithCache {
                    val fade = Brush.horizontalGradient(
                        0.00f to Color.Transparent,
                        0.42f to Color.Black.copy(alpha = 0.55f),
                        0.70f to Color.Black
                    )
                    onDrawWithContent {
                        drawContent()
                        if (titleWraps) {
                            drawRect(brush = fade, blendMode = BlendMode.DstIn)
                        }
                    }
                }
        ) {
            MoodLayeredTile(
                url = covers.getOrNull(1),
                accent = accent,
                rotation = -24f,
                offsetX = (-2).dp,
                offsetY = 2.dp,
                size = 44.dp
            )
            MoodLayeredTile(
                url = covers.getOrNull(0),
                accent = accent,
                rotation = -6f,
                offsetX = 28.dp,
                offsetY = 13.dp,
                size = 48.dp
            )
        }

        Text(
            text = category.title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp
            ),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                val wraps = result.lineCount > 1
                if (wraps != titleWraps) titleWraps = wraps
            },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 14.dp, end = 68.dp)
        )
    }
}

@Composable
private fun MoodLayeredTile(
    url: String?,
    accent: Color,
    rotation: Float,
    offsetX: Dp,
    offsetY: Dp,
    size: Dp
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                rotationZ = rotation
                translationX = with(density) { offsetX.toPx() }
                translationY = with(density) { offsetY.toPx() }
                shape = RoundedCornerShape(8.dp)
                clip = true
            }
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        blendColorOver(accent.copy(alpha = 0.36f), Color(0xFF1D1D20)),
                        Color(0xFF1A1A1D)
                    )
                )
            )
            .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(size * 0.42f)
            )
        }
    }
}

private fun liftMoodTint(accentColorHex: Long): Color {
    val argb = accentColorHex.toInt()
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    if (r == 0 && g == 0 && b == 0) return Color(0xFF3A3A44)
    val lift = { c: Int -> (c + (170 - c) * 0.45f).toInt().coerceIn(40, 220) }
    return Color(lift(r), lift(g), lift(b))
}

private fun blendColorOver(top: Color, bottom: Color): Color {
    val a = top.alpha
    return Color(
        red = top.red * a + bottom.red * (1f - a),
        green = top.green * a + bottom.green * (1f - a),
        blue = top.blue * a + bottom.blue * (1f - a),
        alpha = 1f
    )
}

@Composable
internal fun MoodLoadFallbackCard(
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    strings: SearchStrings = resolveSearchStrings("en")
) {
    if (isLoading) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SearchLoadingDots(dotColor = OndaTheme.colors.accent)
                Text(
                    text = strings.loadingMoods,
                    style = MaterialTheme.typography.bodySmall,
                    color = OndaTheme.colors.textSecondary
                )
            }
            MoodGridSkeleton()
        }
        return
    }

    val shape = RoundedCornerShape(GlassTokens.RadiusMd)
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = GlassElevation.SUBTLE,
        shape = shape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = errorMessage ?: strings.noMoodsAvailable,
                style = MaterialTheme.typography.bodySmall,
                color = OndaTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onRetry,
                modifier = Modifier.defaultMinSize(
                    minWidth = DockMetrics.MinTapTarget,
                    minHeight = DockMetrics.MinTapTarget
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = strings.retryMoodsDescription,
                    tint = OndaTheme.colors.accent
                )
            }
        }
    }
}

@Composable
internal fun SearchSuggestionRow(
    suggestion: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = OndaTheme.colors.textTertiary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = suggestion,
            style = MaterialTheme.typography.bodyMedium,
            color = OndaTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun SearchTrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier,
    strings: SearchStrings = resolveSearchStrings("en")
) {
    val shape = RoundedCornerShape(GlassTokens.RadiusMd)
    val durationLabel = SearchLogic.formatDuration(track.durationMs)
    val trackAlbum = track.album
    val subtitle = buildString {
        if (track.artist.isNotBlank()) append(track.artist)
        if (!trackAlbum.isNullOrBlank()) {
            if (isNotEmpty()) append(" • ")
            append(trackAlbum)
        }
        if (durationLabel.isNotEmpty()) {
            if (isNotEmpty()) append(" • ")
            append(durationLabel)
        }
    }.ifBlank { strings.trackFallbackLabel }

    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .searchPressBounce(interactionSource, pressedScale = 0.98f)
            .clip(shape)
            .then(
                if (isCurrent) {
                    Modifier.smokedGlass(
                        shape = shape,
                        elevation = GlassElevation.CARD,
                        accentTint = OndaTheme.colors.accent
                    )
                } else {
                    Modifier.background(Color.Transparent, shape)
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = SearchHighlightIndication(cornerRadius = GlassTokens.RadiusMd),
                onClick = onClick
            )
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkThumbnail(
            artworkUrl = track.artworkUrl,
            placeholderIcon = Icons.Rounded.MusicNote,
            size = 48.dp,
            cornerRadius = GlassTokens.RadiusSm,
            isActive = isCurrent,
            isPlaying = isPlaying,
            strings = strings
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isCurrent) OndaTheme.colors.accent else OndaTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.source != SourceKind.YOUTUBE) {
                    Spacer(modifier = Modifier.width(6.dp))
                    SourceTagBadge(source = track.source, strings = strings)
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = OndaTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onToggleLike,
            modifier = Modifier.defaultMinSize(
                minWidth = DockMetrics.MinTapTarget,
                minHeight = DockMetrics.MinTapTarget
            )
        ) {
            Icon(
                imageVector = if (track.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (track.liked) strings.unlikeDescription else strings.likeDescription,
                tint = if (track.liked) GlassTokens.ErrorCoral else OndaTheme.colors.textTertiary
            )
        }
    }
}

@Composable
internal fun SourceTagBadge(
    source: SourceKind,
    strings: SearchStrings = resolveSearchStrings("en")
) {
    val (label, tint) = when (source) {
        SourceKind.MONOCHROME -> strings.badgeFlac to GlassTokens.TextPrimary
        SourceKind.LOCAL -> strings.badgeLocal to GlassTokens.SuccessMint
        SourceKind.YOUTUBE -> strings.badgeYt to GlassTokens.TextSecondary
    }
    val shape = RoundedCornerShape(GlassTokens.RadiusXs)
    Row(
        modifier = Modifier
            .background(GlassTokens.IconBadgeSurface, shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (source == SourceKind.LOCAL) Icons.Rounded.Smartphone else Icons.Rounded.HighQuality,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(11.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = tint,
            maxLines = 1
        )
    }
}

@Composable
internal fun SearchAlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onCaptureBounds: (Rect) -> Unit = {},
    strings: SearchStrings = resolveSearchStrings("en")
) {
    val shape = RoundedCornerShape(GlassTokens.RadiusMd)
    val interactionSource = remember { MutableInteractionSource() }
    var cardBounds by remember { mutableStateOf<Rect?>(null) }

    GlassSurface(
        modifier = modifier
            .width(148.dp)
            .onGloballyPositioned { cardBounds = it.boundsInRoot() }
            .searchPressBounce(interactionSource, pressedScale = 0.96f)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = SearchHighlightIndication(cornerRadius = GlassTokens.RadiusMd),
                onClick = {
                    cardBounds?.let(onCaptureBounds)
                    onClick()
                }
            ),
        elevation = GlassElevation.CARD,
        shape = shape
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            ArtworkThumbnail(
                artworkUrl = album.artworkUrl,
                placeholderIcon = Icons.Rounded.Album,
                size = 128.dp,
                cornerRadius = GlassTokens.RadiusSm,
                strings = strings
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleSmall,
                color = OndaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = album.artist.ifBlank { strings.albumFallbackLabel },
                style = MaterialTheme.typography.bodySmall,
                color = OndaTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun SearchPlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onCaptureBounds: (Rect) -> Unit = {},
    strings: SearchStrings = resolveSearchStrings("en")
) {
    val shape = RoundedCornerShape(GlassTokens.RadiusMd)
    val interactionSource = remember { MutableInteractionSource() }
    var cardBounds by remember { mutableStateOf<Rect?>(null) }

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .onGloballyPositioned { cardBounds = it.boundsInRoot() }
            .searchPressBounce(interactionSource, pressedScale = 0.97f)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = SearchHighlightIndication(cornerRadius = GlassTokens.RadiusMd),
                onClick = {
                    cardBounds?.let(onCaptureBounds)
                    onClick()
                }
            ),
        elevation = GlassElevation.CARD,
        shape = shape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 68.dp)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkThumbnail(
                artworkUrl = playlist.artworkUrl,
                placeholderIcon = Icons.Rounded.QueueMusic,
                size = 48.dp,
                cornerRadius = GlassTokens.RadiusSm,
                strings = strings
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = OndaTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (playlist.trackCount > 0) {
                        strings.trackCountLabel(playlist.trackCount)
                    } else {
                        strings.ytMusicPlaylistSubtitle
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = OndaTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = strings.inspectPlaylistDescription,
                tint = OndaTheme.colors.textTertiary
            )
        }
    }
}

@Composable
internal fun SearchArtistChip(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    strings: SearchStrings = resolveSearchStrings("en")
) {
    val shape = RoundedCornerShape(GlassTokens.RadiusPill)
    val interactionSource = remember { MutableInteractionSource() }
    GlassSurface(
        modifier = modifier
            .defaultMinSize(minHeight = DockMetrics.MinTapTarget)
            .searchPressBounce(interactionSource, pressedScale = 0.96f)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = SearchHighlightIndication(cornerRadius = GlassTokens.RadiusPill),
                onClick = onClick
            ),
        elevation = GlassElevation.SUBTLE,
        shape = shape
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkThumbnail(
                artworkUrl = artist.thumbnailUrl,
                placeholderIcon = Icons.Rounded.Person,
                size = 36.dp,
                cornerRadius = GlassTokens.RadiusPill,
                strings = strings
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = artist.name,
                style = MaterialTheme.typography.labelLarge,
                color = OndaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun ArtworkThumbnail(
    artworkUrl: String?,
    placeholderIcon: androidx.compose.ui.graphics.vector.ImageVector,
    size: Dp,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = true,
    strings: SearchStrings = resolveSearchStrings("en")
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(GlassTokens.IconBadgeSurface, shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = placeholderIcon,
            contentDescription = null,
            tint = OndaTheme.colors.textTertiary,
            modifier = Modifier.size((size * 0.45f).coerceAtLeast(18.dp))
        )
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (isActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GlassTokens.AmoledBlack.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    SearchPlayingIndicator(
                        color = OndaTheme.colors.accent,
                        isPlaying = true,
                        modifier = Modifier.height((size * 0.3f).coerceIn(12.dp, 18.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = strings.pausedDescription,
                        tint = OndaTheme.colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = OndaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = OndaTheme.colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (actionLabel != null && onActionClick != null) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = DockMetrics.MinTapTarget)
                    .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = OndaTheme.colors.accent
                )
            }
        }
    }
}
