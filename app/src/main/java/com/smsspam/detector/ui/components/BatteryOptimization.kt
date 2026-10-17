package com.smsspam.detector.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Helpers for the OEM battery/power-saver problem: on many devices (Xiaomi,
 * Samsung, Huawei, Oppo, Vivo …) an aggressive "protected apps" / Doze policy
 * delays or swallows notifications until the app is opened. There is no single
 * API that fixes this, so we (a) detect whether the app is exempt from battery
 * optimisation and (b) deep-link to the manufacturer's own screen when one
 * exists.
 */

/** True when the system will not put the app into Doze / App Standby. */
fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val power = getSystemService(PowerManager::class.java) ?: return false
    return power.isIgnoringBatteryOptimizations(packageName)
}

/**
 * The system dialog that asks the user to exempt this app from battery
 * optimisation. Requires `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
 */
@SuppressLint("BatteryLife")
fun requestIgnoreBatteryOptimizationsIntent(context: Context): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.parse("package:${context.packageName}"))

/** The list of apps exempt from battery optimisation (works on every OEM). */
fun batteryOptimizationSettingsIntent(): Intent =
    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

/** App details screen, where battery usage can also be adjusted. */
fun appDetailsSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.parse("package:${context.packageName}"))

/** A manufacturer "autostart"/"protected apps" screen, when one is installed. */
fun oemAutoStartIntent(context: Context): Intent? {
    for ((pkg, cls) in OEM_AUTOSTART_SCREENS) {
        val intent = Intent().setClassName(pkg, cls)
        if (context.packageManager.resolveActivity(intent, 0) != null) return intent
    }
    return null
}

/** Human-readable name of the manufacturer's battery screen, or null. */
fun oemName(): String? = when {
    Build.MANUFACTURER.equals("xiaomi", ignoreCase = true) -> "Xiaomi (MIUI/HyperOS)"
    Build.MANUFACTURER.equals("redmi", ignoreCase = true) -> "Xiaomi (MIUI/HyperOS)"
    Build.MANUFACTURER.equals("huawei", ignoreCase = true) -> "Huawei"
    Build.MANUFACTURER.equals("honor", ignoreCase = true) -> "Honor"
    Build.MANUFACTURER.equals("samsung", ignoreCase = true) -> "Samsung"
    Build.MANUFACTURER.equals("oppo", ignoreCase = true) -> "Oppo"
    Build.MANUFACTURER.equals("realme", ignoreCase = true) -> "Realme"
    Build.MANUFACTURER.equals("vivo", ignoreCase = true) -> "Vivo"
    Build.MANUFACTURER.equals("oneplus", ignoreCase = true) -> "OnePlus"
    else -> null
}

/** Name of the OEM autostart screen, for the settings button label. */
fun oemAutoStartLabel(): String? = when {
    Build.MANUFACTURER.equals("xiaomi", ignoreCase = true) ||
        Build.MANUFACTURER.equals("redmi", ignoreCase = true) -> "MIUI autostart"
    Build.MANUFACTURER.equals("huawei", ignoreCase = true) -> "Protected apps"
    Build.MANUFACTURER.equals("honor", ignoreCase = true) -> "Protected apps"
    Build.MANUFACTURER.equals("oppo", ignoreCase = true) ||
        Build.MANUFACTURER.equals("realme", ignoreCase = true) -> "Auto-start"
    Build.MANUFACTURER.equals("vivo", ignoreCase = true) -> "Auto-start"
    else -> null
}

private val OEM_AUTOSTART_SCREENS = listOf(
    // Xiaomi / Redmi / POCO
    "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
    // Huawei / Honor
    "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
    "com.huawei.systemmanager" to "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
    // Oppo / Realme
    "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
    "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
    // Vivo
    "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
    // Asus
    "com.asus.mobilemanager" to "com.asus.mobilemanager.powersaver.PowerSaverSettings",
)
