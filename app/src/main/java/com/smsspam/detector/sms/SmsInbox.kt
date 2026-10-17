package com.smsspam.detector.sms

import android.content.ContentValues
import android.content.Context
import android.app.role.RoleManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import com.smsspam.detector.model.QuarantineMessage

/** A raw message read from the system SMS inbox. */
data class InboxSms(
    val id: Long,
    val sender: String,
    val body: String,
    val timestamp: Long,
)

/** Reads and (best effort) deletes messages from the system SMS provider. */
object SmsInbox {

    private val inboxUri: Uri = Telephony.Sms.Inbox.CONTENT_URI

    /** Reads the whole inbox. Requires READ_SMS. Returns an empty list if not allowed. */
    fun readInbox(context: Context): List<InboxSms> {
        val result = mutableListOf<InboxSms>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        try {
            context.contentResolver.query(
                inboxUri,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC",
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (cursor.moveToNext()) {
                    result += InboxSms(
                        id = cursor.getLong(idIdx),
                        sender = cursor.getString(addressIdx) ?: "Unknown",
                        body = cursor.getString(bodyIdx) ?: "",
                        timestamp = cursor.getLong(dateIdx),
                    )
                }
            }
        } catch (_: Exception) {
            // Missing permission or provider unavailable.
        }
        return result
    }

    /**
     * Tries to remove a message from the system SMS app. Only the default SMS app
     * (with WRITE_SMS) is allowed to do this, so failures are swallowed on purpose.
     */
    fun delete(context: Context, message: InboxSms): Boolean = try {
        val deleted = context.contentResolver.delete(
            Telephony.Sms.CONTENT_URI,
            "${Telephony.Sms._ID} = ?",
            arrayOf(message.id.toString()),
        )
        deleted > 0
    } catch (_: Exception) {
        false
    }

    /**
     * Best effort removal used from the receiver where we only know sender/body/date.
     */
    fun deleteMatching(context: Context, sender: String, body: String, timestamp: Long): Boolean = try {
        val deleted = context.contentResolver.delete(
            Telephony.Sms.CONTENT_URI,
            "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.BODY} = ? AND ${Telephony.Sms.DATE} = ?",
            arrayOf(sender, body, timestamp.toString()),
        )
        deleted > 0
    } catch (_: Exception) {
        false
    }

    /** True when this app currently holds the default SMS role. */
    fun isDefaultSmsApp(context: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Some OEM ROMs (e.g. MIUI) keep the role in RoleManager without
            // updating the legacy `sms_default_application` setting, so prefer
            // the role check and only fall back to the secure setting.
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_SMS)
                ?: (Telephony.Sms.getDefaultSmsPackage(context) == context.packageName)
        } else {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }
    } catch (_: Exception) {
        false
    }

    /** Whether an equivalent message already exists in the inbox (avoids duplicates). */
    fun exists(context: Context, sender: String, body: String, timestamp: Long): Boolean = try {
        context.contentResolver.query(
            inboxUri,
            arrayOf(Telephony.Sms.DATE),
            "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.BODY} = ?",
            arrayOf(sender, body),
            null,
        )?.use { cursor ->
            val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            var found = false
            while (cursor.moveToNext()) {
                if (kotlin.math.abs(cursor.getLong(dateIdx) - timestamp) < 10_000) found = true
            }
            found
        } ?: false
    } catch (_: Exception) {
        false
    }

    /**
     * Stores an incoming message in the system SMS inbox. Only the default SMS app
     * is allowed to do this, so it is used from the SMS_DELIVER path.
     */
    fun insertIncoming(context: Context, sender: String, body: String, timestamp: Long): Boolean = try {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, sender)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, timestamp)
            put(Telephony.Sms.DATE_SENT, timestamp)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }
        context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) != null
    } catch (_: Exception) {
        false
    }

    fun toQuarantine(inbox: InboxSms, ruleId: String?, ruleName: String?): QuarantineMessage =
        QuarantineMessage(
            sender = inbox.sender,
            body = inbox.body,
            timestamp = inbox.timestamp,
            matchedRuleId = ruleId,
            matchedRuleName = ruleName,
        )
}
