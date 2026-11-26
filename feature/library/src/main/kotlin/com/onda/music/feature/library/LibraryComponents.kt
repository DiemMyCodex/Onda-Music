package com.onda.music.feature.library

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.onda.music.core.design.BlurDirection
import com.onda.music.core.design.GlassElevation
import com.onda.music.core.design.GlassTokens
import com.onda.music.core.design.OndaBackdropState
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.ProgressiveBlurBackground
import com.onda.music.core.design.SmoothCornerShape
import com.onda.music.core.design.damageBoundary
import com.onda.music.core.design.smokedGlass
import com.onda.music.core.model.DownloadStatus
import com.onda.music.core.model.Playlist
import com.onda.music.core.model.SourceKind
import com.onda.music.core.model.Track
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val LibraryTopInset = 12.dp
private val LibraryRow1Height = 44.dp
private val LibraryHeaderSearchGap = 16.dp
private val LibraryRowGap = 12.dp
private val LibrarySearchHeight = 48.dp
private val LibraryTabRowHeight = 44.dp
private val LibraryHeaderBottomPad = 12.dp

private val CardSurfaceObsidian = Color(0xFF141416)
private val ActionPillObsidian = Color(0xFF1E1E1E)
private val SearchPillObsidian = Color(0xFF1A1A1A)
private val CardHairlineBorder = Color(0x16FFFFFF)
private val MutedTextGray = Color(0xFF9A9AA0)

@Composable
internal fun libraryExpandedTopPadding(): Dp {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return statusBar +
        LibraryTopInset +
        LibraryRow1Height +
        LibraryHeaderSearchGap +
        LibrarySearchHeight +
        LibraryRowGap +
        LibraryTabRowHeight +
        LibraryHeaderBottomPad
}

@Composable
internal fun libraryCollapsedTopPadding(): Dp {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return statusBar +
        LibraryTopInset +
        LibrarySearchHeight +
        LibraryRowGap +
        LibraryTabRowHeight +
        LibraryHeaderBottomPad
}

@Composable
internal fun LibraryGlassTopBar(
    backdropState: OndaBackdropState,
    collapseProgress: () -> Float,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedTab: LibraryTab,
    onSelectTab: (LibraryTab) -> Unit,
    onCreatePlaylist: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    blurEnabled: Boolean = true,
    strings: LibraryStrings = resolveLibraryStrings("en")
) {
    val reducedMotion = OndaTheme.reducedMotion
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current
    val row1TotalPx = with(density) { (LibraryRow1Height + LibraryHeaderSearchGap).toPx() }
    val slideInOffsetPx = with(density) { 16.dp.toPx() }

    Box(modifier = modifier.fillMaxWidth()) {
        ProgressiveBlurBackground(
            backdropState = backdropState,
            direction = BlurDirection.BOTTOM_TO_TOP,
            maxBlurRadius = { 28f },
            foundationColor = Color.Black,
            blurEnabled = blurEnabled,
            modifier = Modifier.matchParentSize()
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBar + LibraryTopInset, bottom = LibraryHeaderBottomPad)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val p = collapseProgress().coerceIn(0f, 1f)
                        val placeable = measurable.measure(constraints)
                        val collapsedHeight = (placeable.height * (1f - p)).roundToInt().coerceIn(0, placeable.height)
                        layout(constraints.maxWidth, collapsedHeight) {
                            placeable.place(0, 0)
                        }
                    }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(LibraryRow1Height)
                            .graphicsLayer {
                                val p = collapseProgress().coerceIn(0f, 1f)
                                alpha = (1f - p * 1.75f).coerceIn(0f, 1f)
                                val s = if (reducedMotion) 1f else 1f - p * 0.08f
                                scaleX = s
                                scaleY = s
                                translationY = if (reducedMotion) 0f else -p * row1TotalPx * 0.45f
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                if (blurEnabled && !reducedMotion && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    renderEffect = if (p > 0.06f) LibraryBlurCache.blur(p * 10f) else null
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    renderEffect = null
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = strings.libraryTitle,
                            fontSize = 28.sp,
                            lineHeight = 34.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val actionsActive = collapseProgress() < 0.45f
                            ObsidianCircleActionPill(
                                icon = Icons.Rounded.Add,
                                contentDescription = strings.newPlaylistDesc,
                                enabled = actionsActive,
                                onClick = onCreatePlaylist
                            )
                            ObsidianCircleActionPill(
                                icon = Icons.Rounded.Settings,
                                contentDescription = strings.settingsDesc,
                                enabled = actionsActive,
                                onClick = onOpenSettings
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(LibraryHeaderSearchGap))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(LibrarySearchHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LibrarySearchPill(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    strings = strings,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val p = collapseProgress().coerceIn(0f, 1f)
                            val placeable = measurable.measure(constraints.copy(minWidth = 0))
                            val shownW = (placeable.width * p).roundToInt().coerceIn(0, placeable.width)
                            layout(shownW, placeable.height) {
                                placeable.place(0, 0)
                            }
                        }
                ) {
                    val inlineActive = collapseProgress() > 0.5f
                    Row(
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .graphicsLayer {
                                val p = collapseProgress().coerceIn(0f, 1f)
                                val reveal = ((p - 0.15f) / 0.85f).coerceIn(0f, 1f)
                                alpha = reveal
                                val s = if (reducedMotion) 1f else 0.78f + 0.22f * reveal
                                scaleX = s
                                scaleY = s
                                translationX = if (reducedMotion) 0f else (1f - reveal) * slideInOffsetPx
                            },
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ObsidianCircleActionPill(
                            icon = Icons.Rounded.Add,
                            contentDescription = strings.newPlaylistDesc,
                            enabled = inlineActive,
                            onClick = onCreatePlaylist
                        )
                        ObsidianCircleActionPill(
                            icon = Icons.Rounded.Settings,
                            contentDescription = strings.settingsDesc,
                            enabled = inlineActive,
                            onClick = onOpenSettings
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(LibraryRowGap))

            LibraryCategoryPills(
                selectedTab = selectedTab,
                onSelectTab = onSelectTab,
                strings = strings
            )
        }
    }
}

@Composable
private fun ObsidianCircleActionPill(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(44.dp)
            .libraryPressBounce(interactionSource, pressedScale = 0.90f)
            .clip(CircleShape)
            .background(ActionPillObsidian)
            .clickable(
                interactionSource = interactionSource,
                indication = LibraryHighlightIndication(cornerRadius = 22.dp),
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
internal fun LibrarySearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    strings: LibraryStrings = resolveLibraryStrings("en")
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    val searchShape = remember { RoundedCornerShape(999.dp) }

    Row(
        modifier = modifier
            .height(LibrarySearchHeight)
            .clip(searchShape)
            .background(SearchPillObsidian)
            .padding(start = 14.dp, end = if (query.isNotEmpty()) 4.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = if (isFocused) Color.White else MutedTextGray,
            modifier = Modifier.size(19.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = strings.searchPlaceholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF7C7C84),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused }
            )
        }
        if (query.isNotEmpty()) {
            val clearInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .libraryPressBounce(clearInteraction, pressedScale = 0.88f)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = clearInteraction,
                        indication = LibraryHighlightIndication(cornerRadius = 22.dp),
                        onClick = { onQueryChange("") }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = strings.clearSearchDesc,
                    tint = MutedTextGray,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
internal fun LibraryCategoryPills(
    selectedTab: LibraryTab,
    onSelectTab: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier,
    strings: LibraryStrings = resolveLibraryStrings("en")
) {
    val reducedMotion = OndaTheme.reducedMotion
    val scrollState = rememberScrollState()
    val tabs = LibraryTab.entries
    val tabStartsPx = remember { FloatArray(tabs.size) }
    val tabWidthsPx = remember { FloatArray(tabs.size) }
    var measuredGeneration by remember { mutableIntStateOf(0) }
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    val indicatorX = remember { Animatable(0f) }
    val indicatorWidth = remember { Animatable(0f) }
    var indicatorInitialized by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val horizontalPadPx = with(density) { 24.dp.toPx() }
    val pillVisualHeightPx = with(density) { 36.dp.toPx() }
    val pillBorderStrokePx = with(density) { 0.5.dp.toPx() }

    LaunchedEffect(selectedTab, strings.isVi, measuredGeneration, viewportWidthPx, reducedMotion) {
        val index = tabs.indexOf(selectedTab).coerceAtLeast(0)
        val targetX = tabStartsPx[index]
        val targetW = tabWidthsPx[index]
        if (targetW > 0f) {
            if (!indicatorInitialized || reducedMotion) {
                indicatorX.snapTo(targetX)
                indicatorWidth.snapTo(targetW)
                indicatorInitialized = true
            } else {
                val spec = spring<Float>(dampingRatio = 0.82f, stiffness = 380f)
                launch { indicatorX.animateTo(targetX, spec) }
                launch { indicatorWidth.animateTo(targetW, spec) }
            }
            if (viewportWidthPx > 0 && scrollState.maxValue > 0) {
                val desiredScroll = (targetX + horizontalPadPx + targetW * 0.5f - viewportWidthPx * 0.5f)
                    .roundToInt()
                    .coerceIn(0, scrollState.maxValue)
                if (reducedMotion) {
                    scrollState.scrollTo(desiredScroll)
                } else {
                    scrollState.animateScrollTo(desiredScroll)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(LibraryTabRowHeight)
            .onSizeChanged { viewportWidthPx = it.width }
            .horizontalScroll(scrollState),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(modifier = Modifier.padding(horizontal = 24.dp)) {
            Row(
                modifier = Modifier.drawBehind {
                    val pillTop = (size.height - pillVisualHeightPx) * 0.5f
                    val pillCorner = CornerRadius(pillVisualHeightPx * 0.5f)
                    for (i in tabs.indices) {
                        val w = tabWidthsPx[i]
                        if (w > 0f) {
                            val x = tabStartsPx[i]
                            drawRoundRect(
                                color = CardSurfaceObsidian,
                                topLeft = Offset(x, pillTop),
                                size = Size(w, pillVisualHeightPx),
                                cornerRadius = pillCorner
                            )
                            drawRoundRect(
                                color = Color(0x14FFFFFF),
                                topLeft = Offset(x, pillTop),
                                size = Size(w, pillVisualHeightPx),
                                cornerRadius = pillCorner,
                                style = Stroke(width = pillBorderStrokePx)
                            )
                        }
                    }
                    val activeW = indicatorWidth.value
                    if (activeW > 0f) {
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(indicatorX.value, pillTop),
                            size = Size(activeW, pillVisualHeightPx),
                            cornerRadius = pillCorner
                        )
                    }
                },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = tab == selectedTab
                    val interactionSource = remember { MutableInteractionSource() }
                    val textColor by animateColorAsState(
                        targetValue = if (selected) Color.Black else MutedTextGray,
                        animationSpec = if (reducedMotion) snap() else tween(160),
                        label = "tabPillText"
                    )

                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 44.dp, minHeight = LibraryTabRowHeight)
                            .onGloballyPositioned { coords ->
                                val newX = coords.positionInParent().x
                                val newW = coords.size.width.toFloat()
                                if (tabStartsPx[index] != newX || tabWidthsPx[index] != newW) {
                                    tabStartsPx[index] = newX
                                    tabWidthsPx[index] = newW
                                    measuredGeneration++
                                }
                            }
                            .libraryPressBounce(interactionSource, pressedScale = 0.94f)
                            .clip(RoundedCornerShape(50))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = LibraryHighlightIndication(cornerRadius = 18.dp),
                                onClick = { onSelectTab(tab) }
                            )
                            .padding(horizontal = 15.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strings.tabLabel(tab),
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = textColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LibraryOverviewHubGrid(
    playlistsCount: Int,
    playlistsCovers: List<String?>,
    localCount: Int,
    localCovers: List<String?>,
    downloadsCount: Int,
    downloadsCovers: List<String?>,
    recentCount: Int,
    recentCovers: List<String?>,
    onOpenPlaylists: () -> Unit,
    onOpenLocal: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    strings: LibraryStrings = resolveLibraryStrings("en")
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OverviewHubCard(
                title = strings.hubPlaylistsTitle,
                countText = strings.playlistCountText(playlistsCount),
                covers = playlistsCovers,
                fallbackIcon = Icons.AutoMirrored.Rounded.QueueMusic,
                onClick = onOpenPlaylists,
                modifier = Modifier.weight(1f)
            )
            OverviewHubCard(
                title = strings.hubLocalTitle,
                countText = strings.trackCountText(localCount),
                covers = localCovers,
                fallbackIcon = Icons.Rounded.Folder,
                onClick = onOpenLocal,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OverviewHubCard(
                title = strings.hubDownloadsTitle,
                countText = strings.offlineCountText(downloadsCount),
                covers = downloadsCovers,
                fallbackIcon = Icons.Rounded.DownloadDone,
                onClick = onOpenDownloads,
                modifier = Modifier.weight(1f)
            )
            OverviewHubCard(
                title = strings.hubHistoryTitle,
                countText = strings.recentCountText(recentCount),
                covers = recentCovers,
                fallbackIcon = Icons.Rounded.History,
                onClick = onOpenHistory,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun OverviewHubCard(
    title: String,
    countText: String,
    covers: List<String?>,
    fallbackIcon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = remember { SmoothCornerShape(20.dp) }
    Box(
        modifier = modifier
            .height(104.dp)
            .libraryPressBounce(interactionSource, pressedScale = 0.96f)
            .clip(shape)
            .background(CardSurfaceObsidian)
            .border(0.5.dp, CardHairlineBorder, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LibraryHighlightIndication(cornerRadius = 20.dp),
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 13.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = countText,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum"
                ),
                color = MutedTextGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        LibraryCoverStack(
            covers = covers,
            tile = 40.dp,
            cornerRadius = 8.dp,
            showFan = true,
            fallbackIcon = fallbackIcon,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 8.dp)
        )
    }
}

@Composable
internal fun LikedSongsSignatureCard(
    count: Int,
    onOpen: () -> Unit,
    onQuickShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    strings: LibraryStrings = resolveLibraryStrings("en")
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shuffleInteraction = remember { MutableInteractionSource() }
    val shape = remember { SmoothCornerShape(20.dp) }
    val badgeShape = remember { SmoothCornerShape(14.dp) }
    val cardBrush = remember {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF281018),
                Color(0xFF141416)
            )
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(86.dp)
            .libraryPressBounce(interactionSource, pressedScale = 0.97f)
            .clip(shape)
            .background(cardBrush)
            .border(0.5.dp, Color(0x22FFFFFF), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LibraryHighlightIndication(cornerRadius = 20.dp),
                onClick = onOpen
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(badgeShape)
                .background(Color(0x36FF375F))
                .border(0.5.dp, Color(0x4DFF375F), badgeShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = null,
                tint = Color(0xFFFF4D6D),
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = strings.likedSongsTitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = strings.songCountText(count),
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum"
                ),
                color = Color(0xB8FFFFFF),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .size(44.dp)
                .libraryPressBounce(shuffleInteraction, pressedScale = 0.90f)
                .clip(CircleShape)
                .background(if (count > 0) Color(0x22FFFFFF) else Color(0x0EFFFFFF))
                .border(0.5.dp, Color(0x26FFFFFF), CircleShape)
                .clickable(
                    interactionSource = shuffleInteraction,
                    indication = LibraryHighlightIndication(cornerRadius = 22.dp),
                    enabled = count > 0,
                    onClick = onQuickShuffle
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = strings.shuffleLikedDesc,
                tint = if (count > 0) Color.White else Color(0x55FFFFFF),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun RecentlyPlayedTelemetryCard(
    recentCovers: List<String?>,
    totalPlayCount: Int,
    totalPlayDurationMs: Long,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    strings: LibraryStrings = resolveLibraryStrings("en")
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = remember { SmoothCornerShape(20.dp) }
    val hoursText = remember(totalPlayDurationMs) { formatHoursListened(totalPlayDurationMs) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(86.dp)
            .libraryPressBounce(interactionSource, pressedScale = 0.97f)
            .clip(shape)
            .background(CardSurfaceObsidian)
            .border(0.5.dp, CardHairlineBorder, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LibraryHighlightIndication(cornerRadius = 20.dp),
                onClick = onOpen
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LibraryCoverStack(
            covers = recentCovers,
            tile = 40.dp,
            cornerRadius = 8.dp,
            showFan = true,
            fallbackIcon = Icons.Rounded.History
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = strings.recentlyPlayedTitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = strings.playsHoursText(totalPlayCount, hoursText),
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum"
                ),
                color = MutedTextGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Color(0xFF7C7C84),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
internal fun LibrarySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    trailingCount: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xB8FFFFFF),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            val interactionSource = remember { MutableInteractionSource() }
            val pillShape = RoundedCornerShape(50)
            Row(
                modifier = Modifier
                    .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                    .libraryPressBounce(interactionSource, pressedScale = 0.94f)
                    .clip(pillShape)
                    .background(CardSurfaceObsidian)
                    .border(0.5.dp, CardHairlineBorder, pillShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = LibraryHighlightIndication(cornerRadius = 22.dp),
                        onClick = onAction
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = actionLabel,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1
                )
            }
        } else if (trailingCount != null) {
            Text(
                text = trailingCount,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum"
                ),
                color = MutedTextGray,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun PlaylistGridCard(
    playlist: Playlist,
    covers: List<String>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    strings: LibraryStrings = resolveLibraryStrings("en")
) {
    val interactionSource = remember { MutableInteractionSource() }
    val cardShape = remember { SmoothCornerShape(18.dp) }
    val artShape = remember { SmoothCornerShape(12.dp) }
    val resolvedCovers = remember(playlist.artworkUrl, covers) {
        (listOfNotNull(playlist.artworkUrl) + covers)
            .filter { it.isNotBlank() }
            .distinct()
            .take(4)
    }
    val monogram = remember(playlist.name) {
        playlist.name.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "ON" }
    }

    Column(
        modifier = modifier
            .libraryPressBounce(interactionSource, pressedScale = 0.96f)
            .clip(cardShape)
            .background(CardSurfaceObsidian)
            .border(0.5.dp, CardHairlineBorder, cardShape)
            .clickable(
                interactionSource = interactionSource,
                indication = LibraryHighlightIndication(cornerRadius = 18.dp),
                onClick = onOpen
            )
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(artShape)
                .background(Color(0xFF1A1A20))
                .border(0.5.dp, Color(0x14FFFFFF), artShape),
            contentAlignment = Alignment.Center
        ) {
            when {
                resolvedCovers.size >= 2 -> {
                    val q0 = resolvedCovers[0]
                    val q1 = resolvedCovers[1]
                    val q2 = resolvedCovers.getOrElse(2) { resolvedCovers[0] }
                    val q3 = resolvedCovers.getOrElse(3) { resolvedCovers[1] }
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(1.5.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                        ) {
                            AsyncImage(
                                model = q0,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                            AsyncImage(
                                model = q1,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                        ) {
                            AsyncImage(
                                model = q2,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                            AsyncImage(
                                model = q3,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
                resolvedCovers.size == 1 -> {
                    AsyncImage(
                        model = resolvedCovers.first(),
                        contentDescription = playlist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF222228), Color(0xFF141418))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = monogram,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = Color(0xCCFFFFFF),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = playlist.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = strings.trackCountText(playlist.trackCount),
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                fontFeatureSettings = "tnum"
            ),
            color = MutedTextGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun QuickPlayActionRow(
    countLabel: String,
    hasTracks: Boolean,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    modifier: Modifier = Modifier,
    strings: LibraryStrings = resolveLibraryStrings("en")
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = countLabel,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = MutedTextGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(
            onClick = onShuffleAll,
            enabled = hasTracks,
            modifier = Modifier.heightIn(min = 44.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = strings.shuffleLabel, maxLines = 1)
        }
        Button(
            onClick = onPlayAll,
            enabled = hasTracks,
            modifier = Modifier.heightIn(min = 44.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = OndaTheme.colors.accent,
                contentColor = GlassTokens.AmoledBlack
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = strings.playLabel, maxLines = 1)
        }
    }
}

@Composable
internal fun PlaylistRowCard(
    playlist: Playlist,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    coverPool: List<String?> = emptyList(),
    strings: LibraryStrings = resolveLibraryStrings("en")
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val shape = remember { SmoothCornerShape(16.dp) }
    val stackCovers = remember(playlist.artworkUrl, coverPool) {
        (listOfNotNull(playlist.artworkUrl) + coverPool.filterNotNull())
            .distinct()
            .take(3)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .libraryPressBounce(interactionSource, pressedScale = 0.97f)
            .clip(shape)
            .background(CardSurfaceObsidian)
            .border(0.5.dp, CardHairlineBorder, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LibraryHighlightIndication(cornerRadius = 16.dp),
                onClick = onOpen
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LibraryCoverStack(
            covers = stackCovers,
            tile = 44.dp,
            cornerRadius = 10.dp,
            showFan = playlist.trackCount > 1 || stackCovers.size > 1
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = buildString {
                append(strings.trackCountText(playlist.trackCount))
                val desc = playlist.description
                if (!desc.isNullOrBlank()) {
                    append("  |  ")
                    append(desc)
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = MutedTextGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = strings.playlistActionsDesc,
                    tint = MutedTextGray
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                containerColor = GlassTokens.ObsidianSurface
            ) {
                DropdownMenuItem(
                    text = { Text(strings.renameLabel, color = OndaTheme.colors.textPrimary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            tint = OndaTheme.colors.textSecondary
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text(strings.deleteLabel, color = OndaTheme.colors.error) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = OndaTheme.colors.error
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
internal fun LibraryTrackRow(
    track: Track,
    isActive: Boolean,
    downloadStatus: DownloadStatus?,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    strings: LibraryStrings = resolveLibraryStrings("en")
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val shape = remember { SmoothCornerShape(16.dp) }
    val bgColor = if (isActive) Color(0xFF1C1C22) else CardSurfaceObsidian
    val borderColor = if (isActive) Color(0x30FFFFFF) else CardHairlineBorder

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .libraryPressBounce(interactionSource, pressedScale = 0.98f)
            .clip(shape)
            .background(bgColor)
            .border(0.5.dp, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LibraryHighlightIndication(cornerRadius = 16.dp),
                onClick = onPlay
            )
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtworkBox(
            artworkUrl = track.artworkUrl,
            title = track.title,
            isActive = isActive,
            isPlaying = isPlaying
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isActive) OndaTheme.colors.accent else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SourceBadge(source = track.source)
                when (downloadStatus) {
                    DownloadStatus.COMPLETED -> Icon(
                        imageVector = Icons.Rounded.DownloadDone,
                        contentDescription = strings.downloadedDesc,
                        tint = OndaTheme.colors.success,
                        modifier = Modifier.size(14.dp)
                    )
                    DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = OndaTheme.colors.accent
                    )
                    else -> Unit
                }
                Text(
                    text = "${track.artist}  |  ${formatDurationMs(track.durationMs)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = MutedTextGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(
            onClick = onToggleLike,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        ) {
            Icon(
                imageVector = if (track.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (track.liked) strings.unlikeLabel else strings.likeLabel,
                tint = if (track.liked) GlassTokens.ErrorCoral else MutedTextGray
            )
        }
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = strings.trackActionsDesc,
                    tint = MutedTextGray
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                containerColor = GlassTokens.ObsidianSurface
            ) {
                DropdownMenuItem(
                    text = { Text(strings.addToPlaylistLabel, color = OndaTheme.colors.textPrimary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = null,
                            tint = OndaTheme.colors.accent
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onAddToPlaylist()
                    }
                )
                val downloadLabel = when (downloadStatus) {
                    DownloadStatus.COMPLETED -> strings.removeDownloadLabel
                    DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> strings.cancelDownloadLabel
                    DownloadStatus.FAILED -> strings.retryDownloadLabel
                    null -> strings.downloadOfflineLabel
                }
                DropdownMenuItem(
                    text = { Text(downloadLabel, color = OndaTheme.colors.textPrimary) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (downloadStatus == DownloadStatus.COMPLETED) {
                                Icons.Rounded.DeleteOutline
                            } else {
                                Icons.Rounded.Download
                            },
                            contentDescription = null,
                            tint = if (downloadStatus == DownloadStatus.COMPLETED) {
                                OndaTheme.colors.error
                            } else {
                                OndaTheme.colors.accentSoft
                            }
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onDownloadClick()
                    }
                )
            }
        }
    }
}

@Composable
internal fun TrackArtworkBox(
    artworkUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false
) {
    val artShape = remember { SmoothCornerShape(10.dp) }
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(artShape)
            .background(Color(0xFF1F1F24))
            .border(0.5.dp, Color(0x16FFFFFF), artShape),
        contentAlignment = Alignment.Center
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = OndaTheme.colors.accentSoft,
                modifier = Modifier.size(22.dp)
            )
        }
        if (isActive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(GlassTokens.AmoledBlack.copy(alpha = 0.52f)),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    LibraryPlayingIndicator(
                        color = OndaTheme.colors.accent,
                        isPlaying = true,
                        modifier = Modifier
                            .height(14.dp)
                            .damageBoundary()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = null,
                        tint = OndaTheme.colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun SourceBadge(
    source: SourceKind,
    modifier: Modifier = Modifier
) {
    val label = when (source) {
        SourceKind.LOCAL -> "LOCAL"
        SourceKind.MONOCHROME -> "FLAC"
        SourceKind.YOUTUBE -> "YTM"
    }
    val tint = when (source) {
        SourceKind.LOCAL -> OndaTheme.colors.success
        SourceKind.MONOCHROME -> OndaTheme.colors.accent
        SourceKind.YOUTUBE -> OndaTheme.colors.accentSecondary
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(GlassTokens.IconBadgeSurface)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1
        )
    }
}

@Composable
internal fun DownloadRowCard(
    entry: DownloadEntryUi,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
    onDeleteOrCancel: () -> Unit,
    modifier: Modifier = Modifier,
    strings: LibraryStrings = resolveLibraryStrings("en")
) {
    val item = entry.item
    val track = entry.track
    val title = track?.title ?: item.trackId
    val artist = track?.artist ?: strings.savedTrackFallbackArtist
    val isActive = item.status == DownloadStatus.QUEUED || item.status == DownloadStatus.DOWNLOADING
    val reducedMotion = OndaTheme.reducedMotion
    val smoothedProgress by animateFloatAsState(
        targetValue = item.progress.coerceIn(0f, 1f),
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "downloadRowProgress"
    )
    val interactionSource = remember { MutableInteractionSource() }
    val shape = remember { SmoothCornerShape(16.dp) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .libraryPressBounce(interactionSource, pressedScale = 0.98f)
            .clip(shape)
            .background(CardSurfaceObsidian)
            .border(0.5.dp, CardHairlineBorder, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LibraryHighlightIndication(cornerRadius = 16.dp),
                enabled = item.status == DownloadStatus.COMPLETED && track != null,
                onClick = onPlay
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackArtworkBox(artworkUrl = track?.artworkUrl, title = title)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val statusLabel = when (item.status) {
                    DownloadStatus.QUEUED -> strings.downloadQueuedText(artist)
                    DownloadStatus.DOWNLOADING -> {
                        val pct = (item.progress * 100f).toInt().coerceIn(0, 100)
                        strings.downloadingPercentText(artist, pct)
                    }
                    DownloadStatus.COMPLETED -> "$artist  |  ${formatBytes(item.totalBytes.coerceAtLeast(item.bytesDownloaded))}"
                    DownloadStatus.FAILED -> strings.downloadFailedText(artist)
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = if (item.status == DownloadStatus.FAILED) OndaTheme.colors.error else MutedTextGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.status == DownloadStatus.FAILED && track != null) {
                IconButton(
                    onClick = onRetry,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = strings.retryDownloadDesc,
                        tint = OndaTheme.colors.accent
                    )
                }
            }
            IconButton(
                onClick = onDeleteOrCancel,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Rounded.Close else Icons.Rounded.DeleteOutline,
                    contentDescription = if (isActive) strings.cancelDownloadDesc else strings.deleteDownloadDesc,
                    tint = if (isActive) MutedTextGray else OndaTheme.colors.error
                )
            }
        }
        if (isActive) {
            LinearProgressIndicator(
                progress = { smoothedProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = OndaTheme.colors.accent,
                trackColor = GlassTokens.GlassHighlight.copy(alpha = 0.14f)
            )
        }
    }
}

@Composable
internal fun LocalScannerBanner(
    hasPermission: Boolean,
    isScanning: Boolean,
    lastScanMessage: String?,
    onRequestPermission: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
    strings: LibraryStrings = resolveLibraryStrings("en")
) {
    val shape = remember { SmoothCornerShape(18.dp) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardSurfaceObsidian)
            .border(0.5.dp, CardHairlineBorder, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = OndaTheme.colors.accent
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.localScannerTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = lastScanMessage ?: if (hasPermission) {
                        strings.localScannerReadyDesc
                    } else {
                        strings.localScannerPermissionDesc
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedTextGray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = if (hasPermission) onScan else onRequestPermission,
                enabled = !isScanning,
                modifier = Modifier.heightIn(min = 44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OndaTheme.colors.accent,
                    contentColor = GlassTokens.AmoledBlack
                )
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = GlassTokens.AmoledBlack
                    )
                } else {
                    Text(
                        text = if (hasPermission) strings.scanLabel else strings.allowLabel,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
internal fun RelinkTracksCard(
    tracks: List<Track>,
    onRelinkTrack: (Track) -> Unit,
    modifier: Modifier = Modifier,
    strings: LibraryStrings = resolveLibraryStrings("en")
) {
    val shape = remember { SmoothCornerShape(18.dp) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardSurfaceObsidian)
            .border(0.5.dp, OndaTheme.colors.warning.copy(alpha = 0.28f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = strings.needsRelinkTitle(tracks.size),
            style = MaterialTheme.typography.titleSmall,
            color = OndaTheme.colors.warning
        )
        Text(
            text = strings.needsRelinkDesc,
            style = MaterialTheme.typography.bodySmall,
            color = MutedTextGray
        )
        tracks.take(4).forEach { track ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedTextGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = { onRelinkTrack(track) },
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = strings.relinkLabel, maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun EmptyLibrarySection(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val shape = remember { SmoothCornerShape(20.dp) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardSurfaceObsidian)
            .border(0.5.dp, CardHairlineBorder, shape)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = OndaTheme.colors.accentSoft,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MutedTextGray
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.heightIn(min = 44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OndaTheme.colors.accent,
                    contentColor = GlassTokens.AmoledBlack
                )
            ) {
                Text(text = actionLabel)
            }
        }
    }
}

@Composable
internal fun PlaylistEditorDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    initialDescription: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit,
    strings: LibraryStrings = resolveLibraryStrings("en")
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var description by remember(initialDescription) { mutableStateOf(initialDescription) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GlassTokens.ObsidianSurface,
        titleContentColor = OndaTheme.colors.textPrimary,
        textContentColor = OndaTheme.colors.textSecondary,
        title = { Text(text = title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.playlistNameFieldLabel) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = OndaTheme.colors.textPrimary,
                        unfocusedTextColor = OndaTheme.colors.textPrimary,
                        focusedBorderColor = OndaTheme.colors.accent,
                        unfocusedBorderColor = GlassTokens.HairlineDivider
                    )
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(strings.descriptionOptionalLabel) },
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = OndaTheme.colors.textPrimary,
                        unfocusedTextColor = OndaTheme.colors.textPrimary,
                        focusedBorderColor = OndaTheme.colors.accent,
                        unfocusedBorderColor = GlassTokens.HairlineDivider
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), description.trim().takeIf { it.isNotEmpty() }) },
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OndaTheme.colors.accent,
                    contentColor = GlassTokens.AmoledBlack
                )
            ) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(text = strings.cancelLabel, color = OndaTheme.colors.textSecondary)
            }
        }
    )
}

@Composable
internal fun DeletePlaylistDialog(
    playlistName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    strings: LibraryStrings = resolveLibraryStrings("en")
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GlassTokens.ObsidianSurface,
        titleContentColor = OndaTheme.colors.textPrimary,
        textContentColor = OndaTheme.colors.textSecondary,
        title = { Text(text = strings.deletePlaylistTitle) },
        text = { Text(text = strings.deletePlaylistConfirmText(playlistName)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OndaTheme.colors.error,
                    contentColor = GlassTokens.AmoledBlack
                )
            ) {
                Text(text = strings.deleteLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(text = strings.cancelLabel, color = OndaTheme.colors.textSecondary)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickPlaylistSheet(
    track: Track,
    playlists: List<Playlist>,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: () -> Unit,
    onDismiss: () -> Unit,
    strings: LibraryStrings = resolveLibraryStrings("en")
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = GlassTokens.ObsidianSurface,
        contentColor = OndaTheme.colors.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.addToPlaylistSheetTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = OndaTheme.colors.textPrimary
                    )
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = OndaTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = onCreatePlaylist,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = strings.newLabel, color = OndaTheme.colors.accent)
                }
            }
            if (playlists.isEmpty()) {
                EmptyLibrarySection(
                    title = strings.noPlaylistsYetTitle,
                    subtitle = strings.noPlaylistsSheetSubtitle
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .smokedGlass(
                                    shape = RoundedCornerShape(GlassTokens.RadiusMd),
                                    elevation = GlassElevation.CARD
                                )
                                .clickable { onSelectPlaylist(playlist) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = OndaTheme.colors.accent
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = OndaTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${playlist.trackCount}",
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                color = OndaTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddTracksToPlaylistSheet(
    playlistName: String,
    candidateTracks: List<Track>,
    existingTrackIds: Set<String>,
    onAddTrack: (Track) -> Unit,
    onDismiss: () -> Unit,
    strings: LibraryStrings = resolveLibraryStrings("en")
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = GlassTokens.ObsidianSurface,
        contentColor = OndaTheme.colors.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = strings.addTracksToPlaylistTitle(playlistName),
                style = MaterialTheme.typography.titleLarge,
                color = OndaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (candidateTracks.isEmpty()) {
                EmptyLibrarySection(
                    title = strings.noSavedTracksTitle,
                    subtitle = strings.noSavedTracksSubtitle
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    items(candidateTracks, key = { it.id }) { track ->
                        val added = track.id in existingTrackIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 60.dp)
                                .smokedGlass(
                                    shape = RoundedCornerShape(GlassTokens.RadiusMd),
                                    elevation = GlassElevation.CARD
                                )
                                .clickable(enabled = !added) { onAddTrack(track) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TrackArtworkBox(artworkUrl = track.artworkUrl, title = track.title)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = OndaTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OndaTheme.colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { if (!added) onAddTrack(track) },
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Icon(
                                    imageVector = if (added) Icons.Rounded.Check else Icons.Rounded.Add,
                                    contentDescription = if (added) strings.addedDesc else strings.addTrackDesc,
                                    tint = if (added) OndaTheme.colors.success else OndaTheme.colors.accent
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

internal fun trackIndexLabel(track: Track): String? {
    val ch = track.title.trim().firstOrNull() ?: return null
    return if (ch.isLetter()) ch.uppercaseChar().toString() else "#"
}

internal fun formatDurationMs(durationMs: Long): String {
    if (durationMs <= 0L) return "0:00"
    val totalSeconds = durationMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

internal fun formatHoursListened(durationMs: Long): String {
    val safeMs = durationMs.coerceAtLeast(0L)
    val totalMinutes = safeMs / 60_000L
    val hours = totalMinutes / 60L
    val tenths = ((safeMs % 3_600_000L) * 10L) / 3_600_000L
    return if (hours >= 10L) {
        "${hours}h"
    } else {
        "${hours}.${tenths}h"
    }
}

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
    }
}
