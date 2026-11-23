package com.onda.music.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkUrlResolverTest {

    @Test
    fun returnsNullAndBlankUnchanged() {
        assertNull(ArtworkUrlResolver.upgradeHighRes(null))
        assertEquals("", ArtworkUrlResolver.upgradeHighRes(""))
        assertEquals("   ", ArtworkUrlResolver.upgradeHighRes("   "))
    }

    @Test
    fun upgradesGoogleUserContentWidthHeightAndSizeTokens() {
        assertEquals(
            "https://lh3.googleusercontent.com/abc123=w1080-h1080-l90-rj",
            ArtworkUrlResolver.upgradeHighRes("https://lh3.googleusercontent.com/abc123=w120-h120-l90-rj")
        )
        assertEquals(
            "https://yt3.ggpht.com/ytc/avatar=w1080-h1080-l90-rj",
            ArtworkUrlResolver.upgradeHighRes("https://yt3.ggpht.com/ytc/avatar=s88-c-k-c0x00ffffff-no-rj")
        )
        assertEquals(
            "https://lh3.ggpht.com/cover_art=w1080-h1080-l90-rj",
            ArtworkUrlResolver.upgradeHighRes("https://lh3.ggpht.com/cover_art=s256")
        )
    }

    @Test
    fun upgradesYoutubeThumbnailDefaultsToMaxresdefault() {
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
            ArtworkUrlResolver.upgradeHighRes("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg")
        )
        assertEquals(
            "https://i.ytimg.com/vi_webp/dQw4w9WgXcQ/maxresdefault.webp",
            ArtworkUrlResolver.upgradeHighRes("https://i.ytimg.com/vi_webp/dQw4w9WgXcQ/mqdefault.webp")
        )
        assertEquals(
            "https://img.youtube.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
            ArtworkUrlResolver.upgradeHighRes("https://img.youtube.com/vi/dQw4w9WgXcQ/sddefault.jpg")
        )
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
            ArtworkUrlResolver.upgradeHighRes("https://i.ytimg.com/vi/dQw4w9WgXcQ/default.jpg")
        )
    }

    @Test
    fun passesContentFileAndNonGoogleUrlsUnchanged() {
        val contentUri = "content://media/external/audio/albumart/42"
        val fileUri = "file:///storage/emulated/0/Music/cover.jpg"
        val tidalUrl = "https://resources.tidal.com/images/1234/5678/1280x1280.jpg"
        val customUrl = "https://cdn.example.com/art=s100/hqdefault.jpg"

        assertEquals(contentUri, ArtworkUrlResolver.upgradeHighRes(contentUri))
        assertEquals(fileUri, ArtworkUrlResolver.upgradeHighRes(fileUri))
        assertEquals(tidalUrl, ArtworkUrlResolver.upgradeHighRes(tidalUrl))
        assertEquals(customUrl, ArtworkUrlResolver.upgradeHighRes(customUrl))
    }
}
