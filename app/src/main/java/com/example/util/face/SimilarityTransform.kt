package com.example.util.face

import kotlin.math.abs
import kotlin.math.sqrt

/** Point 2D pur (aucune dependance Android) pour les transformations geometriques. */
data class FacePoint(val x: Float, val y: Float)

/**
 * Transformation de similarite 2D : rotation + echelle uniforme + translation.
 *
 *   x' = a*x - b*y + tx
 *   y' = b*x + a*y + ty
 *
 * Resolue par moindres carres sur les 5 points du gabarit ArcFace (pas de SVD, donc testable
 * sur JVM sans Android). `a = s*cos(theta)`, `b = s*sin(theta)`.
 */
data class SimilarityTransform(
    val a: Float,
    val b: Float,
    val tx: Float,
    val ty: Float
) {
    val scale: Float get() = sqrt(a * a + b * b)
    val rotationDegrees: Float get() = Math.toDegrees(kotlin.math.atan2(b, a).toDouble()).toFloat()

    fun map(point: FacePoint): FacePoint = FacePoint(
        x = a * point.x - b * point.y + tx,
        y = b * point.x + a * point.y + ty
    )

    fun maxError(pairs: List<Pair<FacePoint, FacePoint>>): Float =
        pairs.maxOfOrNull { (src, dst) ->
            val p = map(src)
            sqrt((p.x - dst.x) * (p.x - dst.x) + (p.y - dst.y) * (p.y - dst.y))
        } ?: 0f

    companion object {
        /** @return null si moins de 3 points ou geometrie degeneree (points colineaires). */
        fun solve(src: List<FacePoint>, dst: List<FacePoint>): SimilarityTransform? {
            if (src.size < 3 || src.size != dst.size) return null

            // M * [a, b, tx, ty]^T = v, construit sur les equations x' et y'.
            val normal = Array(4) { DoubleArray(4) }
            val rhs = DoubleArray(4)
            fun addRow(row: DoubleArray, value: Double) {
                for (i in 0 until 4) {
                    rhs[i] += row[i] * value
                    for (j in 0 until 4) normal[i][j] += row[i] * row[j]
                }
            }
            src.indices.forEach { i ->
                val s = src[i]
                val d = dst[i]
                addRow(doubleArrayOf(s.x.toDouble(), -s.y.toDouble(), 1.0, 0.0), d.x.toDouble())
                addRow(doubleArrayOf(s.y.toDouble(), s.x.toDouble(), 0.0, 1.0), d.y.toDouble())
            }

            val solved = gaussianSolve(normal, rhs) ?: return null
            val transform = SimilarityTransform(
                a = solved[0].toFloat(),
                b = solved[1].toFloat(),
                tx = solved[2].toFloat(),
                ty = solved[3].toFloat()
            )
            if (transform.scale < 1e-4f || transform.scale > 100f) return null
            return transform
        }

        private fun gaussianSolve(matrix: Array<DoubleArray>, vector: DoubleArray): DoubleArray? {
            val n = vector.size
            val m = Array(n) { r -> DoubleArray(n + 1) { c -> if (c < n) matrix[r][c] else vector[r] } }
            val magnitude = matrix.maxOfOrNull { row -> row.maxOfOrNull { abs(it) } ?: 0.0 } ?: 0.0
            val pivotEpsilon = 1e-9 * magnitude.coerceAtLeast(1.0)

            for (col in 0 until n) {
                var pivot = col
                for (r in col + 1 until n) if (abs(m[r][col]) > abs(m[pivot][col])) pivot = r
                if (abs(m[pivot][col]) <= pivotEpsilon) return null
                if (pivot != col) {
                    val tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp
                }
                val diag = m[col][col]
                for (c in col..n) m[col][c] /= diag
                for (r in 0 until n) {
                    if (r == col) continue
                    val factor = m[r][col]
                    if (factor == 0.0) continue
                    for (c in col..n) m[r][c] -= factor * m[col][c]
                }
            }
            return DoubleArray(n) { m[it][n] }
        }
    }
}
