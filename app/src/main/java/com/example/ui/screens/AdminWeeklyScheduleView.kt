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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CoachUser
import com.example.data.model.JudoCategory
import com.example.data.model.RecurrenceScope
import com.example.data.model.ScheduledTrainingSession
import com.example.ui.theme.JudoGold
import com.example.ui.theme.JudoRed

private val WEEK_DAY_ORDER = listOf(
    "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche"
)

@Composable
fun AdminWeeklyScheduleView(
    sessions: List<ScheduledTrainingSession>,
    categories: List<JudoCategory>,
    coaches: List<CoachUser>,
    onAddNewSession: (String, String?) -> Unit,
    onEditSession: (ScheduledTrainingSession) -> Unit,
    onDuplicateSession: (ScheduledTrainingSession) -> Unit,
    onDeleteSession: (String, RecurrenceScope) -> Unit,
    modifier: Modifier = Modifier
) {
    var sessionPendingDelete by remember { mutableStateOf<ScheduledTrainingSession?>(null) }

    val sessionsByDay = remember(sessions) {
        WEEK_DAY_ORDER.associateWith { day ->
            sessions
                .filter { it.dayOfWeekName.equals(day, ignoreCase = true) }
                .sortedBy { it.startTime }
        }
    }

    sessionPendingDelete?.let { target ->
        if (target.isRecurringWeekly) {
            AlertDialog(
                onDismissRequest = { sessionPendingDelete = null },
                title = { Text("Supprimer la séance récurrente") },
                text = {
                    Text(
                        "Cette séance fait partie d'une série hebdomadaire. " +
                            "Souhaitez-vous supprimer uniquement cette occurrence ou toute la série ?"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onDeleteSession(target.id, RecurrenceScope.ALL_SERIES)
                        sessionPendingDelete = null
                    }) {
                        Text("Toute la série", color = JudoRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        onDeleteSession(target.id, RecurrenceScope.THIS_ONLY)
                        sessionPendingDelete = null
                    }) {
                        Text("Cette séance")
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { sessionPendingDelete = null },
                title = { Text("Supprimer la séance ?") },
                text = {
                    Text(
                        "La séance ${target.categoryName} du ${target.dayOfWeekName} " +
                            "de ${target.startTime} sera retirée du planning."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onDeleteSession(target.id, RecurrenceScope.THIS_ONLY)
                        sessionPendingDelete = null
                    }) {
                        Text("Supprimer", color = JudoRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { sessionPendingDelete = null }) {
                        Text("Annuler")
                    }
                }
            )
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("admin_weekly_schedule_view"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = 32.dp)
    ) {
        item {
            Text(
                text = "Planning hebdomadaire des entraînements",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        WEEK_DAY_ORDER.forEach { day ->
            val daySessions = sessionsByDay[day].orEmpty()

            item(key = "header_$day") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = day,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (daySessions.isEmpty()) {
                                    MaterialTheme.colorScheme.surface
                                } else {
                                    JudoRed.copy(alpha = 0.14f)
                                }
                            ) {
                                Text(
                                    text = "${daySessions.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (daySessions.isEmpty()) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            JudoRed
                                        }
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { onAddNewSession(day, null) },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(JudoRed.copy(alpha = 0.12f))
                                .testTag("add_session_$day")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Ajouter une séance le $day",
                                tint = JudoRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (daySessions.isEmpty()) {
                item(key = "empty_$day") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, bottom = 2.dp)
                    ) {
                        Text(
                            text = "Aucune séance programmée — appuyez sur + pour en ajouter.",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            } else {
                items(daySessions, key = { it.id }) { session ->
                    SessionPlanningCard(
                        session = session,
                        onEdit = { onEditSession(session) },
                        onDuplicate = { onDuplicateSession(session) },
                        onDelete = { sessionPendingDelete = session }
                    )
                }
            }
        }

        if (sessions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventBusy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "Aucune séance planifiée pour le moment.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionPlanningCard(
    session: ScheduledTrainingSession,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp)
            .testTag("session_card_${session.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(58.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = JudoRed,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = session.startTime,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black)
                    )
                }
                Text(
                    text = session.endTime,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .size(width = 3.dp, height = 46.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(JudoRed)
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = session.categoryName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    if (session.isRecurringWeekly) {
                        Surface(shape = RoundedCornerShape(5.dp), color = JudoGold.copy(alpha = 0.18f)) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Séance récurrente",
                                tint = JudoGold,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .size(11.dp)
                            )
                        }
                    }
                }

                val coachLabel = if (session.coachNames.isNotEmpty()) {
                    session.coachNames.joinToString(", ")
                } else {
                    "Encadrement à définir"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsMartialArts,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "$coachLabel • Groupe ${session.groupCode}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Modifier la séance",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDuplicate, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Dupliquer la séance",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Supprimer la séance",
                        tint = JudoRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
