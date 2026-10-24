package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ActiveTrainingSession
import com.example.data.model.JudoCategory
import com.example.data.model.Member
import com.example.data.model.PastTrainingSession
import com.example.ui.components.AbsentSmsDialog
import com.example.ui.components.JudokaAvatar
import com.example.ui.theme.JudoGold
import com.example.ui.theme.JudoRed
import com.example.ui.theme.JudoSuccess
import com.example.util.AbsentSmsNotifier
import com.example.util.MemberQr
import com.example.util.NameKey
import kotlinx.coroutines.delay

import com.example.data.model.CoachUser
import com.example.data.model.RecurrenceScope
import com.example.data.model.ScheduledTrainingSession
import com.example.data.model.SessionConflict

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSessionsScreen(
    scheduledSessions: List<ScheduledTrainingSession> = emptyList(),
    pastSessions: List<PastTrainingSession>,
    activeSession: ActiveTrainingSession?,
    categories: List<JudoCategory>,
    coaches: List<CoachUser> = emptyList(),
    members: List<Member> = emptyList(),
    clubName: String = "",
    onAddScheduledSession: (ScheduledTrainingSession, Int) -> Unit = { _, _ -> },
    onUpdateScheduledSession: (ScheduledTrainingSession) -> Unit = {},
    onDuplicateScheduledSession: (ScheduledTrainingSession) -> Unit = {},
    onDeleteScheduledSession: (String, RecurrenceScope) -> Unit = { _, _ -> },
    onCheckConflicts: (ScheduledTrainingSession, String?) -> List<SessionConflict> = { _, _ -> emptyList() },
    onCorrectAttendance: (String, String, Boolean) -> Unit,
    onDeletePastSessions: (List<String>) -> Unit = {},
    initialTab: Int = 0,
    modifier: Modifier = Modifier
) {
    var currentMainTab by remember { mutableStateOf(initialTab) }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }
    var selectedSessionDetail by remember { mutableStateOf<PastTrainingSession?>(null) }
    var deleteSelectionMode by remember { mutableStateOf(false) }
    var selectedForDeletion by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmMultiDelete by remember { mutableStateOf(false) }

    var isCreateEditDialogOpen by remember { mutableStateOf(false) }
    var sessionToEdit by remember { mutableStateOf<ScheduledTrainingSession?>(null) }
    var preselectedDay by remember { mutableStateOf<String?>(null) }
    var preselectedTime by remember { mutableStateOf<String?>(null) }

    val filteredSessions = remember(pastSessions, selectedCategoryFilter) {
        if (selectedCategoryFilter == "ALL") {
            pastSessions
        } else {
            pastSessions.filter { it.categoryId.equals(selectedCategoryFilter, ignoreCase = true) || it.categoryName.equals(selectedCategoryFilter, ignoreCase = true) }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 600.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isTablet) 12.dp else 4.dp, vertical = 6.dp)
                .testTag("admin_sessions_screen"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Mode Tabs: Planning Hebdo vs Historique & Émargements
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = CardDefaults.outlinedCardBorder()
            ) {
                TabRow(
                    selectedTabIndex = currentMainTab,
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    Tab(
                        selected = currentMainTab == 0,
                        onClick = { currentMainTab = 0 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.EventNote, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Planning Hebdo (${scheduledSessions.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    )
                    Tab(
                        selected = currentMainTab == 1,
                        onClick = { currentMainTab = 1 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Historique & Émargements (${pastSessions.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    )
                }
            }

            if (currentMainTab == 0) {
                // TAB 0: Planning Hebdomadaire
                AdminWeeklyScheduleView(
                    sessions = scheduledSessions,
                    categories = categories,
                    coaches = coaches,
                    onAddNewSession = { day, time ->
                        sessionToEdit = null
                        preselectedDay = day
                        preselectedTime = time ?: "17:30"
                        isCreateEditDialogOpen = true
                    },
                    onEditSession = { session ->
                        sessionToEdit = session
                        preselectedDay = session.dayOfWeekName
                        preselectedTime = session.startTime
                        isCreateEditDialogOpen = true
                    },
                    onDuplicateSession = { session ->
                        onDuplicateScheduledSession(session)
                    },
                    onDeleteSession = { id, scope ->
                        onDeleteScheduledSession(id, scope)
                    },
                    modifier = Modifier.weight(1f)
                )
            } else {
                // TAB 1: Historique & Émargements
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

        // Active Session Alert if any is running on tatami
        if (activeSession != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = JudoRed.copy(alpha = 0.12f),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(JudoRed)
                    )
                    Column {
                        Text(
                            text = "Séance en direct sur le tatami : ${activeSession.categoryName}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = JudoRed)
                        )
                        Text(
                            text = "Démarrée à ${activeSession.startTime} par ${activeSession.coach1Name}",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            }
        }

        // Selection mode toggle for the history (multi-delete)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (deleteSelectionMode && selectedForDeletion.isNotEmpty()) {
                Button(
                    onClick = { confirmMultiDelete = true },
                    colors = ButtonDefaults.buttonColors(containerColor = JudoRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("delete_selected_sessions_button")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Supprimer (${selectedForDeletion.size})")
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            TextButton(
                onClick = {
                    deleteSelectionMode = !deleteSelectionMode
                    if (!deleteSelectionMode) selectedForDeletion = emptySet()
                }
            ) {
                Icon(
                    imageVector = if (deleteSelectionMode) Icons.Default.Close else Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (deleteSelectionMode) "Annuler la sélection" else "Supprimer des séances",
                    fontSize = 12.sp
                )
            }
        }

        // Category Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = selectedCategoryFilter == "ALL",
                    onClick = { selectedCategoryFilter = "ALL" },
                    label = { Text("Toutes (${pastSessions.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = JudoRed,
                        selectedLabelColor = Color.White
                    )
                )
            }
            items(categories) { cat ->
                val count = pastSessions.count { it.categoryId.equals(cat.id, ignoreCase = true) || it.categoryName.equals(cat.name, ignoreCase = true) }
                FilterChip(
                    selected = selectedCategoryFilter == cat.id,
                    onClick = { selectedCategoryFilter = cat.id },
                    label = { Text("${cat.name} ($count)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = JudoRed,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        // List of Sessions
        if (filteredSessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucune séance trouvée pour ce filtre.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredSessions) { session ->
                    val total = session.effectivePresentCount + session.effectiveAbsentCount
                    val rate = if (total > 0) ((session.effectivePresentCount.toDouble() / total) * 100).toInt() else 0

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = if (deleteSelectionMode && session.id in selectedForDeletion) {
                            BorderStroke(2.dp, JudoRed)
                        } else {
                            CardDefaults.outlinedCardBorder()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (deleteSelectionMode) {
                                    selectedForDeletion = if (session.id in selectedForDeletion) {
                                        selectedForDeletion - session.id
                                    } else {
                                        selectedForDeletion + session.id
                                    }
                                } else {
                                    selectedSessionDetail = session
                                }
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (deleteSelectionMode) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (session.id in selectedForDeletion) JudoRed
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = if (session.id in selectedForDeletion) "Sélectionnée" else "Non sélectionnée",
                                                tint = if (session.id in selectedForDeletion) Color.White else Color.Transparent,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(JudoRed.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.EventNote,
                                                contentDescription = null,
                                                tint = JudoRed,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = "Séance ${session.categoryName}",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = session.dateString,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = JudoSuccess.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "$rate% assiduité",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = JudoSuccess
                                        )
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "${session.startTime} - ${session.endTime}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.SportsMartialArts, contentDescription = null, modifier = Modifier.size(14.dp), tint = JudoGold)
                                    Text(
                                        text = "Coach : " + listOfNotNull(
                                            session.coach1Name.takeIf { it.isNotBlank() },
                                            session.coach2Name.takeIf { it.isNotBlank() }
                                        ).joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = JudoGold,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = "🟢 ${session.effectivePresentCount} Présents",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "🔴 ${session.effectiveAbsentCount} Absents",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    TextButton(
                                        onClick = { selectedSessionDetail = session },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Corriger", fontSize = 12.sp)
                                    }

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                        contentDescription = "Détails",
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail Dialog: consultation + edition directe de l'emargement, avec confirmation.
    if (selectedSessionDetail != null) {
        val session = selectedSessionDetail!!
        var detailTab by remember(session.id) { mutableStateOf(0) }
        var showAbsentSmsDialog by remember(session.id) { mutableStateOf(false) }
        var searchQuery by remember(session.id) { mutableStateOf("") }
        var scanBuffer by remember(session.id) { mutableStateOf("") }
        var scanFeedback by remember(session.id) { mutableStateOf<String?>(null) }
        var scanFeedbackError by remember(session.id) { mutableStateOf(false) }
        var pendingToggleName by remember(session.id) { mutableStateOf<String?>(null) }
        var pendingTogglePresent by remember(session.id) { mutableStateOf(false) }
        val scanFocusRequester = remember { FocusRequester() }

        val sessionRoster = remember(members, session.id, session.groupCode) {
            AbsentSmsNotifier.membersOfGroup(members, session.groupCode)
        }
        // Miroir local du roster : chaque changement confirme est persiste via onCorrectAttendance.
        var presentNames by remember(session.id) {
            mutableStateOf(
                session.presentMemberNames
                    .associateBy { NameKey.canonical(it) }
                    .values
                    .toSet()
            )
        }

        fun isPresent(member: Member): Boolean =
            presentNames.any { NameKey.same(it, member.fullName) }

        fun requestToggle(name: String, markPresent: Boolean) {
            pendingToggleName = name
            pendingTogglePresent = markPresent
        }

        fun confirmToggle() {
            val name = pendingToggleName ?: return
            val markPresent = pendingTogglePresent
            onCorrectAttendance(session.id, name, markPresent)
            presentNames = if (markPresent) {
                presentNames.filterNot { NameKey.same(it, name) }.toSet() + name
            } else {
                presentNames.filterNot { NameKey.same(it, name) }.toSet()
            }
            scanFeedbackError = false
            scanFeedback = "$name a été marqué(e) ${if (markPresent) "présent(e)" else "absent(e)"} ✓"
            pendingToggleName = null
        }

        fun handleScan(raw: String) {
            val payload = raw.trim()
            if (payload.isBlank()) return
            val qr = MemberQr.normalize(payload)
            if (qr.isBlank()) {
                scanFeedbackError = true
                scanFeedback = "Lecture non reconnue : le code de la carte n'a pas été trouvé."
                return
            }
            val member = sessionRoster.firstOrNull { it.qrCode.equals(qr, ignoreCase = true) }
                ?: qr.removePrefix(MemberQr.PREFIX).toLongOrNull()
                    ?.let { id -> sessionRoster.firstOrNull { it.id == id } }
                ?: members.firstOrNull { it.qrCode.equals(qr, ignoreCase = true) }
            when {
                member == null -> {
                    scanFeedbackError = true
                    scanFeedback = "Carte $qr : aucune fiche n'a été trouvée avec ce code."
                }
                AbsentSmsNotifier.membersOfGroup(listOf(member), session.groupCode).isEmpty() -> {
                    scanFeedbackError = true
                    scanFeedback = "${member.fullName} n'appartient pas à la séance ${session.categoryName}."
                }
                isPresent(member) -> {
                    scanFeedbackError = false
                    scanFeedback = "${member.fullName} est déjà marqué(e) présent(e) pour cette séance."
                }
                else -> {
                    scanFeedbackError = false
                    scanFeedback = "Carte lue : confirmez le pointage de ${member.fullName}."
                    requestToggle(member.fullName, true)
                }
            }
        }

        // Une douchette sans suffixe Enter termine quand le flux de caracteres se calme.
        LaunchedEffect(scanBuffer) {
            val pending = scanBuffer
            if (pending.isNotBlank()) {
                delay(450)
                if (scanBuffer == pending) {
                    scanBuffer = ""
                    handleScan(pending)
                }
            }
        }

        val presentRows: List<Pair<Member?, String>> = buildList {
            sessionRoster.filter { isPresent(it) }.forEach { add(it to it.fullName) }
            presentNames
                .filter { name -> sessionRoster.none { NameKey.same(it.fullName, name) } }
                .sorted()
                .forEach { add(null to it) }
        }
        val absentRows: List<Pair<Member?, String>> = buildList {
            sessionRoster.filterNot { isPresent(it) }.forEach { add(it to it.fullName) }
            session.absentMemberNames
                .filter { name ->
                    presentNames.none { NameKey.same(it, name) } &&
                        sessionRoster.none { NameKey.same(it.fullName, name) }
                }
                .sorted()
                .forEach { add(null to it) }
        }
        val visibleRows = (if (detailTab == 0) presentRows else absentRows)
            .filter { searchQuery.isBlank() || it.second.contains(searchQuery, ignoreCase = true) }
        val absentMemberObjects = remember(absentRows) {
            AbsentSmsNotifier.resolveAbsentMembers(absentRows.map { it.second }, sessionRoster)
        }

        Dialog(onDismissRequest = { selectedSessionDetail = null }) {
            if (showAbsentSmsDialog) {
                AbsentSmsDialog(
                    absentMembers = absentMemberObjects,
                    sessionDate = session.dateString,
                    sessionStartTime = session.startTime,
                    clubName = clubName,
                    onDismiss = { showAbsentSmsDialog = false }
                )
            }
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp)
                    .padding(vertical = 8.dp)
                    .testTag("hist_correction_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Séance ${session.categoryName}",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "${session.dateString} • ${session.startTime} - ${session.endTime}",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                        IconButton(onClick = { selectedSessionDetail = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer")
                        }
                    }

                    // Stat cards row (live: reflecte les corrections deja confirmees)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = JudoSuccess.copy(alpha = 0.15f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "${presentNames.size}", fontWeight = FontWeight.Bold, color = JudoSuccess, fontSize = 18.sp)
                                Text(text = "Présents", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "${absentRows.size}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, fontSize = 18.sp)
                                Text(text = "Absents", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = JudoGold.copy(alpha = 0.15f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "${presentNames.size + absentRows.size}", fontWeight = FontWeight.Bold, color = JudoGold, fontSize = 18.sp)
                                Text(text = "Total inscrits", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Text(
                        text = "Coachs présents : ${session.coach1Name}${if (session.coach2Present) " • ${session.coach2Name}" else ""}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = JudoGold)
                    )

                    TabRow(selectedTabIndex = detailTab) {
                        Tab(
                            selected = detailTab == 0,
                            onClick = { detailTab = 0 },
                            text = { Text("Présents (${presentNames.size})") }
                        )
                        Tab(
                            selected = detailTab == 1,
                            onClick = { detailTab = 1 },
                            text = { Text("Absents (${absentRows.size})") }
                        )
                    }

                    OutlinedTextField(
                        value = scanBuffer,
                        onValueChange = { value ->
                            scanFeedback = null
                            if (value.contains('\n')) {
                                value.split('\n').filter { it.isNotBlank() }.forEach { handleScan(it.trim()) }
                                scanBuffer = value.substringAfterLast('\n')
                            } else {
                                scanBuffer = value
                            }
                        },
                        label = { Text("Douchette : scanner la carte") },
                        placeholder = { Text("Scannez la carte pour pointer l'athlète") },
                        leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { scanFocusRequester.requestFocus() }) {
                                Icon(Icons.Default.CenterFocusWeak, contentDescription = "Récapturer la douchette")
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (scanBuffer.isNotBlank()) {
                                    handleScan(scanBuffer)
                                    scanBuffer = ""
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(scanFocusRequester)
                            .testTag("hist_scan_field")
                    )

                    scanFeedback?.let { message ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (scanFeedbackError) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                            } else {
                                JudoSuccess.copy(alpha = 0.12f)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("hist_scan_feedback")
                        ) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (scanFeedbackError) MaterialTheme.colorScheme.error else JudoSuccess
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Rechercher judoka...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("hist_correction_search_field")
                    )

                    Text(
                        text = "Touchez un athlète pour changer sa présence (confirmation demandée) :",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .testTag("hist_correction_list"),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (visibleRows.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Aucun athlète dans cette liste.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        } else {
                            items(
                                items = visibleRows,
                                key = { (member, name) -> member?.let { "m_${it.id}" } ?: "n_$name" }
                            ) { (member, name) ->
                                val isPresentNow = if (member != null) {
                                    isPresent(member)
                                } else {
                                    presentNames.any { NameKey.same(it, name) }
                                }
                                AttendanceEditableRow(
                                    name = name,
                                    member = member,
                                    isPresent = isPresentNow,
                                    modifier = Modifier.testTag("hist_correction_row_${member?.id ?: name}"),
                                    onClick = { requestToggle(name, !isPresentNow) }
                                )
                            }
                        }
                    }

                    if (detailTab == 1 && absentMemberObjects.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showAbsentSmsDialog = true },
                            border = BorderStroke(1.5.dp, JudoRed),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = JudoRed),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_absent_sms_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Message,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SMS aux parents des absents (${absentMemberObjects.size})",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val pendingName = pendingToggleName
                    if (pendingName != null) {
                        val accent = if (pendingTogglePresent) JudoSuccess else MaterialTheme.colorScheme.error
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = accent.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, accent),
                            modifier = Modifier.fillMaxWidth().testTag("hist_correction_confirm")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (pendingTogglePresent) {
                                        "Confirmer le pointage de $pendingName ?"
                                    } else {
                                        "Confirmer l'absence de $pendingName ?"
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = accent)
                                )
                                Text(
                                    text = "Cette action modifie l'émargement de la séance ${session.categoryName} du ${session.dateString}.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { pendingToggleName = null }) {
                                        Text("Annuler", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { confirmToggle() },
                                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                                    ) {
                                        Text(if (pendingTogglePresent) "Marquer présent" else "Marquer absent")
                                    }
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(
                            onClick = { selectedSessionDetail = null },
                            colors = ButtonDefaults.buttonColors(containerColor = JudoRed)
                        ) {
                            Text("Fermer")
                        }
                    }
                }
            }
        }
    }
    }
}
}

    // Multi-delete confirmation
    if (confirmMultiDelete) {
        AlertDialog(
            onDismissRequest = { confirmMultiDelete = false },
            title = { Text("Supprimer ${selectedForDeletion.size} séance(s) ?") },
            text = {
                Text(
                    "Les séances sélectionnées disparaîtront de l'historique, avec leurs émargements, " +
                        "du club et du cloud. Cette action est définitive."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePastSessions(selectedForDeletion.toList())
                        confirmMultiDelete = false
                        selectedForDeletion = emptySet()
                        deleteSelectionMode = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JudoRed)
                ) {
                    Text("Supprimer", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmMultiDelete = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    if (isCreateEditDialogOpen) {
        CreateEditSessionDialog(
            initialSession = sessionToEdit,
            categories = categories,
            coaches = coaches,
            preselectedDayName = preselectedDay,
            preselectedStartTime = preselectedTime,
            onCheckConflicts = onCheckConflicts,
            onSaveSession = { session, repeatWeeks ->
                if (sessionToEdit == null) {
                    onAddScheduledSession(session, repeatWeeks)
                } else {
                    onUpdateScheduledSession(session)
                }
                isCreateEditDialogOpen = false
                sessionToEdit = null
            },
            onDismiss = {
                isCreateEditDialogOpen = false
                sessionToEdit = null
            }
        )
    }
}

@Composable
private fun AttendanceEditableRow(
    name: String,
    member: Member?,
    isPresent: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (member != null) {
                JudokaAvatar(
                    name = member.fullName,
                    belt = member.belt,
                    photoUrl = member.photoUrl,
                    sizeDp = 30
                )
            }
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                )
                Text(
                    text = if (member != null) {
                        "Ceinture ${member.beltName.lowercase()}"
                    } else {
                        "Hors effectif de la s?ance"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isPresent) {
                JudoSuccess.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
            }
        ) {
            Text(
                text = if (isPresent) "Pr?sent ?" else "Absent",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPresent) JudoSuccess else MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

