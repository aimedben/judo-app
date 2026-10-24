package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.CoachUser
import com.example.data.model.JudoCategory
import com.example.data.model.Member
import com.example.data.model.PastTrainingSession
import com.example.ui.theme.JudoGold
import com.example.ui.theme.JudoRed
import com.example.util.CoachNameKey
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Fiche coach detaillee : photo, grade, telephone, categories encadrees,
 * VRAI nombre de seances animees (historique) et seances recentes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachDetailScreen(
    coach: CoachUser,
    categories: List<JudoCategory>,
    pastSessions: List<PastTrainingSession>,
    members: List<Member> = emptyList(),
    onBack: () -> Unit
) {
    val sessionsLed = remember(coach, pastSessions) {
        pastSessions
            .filter { session ->
                (session.coach1Present && CoachNameKey.same(session.coach1Name, coach.name)) ||
                    (session.coach2Present && CoachNameKey.same(session.coach2Name, coach.name))
            }
            .sortedWith(compareByDescending { it.dateString })
    }
    val totalPresent = sessionsLed.sumOf { it.effectivePresentCount }
    val totalAbsent = sessionsLed.sumOf { it.effectiveAbsentCount }
    val assignedCategories = remember(coach, categories) {
        categories.filter { category ->
            coach.assignedCategories.any { assigned ->
                assigned.equals(category.name, ignoreCase = true) ||
                    category.covers(assigned)
            }
        }
    }
    val followedMembers = remember(assignedCategories, members) {
        members.count { member ->
            member.status == "ACTIF" &&
                assignedCategories.any { it.covers(member.groupCode) }
        }
    }
    val dateParser = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val dateDisplay = remember { SimpleDateFormat("dd MMM yyyy", Locale.FRENCH) }
    dateParser.timeZone = TimeZone.getTimeZone("UTC")
    dateDisplay.timeZone = TimeZone.getTimeZone("UTC")

    Column(modifier = Modifier.fillMaxSize()) {
        // ===== En-tete (gradient + photo + identite) =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(JudoRed, JudoRed.copy(alpha = 0.75f), Color(0xFF3A0D0D))
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("coach_detail_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "Fiche Coach",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(74.dp)
                            .border(2.dp, JudoGold, CircleShape)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (coach.photoPath.isNotBlank()) {
                            AsyncImage(
                                model = coach.photoPath,
                                contentDescription = "Photo de ${coach.name}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.SportsMartialArts,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = coach.name,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MilitaryTech,
                                contentDescription = null,
                                tint = JudoGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = coach.grade,
                                color = JudoGold,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (coach.isActive) {
                                com.example.ui.theme.JudoSuccess.copy(alpha = 0.2f)
                            } else {
                                Color.White.copy(alpha = 0.15f)
                            },
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = if (coach.isActive) "COACH ACTIF" else "COACH DÉSACTIVÉ",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = if (coach.isActive) Color(0xFFB9F6CA) else Color.White
                                )
                            )
                        }
                    }
                }
            }
        }

        // ===== Corps =====
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // --- Contact ---
            if (coach.phone.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = JudoRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = coach.phone,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // --- Statistiques du coach ---
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CoachStatCard(
                    label = "Séances animées",
                    value = "${sessionsLed.size}",
                    modifier = Modifier.weight(1f)
                )
                CoachStatCard(
                    label = "Présences pointées",
                    value = "$totalPresent",
                    modifier = Modifier.weight(1f)
                )
                CoachStatCard(
                    label = "Athlètes suivis",
                    value = "$followedMembers",
                    modifier = Modifier.weight(1f)
                )
            }

            // --- Repartition presents / absents sur ses seances ---
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Assiduité de son groupe",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    val total = (totalPresent + totalAbsent).coerceAtLeast(1)
                    val presentRatio = totalPresent.toFloat() / total
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(presentRatio)
                                .fillMaxSize()
                                .background(com.example.ui.theme.JudoSuccess)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Présents : $totalPresent",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = com.example.ui.theme.JudoSuccess,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Absents : $totalAbsent",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFFC62828),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            // --- Categories encadrees ---
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Catégories encadrées",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                if (assignedCategories.isEmpty()) {
                    Text(
                        text = "Aucune catégorie affectée à ce coach.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        assignedCategories.forEach { category ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = JudoGold.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, JudoGold.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = category.name,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color(0xFF8D6E00),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // --- Seances recentes ---
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Séances animées (les 10 dernières)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                if (sessionsLed.isEmpty()) {
                    Text(
                        text = "Aucune séance enregistrée pour ce coach.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                } else {
                    sessionsLed.take(10).forEach { session ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventNote,
                                contentDescription = null,
                                tint = JudoRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.categoryName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = runCatching {
                                        "${dateDisplay.format(dateParser.parse(session.dateString))} • ${session.startTime}-${session.endTime}"
                                    }.getOrDefault(session.dateString),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                            Text(
                                text = "${session.effectivePresentCount}/${session.effectivePresentCount + session.effectiveAbsentCount}",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = JudoRed,
                                    fontWeight = FontWeight.Bold
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
private fun CoachStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = JudoRed
                )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
