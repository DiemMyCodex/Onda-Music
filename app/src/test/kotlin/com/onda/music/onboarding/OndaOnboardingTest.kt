package com.onda.music.onboarding

import com.onda.music.core.model.PlayerStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OndaOnboardingTest {

    @Test
    fun resolveDefaultOnboardingLanguage_prefersVietnameseForNewInstallOnViLocale() {
        assertEquals(
            "vi",
            resolveDefaultOnboardingLanguage(
                deviceLanguage = "vi",
                storedLanguage = "en",
                onboardingCompleted = false
            )
        )
        assertEquals(
            "vi",
            resolveDefaultOnboardingLanguage(
                deviceLanguage = "vi-VN",
                storedLanguage = "en",
                onboardingCompleted = false
            )
        )
        assertEquals(
            "en",
            resolveDefaultOnboardingLanguage(
                deviceLanguage = "en-US",
                storedLanguage = "en",
                onboardingCompleted = false
            )
        )
        assertEquals(
            "en",
            resolveDefaultOnboardingLanguage(
                deviceLanguage = "vi",
                storedLanguage = "en",
                onboardingCompleted = true
            )
        )
        assertEquals(
            "vi",
            resolveDefaultOnboardingLanguage(
                deviceLanguage = "en",
                storedLanguage = "vi",
                onboardingCompleted = true
            )
        )
    }

    @Test
    fun onboardingFeaturedStyles_exposesFourDistinctSupportedStyles() {
        assertEquals(4, ONBOARDING_FEATURED_STYLES.size)
        assertEquals(4, ONBOARDING_FEATURED_STYLES.toSet().size)
        assertEquals(
            listOf(
                PlayerStyle.POSTER,
                PlayerStyle.FLAGSHIP,
                PlayerStyle.LYRICS,
                PlayerStyle.TURNTABLE
            ),
            ONBOARDING_FEATURED_STYLES
        )
    }

    @Test
    fun onboardingWaveEnvelope_staysBoundedAcrossAllPages() {
        val samples = listOf(-1f, 0f, 0.5f, 1f, 1.5f, 2f, 2.75f, 3f, 5f)
        for (sample in samples) {
            val amp = onboardingWaveEnvelope(sample)
            assertTrue(amp.isFinite())
            assertTrue("Expected $amp in [0.45f, 1.0f]", amp in 0.45f..1.0f)
        }
    }
}
