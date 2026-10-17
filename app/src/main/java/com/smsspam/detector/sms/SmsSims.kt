package com.smsspam.detector.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

/**
 * One SIM card (subscription) the user can pick as the outgoing line for SMS.
 */
data class SimSubscription(
    val subId: Int,
    /** 1-based physical slot, used for the "SIM 1" / "SIM 2" labels. */
    val slot: Int,
    val displayName: String,
)

/**
 * The SIM cards (subscriptions) SMS can be sent with. Requires READ_PHONE_STATE
 * and degrades to an empty list when the permission is missing or the telephony
 * info is unavailable, so single-SIM or permission-less setups simply hide the
 * SIM picker.
 */
object SmsSims {

    /** True when SIMs can be listed (READ_PHONE_STATE granted). */
    fun canList(context: Context): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_PHONE_STATE,
    ) == PackageManager.PERMISSION_GRANTED

    /** Active SIMs, ordered by slot. Best effort: empty on any failure. */
    fun active(context: Context): List<SimSubscription> {
        if (!canList(context)) return emptyList()
        return try {
            val manager = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
            val subs = manager.activeSubscriptionInfoList ?: return emptyList()
            subs.map { info ->
                val slot = info.simSlotIndex + 1
                SimSubscription(
                    subId = info.subscriptionId,
                    slot = slot,
                    displayName = info.displayName?.toString()?.ifBlank { null } ?: "SIM $slot",
                )
            }.sortedBy { it.slot }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
