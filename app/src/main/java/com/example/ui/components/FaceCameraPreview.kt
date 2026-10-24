package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Camera frontale pour le mode Visage : apercu + analyse optionnelle.
 *
 * L'analyse passe par [FaceAnalyzer] (7 fps max, KEEP_ONLY_LATEST). Aucune frame n'est
 * ecrite sur disque. La camera et l'analyseur sont liberes des que le composant quitte la
 * composition.
 *
 * IMPORTANT : l'analyzer peut etre recree par l'ecran hote (rechargement de la galerie,
 * changement de mode...) sans jamais detruire le cas d'usage camera. L'instance est donc
 * rebranchee a chaque recomposition (`update`) : sans cela, l'ecran ferme l'ancien
 * analyzer alors qu'il reste attache a la liaison camera et toutes les frames echouent
 * silencieusement (visage jamais reconnu).
 */
@Composable
fun FaceCameraPreview(
    modifier: Modifier = Modifier,
    analyzer: ImageAnalysis.Analyzer? = null,
    onPermissionChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val analysisExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val analysisRef = remember { mutableStateOf<ImageAnalysis?>(null) }
    // ImageAnalysis n'expose pas de getter analyzer : on suit l'instance attachee soi-meme.
    val attachedRef = remember { mutableStateOf<ImageAnalysis.Analyzer?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        onPermissionChanged(granted)
    }

    LaunchedEffect(Unit) {
        onPermissionChanged(hasPermission)
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    if (!hasPermission) {
        Column(
            modifier = modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.NoPhotography,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Caméra non autorisée - utilisez la douchette ou le pointage manuel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).also { previewView ->
                previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    try {
                        val provider = future.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        // Cas d'usage cree UNE fois ; seuls les analyzers passent a update.
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setTargetResolution(Size(640, 480))
                            .build()
                            .also { analysisRef.value = it }

                        provider.unbindAll()
                        // Lier preview + analyse en un seul appel (recommandation CameraX) :
                        // evite la reconfiguration intermediaire passee a un seul use case.
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            preview,
                            analysis
                        )
                        val current = analyzer
                        if (current != null) {
                            analysis.setAnalyzer(analysisExecutor, current)
                            attachedRef.value = current
                        }
                    } catch (_: Exception) {
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: android.view.View) = Unit
                    override fun onViewDetachedFromWindow(v: android.view.View) {
                        runCatching { ProcessCameraProvider.getInstance(ctx).get().unbindAll() }
                    }
                })
            }
        },
        update = {
            // Rebranche le nouvel analyzer sans toucher au use case camera : l'ecran hote
            // recree son FaceAnalyzer (galerie rechargee, remise a zero) et ferme l'ancien
            // apres la recomposition — l'instance precedente ne doit plus recevoir de frames.
            val analysis = analysisRef.value ?: return@AndroidView
            if (attachedRef.value !== analyzer) {
                if (analyzer == null) {
                    analysis.clearAnalyzer()
                } else {
                    analysis.setAnalyzer(analysisExecutor, analyzer)
                }
                attachedRef.value = analyzer
            }
        }
    )
}
