package com.onda.music.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class PlayerStyle(val label: String) {
    FLAGSHIP("Flagship"),
    CLASSIC("Classic"),
    POSTER("Poster"),
    TURNTABLE("Turntable"),
    VIDEO("Full Video"),
    IPOD("iPod"),
    TIMELINE("Timeline"),
    LYRICS("Lyrics Stage"),
    BEDSIDE("Bedside Clock"),
    BEDSIDE_TILES("Bedside Tiles")
}
