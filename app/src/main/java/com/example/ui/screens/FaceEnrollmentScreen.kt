package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.os.SystemClock
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Member
import com.example.ui.components.FaceCameraPreview
import com.example.ui.theme.JudoSuccess
import com.example.util.face.FaceCaptureAnalyzer
import com.example.util.face.FaceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TARGET_SAMPLES = 5

/**
 * Enrolement du visage d'un athlete : PLEIN ECRAN, camera frontale, 5 echantillons stricts
 * (grand, de face, net, pose tolerante 15 degres) pris a ~700 ms d'intervalle et
 * enregistres localement CHIFFRES. Aucune image de visage n'est conservee sur disque :
 * seul l'embedding l'est.
 *
 * Consentement explicite obligatoire avant toute capture (exigence biometrique).
 */
@Composable
fun FaceEnrollmentScreen(
    member: Member,
    savedCount: Int,
    hasEngine: Boolean,
    onSaveSamples: (List<Pair<FloatArray, Float>>, (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val context = LocalContext.current
        val samples = remember { mutableListOf<Pair<FloatArray, Float>>() }
        var analyzer by remember { mutableStateOf<FaceCaptureAnalyzer?>(null) }
        var hint by remember { mutableStateOf("") }
        var lastCaptureAt by remember { mutableStateOf(0L) }
        var saving by remember { mutableStateOf(false) }
        var saved by remember { mutableStateOf<Boolean?>(null) }

        DisposableEffect(Unit) {
            onDispose { analyzer?.close() }
        }

        LaunchedEffect(hasEngine) {
            analyzer?.close()
            analyzer = null
            if (hasEngine) {
                val embedder = withContext(Dispatchers.Default) { FaceEngine.embedder(context) }
                analyzer = embedder?.let { engine ->
                    FaceCaptureAnalyzer(
                        embedder = engine,
                        onCapture = { capture ->
                            val now = SystemClock.elapsedRealtime()
                            // ~700 ms d'ecart : assez de variation temporelle (lumiere/pose
                            // legere) pour des empreintes pas quasi-identiques, assez vite
                            // pour que l'athlete ne perde pas patience.
                            if (samples.size < TARGET_SAMPLES && now - lastCaptureAt >= 700L) {
                                lastCaptureAt = now
                                samples.add(capture.embedding to capture.quality)
                                hint = "Échantillon ${samples.size}/$TARGET_SAMPLES capturé"
                                if (samples.size >= TARGET_SAMPLES) {
                                    saving = true
                                    val snapshot = samples
                                        .map { (emb, q) -> emb.copyOf() to q }
                                    onSaveSamples(snapshot) { ok ->
                                        saving = false
                                        saved = ok
                                        if (ok) analyzer?.close()
                                    }
                                }
                            }
                        },
                        onHint = { hint = it }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("face_enroll_screen")
        ) {
            if (hasEngine) {
                if (analyzer != null) {
                    FaceCameraPreview(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("face_enroll_preview"),
                        analyzer = analyzer
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("face_enroll_preview"),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("face_enroll_preview")
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .testTag("face_enroll_close_btn")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 28.dp)
            ) {
                Text(
                    text = "Capture du visage",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = member.fullName,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (savedCount > 0) {
                    Text(
                        text = "$savedCount empreinte(s) déjà enregistrée(s) — la capture remplace l'ancienne.",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .padding(horizontal = 24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .testTag("face_enroll_controls")
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Capture du visage de ${member.fullName}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Les images sont jetées, les empreintes restent chiffrées sur cette tablette et ne sont jamais envoyées.",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                when {
                    saving -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            text = "Enregistrement…",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    saved == true -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Face,
                            contentDescription = null,
                            tint = JudoSuccess,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = "Visage enrôlé ($TARGET_SAMPLES échantillons) ✓",
                            color = JudoSuccess,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("face_enroll_done_btn")
                        ) { Text("Terminé", color = Color.White) }
                    }

                    saved == false -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Échec de l'enregistrement local",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        OutlinedButton(
                            onClick = {
                                samples.clear()
                                saved = null
                                hint = ""
                            },
                            modifier = Modifier.testTag("face_enroll_retry_btn")
                        ) { Text("Réessayer") }
                    }

                    else -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(horizontalArrangement = Arrangement.Center) {
                            repeat(TARGET_SAMPLES) { index ->
                                val done = index < samples.size
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 6.dp)
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (done) JudoSuccess else Color.White.copy(alpha = 0.35f)
                                        )
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = when {
                                !hasEngine -> "Moteur de reconnaissance non installé sur cet appareil."
                                else -> hint.ifBlank {
                                    "Présentez le visage en face, lumineux et immobile."
                                }
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
