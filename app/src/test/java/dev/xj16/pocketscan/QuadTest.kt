package dev.xj16.pocketscan

import dev.xj16.pocketscan.vision.PointF2
import dev.xj16.pocketscan.vision.Quad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for the pure-Kotlin quad ordering and target-size math used by the
 * perspective-correction step. No OpenCV needed.
 */
class QuadTest {

    @Test
    fun `orders scrambled corners into canonical TL TR BR BL`() {
        // A 100x60 rectangle, corners provided out of order.
        val br = PointF2(100.0, 60.0)
        val tl = PointF2(0.0, 0.0)
        val bl = PointF2(0.0, 60.0)
        val tr = PointF2(100.0, 0.0)

        val quad = Quad.ordered(listOf(br, bl, tr, tl))
        assertNotNull(quad)
        quad!!
        assertEquals(tl, quad.topLeft)
        assertEquals(tr, quad.topRight)
        assertEquals(br, quad.bottomRight)
        assertEquals(bl, quad.bottomLeft)
    }

    @Test
    fun `computes target width and height from longest edges`() {
        val quad = Quad(
            topLeft = PointF2(0.0, 0.0),
            topRight = PointF2(200.0, 0.0),
            bottomRight = PointF2(200.0, 100.0),
            bottomLeft = PointF2(0.0, 100.0),
        )
        assertEquals(200.0, quad.targetWidth(), 0.001)
        assertEquals(100.0, quad.targetHeight(), 0.001)
    }

    @Test
    fun `rejects wrong number of points`() {
        val three = listOf(
            PointF2(0.0, 0.0),
            PointF2(1.0, 0.0),
            PointF2(0.0, 1.0),
        )
        assertNull(Quad.ordered(three))
    }

    @Test
    fun `rejects degenerate quad with duplicate roles`() {
        // All four points collapse to two distinct locations.
        val pts = listOf(
            PointF2(0.0, 0.0),
            PointF2(0.0, 0.0),
            PointF2(10.0, 10.0),
            PointF2(10.0, 10.0),
        )
        assertNull(Quad.ordered(pts))
    }

    @Test
    fun `handles a slightly skewed quad`() {
        // Perspective-skewed: top edge shorter than bottom edge.
        val quad = Quad.ordered(
            listOf(
                PointF2(20.0, 0.0),   // tl
                PointF2(180.0, 10.0), // tr
                PointF2(200.0, 120.0),// br
                PointF2(0.0, 110.0),  // bl
            ),
        )
        assertNotNull(quad)
        quad!!
        // Bottom edge (~200px) should drive width.
        assertEquals(200.0, quad.targetWidth(), 5.0)
    }
}
