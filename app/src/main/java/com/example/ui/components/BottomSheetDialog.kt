package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Fenetre flottante ancree en BAS de l'ecran, pleine largeur (feuille).
 *
 * Remplace les boites de dialogue centrees pour les formulaires longs : l'admin garde le
 * contexte visible au-dessus, le contenu peut grandir jusqu'a [heightFraction] de l'ecran
 * puis defiler, et le fond assombri reste cliquable pour fermer.
 */
@Composable
fun BottomSheetDialog(
    onDismiss: () -> Unit,
    heightFraction: Float = 0.94f,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(heightFraction.coerceIn(0.3f, 1f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                content()
            }
        }
    }
}
