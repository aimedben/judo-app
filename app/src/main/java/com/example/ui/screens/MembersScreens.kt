package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Attendance
import com.example.data.model.AttendanceWithMember
import com.example.data.model.CoachProfile
import com.example.data.model.CoachUser
import com.example.data.model.JudoCategory
import com.example.data.model.JudoGroup
import com.example.data.model.Member
import com.example.data.model.SessionStatus
import com.example.ui.components.AddEditMemberDialog
import com.example.ui.components.BeltBadge
import com.example.ui.components.GroupBadge
import com.example.ui.components.HeroActionButton
import com.example.ui.components.HeroStat
import com.example.ui.components.JudoHeroCard
import com.example.ui.components.JudokaAvatar
import com.example.ui.components.MemberAttendanceCalendar
import com.example.ui.theme.JudoGold
import com.example.ui.theme.JudoRed
import com.example.ui.theme.JudoSuccess
import com.example.ui.theme.JudoWarning
import coil.compose.AsyncImage
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
    coaches: List<CoachUser> = emptyList(),
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    selectedGroupFilter: String = "ALL",
    onSelectedGroupFilterChange: (String) -> Unit = {},
    allAttendance: List<AttendanceWithMember> = emptyList(),
    scheduledSessions: List<com.example.data.model.ScheduledTrainingSession> = emptyList(),
    officialStartDate: String = "",
    modifier: Modifier = Modifier
) {
    var isAddDialogOpen by remember { mutableStateOf(false) }
    var quickFilter by remember { mutableStateOf(MemberQuickFilter.ALL) }

    // Absences du mois par athlete (meme definition que la fiche : jours de planning
    // de SA categorie, hors periode de blessure). Sert a assombrir les cartes.
    val absenceCounts = remember(allAttendance, scheduledSessions, officialStartDate, members) {
        val now = java.util.Calendar.getInstance()
        com.example.util.AbsenceAlerts.membersWithMonthAbsences(
            members = members,
            attendances = allAttendance,
            scheduledSessions = scheduledSessions,
            officialStartDate = officialStartDate,
            todayMillis = now.timeInMillis,
            year = now.get(java.util.Calendar.YEAR),
            month0 = now.get(java.util.Calendar.MONTH),
            threshold = 1
        ).associate { it.member.id to it.absencesMonth }
    }

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

    val filteredMembers = remember(members, searchQuery, selectedGroupFilter, presentTodayIds, quickFilter) {
        members
            .filter { m ->
                when (quickFilter) {
                    MemberQuickFilter.ALL -> true
                    MemberQuickFilter.INJURED -> m.isInjured
                    MemberQuickFilter.SUSPENDED -> m.isSuspended
                    MemberQuickFilter.PRESENT -> presentTodayIds.contains(m.id)
                    MemberQuickFilter.ABSENT -> !presentTodayIds.contains(m.id)
                }
            }
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
            coaches = coaches,
            categories = categories,
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
            JudoHeroCard(
                title = "Athlètes",
                subtitle = "${members.size} judoka${if (members.size > 1) "s" else ""} inscrits • ${presentTodayIds.size} présent(s) aujourd'hui",
                icon = Icons.Default.People,
                action = {
                    HeroActionButton(
                        text = "Ajouter",
                        icon = Icons.Default.PersonAdd,
                        onClick = { isAddDialogOpen = true },
                        modifier = Modifier.testTag("members_add_btn")
                    )
                },
                stats = listOf(
                    HeroStat(value = "${members.size}", label = "Inscrits"),
                    HeroStat(value = "${presentTodayIds.size}", label = "Présents"),
                    HeroStat(
                        value = "${members.count { it.isInjured }}",
                        label = "Blessés"
                    )
                )
            )
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Rechercher un judoka, QR, tél…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("members_search_field")
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = quickFilter == MemberQuickFilter.ALL,
                        onClick = { quickFilter = MemberQuickFilter.ALL },
                        label = { Text("Tous (${members.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = JudoRed,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("members_filter_all")
                    )
                }
                item {
                    FilterChip(
                        selected = quickFilter == MemberQuickFilter.INJURED,
                        onClick = {
                            quickFilter = if (quickFilter == MemberQuickFilter.INJURED) {
                                MemberQuickFilter.ALL
                            } else {
                                MemberQuickFilter.INJURED
                            }
                        },
                        label = { Text("Blessés (${members.count { it.isInjured }})") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = JudoWarning,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier.testTag("members_filter_injured")
                    )
                }
                item {
                    FilterChip(
                        selected = quickFilter == MemberQuickFilter.SUSPENDED,
                        onClick = {
                            quickFilter = if (quickFilter == MemberQuickFilter.SUSPENDED) {
                                MemberQuickFilter.ALL
                            } else {
                                MemberQuickFilter.SUSPENDED
                            }
                        },
                        label = { Text("Suspendus (${members.count { it.isSuspended }})") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = JudoRed,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier.testTag("members_filter_suspended")
                    )
                }
                item {
                    FilterChip(
                        selected = quickFilter == MemberQuickFilter.PRESENT,
                        onClick = {
                            quickFilter = if (quickFilter == MemberQuickFilter.PRESENT) {
                                MemberQuickFilter.ALL
                            } else {
                                MemberQuickFilter.PRESENT
                            }
                        },
                        label = { Text("Présents (${presentTodayIds.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = JudoSuccess,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("members_filter_present")
                    )
                }
                item {
                    FilterChip(
                        selected = quickFilter == MemberQuickFilter.ABSENT,
                        onClick = {
                            quickFilter = if (quickFilter == MemberQuickFilter.ABSENT) {
                                MemberQuickFilter.ALL
                            } else {
                                MemberQuickFilter.ABSENT
                            }
                        },
                        label = { Text("Absents (${(members.size - presentTodayIds.size).coerceAtLeast(0)})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("members_filter_absent")
                    )
                }
            }
        }

        if (groupFilterOptions.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedGroupFilter == "ALL",
                            onClick = { onSelectedGroupFilterChange("ALL") },
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
                            onClick = { onSelectedGroupFilterChange(option.code) },
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
                        text = when (quickFilter) {
                            MemberQuickFilter.INJURED -> "Aucun athlète blessé actuellement."
                            MemberQuickFilter.SUSPENDED -> "Aucun athlète suspendu actuellement."
                            MemberQuickFilter.PRESENT -> "Aucun athlète présent aujourd'hui."
                            MemberQuickFilter.ABSENT -> "Aucun athlète absent : tout le monde est présent."
                            MemberQuickFilter.ALL -> "Aucun athlète ne correspond à cette recherche."
                        },
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
                    absenceCount = absenceCounts[member.id] ?: 0,
                    onClick = { onSelectMember(member.id) }
                )
            }
        }
    }
}

/** Filtres rapides de la liste des athlètes (tous / blessés / présents / absents). */
private enum class MemberQuickFilter { ALL, INJURED, SUSPENDED, PRESENT, ABSENT }

/** Une puce de la barre de filtres : code technique du groupe + nom lisible de la catégorie. */
private data class GroupFilterOption(
    val code: String,
    val label: String
)

@Composable
private fun MemberListRow(
    member: Member,
    isPresentToday: Boolean,
    absenceCount: Int = 0,
    onClick: () -> Unit
) {
    val isInjured = member.isInjured
    val surface = MaterialTheme.colorScheme.surface
    val beltColor = member.belt.primaryColor

    // La carte reprend la couleur de la ceinture de l'athlete (meme principe que la
    // carte orange des blesses) : fond teinte + liseré de la couleur de ceinture.
    val beltTinted = androidx.compose.ui.graphics.lerp(surface, beltColor, 0.18f)

    // Les absences ne font PLUS assombrir la carte : chaque absence est signalée par
    // une ligne noire diagonale (rayures dessinées derrière le contenu, voir
    // drawAbsenceDiagonals). Le fond reste la teinte ceinture de l'athlete.
    val isSuspended = member.isSuspended
    val containerColor = when {
        isSuspended -> Color(0xFFFFEBEE)
        isInjured -> Color(0xFFFFF3E0)
        isPresentToday -> androidx.compose.ui.graphics.lerp(beltTinted, JudoSuccess, 0.14f)
        else -> beltTinted
    }
    // Contraste automatique : texte clair des que le fond devient sombre.
    val onContainer = when {
        isSuspended -> Color(0xFFB71C1C)
        isInjured -> Color(0xFF5D4037)
        containerColor.luminance() < 0.45f -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (isSuspended) {
            androidx.compose.foundation.BorderStroke(1.5.dp, JudoRed)
        } else if (isInjured) {
            androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFFB74D))
        } else {
            androidx.compose.foundation.BorderStroke(1.4.dp, beltColor.copy(alpha = 0.65f))
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("member_row_${member.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .then(
                    if (isInjured) {
                        Modifier.drawBehind {
                            drawInjuryDiagonals(
                                color = JudoRed.copy(alpha = 0.13f),
                                spacing = 28f,
                                stroke = 7f
                            )
                        }
                    } else {
                        Modifier
                    }
                )
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
                sizeDp = 58
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = member.fullName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = onContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isInjured) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFE65100).copy(alpha = 0.16f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MedicalServices,
                                    contentDescription = "Blessé",
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "Blessé",
                                    color = Color(0xFFE65100),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                    if (member.status == "SUSPENDU") {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = JudoRed
                        ) {
                            Text(
                                text = "Suspendu",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
                            tint = onContainer.copy(alpha = 0.65f),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = member.phone,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = onContainer.copy(alpha = 0.8f)
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

                if (absenceCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = JudoRed.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "$absenceCount abs.",
                            color = JudoRed,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Ouvrir la fiche",
                    tint = onContainer.copy(alpha = 0.45f),
                    modifier = Modifier.size(18.dp)
                )
            }
            }
        }
    }
}

@Composable
fun MemberDetailScreen(
    member: Member,
    attendanceList: List<Attendance>,
    allClubAttendances: List<AttendanceWithMember>,
    categories: List<JudoCategory> = emptyList(),
    scheduledSessions: List<com.example.data.model.ScheduledTrainingSession> = emptyList(),
    officialStartDate: String = "",
    weightEntries: List<com.example.data.model.WeightEntry> = emptyList(),
    onRecordWeight: (Float) -> Unit = {},
    onDeleteWeight: (Long) -> Unit = {},
    onBack: () -> Unit,
    onManualCheckIn: (Long) -> Unit,
    onRecordAttendanceForDate: (Long, String) -> Unit,
    onDeleteAttendanceForDate: (Long, String) -> Unit,
    onEditMember: (Member) -> Unit,
    onDeleteMember: (Member) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Nom officiel de la categorie de l'athlete (parametrage admin > categories).
    val categoryName = remember(categories, member) {
        categories.firstOrNull { it.covers(member.groupCode) }?.name.orEmpty()
    }

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
                    categoryName = it.categoryName,
                    cancelledDates = it.cancelledDates
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
            .testTag("member_detail_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // ===== En-tete thematique : degrade aux couleurs de la ceinture.
        // Place DANS la liste : il defile avec la fiche au lieu de rester fixe.
        item {
            MemberBeltHeader(
                member = member,
                categoryName = categoryName,
                totalAttendances = totalAttendances,
                attendanceRate = attendanceRate,
                onBack = onBack
            )
        }

        item {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
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
        }

        item {
            WeightSection(
                entries = weightEntries,
                onRecord = onRecordWeight,
                onDelete = onDeleteWeight
            )
        }

        item {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
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
}

@Composable
private fun WeightSection(
    entries: List<com.example.data.model.WeightEntry>,
    onRecord: (Float) -> Unit,
    onDelete: (Long) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("weight_section")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Poids",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Button(
                    onClick = { showDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = JudoRed, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("weight_add_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ajouter")
                }
            }

            val latest = entries.firstOrNull()
            if (latest != null) {
                Text(
                    text = "Dernier : ${latest.weightKg} kg (${latest.dateString})",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            } else {
                Text(
                    text = "Aucune mesure enregistrée.",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }

            if (entries.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    entries.forEach { e ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${e.dateString} — ${e.weightKg} kg", style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { onDelete(e.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = JudoRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false; input = "" },
            title = { Text("Nouvelle mesure de poids") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Date : ${java.time.LocalDate.now()}", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text("Poids (kg)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = input.replace(",", ".").toFloatOrNull()
                    if (v != null && v > 0f) {
                        onRecord(v)
                        showDialog = false
                        input = ""
                    }
                }) { Text("Enregistrer", color = JudoRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false; input = "" }) { Text("Annuler") }
            }
        )
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

/**
 * En-tete de la fiche athlete, meme style que la fiche coach, mais dont le
 * degrade_vertical prend les couleurs de la CEINTURE de l'athlete
 * (primaire -> secondaire pour les ceintures bicolores, sinon primaire assombri).
 */
@Composable
private fun MemberBeltHeader(
    member: Member,
    categoryName: String,
    totalAttendances: Int,
    attendanceRate: Int,
    onBack: () -> Unit
) {
    val beltColor = member.belt.primaryColor
    val beltEnd = member.belt.secondaryColor
        ?: androidx.compose.ui.graphics.lerp(beltColor, Color.Black, 0.45f)
    // Derniere bande du degrade : le bout de ceinture leve pour un effet tissu.
    val gradientEnd = androidx.compose.ui.graphics.lerp(beltEnd, Color.Black, 0.35f)
    // Texte lisible : sombre sur les ceintures claires (blanche, jaune...), blanc sinon.
    val onBelt = if (beltColor.luminance() > 0.55f) Color(0xFF371E1E) else Color.White

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(colors = listOf(beltColor, gradientEnd)))
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("member_detail_back_btn")) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = onBelt
                    )
                }
                Text(
                    text = "Fiche de l'athlète",
                    style = MaterialTheme.typography.titleMedium,
                    color = onBelt.copy(alpha = 0.85f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                // Grande photo de profil (150 dp) avec anneau de ceinture
                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .shadow(
                            elevation = 18.dp,
                            shape = CircleShape,
                            ambientColor = Color.Black.copy(alpha = 0.35f),
                            spotColor = Color.Black.copy(alpha = 0.35f)
                        )
                        .border(3.dp, onBelt.copy(alpha = 0.75f), CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(onBelt.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (member.photoUrl.isNotBlank()) {
                        AsyncImage(
                            model = member.photoUrl,
                            contentDescription = "Photo de ${member.fullName}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        val initials = member.fullName
                            .split(" ")
                            .filter { it.isNotBlank() }
                            .take(2)
                            .map { it.first().uppercase() }
                            .joinToString("")
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 44.sp,
                                color = onBelt
                            )
                        )
                    }
                }

                Text(
                    text = member.fullName,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.4).sp
                    ),
                    color = onBelt,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BeltBadge(belt = member.belt)
                    GroupBadge(group = member.group)
                    if (member.isSuspended) {
                        Surface(shape = RoundedCornerShape(50), color = JudoRed.copy(alpha = 0.85f)) {
                            Text("Suspendu", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }

                if (categoryName.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = onBelt.copy(alpha = 0.18f),
                        modifier = Modifier.testTag("member_category_badge")
                    ) {
                        Text(
                            text = categoryName,
                            color = onBelt,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = onBelt.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = when (member.status) {
                            "ACTIF" -> "Compte actif"
                            "BLESSE" -> "Blessé"
                            else -> "Compte suspendu"
                        },
                        color = onBelt,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                if (member.isInjured) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFFFE0B2),
                        modifier = Modifier.testTag("member_injury_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MedicalServices,
                                contentDescription = null,
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = when {
                                    member.injuryStartDate.isBlank() -> "Blessé — retour non prévu"
                                    member.injuryEndDate.isBlank() -> "Blessé depuis ${member.injuryStartDate}"
                                    else -> "Blessé ${member.injuryStartDate} → ${member.injuryEndDate}"
                                },
                                color = Color(0xFF8D4E00),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // Trio de statistiques en pastilles translucides
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MemberHeaderStat(
                    label = "Présences",
                    value = "$totalAttendances",
                    onBelt = onBelt,
                    modifier = Modifier.weight(1f)
                )
                MemberHeaderStat(
                    label = "Assiduité",
                    value = "$attendanceRate%",
                    onBelt = onBelt,
                    modifier = Modifier.weight(1f)
                )
                MemberHeaderStat(
                    label = "Groupe",
                    value = member.group.code,
                    onBelt = onBelt,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MemberHeaderStat(
    label: String,
    value: String,
    onBelt: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = onBelt.copy(alpha = 0.15f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    color = onBelt
                ),
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    color = onBelt.copy(alpha = 0.85f)
                ),
                maxLines = 1
            )
        }
    }
}

/**
 * Rayures diagonales legeres, dessinees derriere le contenu d'une carte.
 * Utilisees pour signaler visuellement un athlete blesse.
 */
private fun DrawScope.drawInjuryDiagonals(
    color: Color,
    spacing: Float,
    stroke: Float
) {
    if (size.height <= 0f || size.width <= 0f) return
    var x = -size.height
    while (x < size.width) {
        drawLine(
            color = color,
            start = Offset(x, size.height),
            end = Offset(x + size.height, 0f),
            strokeWidth = stroke
        )
        x += spacing
    }
}
