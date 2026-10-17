package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Converts picked photos into small WebP images so that they stay light enough
 * to be stored on the Supabase free tier.
 *
 * Strategy:
 *  - Downscale to a max dimension (avatars only).
 *  - Encode as lossy WebP.
 *  - Keep the file in the app internal storage (robust across restarts, unlike
 *    temporary content:// picker URIs).
 *  - Expose base64 helpers so the (already offline-first) sync queue can push the
 *    image bytes into a text column without needing a Storage bucket.
 */
object PhotoCompressor {

    const val AVATAR_MAX_DIMENSION = 512
    const val AVATAR_QUALITY = 80

    private const val PHOTO_DIR = "photos"
    private const val FILE_SCHEME = "file://"

    /**
     * Reads a content:// (or file://) URI, downscales and returns lossy WebP bytes.
     * Returns null when the image cannot be decoded.
     */
    suspend fun uriToWebp(
        context: Context,
        uri: Uri,
        maxDimension: Int = AVATAR_MAX_DIMENSION,
        quality: Int = AVATAR_QUALITY
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return@withContext null

            var sampleSize = 1
            while (width / sampleSize > maxDimension * 2 && height / sampleSize > maxDimension * 2) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return@withContext null

            val scaled = scaleDown(decoded, maxDimension)
            if (scaled !== decoded) decoded.recycle()
            compress(scaled, quality).also { scaled.recycle() }
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleDown(source: Bitmap, maxDimension: Int): Bitmap {
        val largest = maxOf(source.width, source.height)
        if (largest <= maxDimension) return source
        val ratio = maxDimension.toFloat() / largest.toFloat()
        val targetWidth = (source.width * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray? {
        return try {
            val out = ByteArrayOutputStream()
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            bitmap.compress(format, quality, out)
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Persists WebP bytes to internal storage and returns a "file://" path the UI
     * (and Coil/AsyncImage) can load directly.
     */
    suspend fun saveBytes(context: Context, key: String, bytes: ByteArray): String? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.filesDir, "photos").apply { mkdirs() }
                val safeKey = key.replace(Regex("[^A-Za-z0-9_-]"), "_")
                val file = File(dir, "$safeKey.webp")
                file.writeBytes(bytes)
                "file://${file.absolutePath}"
            } catch (_: Exception) {
                null
            }
        }

    /** Reads the raw bytes of a locally stored photo (accepts file://, content:// or absolute path). */
    suspend fun readBytes(path: String?): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val cleaned = path?.removePrefix("file://")?.removePrefix("file:")
            if (cleaned.isNullOrBlank() || cleaned.startsWith("content:")) return@withContext null
            val file = File(cleaned)
            if (!file.exists()) return@withContext null
            file.readBytes()
        } catch (_: Exception) {
            null
        }
    }

    /** Base64 (no wrap) of the locally stored photo, ready to push to Supabase. */
    suspend fun readBase64(path: String?): String? = withContext(Dispatchers.IO) {
        val bytes = readBytes(path) ?: return@withContext null
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** Decodes base64 WebP coming from the cloud and stores it locally. */
    suspend fun storeBase64(context: Context, key: String, base64: String?): String? {
        val bytes = base64ToBytes(base64) ?: return null
        return saveBytes(context, key, bytes)
    }

    fun bytesToBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun base64ToBytes(encoded: String?): ByteArray? {
        if (encoded.isNullOrBlank()) return null
        return try {
            Base64.decode(encoded, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    /** Deletes a locally stored photo file. */
    suspend fun deleteLocal(path: String?) = withContext(Dispatchers.IO) {
        try {
            val cleaned = path?.removePrefix("file://")?.removePrefix("file:")
            if (cleaned.isNullOrBlank() || cleaned.startsWith("content:")) return@withContext
            File(cleaned).takeIf { it.exists() }?.delete()
        } catch (_: Exception) {
        }
    }
}
