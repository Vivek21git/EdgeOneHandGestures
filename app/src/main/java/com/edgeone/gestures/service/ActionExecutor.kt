package com.edgeone.gestures.service

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import com.edgeone.gestures.core.ActionRegistry
import com.edgeone.gestures.core.ActionType
import com.edgeone.gestures.core.GestureAction
import com.edgeone.gestures.ui.MainActivity

sealed interface ExecResult {
    data object Success : ExecResult
    data class Failure(val message: String) : ExecResult
}

/** Runs a [GestureAction] from inside the accessibility service. Never throws. */
class ActionExecutor(private val service: AccessibilityService) {

    fun execute(action: GestureAction): ExecResult {
        val support = ActionRegistry.support(action, Build.VERSION.SDK_INT, accessibilityServiceEnabled = true)
        if (!support.supported) return ExecResult.Failure(support.warning ?: "Unsupported action")
        val title = ActionRegistry.info(action.type).title
        return try {
            when (action.type) {
                ActionType.NONE -> ExecResult.Success
                ActionType.BACK -> global(AccessibilityService.GLOBAL_ACTION_BACK, title)
                ActionType.HOME -> global(AccessibilityService.GLOBAL_ACTION_HOME, title)
                ActionType.RECENTS -> global(AccessibilityService.GLOBAL_ACTION_RECENTS, title)
                ActionType.LOCK_SCREEN -> global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, title)
                ActionType.NOTIFICATIONS -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, title)
                ActionType.QUICK_SETTINGS -> global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, title)
                ActionType.SCREENSHOT -> global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, title)
                ActionType.SPLIT_SCREEN -> global(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN, title)
                ActionType.LAUNCH_APP -> launchApp(action)
                ActionType.LAUNCH_SHORTCUT -> launchShortcut(action)
                ActionType.OPEN_APP_SETTINGS -> start(
                    Intent(service, MainActivity::class.java),
                    "Open app settings",
                )
            }
        } catch (t: Throwable) {
            ExecResult.Failure("${t.javaClass.simpleName}: ${t.message ?: "action failed"}")
        }
    }

    private fun global(id: Int, title: String): ExecResult =
        if (service.performGlobalAction(id)) ExecResult.Success
        else ExecResult.Failure("$title was refused by the system (not supported on this device/screen state).")

    private fun launchApp(action: GestureAction): ExecResult {
        val pkg = action.packageName ?: return ExecResult.Failure("No app chosen.")
        val intent = service.packageManager.getLaunchIntentForPackage(pkg)
            ?: return ExecResult.Failure("App $pkg is not installed or has no launcher activity.")
        return start(intent, action.label ?: pkg)
    }

    private fun launchShortcut(action: GestureAction): ExecResult {
        val uri = action.shortcutUri ?: return ExecResult.Failure("No shortcut chosen.")
        val intent = try {
            Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
        } catch (e: Exception) {
            return ExecResult.Failure("Saved shortcut is invalid; choose it again.")
        }
        intent.selector = null // never honour a selector smuggled into a stored URI
        return start(intent, action.label ?: "shortcut")
    }

    private fun start(intent: Intent, what: String): ExecResult {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            service.startActivity(intent)
            ExecResult.Success
        } catch (e: ActivityNotFoundException) {
            ExecResult.Failure("Nothing can open $what.")
        } catch (e: SecurityException) {
            ExecResult.Failure("Android blocked starting $what (${e.message}).")
        }
    }
}
