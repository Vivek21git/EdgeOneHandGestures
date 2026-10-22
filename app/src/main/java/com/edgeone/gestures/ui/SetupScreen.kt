package com.edgeone.gestures.ui

import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun SetupScreen(serviceEnabled: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    ScreenScaffold(title = "Setup and permissions", onBack = onBack) {

        SectionCard("Why the accessibility permission?") {
            Text(
                "Android only lets an app press Back, Home, Recents, Lock screen, Screenshot and similar system buttons " +
                    "on your behalf through an accessibility service. This app also uses that service to place two thin, " +
                    "invisible touch strips along the screen edges.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "What it does NOT do: it subscribes to no accessibility events, cannot read what is on your screen, " +
                    "has no internet permission, and stores nothing except your gesture settings on this device. " +
                    "It is a system-gesture utility, not an assistive technology, and is declared as such.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SectionCard("Step 1 – Allow restricted settings (installed from an APK)") {
            Text(
                "Android 13 and newer block accessibility access for apps installed outside an app store, and the " +
                    "service switch can appear greyed out. If that happens:",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "1. Open App info below.\n" +
                    "2. Tap the ⋮ menu (top right).\n" +
                    "3. Tap \"Allow restricted settings\" and confirm with your PIN / fingerprint.\n" +
                    "If the menu item is missing, first try to switch the service on in Step 2 once (it will be refused), " +
                    "then come back; the item usually appears after that attempt. Wording can differ on Motorola's software.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = { SystemStatus.openAppInfo(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Open App info")
            }
        }

        SectionCard("Step 2 – Turn on the accessibility service") {
            StatusLine(serviceEnabled, if (serviceEnabled) "Service is enabled" else "Service is not enabled yet")
            Text(
                "Open Accessibility settings, find \"Edge One-Hand Gestures\" (it may be under Installed apps or Downloaded apps), " +
                    "switch it on and accept the system warning. This app never enables it for you.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = { SystemStatus.openAccessibilitySettings(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Open Accessibility settings")
            }
        }

        SectionCard("Step 3 – Keep it running (battery)") {
            Text(
                "A running accessibility service is normally kept alive by Android, but aggressive battery managers can " +
                    "stop it. To be safe: open App info → Battery (App battery usage) and choose \"Unrestricted\" " +
                    "(wording varies). I could not verify Motorola-specific background limits on your Edge 70 Fusion; " +
                    "if the strips vanish after a while, this is the first thing to check.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = { SystemStatus.openAppInfo(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Open App info → Battery")
            }
        }

        SectionCard("\"Display over other apps\" – not needed") {
            Text(
                "The edge strips are accessibility-overlay windows owned by the accessibility service, so the separate " +
                    "\"Display over other apps\" permission is NOT requested. No other permission is required.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionCard("Good to know") {
            Text(
                "• Lock screen presses the equivalent of the power button: the screen turns off and your normal unlock " +
                    "method is needed. It does not shut the phone down and does not use device-admin rights.\n" +
                    "• Android's own Back gesture also lives on the screen edges. This app asks the system to leave its strips " +
                    "alone, but whether your phone honours that for overlay windows is not guaranteed. If inward swipes trigger " +
                    "Back twice or do nothing, lower the system Back-gesture sensitivity (Settings → System → Gestures → " +
                    "System navigation → gear icon), narrow the strip, or move it away from where you swipe for Back.\n" +
                    "• Touches that start inside a strip belong to the strip, so the app underneath cannot use that thin " +
                    "slice of the screen. Keep strips narrow and limit their vertical range.\n" +
                    "• This device: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
