package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.JudoCategory
import com.example.ui.theme.JudoRed
import com.example.ui.theme.JudoSuccess
import com.example.util.CancelledSessionDays
import com.example.util.GroupCodes

/**
 * Dialogue « Séance annulée » : l'admin sélectionne la DATE (AAAA-MM-JJ) et la
 * CATÉGORIE dont la séance n'a pas eu lieu. Le couple (code groupe, date) part dans
 * le journal partagé — les athlètes de cette catégorie ne compteront JAMAIS absents
 * ce jour-là (pas d'alerte, pas d'agenda rouge, pas de SMS, pas de baisse de taux).
 *
 * @param editTarget si non-null : mode ÉDITION (pré-rempli depuis l'historique des
 *   jours annulés) — le bouton unique « Enregistrer » appelle [onUpdateDay].
 */
@Composable
fun CancelledSessionDialog(
    categories: List<JudoCategory>,
    cancelledSessionDays: List<CancelledSessionDays.Day>,
    todayStr: String,
    onCancelDay: (groupCode: String, dateString: String) -> Unit,
    onRestoreDay: (groupCode: String, dateString: String) -> Unit,
    onDismiss: () -> Unit,
    editTarget: CancelledSessionDays.Day? = null,
    onUpdateDay: (oldGroup: String, oldDate: String, newGroup: String, newDate: String) -> Unit = { _, _, _, _ -> }
) {
    val isEditMode = editTarget != null
    val eligibleCategories = remember(categories) {
        categories.filter { it.isActive && it.groupCode.isNotBlank() }
    }
    var dateText by remember { mutableStateOf(editTarget?.dateString ?: todayStr) }
    var selectedCategory by remember(eligibleCategories, editTarget) {
        mutableStateOf(
            editTarget?.let { target ->
                eligibleCategories.firstOrNull { cat ->
                    GroupCodes.matches(
                        target.groupCode, cat.groupCode
                    ) || cat.groupCode.equals(target.groupCode, ignoreCase = true)
                }
            } ?: eligibleCategories.firstOrNull()
        )
    }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    val dateRegex = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    val isDateValid = dateRegex.matches(dateText.trim())
    val alreadyCancelled = !isEditMode && selectedCategory != null && cancelledSessionDays.any {
        it.dateString == dateText.trim() && GroupCodes.matches(
            it.groupCode, selectedCategory!!.groupCode
        )
    }
    val weekdayLabel = remember(dateText) {
        try {
            val d = dateText.trim().split("-").mapNotNull { it.toIntOrNull() }
            if (d.size < 3) return@remember ""
            val cal = java.util.Calendar.getInstance().apply { clear() }
            cal.set(d[0], d[1] - 1, d[2])
            when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
                java.util.Calendar.SUNDAY -> "Dimanche"
                java.util.Calendar.MONDAY -> "Lundi"
                java.util.Calendar.TUESDAY -> "Mardi"
                java.util.Calendar.WEDNESDAY -> "Mercredi"
                java.util.Calendar.THURSDAY -> "Jeudi"
                java.util.Calendar.FRIDAY -> "Vendredi"
                else -> "Samedi"
            }
        } catch (_: Exception) {
            ""
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp)
                .testTag("cancel_session_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (isEditMode) "Modifier la séance annulée" else "Séance annulée",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black)
                )
                Text(
                    text = "Désigne la séance programmée qui n'a pas eu lieu : " +
                        "les athlètes de cette catégorie ne compteront PAS absents ce jour-là.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                // --- Catégorie ---
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Catégorie",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Box {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(
                                1.dp, MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { categoryMenuOpen = true }
                                .testTag("cancel_session_category_picker")
                        ) {
                            Text(
                                text = selectedCategory?.name
                                    ?: editTarget?.groupCode?.let { "$it (hors catégories)" }
                                    ?: "Choisir une catégorie",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = categoryMenuOpen,
                            onDismissRequest = { categoryMenuOpen = false }
                        ) {
                            eligibleCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        selectedCategory = cat
                                        categoryMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                // --- Date ---
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Date de la séance non tenue (AAAA-MM-JJ)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        isError = !isDateValid,
                        singleLine = true,
                        placeholder = { Text(todayStr) },
                        supportingText = {
                            Text(
                                if (isDateValid) "Jour : $weekdayLabel" else "Format attendu : 2026-11-08",
                                color = if (isDateValid) JudoSuccess else MaterialTheme.colorScheme.error
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cancel_session_date_field")
                    )
                }

                // --- Journal des jours déjà annulés (rétablissables, hors mode édition) ---
                if (cancelledSessionDays.isNotEmpty() && !isEditMode) {
                    Text(
                        text = "Jours déjà annulés (${cancelledSessionDays.size})",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    cancelledSessionDays.takeLast(10).forEach { day ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = JudoSuccess.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${day.groupCode} • ${day.dateString}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { onRestoreDay(day.groupCode, day.dateString) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Restore,
                                        contentDescription = null,
                                        tint = JudoSuccess,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Rétablir",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = JudoSuccess, fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // --- Actions ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            1.dp, MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) { Text("Fermer") }

                    Button(
                        onClick = {
                            selectedCategory?.let { cat ->
                                when {
                                    isEditMode -> editTarget?.let { target ->
                                        onUpdateDay(
                                            target.groupCode, target.dateString,
                                            cat.groupCode, dateText.trim()
                                        )
                                    }
                                    alreadyCancelled -> onRestoreDay(cat.groupCode, dateText.trim())
                                    else -> onCancelDay(cat.groupCode, dateText.trim())
                                }
                            }
                        },
                        enabled = isDateValid && selectedCategory != null,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (alreadyCancelled) JudoSuccess else JudoRed,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(52.dp)
                            .testTag("cancel_session_confirm_btn")
                    ) {
                        Text(
                            text = when {
                                isEditMode -> "Enregistrer"
                                alreadyCancelled -> "Rétablir la séance"
                                else -> "Confirmer"
                            },
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
