
package com.example.ui.screens
 
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActiveTrainingSession
import com.example.data.model.AttendanceWithMember
import com.example.data.model.Belt
import com.example.data.model.CoachScanFeedback
import com.example.data.model.FaceDecision
import com.example.data.model.Member
import com.example.data.model.PointageMode
import com.example.ui.components.AbsentSmsDialog
import com.example.ui.components.FaceCameraPreview
import com.example.ui.components.JudokaAvatar
import com.example.ui.theme.JudoRed
import com.example.ui.theme.JudoSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.util.DouchetteReceiver
import com.example.util.face.FaceAnalyzer
import com.example.util.face.FaceEngine
import com.example.util.face.FaceObservation
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
 
/**
 * Screen 3 — Pointage (Check-in during training)
 * Essential stats: presents, absents, attendance rate.
 * Big button: "Scanner un judoka" if camera mode.
 * Indicator: "Douchette prête" in external barcode mode.
 * Auto-handled continuous barcode input in douchette mode.
 * Real-time instant visual feedback banner:
 *   - Green: Present & cotisation up-to-date
 *   - Orange: Present but cotisation late
 *   - Red: Invalid QR or unknown judoka
 * Manual check-in button to mark a judoka present manually.
 * Recent check-ins list.
 * Finish session button.
 */
/**
 * Puce de selection de mode de pointage : remplie quand active, contournee sinon.
 * Cible tactile 52 dp, legende icone + texte (convention Material : jamais icône seule).
 */
@Composable
private fun PointageModeChip(
    label: String,
    icon: ImageVector,
    active: Boolean,
    activeColor: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (active) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = activeColor,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = modifier
                .height(52.dp)
                .testTag(testTag)
        ) {
            ModeChipContent(label, icon, contentColor = Color.White, active = true)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = modifier
                .height(52.dp)
                .testTag(testTag)
        ) {
            ModeChipContent(
                label,
                icon,
                contentColor = MaterialTheme.colorScheme.onSurface,
                active = false
            )
        }
    }
}

@Composable
private fun ModeChipContent(
    label: String,
    icon: ImageVector,
    contentColor: Color,
    active: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        )
        if (active) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Sélectionné",
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun CoachSessionPointageScreen(
    session: ActiveTrainingSession,
    categoryMembers: List<Member>,
    sessionAttendances: List<AttendanceWithMember>,
    scanFeedback: CoachScanFeedback?,
    onOpenScanner: () -> Unit,
    onManualCheckInMember: (Member) -> Unit,
    onFinishSession: () -> Unit,
    onDismissFeedback: () -> Unit,
    /**
     * Appelé à chaque scan (caméra ou douchette) avec UNIQUEMENT le numéro "N12345"
     * extrait du QR code, ou null si le QR ne contient pas de numéro N valide.
     * Le ViewModel retrouve ensuite le judoka avec ce numéro.
     */
    onNumeroScanned: (String?) -> Unit = {},
    faceGallery: Map<Long, List<FloatArray>> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val totalCount = categoryMembers.size
    val presentCount = sessionAttendances.size
    val absentCount = (totalCount - presentCount).coerceAtLeast(0)
    val attendanceRate = if (totalCount > 0) ((presentCount.toDouble() / totalCount.toDouble()) * 100).toInt() else 0
 
    var isManualCheckInDialogOpen by remember { mutableStateOf(false) }
    var statDetailMode by remember { mutableStateOf<StatDetailMode?>(null) }

    // Mode de pointage de la seance : le VISAGE est le mode par defaut. La douchette et la
    // camera QR restent accessibles en un tap via les boutons de mode.
    val context = LocalContext.current
    var pointageMode by remember(session.categoryId, session.startTimestamp) {
        mutableStateOf(PointageMode.FACE)
    }
    val isFaceMode = pointageMode == PointageMode.FACE
    // La permission est remontee par l'apercu camera : le moteur n'est "actif" que si
    // modele + hash valides, permission accordee et galerie du groupe non vide.
    var cameraGranted by remember { mutableStateOf(false) }
    val faceStatus = remember(faceGallery.size, cameraGranted) {
        FaceEngine.status(context, cameraGranted, faceGallery.size)
    }

    // Analyse temps reel : creee uniquement en mode Visage quand le moteur est actif.
    var analyzer by remember { mutableStateOf<FaceAnalyzer?>(null) }
    var observation by remember { mutableStateOf<FaceObservation?>(null) }
    var pendingFaceMember by remember { mutableStateOf<Member?>(null) }

    // Halo "visage detecte" : chaque passage vide->visage allume un anneau vert durant 2 s.
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
        targetValue = if (faceDetectedGlow) 4f else 2f,
        label = "face_glow_width"
    )
    val glowScale by animateFloatAsState(
        targetValue = if (faceDetectedGlow) 1.05f else 1f,
        label = "face_glow_scale"
    )
    val glowColor by animateColorAsState(
        targetValue = if (faceDetectedGlow) JudoSuccess else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
        label = "face_glow_color"
    )

    LaunchedEffect(isFaceMode, faceStatus.ready, faceGallery) {
        analyzer?.close()
        analyzer = null
        observation = null
        if (isFaceMode && faceStatus.ready) {
            val embedder = withContext(Dispatchers.Default) { FaceEngine.embedder(context) }
            analyzer = embedder?.let { engine ->
                FaceAnalyzer(
                    embedder = engine,
                    initialGallery = faceGallery,
                    onResult = { result ->
                        observation = result
                        // autoCheckInEnabled = false : meme "certain", rien n'est pointe
                        // sans confirmation explicite du coach.
                        if (result.decision is FaceDecision.Certain && pendingFaceMember == null) {
                            pendingFaceMember = categoryMembers.firstOrNull {
                                it.id == (result.decision as FaceDecision.Certain).memberId
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
        }
    }
 
    // Résultat réellement affiché à l'écran.
    // - QR invalide : aucun message, on reste sur "Prêt à scanner".
    // - Scan détecté (SUCCESS / AlreadyChecked) : l'athlète apparaît IMMÉDIATEMENT au centre,
    //   à la place de l'animation radar, avec une grande photo de profil, puis la carte
    //   revient sur "Douchette prête".
    // - Catégorie refusée : fenêtre plus longue pour laisser le temps de lire.
    var displayedFeedback by remember { mutableStateOf<CoachScanFeedback?>(null) }
    LaunchedEffect(scanFeedback) {
        when (scanFeedback) {
            null -> displayedFeedback = null
            is CoachScanFeedback.Invalid -> {
                displayedFeedback = null
                onDismissFeedback()
            }
            else -> {
                // Affichage immédiat : aucune attente de radar avant la photo.
                displayedFeedback = scanFeedback
                val holdMs = if (scanFeedback is CoachScanFeedback.WrongCategory) 2500L else 2200L
                delay(holdMs)
                displayedFeedback = null
                onDismissFeedback()
            }
        }
    }
    val shownFeedback = displayedFeedback
 
    val presentMemberIds = remember(sessionAttendances) {
        sessionAttendances.map { it.memberId }.toSet()
    }
 
    // Mode douchette HID : la douchette "tape" le QR comme un clavier puis envoie Entrée.
    val scanBuffer = remember { StringBuilder() }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(pointageMode, isManualCheckInDialogOpen) {
        if (pointageMode == PointageMode.DOUCHETTE && !isManualCheckInDialogOpen) {
            runCatching { focusRequester.requestFocus() }
        }
    }
 
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp)
            .testTag("coach_pointage_screen")
            .then(
                if (pointageMode == PointageMode.DOUCHETTE) {
                    Modifier
                        .focusRequester(focusRequester)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.Enter, Key.NumPadEnter -> {
                                    if (scanBuffer.isNotEmpty()) {
                                        // Le QR de la carte est multi-ligne : la première ligne ne
                                        // porte que le nom du club. On transmet donc le payload tel
                                        // quel (normalisé comme le fait la douchette HID) pour que
                                        // les parseurs de carte y trouvent la ligne du numéro
                                        // ("N: 121"), au lieu de signaler un QR invalide à chaque
                                        // retour à la ligne.
                                        val payload = DouchetteReceiver.normalizePayload(scanBuffer.toString())
                                        scanBuffer.clear()
                                        if (payload.isNotBlank()) onNumeroScanned(payload)
                                    }
                                    true
                                }
                                else -> {
                                    val cp = event.utf16CodePoint
                                    if (cp > 0) {
                                        scanBuffer.append(cp.toChar())
                                        true
                                    } else false
                                }
                            }
                        }
                } else Modifier
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Header: Category & Start Time
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = session.categoryName,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black
                        )
                    )
                    Text(
                        text = "Début : ${session.startTime} • Tatami Seddouk",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
 
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = JudoRed.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, JudoRed.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(JudoRed)
                        )
                        Text(
                            text = "Séance en cours",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = JudoRed
                            )
                        )
                    }
                }
            }
        }
 
        // Essential Stats: Présents | Absents | Taux (clickables → liste des athlètes)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatPill(
                title = "Présents",
                value = "$presentCount",
                color = JudoSuccess,
                modifier = Modifier
                    .weight(1f)
                    .testTag("coach_stat_present"),
                onClick = { statDetailMode = StatDetailMode.PRESENT }
            )
            StatPill(
                title = "Absents",
                value = "$absentCount",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .weight(1f)
                    .testTag("coach_stat_absent"),
                onClick = { statDetailMode = StatDetailMode.ABSENT }
            )
            StatPill(
                title = "Taux",
                value = "$attendanceRate%",
                color = JudoRed,
                modifier = Modifier
                    .weight(1f)
                    .testTag("coach_stat_rate"),
                onClick = { statDetailMode = StatDetailMode.ALL }
            )
        }
 
        // 1. & 3. Central Scan Zone with Concentric Red Ripples & Real-Time Feedback
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(
                1.dp,
                when (shownFeedback) {
                    is CoachScanFeedback.Success -> JudoSuccess.copy(alpha = 0.6f)
                    is CoachScanFeedback.AlreadyChecked -> Color(0xFF1976D2).copy(alpha = 0.5f)
                    is CoachScanFeedback.Invalid -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    is CoachScanFeedback.WrongCategory -> Color(0xFFE65100).copy(alpha = 0.6f)
                    null -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("coach_center_scanner_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Radar Animation
                ConcentricRipplesRadar(
                    isScanning = shownFeedback == null && !isFaceMode,
                    accentColor = if (isFaceMode) {
                        Color(0xFF1976D2)
                    } else when (shownFeedback) {
                        is CoachScanFeedback.Success -> JudoSuccess
                        is CoachScanFeedback.AlreadyChecked -> Color(0xFF1976D2)
                        is CoachScanFeedback.Invalid -> MaterialTheme.colorScheme.error
                        is CoachScanFeedback.WrongCategory -> Color(0xFFE65100)
                        null -> JudoRed
                    }
                ) {
                    when (shownFeedback) {
                        is CoachScanFeedback.Success -> {
                            // Confirmation au centre : grande photo de profil cerclée de vert.
                            Box {
                                JudokaAvatar(
                                    name = shownFeedback.member.fullName,
                                    belt = shownFeedback.member.belt,
                                    photoUrl = shownFeedback.member.photoUrl,
                                    sizeDp = 128,
                                    borderColor = JudoSuccess,
                                    borderWidth = 4.dp
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(JudoSuccess),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Validé",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        is CoachScanFeedback.AlreadyChecked -> {
                            // Même confirmation, anneau bleu : l'athlète était déjà pointé.
                            Box {
                                JudokaAvatar(
                                    name = shownFeedback.member.fullName,
                                    belt = shownFeedback.member.belt,
                                    photoUrl = shownFeedback.member.photoUrl,
                                    sizeDp = 128,
                                    borderColor = Color(0xFF1976D2),
                                    borderWidth = 4.dp
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1976D2)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Déjà présent",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        is CoachScanFeedback.WrongCategory -> {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE65100)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Mauvaise catégorie",
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        is CoachScanFeedback.Invalid -> {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Erreur",
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        null -> {
                            if (isFaceMode) {
                                // Mode Visage : apercu camera (PiP) avec halo qui s'allume
                                // 2 s a chaque detection de visage. Aucune frame analyse
                                // sans modele installe.
                                Box(
                                    modifier = Modifier
                                        .size(156.dp)
                                        .graphicsLayer {
                                            scaleX = glowScale
                                            scaleY = glowScale
                                        }
                                        .border(
                                            width = glowWidth.dp,
                                            color = glowColor,
                                            shape = CircleShape
                                        )
                                        .clip(CircleShape)
                                ) {
                                    FaceCameraPreview(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .testTag("face_camera_preview"),
                                        analyzer = analyzer,
                                        onPermissionChanged = { cameraGranted = it }
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(JudoRed),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = "QR Code",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }
                    }
                }
 
                // Text status / Athlete feedback
                AnimatedContent(
                    targetState = shownFeedback,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(150))
                    },
                    label = "scan_feedback_content"
                ) { feedback ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        when (feedback) {
                            is CoachScanFeedback.Success -> {
                                Text(
                                    text = feedback.member.fullName,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 20.sp
                                    ),
                                    color = JudoSuccess,
                                    textAlign = TextAlign.Center
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (feedback.member.beltName.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                        ) {
                                            Text(
                                                text = "🥋 ${feedback.member.beltName}",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (feedback.isCotisationUpToDate) JudoSuccess.copy(alpha = 0.15f) else Color(0xFFFF9800).copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = if (feedback.isCotisationUpToDate) "Présent • À jour ✓" else "Présent • Cotisation en retard ⚠️",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (feedback.isCotisationUpToDate) JudoSuccess else Color(0xFFE65100)
                                            ),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Pointé à ${feedback.timeString}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                            is CoachScanFeedback.AlreadyChecked -> {
                                Text(
                                    text = feedback.member.fullName,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 19.sp
                                    ),
                                    color = Color(0xFF1976D2),
                                    textAlign = TextAlign.Center
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF1976D2).copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "Athlète déjà marqué présent (à ${feedback.timeString})",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1976D2)
                                        ),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            is CoachScanFeedback.WrongCategory -> {
                                Text(
                                    text = feedback.member.fullName,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 19.sp
                                    ),
                                    color = Color(0xFFE65100),
                                    textAlign = TextAlign.Center
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFE65100).copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, Color(0xFFE65100).copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "N'appartient pas à cette catégorie",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFBF360C)
                                        ),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                                Text(
                                    text = "Athlète : ${feedback.memberGroupLabel}" +
                                        if (feedback.sessionCategoryName.isNotBlank()) {
                                            " • Séance : ${feedback.sessionCategoryName}"
                                        } else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Pointage refusé",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            is CoachScanFeedback.Invalid -> {
                                // Aucun message : un QR invalide est ignoré silencieusement.
                            }
                            null -> {
                                if (isFaceMode) {
                                    Text(
                                        text = "Mode visage",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = when {
                                            !faceStatus.ready -> faceStatus.reason
                                            observation?.hint != null -> observation!!.hint!!
                                            else -> "Présentez votre visage à la caméra"
                                        },
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                    Text(
                                        text = "Aucune photo n'est conservée : seules des empreintes chiffrées restent sur cette tablette.",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                        ),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                } else {
                                    Text(
                                        text = if (pointageMode == PointageMode.DOUCHETTE) "Douchette prête" else "Prêt à scanner",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = if (pointageMode == PointageMode.DOUCHETTE) {
                                            "Scannez le QR code de l’athlète avec la douchette HID (Bluetooth ou USB)"
                                        } else {
                                            "Présentez le QR code de l’athlète devant la caméra"
                                        },
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
 
        // Actions Row: selecteur de mode (Visage par defaut | Camera | Douchette) + manuel.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PointageModeChip(
                label = "Visage",
                icon = Icons.Default.Face,
                active = isFaceMode,
                activeColor = Color(0xFF1976D2),
                testTag = "coach_face_mode_btn",
                modifier = Modifier.weight(1f),
                onClick = { pointageMode = PointageMode.FACE }
            )
            PointageModeChip(
                label = "Caméra",
                icon = Icons.Default.PhotoCamera,
                active = false,
                activeColor = JudoRed,
                testTag = "coach_scan_judoka_btn",
                modifier = Modifier.weight(1f),
                onClick = onOpenScanner
            )
            PointageModeChip(
                label = "Douchette",
                icon = Icons.Default.QrCodeScanner,
                active = pointageMode == PointageMode.DOUCHETTE,
                activeColor = Color(0xFF6A4AA0),
                testTag = "coach_douchette_mode_btn",
                modifier = Modifier.weight(1f),
                onClick = { pointageMode = PointageMode.DOUCHETTE }
            )
            PointageModeChip(
                label = "Manuel",
                icon = Icons.Default.PersonAdd,
                active = false,
                activeColor = MaterialTheme.colorScheme.onSurfaceVariant,
                testTag = "coach_manual_checkin_btn",
                modifier = Modifier.weight(1f),
                onClick = { isManualCheckInDialogOpen = true }
            )
        }
 
        // Recent Scans / Check-ins — liste compacte : nombre d'athletes visibles maximise.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Derniers pointages",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = JudoSuccess.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "${sessionAttendances.size}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = JudoSuccess
                    ),
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                        .testTag("coach_pointage_count")
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (sessionAttendances.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "En attente des premiers judokas sur le tatami...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            } else {
                items(sessionAttendances, key = { it.attendanceId }) { attendance ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                JudokaAvatar(
                                    name = attendance.fullName,
                                    belt = attendance.belt,
                                    photoUrl = attendance.photoUrl,
                                    sizeDp = 44
                                )
                                Column {
                                    Text(
                                        text = attendance.fullName,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = "Pointé à ${attendance.timeString}",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = JudoSuccess.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Présent ✓",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = JudoSuccess,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Finish Session Button
        OutlinedButton(
            onClick = onFinishSession,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("coach_finish_session_btn")
        ) {
            Icon(
                imageVector = Icons.Default.StopCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Terminer la séance",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
 
    // Modal Dialog: Pointage Manuel (Judoka absent du scan ou carte oubliée)
    if (isManualCheckInDialogOpen) {
        var manualSearchQuery by remember { mutableStateOf("") }
        val absentMembers = remember(categoryMembers, presentMemberIds, manualSearchQuery) {
            categoryMembers
                .filter { it.id !in presentMemberIds }
                .filter {
                    manualSearchQuery.isBlank() || it.fullName.contains(manualSearchQuery, ignoreCase = true)
                }
        }
 
        AlertDialog(
            onDismissRequest = { isManualCheckInDialogOpen = false },
            title = {
                Text(
                    text = "Pointage manuel",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = manualSearchQuery,
                        onValueChange = { manualSearchQuery = it },
                        placeholder = { Text("Rechercher judoka...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
 
                    Text(
                        text = "Judokas non encore pointés (${absentMembers.size}) :",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
 
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (absentMembers.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Tous les judokas sont déjà pointés !",
                                        style = MaterialTheme.typography.bodySmall.copy(color = JudoSuccess)
                                    )
                                }
                            }
                        } else {
                            items(absentMembers, key = { it.id }) { member ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            onManualCheckInMember(member)
                                            isManualCheckInDialogOpen = false
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        JudokaAvatar(name = member.fullName, belt = member.belt, photoUrl = member.photoUrl, sizeDp = 30)
                                        Column {
                                            Text(member.fullName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            Text("Ceinture ${member.beltName.lowercase()}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Text("Pointer +", fontWeight = FontWeight.Bold, color = JudoRed, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isManualCheckInDialogOpen = false }) {
                    Text("Annuler", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
 
    if (statDetailMode != null) {
        StatAthletesDialog(
            mode = statDetailMode!!,
            present = sessionAttendances,
            absent = categoryMembers.filter { it.id !in presentMemberIds },
            onDismiss = { statDetailMode = null }
        )
    }

    // Pointage facial : toujours confirme par le coach (autoCheckInEnabled = false).
    val faceCandidate = pendingFaceMember
    if (faceCandidate != null) {
        AlertDialog(
            onDismissRequest = {
                pendingFaceMember = null
                analyzer?.reset()
            },
            title = { Text("Visage reconnu") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = faceCandidate.fullName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Confirmer la présence de cet athlète ? Aucun pointage n'est enregistré automatiquement.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onManualCheckInMember(faceCandidate)
                        pendingFaceMember = null
                        analyzer?.reset()
                    },
                    modifier = Modifier.testTag("face_confirm_checkin_btn")
                ) {
                    Text("Confirmer", color = JudoSuccess)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingFaceMember = null
                        analyzer?.reset()
                    },
                    modifier = Modifier.testTag("face_reject_checkin_btn")
                ) {
                    Text("Ce n'est pas lui", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}
 
/**
 * Screen 4 — Fin de séance (Récapitulatif officiel)
 * Displays:
 * - Total presents and total absents
 * - Start time and end time
 * - Present coaches
 * - Fast list of presents and absents (with tabs)
 * - "Valider et enregistrer" button
 */
@Composable
fun CoachSessionSummaryScreen(
    session: ActiveTrainingSession,
    categoryMembers: List<Member>,
    sessionAttendances: List<AttendanceWithMember>,
onValidateAndSave: (String) -> Unit,
onCancelSession: () -> Unit = {},
modifier: Modifier = Modifier,
    clubName: String = ""
) {
    val totalCount = categoryMembers.size
    val presentCount = sessionAttendances.size
    val absentCount = (totalCount - presentCount).coerceAtLeast(0)
    val attendanceRate = if (totalCount > 0) ((presentCount.toDouble() / totalCount.toDouble()) * 100).toInt() else 0
 
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var endTimeString by remember { mutableStateOf("") }
    var showSmsDialog by remember { mutableStateOf(false) }
 
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm", Locale.FRENCH)
        endTimeString = sdf.format(Date())
    }
 
    val presentMemberIds = remember(sessionAttendances) {
        sessionAttendances.map { it.memberId }.toSet()
    }
 
    val presentMembers = remember(sessionAttendances) {
        sessionAttendances.map { it.fullName }
    }
 
    // Absent athletes = members of THIS session's category who were not scanned as present.
    val absentMemberObjects = remember(categoryMembers, presentMemberIds) {
        categoryMembers.filter { it.id !in presentMemberIds }
    }
 
    val absentMembers = remember(absentMemberObjects) {
        absentMemberObjects.map { it.fullName }
    }
 
    if (showSmsDialog) {
        AbsentSmsDialog(
            absentMembers = absentMemberObjects,
            sessionDate = session.startDate,
            sessionStartTime = session.startTime,
            clubName = clubName,
            onDismiss = { showSmsDialog = false }
        )
    }
 
    val presentCoaches = remember(session) {
        val list = mutableListOf<String>()
        if (session.coach1Present) list.add(session.coach1Name)
        if (session.coach2Present) list.add(session.coach2Name)
        if (list.isEmpty()) list.add("Aucun coach consigné")
        list.joinToString(" • ")
    }
 
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
            .testTag("coach_session_summary_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(JudoSuccess.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = JudoSuccess,
                        modifier = Modifier.size(32.dp)
                    )
                }
 
                Spacer(modifier = Modifier.height(12.dp))
 
                Text(
                    text = "Séance terminée",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black
                    )
                )
 
                Text(
                    text = "Récapitulatif officiel • ${session.categoryName}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
 
                Spacer(modifier = Modifier.height(16.dp))
 
                // Summary Stats Card
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Coachs présents",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                            Text(
                                text = presentCoaches,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
 
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Horaire",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                            Text(
                                text = "${session.startTime} - $endTimeString",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
 
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        )
 
                        // 3 Stats Pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatPill(
                                title = "Présents",
                                value = "$presentCount",
                                color = JudoSuccess,
                                modifier = Modifier.weight(1f)
                            )
                            StatPill(
                                title = "Absents",
                                value = "$absentCount",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            StatPill(
                                title = "Taux",
                                value = "$attendanceRate%",
                                color = JudoRed,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
 
                Spacer(modifier = Modifier.height(14.dp))
 
                // Tabs for Presents and Absents list
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    contentColor = JudoRed,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text("Présents ($presentCount)", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text("Absents ($absentCount)", fontWeight = FontWeight.Bold) }
                    )
                }
 
                Spacer(modifier = Modifier.height(8.dp))
 
                // Scrollable List of presents / absents
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val currentList = if (selectedTabIndex == 0) presentMembers else absentMembers
                    if (currentList.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (selectedTabIndex == 0) "Aucun judoka présent" else "Aucun judoka absent !",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                        }
                    } else {
                        items(currentList) { name ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        text = if (selectedTabIndex == 0) "Présent ✓" else "Absent",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (selectedTabIndex == 0) JudoSuccess else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
 
            // Bottom actions: prepare the SMS to the absent athletes' guardians, then validate.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (absentCount > 0) {
                    OutlinedButton(
                        onClick = { showSmsDialog = true },
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.5.dp, JudoRed),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JudoRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("coach_absent_sms_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Message,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SMS aux parents des absents ($absentCount)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
 
OutlinedButton(
    onClick = onCancelSession,
    shape = RoundedCornerShape(18.dp),
    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
    modifier = Modifier.fillMaxWidth().height(54.dp).testTag("coach_cancel_summary_btn")
) {
    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(modifier = Modifier.width(8.dp))
    Text("Annuler la séance", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
}
Button(
    onClick = { onValidateAndSave(endTimeString) },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JudoRed,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .testTag("coach_validate_summary_btn")
                ) {
                    Text(
                        text = "Valider et enregistrer",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                    )
                }
            }
        }
    }
}
 
@Composable
fun ConcentricRipplesRadar(
    isScanning: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = JudoRed,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_concentric_transition")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_progress"
    )
 
    Box(
        modifier = modifier.size(175.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val minDim = size.minDimension
            val baseRadius = 38.dp.toPx()
            val maxRadius = (minDim / 2f) - 6.dp.toPx()
            val spread = maxRadius - baseRadius
 
            if (isScanning) {
                // 3 concentric expanding rings with subtle glow
                for (i in 0 until 3) {
                    val ringPhase = (progress + (i / 3f)) % 1f
                    val currentRadius = baseRadius + spread * ringPhase
                    val ringAlpha = ((1f - ringPhase) * 0.75f).coerceIn(0f, 1f)
 
                    // Outer soft glow
                    drawCircle(
                        color = accentColor.copy(alpha = ringAlpha * 0.25f),
                        radius = currentRadius,
                        center = center,
                        style = Stroke(width = 4.dp.toPx())
                    )
                    // Sharp fine ring
                    drawCircle(
                        color = accentColor.copy(alpha = ringAlpha),
                        radius = currentRadius,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
 
            // Central circle backdrop & border
            drawCircle(
                color = accentColor.copy(alpha = 0.12f),
                radius = baseRadius + 3.dp.toPx(),
                center = center
            )
            drawCircle(
                color = accentColor.copy(alpha = 0.22f),
                radius = baseRadius,
                center = center
            )
            drawCircle(
                color = accentColor,
                radius = baseRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }
 
        // Inner emblem/icon
        content()
    }
}
 
@Composable
private fun StatPill(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    color = color
                )
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
 
private enum class StatDetailMode { PRESENT, ABSENT, ALL }
 
@Composable
private fun StatAthletesDialog(
    mode: StatDetailMode,
    present: List<AttendanceWithMember>,
    absent: List<Member>,
    onDismiss: () -> Unit
) {
    val title = when (mode) {
        StatDetailMode.PRESENT -> "Présents (${present.size})"
        StatDetailMode.ABSENT -> "Absents (${absent.size})"
        StatDetailMode.ALL -> "Athlètes (${present.size + absent.size})"
    }
    val isEmpty = when (mode) {
        StatDetailMode.PRESENT -> present.isEmpty()
        StatDetailMode.ABSENT -> absent.isEmpty()
        StatDetailMode.ALL -> present.isEmpty() && absent.isEmpty()
    }
 
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
            )
        },
        text = {
            if (isEmpty) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aucun athlète à afficher",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (mode != StatDetailMode.ABSENT) {
                        items(present, key = { "present_${it.attendanceId}" }) { attendance ->
                            AthleteStatRow(
                                name = attendance.fullName,
                                belt = attendance.belt,
                                photoUrl = attendance.photoUrl,
                                subtitle = "Présent • ${attendance.timeString}",
                                present = true
                            )
                        }
                    }
                    if (mode != StatDetailMode.PRESENT) {
                        items(absent, key = { "absent_${it.id}" }) { member ->
                            AthleteStatRow(
                                name = member.fullName,
                                belt = member.belt,
                                photoUrl = member.photoUrl,
                                subtitle = member.group.label,
                                present = false
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )
}
 
@Composable
private fun AthleteStatRow(
    name: String,
    belt: Belt,
    photoUrl: String,
    subtitle: String,
    present: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        JudokaAvatar(
            name = name,
            belt = belt,
            photoUrl = photoUrl,
            sizeDp = 38
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (present) JudoSuccess.copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        ) {
            Text(
                text = if (present) "Présent ✓" else "Absent",
                style = MaterialTheme.typography.labelSmall.copy(
                     color = if (present) JudoSuccess else MaterialTheme.colorScheme.error,
                     fontWeight = FontWeight.Bold
                 ),
                 modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
             )
         }
     }
}

 