package dev.xj16.pocketscan.vision

import android.util.Log

/**
 * Loads the OpenCV native runtime exactly once and remembers whether it
 * succeeded. Every CV entry point checks [isAvailable] first so a device or
 * an emulator missing the native `.so` degrades to a no-op fallback instead of
 * throwing `UnsatisfiedLinkError` at capture time.
 */
object OpenCvLoader {
    private const val TAG = "OpenCvLoader"

    @Volatile
    var isAvailable: Boolean = false
        private set

    @Synchronized
    fun init() {
        if (isAvailable) return
        isAvailable = try {
            // The org.opencv:opencv artifact ships the loader + bundled .so's.
            org.opencv.android.OpenCVLoader.initLocal()
        } catch (t: Throwable) {
            Log.w(TAG, "OpenCV native runtime unavailable; using fallback path", t)
            false
        }
        Log.i(TAG, "OpenCV available = $isAvailable")
    }
}
