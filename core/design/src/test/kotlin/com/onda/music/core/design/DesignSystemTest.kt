package com.onda.music.core.design

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.onda.music.core.model.LyricsWord
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignSystemTest {

    @Test
    fun dockMetricsCalculatesClearanceWithAndWithoutMiniPlayer() {
        val withMini = DockMetrics.contentBottomPadding(
            hasMiniPlayer = true,
            navigationBarInset = 24.dp
        )
        assertEquals(182.dp, withMini)

        val withoutMini = DockMetrics.contentBottomPadding(
            hasMiniPlayer = false,
            navigationBarInset = 24.dp
        )
        assertEquals(118.dp, withoutMini)
    }

    @Test
    fun homeRecedeInterpolatesScaleAndAlpha() {
        assertEquals(1f, AppMotion.homeRecedeScale(0f), 0.0001f)
        assertEquals(1f, AppMotion.homeRecedeAlpha(0f), 0.0001f)
        assertEquals(0.92f, AppMotion.homeRecedeScale(1f), 0.0001f)
        assertEquals(0.45f, AppMotion.homeRecedeAlpha(1f), 0.0001f)
        assertEquals(0.96f, AppMotion.homeRecedeScale(0.5f), 0.0001f)
    }

    @Test
    fun morphCornerRadiusClampsNegativeAndBounds() {
        assertEquals(18.dp, AppMotion.morphCornerRadius(18.dp, 32.dp, -0.5f))
        assertEquals(32.dp, AppMotion.morphCornerRadius(18.dp, 32.dp, 1.5f))
        assertEquals(0.dp, AppMotion.morphCornerRadius((-4).dp, (-2).dp, 0.5f))
    }

    @Test
    fun wcagAaTokensMeetContrastOnAmoledBackground() {
        assertTrue(GlassTokens.meetsWcagAa(GlassTokens.TextPrimary, GlassTokens.AmoledBlack))
        assertTrue(GlassTokens.meetsWcagAa(GlassTokens.TextSecondary, GlassTokens.AmoledBlack))
        assertTrue(GlassTokens.meetsWcagAa(GlassTokens.TextTertiary, GlassTokens.AmoledBlack))
        assertTrue(GlassTokens.meetsWcagAa(GlassTokens.WaveCyan, GlassTokens.AmoledBlack))
        assertFalse(GlassTokens.meetsWcagAa(Color(0xFF1A1A1A), GlassTokens.AmoledBlack))

        val lifted = GlassTokens.ensureContrastOnDark(Color(0xFF182030), GlassTokens.AmoledBlack, 4.5)
        assertTrue(GlassTokens.meetsWcagAa(lifted, GlassTokens.AmoledBlack))
    }

    @Test
    fun monochromeOledTokensAndStylesAreBorderless() {
        assertEquals(Color(0xFF000000), GlassTokens.AmoledBlack)
        assertEquals(Color(0xFF141414), GlassTokens.ObsidianSurface)
        assertEquals(Color(0xFF141414), GlassTokens.MatteCard)
        assertEquals(Color(0xFF242424), GlassTokens.IconBadgeSurface)
        assertEquals(Color(0xFF2E2E2E), GlassTokens.InactiveSurface)
        assertEquals(Color(0xFF1F1F1F), GlassTokens.HairlineDivider)
        assertEquals(Color(0xFFFFFFFF), GlassTokens.WaveCyan)
        assertEquals(Color(0xFF9E9E9E), GlassTokens.SilverLavender)
        assertEquals(Color.White, OndaArtworkPalette.Default.primary)
        assertEquals(Color(0xFF9E9E9E), OndaArtworkPalette.Default.secondary)
        assertEquals(Color(0xFF242424), OndaArtworkPalette.Default.tertiary)

        GlassElevation.entries.forEach { elevation ->
            assertEquals(0.dp, GlassKit.styleFor(elevation).borderWidth)
        }
    }

    @Test
    fun likeBurstQuadraticBezierHitsEndpointsAndMidCurve() {
        val p0 = Offset(0f, 0f)
        val p1 = Offset(50f, -40f)
        val p2 = Offset(100f, 0f)

        assertEquals(p0, LikeBurstState.quadraticBezier(p0, p1, p2, 0f))
        assertEquals(p2, LikeBurstState.quadraticBezier(p0, p1, p2, 1f))
        val mid = LikeBurstState.quadraticBezier(p0, p1, p2, 0.5f)
        assertEquals(50f, mid.x, 0.001f)
        assertEquals(-20f, mid.y, 0.001f)
    }

    @Test
    fun likeBurstStatePopulatesParticlesOnTrigger() {
        val state = LikeBurstState()
        state.trigger(origin = Offset(10f, 20f))
        assertEquals(12, state.particles.size)
    }

    @Test
    fun smoothCornerShapeClampsNegativeAndEvaluatesSuperellipseMath() {
        val density = Density(2f)
        val zeroShape = SmoothCornerShape(radius = (-8).dp)
        val zeroOutline = zeroShape.createOutline(Size(120f, 80f), LayoutDirection.Ltr, density)
        assertTrue(zeroOutline is Outline.Rectangle)

        assertEquals(0f, clampSmoothRadiusPx(-16f, 120f, 80f), 0.001f)
        assertEquals(40f, clampSmoothRadiusPx(80f, 120f, 80f), 0.001f)

        val (alongStart, acrossStart) = superellipseQuarterOffsets(20f, 4.0, 0.0)
        assertEquals(20f, alongStart, 0.001f)
        assertEquals(0f, acrossStart, 0.001f)

        val (alongEnd, acrossEnd) = superellipseQuarterOffsets(20f, 4.0, PI * 0.5)
        assertEquals(0f, alongEnd, 0.001f)
        assertEquals(20f, acrossEnd, 0.001f)
    }

    @Test
    fun blurEffectCacheQuantizesRadiiAndClampsSafely() {
        assertEquals(0f, BlurEffectCache.quantize(-5f), 0.001f)
        assertEquals(0f, BlurEffectCache.quantize(0.4f), 0.001f)
        assertEquals(10f, BlurEffectCache.quantize(10.2f), 0.001f)
        assertEquals(24f, BlurEffectCache.quantize(24.4f), 0.001f)
        assertEquals(48f, BlurEffectCache.quantize(47.2f), 0.001f)
    }

    @Test
    fun revealSeenStateTracksKeysAcrossSaveAndRestore() {
        val state = RevealSeenState()
        assertFalse(state.hasSeen("hub_eq"))
        state.markSeen("hub_eq")
        state.markSeen("hub_transfer")
        assertTrue(state.hasSeen("hub_eq"))
        assertEquals(setOf("hub_eq", "hub_transfer"), state.snapshotKeys().toSet())
    }

    @Test
    fun launchChoreographyStaggersOrdersMonotonicallyWithinBounds() {
        for (order in 0..3) {
            assertEquals(0f, ondaLaunchRiseOrderProgress(0f, order), 0.0001f)
            assertEquals(1f, ondaLaunchRiseOrderProgress(1f, order), 0.0001f)
        }
        val mid0 = ondaLaunchRiseOrderProgress(0.35f, 0)
        val mid1 = ondaLaunchRiseOrderProgress(0.35f, 1)
        val mid2 = ondaLaunchRiseOrderProgress(0.35f, 2)
        val mid3 = ondaLaunchRiseOrderProgress(0.35f, 3)
        assertTrue(mid0 >= mid1 && mid1 >= mid2 && mid2 >= mid3)
        assertTrue(mid0 > 0f)
    }

    @Test
    fun morphCornerShapeReturnsRectangleAtZeroAndCachesQuantizedShapes() {
        assertSame(RectangleShape, AppMotion.morphCornerShape(18.dp, 0.dp, 1f))
        assertSame(RectangleShape, AppMotion.morphCornerShape(0.2.dp, 0.dp, 0f))
        assertSame(RectangleShape, AppMotion.morphCornerShape((-4).dp, (-2).dp, 0.5f))

        val s1 = AppMotion.morphCornerShape(12.1.dp, 12.1.dp, 0f)
        val s2 = AppMotion.morphCornerShape(12.2.dp, 12.2.dp, 0f)
        val s3 = AppMotion.morphCornerShape(18.dp, 18.dp, 0f)
        assertSame(s1, s2)
        assertNotSame(s1, s3)
    }

    @Test
    fun pagerIndicatorDotProgressConservesTotalWeightAcrossLoopAndLinear() {
        val linearSum = (0 until 5).sumOf { idx ->
            pagerIndicatorDotProgress(idx, 1.35f, count = 5, loop = false).toDouble()
        }
        assertEquals(1.0, linearSum, 0.0001)
        assertEquals(0.65f, pagerIndicatorDotProgress(1, 1.35f, count = 5, loop = false), 0.0001f)
        assertEquals(0.35f, pagerIndicatorDotProgress(2, 1.35f, count = 5, loop = false), 0.0001f)
        assertEquals(1f, pagerIndicatorDotProgress(0, -1f, count = 5, loop = false), 0.0001f)
        assertEquals(1f, pagerIndicatorDotProgress(4, 9f, count = 5, loop = false), 0.0001f)

        val loopSum = (0 until 4).sumOf { idx ->
            pagerIndicatorDotProgress(idx, 3.4f, count = 4, loop = true).toDouble()
        }
        assertEquals(1.0, loopSum, 0.0001)
        assertEquals(0.6f, pagerIndicatorDotProgress(3, 3.4f, count = 4, loop = true), 0.0001f)
        assertEquals(0.4f, pagerIndicatorDotProgress(0, 3.4f, count = 4, loop = true), 0.0001f)
    }

    @Test
    fun launchChoreographyWindowProgressAndContentReadyLifecycle() {
        assertEquals(0f, ondaLaunchWindowProgress(elapsedMs = 100f, startMs = 200f, durationMs = 400f), 0.0001f)
        assertEquals(0.5f, ondaLaunchWindowProgress(elapsedMs = 400f, startMs = 200f, durationMs = 400f), 0.0001f)
        assertEquals(1f, ondaLaunchWindowProgress(elapsedMs = 900f, startMs = 200f, durationMs = 400f), 0.0001f)
        assertEquals(0f, ondaLaunchWindowProgress(elapsedMs = 50f, startMs = 100f, durationMs = 0f), 0.0001f)
        assertEquals(1f, ondaLaunchWindowProgress(elapsedMs = 100f, startMs = 100f, durationMs = 0f), 0.0001f)

        val coldStart = OndaLaunchChoreographyState(playOnStart = true)
        assertFalse(coldStart.isSettled)
        assertFalse(coldStart.isContentReady)
        coldStart.markContentReady()
        assertTrue(coldStart.isContentReady)
        assertFalse(coldStart.isSettled)
        coldStart.markSettled()
        assertTrue(coldStart.isSettled)
        assertEquals(1f, coldStart.orderProgress(0), 0.0001f)

        val warmResume = OndaLaunchChoreographyState(playOnStart = false)
        assertTrue(warmResume.isSettled)
        assertTrue(warmResume.isContentReady)
    }

    @Test
    fun ondaKaraokeHeadGlidesContinuouslyAndSwellsOnHeldNotes() {
        val text = "Hold this note"
        val words = listOf(
            com.onda.music.core.model.LyricsWord("Hold", 1_000L, 1_320L),
            com.onda.music.core.model.LyricsWord("this", 1_320L, 1_640L),
            com.onda.music.core.model.LyricsWord("note", 1_640L, 3_400L)
        )
        val timed = computeOndaTimedRanges(text, words, 1_000L, 3_400L)
        assertEquals(3, timed.size)
        val head = ondaKaraokeHeadFor(text)
        assertEquals(3, head.wordCount)
        assertTrue(head.cellCount >= 12)

        head.advance(timed, positionSec = 1.31, dt = 0.016f, wordMotion = true)
        assertTrue(head.offset.floatValue > 4.0f && head.offset.floatValue < 5.0f)

        var maxSwell = 0f
        var maxGlow = 0f
        for (step in 0 until 45) {
            val sec = 1.64 + step * 0.032
            head.advance(timed, positionSec = sec, dt = 0.032f, wordMotion = true)
            maxGlow = maxOf(maxGlow, head.glow.floatValue)
            for (c in 0 until head.cellCount) {
                maxSwell = maxOf(maxSwell, head.swell[c])
            }
        }
        assertTrue(maxGlow > 0.75f)
        assertTrue(maxSwell > 0.50f)
    }

    @Test
    fun computeOndaTimedRangesSynthesizesWeightedWordsWithPunctuationAndNaturalCap() {
        val text = "Hello, world! Stay here"
        val timed = computeOndaTimedRanges(
            text = text,
            words = emptyList(),
            fallbackStartMs = 1_000L,
            fallbackEndMs = 20_000L
        )
        assertEquals(4, timed.size)
        assertEquals(1.0, timed.first().startTimeSec, 0.001)
        assertTrue(timed.last().endTimeSec - timed.first().startTimeSec <= 6.801)
        val commaSpan = timed[0].endTimeSec - timed[0].startTimeSec
        val plainSpan = timed[2].endTimeSec - timed[2].startTimeSec
        assertTrue(commaSpan > plainSpan)
        for (i in 1 until timed.size) {
            assertTrue(timed[i].startTimeSec >= timed[i - 1].endTimeSec - 0.0001)
        }
    }

    @Test
    fun oledAdaptiveAccentMaintainsWcagAaContrastOnPureBlack() {
        val deepBlue = computeOledAdaptiveAccent(Color(0xFF0A1230))
        val brightCrimson = computeOledAdaptiveAccent(Color(0xFFD92038))
        val pureBlack = computeOledAdaptiveAccent(Color(0xFF000000))

        assertTrue(GlassTokens.meetsWcagAa(deepBlue, GlassTokens.AmoledBlack))
        assertTrue(GlassTokens.meetsWcagAa(brightCrimson, GlassTokens.AmoledBlack))
        assertTrue(GlassTokens.meetsWcagAa(pureBlack, GlassTokens.AmoledBlack))

        val defaultPalette = computeOledAdaptivePalette(OndaArtworkPalette.Default)
        assertEquals(OndaArtworkPalette.Default, defaultPalette)
    }

    @Test
    fun appMotionAdaptiveSpringAndBoundsAndCornerCache() {
        val snapSpec = AppMotion.adaptiveSpring<Float>(reducedMotion = true)
        assertTrue(snapSpec is SnapSpec<*>)

        val springSpec = AppMotion.adaptiveSpring<Float>(
            dampingRatio = 0.72f,
            stiffness = 520f,
            reducedMotion = false
        )
        assertTrue(springSpec is SpringSpec<*>)
        val typedSpring = springSpec as SpringSpec<Float>
        assertEquals(0.72f, typedSpring.dampingRatio, 0.0001f)
        assertEquals(520f, typedSpring.stiffness, 0.0001f)

        assertEquals(1f, AppMotion.homeRecedeScale(-0.5f), 0.0001f)
        assertEquals(0.92f, AppMotion.homeRecedeScale(2f), 0.0001f)
        assertEquals(1f, AppMotion.homeRecedeAlpha(-1f), 0.0001f)
        assertEquals(0.45f, AppMotion.homeRecedeAlpha(2f), 0.0001f)

        val clamped60 = AppMotion.morphCornerShape(60.dp, 60.dp, 0f)
        val clamped72 = AppMotion.morphCornerShape(72.dp, 72.dp, 0f)
        assertSame(clamped60, clamped72)
    }

    @Test
    fun dockMetricsHandlesCustomValuesAndNegativeInsets() {
        val clamped = DockMetrics.contentBottomPadding(
            hasMiniPlayer = true,
            navigationBarInset = (-12).dp,
            navBarHeight = 10.dp,
            miniPlayerHeight = 20.dp,
            extraClearance = 0.dp
        )
        assertEquals(18.dp, clamped)

        val floored = DockMetrics.contentBottomPadding(
            hasMiniPlayer = false,
            navigationBarInset = (-100).dp,
            navBarHeight = 10.dp,
            miniPlayerHeight = 20.dp,
            extraClearance = 0.dp
        )
        assertEquals(0.dp, floored)
    }

    @Test
    fun ondaColorsAndOledAdaptivePaletteHandleCustomPalettes() {
        val defaults = OndaColors()
        assertEquals(GlassTokens.AmoledBlack, defaults.background)
        assertEquals(GlassTokens.ObsidianSurface, defaults.surface)
        assertEquals(GlassTokens.WaveCyan, defaults.accent)

        val translucentAdapted = computeOledAdaptiveAccent(Color(0x80102030))
        assertEquals(1f, translucentAdapted.alpha, 0.0001f)
        assertTrue(GlassTokens.meetsWcagAa(translucentAdapted, GlassTokens.AmoledBlack))

        val source = OndaArtworkPalette(
            primary = Color(0xFF0A1230),
            secondary = Color(0xFF1A2240),
            tertiary = Color(0xFF111822),
            muted = Color(0xFF222830),
            deepShadow = Color(0xFF030508),
            onAccent = Color.White
        )
        val adapted = computeOledAdaptivePalette(source)
        assertTrue(GlassTokens.meetsWcagAa(adapted.primary, GlassTokens.AmoledBlack))
        assertEquals(0.85f, adapted.secondary.alpha, 0.001f)
        assertEquals(GlassTokens.AmoledBlack, adapted.onAccent)
        assertEquals(source.tertiary, adapted.tertiary)
        assertEquals(source.muted, adapted.muted)
        assertEquals(source.deepShadow, adapted.deepShadow)
    }

    @Test
    fun glassTokensLuminanceContrastLargeTextAndBorderlessBrushes() {
        assertEquals(0.0, GlassTokens.relativeLuminance(Color.Black), 0.0001)
        assertEquals(1.0, GlassTokens.relativeLuminance(Color.White), 0.0001)
        assertEquals(21.0, GlassTokens.contrastRatio(Color.White, Color.Black), 0.01)

        val midGray = Color(0xFF686868)
        assertTrue(GlassTokens.meetsWcagAa(midGray, GlassTokens.AmoledBlack, largeText = true))
        assertFalse(GlassTokens.meetsWcagAa(midGray, GlassTokens.AmoledBlack, largeText = false))

        assertEquals(SolidColor(Color.Transparent), GlassTokens.specularBorderBrush(intensity = 0f))
        assertEquals(SolidColor(Color.Transparent), GlassTokens.topEdgeSpecularBrush(intensity = -1f))
    }

    @Test
    fun likeBurstRevealAndPagerIndicatorEdgeCases() {
        val burst = LikeBurstState()
        val anchor = Offset(24f, 48f)
        burst.trigger(origin = anchor)
        burst.trigger(origin = anchor)
        assertEquals(2, burst.burstId)
        assertEquals(anchor, burst.origin)
        assertEquals(12, burst.particles.size)
        assertEquals(6, burst.particles.count { it.isHeart })
        assertEquals(6, burst.particles.count { !it.isHeart })

        val seen = RevealSeenState(initial = listOf("a", "b"))
        seen.markSeen("a")
        assertTrue(seen.hasSeen("a"))
        assertTrue(seen.hasSeen("b"))
        assertEquals(setOf("a", "b"), seen.snapshotKeys().toSet())

        assertEquals(0f, pagerIndicatorDotProgress(0, 0f, count = 0), 0.0001f)
        assertEquals(1f, pagerIndicatorDotProgress(0, 0.5f, count = 1), 0.0001f)
        assertEquals(0f, pagerIndicatorDotProgress(1, 0.5f, count = 1), 0.0001f)
        assertEquals(0.25f, pagerIndicatorDotProgress(3, -0.25f, count = 4, loop = true), 0.0001f)
        assertEquals(0.75f, pagerIndicatorDotProgress(0, -0.25f, count = 4, loop = true), 0.0001f)
    }

    @Test
    fun synthesizeOndaWordTimingsAndKaraokeHeadEdgeCases() {
        assertTrue(synthesizeOndaWordTimings("   ", lineStartMs = 1_000L).isEmpty())

        val explicit = synthesizeOndaWordTimings(
            text = "one two three",
            lineStartMs = 100L,
            explicitWords = listOf(
                LyricsWord("one", 100L, 120L),
                LyricsWord(" ", 120L, 140L),
                LyricsWord("two", 200L, 250L),
                LyricsWord("three", 300L, 500L)
            ),
            preferExplicit = true,
            maxTokens = 2
        )
        assertEquals(2, explicit.size)
        assertEquals("one", explicit[0].text)
        assertEquals(180L, explicit[0].endTimeMs)
        assertEquals("two...", explicit[1].text)
        assertEquals(280L, explicit[1].endTimeMs)

        val bounded = synthesizeOndaWordTimings(
            text = "alpha beta gamma",
            lineStartMs = 1_000L,
            lineEndMs = 0L,
            nextLineStartMs = 3_000L
        )
        assertEquals(3, bounded.size)
        assertTrue(bounded.last().endTimeMs - 1_000L <= ((3_000L - 1_000L) * 0.92f).toLong() + 1L)
        bounded.forEach { word ->
            assertTrue(word.startTimeMs < word.endTimeMs)
        }

        val cjk = synthesizeOndaWordTimings(
            text = "東京大阪京都札幌",
            lineStartMs = 500L,
            lineEndMs = 2_500L
        )
        assertEquals(listOf("東京大", "阪京都", "札幌"), cjk.map { it.text })

        assertTrue(computeOndaTimedRanges("", emptyList()).isEmpty())

        val caseInsensitive = computeOndaTimedRanges(
            text = "HELLO world",
            words = listOf(
                LyricsWord("hello", 100L, 400L),
                LyricsWord("world...", 400L, 900L)
            )
        )
        assertEquals(2, caseInsensitive.size)
        assertEquals(0, caseInsensitive[0].start)
        assertEquals(5, caseInsensitive[0].end)
        assertEquals(6, caseInsensitive[1].start)
        assertEquals(11, caseInsensitive[1].end)

        val unmatched = computeOndaTimedRanges(
            text = "Stay here",
            words = listOf(LyricsWord("missing", 100L, 500L)),
            fallbackStartMs = 1_000L,
            fallbackEndMs = 2_000L
        )
        assertEquals(2, unmatched.size)

        val head = ondaKaraokeHeadFor("Hi there")
        head.seedSettledFor(5f)
        assertEquals(1, head.motionTick.intValue)
        assertEquals(1f, head.rise[0], 0.0001f)
        assertEquals(0f, head.swell[0], 0.0001f)

        val noMotionHead = ondaKaraokeHeadFor("Hi there")
        val timed = computeOndaTimedRanges(
            text = "Hi there",
            words = listOf(
                LyricsWord("Hi", 1_000L, 1_400L),
                LyricsWord("there", 1_400L, 2_000L)
            )
        )
        noMotionHead.advance(timed, positionSec = 0.5, dt = 0.016f, wordMotion = false)
        assertEquals(0f, noMotionHead.offset.floatValue, 0.0001f)
        assertEquals(0f, noMotionHead.glow.floatValue, 0.0001f)
        assertEquals(0, noMotionHead.motionTick.intValue)
    }

    @Test
    fun blurQuantizeTiersAndSmoothShapeZeroSizeOutline() {
        assertEquals(15.0f, BlurEffectCache.quantize(15.24f), 0.001f)
        assertEquals(24.0f, BlurEffectCache.quantize(24.4f), 0.001f)
        assertEquals(48.0f, BlurEffectCache.quantize(47.2f), 0.001f)
        assertEquals(80.0f, BlurEffectCache.quantize(80.9f), 0.001f)

        val density = Density(2f)
        val shape = SmoothCornerShape(16.dp)
        assertTrue(shape.createOutline(Size.Zero, LayoutDirection.Ltr, density) is Outline.Rectangle)
        assertTrue(shape.createOutline(Size(120f, 0f), LayoutDirection.Ltr, density) is Outline.Rectangle)
        assertTrue(shape.createOutline(Size(0f, 80f), LayoutDirection.Ltr, density) is Outline.Rectangle)
        assertTrue(SmoothCornerShape(0.dp).createOutline(Size(120f, 80f), LayoutDirection.Ltr, density) is Outline.Rectangle)
    }
}
