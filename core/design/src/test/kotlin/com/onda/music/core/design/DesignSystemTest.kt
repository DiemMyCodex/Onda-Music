package com.onda.music.core.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignSystemTest {

    @Test
    fun dockMetricsCalculatesClearanceWithAndWithoutMiniPlayer() {
        val withMini = DockMetrics.contentBottomPadding(
            hasMiniPlayer = true,
            navigationBarInset = 24.dp
        )
        assertEquals(168.dp, withMini)

        val withoutMini = DockMetrics.contentBottomPadding(
            hasMiniPlayer = false,
            navigationBarInset = 24.dp
        )
        assertEquals(104.dp, withoutMini)
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
}
