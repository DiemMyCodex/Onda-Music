package com.onda.music.core.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
}
