package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.ActiveTrainingSession
import com.example.data.model.CoachScanFeedback
import com.example.data.model.FaceDecision
import com.example.data.model.Member
import com.example.ui.components.FaceCameraPreview
import com.example.ui.theme.JudoSuccess
import com.example.util.face.FaceAnalyzer
import com.example.util.face.FaceEngine
import com.example.util.face.FaceObservation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Pointage facial PLEIN ECRAN, meme pattern que [CoachDedicatedScannerScreen]
 * (scan QR) : camera plein ecran, barre du haut avec bouton retour qui ramene
 * a la page de pointage actuelle. Reconnaissance 100% locale : quand un visage
 * est VALIDE (FaceDecision.Certain), le pointage part immediatement par le
 * pipeline metier existant et le visage reconnu reste affiche 2 s en
 * surimpression du flux video, puis la reconnaissance reprend automatiquement.
 * Aucune fenetre de confirmation, aucune image conservee.
 */
@Composable
fun CoachFacePointageScreen(
    session: ActiveTrainingSession,
    categoryMembers: List<Member>,
    faceGallery: Map<Long, List<FloatArray>>,
    onManualCheckInMember: (Member) -> Unit,
    scanFeedback: CoachScanFeedback?,
    onDismissFeedback: () -> Unit,
    onBack: () -> Unit
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

    var cameraGranted by remember { mutableStateOf(false) }
    val faceStatus = remember(faceGallery.size, cameraGranted) {
        FaceEngine.status(context, cameraGranted, faceGallery.size)
    }

    var analyzer by remember { mutableStateOf<FaceAnalyzer?>(null) }
    var observation by remember { mutableStateOf<FaceObservation?>(null) }

    // Snapshot du visage valide affiche 2 s (image recyclee des qu'est remplacee).
    var faceSnapshot by remember { mutableStateOf<Bitmap?>(null) }
    val lastSnapshotRef = remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(faceSnapshot) {
        val previous = lastSnapshotRef.value
        if (previous != null && previous !== faceSnapshot) {
            runCatching { previous.recycle() }
        }
        lastSnapshotRef.value = faceSnapshot
        if (faceSnapshot != null) {
            delay(2000L)
            faceSnapshot = null
        }
    }

    // Halo "visage detecte" sur le cadre central : vert 2 s a chaque detection.
    var faceDetectedGlow by remember { mutableStateOf(false) }
    val faceDetectedNow = observation?.faceDetected == true
    LaunchedEffect(faceDetectedNow) {
        if (faceDetectedNow) {
            faceDetectedGlow = true
            delay(2000L)
            faceDetectedGlow = false
        }
    }
    val glowWidth by animateFloatAsState(
        targetValue = if (faceDetectedGlow) 5f else 2f,
        label = "face_glow_width"
    )
    val glowColor by animateColorAsState(
        targetValue = if (faceDetectedGlow) JudoSuccess else Color.White.copy(alpha = 0.65f),
        label = "face_glow_color"
    )

    LaunchedEffect(faceStatus.ready, faceGallery) {
        analyzer?.close()
        analyzer = null
        observation = null
        if (faceStatus.ready) {
            val embedder = withContext(Dispatchers.Default) { FaceEngine.embedder(context) }
            analyzer = embedder?.let { engine ->
                FaceAnalyzer(
                    embedder = engine,
                    initialGallery = faceGallery,
                    onResult = { result ->
                        observation = result
                        // Certain => pointage immediat ; pendant l'affichage du
                        // snapshot (2 s) aucun nouveau declenchement n'est pris.
                        if (result.decision is FaceDecision.Certain && faceSnapshot == null) {
                            val memberId = (result.decision as FaceDecision.Certain).memberId
                            val member = categoryMembers.firstOrNull { it.id == memberId }
                            if (member != null) {
                                onManualCheckInMember(member)
                                result.snapshot?.let { snap -> faceSnapshot = snap }
                            }
                        }
                    }
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzer?.close()
            analyzer = null
            lastSnapshotRef.value?.let { runCatching { it.recycle() } }
            lastSnapshotRef.value = null
            faceSnapshot = null
        }
    }

    // Retour metier de la presence (SUCCESS / AlreadyChecked / WrongCategory).
    val displayedFeedback = scanFeedback
    LaunchedEffect(displayedFeedback) {
        if (displayedFeedback is CoachScanFeedback.Invalid) {
            onDismissFeedback()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("coach_face_pointage_screen")
    ) {
        FaceCameraPreview(
            modifier = Modifier
                .fillMaxSize()
                .testTag("face_camera_preview_fullscreen"),
            analyzer = analyzer,
            onPermissionChanged = { cameraGranted = it }
        )

        // Cadre central 4:3 : la cible pour l'athlete (contour anime a la detection).
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.62f)
                .aspectRatio(3f / 4f)
                .testTag("face_framing_guide")
                .clip(RoundedCornerShape(24.dp))
                .border(width = glowWidth.dp, color = glowColor, shape = RoundedCornerShape(24.dp))
        )

        // Snapshot 2 s du visage reconnu en surimpression du flux video.
        AnimatedVisibility(
            visible = faceSnapshot != null,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(200))
        ) {
            val snap = faceSnapshot
            if (snap != null && !snap.isRecycled) {
                Image(
                    bitmap = snap.asImageBitmap(),
                    contentDescription = "Visage reconnu",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { scaleX = -1f }
                )
            }
        }

        // Barre du haut : bouton retour -> page de pointage actuelle.
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
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("face_fullscreen_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour au pointage",
                        tint = Color.White
                    )
                }
                Column {
                    Text(
                        text = "Pointage par le visage",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${session.categoryName} • Reconnaissance ${
                            if (faceStatus.ready) "active" else "indisponible"
                        }",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Spacer(Modifier.weight(1f))

                // Bascule vers le petit ecran integre (la page de pointage).
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("face_exit_fullscreen_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Revenir au petit écran",
                        tint = Color.White
                    )
                }
            }
        }

        // Retour metier : carte grande et lisible, disparue automatiquement au
        // bout de 1,5 s — le scan continue en dessous sans user-input.
        LaunchedEffect(displayedFeedback) {
            val isReal = displayedFeedback != null && displayedFeedback !is CoachScanFeedback.Invalid
            if (isReal) {
                delay(1500L)
                onDismissFeedback()
            }
        }

        // Barre d'indice en bas du cadre (remplace par la barre metier si active).
        Surface(
            color = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            Text(
                text = when {
                    faceSnapshot != null -> ""
                    displayedFeedback != null && displayedFeedback !is CoachScanFeedback.Invalid -> ""
                    !faceStatus.ready -> faceStatus.reason
                    else -> "Présentez votre visage au centre du cadre"
                },
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        // Carte de retour metier au-dessus de la barre d'indice, fade + scale.
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = displayedFeedback != null && displayedFeedback !is CoachScanFeedback.Invalid,
                enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.85f),
                exit = fadeOut(tween(180))
            ) {
                FeedbackCard(
                    feedback = displayedFeedback,
                    modifier = Modifier.padding(bottom = 72.dp)
                )
            }
        }
    }
}

/**
 * Carte de retour metier PLEIN ECRAN : grande, lisible de loin, description
 * personnalisee (pointe / deja pointe / categorie refusee) avec pastille de
 * couleur. Affichee 1,5 s maximum, jamais bloquante, jamais modale.
 */
@Composable
private fun FeedbackCard(
    feedback: CoachScanFeedback?,
    modifier: Modifier = Modifier
) {
    val (icon, title, detail, tint) = when (feedback) {
        is CoachScanFeedback.Success -> Tuple4(
            Icons.Default.CheckCircle,
            feedback.member.fullName,
            "Pointé — ${feedback.timeString}",
            JudoSuccess
        )
        is CoachScanFeedback.AlreadyChecked -> Tuple4(
            Icons.Default.CheckCircle,
            feedback.member.fullName,
            "Déjà pointé — ${feedback.timeString}",
            Color(0xFF4FC3F7)
        )
        is CoachScanFeedback.WrongCategory -> Tuple4(
            Icons.Default.Warning,
            feedback.member.fullName,
            "Catégorie non autorisée dans cette séance",
            Color(0xFFFF6E00)
        )
        else -> return
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = 0.82f),
        modifier = modifier
            .padding(horizontal = 24.dp)
            .testTag("coach_face_feedback_card")
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
            }
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = detail,
                    color = tint,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
            }
        }
    }
}

/** Petit data holder pour les trois socio-parametres de la carte de retour. */
private data class Tuple4(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val detail: String,
    val tint: Color
)

