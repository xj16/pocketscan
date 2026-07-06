package dev.xj16.pocketscan.vision

import kotlin.math.hypot
import kotlin.math.max

/** A 2-D point in image pixel coordinates. */
data class PointF2(val x: Double, val y: Double)

/**
 * A convex quadrilateral describing the four detected document corners, in a
 * canonical order: top-left, top-right, bottom-right, bottom-left.
 *
 * Kept as pure Kotlin (no OpenCV types) so ordering and target-size math are
 * unit-testable on the JVM without the native library.
 */
data class Quad(
    val topLeft: PointF2,
    val topRight: PointF2,
    val bottomRight: PointF2,
    val bottomLeft: PointF2,
) {
    val corners: List<PointF2> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /** Longest of the two horizontal edges — the warped output width. */
    fun targetWidth(): Double = max(
        distance(bottomRight, bottomLeft),
        distance(topRight, topLeft),
    )

    /** Longest of the two vertical edges — the warped output height. */
    fun targetHeight(): Double = max(
        distance(topRight, bottomRight),
        distance(topLeft, bottomLeft),
    )

    companion object {
        private fun distance(a: PointF2, b: PointF2): Double =
            hypot(a.x - b.x, a.y - b.y)

        /**
         * Orders four arbitrary points into [Quad] canonical order using the
         * classic sum/diff trick:
         *  - top-left has the smallest (x + y)
         *  - bottom-right has the largest (x + y)
         *  - top-right has the smallest (y - x)
         *  - bottom-left has the largest (y - x)
         *
         * Requires exactly four points; returns null otherwise so callers fall
         * back to the full frame.
         */
        fun ordered(points: List<PointF2>): Quad? {
            if (points.size != 4) return null
            val tl = points.minByOrNull { it.x + it.y }!!
            val br = points.maxByOrNull { it.x + it.y }!!
            val tr = points.minByOrNull { it.y - it.x }!!
            val bl = points.maxByOrNull { it.y - it.x }!!
            // Degenerate detections can map two roles to one vertex.
            val distinct = setOf(tl, tr, br, bl)
            if (distinct.size != 4) return null
            return Quad(tl, tr, br, bl)
        }
    }
}
