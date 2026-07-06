package dev.xj16.pocketscan.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Saves and loads scan images in app-internal storage (`filesDir/scans`). The
 * directory is excluded from cloud backups so images never leave the device.
 */
object ImageStore {

    private fun scansDir(context: Context): File =
        File(context.filesDir, "scans").apply { mkdirs() }

    /** Persists [bitmap] as a JPEG and returns its absolute path. */
    fun save(context: Context, bitmap: Bitmap, quality: Int = 85): String {
        val file = File(scansDir(context), "${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        return file.absolutePath
    }

    /** Deletes a previously saved scan; safe to call with a null/missing path. */
    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    /** Loads a gallery [uri] into a bitmap for import. */
    fun loadFromUri(context: Context, uri: Uri): Bitmap? = runCatching {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }.getOrNull()
}
