package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.example.util.ExtractedMemberInfo

/**
 * Fenetre d'information affichee quand un code scanne ne correspond a aucun membre.
 *
 * Regle metier : on n'ajoute JAMAIS un athlete automatiquement. L'utilisateur doit
 * accepter explicitement ; s'il accepte, le formulaire pre-rempli s'ouvre (il peut
 * encore verifier les donnees avant l'enregistrement).
 */
@Composable
fun ConfirmAddMemberDialog(
    extracted: ExtractedMemberInfo,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val fullName = listOf(extracted.firstName, extracted.lastName)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .ifBlank { "Athlète inconnu" }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
        title = { Text("Athlète non reconnu") },
        text = {
            val details = buildList {
                add("Ce code ne correspond à aucun membre du club.")
                add("Athlète détecté : $fullName")
                extracted.birthDate.trim().takeIf { it.isNotEmpty() }?.let { add("Né(e) le : $it") }
                extracted.phone.trim().takeIf { it.isNotEmpty() }?.let { add("Téléphone : $it") }
                add("Voulez-vous l'ajouter à la liste des membres ?")
            }.joinToString("\n\n")
            Text(details)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Ajouter", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
