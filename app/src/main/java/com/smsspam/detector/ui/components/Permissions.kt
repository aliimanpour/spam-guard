package com.smsspam.detector.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat

/** Permissions the app cares about. */
enum class AppPermission(val permission: String, val title: String, val rationale: String) {
    RECEIVE_SMS(
        Manifest.permission.RECEIVE_SMS,
        "Receive messages",
        "Lets Spam Guard inspect incoming SMS the moment they arrive.",
    ),
    READ_SMS(
        Manifest.permission.READ_SMS,
        "Read messages",
        "Lets you scan the existing inbox for spam.",
    ),
    SEND_SMS(
        Manifest.permission.SEND_SMS,
        "Send messages",
        "Needed to reply to messages and to act as the default SMS app.",
    ),
    READ_CONTACTS(
        Manifest.permission.READ_CONTACTS,
        "Contacts",
        "Shows contact names instead of raw phone numbers in your conversations.",
    ),
    POST_NOTIFICATIONS(
        "android.permission.POST_NOTIFICATIONS",
        "Notifications",
        "Shows an alert whenever a message is blocked.",
    );

    companion object {
        val needed: List<AppPermission>
            get() = buildList {
                add(RECEIVE_SMS)
                add(READ_SMS)
                add(SEND_SMS)
                add(READ_CONTACTS)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(POST_NOTIFICATIONS)
            }
    }
}

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Remembers whether each tracked permission is currently granted. */
@Composable
fun rememberPermissionStatus(): MutableState<Map<AppPermission, Boolean>> {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember {
        mutableStateOf(
            AppPermission.needed.associateWith { context.hasPermission(it.permission) }
        )
    }
}
