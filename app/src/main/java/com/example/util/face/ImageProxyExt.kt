package com.example.util.face

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy

/** Bitmap dans le MEME repere que les coordonnees ML Kit (frame redressee). */
fun ImageProxy.toUprightBitmap(): Bitmap? {
    val raw = try { toBitmap() } catch (_: Exception) { return null }
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return raw
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val upright = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    if (upright !== raw) raw.recycle()
    return upright
}