package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Attendance
import com.example.data.model.AttendanceWithMember
import com.example.data.model.CoachProfile
import com.example.data.model.JudoCategory
import com.example.data.model.JudoGroup
import com.example.data.model.Member
import com.example.data.model.SessionStatus
import com.example.ui.components.AddEditMemberDialog
import com.example.ui.components.BeltBadge
import com.example.ui.components.GroupBadge
import com.example.ui.components.JudokaAvatar
import com.example.ui.components.MemberAttendanceCalendar
import com.example.ui.theme.JudoGold
import com.example.ui.theme.JudoRed
import com.example.ui.theme.JudoSuccess
import com.example.util.MemberDuplicates

@Composable
fun MembersScreen(
    members: List<Member>,
    todayAttendance: List<AttendanceWithMember>,
    coachProfile: CoachProfile,
    onEditCoachProfile: () -> Unit,
    onSelectMember: (Long) -> Unit,
    onSaveMember: (Member) -> Unit,
    categories: List<JudoCategory> = emptyList(),
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedGroupFilter by remember { mutableStateOf("ALL") }
    var isAddDialogOpen by remember { mutableStateOf(false) }

    val presentTodayIds = remember(todayAttendance) {
        todayAttendance.map { it.memberId }.toSet()
    }

    // Options de la barre de filtres : code de groupe + libellé lisible de la
    // CATÉGORIE (et non le code brut), ordonnées selon la progression judo
    // (Éveil → U9 → U11 → U13 → U15 → U18 → Séniors) et non par ordre alphabétique.
    val groupFilterOptions = remember(members, categories) {
        // Un filtre par CATEGORIE qui contient reellement des athletes.
        // Une categorie n'est retenue que si au moins un athlete lui correspond,
        // ce qui evite les doublons quand une ancienne categorie (libelle libre)
        // et un code de groupe pointent sur les memes judokas.
        val memberCodes = members.map { it.groupCode }.filter { it.isNotBlank() }

        val fromCategories = categories
            .filter { it.isActive && it.groupCode.isNotBlank() }
            .filter { cat -> memberCodes.any { com.example.util.GroupCodes.matches(cat.groupCode, it) } }
            .distinctBy { com.example.util.GroupCodes.canonical(it.groupCode).sorted().joinToString(",") }
            .map { GroupFilterOption(code = it.groupCode, label = it.groupLabel) }

        // Athletes dont le groupe n'est couvert par AUCUNE categorie retenue :
        // un seul filtre de secours par code, jamais de doublon de libelle.
        val coveredCodes = fromCategories
            .flatMap { com.example.util.GroupCodes.canonical(it.code) }
            .toSet()
        val orphans = memberCodes
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() && it !in coveredCodes }
            .distinct()
            .map { GroupFilterOption(code = it, label = JudoGroup.fromString(it).label) }

        (fromCategories + orphans)
            .distinctBy { com.example.util.GroupCodes.canonical(it.code).sorted().joinToString(",") }
            .sortedBy { JudoGroup.fromString(com.example.util.GroupCodes.canonical(it.code).first()).ordinal }
    }

    val filteredMembers = remember(members, searchQuery, selectedGroupFilter, presentTodayIds) {
        members
            .filter { m ->
                selectedGroupFilter == "ALL" ||
                    com.example.util.GroupCodes.matches(selectedGroupFilter, m.groupCode)
            }
            .filter { m ->
                searchQuery.isBlank() ||
                    m.fullName.contains(searchQuery, ignoreCase = true) ||
                    m.qrCode.contains(searchQuery, ignoreCase = true) ||
                    m.phone.contains(searchQuery, ignoreCase = true)
            }
            .sortedBy { it.fullName.lowercase() }
    }

    if (isAddDialogOpen) {
        AddEditMemberDialog(
            initialMember = null,
            coachProfile = coachProfile,
            onEditCoachProfile = onEditCoachProfile,
            findDuplicate = { candidate -> MemberDuplicates.find(members, candidate) },
            onSave = { newMember ->
                onSaveMember(newMember)
                isAddDialogOpen = false
            },
            onDismiss = { isAddDialogOpen = false }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("members_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Athlètes",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black)
                    )
                    Text(
                        text = "${members.size} judoka${if (members.size > 1) "s" else ""} inscrits • ${presentTodayIds.size} présent(s) aujourd'hui",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                Button(
                    onClick = { isAddDialogOpen = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = JudoRed, contentColor = Color.White),
                    modifier = Modifier.testTag("members_add_btn")
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ajouter", fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Rechercher un judoka, QR, tél…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("members_search_field")
            )
        }

        if (groupFilterOptions.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedGroupFilter == "ALL",
                            onClick = { selectedGroupFilter = "ALL" },
                            label = { Text("Toutes les catégories") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = JudoRed,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    items(groupFilterOptions, key = { it.code }) { option ->
                        val count = members.count { com.example.util.GroupCodes.matches(option.code, it.groupCode) }
                        FilterChip(
                            selected = selectedGroupFilter == option.code,
                            onClick = { selectedGroupFilter = option.code },
                            label = { Text("${option.label} ($count)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = JudoRed,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        if (filteredMembers.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aucun athlète ne correspond à cette recherche.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        } else {
            items(filteredMembers, key = { it.id }) { member ->
                MemberListRow(
                    member = member,
                    isPresentToday = presentTodayIds.contains(member.id),
                    onClick = { onSelectMember(member.id) }
                )
            }
        }
    }
}

/** Une puce de la barre de filtres : code technique du groupe + nom lisible de la catégorie. */
private data class GroupFilterOption(
    val code: String,
    val label: String
)

@Composable
private fun MemberListRow(
    member: Member,
    isPresentToday: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPresentToday) {
                JudoSuccess.copy(alpha = 0.06f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("member_row_${member.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            JudokaAvatar(
                name = member.fullName,
                belt = member.belt,
                photoUrl = member.photoUrl,
                sizeDp = 50
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = member.fullName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (member.status == "SUSPENDU") {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = JudoRed.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "Suspendu",
                                color = JudoRed,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BeltBadge(belt = member.belt)
                    GroupBadge(group = member.group)
                }

                if (member.phone.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = member.phone,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isPresentToday) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = JudoSuccess.copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = JudoSuccess,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Présent",
                                color = JudoSuccess,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Ouvrir la fiche",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun MemberDetailScreen(
    member: Member,
    attendanceList: List<Attendance>,
    allClubAttendances: List<AttendanceWithMember>,
    scheduledSessions: List<com.example.data.model.ScheduledTrainingSession> = emptyList(),
    officialStartDate: String = "",
    onBack: () -> Unit,
    onManualCheckIn: (Long) -> Unit,
    onRecordAttendanceForDate: (Long, String) -> Unit,
    onDeleteAttendanceForDate: (Long, String) -> Unit,
    onEditMember: (Member) -> Unit,
    onDeleteMember: (Member) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    val totalAttendances = remember(attendanceList, officialStartDate) {
        // Presences comptees uniquement depuis la date de debut du pointage officiel.
        attendanceList.count { com.example.util.OfficialPeriod.isOfficial(it.dateString, officialStartDate) }
    }
    // Jours d'entrainement repris du planning admin > seances (categorie de l'athlete),
    // pour que l'agenda de la fiche affiche les vrais jours.
    val trainingSlots = remember(scheduledSessions, member) {
        scheduledSessions
            .filter { it.status != SessionStatus.CANCELLED }
            .filter { s ->
                com.example.util.GroupCodes.matches(s.groupCode, member.groupCode) ||
                    s.categoryName.equals(member.group.name, ignoreCase = true)
            }
            .map {
                com.example.ui.components.TrainingSlot(
                    dayOfWeekName = it.dayOfWeekName,
                    startTime = it.startTime,
                    endTime = it.endTime,
                    categoryName = it.categoryName
                )
            }
            .distinctBy { "${it.dayOfWeekName}|${it.startTime}".lowercase() }
            .sortedBy { it.calendarDayOfWeek ?: 7 }
    }
    val clubDistinctDays = remember(allClubAttendances, officialStartDate) {
        // Seules les dates dans la periode de pointage officiel comptent.
        allClubAttendances
            .map { it.dateString }
            .distinct()
            .count { com.example.util.OfficialPeriod.isOfficial(it, officialStartDate) }
    }
    val attendanceRate = if (clubDistinctDays > 0) {
        ((totalAttendances.toDouble() / clubDistinctDays.toDouble()) * 100).toInt().coerceIn(0, 100)
    } else 0

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Supprimer l'athlète ?") },
            text = {
                Text("Cette action supprimera définitivement ${member.fullName} ainsi que son historique de présences.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteMember(member)
                    }
                ) {
                    Text("Supprimer", color = JudoRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("member_detail_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("member_detail_back_btn")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                }
                Text(
                    text = "Fiche de l'athlète",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        JudokaAvatar(
                            name = member.fullName,
                            belt = member.belt,
                            photoUrl = member.photoUrl,
                            sizeDp = 68
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = member.fullName,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                BeltBadge(belt = member.belt)
                                GroupBadge(group = member.group)
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (member.status == "ACTIF") JudoSuccess.copy(alpha = 0.14f) else JudoRed.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = if (member.status == "ACTIF") "Compte actif" else "Compte suspendu",
                                    color = if (member.status == "ACTIF") JudoSuccess else JudoRed,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        InfoStatCard(
                            label = "Présences",
                            value = "$totalAttendances",
                            accent = JudoRed,
                            modifier = Modifier.weight(1f)
                        )
                        InfoStatCard(
                            label = "Assiduité",
                            value = "$attendanceRate%",
                            accent = if (attendanceRate >= 75) JudoSuccess else JudoGold,
                            modifier = Modifier.weight(1f)
                        )
                        InfoStatCard(
                            label = "Groupe",
                            value = member.group.code,
                            accent = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoRow(icon = Icons.Default.Phone, label = "Téléphone", value = member.phone.ifBlank { "—" })
                        InfoRow(icon = Icons.Default.Cake, label = "Date de naissance", value = member.birthDate.ifBlank { "—" })
                        InfoRow(
                            icon = Icons.Default.CheckCircle,
                            label = "QR / Carte",
                            value = if (member.qrCode.isBlank()) "—" else member.qrCode
                        )
                    }

                    if (member.notes.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = member.notes,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Button(
                        onClick = { onManualCheckIn(member.id) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = JudoSuccess, contentColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("member_detail_checkin_btn")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pointer maintenant (présence du jour)", fontWeight = FontWeight.Bold)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { onEditMember(member) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("member_detail_edit_btn")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Modifier")
                        }
                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = JudoRed),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("member_detail_delete_btn")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Supprimer")
                        }
                    }
                }
            }
        }

        item {
            MemberAttendanceCalendar(
                member = member,
                attendanceList = attendanceList,
                allClubAttendances = allClubAttendances,
                trainingSlots = trainingSlots,
                officialStartDate = officialStartDate,
                onRecordAttendanceForDate = onRecordAttendanceForDate,
                onDeleteAttendanceForDate = onDeleteAttendanceForDate
            )
        }
    }
}

@Composable
private fun InfoStatCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = CardDefaults.outlinedCardBorder(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = accent),
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f)
        )
    }
}
