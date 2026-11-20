package com.example.ui.components

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Member
import com.example.ui.theme.JudoRed
import com.example.ui.theme.JudoSuccess
import com.example.ui.theme.JudoWarning
import com.example.util.AbsentSmsNotifier
import com.example.util.SimSmsSender
import kotlinx.coroutines.launch

/**
 * Builds the native SMS intent for one guardian.
 *
 * We deliberately use `Intent.ACTION_SENDTO` with an `smsto:` URI (and **not** `ACTION_SEND`):
 * it opens the user's messaging application with the recipient and the body prefilled. No SMS
 * provider, API key or `SEND_SMS` permission is involved.
 */
internal object SmsIntentFactory {

    fun buildIntent(phone: String, body: String): Intent =
        Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${AbsentSmsNotifier.toSmsUriNumber(phone)}")
            putExtra("sms_body", body)
        }

    /** True when at least one installed application can handle the intent. */
    fun canHandle(context: Context, intent: Intent): Boolean =
        try {
            intent.resolveActivity(context.packageManager) != null
        } catch (_: Exception) {
            false
        }
}

private enum class SmsStage { RECAP, REVIEW, DIRECT }

/** One live dispatch shown in the direct-send stage, its status set through the SENT broadcast. */
data class DirectSmsLine(
    val key: String,
    val label: String,
    val number: String,
    var state: String,
    var isError: Boolean
)

/**
 * "Envoyer les SMS aux parents des absents" — recap + per-guardian preparation.
 *
 * Flow:
 *  1. RECAP: shows how many athletes are absent, how many have a usable guardian number, how many
 *     messages will be prepared, the recipient list, and the athletes that were excluded (missing /
 *     invalid number, duplicates). The coach confirms or cancels.
 *  2. REVIEW: walks through the messages one by one (individual, personalised messages so no
 *     guardian ever sees another guardian's number). Each "Ouvrir l'application SMS" button hands
 *     the prefilled message to the native SMS app.
 *
 * The dialog never claims a message was *sent*: `ACTION_SENDTO` cannot confirm delivery.
 */
@Composable
fun AbsentSmsDialog(
    absentMembers: List<Member>,
    sessionDate: String,
    sessionStartTime: String? = null,
    clubName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val plan = remember(absentMembers) { AbsentSmsNotifier.buildPlan(absentMembers) }

    var stage by remember { mutableStateOf(SmsStage.RECAP) }
    val preparedMemberIds = remember { mutableStateListOf<Long>() }
    var currentIndex by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Modèle du message : toujours pré-rempli avec le modèle officiel du club,
    // mais personnalisable par le coach (emplacements {NOM} {DATE} {HEURE} {CLUB}).
    var messageTemplate by remember { mutableStateOf(AbsentSmsNotifier.DEFAULT_MESSAGE_TEMPLATE) }
    // Brouillons édités par destinataire dans l'étape de revue (clé = memberId).
    val editableDrafts = remember { mutableStateMapOf<Long, String>() }

    val messageFor: (AbsentSmsNotifier.SmsRecipient) -> String = { recipient ->
        AbsentSmsNotifier.messageFromTemplate(
            template = messageTemplate,
            memberName = recipient.memberName,
            sessionDate = sessionDate,
            sessionStart = sessionStartTime,
            clubName = clubName
        )
    }

    fun openSmsApp(recipient: AbsentSmsNotifier.SmsRecipient, bodyOverride: String? = null) {
        val body = bodyOverride ?: messageFor(recipient)
        val intent = SmsIntentFactory.buildIntent(recipient.normalizedPhone, body)
        if (!SmsIntentFactory.canHandle(context, intent)) {
            // Never log the phone number itself.
            errorMessage = "Aucune application SMS n'est disponible sur cet appareil."
            return
        }
        try {
            context.startActivity(intent)
            errorMessage = null
            if (recipient.memberId !in preparedMemberIds) {
                preparedMemberIds.add(recipient.memberId)
            }
        } catch (_: ActivityNotFoundException) {
            errorMessage = "Aucune application SMS n'est disponible sur cet appareil."
        } catch (e: Exception) {
            errorMessage = "Impossible d'ouvrir l'application SMS."
        }
    }

    val noSmsAppAvailable = remember {
        !SmsIntentFactory.canHandle(
            context,
            SmsIntentFactory.buildIntent("0000000000", "")
        )
    }

    // ---- Envoi direct par la SIM (sans quitter l'application) ----------------------------
    val simCapable = remember { SimSmsSender.isDeviceSmsCapable(context) }
    val directLines = remember { mutableStateListOf<DirectSmsLine>() }
    var awaitingDirectAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val directScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = awaitingDirectAction
        awaitingDirectAction = null
        if (granted) {
            pending?.invoke()
        } else {
            errorMessage = "Autorisation SMS refusée : l'envoi direct n'est pas possible."
            Toast.makeText(context, "Autorisation SMS refusée", Toast.LENGTH_SHORT).show()
        }
    }

    fun ensurePermissionThen(action: () -> Unit) {
        if (SimSmsSender.hasSendSmsPermission(context)) {
            action()
        } else {
            awaitingDirectAction = action
            permissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
    }

    fun updateLine(key: String, state: String, isError: Boolean) {
        val index = directLines.indexOfFirst { it.key == key }
        if (index >= 0) {
            directLines[index] = directLines[index].copy(state = state, isError = isError)
        }
    }

    /** Registers the SENT receiver and dispatches the message, updating the line's status. */
    fun launchDirectSend(label: String, number: String, body: String, key: String) {
        val action = SimSmsSender.SENT_ACTION_PREFIX + key
        val receiver = SimSmsSender.registerSentReceiver(context, action) { result ->
            when (result) {
                is SimSmsSender.SentResult.Sent -> updateLine(key, "Envoyé ✓", false)
                is SimSmsSender.SentResult.Error -> {
                    val cause = when (result.resultCode) {
                        android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE -> "Pas de réseau"
                        android.telephony.SmsManager.RESULT_ERROR_RADIO_OFF -> "Radio désactivée (SIM absente ?)"
                        android.telephony.SmsManager.RESULT_ERROR_NULL_PDU -> "PDU invalide"
                        else -> "Échec" + if (result.errorCode != 0) " (code ${result.errorCode})" else ""
                    }
                    updateLine(key, cause, true)
                }
            }
            // Un seul verdict suffit : le moteur désinscrit le receiver à ce moment.
            true
        }
        updateLine(key, "…", false)
        directScope.launch {
            if (!SimSmsSender.dispatch(context, number, body, action)) {
                updateLine(key, "Cet appareil ne gère pas la téléphonie (SMS)", true)
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: IllegalArgumentException) {
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(max = 560.dp)
            .testTag("absent_sms_dialog"),
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Message,
                        contentDescription = null,
                        tint = JudoRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "SMS aux parents des absents",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
                    )
                }
                if (stage == SmsStage.REVIEW && plan.hasRecipients) {
                    Text(
                        text = "Message ${currentIndex + 1} / ${plan.messageCount}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        text = {
            when (stage) {
                SmsStage.RECAP -> SmsRecapContent(
                    plan = plan,
                    noSmsAppAvailable = noSmsAppAvailable,
                    messageTemplate = messageTemplate,
                    onTemplateChange = { messageTemplate = it },
                    exampleMessage = plan.recipients.firstOrNull()?.let { messageFor(it) }.orEmpty()
                )
                SmsStage.REVIEW -> {
                    val recipient = plan.recipients.getOrNull(currentIndex)
                    val draft = recipient?.let { editableDrafts[it.memberId] }
                        ?: recipient?.let { messageFor(it) }
                        ?: ""
                    SmsReviewContent(
                        plan = plan,
                        currentIndex = currentIndex,
                        preparedMemberIds = preparedMemberIds,
                        errorMessage = errorMessage,
                        draft = draft,
                        onDraftChange = { value ->
                            recipient?.let { editableDrafts[it.memberId] = value }
                        }
                    )
                }
                SmsStage.DIRECT -> SmsDirectContent(
                    plan = plan,
                    lines = directLines,
                    simCapable = simCapable,
                    errorMessage = errorMessage
                )
            }
        },
        confirmButton = {
            when (stage) {
                SmsStage.RECAP -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            errorMessage = null
                            stage = SmsStage.DIRECT
                        },
                        enabled = simCapable,
                        modifier = Modifier.testTag("absent_sms_direct_btn")
                    ) {
                        Text("Envoi direct (SIM)", color = if (simCapable) JudoRed else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            errorMessage = null
                            currentIndex = 0
                            stage = SmsStage.REVIEW
                        },
                        enabled = plan.hasRecipients && !noSmsAppAvailable,
                        colors = ButtonDefaults.buttonColors(containerColor = JudoRed),
                        modifier = Modifier.testTag("absent_sms_prepare_btn")
                    ) {
                        Text("Préparer les SMS (${plan.messageCount})")
                    }
                }

                SmsStage.REVIEW -> {
                    val recipient = plan.recipients.getOrNull(currentIndex)
                    val isLast = currentIndex >= plan.recipients.lastIndex
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                // Le message personnalisé (édité ou modèle pré-rempli) est
                                // toujours celui affiché dans le champ de revue.
                                recipient?.let { openSmsApp(it, editableDrafts[it.memberId]) }
                            },
                            enabled = recipient != null,
                            colors = ButtonDefaults.buttonColors(containerColor = JudoRed),
                            modifier = Modifier.testTag("absent_sms_open_btn")
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Ouvrir l'application SMS")
                        }
                        Button(
                            onClick = { if (isLast) onDismiss() else currentIndex++ },
                            colors = ButtonDefaults.buttonColors(containerColor = JudoSuccess),
                            modifier = Modifier.testTag("absent_sms_next_btn")
                        ) {
                            Text(if (isLast) "Terminer" else "Suivant")
                        }
                    }
                }

                SmsStage.DIRECT -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            ensurePermissionThen {
                                // L'appareil ne quitte pas l'application : le SMS part direct
                                // par la SIM, avec le message personnalisé de chaque absent.
                                plan.recipients.forEach { recipient ->
                                    val key = "${recipient.memberId}-${System.nanoTime()}"
                                    directLines.add(
                                        DirectSmsLine(
                                            key = key,
                                            label = recipient.memberName,
                                            number = recipient.normalizedPhone,
                                            state = "…", isError = false
                                        )
                                    )
                                    launchDirectSend(
                                        recipient.memberName,
                                        recipient.normalizedPhone,
                                        messageFor(recipient),
                                        key
                                    )
                                }
                            }
                        },
                        enabled = plan.hasRecipients,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JudoRed),
                        modifier = Modifier.testTag("absent_sms_direct_send_absents_btn")
                    ) {
                        Text("Envoyer aux absents (${plan.messageCount})")
                    }
                    Button(
                        onClick = { onDismiss() },
                        colors = ButtonDefaults.buttonColors(containerColor = JudoSuccess),
                        modifier = Modifier.testTag("absent_sms_direct_finish_btn")
                    ) {
                        Text("Terminer")
                    }
                }
            }
        },
        dismissButton = {
            when (stage) {
                SmsStage.RECAP -> TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("absent_sms_cancel_btn")
                ) {
                    Text("Annuler", color = JudoRed)
                }

                SmsStage.REVIEW -> TextButton(
                    onClick = { stage = SmsStage.RECAP },
                    modifier = Modifier.testTag("absent_sms_back_btn")
                ) {
                    Text("Retour", color = JudoRed)
                }

                SmsStage.DIRECT -> TextButton(
                    onClick = { stage = SmsStage.RECAP },
                    modifier = Modifier.testTag("absent_sms_direct_back_btn")
                ) {
                    Text("Retour", color = JudoRed)
                }
            }
        }
    )
}

@Composable
private fun SmsRecapContent(
    plan: AbsentSmsNotifier.SmsPlan,
    noSmsAppAvailable: Boolean,
    messageTemplate: String,
    onTemplateChange: (String) -> Unit,
    exampleMessage: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (plan.absentCount == 0) {
            Text(
                text = "Aucun athlète absent pour cette séance : aucun SMS à préparer.",
                style = MaterialTheme.typography.bodyMedium.copy(color = JudoSuccess, fontWeight = FontWeight.Bold),
                modifier = Modifier.testTag("absent_sms_no_absent")
            )
            return@Column
        }

        // 3 summary pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RecapPill("Absents", "${plan.absentCount}", MaterialTheme.colorScheme.error, Modifier.weight(1f))
            RecapPill("Numéros valides", "${plan.validNumberCount}", JudoSuccess, Modifier.weight(1f))
            RecapPill("Messages", "${plan.messageCount}", JudoRed, Modifier.weight(1f))
        }

        if (noSmsAppAvailable) {
            InlineBanner(
                text = "Aucune application SMS n'est disponible sur cet appareil : l'envoi est impossible.",
                color = JudoWarning,
                testTag = "absent_sms_no_app_warning"
            )
        }

        // ---- Modèle du message : toujours pré-rempli, personnalisable -------------
        if (plan.absentCount > 0) {
            Text(
                text = "Message envoyé — personnalisable",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.testTag("absent_sms_template_title")
            )
            OutlinedTextField(
                value = messageTemplate,
                onValueChange = onTemplateChange,
                label = { Text("Modèle du message") },
                supportingText = {
                    Text(
                        text = "Emplacements : {NOM} est remplacé par le nom de l'absent " +
                            "(propre à chaque message), {DATE} par la date, {HEURE} par l'heure, " +
                            "{CLUB} par le club.",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                minLines = 5,
                maxLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("absent_sms_template_edit")
            )
            if (exampleMessage.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Aperçu pour un destinataire :",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Text(
                            text = exampleMessage,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("absent_sms_template_preview")
                        )
                    }
                }
            }
        }

        if (!plan.hasRecipients) {
            Text(
                text = "Aucun numéro valide pour les absents : aucun SMS ne peut être préparé.",
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold),
                modifier = Modifier.testTag("absent_sms_no_valid_number")
            )
        } else {
            Text(
                text = "Destinataires (${plan.validNumberCount})",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
            plan.recipients.forEach { recipient ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = recipient.memberName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        // The full number is shown here so the coach can verify it before sending.
                        Text(
                            text = recipient.rawPhone,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }

        if (plan.skipped.isNotEmpty()) {
            Text(
                text = "Non contactés (${plan.skipped.size})",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
            plan.skipped.forEach { skip ->
                val reason = when (skip.reason) {
                    AbsentSmsNotifier.SkipReason.MISSING_PHONE -> "Numéro manquant"
                    AbsentSmsNotifier.SkipReason.INVALID_PHONE -> "Numéro invalide"
                    AbsentSmsNotifier.SkipReason.DUPLICATE_MEMBER -> "Doublon"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = skip.memberName,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = JudoWarning,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        if (plan.sharedNumbers.isNotEmpty()) {
            InlineBanner(
                text = "${plan.sharedNumbers.size} numéro(s) partagé(s) par plusieurs athlètes " +
                    "(fratrie) : chaque enfant recevra son propre message personnalisé.",
                color = JudoWarning,
                testTag = "absent_sms_shared_numbers"
            )
        }
    }
}

@Composable
private fun SmsDirectContent(
    plan: AbsentSmsNotifier.SmsPlan,
    lines: List<DirectSmsLine>,
    simCapable: Boolean,
    errorMessage: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Envoi direct par la carte SIM — sans quitter l'application",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.testTag("absent_sms_direct_title")
        )

        if (!simCapable) {
            InlineBanner(
                text = "Cet appareil ne gère pas la téléphonie (SMS) : l'envoi direct est impossible ici.",
                color = JudoWarning,
                testTag = "absent_sms_direct_no_telephony"
            )
        } else {
            InlineBanner(
                text = "Le message part immédiatement par la SIM de l'appareil. Le statut " +
                    "affiché est confirmé par le système (réseau radio), pas par une garantie de livraison.",
                color = JudoWarning,
                testTag = "absent_sms_direct_disclaimer"
            )
        }

        if (plan.hasRecipients) {
            Text(
                text = "Pré-remplir avec les absents de la séance",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Destinataires des absents : " +
                    plan.recipients.joinToString(", ") { it.normalizedPhone },
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        Text(
            text = "Les numéros des parents sont repris automatiquement depuis les fiches " +
                "des athlètes absents : aucune saisie manuelle n'est nécessaire.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.testTag("absent_sms_direct_auto_numbers")
        )

        if (lines.isNotEmpty()) {
            Text(
                text = "Statut des envois",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
            lines.forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${line.label} • ${line.number}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = line.state,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (line.isError) MaterialTheme.colorScheme.error else JudoSuccess,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        errorMessage?.let { text ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.testTag("absent_sms_direct_error")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
private fun SmsReviewContent(
    plan: AbsentSmsNotifier.SmsPlan,
    currentIndex: Int,
    preparedMemberIds: List<Long>,
    errorMessage: String?,
    draft: String,
    onDraftChange: (String) -> Unit
) {
    val recipient = plan.recipients.getOrNull(currentIndex) ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = recipient.memberName,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.testTag("absent_sms_recipient_name")
        )
        Text(
            text = recipient.rawPhone,
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )

        // Message déjà rempli avec le modèle (ou la personnalisation) ;
        // le coach peut l'ajuster spécifiquement pour ce destinataire avant l'ouverture de l'app.
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = { Text("Message pour ce destinataire (pré-rempli, modifiable)") },
            supportingText = {
                Text(
                    text = "Ce message partira exactement comme il est écrit ici.",
                    style = MaterialTheme.typography.labelSmall
                )
            },
            minLines = 6,
            maxLines = 10,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("absent_sms_message_edit")
        )

        val isPrepared = recipient.memberId in preparedMemberIds
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (isPrepared) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                contentDescription = null,
                tint = if (isPrepared) JudoSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (isPrepared) {
                    "Message ouvert dans l'application SMS (envoi non confirmé)"
                } else {
                    "Pas encore préparé"
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (isPrepared) JudoSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.testTag("absent_sms_status")
            )
        }

        errorMessage?.let { message ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.testTag("absent_sms_error")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        InlineBanner(
            text = "L'application SMS s'ouvre avec le destinataire et le message préremplis. " +
                "Vous devez confirmer l'envoi dans cette application : l'application ne peut pas " +
                "garantir que le SMS a été envoyé.",
            color = JudoWarning,
            testTag = "absent_sms_disclaimer"
        )
    }
}

@Composable
private fun RecapPill(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, fontWeight = FontWeight.Black, color = color, fontSize = 18.sp)
            Text(text = title, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun InlineBanner(
    text: String,
    color: Color,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(10.dp)
        )
    }
}
