package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.data.model.ActiveTrainingSession
import com.example.data.model.AttendanceWithMember
import com.example.data.model.CoachScanFeedback
import com.example.data.model.CoachUser
import com.example.data.model.JudoCategory
import com.example.ui.theme.JudoRed
import com.example.ui.theme.JudoSuccess
import com.example.util.CoachNameKey
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

private fun coachTimeNow(): String = SimpleDateFormat("HH:mm", Locale.FRENCH).format(Date())
private fun coachDateToday(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

/** Resout la fiche coach (donc sa photo) a partir du nom enregistre sur la categorie. */
@Composable
private fun coachForName(coaches: List<CoachUser>, name: String): CoachUser? =
    remember(coaches, name) { coaches.firstOrNull { CoachNameKey.same(it.name, name) } }

private fun coachInitials(name: String): String =
    name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "?" }

/**
 * Photo ronde d'un coach (repli sur les initiales si aucune photo n'est disponible).
 * Utilisee sur l'accueil coach et sur l'ecran de preparation de seance.
 */
@Composable
private fun CoachRoundAvatar(
    name: String,
    photoPath: String,
    size: Dp = 48.dp,
    ringColor: Color? = null,
    modifier: Modifier = Modifier
) {
    var photoFailed by remember(photoPath) { mutableStateOf(false) }
    val showPhoto = photoPath.isNotBlank() && !photoFailed
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(JudoRed.copy(alpha = 0.12f))
            .then(
                if (ringColor != null) Modifier.border(2.dp, ringColor, CircleShape) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (showPhoto) {
            AsyncImage(
                model = photoPath,
                contentDescription = "Photo de ${name.ifBlank { "coach" }}",
                contentScale = ContentScale.Crop,
                onError = { photoFailed = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = coachInitials(name),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                color = JudoRed
            )
        }
    }
}

/** Ligne identite coach : photo + nom + role (accueil coach). */
@Composable
private fun CoachIdentityRow(
    role: String,
    name: String,
    photoPath: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CoachRoundAvatar(name = name, photoPath = photoPath, size = 44.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = role,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

/** Carte de presence d'un coach (preparation de seance) : photo, nom, role, coche verte. */
@Composable
private fun CoachPresenceCard(
    name: String,
    role: String,
    photoPath: String,
    present: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (present) JudoSuccess.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (present) 2.dp else 1.dp,
            if (present) JudoSuccess else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        modifier = modifier.clickable(onClick = onToggle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                CoachRoundAvatar(
                    name = name,
                    photoPath = photoPath,
                    size = 64.dp,
                    ringColor = if (present) JudoSuccess else null
                )
                if (present) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(JudoSuccess),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = role,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Screen 1 — Minimalist coach home. Shown when a coach has selected a category but no
 * training session is running yet. Focus on a single, prominent "Nouvelle séance" action.
 */
@Composable
fun CoachMinimalHomeScreen(
    category: JudoCategory,
    onChangeCategory: () -> Unit,
    onStartNewSession: () -> Unit,
    coaches: List<CoachUser> = emptyList(),
    modifier: Modifier = Modifier
) {
    val coach1 = coachForName(coaches, category.coach1Name)
    val coach2 = coachForName(coaches, category.coach2Name)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
            .testTag("coach_minimal_home_screen"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Bonjour Coach 👋",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black)
                )
            }

            OutlinedButton(
                onClick = onChangeCategory,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                modifier = Modifier.testTag("coach_change_category_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = JudoRed,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Changer", color = MaterialTheme.colorScheme.onSurface)
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(JudoRed.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsMartialArts,
                            contentDescription = null,
                            tint = JudoRed,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column {
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Groupe ${category.groupLabel}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                )

                CoachIdentityRow(
                    role = "Entraîneur principal",
                    name = category.coach1Name,
                    photoPath = coach1?.photoPath.orEmpty()
                )
                if (category.coach2Name.isNotBlank()) {
                    CoachIdentityRow(
                        role = "Co-entraîneur",
                        name = category.coach2Name,
                        photoPath = coach2?.photoPath.orEmpty()
                    )
                }
            }
        }

        Button(
            onClick = onStartNewSession,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = JudoRed,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .testTag("coach_start_new_session_btn")
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Nouvelle séance",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
            )
        }

        Text(
            text = "Démarrez une séance pour activer le pointage QR des judokas sur le tatami.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Screen 2 — Session preparation. Confirms the category, lets the coach validate which
 * coaches are present and then launches the training session (and the scanner).
 */
@Composable
fun CoachSessionPreparationScreen(
    category: JudoCategory,
    onBack: () -> Unit,
    onStartTraining: (ActiveTrainingSession) -> Unit,
    coaches: List<CoachUser> = emptyList(),
    modifier: Modifier = Modifier
) {
    var coach1Present by remember { mutableStateOf(true) }
    var coach2Present by remember { mutableStateOf(category.coach2Name.isNotBlank()) }
    val startTime = remember { coachTimeNow() }
    val startDate = remember { coachDateToday() }
    val coach1 = coachForName(coaches, category.coach1Name)
    val coach2 = coachForName(coaches, category.coach2Name)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
            .testTag("coach_session_preparation_screen"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour"
                )
            }
            Text(
                text = "Préparation de séance",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
            )
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Groupe", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(category.groupLabel, fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Heure de début", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(startTime, fontWeight = FontWeight.Bold, color = JudoRed)
                }
            }
        }

        Text(
            text = "Coachs présents sur le tatami",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CoachPresenceCard(
                name = category.coach1Name,
                role = "Entraîneur principal",
                photoPath = coach1?.photoPath.orEmpty(),
                present = coach1Present,
                onToggle = { coach1Present = !coach1Present },
                modifier = Modifier
                    .weight(1f)
                    .testTag("coach_present_card_1")
            )
            if (category.coach2Name.isNotBlank()) {
                CoachPresenceCard(
                    name = category.coach2Name,
                    role = "Co-entraîneur",
                    photoPath = coach2?.photoPath.orEmpty(),
                    present = coach2Present,
                    onToggle = { coach2Present = !coach2Present },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                onStartTraining(
                    ActiveTrainingSession(
                        categoryId = category.id,
                        categoryName = category.name,
                        startTime = startTime,
                        startDate = startDate,
                        coach1Name = category.coach1Name,
                        coach1Present = coach1Present,
                        coach2Name = category.coach2Name,
                        coach2Present = coach2Present,
                        isExternalScannerMode = true
                    )
                )
            },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = JudoRed,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .testTag("coach_start_training_btn")
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Démarrer l'entraînement",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
            )
        }
    }
}

/**
 * Dedicated full-screen scanner shown while a training session is running.
 * Uses CameraX + ML Kit barcode scanning for QR codes and keeps a manual entry fallback.
 */
@Composable
fun CoachDedicatedScannerScreen(
    session: ActiveTrainingSession,
    sessionAttendances: List<AttendanceWithMember>,
    totalCategoryCount: Int,
    scanFeedback: CoachScanFeedback?,
    onQrScanned: (String) -> Unit,
    onBackToSession: () -> Unit,
    onDismissFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var manualCode by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("coach_dedicated_scanner_screen")
    ) {
        if (hasCameraPermission) {
            CameraQrScanner(
                modifier = Modifier.fillMaxSize(),
                onQrScanned = onQrScanned
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Accès caméra refusé.\nUtilisez la saisie manuelle ci-dessous.",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Center framing guide
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(230.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(2.dp, JudoRed, RoundedCornerShape(24.dp))
        )

        // Top bar overlay
        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBackToSession) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour à la séance",
                            tint = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = session.categoryName,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Début ${session.startTime}",
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Surface(shape = RoundedCornerShape(10.dp), color = JudoRed) {
                    Text(
                        text = "${sessionAttendances.size}/$totalCategoryCount présents",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Bottom: feedback + manual entry
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        ) {
            ScanFeedbackBanner(feedback = scanFeedback, onDismiss = onDismissFeedback)

            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualCode,
                        onValueChange = { manualCode = it },
                        placeholder = { Text("Saisie manuelle du code…") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = JudoRed
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            if (manualCode.isNotBlank()) {
                                onQrScanned(manualCode.trim())
                                manualCode = ""
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = JudoRed, contentColor = Color.White),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("Valider", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanFeedbackBanner(
    feedback: CoachScanFeedback?,
    onDismiss: () -> Unit
) {
    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(if (feedback is CoachScanFeedback.WrongCategory) 3500 else 1200)
            onDismiss()
        }
    }

    if (feedback == null) return

    val bannerColor: Color
    val bannerIcon: androidx.compose.ui.graphics.vector.ImageVector
    val bannerTitle: String
    val bannerSubtitle: String

    when (feedback) {
        is CoachScanFeedback.Success -> {
            bannerColor = JudoSuccess
            bannerIcon = Icons.Default.Check
            bannerTitle = feedback.member.fullName
            bannerSubtitle = if (feedback.isCotisationUpToDate) "Présent • à jour ✓" else "Présent • cotisation en retard ⚠️"
        }
        is CoachScanFeedback.AlreadyChecked -> {
            bannerColor = Color(0xFF1976D2)
            bannerIcon = Icons.Default.Check
            bannerTitle = feedback.member.fullName
            bannerSubtitle = "Déjà pointé à ${feedback.timeString}"
        }
        is CoachScanFeedback.WrongCategory -> {
            bannerColor = Color(0xFFE65100)
            bannerIcon = Icons.Default.Warning
            bannerTitle = feedback.member.fullName
            bannerSubtitle = buildString {
                append("N'appartient pas à cette catégorie")
                if (feedback.memberGroupLabel.isNotBlank()) append(" (${feedback.memberGroupLabel})")
                append(" — pointage refusé")
            }
        }
        is CoachScanFeedback.Invalid -> {
            bannerColor = Color(0xFFD32F2F)
            bannerIcon = Icons.Default.Error
            bannerTitle = "QR non reconnu"
            bannerSubtitle = feedback.message.ifBlank { "Carte inconnue du club" }
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = bannerColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = bannerIcon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )
            Text(
                text = bannerTitle,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                textAlign = TextAlign.Center
            )
            Text(
                text = bannerSubtitle,
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CameraQrScanner(
    modifier: Modifier = Modifier,
    onQrScanned: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }
    val onQrScannedState = rememberUpdatedState(onQrScanned)
    val lastScanTime = remember { AtomicLong(0L) }
    var lastCode by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            try {
                executor.shutdown()
            } catch (_: Exception) {
            }
            try {
                scanner.close()
            } catch (_: Exception) {
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).also { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        analysis.setAnalyzer(executor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage == null) {
                                imageProxy.close()
                            } else {
                                val image = InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees
                                )
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        val code = barcodes.firstOrNull {
                                            !it.rawValue.isNullOrBlank()
                                        }?.rawValue
                                        if (code != null) {
                                            val now = System.currentTimeMillis()
                                            if (code != lastCode || now - lastScanTime.get() > 1500L) {
                                                lastCode = code
                                                lastScanTime.set(now)
                                                onQrScannedState.value(code)
                                            }
                                        }
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            }
                        }

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                    } catch (_: Exception) {
                        // Camera unavailable — manual entry remains available.
                    }
                }, ContextCompat.getMainExecutor(ctx))
            }
        }
    )
}
