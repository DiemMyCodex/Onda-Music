package com.onda.music.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.content.ContextCompat
import com.onda.music.core.database.OndaUserSettings
import com.onda.music.core.design.DockMetrics
import com.onda.music.core.design.OndaAnimatedBrandMark
import com.onda.music.core.design.OndaPagerIndicator
import com.onda.music.core.design.OndaTheme
import com.onda.music.core.design.RoundedHighlight
import com.onda.music.core.design.SmoothCornerShape
import com.onda.music.core.design.pressBounce
import com.onda.music.core.model.LyricsLine
import com.onda.music.core.model.PlayerStyle
import com.onda.music.feature.player.FeatheredSweepWord
import com.onda.music.feature.player.synthesizeWordTimings
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

data class OndaOnboardingSelection(
    val languageCode: String,
    val playerStyle: PlayerStyle,
    val lyricsMotionEnabled: Boolean,
    val monochromeEnabled: Boolean,
    val hapticsEnabled: Boolean
)

val ONBOARDING_FEATURED_STYLES: List<PlayerStyle> = listOf(
    PlayerStyle.POSTER,
    PlayerStyle.FLAGSHIP,
    PlayerStyle.LYRICS,
    PlayerStyle.TURNTABLE
)

fun resolveDefaultOnboardingLanguage(
    deviceLanguage: String,
    storedLanguage: String,
    onboardingCompleted: Boolean
): String {
    val normalizedStored = if (storedLanguage.equals("vi", ignoreCase = true)) "vi" else "en"
    if (onboardingCompleted) return normalizedStored
    return if (deviceLanguage.startsWith("vi", ignoreCase = true)) "vi" else normalizedStored
}

fun onboardingWaveEnvelope(pagePosition: Float): Float {
    val clamped = pagePosition.coerceIn(0f, 3f)
    val phase = clamped / 3f
    val ridge = 0.5f + 0.5f * sin(phase * PI.toFloat() * 1.35f)
    return (0.45f + 0.55f * ridge).coerceIn(0.45f, 1.0f)
}

@Immutable
private data class OnboardingCopy(
    val skipLabel: String,
    val continueLabel: String,
    val enterOndaLabel: String,
    val heroEyebrow: String,
    val heroTitle: String,
    val heroBody: String,
    val heroSpecs: List<String>,
    val studioEyebrow: String,
    val studioTitle: String,
    val studioBody: String,
    val previewHeader: String,
    val lyricsPrev: String,
    val lyricsActive: String,
    val lyricsNext: String,
    val lyricsToggleTitle: String,
    val lyricsToggleDesc: String,
    val flacToggleTitle: String,
    val flacToggleDesc: String,
    val hapticsToggleTitle: String,
    val hapticsToggleDesc: String,
    val stageEyebrow: String,
    val stageTitle: String,
    val stageBody: String,
    val stageFootnote: String,
    val readyEyebrow: String,
    val readyTitle: String,
    val readyBody: String,
    val permissionTitle: String,
    val permissionBody: String,
    val permissionGranted: String,
    val permissionPending: String,
    val grantPermissionLabel: String,
    val setupSummaryTitle: String,
    val replayBadge: String
)

private fun onboardingCopyFor(languageCode: String): OnboardingCopy {
    val isVi = languageCode == "vi"
    return if (isVi) {
        OnboardingCopy(
            skipLabel = "Bỏ qua",
            continueLabel = "Tiếp tục",
            enterOndaLabel = "Bắt đầu nghe nhạc",
            heroEyebrow = "OLED ACOUSTIC ENGINE",
            heroTitle = "Âm nhạc thuần khiết.\nKhông nhiễu tạp.",
            heroBody = "Trình phát nhạc đen tuyệt đối cho màn OLED, tối ưu tốc độ mở luồng tức thì và chuyển động GPU 120Hz.",
            heroSpecs = listOf("OLED 120Hz", "FLAC + Tức thì", "Lời ánh sáng"),
            studioEyebrow = "ENGINE ÂM THANH & LỜI",
            studioTitle = "Cảm nhận từng nhịp chữ.",
            studioBody = "Thử tắt bật ngay bên dưới — lời bài hát tự chia nhịp theo từng từ ngay cả khi không có word-tag sẵn.",
            previewHeader = "LIVE KINETIC LYRICS PREVIEW",
            lyricsPrev = "Đêm đen lặng yên giữ lại thanh âm",
            lyricsActive = "Từng ánh sáng trôi theo làn sóng nhạc",
            lyricsNext = "Chỉ còn giai điệu chạm khẽ không gian",
            lyricsToggleTitle = "Lời bài hát Kinetic 120Hz",
            lyricsToggleDesc = "Vệt sáng quét theo từng từ kèm hào quang GPU mượt",
            flacToggleTitle = "Ưu tiên lossless FLAC",
            flacToggleDesc = "Đua Monochrome FLAC cùng luồng YouTube siêu nhanh",
            hapticsToggleTitle = "Phản hồi rung xúc giác",
            hapticsToggleDesc = "Rung nhẹ tinh tế khi kéo thanh tua và đổi trang",
            stageEyebrow = "SÂN KHẤU NOW PLAYING",
            stageTitle = "Chọn giao diện mở màn.",
            stageBody = "Mỗi chế độ là một không gian trình diễn riêng trên nền đen thuần, mượt ở tần số quét cao.",
            stageFootnote = "Còn 6 chế độ khác (iPod, Bedside, Timeline, Video...) trong Cài đặt.",
            readyEyebrow = "SẴN SÀNG PHÁT NHẠC",
            readyTitle = "Phòng thu của riêng bạn.",
            readyBody = "Cấp quyền đọc nhạc máy và thông báo điều khiển khóa màn hình, hoặc bỏ qua để stream trực tuyến ngay.",
            permissionTitle = "Quyền thư viện & thông báo",
            permissionBody = "Quét tệp FLAC/MP3 cục bộ và hiện thanh điều khiển phát nhạc ở màn hình khóa.",
            permissionGranted = "Đã cấp đủ",
            permissionPending = "Chưa cấp",
            grantPermissionLabel = "Cấp quyền truy cập",
            setupSummaryTitle = "Cấu hình khởi đầu của Onda",
            replayBadge = "ĐANG XEM LẠI"
        )
    } else {
        OnboardingCopy(
            skipLabel = "Skip",
            continueLabel = "Continue",
            enterOndaLabel = "Enter Onda",
            heroEyebrow = "OLED ACOUSTIC ENGINE",
            heroTitle = "Pure acoustics.\nZero visual noise.",
            heroBody = "Pure-black OLED music architecture engineered for instant stream start and GPU-driven 120Hz motion.",
            heroSpecs = listOf("120Hz OLED", "FLAC + Instant", "Kinetic Lyrics"),
            studioEyebrow = "AUDIO & LYRICS ENGINE",
            studioTitle = "Feel every syllable.",
            studioBody = "Test toggles live below — synced lines synthesize natural word sweeps even when tracks lack word tags.",
            previewHeader = "LIVE KINETIC LYRICS PREVIEW",
            lyricsPrev = "Silence settles over the pure black stage",
            lyricsActive = "Luminous waves glide across every syllable",
            lyricsNext = "Clean sound stays anchored in focus",
            lyricsToggleTitle = "120Hz Kinetic Lyrics",
            lyricsToggleDesc = "Feathered word sweep with GPU photon halo and lift",
            flacToggleTitle = "Priority Lossless FLAC",
            flacToggleDesc = "Race Monochrome FLAC alongside fast direct streams",
            hapticsToggleTitle = "Tactile Haptics",
            hapticsToggleDesc = "Crisp haptic ticks across scrubber and controls",
            stageEyebrow = "NOW PLAYING STAGE",
            stageTitle = "Pick your opening stage.",
            stageBody = "Each mode shapes artwork, typography, and motion without breaking pure-black clarity.",
            stageFootnote = "6 more styles (iPod, Bedside, Timeline, Full Video...) await in Settings.",
            readyEyebrow = "STUDIO READY",
            readyTitle = "Tuned and ready to play.",
            readyBody = "Allow local audio access and lock-screen controls, or enter Onda immediately for online streaming.",
            permissionTitle = "Audio Library & Notifications",
            permissionBody = "Scan offline FLAC/MP3 files on device and render lock-screen media controls.",
            permissionGranted = "Granted",
            permissionPending = "Optional",
            grantPermissionLabel = "Grant Access",
            setupSummaryTitle = "Your Initial Onda Setup",
            replayBadge = "REPLAY TOUR"
        )
    }
}

private fun requiredMediaPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun hasAllMediaPermissions(context: Context): Boolean {
    return requiredMediaPermissions().all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

private val OnboardingSurface = Color(0xFF101012)
private val OnboardingElevatedSurface = Color(0xFF161619)
private val OnboardingBorder = Color(0x1CFFFFFF)

@Composable
fun OndaOnboardingScreen(
    initialSettings: OndaUserSettings,
    isReplay: Boolean = false,
    onComplete: (OndaOnboardingSelection) -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = OndaTheme.reducedMotion
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var languageCode by rememberSaveable {
        mutableStateOf(
            resolveDefaultOnboardingLanguage(
                deviceLanguage = Locale.getDefault().language,
                storedLanguage = initialSettings.languageCode,
                onboardingCompleted = initialSettings.onboardingCompleted
            )
        )
    }
    var selectedStyleName by rememberSaveable {
        mutableStateOf(initialSettings.playerStyle.name)
    }
    val selectedStyle = remember(selectedStyleName) {
        runCatching { PlayerStyle.valueOf(selectedStyleName) }.getOrDefault(PlayerStyle.POSTER)
    }
    var lyricsMotionEnabled by rememberSaveable { mutableStateOf(initialSettings.lyricsMotionEnabled) }
    var monochromeEnabled by rememberSaveable { mutableStateOf(initialSettings.monochromeEnabled) }
    var hapticsEnabled by rememberSaveable { mutableStateOf(initialSettings.hapticsEnabled) }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
    val copy = remember(languageCode) { onboardingCopyFor(languageCode) }

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch {
            val prev = (pagerState.currentPage - 1).coerceAtLeast(0)
            if (reducedMotion) {
                pagerState.scrollToPage(prev)
            } else {
                pagerState.animateScrollToPage(prev)
            }
        }
    }

    val emitSelection = {
        onComplete(
            OndaOnboardingSelection(
                languageCode = languageCode,
                playerStyle = selectedStyle,
                lyricsMotionEnabled = lyricsMotionEnabled,
                monochromeEnabled = monochromeEnabled,
                hapticsEnabled = hapticsEnabled
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        OndaOnboardingAcousticBackdrop(
            positionProvider = {
                (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 3f)
            },
            reducedMotion = reducedMotion,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            OnboardingTopBar(
                languageCode = languageCode,
                showSkip = pagerState.currentPage < 3 || isReplay,
                skipLabel = copy.skipLabel,
                replayBadge = if (isReplay) copy.replayBadge else null,
                onSelectLanguage = { code ->
                    if (hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    languageCode = code
                },
                onSkip = emitSelection
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                            val clamped = pageOffset.coerceIn(-1f, 1f)
                            val absOffset = abs(clamped)
                            alpha = lerp(1f, 0.25f, absOffset)
                            val s = lerp(1f, 0.94f, absOffset)
                            scaleX = s
                            scaleY = s
                            translationX = clamped * 18.dp.toPx()
                        }
                ) {
                    when (page) {
                        0 -> ManifestoPage(
                            copy = copy,
                            reducedMotion = reducedMotion
                        )
                        1 -> KineticEnginePage(
                            copy = copy,
                            isPageVisible = pagerState.currentPage == 1 || abs(pagerState.currentPage - 1) <= 1,
                            lyricsMotionEnabled = lyricsMotionEnabled,
                            monochromeEnabled = monochromeEnabled,
                            hapticsEnabled = hapticsEnabled,
                            reducedMotion = reducedMotion,
                            onToggleLyricsMotion = {
                                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lyricsMotionEnabled = !lyricsMotionEnabled
                            },
                            onToggleMonochrome = {
                                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                monochromeEnabled = !monochromeEnabled
                            },
                            onToggleHaptics = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                hapticsEnabled = !hapticsEnabled
                            }
                        )
                        2 -> StagePickerPage(
                            copy = copy,
                            selectedStyle = selectedStyle,
                            isPageActive = pagerState.currentPage == 2,
                            reducedMotion = reducedMotion,
                            onSelectStyle = { style ->
                                if (hapticsEnabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                selectedStyleName = style.name
                            }
                        )
                        else -> ReadyPermissionsPage(
                            context = context,
                            copy = copy,
                            languageCode = languageCode,
                            selectedStyle = selectedStyle,
                            lyricsMotionEnabled = lyricsMotionEnabled,
                            monochromeEnabled = monochromeEnabled,
                            hapticsEnabled = hapticsEnabled
                        )
                    }
                }
            }

            OnboardingBottomBar(
                pagerState = pagerState,
                continueLabel = copy.continueLabel,
                enterOndaLabel = copy.enterOndaLabel,
                reducedMotion = reducedMotion,
                onBack = {
                    if (hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    scope.launch {
                        val target = (pagerState.currentPage - 1).coerceAtLeast(0)
                        if (reducedMotion) {
                            pagerState.scrollToPage(target)
                        } else {
                            pagerState.animateScrollToPage(target)
                        }
                    }
                },
                onPrimaryAction = {
                    if (hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    if (pagerState.currentPage < 3) {
                        scope.launch {
                            val target = (pagerState.currentPage + 1).coerceAtMost(3)
                            if (reducedMotion) {
                                pagerState.scrollToPage(target)
                            } else {
                                pagerState.animateScrollToPage(target)
                            }
                        }
                    } else {
                        emitSelection()
                    }
                }
            )
        }
    }
}

@Composable
private fun OndaOnboardingAcousticBackdrop(
    positionProvider: () -> Float,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = ((frameNanos - lastNanos).coerceIn(0L, 50_000_000L)) / 1_000_000_000f
                    phase = (phase + dt * 1.15f) % (2f * PI.toFloat())
                }
                lastNanos = frameNanos
            }
        }
    }

    val wavePath = remember { Path() }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val pagePos = positionProvider()
        val env = onboardingWaveEnvelope(pagePos)
        val cx = lerp(w * 0.50f, w * 0.72f, (pagePos / 3f).coerceIn(0f, 1f))
        val cy = lerp(h * 0.30f, h * 0.24f, (pagePos / 3f).coerceIn(0f, 1f))
        val bloomRadius = maxOf(w, h) * 0.64f

        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0x16FFFFFF),
                0.42f to Color(0x08FFFFFF),
                1f to Color.Transparent,
                center = Offset(cx, cy),
                radius = bloomRadius
            ),
            radius = bloomRadius,
            center = Offset(cx, cy)
        )

        val steps = 42
        val baseY = h * 0.28f
        for (band in 0..2) {
            wavePath.reset()
            val amp = (14f + band * 9f).dp.toPx() * env
            val freq = 1.6f + band * 0.55f
            val bandPhase = phase + band * 1.1f + pagePos * 0.85f
            val bandY = baseY + (band - 1) * 18.dp.toPx()
            for (i in 0..steps) {
                val u = i.toFloat() / steps.toFloat()
                val x = u * w
                val taper = sin(u * PI.toFloat())
                val y = bandY + sin(u * 2f * PI.toFloat() * freq + bandPhase) * amp * taper
                if (i == 0) {
                    wavePath.moveTo(x, y)
                } else {
                    wavePath.lineTo(x, y)
                }
            }
            drawPath(
                path = wavePath,
                color = Color.White.copy(alpha = 0.055f - band * 0.012f),
                style = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun OnboardingTopBar(
    languageCode: String,
    showSkip: Boolean,
    skipLabel: String,
    replayBadge: String?,
    onSelectLanguage: (String) -> Unit,
    onSkip: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "ONDA",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.2.sp,
                    fontSize = 13.sp
                ),
                color = Color.White
            )
            if (replayBadge != null) {
                Text(
                    text = replayBadge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.6.sp
                    ),
                    color = Color(0xFFB5B5BE),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(OnboardingSurface)
                        .border(1.dp, OnboardingBorder, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(OnboardingSurface)
                    .border(1.dp, OnboardingBorder, CircleShape)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                LanguageChip(
                    label = "VI",
                    selected = languageCode == "vi",
                    onClick = { onSelectLanguage("vi") }
                )
                LanguageChip(
                    label = "EN",
                    selected = languageCode != "vi",
                    onClick = { onSelectLanguage("en") }
                )
            }

            if (showSkip) {
                val skipInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .pressBounce(skipInteraction, pressedScale = 0.92f)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = skipInteraction,
                            indication = RoundedHighlight(cornerRadius = 24.dp),
                            role = Role.Button,
                            onClick = onSkip
                        )
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = skipLabel,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        color = Color(0xFFB3B3BC),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (selected) Color.White else Color.Transparent,
        animationSpec = tween(160),
        label = "LangChipBg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) Color.Black else Color(0xFFB5B5BE),
        animationSpec = tween(160),
        label = "LangChipFg"
    )
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = fg,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManifestoPage(
    copy: OnboardingCopy,
    reducedMotion: Boolean
) {
    val ringProgress = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val waveProgress = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val dotProgress = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val breathStrength = remember { Animatable(if (reducedMotion) 0f else 0.7f) }
    var breathPhase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            ringProgress.snapTo(1f)
            waveProgress.snapTo(1f)
            dotProgress.snapTo(1f)
            breathStrength.snapTo(0f)
            return@LaunchedEffect
        }
        coroutineScope {
            launch {
                ringProgress.animateTo(1f, tween(420, easing = CubicBezierEasing(0.18f, 0.88f, 0.24f, 1f)))
            }
            launch {
                delay(140L)
                waveProgress.animateTo(1f, tween(460, easing = CubicBezierEasing(0.22f, 0f, 0.12f, 1f)))
            }
            launch {
                delay(380L)
                dotProgress.animateTo(1f, spring(dampingRatio = 0.48f, stiffness = 540f))
            }
            launch {
                var lastNanos = 0L
                while (true) {
                    withFrameNanos { frameNanos ->
                        if (lastNanos != 0L) {
                            val dt = ((frameNanos - lastNanos).coerceIn(0L, 50_000_000L)) / 1_000_000_000f
                            breathPhase = (breathPhase + dt * 2.1f) % (2f * PI.toFloat())
                        }
                        lastNanos = frameNanos
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(164.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width * 0.5f, size.height * 0.5f)
                val maxR = size.minDimension * 0.48f
                val pulse = if (reducedMotion) 1f else 0.96f + 0.04f * sin(breathPhase)
                drawCircle(
                    color = Color(0x16FFFFFF),
                    radius = maxR * pulse,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
                drawCircle(
                    color = Color(0x22FFFFFF),
                    radius = maxR * 0.76f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            OndaAnimatedBrandMark(
                ringProgressProvider = { ringProgress.value },
                waveProgressProvider = { waveProgress.value },
                dotProgressProvider = { dotProgress.value },
                waveBreathPhaseProvider = { breathPhase },
                waveBreathStrengthProvider = { breathStrength.value },
                tint = Color.White,
                modifier = Modifier.size(84.dp)
            )
        }

        Spacer(modifier = Modifier.height(26.dp))

        Text(
            text = copy.heroEyebrow,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = Color(0xFF9C9CA6),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = copy.heroTitle,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 31.sp,
                lineHeight = 37.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp
            ),
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = copy.heroBody,
            style = MaterialTheme.typography.bodyLarge.copy(
                lineHeight = 23.sp
            ),
            color = Color(0xFFB3B3BC),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(22.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            copy.heroSpecs.forEach { spec ->
                Text(
                    text = spec,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(OnboardingSurface)
                        .border(1.dp, OnboardingBorder, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KineticEnginePage(
    copy: OnboardingCopy,
    isPageVisible: Boolean,
    lyricsMotionEnabled: Boolean,
    monochromeEnabled: Boolean,
    hapticsEnabled: Boolean,
    reducedMotion: Boolean,
    onToggleLyricsMotion: () -> Unit,
    onToggleMonochrome: () -> Unit,
    onToggleHaptics: () -> Unit
) {
    val activeLine = remember(copy.lyricsActive) {
        LyricsLine(
            startTimeMs = 400L,
            endTimeMs = 3_900L,
            text = copy.lyricsActive
        )
    }
    val previewWords = remember(activeLine) {
        synthesizeWordTimings(
            line = activeLine,
            nextLineStartMs = 4_200L,
            preferExplicit = false
        )
    }

    var loopPositionMs by remember { mutableLongStateOf(1_950L) }
    val motionActive = lyricsMotionEnabled && !reducedMotion

    LaunchedEffect(motionActive, isPageVisible) {
        if (!motionActive || !isPageVisible) {
            loopPositionMs = 2_250L
            return@LaunchedEffect
        }
        var lastNanos = 0L
        var elapsed = 500L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dtMs = ((frameNanos - lastNanos) / 1_000_000L).coerceIn(0L, 48L)
                    elapsed = (elapsed + dtMs) % 4_300L
                    loopPositionMs = elapsed
                }
                lastNanos = frameNanos
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = copy.studioEyebrow,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.4.sp
                ),
                color = Color(0xFF9C9CA6)
            )
            Text(
                text = copy.studioTitle,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp
                ),
                color = Color.White
            )
            Text(
                text = copy.studioBody,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB3B3BC)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SmoothCornerShape(22.dp))
                .background(OnboardingSurface)
                .border(1.dp, OnboardingBorder, SmoothCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = copy.previewHeader,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.8.sp
                    ),
                    color = Color(0xFF9C9CA6),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = copy.lyricsPrev,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0x58FFFFFF),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val sweepStyle = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.ExtraBold
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.Start),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                previewWords.forEach { word ->
                    FeatheredSweepWord(
                        text = word.text,
                        style = sweepStyle,
                        startTimeMs = word.startTimeMs,
                        endTimeMs = word.endTimeMs,
                        positionProvider = { loopPositionMs },
                        activeColor = Color.White,
                        baseColor = Color(0x52FFFFFF),
                        motionActive = motionActive,
                        glowColor = Color(0xFFE4E7FF)
                    )
                }
            }

            Text(
                text = copy.lyricsNext,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0x45FFFFFF),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        OnboardingToggleCard(
            icon = Icons.Rounded.Lyrics,
            title = copy.lyricsToggleTitle,
            subtitle = copy.lyricsToggleDesc,
            checked = lyricsMotionEnabled,
            onToggle = onToggleLyricsMotion
        )
        OnboardingToggleCard(
            icon = Icons.Rounded.HighQuality,
            title = copy.flacToggleTitle,
            subtitle = copy.flacToggleDesc,
            checked = monochromeEnabled,
            onToggle = onToggleMonochrome
        )
        OnboardingToggleCard(
            icon = Icons.Rounded.Vibration,
            title = copy.hapticsToggleTitle,
            subtitle = copy.hapticsToggleDesc,
            checked = hapticsEnabled,
            onToggle = onToggleHaptics
        )
    }
}

@Composable
private fun OnboardingToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val cardShape = remember { SmoothCornerShape(20.dp) }
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(
        targetValue = if (checked) OnboardingElevatedSurface else OnboardingSurface,
        animationSpec = tween(160),
        label = "OnboardingToggleBg"
    )
    val border by animateColorAsState(
        targetValue = if (checked) Color(0x38FFFFFF) else OnboardingBorder,
        animationSpec = tween(160),
        label = "OnboardingToggleBorder"
    )
    val pillBg by animateColorAsState(
        targetValue = if (checked) Color.White else Color(0xFF242429),
        animationSpec = tween(160),
        label = "OnboardingTogglePill"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .pressBounce(interaction, pressedScale = 0.975f)
            .clip(cardShape)
            .background(bg)
            .border(1.dp, border, cardShape)
            .clickable(
                interactionSource = interaction,
                indication = RoundedHighlight(cornerRadius = 20.dp),
                role = Role.Switch,
                onClick = onToggle
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0x16FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(19.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9C9CA6),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .size(width = 42.dp, height = 24.dp)
                .clip(CircleShape)
                .background(pillBg),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .padding(3.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (checked) Color.Black else Color(0xFF9C9CA6))
            )
        }
    }
}

@Composable
private fun StagePickerPage(
    copy: OnboardingCopy,
    selectedStyle: PlayerStyle,
    isPageActive: Boolean,
    reducedMotion: Boolean,
    onSelectStyle: (PlayerStyle) -> Unit
) {
    var previewPhase by remember { mutableFloatStateOf(0.2f) }
    LaunchedEffect(reducedMotion, isPageActive) {
        if (reducedMotion || !isPageActive) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = ((frameNanos - lastNanos).coerceIn(0L, 50_000_000L)) / 1_000_000_000f
                    previewPhase = (previewPhase + dt * 0.35f) % 1f
                }
                lastNanos = frameNanos
            }
        }
    }

    val rows = remember { ONBOARDING_FEATURED_STYLES.chunked(2) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = copy.stageEyebrow,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.4.sp
                ),
                color = Color(0xFF9C9CA6)
            )
            Text(
                text = copy.stageTitle,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp
                ),
                color = Color.White
            )
            Text(
                text = copy.stageBody,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB3B3BC)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pair.forEach { style ->
                        OnboardingStageTile(
                            style = style,
                            selected = style == selectedStyle,
                            phaseProvider = { previewPhase },
                            onClick = { onSelectStyle(style) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Text(
            text = copy.stageFootnote,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF8E8E98),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun OnboardingStageTile(
    style: PlayerStyle,
    selected: Boolean,
    phaseProvider: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tileShape = remember { SmoothCornerShape(20.dp) }
    val previewShape = remember { SmoothCornerShape(14.dp) }
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(
        targetValue = if (selected) Color.White else OnboardingSurface,
        animationSpec = tween(180),
        label = "StageTileBg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) Color.Black else Color.White,
        animationSpec = tween(180),
        label = "StageTileFg"
    )
    val border by animateColorAsState(
        targetValue = if (selected) Color.White else OnboardingBorder,
        animationSpec = tween(180),
        label = "StageTileBorder"
    )

    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 148.dp)
            .pressBounce(interaction, pressedScale = 0.96f)
            .clip(tileShape)
            .background(bg)
            .border(1.dp, border, tileShape)
            .clickable(
                interactionSource = interaction,
                indication = RoundedHighlight(cornerRadius = 20.dp),
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .clip(previewShape)
                .background(if (selected) Color(0xFF141418) else Color(0xFF070709))
                .padding(8.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val p = phaseProvider()
                val w = size.width
                val h = size.height
                when (style) {
                    PlayerStyle.POSTER -> {
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(Color(0x36FFFFFF), Color(0x10FFFFFF))),
                            topLeft = Offset.Zero,
                            size = Size(w, h * 0.56f),
                            cornerRadius = CornerRadius(12f, 12f)
                        )
                        val sweep = 0.35f + 0.65f * (0.5f + 0.5f * sin(p * 2f * PI.toFloat()))
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.92f),
                            topLeft = Offset(0f, h * 0.67f),
                            size = Size(w * sweep * 0.85f, 5.dp.toPx()),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                        drawRoundRect(
                            color = Color(0x45FFFFFF),
                            topLeft = Offset(0f, h * 0.84f),
                            size = Size(w * 0.58f, 4.dp.toPx()),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                    }
                    PlayerStyle.FLAGSHIP -> {
                        val art = minOf(w, h) * 0.54f
                        drawRoundRect(
                            color = Color(0x32FFFFFF),
                            topLeft = Offset((w - art) * 0.5f, 2f),
                            size = Size(art, art),
                            cornerRadius = CornerRadius(14f, 14f)
                        )
                        val fillW = w * 0.72f * ((p + 0.25f) % 1f).coerceIn(0.2f, 1f)
                        drawRoundRect(
                            color = Color(0x28FFFFFF),
                            topLeft = Offset(w * 0.14f, h * 0.68f),
                            size = Size(w * 0.72f, 3.dp.toPx()),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(w * 0.14f, h * 0.68f),
                            size = Size(fillW, 3.dp.toPx()),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                        drawRoundRect(
                            color = Color(0x28FFFFFF),
                            topLeft = Offset(w * 0.22f, h * 0.80f),
                            size = Size(w * 0.56f, h * 0.16f),
                            cornerRadius = CornerRadius(20f, 20f)
                        )
                    }
                    PlayerStyle.LYRICS -> {
                        val activeY = h * 0.44f
                        drawRoundRect(
                            color = Color(0x1EFFFFFF),
                            topLeft = Offset(0f, activeY - 5.dp.toPx()),
                            size = Size(w, 16.dp.toPx()),
                            cornerRadius = CornerRadius(10f, 10f)
                        )
                        val glowX = ((p * 1.2f) % 1f) * w * 0.82f
                        drawCircle(
                            color = Color(0x45FFFFFF),
                            radius = 12.dp.toPx(),
                            center = Offset(glowX, activeY + 3.dp.toPx())
                        )
                        drawRoundRect(
                            color = Color(0x38FFFFFF),
                            topLeft = Offset(w * 0.18f, h * 0.14f),
                            size = Size(w * 0.64f, 4.dp.toPx()),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(w * 0.10f, activeY),
                            size = Size(w * 0.80f, 5.dp.toPx()),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                        drawRoundRect(
                            color = Color(0x38FFFFFF),
                            topLeft = Offset(w * 0.20f, h * 0.76f),
                            size = Size(w * 0.60f, 4.dp.toPx()),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                    }
                    else -> {
                        val discCenter = Offset(w * 0.46f, h * 0.5f)
                        val radius = minOf(w, h) * 0.40f
                        drawCircle(
                            color = Color(0x22FFFFFF),
                            radius = radius,
                            center = discCenter
                        )
                        rotate(degrees = p * 360f, pivot = discCenter) {
                            drawArc(
                                color = Color.White.copy(alpha = 0.75f),
                                startAngle = 0f,
                                sweepAngle = 60f,
                                useCenter = false,
                                topLeft = Offset(discCenter.x - radius * 0.72f, discCenter.y - radius * 0.72f),
                                size = Size(radius * 1.44f, radius * 1.44f),
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        drawCircle(
                            color = Color.White,
                            radius = radius * 0.24f,
                            center = discCenter
                        )
                        drawLine(
                            color = Color(0xCCFFFFFF),
                            start = Offset(w * 0.84f, h * 0.16f),
                            end = Offset(w * 0.68f, h * 0.60f),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = style.label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadyPermissionsPage(
    context: Context,
    copy: OnboardingCopy,
    languageCode: String,
    selectedStyle: PlayerStyle,
    lyricsMotionEnabled: Boolean,
    monochromeEnabled: Boolean,
    hapticsEnabled: Boolean
) {
    var permissionsGranted by remember { mutableStateOf(hasAllMediaPermissions(context)) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionsGranted = hasAllMediaPermissions(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = copy.readyEyebrow,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.4.sp
                ),
                color = Color(0xFF9C9CA6)
            )
            Text(
                text = copy.readyTitle,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp
                ),
                color = Color.White
            )
            Text(
                text = copy.readyBody,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB3B3BC)
            )
        }

        val cardShape = remember { SmoothCornerShape(22.dp) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(OnboardingSurface)
                .border(1.dp, OnboardingBorder, cardShape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0x16FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = copy.permissionTitle,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = copy.permissionBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9C9CA6)
                    )
                }
                Text(
                    text = if (permissionsGranted) copy.permissionGranted else copy.permissionPending,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (permissionsGranted) Color.Black else Color(0xFFD0D0DA),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (permissionsGranted) Color.White else Color(0xFF222228))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }

            if (!permissionsGranted) {
                val grantInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .pressBounce(grantInteraction, pressedScale = 0.97f)
                        .clip(CircleShape)
                        .background(OnboardingElevatedSurface)
                        .border(1.dp, Color(0x32FFFFFF), CircleShape)
                        .clickable(
                            interactionSource = grantInteraction,
                            indication = RoundedHighlight(cornerRadius = 24.dp),
                            role = Role.Button,
                            onClick = { launcher.launch(requiredMediaPermissions()) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = copy.grantPermissionLabel,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(OnboardingSurface)
                .border(1.dp, OnboardingBorder, cardShape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Headphones,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = copy.setupSummaryTitle,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryBadge(selectedStyle.label)
                SummaryBadge(if (languageCode == "vi") "Tieng Viet" else "English")
                if (lyricsMotionEnabled) SummaryBadge("120Hz Lyrics")
                if (monochromeEnabled) SummaryBadge("Monochrome FLAC")
                if (hapticsEnabled) SummaryBadge("Haptics")
            }
        }
    }
}

@Composable
private fun SummaryBadge(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
        color = Color(0xFFE6E6EC),
        modifier = Modifier
            .clip(CircleShape)
            .background(Color(0xFF1A1A1F))
            .border(1.dp, OnboardingBorder, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun OnboardingBottomBar(
    pagerState: PagerState,
    continueLabel: String,
    enterOndaLabel: String,
    reducedMotion: Boolean,
    onBack: () -> Unit,
    onPrimaryAction: () -> Unit
) {
    val isLastPage = pagerState.currentPage >= 3
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            AnimatedVisibility(
                visible = pagerState.currentPage > 0,
                enter = fadeIn(tween(if (reducedMotion) 60 else 180)),
                exit = fadeOut(tween(if (reducedMotion) 60 else 140))
            ) {
                val backInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .pressBounce(backInteraction, pressedScale = 0.90f)
                        .clip(CircleShape)
                        .background(OnboardingSurface)
                        .border(1.dp, OnboardingBorder, CircleShape)
                        .clickable(
                            interactionSource = backInteraction,
                            indication = RoundedHighlight(cornerRadius = 24.dp),
                            role = Role.Button,
                            onClick = onBack
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(14.dp)) {
                        val midY = size.height * 0.5f
                        drawLine(
                            color = Color.White,
                            start = Offset(size.width * 0.68f, size.height * 0.14f),
                            end = Offset(size.width * 0.28f, midY),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(size.width * 0.28f, midY),
                            end = Offset(size.width * 0.68f, size.height * 0.86f),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }

        OndaPagerIndicator(
            count = 4,
            positionProvider = { pagerState.currentPage + pagerState.currentPageOffsetFraction }
        )

        val ctaInteraction = remember { MutableInteractionSource() }
        val ctaBg by animateColorAsState(
            targetValue = if (isLastPage) Color.White else OnboardingElevatedSurface,
            animationSpec = if (reducedMotion) snap() else tween(200),
            label = "OnboardingCtaBg"
        )
        val ctaFg by animateColorAsState(
            targetValue = if (isLastPage) Color.Black else Color.White,
            animationSpec = if (reducedMotion) snap() else tween(200),
            label = "OnboardingCtaFg"
        )

        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = DockMetrics.MinTapTarget, minHeight = 48.dp)
                .pressBounce(ctaInteraction, pressedScale = 0.95f)
                .clip(CircleShape)
                .background(ctaBg)
                .border(
                    width = 1.dp,
                    color = if (isLastPage) Color.White else Color(0x36FFFFFF),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = ctaInteraction,
                    indication = RoundedHighlight(cornerRadius = 24.dp),
                    role = Role.Button,
                    onClick = onPrimaryAction
                )
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isLastPage,
                transitionSpec = {
                    if (reducedMotion) {
                        fadeIn(snap()) togetherWith fadeOut(snap())
                    } else {
                        (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.94f)) togetherWith
                            (fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.96f))
                    }
                },
                label = "OnboardingCtaLabel"
            ) { finalStep ->
                Text(
                    text = if (finalStep) enterOndaLabel else continueLabel,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = ctaFg,
                    maxLines = 1
                )
            }
        }
    }
}
