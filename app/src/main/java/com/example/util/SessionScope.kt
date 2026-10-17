package com.example.util

import com.example.data.model.ActiveTrainingSession
import com.example.data.model.JudoCategory

/**
 * Encapsule l'invariant : « une séance en cours n'appartient qu'à la catégorie pour laquelle
 * elle a été démarrée ».
 *
 * Une séance restée active ne doit jamais s'afficher — ni capter les scans de la douchette —
 * lorsque le coach travaille dans une autre catégorie. Sans ce garde-fou, toutes les cartes de
 * catégorie semblaient mener au pointage de la même séance.
 */
object SessionScope {

    /**
     * Renvoie [session] uniquement si elle appartient bien à [category], sinon `null`.
     * Une séance sans catégorie sélectionnée (le coach est revenu à l'écran de choix) est
     * également considérée comme non affichable.
     */
    fun forCategory(
        session: ActiveTrainingSession?,
        category: JudoCategory?
    ): ActiveTrainingSession? {
        if (session == null || category == null) return null
        return if (session.categoryId == category.id) session else null
    }

    /** Vrai si [session] est une séance en cours affichable pour [category]. */
    fun isActive(
        session: ActiveTrainingSession?,
        category: JudoCategory?
    ): Boolean = forCategory(session, category) != null
}
