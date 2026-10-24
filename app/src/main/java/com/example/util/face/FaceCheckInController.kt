package com.example.util.face

import com.example.data.model.FaceCandidate
import com.example.data.model.FaceDecision
import com.example.data.model.FaceRecognitionConfig

/**
 * Machine a etats de decision : vote temporel sur plusieurs frames, marge minimale entre les
 * deux meilleurs candidats et cooldown par athlete.
 *
 * Aucun pointage n'est declenche ici : le controleur ne fait que qualifier un resultat
 * (certain / incertain / inconnu). Le pointage lui-meme reste celui du ViewModel.
 */
class FaceCheckInController(
    private val config: FaceRecognitionConfig = FaceRecognitionConfig()
) {

    private val votes = HashMap<Long, Int>()
    private val lastPointedAt = HashMap<Long, Long>()
    private var lastTrackId: Int? = null

    fun reset() {
        votes.clear()
        lastTrackId = null
    }

    /**
     * @param trackId identifiant de suivi ML Kit (le vote n'est valable que sur le meme visage)
     * @param scores score de chaque athlete de la galerie (max de ses templates)
     */
    fun evaluate(
        trackId: Int,
        scores: Map<Long, Float>,
        now: Long = System.currentTimeMillis()
    ): FaceDecision {
        if (lastTrackId != trackId) {
            votes.clear()
            lastTrackId = trackId
        }
        if (scores.isEmpty()) return FaceDecision.Unknown

        val ranked = scores.entries
            .sortedByDescending { it.value }
            .map { FaceCandidate(it.key, it.value) }
        val best = ranked.first()
        val second = ranked.getOrNull(1)

        if (best.score < config.seuilBas) {
            votes.clear()
            return FaceDecision.Unknown
        }

        val inCooldown = lastPointedAt[best.memberId]?.let {
            now - it < config.cooldownSeconds * 1000L
        } == true

        val reliableMargin = second == null || (best.score - second.score) >= config.margeMin
        val aboveHigh = best.score >= config.seuilHaut

        if (aboveHigh && reliableMargin && !inCooldown) {
            val count = (votes[best.memberId] ?: 0) + 1
            votes.clear()
            votes[best.memberId] = count
            if (count >= config.nFrames) {
                lastPointedAt[best.memberId] = now
                votes.clear()
                return FaceDecision.Certain(best.memberId, best.score)
            }
            // Pas encore assez de frames stables : on ne pointe pas, on reste prudent.
            return FaceDecision.Unknown
        }

        votes.clear()
        if (inCooldown) return FaceDecision.Unknown
        return FaceDecision.Uncertain(ranked.take(3))
    }
}
