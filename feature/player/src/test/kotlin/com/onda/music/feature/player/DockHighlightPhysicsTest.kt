package com.onda.music.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DockHighlightPhysicsTest {

    @Test
    fun movingRightLeadsWithEndEdgeAndConvergesCleanly() {
        var s = 0f
        var sv = 0f
        var e = 0f
        var ev = 0f

        val first = stepDockHighlightSpring(s, sv, e, ev, target = 3f, dtSeconds = 1f / 120f)
        assertTrue(first.end > first.start)

        s = first.start
        sv = first.startVel
        e = first.end
        ev = first.endVel
        repeat(180) {
            val next = stepDockHighlightSpring(s, sv, e, ev, target = 3f, dtSeconds = 1f / 120f)
            s = next.start
            sv = next.startVel
            e = next.end
            ev = next.endVel
            assertTrue(s.isFinite() && e.isFinite())
        }

        assertEquals(3f, s, 0.01f)
        assertEquals(3f, e, 0.01f)
    }

    @Test
    fun movingLeftLeadsWithStartEdgeAndFollowsRapidTargetUpdates() {
        var s = 3f
        var sv = 0f
        var e = 3f
        var ev = 0f

        val firstLeft = stepDockHighlightSpring(s, sv, e, ev, target = 0f, dtSeconds = 1f / 120f)
        assertTrue(firstLeft.start < firstLeft.end)

        var draggedTarget = 0f
        repeat(90) { step ->
            draggedTarget = (step / 89f) * 2.6f
            val next = stepDockHighlightSpring(s, sv, e, ev, target = draggedTarget, dtSeconds = 1f / 120f)
            s = next.start
            sv = next.startVel
            e = next.end
            ev = next.endVel
        }
        assertTrue(s > 1.8f && e > 1.8f)

        repeat(180) {
            val next = stepDockHighlightSpring(s, sv, e, ev, target = 2.6f, dtSeconds = 1f / 120f)
            s = next.start
            sv = next.startVel
            e = next.end
            ev = next.endVel
        }
        assertEquals(2.6f, s, 0.01f)
        assertEquals(2.6f, e, 0.01f)
    }
}
