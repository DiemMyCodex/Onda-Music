package com.onda.music.core.playback

import com.onda.music.core.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaNotificationLogicTest {

    @Test
    fun nextRepeatModeCyclesOffAllOne() {
        assertEquals(RepeatMode.ALL, nextRepeatMode(RepeatMode.OFF))
        assertEquals(RepeatMode.ONE, nextRepeatMode(RepeatMode.ALL))
        assertEquals(RepeatMode.OFF, nextRepeatMode(RepeatMode.ONE))
    }

    @Test
    fun upgradeNotificationArtworkUrlReturnsNullAndBlankUnchanged() {
        assertNull(upgradeNotificationArtworkUrl(null))
        assertEquals("", upgradeNotificationArtworkUrl(""))
        assertEquals("   ", upgradeNotificationArtworkUrl("   "))
    }

    @Test
    fun upgradeNotificationArtworkUrlPreservesContentAndFileUris() {
        val contentUri = "content://media/external/audio/albumart/99"
        val fileUri = "file:///storage/emulated/0/Music/cover.jpg"
        assertEquals(contentUri, upgradeNotificationArtworkUrl(contentUri))
        assertEquals(fileUri, upgradeNotificationArtworkUrl(fileUri))
    }

    @Test
    fun upgradeNotificationArtworkUrlUpgradesGoogleHostsTo720Square() {
        assertEquals(
            "https://lh3.googleusercontent.com/cover123=w720-h720-l90-rj",
            upgradeNotificationArtworkUrl("https://lh3.googleusercontent.com/cover123=w120-h120-l90-rj")
        )
        assertEquals(
            "https://lh3.googleusercontent.com/cover456=w720-h720-l90-rj",
            upgradeNotificationArtworkUrl("https://lh3.googleusercontent.com/cover456=w226-h226")
        )
        assertEquals(
            "https://yt3.ggpht.com/ytc/avatar=w720-h720-l90-rj",
            upgradeNotificationArtworkUrl("https://yt3.ggpht.com/ytc/avatar=s88-c-k-c0x00ffffff-no-rj")
        )
        assertEquals(
            "https://yt3.ggpht.com/ytc/avatar=w720-h720-l90-rj?sqp=abc",
            upgradeNotificationArtworkUrl("https://yt3.ggpht.com/ytc/avatar=s256?sqp=abc")
        )
    }

    @Test
    fun upgradeNotificationArtworkUrlLeavesNonGoogleUrlsAndParamlessUrlsUnchanged() {
        val nonGoogle = "https://resources.tidal.com/images/1234/5678/1280x1280.jpg"
        val paramlessGoogle = "https://lh3.googleusercontent.com/raw-image"
        assertEquals(nonGoogle, upgradeNotificationArtworkUrl(nonGoogle))
        assertEquals(paramlessGoogle, upgradeNotificationArtworkUrl(paramlessGoogle))
    }
}
