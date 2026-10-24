package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AttendanceWithMember
import com.example.data.model.ClubSettings
import com.example.data.model.Member
import com.example.data.model.ScheduledTrainingSession
import com.example.ui.components.AbsentSmsDialog
import com.example.ui.components.JudokaAvatar
import com.example.ui.theme.JudoGold
import com.example.ui.theme.JudoRed
import com.example.ui.theme.JudoSuccess
import com.example.util.AbsenceAlerts
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class AbsenceAlertRow(
    val member: Member,
    val absencesThisMonth: Int
)

/**
 * Page "Notifications" du tableau de bord administrateur : liste des judokas qui
 * ont cumule 3 absences ou plus sur le mois en cours, avec la MEME definition que
 * l'agenda de la fiche athlete (jours d'entrainement prevus du planning manques).
 * Un tap sur une ligne ouvre la fiche/statistiques de l'athlete.
 */
@Composable
fun AdminAbsenceNotificationsScreen(
    allMembers: List<Member>,
    allAttendance: List<AttendanceWithMember>,
    scheduledSessions: List<ScheduledTrainingSession>,
    clubSettings: ClubSettings,
    onSelectMember: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val monthTitle = SimpleDateFormat("MMMM yyyy", Locale.FRENCH)
        .format(Date()).replaceFirstChar { it.uppercase() }

    // Mode selection multiple : actions "Envoyer SMS aux parents" / "Supprimer".
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showSmsDialog by remember { mutableStateOf(false) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    // Notifications ignorees apres suppression (jusqu'a rechargement complet).
    var dismissedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val monthKey = remember { SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date()) }
    val alerts = remember(
        allMembers, allAttendance, scheduledSessions, clubSettings.officialStartDate, monthKey
    ) {
        val now = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() }
        AbsenceAlerts.membersWithMonthAbsences(
            members = allMembers,
            attendances = allAttendance,
            scheduledSessions = scheduledSessions,
            officialStartDate = clubSettings.officialStartDate,
            todayMillis = now.timeInMillis,
            year = now.get(Calendar.YEAR),
            month0 = now.get(Calendar.MONTH),
            threshold = 3
        ).map { AbsenceAlertRow(it.member, it.absencesMonth) }
    }
    val visibleAlerts = alerts.filter { it.member.id !in dismissedIds }
    val selectedMembers = visibleAlerts.filter { it.member.id in selectedIds }.map { it.member }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("absence_notifications_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Title & Back
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("absence_notifications_back_btn")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
            }
            Column {
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Athletes absents 3 fois et plus — $monthTitle",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = {
                    selectionMode = !selectionMode
                    selectedIds = emptySet()
                    confirmBatchDelete = false
                },
                modifier = Modifier.testTag("absence_notifications_select_toggle_btn")
            ) {
                Icon(
                    imageVector = if (selectionMode) Icons.Default.Close else Icons.Default.DoneAll,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (selectionMode) "Annuler" else "Sélectionner")
            }
        }

        // Barre d'actions de la selection multiple : envoyer un SMS personnalise
        // au parent de chaque athlete (son numero = member.phone) ou supprimer
        // les notifications.
        if (selectionMode && selectedMembers.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showSmsDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = JudoSuccess),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("absence_notifications_send_sms_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Sms,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Envoyer SMS (${selectedMembers.size})",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = { confirmBatchDelete = true },
                    colors = ButtonDefaults.buttonColors(containerColor = JudoRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("absence_notifications_delete_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Supprimer (${selectedMembers.size})",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else if (selectionMode && visibleAlerts.isNotEmpty()) {
            OutlinedButton(
                onClick = { selectedIds = visibleAlerts.map { it.member.id }.toSet() },
                modifier = Modifier.fillMaxWidth().testTag("absence_notifications_select_all_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tout sélectionner")
            }
        }

        if (visibleAlerts.isEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth().testTag("absence_notifications_empty")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(18.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = JudoGold,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Aucune alerte : aucun athlete n'a cumule 3 absences ou plus ce mois-ci.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            Text(
                text = "${alerts.size} athlete(s) concerné(s) — touchez une ligne pour voir les statistiques :",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().testTag("absence_notifications_list"),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items = visibleAlerts, key = { it.member.id }) { alert ->
                val isSelected = alert.member.id in selectedIds
                val severity = when {
                    alert.absencesThisMonth >= 5 -> JudoRed
                    alert.absencesThisMonth >= 4 -> Color(0xFFE67E22)
                    else -> JudoGold
                }
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) JudoSuccess.copy(alpha = 0.10f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, JudoSuccess)
                    else CardDefaults.outlinedCardBorder(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            if (selectionMode) {
                                selectedIds = if (isSelected) {
                                    selectedIds - alert.member.id
                                } else {
                                    selectedIds + alert.member.id
                                }
                            } else {
                                onSelectMember(alert.member.id)
                            }
                        }
                        .testTag("absence_notification_row_${alert.member.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        if (selectionMode) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = if (isSelected) "Sélectionné" else "Non sélectionné",
                                tint = if (isSelected) JudoSuccess
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.size(26.dp).testTag("absence_notification_check_${alert.member.id}")
                            )
                        }
                        Box {
                            JudokaAvatar(
                                name = alert.member.fullName,
                                belt = alert.member.belt,
                                photoUrl = alert.member.photoUrl,
                                sizeDp = 44
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = severity,
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Text(
                                    text = "${alert.absencesThisMonth}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = alert.member.fullName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append("${alert.absencesThisMonth} absence(s) ce mois-ci")
                                    append(" • ${alert.member.groupCode}")
                                    append(" • planning de sa catégorie")
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "Stats",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = severity
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = "Voir ses statistiques",
                            tint = severity
                        )
                    }
                }
            }
        }
    }

    // SMS personnalisé : chaque parent reçoit le message à son numéro (member.phone),
    // un SMS par enfant sélectionné.
    if (showSmsDialog && selectedMembers.isNotEmpty()) {
        AbsentSmsDialog(
            absentMembers = selectedMembers,
            sessionDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
            clubName = "Judo Club Seddouk",
            onDismiss = {
                showSmsDialog = false
                selectionMode = false
                selectedIds = emptySet()
            }
        )
    }

    if (confirmBatchDelete && selectedMembers.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            title = {
                Text("Supprimer ${selectedMembers.size} notification(s) ?")
            },
            text = {
                Text(
                    "Ces alertes d'absence seront retirées de la liste. Les séances de l'historique et les fiches des athletes restent inchangées."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dismissedIds = dismissedIds + selectedIds
                        selectedIds = emptySet()
                        confirmBatchDelete = false
                        selectionMode = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = JudoRed),
                    modifier = Modifier.testTag("absence_notifications_confirm_delete_btn")
                ) {
                    Text("Supprimer", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBatchDelete = false }) {
                    Text("Annuler")
                }
            },
            modifier = Modifier.testTag("absence_notifications_delete_dialog")
        )
    }
}
