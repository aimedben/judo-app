package com.example.util

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicInteger

/**
 * Direct SMS transmission through the device's own SIM — the alternative gameplan to
 * [com.example.ui.components.AbsentSmsDialog]'s `ACTION_SENDTO` handover, used when the coach
 * wants the message to actually leave the device right away without opening another application.
 *
 * Constraint: this requires the `SEND_SMS` runtime permission, granted explicitly by the user
 * (asked by the UI before the first send). `SmsManager` reports back through a `SENT` broadcast,
 * so the status shown in the app is the platform-confirmed radio transmission — with no external
 * SMS provider, no API key and no backend involvement.
 */
object SimSmsSender {

    /** Prefix shared by the per-message `SENT` broadcast actions (suffix: a unique nonce). */
    const val SENT_ACTION_PREFIX = "com.example.directsms.SMS_SENT."

    /** True only on devices with real telephony hardware (SIM mobiles, LTE tablets). */
    fun isDeviceSmsCapable(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    fun hasSendSmsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /** Platform-confirmed outcome of one direct SMS dispatch. */
    sealed class SentResult {
        /** The radio accepted every reported part of the message. */
        data object Sent : SentResult()

        /** The platform refused (no service, radio off, generic failure…). */
        data class Error(val resultCode: Int, val errorCode: Int) : SentResult()
    }

    /**
     * Registers the receiver that reports the platform-confirmed transmission of a message
     * identified by [action]. The callback returns `true` to ask for the receiver to be
     * unregistered right after it fired (one verdict is enough), or `false` to keep listening
     * (the caller then unregisters it itself).
     */
    fun registerSentReceiver(
        context: Context,
        action: String,
        callback: (SentResult) -> Boolean
    ): BroadcastReceiver {
        val receiver: BroadcastReceiver = object : BroadcastReceiver() {
            private val settled = AtomicInteger(0)
            override fun onReceive(silentContext: Context?, intent: Intent?) {
                // Multi-part messages fire this action once per part with the SAME intent:
                // only the first verdict is reported, the rest are noise.
                if (!settled.compareAndSet(0, 1)) return
                val result = when (resultCode) {
                    android.app.Activity.RESULT_OK -> SentResult.Sent
                    else ->
                        SentResult.Error(
                            resultCode = resultCode,
                            errorCode = intent?.getIntExtra("errorCode", 0) ?: 0
                        )
                }
                if (callback(result)) {
                    try {
                        context.unregisterReceiver(this)
                    } catch (_: IllegalArgumentException) {
                        // Already gone: nothing else to do.
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(action),
            // The SENT broadcast is emitted by the telephony framework, not this app: an (API
            // 33+) exported registration is what lets it reach an in-memory receiver.
            ContextCompat.RECEIVER_EXPORTED
        )
        return receiver
    }

    /**
     * Dispatches [body] to [number] immediately through the SIM.
     *
     * @return false only when the device has no usable [SmsManager] (no telephony feature);
     *         a radio-level failure (no service, no SIM…) is reported asynchronously through
     *         the receiver registered with [registerSentReceiver] for [action].
     */
    fun dispatch(context: Context, number: String, body: String, action: String): Boolean {
        val manager = smsManager(context) ?: return false

        val sentIntent = PendingIntent.getBroadcast(
            context,
            // Unique inside the process so concurrent dispatches never overwrite one another.
            nextRequestId.getAndIncrement(),
            Intent(action).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        if (body.length <= 160) {
            manager.sendTextMessage(number, null, body, sentIntent, null)
        } else {
            val parts = manager.divideMessage(body)
            // One SENT PendingIntent per part (the platform API expects an ArrayList), all
            // sharing the same action: the receiver collapses them into a single verdict.
            val sentIntents = ArrayList<PendingIntent>(parts.size)
            repeat(parts.size) { sentIntents.add(sentIntent) }
            manager.sendMultipartTextMessage(number, null, parts, sentIntents, null)
        }
        return true
    }

    /**
     * Parses a free-form destination field (comma / semicolon / line separated) into the set of
     * normalized club numbers. Pure and Android-free so it can be unit tested.
     */
    fun parseDestinations(raw: String): List<String> =
        raw.split(",", ";", "\n")
            .mapNotNull { AbsentSmsNotifier.normalizePhone(it) }
            .distinct()

    private val nextRequestId = AtomicInteger(1)

    private fun smsManager(context: Context): SmsManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }
}
