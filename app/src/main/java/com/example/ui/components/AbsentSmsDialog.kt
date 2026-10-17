package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Member
import com.example.ui.theme.JudoRed
import com.example.ui.theme.JudoSuccess
import com.example.ui.theme.JudoWarning
import com.example.util.AbsentSmsNotifier

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

private enum class SmsStage { RECAP, REVIEW }

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
    clubName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val plan = remember(absentMembers) { AbsentSmsNotifier.buildPlan(absentMembers) }

    var stage by remember { mutableStateOf(SmsStage.RECAP) }
    val preparedMemberIds = remember { mutableStateListOf<Long>() }
    var currentIndex by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val messageFor: (AbsentSmsNotifier.SmsRecipient) -> String = { recipient ->
        AbsentSmsNotifier.buildMessage(
            memberName = recipient.memberName,
            sessionDate = sessionDate,
            clubName = clubName
        )
    }

    fun openSmsApp(recipient: AbsentSmsNotifier.SmsRecipient) {
        val body = messageFor(recipient)
        val intent = SmsIntentFactory.buildIntent(recipient.normalizedPhone, body)
        if (!SmsIntentFactory.canHandle(context, intent)) {
            // Never log the phone number itself.
            Log.w("AbsentSms", "No SMS application available to handle ACTION_SENDTO/smsto")
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
            Log.w("AbsentSms", "ActivityNotFoundException while opening the SMS application")
            errorMessage = "Aucune application SMS n'est disponible sur cet appareil."
        } catch (e: Exception) {
            Log.w("AbsentSms", "Could not open the SMS application: ${e.javaClass.simpleName}")
            errorMessage = "Impossible d'ouvrir l'application SMS."
        }
    }

    val noSmsAppAvailable = remember {
        !SmsIntentFactory.canHandle(
            context,
            SmsIntentFactory.buildIntent("0000000000", "")
        )
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
                SmsStage.RECAP -> SmsRecapContent(plan = plan, noSmsAppAvailable = noSmsAppAvailable)
                SmsStage.REVIEW -> SmsReviewContent(
                    plan = plan,
                    currentIndex = currentIndex,
                    preparedMemberIds = preparedMemberIds,
                    errorMessage = errorMessage,
                    messageFor = messageFor
                )
            }
        },
        confirmButton = {
            when (stage) {
                SmsStage.RECAP -> Button(
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

                SmsStage.REVIEW -> {
                    val recipient = plan.recipients.getOrNull(currentIndex)
                    val isLast = currentIndex >= plan.recipients.lastIndex
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { recipient?.let { openSmsApp(it) } },
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
            }
        }
    )
}

@Composable
private fun SmsRecapContent(
    plan: AbsentSmsNotifier.SmsPlan,
    noSmsAppAvailable: Boolean
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
private fun SmsReviewContent(
    plan: AbsentSmsNotifier.SmsPlan,
    currentIndex: Int,
    preparedMemberIds: List<Long>,
    errorMessage: String?,
    messageFor: (AbsentSmsNotifier.SmsRecipient) -> String
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

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = messageFor(recipient),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(12.dp)
                    .testTag("absent_sms_message_preview")
            )
        }

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
