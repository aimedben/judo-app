package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AdminNotification
import com.example.data.model.AdminProfile
import com.example.data.model.AttendanceWithMember
import com.example.data.model.ClubSettings
import com.example.data.model.CoachUser
import com.example.data.model.JudoCategory
import com.example.data.model.Member
import com.example.data.model.PastTrainingSession
import com.example.ui.components.JudokaAvatar
import com.example.ui.theme.JudoGold
import com.example.ui.theme.JudoGoldDark
import com.example.ui.theme.JudoRed
import com.example.ui.theme.JudoSuccess
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminDashboardScreen(
    clubSettings: ClubSettings,
    adminProfile: AdminProfile,
    allMembers: List<Member>,
    todayAttendance: List<AttendanceWithMember>,
    allAttendance: List<AttendanceWithMember>,
    categories: List<JudoCategory>,
    coaches: List<CoachUser>,
    pastSessions: List<PastTrainingSession>,
    notifications: List<AdminNotification>,
    scheduledSessions: List<com.example.data.model.ScheduledTrainingSession> = emptyList(),
    isDarkTheme: Boolean,
    onNavigateToAthletes: () -> Unit,
    onNavigateToCoaches: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToAssignments: () -> Unit,
    onNavigateToSessions: () -> Unit,
    onNavigateToAttendanceOverview: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToExports: () -> Unit,
    onNavigateToSettings: () -> Unit,
    pendingSyncCount: Int = 0,
    onOpenCloudSync: () -> Unit = {},
    onOpenNotifications: () -> Unit,
    onOpenProfileDialog: () -> Unit,
    onSwitchToCoach: () -> Unit,
    onLogout: () -> Unit,
    onSelectMember: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var statPeriod by remember { mutableStateOf(StatPeriod.TODAY) }
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.FRENCH)
        val dateFormat = SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRENCH)
        while (true) {
            val now = Date()
            currentTime = timeFormat.format(now)
            currentDate = dateFormat.format(now).replaceFirstChar { it.uppercase() }
            delay(1000)
        }
    }

    // Calculations based on actual DB
    val activeAthletesCount = allMembers.count { it.status == "ACTIF" }
    val activeCoachesCount = coaches.count { it.isActive }
    val activeCategoriesCount = categories.count { it.isActive }
    val sessionsCompletedCount = pastSessions.size

    // Calcul du taux de presence CONSISTENT sur toutes les periodes :
    // presences uniques (membre x jour) / (jours de seance tenus x membres ACTIFS).
    // Corrige les 3 bugs precedents : doublons comptes plusieurs fois (TODAY),
    // semantique heterogene (distinct n'importe quand pour la saison),
    // et periodes hors saison officielle incluses.
    val (periodPresents, periodTotalExpected, attendanceRate) = remember(statPeriod, allAttendance, allMembers, clubSettings) {
        val officialStart = clubSettings.officialStartDate
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val periodRows = when (statPeriod) {
            StatPeriod.TODAY -> allAttendance.filter { it.dateString == todayStr }
            StatPeriod.WEEK -> {
                val cutoff = System.currentTimeMillis() - (7L * 24 * 3600 * 1000)
                allAttendance.filter { it.timestamp >= cutoff }
            }
            StatPeriod.MONTH -> {
                val cutoff = System.currentTimeMillis() - (30L * 24 * 3600 * 1000)
                allAttendance.filter { it.timestamp >= cutoff }
            }
            else -> allAttendance
        }.filter { com.example.util.OfficialPeriod.isOfficial(it.dateString, officialStart) }

        val activeAthletes = allMembers.count { it.status == "ACTIF" }
        val sessionsHeld = periodRows.map { it.dateString }.distinct().size
        val presentPairs = periodRows.map { "${it.memberId}|${it.dateString}" }.distinct().size
        val expected = sessionsHeld * activeAthletes
        val rate = if (expected > 0) {
            ((presentPairs.toDouble() / expected) * 100).toInt().coerceAtMost(100)
        } else 0
        Triple(presentPairs, expected, rate)
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Modern Header Bar
        AdminHeaderBar(
            clubSettings = clubSettings,
            adminProfile = adminProfile,
            notifications = notifications,
            isDarkTheme = isDarkTheme,
            pendingSyncCount = pendingSyncCount,
            absenceAlertCount = remember(allMembers, allAttendance, scheduledSessions, clubSettings) {
                val now = java.util.Calendar.getInstance().apply {
                    timeInMillis = System.currentTimeMillis()
                }
                com.example.util.AbsenceAlerts.membersWithMonthAbsences(
                    members = allMembers,
                    attendances = allAttendance,
                    scheduledSessions = scheduledSessions,
                    officialStartDate = clubSettings.officialStartDate,
                    todayMillis = now.timeInMillis,
                    year = now.get(java.util.Calendar.YEAR),
                    month0 = now.get(java.util.Calendar.MONTH),
                    threshold = 1
                ).size
            },
            onOpenNotifications = onOpenNotifications,
            onOpenSettings = onNavigateToSettings,
            onOpenCloudSync = onOpenCloudSync,
            onOpenProfileDialog = onOpenProfileDialog,
            onSwitchToCoach = onSwitchToCoach,
            onLogout = onLogout
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isTabletLandscape = maxWidth >= 720.dp
            val quickActionColumns = if (isTabletLandscape) 4 else 2
            // Gold is too light on white surfaces, so the light theme uses a darker amber.
            val goldAccent = if (isDarkTheme) JudoGold else JudoGoldDark
            val coachAccentColor = goldAccent

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("admin_dashboard_screen"),
                contentPadding = PaddingValues(
                    horizontal = if (isTabletLandscape) 28.dp else 16.dp,
                    vertical = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Live Date & Greeting Banner
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Bonjour, ${adminProfile.name.split(" ").getOrNull(1) ?: adminProfile.name}",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-0.5).sp
                                )
                            )
                            Text(
                                text = currentDate,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Text(
                                text = "🕒 $currentTime",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = JudoRed
                                )
                            )
                        }
                    }
                }

                // 4.A Indicateurs Principaux (Cartes statistiques calculées sur BDD réelle)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Indicateurs Principaux",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )

                            // Period selector
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item {
                                    FilterChip(
                                        selected = statPeriod == StatPeriod.TODAY,
                                        onClick = { statPeriod = StatPeriod.TODAY },
                                        label = { Text("Aujourd'hui", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = JudoRed,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = statPeriod == StatPeriod.WEEK,
                                        onClick = { statPeriod = StatPeriod.WEEK },
                                        label = { Text("Semaine", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = JudoRed,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = statPeriod == StatPeriod.MONTH,
                                        onClick = { statPeriod = StatPeriod.MONTH },
                                        label = { Text("Mois", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = JudoRed,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }

                        // Stat Cards Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AdminStatCard(
                                title = "Athlètes actifs",
                                value = "$activeAthletesCount",
                                subtitle = "sur ${allMembers.size} inscrits",
                                accentColor = Color(0xFF2196F3),
                                icon = Icons.Default.People,
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToAthletes
                            )

                            AdminStatCard(
                                title = "Coachs actifs",
                                value = "$activeCoachesCount",
                                subtitle = "sur ${coaches.size} au club",
                                accentColor = coachAccentColor,
                                icon = Icons.Default.SportsMartialArts,
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToCoaches
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AdminStatCard(
                                title = "Catégories",
                                value = "$activeCategoriesCount",
                                subtitle = "groupes d'âge",
                                accentColor = Color(0xFF9C27B0),
                                icon = Icons.Default.Category,
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToCategories
                            )

                            AdminStatCard(
                                title = "Séances faites",
                                value = "$sessionsCompletedCount",
                                subtitle = "historique dojo",
                                accentColor = Color(0xFFFF9800),
                                icon = Icons.Default.EventNote,
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToSessions
                            )

                            AdminStatCard(
                                title = "Taux Présence",
                                value = "$attendanceRate%",
                                subtitle = "$periodPresents/$periodTotalExpected",
                                accentColor = JudoSuccess,
                                icon = Icons.Default.FactCheck,
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToAttendanceOverview
                            )
                        }
                    }
                }

                // 4.B Accès Rapide (Grille de boutons cliquables)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Accès Rapide",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        // 10 Quick Access Tiles
                        val quickActions = listOf(
                            QuickActionItem("Gérer les athlètes", "Fiches, ceintures & statuts", Icons.Default.People, Color(0xFF2196F3), onNavigateToAthletes),
                            QuickActionItem("Gérer les coachs", "Profils, grades & présences", Icons.Default.SportsMartialArts, coachAccentColor, onNavigateToCoaches),
                            QuickActionItem("Gérer catégories", "Groupes d'âge & effectifs", Icons.Default.Category, Color(0xFF9C27B0), onNavigateToCategories),
                            QuickActionItem("Affectations", "Coachs référents par groupe", Icons.Default.AssignmentInd, Color(0xFF009688), onNavigateToAssignments),
                            QuickActionItem("Gérer les séances", "Récapitulatifs & validations", Icons.Default.EventNote, JudoRed, onNavigateToSessions),
                            QuickActionItem("Présences & absences", "Analyse croisée & pointages", Icons.Default.FactCheck, JudoSuccess, onNavigateToAttendanceOverview),
                            QuickActionItem("Consulter historique", "Journal complet du dojo", Icons.Default.History, Color(0xFF607D8B), onNavigateToHistory),
                            QuickActionItem("Ouvrir statistiques", "Graphiques & assiduité", Icons.Default.BarChart, Color(0xFFE91E63), onNavigateToStats),
                            QuickActionItem("Accéder aux exports", "Rapports CSV & bilans", Icons.Default.FileDownload, Color(0xFF795548), onNavigateToExports),
                            QuickActionItem("Paramètres du club", "Dojo, infos & thème", Icons.Default.Settings, Color(0xFF455A64), onNavigateToSettings),
                            QuickActionItem("Supabase Cloud", "Synchronisation & Sauvegardes", Icons.Default.CloudSync, goldAccent, onOpenCloudSync)
                        )

                        // Adaptive grid chunking based on available width
                        for (i in quickActions.indices step quickActionColumns) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                for (col in 0 until quickActionColumns) {
                                    val index = i + col
                                    if (index < quickActions.size) {
                                        QuickActionCard(
                                            item = quickActions[index],
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

            // 4.C Activité Récente (Événements réels : Dernières séances & Nouveaux inscrits)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Activité Récente",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    // Recent sessions
                    if (pastSessions.isNotEmpty()) {
                        Text(
                            text = "Dernières séances terminées :",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        pastSessions.take(2).forEach { session ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = CardDefaults.outlinedCardBorder(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToSessions() }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(JudoRed.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.EventNote,
                                                contentDescription = null,
                                                tint = JudoRed,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        Column {
                                            Text(
                                                text = "Séance ${session.categoryName}",
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = "${session.dateString} • ${session.startTime} - ${session.endTime}",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            )
                                            Text(
                                                text = "Coach : ${session.coach1Name}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = goldAccent,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            )
                                            if (session.presentMemberNames.isNotEmpty()) {
                                                Text(
                                                    text = "Présents : " + session.presentMemberNames.joinToString(", "),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    ),
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = JudoSuccess.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "${session.effectivePresentCount} présents",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = JudoSuccess,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${session.effectiveAbsentCount} absents",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Newly registered athletes : les PLUS RECENTS uniquement
                    // (date d'inscription, puis id d'ajout le plus grand).
                    val recentAddedMembers = remember(allMembers) {
                        allMembers
                            .sortedWith(
                                compareByDescending<Member> { it.registrationDate }
                                    .thenByDescending { it.id }
                            )
                            .take(3)
                    }
                    if (recentAddedMembers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Nouveaux athlètes enregistrés :",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        recentAddedMembers.forEach { member ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = CardDefaults.outlinedCardBorder(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectMember(member.id) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        JudokaAvatar(
                                            name = member.fullName,
                                            belt = member.belt,
                                            photoUrl = member.photoUrl,
                                            sizeDp = 40
                                        )

                                        Column {
                                            Text(
                                                text = member.fullName,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = "${member.group.label} • Ceinture ${member.belt.displayName}",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Voir",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (pastSessions.isEmpty() && allMembers.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Aucune activité enregistrée récemment.",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
}

private data class QuickActionItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
private fun QuickActionCard(
    item: QuickActionItem,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = modifier
            .clickable { item.onClick() }
            .testTag("quick_action_${item.title.lowercase().replace(" ", "_")}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(item.color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = item.color,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    maxLines = 1
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun AdminStatCard(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    color = accentColor
                )
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                ),
                maxLines = 1
            )
        }
    }
}
