package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.ui.components.CancelledSessionDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AdminNotification
import com.example.data.model.AdminProfile
import com.example.data.model.AttendanceWithMember
import com.example.data.model.ClubSettings
import com.example.data.model.CoachUser
import com.example.data.model.JudoCategory
import com.example.data.model.Member
import com.example.data.model.PastTrainingSession
import com.example.data.model.ScheduledTrainingSession
import com.example.data.model.SessionStatus
import com.example.ui.components.JudokaAvatar
import com.example.ui.theme.JudoGold
import com.example.ui.theme.JudoGoldDark
import com.example.ui.theme.JudoRed
import com.example.ui.theme.JudoRedDark
import com.example.ui.theme.JudoSuccess
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HeroGradientStart = Color(0xFF7E0B0E)
private val HeroGradientMid = Color(0xFFC0151B)
private val HeroGradientEnd = Color(0xFF5E0709)

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
    scheduledSessions: List<ScheduledTrainingSession> = emptyList(),
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
    onOpenSmsCenter: () -> Unit = {},
    onOpenProfileDialog: () -> Unit,
    onCancelScheduledSession: (String, String) -> Unit = { _, _ -> },
    onRestoreScheduledSession: (String, String) -> Unit = { _, _ -> },
    cancelledSessionDays: List<com.example.util.CancelledSessionDays.Day> = emptyList(),
    onSwitchToCoach: () -> Unit,
    onLogout: () -> Unit,
    onSelectMember: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var statPeriod by remember { mutableStateOf(StatPeriod.MONTH) }
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

    // --- Chiffres cles calcules sur la base reelle ---
    val activeAthletesCount = allMembers.count { it.status == "ACTIF" }
    val activeCoachesCount = coaches.count { it.isActive }
    val activeCategoriesCount = categories.count { it.isActive }
    val sessionsCompletedCount = pastSessions.size
    val injuredCount = allMembers.count { it.status == "ACTIF" && it.isInjured }

    // Taux de presence : (membre x jour uniques) / (jours de seance x athletes actifs).
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

    val absenceAlertCount = remember(allMembers, allAttendance, scheduledSessions, clubSettings) {
        val now = java.util.Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() }
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
    }

    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    // Jour de semaine en français (Samedi, Dimanche…) : les créneaux hebdomadaires sont
    // identifiés par dayOfWeekName — leur `dateString` reste la première semaine où le
    // créneau a été créé, elle est souvent déjà passée.
    val todayDayName = remember(todayStr) {
        when (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.SUNDAY -> "Dimanche"
            java.util.Calendar.MONDAY -> "Lundi"
            java.util.Calendar.TUESDAY -> "Mardi"
            java.util.Calendar.WEDNESDAY -> "Mercredi"
            java.util.Calendar.THURSDAY -> "Jeudi"
            java.util.Calendar.FRIDAY -> "Vendredi"
            else -> "Samedi"
        }
    }
    // Séances du jour = créneaux programmés POUR CE JOUR DE SEMAINE (pas la date figée).
    val todaySessions = remember(scheduledSessions, todayDayName) {
        scheduledSessions
            .filter { it.dayOfWeekName.equals(todayDayName, ignoreCase = true) }
            .filter { it.status != SessionStatus.CANCELLED }
            .sortedBy { it.startTime }
    }
    val upcomingSessions = remember(scheduledSessions, todayStr) {
        scheduledSessions
            .filter { it.status != SessionStatus.CANCELLED && it.dateString >= todayStr }
            .sortedWith(compareBy({ it.dateString }, { it.startTime }))
            .take(6)
    }
    // Cartes du carrousel « Prochaines séances » : celles du JOUR (toujours présentes,
    // donc annulables) puis les suivantes, sans doublon.
    // -> (séance, est-ce-celle-du-jour, date à annuler si besoin)
    val dashScheduleCards = remember(todaySessions, upcomingSessions, todayStr) {
        val merged = mutableListOf<Pair<ScheduledTrainingSession, Boolean>>()
        todaySessions.forEach { merged.add(it to true) }
        upcomingSessions.forEach { s ->
            if (merged.none { it.first.id == s.id }) merged.add(s to false)
        }
        merged.map { (s, isToday) ->
            val cancelDate = if (isToday) todayStr else maxOf(s.dateString, todayStr)
            Triple(s, isToday, cancelDate)
        }
    }

    // Fonctionnalité « séance annulée » : l'admin choisit la DATE et la CATÉGORIE de la
    // séance qui n'a pas eu lieu ; les athlètes de cette catégorie ne compteront PAS
    // absents ce jour-là (journal partagé via le cloud avec les autres tablettes).
    var showCancelSessionDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AdminHeaderBar(
            clubSettings = clubSettings,
            adminProfile = adminProfile,
            notifications = notifications,
            isDarkTheme = isDarkTheme,
            pendingSyncCount = pendingSyncCount,
            absenceAlertCount = absenceAlertCount,
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
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 1. Banniere hero : salutation, horloge, anneau de presence, mini-stats
                item {
                    RevealSection(index = 0) {
                        HeroBanner(
                            adminName = adminProfile.name,
                            clubName = clubSettings.clubName.ifBlank { "Judo Club Seddouk" },
                            currentDate = currentDate,
                            currentTime = currentTime,
                            attendanceRate = attendanceRate,
                            periodLabel = statPeriod.frenchLabel,
                            athletes = activeAthletesCount,
                            coaches = activeCoachesCount,
                            sessions = sessionsCompletedCount,
                            injuredCount = injuredCount,
                            todaySessionsCount = todaySessions.count { todayStr !in it.cancelledDates },
                            absenceAlertCount = absenceAlertCount,
                            onOpenNotifications = onOpenNotifications,
                            onOpenProfile = onOpenProfileDialog
                        )
                    }
                }

                // 2. Periode + indicateurs
                item {
                    RevealSection(index = 1) {
                        SectionTitle(
                            title = "Vue d'ensemble",
                            subtitle = "$periodPresents présences / $periodTotalExpected attendues"
                        )
                        PeriodSegmentedControl(
                            selected = statPeriod,
                            onSelect = { statPeriod = it }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            KpiCard(
                                title = "Athlètes actifs",
                                value = "$activeAthletesCount",
                                subtitle = "sur ${allMembers.size} inscrits",
                                icon = Icons.Default.People,
                                accent = Color(0xFF2F6FED),
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToAthletes
                            )
                            KpiCard(
                                title = "Taux présence",
                                value = "$attendanceRate%",
                                subtitle = statPeriod.frenchLabel.lowercase(),
                                icon = Icons.Default.TrendingUp,
                                accent = JudoSuccess,
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToAttendanceOverview
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            KpiCard(
                                title = "Coachs actifs",
                                value = "$activeCoachesCount",
                                subtitle = "sur ${coaches.size} au club",
                                icon = Icons.Default.SportsMartialArts,
                                accent = coachAccentColor,
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToCoaches
                            )
                            KpiCard(
                                title = "Séances faites",
                                value = "$sessionsCompletedCount",
                                subtitle = if (todaySessions.isNotEmpty()) "${todaySessions.count { todayStr !in it.cancelledDates }} aujourd'hui" else "historique dojo",
                                icon = Icons.Default.EventNote,
                                accent = Color(0xFFB0521C),
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToSessions
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            KpiCard(
                                title = "Catégories",
                                value = "$activeCategoriesCount",
                                subtitle = "groupes d'âge",
                                icon = Icons.Default.Category,
                                accent = Color(0xFF7C3AED),
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToCategories
                            )
                            KpiCard(
                                title = "Blessés",
                                value = "$injuredCount",
                                subtitle = "athlètes indisponibles",
                                icon = Icons.Default.Warning,
                                accent = Color(0xFFD97706),
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToAthletes
                            )
                        }
                    }
                }

                // 3. Seances a venir + fonctionnalite « seance annulee » (date + categorie)
                item {
                    RevealSection(index = 2) {
                        SectionTitle(
                            title = "Séances",
                            subtitle = if (dashScheduleCards.isEmpty()) "Aucune séance programmée"
                            else "${dashScheduleCards.count { (s, _, d) -> d !in s.cancelledDates }} prévues",
                            actionLabel = "Séance annulée…",
                            onAction = { showCancelSessionDialog = true }
                        )
                        if (dashScheduleCards.isEmpty()) {
                            EmptyHintCard(
                                icon = Icons.Default.Schedule,
                                text = "Programmez les séances du dojo pour les voir ici.",
                                onClick = onNavigateToSessions
                            )
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(dashScheduleCards, key = { it.first.id }) { (session, isToday, cancelDate) ->
                                    val isCancelled = cancelDate in session.cancelledDates
                                    UpcomingSessionCard(
                                        session = session,
                                        isToday = isToday,
                                        isCancelled = isCancelled,
                                        displayDate = cancelDate,
                                        onClick = onNavigateToSessions
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Acces rapide : 3 tuiles vedettes puis la grille complete
                item {
                    RevealSection(index = 3) {
                        SectionTitle(title = "Accès rapide", subtitle = "Tout le club à portée de main")

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FeatureTile(
                                title = "SMS aux parents",
                                subtitle = "Relances & infos",
                                icon = Icons.Default.Sms,
                                startColor = Color(0xFF1B5E20),
                                endColor = Color(0xFF43A047),
                                onClick = onOpenSmsCenter,
                                modifier = Modifier.weight(1f)
                            )
                            FeatureTile(
                                title = "Présences",
                                subtitle = "Pointages du jour",
                                icon = Icons.Default.FactCheck,
                                startColor = HeroGradientMid,
                                endColor = Color(0xFFE53935),
                                onClick = onNavigateToAttendanceOverview,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        val quickActions = listOf(
                            QuickActionItem("SMS", "Messages aux parents & coût", Icons.Default.Sms, Color(0xFF2E7D32), onOpenSmsCenter),
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

                        for (i in quickActions.indices step quickActionColumns) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
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

                // 5. Dernieres seances terminees
                item {
                    RevealSection(index = 4) {
                        SectionTitle(
                            title = "Dernières séances",
                            subtitle = if (pastSessions.isEmpty()) "L'historique se remplira après les cours"
                            else "Bilan des ${minOf(pastSessions.size, 3)} plus récentes",
                            actionLabel = if (pastSessions.isEmpty()) null else "Historique",
                            onAction = onNavigateToHistory
                        )
                        if (pastSessions.isEmpty()) {
                            EmptyHintCard(
                                icon = Icons.Default.EventNote,
                                text = "Aucune séance terminée pour l'instant.",
                                onClick = onNavigateToSessions
                            )
                        } else {
                            pastSessions.take(3).forEach { session ->
                                RecentSessionCard(
                                    session = session,
                                    goldAccent = goldAccent,
                                    onClick = onNavigateToSessions
                                )
                            }
                        }
                    }
                }

                // 6. Nouveaux athletes
                item {
                    val recentAddedMembers = remember(allMembers) {
                        allMembers
                            .sortedWith(
                                compareByDescending<Member> { it.registrationDate }
                                    .thenByDescending { it.id }
                            )
                            .take(3)
                    }
                    if (recentAddedMembers.isNotEmpty()) {
                        RevealSection(index = 5) {
                            SectionTitle(
                                title = "Nouveaux athlètes",
                                subtitle = "Dernières inscriptions au club",
                                actionLabel = "Tous",
                                onAction = onNavigateToAthletes
                            )
                            recentAddedMembers.forEach { member ->
                                NewMemberRow(member = member, onClick = { onSelectMember(member.id) })
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }

    if (showCancelSessionDialog) {
        CancelledSessionDialog(
            categories = categories,
            cancelledSessionDays = cancelledSessionDays,
            todayStr = todayStr,
            onCancelDay = { groupCode, date ->
                onCancelScheduledSession(groupCode, date)
                showCancelSessionDialog = false
            },
            onRestoreDay = { groupCode, date ->
                onRestoreScheduledSession(groupCode, date)
                showCancelSessionDialog = false
            },
            onDismiss = { showCancelSessionDialog = false }
        )
    }
}


/* ------------------------------------------------------------------ */
/*  Banniere hero                                                      */
/* ------------------------------------------------------------------ */

@Composable
private fun HeroBanner(
    adminName: String,
    clubName: String,
    currentDate: String,
    currentTime: String,
    attendanceRate: Int,
    periodLabel: String,
    athletes: Int,
    coaches: Int,
    sessions: Int,
    injuredCount: Int,
    todaySessionsCount: Int,
    absenceAlertCount: Int,
    onOpenNotifications: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 14.dp, shape = shape, ambientColor = HeroGradientMid, spotColor = HeroGradientStart)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(HeroGradientStart, HeroGradientMid, HeroGradientEnd)
                )
            )
    ) {
        // Reflet diagonal + halo dore
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent,
                            JudoGold.copy(alpha = 0.10f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.16f),
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .size(52.dp)
                        .clickable { onOpenProfile() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = initialsOf(adminName),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Bonjour, ${adminName.split(" ").getOrNull(1) ?: adminName}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = (-0.4).sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = clubName.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.72f),
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.4.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$currentDate • $currentTime",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.82f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                AttendanceRing(rate = attendanceRate)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeroPill(
                    icon = Icons.Default.People,
                    value = "$athletes",
                    label = "Athlètes",
                    modifier = Modifier.weight(1f)
                )
                HeroPill(
                    icon = Icons.Default.SportsMartialArts,
                    value = "$coaches",
                    label = "Coachs",
                    modifier = Modifier.weight(1f)
                )
                HeroPill(
                    icon = Icons.Default.EventNote,
                    value = "$sessions",
                    label = "Séances",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeroTag(
                    icon = Icons.Default.Schedule,
                    text = if (todaySessionsCount > 0) "$todaySessionsCount cours aujourd'hui" else "Pas de cours aujourd'hui",
                    highlight = todaySessionsCount > 0
                )
                if (injuredCount > 0) {
                    HeroTag(
                        icon = Icons.Default.Warning,
                        text = "$injuredCount blessé${if (injuredCount > 1) "s" else ""}",
                        highlight = false
                    )
                }
                if (absenceAlertCount > 0) {
                    HeroTag(
                        icon = Icons.Default.Warning,
                        text = "$absenceAlertCount à relancer",
                        highlight = true,
                        onClick = onOpenNotifications
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroPill(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.13f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(16.dp)
        )
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    color = Color.White
                ),
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 9.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HeroTag(
    icon: ImageVector,
    text: String,
    highlight: Boolean,
    onClick: (() -> Unit)? = null
) {
    val bg = if (highlight) JudoGold else Color.White.copy(alpha = 0.12f)
    val fg = if (highlight) Color(0xFF2B1A00) else Color.White.copy(alpha = 0.9f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                color = fg,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun AttendanceRing(rate: Int) {
    val progress = rate.coerceIn(0, 100) / 100f
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(92.dp)) {
            val strokeWidth = 9.dp.toPx()
            val inset = strokeWidth / 2
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            drawArc(
                color = Color.White.copy(alpha = 0.20f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = JudoGold,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$rate%",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontSize = 22.sp
                )
            )
            Text(
                text = "présence",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 9.sp
                )
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Titres de section + controle de periode                            */
/* ------------------------------------------------------------------ */

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.2).sp
                )
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onAction() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = JudoRed,
                        fontWeight = FontWeight.Bold
                    )
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = JudoRed,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun PeriodSegmentedControl(
    selected: StatPeriod,
    onSelect: (StatPeriod) -> Unit
) {
    val periods = listOf(StatPeriod.TODAY, StatPeriod.WEEK, StatPeriod.MONTH)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        periods.forEach { period ->
            val isSelected = period == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) JudoRed else Color.Transparent)
                    .clickable { onSelect(period) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = period.frenchLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Indicateurs                                                        */
/* ------------------------------------------------------------------ */

@Composable
private fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminanceSafe() < 0.5f
    val container = lerp(MaterialTheme.colorScheme.surface, accent, if (isDark) 0.16f else 0.07f)
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(accent, accent.copy(alpha = 0.78f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.size(15.dp)
                )
            }

            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFeatureSettings = "tnum"
                ),
                maxLines = 1
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontSize = 10.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Acces rapide                                                       */
/* ------------------------------------------------------------------ */

private data class QuickActionItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
private fun FeatureTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    startColor: Color,
    endColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .shadow(elevation = 8.dp, shape = shape, ambientColor = startColor, spotColor = startColor)
            .clip(shape)
            .background(Brush.linearGradient(listOf(startColor, endColor)))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(21.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Black,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 10.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    item: QuickActionItem,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .clickable { item.onClick() }
            .testTag("quick_action_${item.title.lowercase().replace(" ", "_")}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(item.color.copy(alpha = 0.13f)),
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
                        fontSize = 12.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Seances                                                            */
/* ------------------------------------------------------------------ */

private val FRENCH_MONTHS_SHORT = listOf(
    "janv.", "févr.", "mars", "avr.", "mai", "juin",
    "juil.", "août", "sept.", "oct.", "nov.", "déc."
)

@Composable
private fun UpcomingSessionCard(
    session: ScheduledTrainingSession,
    isToday: Boolean,
    isCancelled: Boolean = false,
    displayDate: String = session.dateString,
    onClick: () -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val parts = displayDate.split("-")
    val day = parts.getOrNull(2)?.trimStart('0')?.ifBlank { "0" } ?: "—"
    val monthIndex = parts.getOrNull(1)?.toIntOrNull()?.minus(1) ?: 0
    val month = FRENCH_MONTHS_SHORT.getOrNull(monthIndex) ?: ""

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCancelled) {
                // Séance annulée : carte grise désaturée pour signifier « n'a pas lieu ».
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            1.dp,
            when {
                isCancelled -> JudoSuccess.copy(alpha = 0.45f)
                isToday -> JudoRed.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .width(196.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(JudoRed.copy(alpha = 0.10f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = day,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = JudoRed
                        )
                    )
                    Text(
                        text = month,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = JudoRed.copy(alpha = 0.85f),
                            fontSize = 9.sp
                        )
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.categoryName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${session.startTime} - ${session.endTime}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }

            if (session.coachNames.isNotEmpty()) {
                Text(
                    text = session.coachNames.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Pill(
                    text = if (isCancelled) "Annulée" else if (isToday) "Aujourd'hui" else session.dayOfWeekName,
                    color = when {
                        isCancelled -> JudoSuccess
                        isToday -> JudoRed
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = "${session.durationMinutes} min",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun RecentSessionCard(
    session: PastTrainingSession,
    goldAccent: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(Brush.verticalGradient(listOf(JudoRed, goldAccent)))
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(JudoRed.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.EventNote,
                        contentDescription = null,
                        tint = JudoRed,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Séance ${session.categoryName}",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${session.dateString} • ${session.startTime} - ${session.endTime}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Coach : ${session.coach1Name}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = goldAccent,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Pill(
                        text = "${session.effectivePresentCount} présents",
                        color = JudoSuccess,
                        filled = true
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${session.effectiveAbsentCount} absents",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Nouveaux athletes                                                  */
/* ------------------------------------------------------------------ */

@Composable
private fun NewMemberRow(
    member: Member,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                JudokaAvatar(
                    name = member.fullName,
                    belt = member.belt,
                    photoUrl = member.photoUrl,
                    sizeDp = 42
                )

                Column {
                    Text(
                        text = member.fullName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${member.group.label} • Ceinture ${member.belt.displayName}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Voir",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Petits composants partages                                         */
/* ------------------------------------------------------------------ */

@Composable
private fun Pill(
    text: String,
    color: Color,
    filled: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (filled) color.copy(alpha = 0.14f) else color.copy(alpha = 0.08f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun EmptyHintCard(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** Section qui apparait en fondu + glissement, avec un leger decalage par index. */
@Composable
private fun RevealSection(
    index: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 45L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(320)) +
            slideInVertically(animationSpec = tween(320)) { height -> height / 8 }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

private fun initialsOf(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "JC"
        parts.size == 1 -> parts.first().take(1).uppercase()
        else -> (parts.first().take(1) + parts[1].take(1)).uppercase()
    }
}

private fun Color.luminanceSafe(): Float = luminance()
