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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.data.model.AttendanceWithMember
import com.example.data.model.Member
import com.example.ui.components.BeltBadge
import com.example.ui.components.GroupBadge
import com.example.ui.components.JudokaAvatar
import com.example.ui.theme.JudoGold
import com.example.ui.theme.JudoRed
import com.example.ui.theme.JudoSuccess

private fun attendancesForPeriod(
    period: StatPeriod,
    todayAttendance: List<AttendanceWithMember>,
    allAttendance: List<AttendanceWithMember>
): List<AttendanceWithMember> {
    val now = System.currentTimeMillis()
    val dayMs = 24L * 3600 * 1000L
    return when (period) {
        StatPeriod.TODAY -> todayAttendance
        StatPeriod.WEEK -> allAttendance.filter { it.timestamp >= now - 7L * dayMs }
        StatPeriod.MONTH -> allAttendance.filter { it.timestamp >= now - 30L * dayMs }
        StatPeriod.TWO_MONTHS -> allAttendance.filter { it.timestamp >= now - 60L * dayMs }
        StatPeriod.SEASON -> allAttendance
    }
}

@Composable
fun DashboardScreen(
    members: List<Member>,
    todayAttendance: List<AttendanceWithMember>,
    allAttendance: List<AttendanceWithMember>,
    selectedPeriod: StatPeriod,
    onPeriodChange: (StatPeriod) -> Unit,
    onSelectMember: (Long) -> Unit,
    onStatClick: (StatFilterMode, StatPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    val periodAttendances = remember(selectedPeriod, todayAttendance, allAttendance) {
        attendancesForPeriod(selectedPeriod, todayAttendance, allAttendance)
    }

    val presentMemberIds = remember(periodAttendances) {
        periodAttendances.map { it.memberId }.toSet()
    }

    val enrolledCount = members.size
    val presentCount = presentMemberIds.size
    val absentCount = (enrolledCount - presentCount).coerceAtLeast(0)
    val attendanceRate = if (enrolledCount > 0) {
        ((presentCount.toDouble() / enrolledCount.toDouble()) * 100).toInt().coerceIn(0, 100)
    } else 0

    val ranking = remember(members, periodAttendances) {
        val countByMember = periodAttendances.groupBy { it.memberId }.mapValues { it.value.size }
        members
            .map { it to (countByMember[it.id] ?: 0) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(15)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Statistiques du club",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black)
                )
                Text(
                    text = "Effectifs, présences et assiduité • ${selectedPeriod.description}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StatPeriod.entries.toTypedArray()) { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { onPeriodChange(period) },
                        label = { Text(period.title) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = JudoRed,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DashboardStatCard(
                    title = "Présents",
                    value = "$presentCount",
                    subtitle = selectedPeriod.shortLabel,
                    accent = JudoSuccess,
                    onClick = { onStatClick(StatFilterMode.PRESENTS, selectedPeriod) },
                    modifier = Modifier.weight(1f)
                )
                DashboardStatCard(
                    title = "Absents",
                    value = "$absentCount",
                    subtitle = selectedPeriod.shortLabel,
                    accent = JudoRed,
                    onClick = { onStatClick(StatFilterMode.ABSENTS, selectedPeriod) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DashboardStatCard(
                    title = "Inscrits",
                    value = "$enrolledCount",
                    subtitle = "Effectif global",
                    accent = JudoGold,
                    onClick = { onStatClick(StatFilterMode.ALL_MEMBERS, selectedPeriod) },
                    modifier = Modifier.weight(1f)
                )
                DashboardStatCard(
                    title = "Assiduité",
                    value = "$attendanceRate%",
                    subtitle = selectedPeriod.shortLabel,
                    accent = if (attendanceRate >= 75) JudoSuccess else JudoGold,
                    onClick = { onStatClick(StatFilterMode.ATTENDANCE_RATE, selectedPeriod) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = JudoGold,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Top assiduité (${selectedPeriod.shortLabel})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        if (ranking.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aucune présence enregistrée sur cette période.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        } else {
            itemsIndexed(ranking, key = { _, item -> item.first.id }) { index, item ->
                val member = item.first
                val count = item.second
                val rate = if (enrolledCount > 0) ((count.toDouble() / count.coerceAtLeast(count).toDouble()) * 100).toInt() else 0

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RankBadge(rank = index + 1)
                        JudokaAvatar(
                            name = member.fullName,
                            belt = member.belt,
                            photoUrl = member.photoUrl,
                            sizeDp = 42
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = member.fullName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                BeltBadge(belt = member.belt)
                                GroupBadge(group = member.group)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    color = JudoRed
                                )
                            )
                            Text(
                                text = "présences",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardStatCard(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    color = accent
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            )
        }
    }
}

@Composable
private fun RankBadge(rank: Int) {
    val (bg, fg) = when (rank) {
        1 -> JudoGold to Color(0xFF1E2024)
        2 -> Color(0xFFB0BEC5) to Color(0xFF1E2024)
        3 -> Color(0xFFCD7F32) to Color.White
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black, color = fg)
        )
    }
}

@Composable
fun StatFilteredMembersScreen(
    filterMode: StatFilterMode,
    statPeriod: StatPeriod,
    onPeriodChange: (StatPeriod) -> Unit,
    onFilterModeChange: (StatFilterMode) -> Unit,
    allMembers: List<Member>,
    todayAttendance: List<AttendanceWithMember>,
    allAttendance: List<AttendanceWithMember>,
    onSelectMember: (Long) -> Unit,
    onManualCheckIn: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val periodAttendances = remember(statPeriod, todayAttendance, allAttendance) {
        attendancesForPeriod(statPeriod, todayAttendance, allAttendance)
    }

    val presentMemberIds = remember(periodAttendances) {
        periodAttendances.map { it.memberId }.toSet()
    }

    val countByMember = remember(periodAttendances) {
        periodAttendances.groupBy { it.memberId }.mapValues { it.value.size }
    }

    val displayedMembers = remember(filterMode, allMembers, presentMemberIds, countByMember) {
        when (filterMode) {
            StatFilterMode.ALL_MEMBERS -> allMembers.sortedBy { it.fullName.lowercase() }
            StatFilterMode.PRESENTS -> allMembers
                .filter { presentMemberIds.contains(it.id) }
                .sortedByDescending { countByMember[it.id] ?: 0 }
            StatFilterMode.ABSENTS -> allMembers
                .filter { !presentMemberIds.contains(it.id) }
                .sortedBy { it.fullName.lowercase() }
            StatFilterMode.ATTENDANCE_RATE -> allMembers
                .sortedByDescending { countByMember[it.id] ?: 0 }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("stat_filtered_members_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("stat_filter_back_btn")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                }
                Column {
                    Text(
                        text = filterMode.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                    )
                    Text(
                        text = "${displayedMembers.size} judoka(s) • ${statPeriod.frenchLabel}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StatFilterMode.entries.toTypedArray()) { mode ->
                    FilterChip(
                        selected = filterMode == mode,
                        onClick = { onFilterModeChange(mode) },
                        label = { Text(mode.title) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = mode.accentColor,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StatPeriod.entries.toTypedArray()) { period ->
                    FilterChip(
                        selected = statPeriod == period,
                        onClick = { onPeriodChange(period) },
                        label = { Text(period.title) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = JudoRed,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        if (displayedMembers.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aucun athlète dans cette catégorie pour la période choisie.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        } else {
            items(displayedMembers, key = { it.id }) { member ->
                val count = countByMember[member.id] ?: 0
                val isPresent = presentMemberIds.contains(member.id)

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        JudokaAvatar(
                            name = member.fullName,
                            belt = member.belt,
                            photoUrl = member.photoUrl,
                            sizeDp = 44
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            RownameWithStatus(name = member.fullName, isPresent = isPresent)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                BeltBadge(belt = member.belt)
                                GroupBadge(group = member.group)
                            }
                        }

                        if (filterMode == StatFilterMode.ABSENTS && !isPresent) {
                            Button(
                                onClick = { onManualCheckIn(member.id) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = JudoSuccess,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pointer", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "$count",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        color = if (count > 0) JudoSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Text(
                                    text = "séance(s)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RownameWithStatus(name: String, isPresent: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 1
        )
        if (isPresent) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Présent",
                tint = JudoSuccess,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}
