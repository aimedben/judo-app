package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.Belt
import com.example.data.model.CoachProfile
import com.example.data.model.CoachUser
import com.example.data.model.JudoCategory
import com.example.data.model.JudoGroup
import com.example.data.model.Member
import com.example.util.GroupCodes
import com.example.ui.theme.JudoGold
import com.example.ui.theme.JudoGoldLight
import com.example.ui.theme.JudoRed
import com.example.ui.theme.JudoSuccess
import com.example.ui.screens.FaceEnrollmentScreen
import com.example.util.CardDataParser
import com.example.util.ExtractedMemberInfo
import com.example.util.PhotoCompressor
import com.example.util.face.FaceEngine
import kotlinx.coroutines.launch

/**
 * Converts a picked member photo (temporary content:// URI) into a small WebP file
 * stored in the app internal storage so it survives restarts and can be pushed to
 * Supabase. Non-picker values (already a file path, or empty) are returned as-is.
 */
private suspend fun resolveMemberPhoto(
    context: android.content.Context,
    rawUrl: String,
    fallbackKey: String
): String {
    if (rawUrl.isBlank()) return ""
    if (!rawUrl.startsWith("content:")) return rawUrl
    return try {
        val bytes = PhotoCompressor.uriToWebp(context, android.net.Uri.parse(rawUrl))
        if (bytes != null) {
            PhotoCompressor.saveBytes(context, "member_$fallbackKey", bytes) ?: rawUrl
        } else {
            rawUrl
        }
    } catch (_: Exception) {
        rawUrl
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberFormSection(index: String, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(JudoRed.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = index,
                color = JudoRed,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = JudoRed,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMemberDialog(
    initialMember: Member?,
    initialExtractedInfo: ExtractedMemberInfo? = null,
    coachProfile: CoachProfile = CoachProfile(),
    coaches: List<CoachUser> = emptyList(),
    categories: List<JudoCategory> = emptyList(),
    onRequestScanCard: (() -> Unit)? = null,
    onEditCoachProfile: (() -> Unit)? = null,
    findDuplicate: (Member) -> Member? = { null },
    // Enrolement facial : disponibles seulement pour un athlete deja enregistre (initialMember != null).
    getFaceEnrollmentCount: (suspend (Long) -> Int)? = null,
    saveFaceSamples: ((Long, List<Pair<FloatArray, Float>>, (Boolean) -> Unit) -> Unit)? = null,
    onSave: (Member) -> Unit,
    onDismiss: () -> Unit
) {
    var firstName by remember {
        mutableStateOf(initialMember?.firstName ?: initialExtractedInfo?.firstName ?: "")
    }
    var lastName by remember {
        mutableStateOf(initialMember?.lastName ?: initialExtractedInfo?.lastName ?: "")
    }
    var birthDate by remember {
        mutableStateOf(initialMember?.birthDate ?: initialExtractedInfo?.birthDate?.ifBlank { null } ?: "")
    }
    var phone by remember {
        mutableStateOf(initialMember?.phone ?: initialExtractedInfo?.phone ?: "")
    }
    var notes by remember {
        mutableStateOf(initialMember?.notes ?: initialExtractedInfo?.notes ?: "")
    }
    var qrCode by remember {
        mutableStateOf(
            initialMember?.qrCode ?: initialExtractedInfo?.qrCode?.ifBlank { null }
                ?: "JCS-${System.currentTimeMillis().toString().takeLast(4)}"
        )
    }
    var season by remember {
        mutableStateOf(initialExtractedInfo?.season?.ifBlank { null } ?: "2026/2027")
    }
    var photoUrl by remember {
        mutableStateOf(initialMember?.photoUrl ?: initialExtractedInfo?.photoUrl ?: "")
    }
    var status by remember {
        mutableStateOf(initialMember?.status ?: "ACTIF")
    }

    // ---- Enrolement facial (athlete deja enregistre uniquement) ----
    var showFaceEnrollment by remember { mutableStateOf(false) }
    var faceCount by remember { mutableStateOf(-1) }
    var faceCountRefresh by remember { mutableStateOf(0) }
    val canEnrollFace = initialMember != null &&
        getFaceEnrollmentCount != null &&
        saveFaceSamples != null
    LaunchedEffect(canEnrollFace, initialMember?.id, faceCountRefresh) {
        val memberId = initialMember?.id
        val ask = getFaceEnrollmentCount
        if (canEnrollFace && memberId != null && ask != null) {
            faceCount = ask(memberId)
        }
    }

    var selectedGroup by remember {
        mutableStateOf(
            initialMember?.group ?: initialExtractedInfo?.group
                ?: if (birthDate.isNotBlank()) JudoGroup.fromBirthDate(birthDate) else JudoGroup.MINIMES
        )
    }
    // Vraie CATEGORIE du membre (admin > categories), synchronisee au chargement
    // avec celle reconnue depuis son groupCode.
    var selectedCategory by remember {
        mutableStateOf(initialMember?.let { m -> categories.firstOrNull { it.covers(m.groupCode) } })
    }
    // Categories actives triees dans l'ordre judo (plus jeunes d'abord).
    val activeCategoryOptions = remember(categories) {
        categories.filter { it.isActive }.sortedWith(
            compareBy(
                { JudoGroup.fromString(it.groupCode).ordinal },
                { it.name.lowercase() }
            )
        )
    }
    var isGroupExpanded by remember { mutableStateOf(false) }

    var selectedBelt by remember {
        mutableStateOf(initialMember?.belt ?: initialExtractedInfo?.belt ?: Belt.BLANCHE)
    }
    var isBeltExpanded by remember { mutableStateOf(false) }

    /**
     * VRAI coach référent : l'entraîneur actif dont les catégories affectées couvrent
     * le groupe du membre (édition) ou du groupe suggéré (création). Retombe sur le
     * profil coach connecté si aucune categorie/entraineur ne correspond.
     */
    val memberGroupForCoach = initialMember?.group ?: selectedGroup
    val referentCoach = remember(memberGroupForCoach, coaches, categories) {
        val coveringCategoryNames = categories
            .filter { GroupCodes.matches(it.groupCode, memberGroupForCoach.code) }
            .map { it.name.trim() }
            .toHashSet()
        coaches.firstOrNull { coach ->
            coach.isActive && coach.assignedCategories.any { name ->
                coveringCategoryNames.contains(name.trim())
            }
        }
    }

    var isFromCardScan by remember {
        mutableStateOf(initialExtractedInfo != null)
    }
    var lastExtractedCardNumber by remember {
        mutableStateOf(initialExtractedInfo?.cardNumber ?: "")
    }
    var isRealCardScanDialogOpen by remember { mutableStateOf(false) }

    var errorMsg by remember { mutableStateOf("") }

    // Sélecteur de date (calendrier) pour la date de naissance.
    var showBirthDatePicker by remember { mutableStateOf(false) }
    if (showBirthDatePicker) {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        val initialMillis = runCatching {
            val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            parser.timeZone = utc
            parser.parse(birthDate)?.time
        }.getOrNull()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis
        )
        DatePickerDialog(
            onDismissRequest = { showBirthDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            formatter.timeZone = utc
                            val iso = formatter.format(java.util.Date(millis))
                            birthDate = iso
                            selectedGroup = JudoGroup.fromBirthDate(iso)
                            // La vraie categorie choisie est ignoree si elle ne
                            // couvre plus la tranche d'age suggeree.
                            selectedCategory = selectedCategory?.takeIf {
                                GroupCodes.matches(it.groupCode, selectedGroup.code)
                            }
                            errorMsg = ""
                        }
                        showBirthDatePicker = false
                    },
                    modifier = Modifier.testTag("birth_date_picker_confirm")
                ) {
                    Text("Valider")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBirthDatePicker = false }) {
                    Text("Annuler")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    var submitAttempted by remember { mutableStateOf(false) }

    // Android zero-permission Photo Picker for Adherent
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            photoUrl = uri.toString()
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun applyExtracted(info: ExtractedMemberInfo) {
        if (info.firstName.isNotBlank()) firstName = info.firstName
        if (info.lastName.isNotBlank()) lastName = info.lastName
        if (info.birthDate.isNotBlank()) {
            birthDate = info.birthDate
            selectedGroup = JudoGroup.fromBirthDate(info.birthDate)
        } else if (info.group != null) {
            selectedGroup = info.group
        }
        if (info.phone.isNotBlank()) phone = info.phone
        if (info.qrCode.isNotBlank()) qrCode = info.qrCode
        else if (info.cardNumber.isNotBlank()) qrCode = com.example.util.MemberQr.PREFIX + info.cardNumber
        if (info.season.isNotBlank()) season = info.season
        if (info.notes.isNotBlank()) notes = info.notes
        if (info.photoUrl.isNotBlank()) photoUrl = info.photoUrl
        if (info.cardNumber.isNotBlank()) lastExtractedCardNumber = info.cardNumber
        if (info.belt != Belt.BLANCHE || info.rawScannedData.contains("blanche", ignoreCase = true)) {
            selectedBelt = info.belt
        }
        isFromCardScan = true
    }

    // Dynamic age calculation
    val calculatedAge = remember(birthDate) {
        try {
            if (birthDate.length >= 4) {
                val year = birthDate.take(4).toInt()
                (2026 - year).coerceAtLeast(0)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .imePadding()
                .padding(vertical = 16.dp)
                .testTag("add_edit_member_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header Bar with Club branding & Title (fixe : reste visible pendant le defilement)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(JudoRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "柔",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (initialMember == null) "Nouvelle Inscription" else "Modifier Judoka",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black
                                    )
                                )
                            }
                            Text(
                                text = "JUDO CLUB SEDDOUK • Saison $season",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_member_dialog_button")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                // Contenu defilant (le pied de page avec les boutons reste toujours visible)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                // Smart Detection Banner if from QR Scan
                if (isFromCardScan) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = JudoGold.copy(alpha = 0.15f),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = JudoGoldLight,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Données détectées automatiquement depuis le QR Code",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = JudoGoldLight
                                    )
                                )
                                Text(
                                    text = if (lastExtractedCardNumber.isNotBlank())
                                        "Carte N° $lastExtractedCardNumber • Tous les champs ont été remplis"
                                    else "Champs pré-remplis avec succès",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }

                // Sports Card Scan / Simulation Quick Action Bar
                if (initialMember == null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DocumentScanner,
                                    contentDescription = null,
                                    tint = JudoRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Extraction automatique de carte sportive",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { isRealCardScanDialogOpen = true },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = JudoRed),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("scan_real_seddouk_card_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DocumentScanner,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Scanner la carte (Caméra / Galerie)",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }

                                if (onRequestScanCard != null) {
                                    OutlinedButton(
                                        onClick = onRequestScanCard,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(48.dp).testTag("dialog_scan_card_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.QrCodeScanner,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("QR seul", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // SECTION 1: PHOTO DE PROFIL DE L'ADHÉRENT (OPTIONNEL)
                // ==========================================
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = JudoRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Photo de profil de l'adhérent",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "Non obligatoire (Optionnel)",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        // Avatar Frame with Belt border
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(3.dp, selectedBelt.primaryColor, CircleShape)
                        ) {
                            if (photoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = "Photo de l'adhérent",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = "Aucune",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }

                        // Action buttons for Photo Picker
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    // Certains appareils n'ont aucun sélecteur de photos :
                                    // on intercepte l'erreur pour ne pas fermer l'application.
                                    try {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    } catch (e: Exception) {
                                        errorMsg = if (e is android.content.ActivityNotFoundException) {
                                            "Aucune galerie photo disponible sur cet appareil."
                                        } else {
                                            "Impossible d'ouvrir la galerie : ${e.message ?: "erreur inconnue"}"
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("adherent_pick_photo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (photoUrl.isBlank()) "Ajouter une photo" else "Changer photo",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }

                            if (photoUrl.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { photoUrl = "" },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Retirer", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // SECTION 3 (OPTIONNEL): IDENTIFICATION PAR VISAGE
                // ==========================================
                if (canEnrollFace) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("face_enroll_entry")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(JudoRed.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Face,
                                    contentDescription = null,
                                    tint = JudoRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Pointage par reconnaissance du visage",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                val countLabel = when {
                                    faceCount <= 0 -> "Aucune empreinte enrôlée pour cet athlète."
                                    else -> "$faceCount empreinte(s) enregistrée(s), chiffrée(s) sur cette tablette."
                                }
                                Text(
                                    text = countLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = { showFaceEnrollment = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("face_enroll_open_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessibilityNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (faceCount > 0) "Recapturer" else "Capturer le visage")
                            }
                        }
                    }
                }

                // ==========================================
                // SECTION 2: IDENTITÉ DE L'ADHÉRENT
                // ==========================================
                MemberFormSection("1", "Identité civile", Icons.Default.Person)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it; errorMsg = "" },
                        label = { Text("Prénom *") },
                        placeholder = { Text("ex: Babi") },
                        singleLine = true,
                        isError = submitAttempted && firstName.isBlank(),
                        supportingText = if (submitAttempted && firstName.isBlank()) {
                            { Text("Prénom obligatoire") }
                        } else null,
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_first_name")
                    )

                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it; errorMsg = "" },
                        label = { Text("Nom *") },
                        placeholder = { Text("ex: BOURENANE") },
                        singleLine = true,
                        isError = submitAttempted && lastName.isBlank(),
                        supportingText = if (submitAttempted && lastName.isBlank()) {
                            { Text("Nom obligatoire") }
                        } else null,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_last_name")
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = birthDate,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Date de Naissance *") },
                        placeholder = { Text("Choisir dans le calendrier") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Cake, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { showBirthDatePicker = true },
                                modifier = Modifier.testTag("birth_date_picker_open")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = "Choisir la date de naissance"
                                )
                            }
                        },
                        isError = submitAttempted && birthDate.isBlank(),
                        supportingText = if (submitAttempted && birthDate.isBlank()) {
                            { Text("Date de naissance obligatoire") }
                        } else null,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showBirthDatePicker = true }
                            .testTag("input_birth_date")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (calculatedAge != null) "Âge calculé : $calculatedAge ans" else "Format attendu : AAAA-MM-JJ",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Catégorie suggérée : ${selectedGroup.label}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = JudoRed,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // ==========================================
                // SECTION 3: SECTION SPORTIVE & GRADES JUDO
                // ==========================================
                MemberFormSection("2", "Catégorie & grade", Icons.Default.SportsMartialArts)

                // Category Dropdown : les vraies CATEGORIES configurees dans admin >
                // categories (nom officiel, ex. "Adults"), pas une liste figee de groupes.
                ExposedDropdownMenuBox(
                    expanded = isGroupExpanded,
                    onExpandedChange = { isGroupExpanded = !isGroupExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedCategory?.name
                            ?: "${selectedGroup.label} (${selectedGroup.ageRange})",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Catégorie") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.SportsMartialArts, contentDescription = null)
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isGroupExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    if (activeCategoryOptions.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = isGroupExpanded,
                            onDismissRequest = { isGroupExpanded = false }
                        ) {
                            activeCategoryOptions.forEach { category ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = category.name, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = category.groupLabel,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedCategory = category
                                        selectedGroup = JudoGroup.fromString(category.groupCode)
                                        isGroupExpanded = false
                                    }
                                )
                            }
                        }
                    } else {
                        ExposedDropdownMenu(
                            expanded = isGroupExpanded,
                            onDismissRequest = { isGroupExpanded = false }
                        ) {
                            JudoGroup.entries.forEach { group ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = group.label, fontWeight = FontWeight.Bold)
                                            Text(text = "(${group.ageRange})", style = MaterialTheme.typography.bodySmall)
                                        }
                                    },
                                    onClick = {
                                        selectedGroup = group
                                        isGroupExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Belt Dropdown with Belt Colors
                ExposedDropdownMenuBox(
                    expanded = isBeltExpanded,
                    onExpandedChange = { isBeltExpanded = !isBeltExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedBelt.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Ceinture / Grade") },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(selectedBelt.primaryColor)
                                    .border(1.dp, Color.Gray, CircleShape)
                            )
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isBeltExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = isBeltExpanded,
                        onDismissRequest = { isBeltExpanded = false }
                    ) {
                        Belt.entries.forEach { belt ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(belt.primaryColor)
                                                .border(1.dp, Color.Gray, CircleShape)
                                        )
                                        Text(text = belt.displayName, fontWeight = FontWeight.SemiBold)
                                    }
                                },
                                onClick = {
                                    selectedBelt = belt
                                    isBeltExpanded = false
                                }
                            )
                        }
                    }
                }

                // ==========================================
                // SECTION 4: COORDONNÉES & TUTEUR LÉGAL
                // ==========================================
                MemberFormSection("3", "Contact du tuteur", Icons.Default.Phone)

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Téléphone tuteur / parent *") },
                    placeholder = { Text("0782487120") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Phone, contentDescription = null)
                    },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_phone")
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes médicales & Observations") },
                    placeholder = { Text("Certificat médical, observations...") },
                    modifier = Modifier.fillMaxWidth()
                )

                // ==========================================
                // SECTION 5: LICENCE & IDENTIFIANT QR
                // ==========================================
                MemberFormSection("4", "Licence & badge QR", Icons.Default.QrCode)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = qrCode,
                        onValueChange = { qrCode = it },
                        label = { Text("Identifiant Carte / QR *") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.QrCode, contentDescription = null)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("input_qr_code")
                    )

                    OutlinedTextField(
                        value = season,
                        onValueChange = { season = it },
                        label = { Text("Saison") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Status Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Statut :",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    FilterChip(
                        selected = status == "ACTIF",
                        onClick = { status = "ACTIF" },
                        label = { Text("Actif (Inscrit)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = JudoSuccess.copy(alpha = 0.2f),
                            selectedLabelColor = JudoSuccess
                        )
                    )
                    FilterChip(
                        selected = status == "SUSPENDU",
                        onClick = { status = "SUSPENDU" },
                        label = { Text("Suspendu") }
                    )
                }

                // ==========================================
                // SECTION 6: COACH RÉFÉRENT & SUPERVISEUR
                // ==========================================
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = CardDefaults.outlinedCardBorder()
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
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF212121))
                                    .border(2.dp, JudoGold, CircleShape)
                            ) {
                                if (!referentCoach?.photoPath.isNullOrBlank()) {
                                    AsyncImage(
                                        model = referentCoach?.photoPath,
                                        contentDescription = "Coach",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                } else if (coachProfile.photoUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = coachProfile.photoUrl,
                                        contentDescription = "Coach",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.SportsMartialArts,
                                        contentDescription = null,
                                        tint = JudoGold,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Coach Référent: ${referentCoach?.name ?: coachProfile.name}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (referentCoach != null) {
                                        "${referentCoach.grade} • ${referentCoach.assignedCategories.joinToString(", ")}"
                                    } else {
                                        "${coachProfile.danGrade} • ${coachProfile.clubName}"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        if (onEditCoachProfile != null) {
                            IconButton(
                                onClick = onEditCoachProfile,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Modifier profil coach",
                                    tint = JudoRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                if (errorMsg.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = errorMsg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                // Actions Buttons (pied de page fixe)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("Annuler")
                    }

                    Button(
                        onClick = {
                            submitAttempted = true
                            if (firstName.isBlank() || lastName.isBlank()) {
                                errorMsg = "Veuillez renseigner le prénom et le nom de l'adhérent."
                                return@Button
                            }
                            if (birthDate.isBlank()) {
                                errorMsg = "Veuillez renseigner la date de naissance (AAAA-MM-JJ)."
                                return@Button
                            }
                            val cleanBirthDate = birthDate.trim()
                            val cleanPhone = phone.trim()
                            val cleanQr = com.example.util.MemberQr
                                .normalize(qrCode, lastExtractedCardNumber)
                                .ifBlank { "JCS-${System.currentTimeMillis().toString().takeLast(4)}" }
                            val photoKey = initialMember?.id?.takeIf { it > 0 }?.toString() ?: cleanQr

                            // Anti-doublon : refuser d'enregistrer un athlete deja existant.
                            val candidate = (initialMember ?: Member(
                                qrCode = cleanQr,
                                firstName = "",
                                lastName = "",
                                birthDate = "",
                                phone = "",
                                groupCode = selectedGroup.code,
                                beltName = selectedBelt.name
                            )).copy(
                                firstName = firstName.trim(),
                                lastName = lastName.trim(),
                                birthDate = cleanBirthDate,
                                phone = cleanPhone,
                                qrCode = cleanQr
                            )
                            val duplicate = findDuplicate(candidate)
                            if (duplicate != null) {
                                errorMsg = "Cet athlète existe déjà : ${duplicate.fullName} " +
                                    "(${duplicate.qrCode}). Doublon refusé. " +
                                    "Modifiez la fiche existante si nécessaire."
                                return@Button
                            }

                            scope.launch {
                                val resolvedPhoto = resolveMemberPhoto(context, photoUrl.trim(), photoKey)
                                val toSave = initialMember?.copy(
                                    firstName = firstName.trim(),
                                    lastName = lastName.trim(),
                                    birthDate = cleanBirthDate,
                                    phone = cleanPhone,
                                    groupCode = selectedCategory?.groupCode ?: selectedGroup.code,
                                    beltName = selectedBelt.name,
                                    qrCode = cleanQr,
                                    status = status,
                                    photoUrl = resolvedPhoto,
                                    notes = notes.trim()
                                ) ?: Member(
                                    firstName = firstName.trim(),
                                    lastName = lastName.trim(),
                                    birthDate = cleanBirthDate,
                                    phone = cleanPhone,
                                    groupCode = selectedCategory?.groupCode ?: selectedGroup.code,
                                    beltName = selectedBelt.name,
                                    qrCode = cleanQr,
                                    status = status,
                                    registrationDate = java.text.SimpleDateFormat(
                                        "yyyy-MM-dd", java.util.Locale.US
                                    ).format(java.util.Date()),
                                    photoUrl = resolvedPhoto,
                                    notes = notes.trim()
                                )
                                onSave(toSave)
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = JudoRed),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.8f)
                            .height(50.dp)
                            .testTag("submit_member_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (initialMember == null) "Valider & Enregistrer" else "Enregistrer Modifications",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }

    if (isRealCardScanDialogOpen) {
        SportCardScanDialog(
            onExtracted = { info ->
                applyExtracted(info)
                isRealCardScanDialogOpen = false
            },
            onDismiss = { isRealCardScanDialogOpen = false }
        )
    }

    if (showFaceEnrollment && initialMember != null && saveFaceSamples != null) {
        FaceEnrollmentScreen(
            member = initialMember,
            savedCount = faceCount.coerceAtLeast(0),
            hasEngine = FaceEngine.engineReady(context),
            onSaveSamples = { samples, onDone ->
                saveFaceSamples.invoke(initialMember.id, samples, onDone)
            },
            onDismiss = {
                showFaceEnrollment = false
                // Recharge le compteur apres un (re)enrolement reussi ou abandonne.
                faceCountRefresh++
            }
        )
    }
}
