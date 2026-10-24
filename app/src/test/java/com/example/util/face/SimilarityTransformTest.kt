package com.example.util.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la transformation de similarite utilisee pour l'alignement ArcFace.
 * Aucune dependance Android : la geometrie est testable sur JVM.
 */
class SimilarityTransformTest {

    private fun buildTransform(a: Float, b: Float, tx: Float, ty: Float): SimilarityTransform =
        SimilarityTransform(a = a, b = b, tx = tx, ty = ty)

    @Test
    fun `retrouve exactement une transformation connue`() {
        val truth = buildTransform(a = 0.5f, b = 0.2f, tx = 12f, ty = -7f)
        val src = FaceAligner.ARCFACE_TEMPLATE
        val dst = src.map { truth.map(it) }

        val solved = SimilarityTransform.solve(src, dst)

        assertNotNull(solved)
        solved!!
        assertEquals(truth.a, solved.a, 1e-4f)
        assertEquals(truth.b, solved.b, 1e-4f)
        assertEquals(truth.tx, solved.tx, 1e-3f)
        assertEquals(truth.ty, solved.ty, 1e-3f)
        assertEquals(0f, solved.maxError(src.zip(dst)), 1e-2f)
    }

    @Test
    fun `calcule echelle et rotation`() {
        val transform = buildTransform(a = 0.6f, b = 0.8f, tx = 0f, ty = 0f)
        assertEquals(1f, transform.scale, 1e-5f)
        assertEquals(53.1301f, transform.rotationDegrees, 1e-2f)
    }

    @Test
    fun `refuse une geometrie degeneree`() {
        val identical = FaceAligner.ARCFACE_TEMPLATE.map { FacePoint(10f, 10f) }
        assertNull(SimilarityTransform.solve(identical, FaceAligner.ARCFACE_TEMPLATE))

        val collinear = List(5) { FacePoint(it * 10f, it * 10f) }
        assertNull(FaceAligner.computeTransform(collinear))

        assertNull(SimilarityTransform.solve(emptyList(), emptyList()))
    }

    @Test
    fun `ordre des reperes front camera miroir puis non miroir`() {
        // Image 640 de large : yeux inverses (oeil "gauche" de ML Kit a droite de l'image),
        // puis image miroir : le point le plus a gauche du gabarit doit rester a gauche.
        val eyeLeft = FacePoint(400f, 200f)
        val eyeRight = FacePoint(240f, 202f)
        val nose = FacePoint(320f, 280f)
        val mouthLeft = FacePoint(400f, 340f)
        val mouthRight = FacePoint(240f, 342f)

        val straight = FaceAligner.orderLandmarks(
            eyeLeft, eyeRight, nose, mouthLeft, mouthRight, imageWidth = 640, mirrored = false
        )
        assertNotNull(straight)
        straight!!
        assertTrue(straight[0].x < straight[1].x)
        assertTrue(straight[3].x < straight[4].x)
        assertTrue(straight[2].y > straight[0].y)
        assertTrue(straight[3].y > straight[2].y)

        val mirrored = FaceAligner.orderLandmarks(
            eyeLeft, eyeRight, nose, mouthLeft, mouthRight, imageWidth = 640, mirrored = true
        )
        assertNotNull(mirrored)
        mirrored!!
        assertEquals(640f - straight[0].x, mirrored[1].x, 1e-3f)
        assertEquals(640f - straight[1].x, mirrored[0].x, 1e-3f)
        assertTrue(mirrored[0].x < mirrored[1].x)
    }

    @Test
    fun `refuse des yeux confondus`() {
        val result = FaceAligner.orderLandmarks(
            FacePoint(300f, 200f),
            FacePoint(305f, 200f),
            FacePoint(302f, 250f),
            FacePoint(300f, 300f),
            FacePoint(305f, 300f),
            imageWidth = 640
        )
        assertNull(result)
    }

    @Test
    fun `aligne un visage synthetique sur le gabarit`() {
        val truth = buildTransform(a = 0.9f, b = -0.1f, tx = 20f, ty = 30f)
        val landmarks = FaceAligner.ARCFACE_TEMPLATE.map { truth.map(it) }
        val transform = FaceAligner.computeTransform(landmarks)
        assertNotNull(transform)
        assertTrue(transform!!.maxError(landmarks.zip(FaceAligner.ARCFACE_TEMPLATE)) <= 4f)
    }
}
