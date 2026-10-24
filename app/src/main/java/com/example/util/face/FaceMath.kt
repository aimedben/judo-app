package com.example.util.face

import kotlin.math.sqrt

/** Operations vectorielles de la reconnaissance faciale (aucune dependance reseau). */
object FaceMath {

    private const val EPSILON = 1e-8f

    fun l2Normalize(vector: FloatArray): FloatArray {
        var sum = 0f
        for (v in vector) sum += v * v
        val norm = sqrt(sum)
        if (norm < EPSILON) return vector.copyOf()
        return FloatArray(vector.size) { vector[it] / norm }
    }

    /** Similarite cosinus entre deux vecteurs deja normalises (ou non). */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val denom = sqrt(na) * sqrt(nb)
        return if (denom < EPSILON) 0f else dot / denom
    }

    fun toBytes(vector: FloatArray, target: ByteArray) {
        require(target.size == vector.size * 4) { "Taille de tampon invalide" }
        var offset = 0
        for (v in vector) {
            val bits = v.toRawBits()
            target[offset] = (bits ushr 24).toByte()
            target[offset + 1] = (bits ushr 16).toByte()
            target[offset + 2] = (bits ushr 8).toByte()
            target[offset + 3] = bits.toByte()
            offset += 4
        }
    }

    fun fromBytes(bytes: ByteArray): FloatArray? {
        if (bytes.isEmpty() || bytes.size % 4 != 0) return null
        val out = FloatArray(bytes.size / 4)
        for (i in out.indices) {
            val offset = i * 4
            val bits = ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
            out[i] = Float.fromBits(bits)
        }
        return out
    }
}
