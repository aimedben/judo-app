package com.example.util

/**
 * Identifiants du développeur pour l'application du Judo Club Seddouk.
 *
 * - [DEV_MASTER_PASSWORD] : mot de passe maitre, compile dans l'application et
 *   connu uniquement du developpeur (« aimedjudo »). Saisi a la place du code PIN,
 *   il ouvre toujours l'espace administrateur, meme si le code PIN du cloud est
 *   inconnu de la tablette ou perdu.
 *
 * - Le PIN ADMINISTRATEUR, lui, vit dans la base de donnees (colonne
 *   `admin_pin` de la table `club_settings` de Supabase) : modifie sur une
 *   tablette, il se propage automatiquement a toutes les autres a la
 *   synchronisation. La valeur par defaut reste « 2026 » si la colonne est vide.
 */
object AdminCredentials {
    /** Mot de passe maitre du developpeur : ouvre toujours l'espace administrateur. */
    const val DEV_MASTER_PASSWORD: String = "aimedjudo"
}
