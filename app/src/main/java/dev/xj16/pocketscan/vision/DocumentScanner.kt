package dev.xj16.pocketscan.vision

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

/**
 * The computer-vision core: given a photo of a document, find its four corners
 * and produce a flattened, perspective-corrected, contrast-enhanced scan.
 *
 * Pipeline (all on-device, all OpenCV):
 *   1. Downscale for fast contour work.
 *   2. Grayscale → Gaussian blur → Canny edges → dilate to close gaps.
 *   3. Find external contours, keep the largest 4-vertex convex quad that
 *      covers a meaningful fraction of the frame.
 *   4. Scale the quad back to full resolution and warp with a perspective
 *      transform to a rectangle.
 *   5. Adaptive-threshold to a crisp, high-contrast "scanned" look.
 *
 * If OpenCV is unavailable, or no document-like quad is found, the caller can
 * fall back to the original bitmap — nothing throws.
 */
class DocumentScanner {

    /** Result of a scan attempt. [detected] is false when we used the whole frame. */
    data class Result(
        val bitmap: Bitmap,
        val quad: Quad?,
        val detected: Boolean,
    )

    /**
     * Detects the document quad in [source] without warping. Useful for drawing
     * a live overlay. Returns null if OpenCV is unavailable or nothing found.
     */
    fun detectQuad(source: Bitmap): Quad? {
        if (!OpenCvLoader.isAvailable) return null
        val src = Mat()
        return try {
            Utils.bitmapToMat(source, src)
            findDocumentQuad(src)
        } catch (t: Throwable) {
            Log.w(TAG, "detectQuad failed", t)
            null
        } finally {
            src.release()
        }
    }

    /**
     * Full scan: detect + perspective-correct + enhance. Falls back to a
     * lightly enhanced full frame when no quad is found.
     */
    fun scan(source: Bitmap): Result {
        if (!OpenCvLoader.isAvailable) {
            return Result(source, quad = null, detected = false)
        }
        val src = Mat()
        try {
            Utils.bitmapToMat(source, src)
            val quad = findDocumentQuad(src)
            return if (quad != null) {
                val warped = warpPerspective(src, quad)
                val enhanced = enhance(warped)
                warped.release()
                val out = matToBitmap(enhanced)
                enhanced.release()
                Result(out, quad, detected = true)
            } else {
                val enhanced = enhance(src)
                val out = matToBitmap(enhanced)
                enhanced.release()
                Result(out, quad = null, detected = false)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "scan failed; returning original frame", t)
            return Result(source, quad = null, detected = false)
        } finally {
            src.release()
        }
    }

    // --- internals -----------------------------------------------------------

    /** Finds the best document-like quadrilateral, in full-resolution coords. */
    private fun findDocumentQuad(srcRgba: Mat): Quad? {
        val fullW = srcRgba.cols().toDouble()
        val fullH = srcRgba.rows().toDouble()
        if (fullW < 20 || fullH < 20) return null

        // Downscale so the longest side is ~PROCESS_MAX px for speed.
        val scale = (PROCESS_MAX / maxOf(fullW, fullH)).coerceAtMost(1.0)
        val small = Mat()
        Imgproc.resize(srcRgba, small, Size(fullW * scale, fullH * scale))

        val gray = Mat()
        Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

        val edges = Mat()
        Imgproc.Canny(gray, edges, 75.0, 200.0)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.dilate(edges, edges, kernel)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            edges, contours, hierarchy,
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE,
        )

        val frameArea = (fullW * scale) * (fullH * scale)
        var best: Quad? = null
        var bestArea = frameArea * MIN_AREA_FRACTION

        for (c in contours) {
            val c2f = MatOfPoint2f(*c.toArray())
            val peri = Imgproc.arcLength(c2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
            val pts = approx.toArray()
            if (pts.size == 4 && Imgproc.isContourConvex(MatOfPoint(*pts))) {
                val area = abs(Imgproc.contourArea(approx))
                if (area > bestArea) {
                    val ordered = Quad.ordered(pts.map { PointF2(it.x / scale, it.y / scale) })
                    if (ordered != null) {
                        bestArea = area
                        best = ordered
                    }
                }
            }
            c2f.release()
            approx.release()
        }

        small.release()
        gray.release()
        edges.release()
        kernel.release()
        hierarchy.release()
        contours.forEach { it.release() }
        return best
    }

    /** Warps [srcRgba] so [quad] becomes an axis-aligned rectangle. */
    private fun warpPerspective(srcRgba: Mat, quad: Quad): Mat {
        val w = quad.targetWidth().coerceAtLeast(1.0)
        val h = quad.targetHeight().coerceAtLeast(1.0)

        val srcPts = MatOfPoint2f(
            Point(quad.topLeft.x, quad.topLeft.y),
            Point(quad.topRight.x, quad.topRight.y),
            Point(quad.bottomRight.x, quad.bottomRight.y),
            Point(quad.bottomLeft.x, quad.bottomLeft.y),
        )
        val dstPts = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(w - 1, 0.0),
            Point(w - 1, h - 1),
            Point(0.0, h - 1),
        )
        val transform = Imgproc.getPerspectiveTransform(srcPts, dstPts)
        val out = Mat()
        Imgproc.warpPerspective(srcRgba, out, transform, Size(w, h))
        srcPts.release()
        dstPts.release()
        transform.release()
        return out
    }

    /** Converts to a high-contrast, near-binary "scanned" look. */
    private fun enhance(rgba: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        // Normalize illumination, then adaptive threshold for clean text.
        val norm = Mat()
        Core.normalize(gray, norm, 0.0, 255.0, Core.NORM_MINMAX)
        val thresh = Mat()
        Imgproc.adaptiveThreshold(
            norm, thresh, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY, 15, 12.0,
        )
        val rgbaOut = Mat()
        Imgproc.cvtColor(thresh, rgbaOut, Imgproc.COLOR_GRAY2RGBA)
        gray.release()
        norm.release()
        thresh.release()
        return rgbaOut
    }

    private fun matToBitmap(mat: Mat): Bitmap {
        // Ensure 4-channel RGBA for a valid ARGB_8888 bitmap.
        val rgba = if (mat.type() == CvType.CV_8UC4) {
            mat
        } else {
            Mat().also { Imgproc.cvtColor(mat, it, Imgproc.COLOR_GRAY2RGBA) }
        }
        val bmp = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rgba, bmp)
        if (rgba !== mat) rgba.release()
        return bmp
    }

    companion object {
        private const val TAG = "DocumentScanner"

        /** Longest side (px) used during contour detection. */
        private const val PROCESS_MAX = 900.0

        /** A quad must cover at least this fraction of the frame to count. */
        private const val MIN_AREA_FRACTION = 0.20

        @Suppress("unused")
        private val WHITE = Scalar(255.0, 255.0, 255.0, 255.0)
    }
}
