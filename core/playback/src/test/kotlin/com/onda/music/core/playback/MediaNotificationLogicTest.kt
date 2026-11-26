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

    @Test
    fun upgradeNotificationArtworkUrlUpgradesYoutubeThumbsAndStripsQueryAndFragment() {
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
            upgradeNotificationArtworkUrl(
                "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg?sqp=-oaymwEcCPYBEIoBSFXyq4qpAw4IARUAAIhCGAFwAcABBg==&rs=AOn4CLD"
            )
        )
        assertEquals(
            "https://i9.ytimg.com/vi/track123/maxresdefault.jpg",
            upgradeNotificationArtworkUrl("https://i9.ytimg.com/vi/track123/sddefault.jpg#frag")
        )
        assertEquals(
            "https://img.youtube.com/vi/track456/maxresdefault.jpg",
            upgradeNotificationArtworkUrl("https://img.youtube.com/vi/track456/mqdefault.JPG?foo=bar")
        )
        assertEquals(
            "https://i.ytimg.com/vi_webp/track789/maxresdefault.webp",
            upgradeNotificationArtworkUrl("https://i.ytimg.com/vi_webp/track789/default.WEBP?sqp=xyz")
        )
    }

    @Test
    fun upgradeNotificationArtworkUrlPreservesMaxresHq720AndStaticYtimg() {
        val maxres = "https://i.ytimg.com/vi/abc/maxresdefault.jpg?sqp=keep"
        val hq720 = "https://i.ytimg.com/vi/abc/hq720.jpg?sqp=keep"
        val staticYtimg = "https://s.ytimg.com/yts/img/hqdefault.jpg?sqp=keep"
        assertEquals(maxres, upgradeNotificationArtworkUrl(maxres))
        assertEquals(hq720, upgradeNotificationArtworkUrl(hq720))
        assertEquals(staticYtimg, upgradeNotificationArtworkUrl(staticYtimg))
    }

    @Test
    fun buildNotificationArtworkFallbackUrlsGeneratesDeduplicatedCandidates() {
        assertEquals(
            listOf(
                "https://i.ytimg.com/vi/abc/maxresdefault.jpg",
                "https://i.ytimg.com/vi/abc/hqdefault.jpg",
                "https://i.ytimg.com/vi/abc/sddefault.jpg?sqp=orig"
            ),
            buildNotificationArtworkFallbackUrls(
                requestedUrl = "https://i.ytimg.com/vi/abc/maxresdefault.jpg",
                originalUrl = "https://i.ytimg.com/vi/abc/sddefault.jpg?sqp=orig"
            )
        )
        assertEquals(
            listOf(
                "https://i.ytimg.com/vi_webp/abc/maxresdefault.webp",
                "https://i.ytimg.com/vi_webp/abc/hqdefault.jpg",
                "https://i.ytimg.com/vi_webp/abc/hqdefault.webp"
            ),
            buildNotificationArtworkFallbackUrls(
                requestedUrl = "https://i.ytimg.com/vi_webp/abc/maxresdefault.webp",
                originalUrl = "   "
            )
        )
        assertEquals(
            listOf(
                "https://i.ytimg.com/vi/abc/maxresdefault.jpg",
                "https://i.ytimg.com/vi/abc/hqdefault.jpg",
                "https://i.ytimg.com/vi/abc/hqdefault.jpg?sqp=abc"
            ),
            buildNotificationArtworkFallbackUrls(
                requestedUrl = "https://i.ytimg.com/vi/abc/hqdefault.jpg?sqp=abc",
                originalUrl = "https://i.ytimg.com/vi/abc/hqdefault.jpg?sqp=abc"
            )
        )
        assertEquals(
            listOf("https://resources.tidal.com/images/cover.jpg"),
            buildNotificationArtworkFallbackUrls(
                requestedUrl = "https://resources.tidal.com/images/cover.jpg",
                originalUrl = "https://resources.tidal.com/images/cover.jpg"
            )
        )
        assertEquals(
            emptyList<String>(),
            buildNotificationArtworkFallbackUrls(requestedUrl = "   ", originalUrl = null)
        )
    }

    @Test
    fun computeBitmapSampleSizeReturnsPowerOfTwoWithinMaxDim() {
        assertEquals(1, computeBitmapSampleSize(512, 512))
        assertEquals(1, computeBitmapSampleSize(320, 480))
        assertEquals(2, computeBitmapSampleSize(513, 512))
        assertEquals(2, computeBitmapSampleSize(1024, 1024))
        assertEquals(4, computeBitmapSampleSize(1280, 720))
        assertEquals(4, computeBitmapSampleSize(2048, 2048))
        assertEquals(1, computeBitmapSampleSize(0, 512))
        assertEquals(1, computeBitmapSampleSize(512, -10))
        assertEquals(1, computeBitmapSampleSize(1024, 1024, maxDim = 0))
    }

    @Test
    fun buildNotificationButtonSpecsAssignsSlotsAndActiveState() {
        val defaultSpecs = buildNotificationButtonSpecs(NotificationButtonsState())
        assertEquals(
            listOf(
                NotificationButtonSpec(ACTION_TOGGLE_LIKE, NotificationButtonSlot.BACK_SECONDARY, false),
                NotificationButtonSpec(ACTION_CYCLE_REPEAT, NotificationButtonSlot.FORWARD_SECONDARY, false),
                NotificationButtonSpec(ACTION_TOGGLE_SHUFFLE, NotificationButtonSlot.OVERFLOW, false),
                NotificationButtonSpec(ACTION_TOGGLE_SLEEP_TIMER, NotificationButtonSlot.OVERFLOW, false)
            ),
            defaultSpecs
        )

        val activeSpecs = buildNotificationButtonSpecs(
            NotificationButtonsState(
                isLiked = true,
                repeatMode = RepeatMode.ALL,
                shuffleEnabled = true,
                sleepTimerActive = true
            )
        )
        assertEquals(
            listOf(
                NotificationButtonSpec(ACTION_TOGGLE_LIKE, NotificationButtonSlot.BACK_SECONDARY, true),
                NotificationButtonSpec(ACTION_CYCLE_REPEAT, NotificationButtonSlot.FORWARD_SECONDARY, true),
                NotificationButtonSpec(ACTION_TOGGLE_SHUFFLE, NotificationButtonSlot.OVERFLOW, true),
                NotificationButtonSpec(ACTION_TOGGLE_SLEEP_TIMER, NotificationButtonSlot.OVERFLOW, true)
            ),
            activeSpecs
        )

        val repeatOneSpecs = buildNotificationButtonSpecs(
            NotificationButtonsState(repeatMode = RepeatMode.ONE)
        )
        assertEquals(true, repeatOneSpecs[1].active)
    }

    @Test
    fun resolveSkipNextBehaviorCoversEmptyMiddleLastAndSingleItem() {
        assertEquals(SkipNextBehavior.NO_OP, resolveSkipNextBehavior(hasNextMediaItem = false, mediaItemCount = 0))
        assertEquals(SkipNextBehavior.NO_OP, resolveSkipNextBehavior(hasNextMediaItem = true, mediaItemCount = 0))
        assertEquals(SkipNextBehavior.SEEK_NEXT, resolveSkipNextBehavior(hasNextMediaItem = true, mediaItemCount = 3))
        assertEquals(SkipNextBehavior.WRAP_TO_FIRST, resolveSkipNextBehavior(hasNextMediaItem = false, mediaItemCount = 3))
        assertEquals(SkipNextBehavior.WRAP_TO_FIRST, resolveSkipNextBehavior(hasNextMediaItem = false, mediaItemCount = 1))
    }

    @Test
    fun resolveSkipPreviousBehaviorCoversEmptyThresholdStartAndPrevious() {
        assertEquals(
            SkipPreviousBehavior.NO_OP,
            resolveSkipPreviousBehavior(hasPreviousMediaItem = false, mediaItemCount = 0, currentPositionMs = 5_000L)
        )
        assertEquals(
            SkipPreviousBehavior.SEEK_TO_START,
            resolveSkipPreviousBehavior(hasPreviousMediaItem = true, mediaItemCount = 3, currentPositionMs = 3_001L)
        )
        assertEquals(
            SkipPreviousBehavior.SEEK_PREVIOUS,
            resolveSkipPreviousBehavior(hasPreviousMediaItem = true, mediaItemCount = 3, currentPositionMs = 3_000L)
        )
        assertEquals(
            SkipPreviousBehavior.SEEK_PREVIOUS,
            resolveSkipPreviousBehavior(hasPreviousMediaItem = true, mediaItemCount = 3, currentPositionMs = 0L)
        )
        assertEquals(
            SkipPreviousBehavior.SEEK_TO_START,
            resolveSkipPreviousBehavior(hasPreviousMediaItem = false, mediaItemCount = 3, currentPositionMs = 1_500L)
        )
        assertEquals(
            SkipPreviousBehavior.SEEK_TO_START,
            resolveSkipPreviousBehavior(hasPreviousMediaItem = false, mediaItemCount = 1, currentPositionMs = 0L)
        )
    }
}
