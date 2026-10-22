package com.edgeone.gestures.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.edgeone.gestures.service.EdgeAccessibilityService

/** Read-only checks plus helpers that OPEN Android Settings pages. Nothing is granted silently. */
object SystemStatus {

    /** Is our service switched on in Settings > Accessibility? (Independent of whether it is running.) */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val me = ComponentName(context, EdgeAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun openAccessibilitySettings(context: Context) =
        safeStart(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    /** App info page: hosts "Allow restricted settings" (top-right menu) and Battery usage. */
    fun openAppInfo(context: Context) = safeStart(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )

    private fun safeStart(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open that Settings page on this device.", Toast.LENGTH_LONG).show()
        }
    }
}
