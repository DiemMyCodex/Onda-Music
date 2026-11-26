package com.onda.music.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class StudioBackdropMode(val label: String) {
    OLED_PURE("OLED Pure"),
    ACOUSTIC_MIST("Acoustic Mist"),
    AGSL_MESH("Onda Mesh"),
    VINYL_DARK("Vinyl Vignette"),
    AURORA_RIBBONS("Aurora Ribbons")
}

@Serializable
enum class StudioArtworkFrame(val label: String) {
    SOFT_SQUIRCLE("Squircle"),
    EDGE_BLEED("Edge Bleed"),
    VINYL_DISC("Vinyl Disc"),
    MINIMAL_FRAME("Precision Frame")
}

@Serializable
enum class StudioSeekbarStyle(val label: String) {
    HARMONIC_WAVE("Harmonic Wave"),
    PRECISION_BAR("Precision Rail"),
    SEGMENTED_PULSE("Pulse Ticks"),
    LIQUID_SPECTRUM("Liquid Spectrum")
}

@Serializable
enum class StudioBottomDeckMode(val label: String) {
    DUAL_DECK("Dual Deck"),
    QUICK_ACTIONS("Quick Actions"),
    MINIMAL("Minimal")
}

@Serializable
enum class StudioQuickAction(val label: String) {
    LIKE("Favorite"),
    LYRICS("Lyrics"),
    QUEUE("Up Next"),
    SHARE("Soundcard"),
    SPEED("Speed & Pitch"),
    STREAM_INFO("Signal Info"),
    LYRICS_OFFSET("Lyric Sync"),
    SLEEP_TIMER("Sleep Timer"),
    EQUALIZER("Audio EQ"),
    DOWNLOAD("Download")
}

@Serializable
enum class IpodFinish(val label: String) {
    OBSIDIAN("Obsidian"),
    SILVER("Brushed Silver"),
    CRIMSON("Studio Crimson"),
    MIDNIGHT("Midnight")
}

@Serializable
enum class BedsideTint(val label: String) {
    AMBER("Warm Amber"),
    CRIMSON("OLED Red"),
    ARTWORK("Artwork Hue")
}

@Immutable
@Serializable
data class PlayerStudioConfig(
    val backdropMode: StudioBackdropMode = StudioBackdropMode.ACOUSTIC_MIST,
    val artworkFrame: StudioArtworkFrame = StudioArtworkFrame.SOFT_SQUIRCLE,
    val seekbarStyle: StudioSeekbarStyle = StudioSeekbarStyle.HARMONIC_WAVE,
    val bottomDeckMode: StudioBottomDeckMode = StudioBottomDeckMode.DUAL_DECK,
    val controlsShiftDp: Int = 0,
    val quickActions: List<StudioQuickAction> = DEFAULT_QUICK_ACTIONS,
    val ipodFinish: IpodFinish = IpodFinish.OBSIDIAN,
    val bedsideTint: BedsideTint = BedsideTint.AMBER,
    val bedsideBurnInGuard: Boolean = true,
    val videoSmartFill: Boolean = true,
    val videoAmbientGlow: Boolean = true
) {
    fun withSwappedQuickAction(slotIndex: Int, action: StudioQuickAction): PlayerStudioConfig {
        val clean = sanitized()
        if (slotIndex !in 0 until QUICK_ACTION_SLOTS) return clean
        val updated = clean.quickActions.toMutableList()
        val existingIndex = updated.indexOf(action)
        if (existingIndex >= 0) {
            if (existingIndex != slotIndex) {
                val displaced = updated[slotIndex]
                updated[slotIndex] = action
                updated[existingIndex] = displaced
            }
        } else {
            updated[slotIndex] = action
        }
        return clean.copy(quickActions = updated.toList())
    }

    fun sanitized(): PlayerStudioConfig {
        val unique = ArrayList<StudioQuickAction>(QUICK_ACTION_SLOTS)
        for (item in quickActions) {
            if (item !in unique) {
                unique.add(item)
                if (unique.size == QUICK_ACTION_SLOTS) break
            }
        }
        if (unique.size < QUICK_ACTION_SLOTS) {
            for (fallback in DEFAULT_QUICK_ACTIONS + StudioQuickAction.entries) {
                if (fallback !in unique) {
                    unique.add(fallback)
                    if (unique.size == QUICK_ACTION_SLOTS) break
                }
            }
        }
        return copy(
            controlsShiftDp = controlsShiftDp.coerceIn(MIN_CONTROLS_SHIFT_DP, MAX_CONTROLS_SHIFT_DP),
            quickActions = unique.toList()
        )
    }

    companion object {
        const val QUICK_ACTION_SLOTS: Int = 4
        const val MIN_CONTROLS_SHIFT_DP: Int = -24
        const val MAX_CONTROLS_SHIFT_DP: Int = 24

        val DEFAULT_QUICK_ACTIONS: List<StudioQuickAction> = listOf(
            StudioQuickAction.LYRICS,
            StudioQuickAction.QUEUE,
            StudioQuickAction.SHARE,
            StudioQuickAction.SPEED
        )
    }
}
