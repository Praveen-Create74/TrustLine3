package com.trustline.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class CallDetectionAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CallDetectionService"

        // Packages to monitor for VoIP call screens
        private val VOIP_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "us.zoom.videomeetings",
            "com.skype.raider",
            "com.google.android.talk"
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (VOIP_PACKAGES.contains(packageName)) {
            val className = event.className?.toString() ?: ""
            Log.d(TAG, "VoIP activity detected: $packageName / $className")

            // Detect call screen triggers (e.g., WhatsApp CallActivity or InCallActivity)
            if (className.contains("Call", ignoreCase = true) || className.contains("Voip", ignoreCase = true)) {
                val platformName = when {
                    packageName.contains("whatsapp") -> "WhatsApp"
                    packageName.contains("telegram") -> "Telegram"
                    packageName.contains("zoom") -> "Zoom"
                    else -> "VoIP Call"
                }

                Log.i(TAG, "VoIP Call Screen Active on $platformName! Emitting event...")
                TrustLineMediaPlugin.emitCallInterceptedEvent(
                    callerName = "Active VoIP Caller",
                    callerNumber = "+1 (800) 555-0199",
                    platform = platformName,
                    initialRiskScore = 20,
                    state = "ACTIVE"
                )
                launchTrustLineOverlay()
            }
        }
    }

    private fun launchTrustLineOverlay() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra("EXTRA_VOIP_CALL_DETECTED", true)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService interrupted")
    }
}
