package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AttendanceWithMember
import com.example.data.model.JudoGroup
import com.example.data.model.Member
import com.example.data.model.ScheduledTrainingSession
import com.example.ui.components.HeroStat
import com.example.ui.components.JudoHeroCard
import com.example.ui.theme.JudoGold
import com.example.ui.theme.JudoRed
import com.example.ui.theme.JudoSuccess
import com.example.ui.theme.JudoWarning
import com.example.util.AbsenceAlerts
import com.example.util.AbsentSmsNotifier
import com.example.util.NameKey
import com.example.util.OfficialPeriod
import com.example.util.SimSmsSender
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil

/** Filtre principal de la sélection des athlètes (notamment les filtres d'absence). */
private enum class SmsMode { TOUS, DERNIERE_SEANCE, ABSENCES_3, ABSENCES_2, ASSIDUITE_FAIBLE, SUSPENDUS }

/**
 * Centre SMS admin : composition d'un message (toujours pré-rempli, personnalisable),
 * sélection des athlètes par filtres (groupes, absences de la dernière séance,
 * absences du mois, assiduité faible, recherche), et coût EXACT des SMS à payer
 * (segments × prix unitaire × destinataires, indépendant de l'opérateur).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSmsCenterScreen(
    allMembers: List<Member>,
    allAttendance: List<AttendanceWithMember>,
    pastSessions: List<com.example.data.model.PastTrainingSession>,
    scheduledSessions: List<ScheduledTrainingSession>,
    clubName: String,
    officialStartDate: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = Color(0xFF1B5E20)

    // ---- Forme du message : modèle pré-rempli, éditable ------------------------------
    var messageTemplate by remember { mutableStateOf(AbsentSmsNotifier.DEFAULT_MESSAGE_TEMPLATE) }

    // ---- Prix unitaire d'un SMS (dinars), réglable par l'admin ------------------------
    var unitPriceText by remember { mutableStateOf("1.00") }

    // ---- Recherche + filtres ----------------------------------------------------------
    var searchQuery by remember { mutableStateOf("") }
    var selectedGroupCodes by remember { mutableStateOf(setOf<String>()) }
    var mode by remember { mutableStateOf(SmsMode.TOUS) }

    // ---- Données dérivées --------------------------------------------------------------
    val today = Calendar.getInstance()
    val lastSession = remember(pastSessions, officialStartDate) {
        pastSessions.maxByOrNull { it.dateString }
    }
    val lastSessionDate = lastSession?.dateString
    val lastSessionAbsentMembers = remember(lastSession, allMembers) {
        val wanted = (lastSession?.absentMemberNames.orEmpty())
            .map { NameKey.canonical(it) }.toHashSet()
        allMembers.filter { wanted.contains(NameKey.canonical(it.fullName)) }
    }

    fun monthAlerts(threshold: Int): List<AbsenceAlerts.Alert> =
        AbsenceAlerts.membersWithMonthAbsences(
            members = allMembers,
            attendances = allAttendance,
            scheduledSessions = scheduledSessions,
            officialStartDate = officialStartDate,
            todayMillis = today.timeInMillis,
            year = today.get(Calendar.YEAR),
            month0 = today.get(Calendar.MONTH),
            threshold = threshold
        )

    val absences3 = remember(mode, allMembers, allAttendance, scheduledSessions, officialStartDate) {
        if (mode == SmsMode.ABSENCES_3) monthAlerts(3) else emptyList()
    }
    val absences2 = remember(mode, allMembers, allAttendance, scheduledSessions, officialStartDate) {
        if (mode == SmsMode.ABSENCES_2) monthAlerts(2) else emptyList()
    }
    val absenceCountByMember = remember(allMembers, allAttendance, scheduledSessions, officialStartDate) {
        AbsenceAlerts.membersWithMonthAbsences(
            members = allMembers,
            attendances = allAttendance,
            scheduledSessions = scheduledSessions,
            officialStartDate = officialStartDate,
            todayMillis = today.timeInMillis,
            year = today.get(Calendar.YEAR),
            month0 = today.get(Calendar.MONTH),
            threshold = 1
        ).associate { it.member.id to it.absencesMonth }
    }
    val attendanceDaysByMember = remember(allMembers, allAttendance, officialStartDate) {
        allAttendance.groupBy { it.memberId }
            .mapValues { (_, rows) -> rows.map { it.dateString }.distinct()
                .count { OfficialPeriod.isOfficial(it, officialStartDate) } }
    }
    val clubDistinctDays = remember(allAttendance, officialStartDate) {
        allAttendance.map { it.dateString }.distinct()
            .count { OfficialPeriod.isOfficial(it, officialStartDate) }
    }

    val memberPool = remember(mode, lastSessionAbsentMembers, absences2, absences3, attendanceDaysByMember, clubDistinctDays) {
        val pool = when (mode) {
            SmsMode.TOUS -> allMembers.filter { it.status == "ACTIF" }
            SmsMode.DERNIERE_SEANCE -> lastSessionAbsentMembers
            SmsMode.ABSENCES_3 -> absences3.map { it.member }
            SmsMode.ABSENCES_2 -> absences2.map { it.member }
            SmsMode.ASSIDUITE_FAIBLE -> allMembers.filter { m ->
                val days = attendanceDaysByMember[m.id] ?: 0
                clubDistinctDays > 0 && (days.toDouble() / clubDistinctDays * 100).toInt() < 50
            }
            SmsMode.SUSPENDUS -> allMembers.filter { it.isSuspended }
        }
        // Jamais de SMS a un athlete blesse ou suspendu, sauf en mode SUSPENDUS.
        if (mode == SmsMode.SUSPENDUS) {
            pool.distinctBy { it.id }
        } else {
            pool.filter { it.status == "ACTIF" && !it.isInjured }.distinctBy { it.id }
        }
    }

    val filteredMembers = remember(memberPool, selectedGroupCodes, searchQuery) {
        memberPool
            .filter { m -> selectedGroupCodes.isEmpty() || selectedGroupCodes.contains(m.group.code.uppercase()) }
            .filter { m ->
                searchQuery.isBlank() ||
                    m.fullName.contains(searchQuery.trim(), ignoreCase = true) ||
                    m.phone.contains(searchQuery.trim())
            }
    }

    // ---- Sélection ---------------------------------------------------------------------
    val selectedIds = remember { mutableStateListOf<Long>() }
    val selectableMembers = remember(filteredMembers) {
        filteredMembers.filter { AbsentSmsNotifier.normalizePhone(it.phone) != null }
    }
    val noNumberCount = filteredMembers.size - selectableMembers.size
    val recipients = selectableMembers.filter { it.id in selectedIds }

    fun toggle(memberId: Long) {
        if (memberId in selectedIds) selectedIds.remove(memberId) else selectedIds.add(memberId)
    }

    // A chaque changement de filtre, on pré-coche tous les athlètes joignables :
    // l'admin retire ensuite ceux qu'il ne veut pas (personnalisation avant envoi).
    LaunchedEffect(mode) {
        selectedIds.clear()
        memberPool
            .filter { m -> AbsentSmsNotifier.normalizePhone(m.phone) != null }
            .forEach { selectedIds.add(it.id) }
    }

    // ---- Coût exact ---------------------------------------------------------------------
    val pricePerSms = unitPriceText.replace(",", ".").toDoubleOrNull() ?: 0.0
    val segmentsPerMessage = remember(recipients, messageTemplate) {
        val dateLabel = lastSessionDate
            ?.let { d -> Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").find(d)?.let { "${it.groupValues[3]}/${it.groupValues[2]}/${it.groupValues[1]}" } ?: d }
            ?: "jj/MM/aaaa"
        val sample = messageTemplate
            .replace("{nom}", recipients.firstOrNull()?.fullName ?: "Nom Athlète", ignoreCase = true)
            .replace("{date}", dateLabel, ignoreCase = true)
            .replace("{heure}", "", ignoreCase = true)
            .replace("{club}", clubName, ignoreCase = true)
        smsSegmentCount(sample)
    }
    val totalCost = pricePerSms * segmentsPerMessage * recipients.size
    val costLabel = String.format(Locale.FRENCH, "%.2f DA", totalCost)

    // ---- Envoi direct par la SIM --------------------------------------------------------
    val smsCapable = remember { SimSmsSender.isDeviceSmsCapable(context) }
    val sendResults = remember { mutableStateListOf<Pair<Member, String>>() } // member → statut
    var isSending by remember { mutableStateOf(false) }
    var awaitingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = awaitingPermissionAction
        awaitingPermissionAction = null
        if (granted) pending?.invoke()
        else Toast.makeText(context, "Autorisation SMS refusée", Toast.LENGTH_LONG).show()
    }

    fun ensurePermissionThen(action: () -> Unit) {
        if (SimSmsSender.hasSendSmsPermission(context)) action()
        else {
            awaitingPermissionAction = action
            permissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
    }

    Column(modifier = Modifier.fillMaxSize().testTag("admin_sms_center_screen")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ===== Banniere heros (style accueil du dashboard) =====
            JudoHeroCard(
                title = "Centre SMS",
                subtitle = "Composez votre message, choisissez les athlètes et payez le juste coût",
                icon = Icons.Default.Sms,
                startColor = Color(0xFF14532D),
                midColor = Color(0xFF1B7A34),
                endColor = Color(0xFF0B3D0B),
                onBack = onBack,
                stats = listOf(
                    HeroStat(value = "${recipients.size}", label = "Destinataires"),
                    HeroStat(value = "${recipients.size * segmentsPerMessage}", label = "SMS au total"),
                    HeroStat(value = costLabel, label = "Coût estimé")
                )
            )

            // ===== 1. Message (pré-rempli, personnalisable) =====
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmsSectionTitle(
                        number = "1",
                        title = "Votre message",
                        subtitle = "Pré-rempli et modifiable",
                        accent = accent
                    )
                    OutlinedTextField(
                        value = messageTemplate,
                        onValueChange = { messageTemplate = it },
                        minLines = 5,
                        maxLines = 8,
                        shape = RoundedCornerShape(14.dp),
                        label = { Text("Modèle du message") },
                        supportingText = { Text("{NOM} = nom de l'athlète • {CLUB} = club • {DATE} = date de la dernière séance") },
                        modifier = Modifier.fillMaxWidth().testTag("sms_center_message_edit")
                    )

                    // Aperçu réel du message personnalisé pour le premier destinataire
                    val previewName = recipients.firstOrNull()?.fullName
                    if (previewName != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = accent.copy(alpha = 0.07f),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.20f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Aperçu pour $previewName",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = accent
                                        ),
                                        maxLines = 1
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = accent.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = "$segmentsPerMessage SMS",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = accent
                                            ),
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = AbsentSmsNotifier.messageFromTemplate(
                                        template = messageTemplate,
                                        memberName = previewName,
                                        sessionDate = lastSessionDate ?: todayIso(),
                                        sessionStart = null,
                                        clubName = clubName
                                    ),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ===== 2. Destinataires : filtres + personnalisation =====
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmsSectionTitle(
                        number = "2",
                        title = "Destinataires",
                        subtitle = "$noNumberCount numéro(s) invalide(s) — ${selectedIds.size} retenu(s)",
                        accent = accent
                    )

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Rechercher (nom ou numéro)") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("sms_center_search")
                    )

                    // Filtres rapides (défilement horizontal)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            SmsFilterChip(
                                label = "Tous les actifs",
                                selected = mode == SmsMode.TOUS,
                                onClick = { mode = SmsMode.TOUS },
                                accent = accent,
                                modifier = Modifier.testTag("sms_filter_all")
                            )
                        }
                        item {
                            SmsFilterChip(
                                label = "Absents dernière séance",
                                selected = mode == SmsMode.DERNIERE_SEANCE,
                                onClick = { mode = SmsMode.DERNIERE_SEANCE },
                                accent = accent,
                                modifier = Modifier.testTag("sms_filter_last_session")
                            )
                        }
                        item {
                            SmsFilterChip(
                                label = "3+ absences",
                                selected = mode == SmsMode.ABSENCES_3,
                                onClick = { mode = SmsMode.ABSENCES_3 },
                                accent = accent,
                                modifier = Modifier.testTag("sms_filter_abs3")
                            )
                        }
                        item {
                            SmsFilterChip(
                                label = "2+ absences",
                                selected = mode == SmsMode.ABSENCES_2,
                                onClick = { mode = SmsMode.ABSENCES_2 },
                                accent = accent,
                                modifier = Modifier.testTag("sms_filter_abs2")
                            )
                        }
                        item {
                            SmsFilterChip(
                                label = "Assiduité < 50 %",
                                selected = mode == SmsMode.ASSIDUITE_FAIBLE,
                                onClick = { mode = SmsMode.ASSIDUITE_FAIBLE },
                                accent = accent,
                                modifier = Modifier.testTag("sms_filter_low_rate")
                            )
                        }
                        item {
                            SmsFilterChip(
                                label = "Suspendus",
                                selected = mode == SmsMode.SUSPENDUS,
                                onClick = { mode = SmsMode.SUSPENDUS },
                                accent = accent,
                                modifier = Modifier.testTag("sms_filter_suspended")
                            )
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(JudoGroup.entries.toList()) { group ->
                            SmsFilterChip(
                                label = group.code,
                                selected = group.code.uppercase() in selectedGroupCodes,
                                onClick = {
                                    val code = group.code.uppercase()
                                    selectedGroupCodes =
                                        if (code in selectedGroupCodes) selectedGroupCodes - code
                                        else selectedGroupCodes + code
                                },
                                accent = accent,
                                modifier = Modifier.testTag("sms_filter_group_${group.code}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallPillButton(
                            text = "Tout cocher",
                            onClick = {
                                selectedIds.clear()
                                selectableMembers.forEach { selectedIds.add(it.id) }
                            },
                            accent = accent,
                            modifier = Modifier.testTag("sms_select_all_btn")
                        )
                        SmallPillButton(
                            text = "Vider",
                            onClick = { selectedIds.clear() },
                            accent = JudoRed,
                            modifier = Modifier.testTag("sms_clear_btn")
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${selectedIds.size} / ${selectableMembers.size}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                        )
                    }

                    if (noNumberCount > 0) {
                        Text(
                            text = "$noNumberCount athlète(s) sans numéro valide sont exclus de l'envoi.",
                            style = MaterialTheme.typography.bodySmall.copy(color = JudoWarning, fontWeight = FontWeight.Bold)
                        )
                    }

                    // Destinataires retenus, retirables un par un
                    if (recipients.isNotEmpty()) {
                        Text(
                            text = "Retenus pour l'envoi — appuyez sur ✕ pour retirer",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(recipients, key = { it.id }) { member ->
                                RecipientChip(
                                    name = member.fullName,
                                    accent = accent,
                                    onRemove = { toggle(member.id) },
                                    modifier = Modifier.testTag("sms_recipient_chip_${member.id}")
                                )
                            }
                        }
                    }

                    if (filteredMembers.isEmpty()) {
                        Text(
                            text = "Aucun athlète pour ces filtres.",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }

                    val visibleMembers = filteredMembers.take(40)
                    visibleMembers.forEach { member ->
                        val hasNumber = AbsentSmsNotifier.normalizePhone(member.phone) != null
                        val absCount = absenceCountByMember[member.id] ?: 0
                        val isChecked = member.id in selectedIds
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = hasNumber) { toggle(member.id) }
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isChecked) accent.copy(alpha = 0.10f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .testTag("sms_member_row_${member.id}")
                        ) {
                            Icon(
                                imageVector = if (isChecked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isChecked) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = member.fullName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (hasNumber) member.phone else "Sans numéro valide — exclus",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (hasNumber) MaterialTheme.colorScheme.onSurfaceVariant else JudoWarning
                                    )
                                )
                            }
                            if (absCount > 0) {
                                Surface(shape = RoundedCornerShape(50), color = JudoRed.copy(alpha = 0.12f)) {
                                    Text(
                                        text = "$absCount abs.",
                                        style = MaterialTheme.typography.labelSmall.copy(color = JudoRed, fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            if (isChecked) {
                                IconButton(
                                    onClick = { toggle(member.id) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Retirer ${member.fullName}",
                                        tint = JudoRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (filteredMembers.size > visibleMembers.size) {
                        Text(
                            text = "+${filteredMembers.size - visibleMembers.size} autre(s) athlète(s) — affinez la recherche ou les filtres pour les voir.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }

            // ===== 3. Coût exact & envoi =====
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmsSectionTitle(
                        number = "3",
                        title = "Coût & envoi",
                        subtitle = "Paiement au SMS réellement envoyé",
                        accent = accent
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "${recipients.size}",
                                style = MaterialTheme.typography.headlineSmall.copy(color = accent, fontWeight = FontWeight.Bold)
                            )
                            Text("Destinataires", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "$segmentsPerMessage",
                                style = MaterialTheme.typography.headlineSmall.copy(color = accent, fontWeight = FontWeight.Bold)
                            )
                            Text("SMS / message", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                        Column(modifier = Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            OutlinedTextField(
                                value = unitPriceText,
                                onValueChange = { unitPriceText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                                label = { Text("Prix d'un SMS (DA)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("sms_center_price_edit")
                            )
                        }
                    }
                    Surface(shape = RoundedCornerShape(12.dp), color = JudoGold.copy(alpha = 0.15f)) {
                        Text(
                            text = "$costLabel — = ${recipients.size} × $segmentsPerMessage SMS × $pricePerSms DA",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF8D6E00)),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }

                    if (recipients.isEmpty()) {
                        Text(
                            text = "Cochez les athlètes à contacter pour envoyer vos SMS.",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    } else if (!smsCapable) {
                        Text(
                            text = "Cet appareil ne gère pas la téléphonie (SMS) : l'envoi direct est impossible ici.",
                            style = MaterialTheme.typography.bodySmall.copy(color = JudoWarning, fontWeight = FontWeight.Bold)
                        )
                    } else {
                        Button(
                            onClick = {
                                ensurePermissionThen {
                                    isSending = true
                                    sendResults.clear()
                                    scope.launch {
                                        val pending = recipients.toList()
                                        sendResults.clear()
                                        pending.forEach { sendResults.add(it to "En attente…") }

                                        val setStatus: (Member, String) -> Unit = { m, s ->
                                            val idx = sendResults.indexOfFirst { it.first.id == m.id }
                                            if (idx >= 0) sendResults[idx] = m to s
                                        }

                                        val runPass: suspend (List<Member>, Int) -> Unit = { list, attempt ->
                                            for (member in list) {
                                                val number = AbsentSmsNotifier.normalizePhone(member.phone)
                                                    ?: continue
                                                val body = AbsentSmsNotifier.messageFromTemplate(
                                                    template = messageTemplate,
                                                    memberName = member.fullName,
                                                    sessionDate = lastSessionDate ?: todayIso(),
                                                    sessionStart = null,
                                                    clubName = clubName
                                                )
                                                setStatus(member, if (attempt == 1) "Envoi…" else "Nouvelle tentative $attempt…")
                                                val result = sendOneAttempt(context, number, body)
                                                setStatus(
                                                    member,
                                                    when (result) {
                                                        is SimSmsSender.SentResult.Sent -> "Envoyé ✓"
                                                        is SimSmsSender.SentResult.Error ->
                                                            "Échec (code ${result.errorCode})"
                                                    }
                                                )
                                                delay(600) // espace entre SMS, sans bloquer l'UI
                                            }
                                        }

                                        runPass(pending, 1)
                                        // Renvoi AUTOMATIQUE des échecs : 2 nouvelles tentatives.
                                        for (round in 2..3) {
                                            val failed = sendResults
                                                .filter { it.second.startsWith("Échec") }
                                                .map { it.first }
                                            if (failed.isEmpty()) break
                                            delay(1_500)
                                            runPass(failed, round)
                                        }
                                        val stillFailing = sendResults.count { it.second.startsWith("Échec") }
                                        if (stillFailing > 0) {
                                            sendResults.add(
                                                Member(id = -1, qrCode = "", firstName = "Bilan", lastName = "",
                                                    birthDate = "", phone = "", groupCode = "", beltName = "") to
                                                    "$stillFailing échec(s) après 3 tentatives"
                                            )
                                        }
                                        isSending = false
                                    }
                                }
                            },
                            enabled = !isSending,
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("sms_center_send_btn")
                        ) {
                            Text(
                                text = if (isSending) "Envoi en cours…" else "Envoyer par la SIM (${recipients.size} × $segmentsPerMessage SMS — $costLabel)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    sendResults.forEach { (member, state) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(member.fullName, style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = state,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = when {
                                        state.contains("Envoyé") -> JudoSuccess
                                        state.contains("attente") || state.contains("Envoi") ||
                                            state.contains("tentative") -> MaterialTheme.colorScheme.onSurfaceVariant
                                        else -> JudoRed
                                    },
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

/** Titre numéroté d'une étape du centre SMS (1, 2, 3). */
@Composable
private fun SmsSectionTitle(
    number: String,
    title: String,
    subtitle: String,
    accent: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

/** Puce de filtre compacte et arrondie (défilement horizontal). */
@Composable
private fun SmsFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) accent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

/** Petit bouton pilule d'action (Tout cocher / Vider). */
@Composable
private fun SmallPillButton(
    text: String,
    onClick: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = accent
            ),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

/** Puce d'un destinataire retenu, avec croix de retrait. */
@Composable
private fun RecipientChip(
    name: String,
    accent: Color,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 12.dp, end = 5.dp, top = 5.dp, bottom = 5.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1
            )
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Retirer $name",
                    tint = accent,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/** Nombre de segments GSM7 (153/160 caractères) ou Unicode/UCS2 (67/70) d'un message. */
private fun smsSegmentCount(message: String): Int {
    if (message.isBlank()) return 0
    val gsmExtra = "éèêëàâäçîïôöûüùÉÈÊËÀÄÇÎÏÔÖÛÜÙ€«»"
    val isGsm = message.all { it.code < 128 || it in gsmExtra }
    val singleLimit = if (isGsm) 160 else 70
    val unit = if (isGsm) 153 else 67
    val length = message.length
    return if (length <= singleLimit) 1 else ceil(length.toDouble() / unit).toInt()
}

/** Date du jour au format ISO yyyy-MM-dd (sans java.time pour rester compatible). */
private fun todayIso(): String {
    val cal = Calendar.getInstance()
    return String.format(
        Locale.US,
        "%04d-%02d-%02d",
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH)
    )
}

/**
 * Envoie un SMS et attend le verdict confirme par la radio (25 s max).
 * Sert de brique au suivi d'envoi : chaque destinataire a un vrai statut
 * (envoye / echec) et les echecs peuvent etre rejoues automatiquement.
 */
private suspend fun sendOneAttempt(
    context: Context,
    number: String,
    body: String
): SimSmsSender.SentResult {
    val action = SimSmsSender.SENT_ACTION_PREFIX + "smsc-" + System.nanoTime()
    val verdict = CompletableDeferred<SimSmsSender.SentResult>()
    val receiver = SimSmsSender.registerSentReceiver(context, action) { result ->
        verdict.complete(result)
        true
    }
    val dispatched = SimSmsSender.dispatch(context, number, body, action)
    if (!dispatched) {
        try { context.unregisterReceiver(receiver) } catch (_: IllegalArgumentException) { }
        verdict.complete(SimSmsSender.SentResult.Error(-1, 0))
    }
    return withTimeoutOrNull(25_000) { verdict.await() }
        ?: SimSmsSender.SentResult.Error(-2, 0)
}
