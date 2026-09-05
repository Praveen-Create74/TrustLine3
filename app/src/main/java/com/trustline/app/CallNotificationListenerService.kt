package com.trustline.app

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CallNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "CallNotifListener"

        private val CALL_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "us.zoom.videomeetings"
        )
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName ?: return
        if (CALL_PACKAGES.contains(packageName)) {
            val extras = sbn.notification.extras
            val title = extras.getString("android.title") ?: ""
            val text = extras.getString("android.text") ?: ""

            Log.d(TAG, "Notification received from $packageName: $title - $text")

            if (title.contains("call", ignoreCase = true) || 
                text.contains("call", ignoreCase = true) || 
                text.contains("ringing", ignoreCase = true) ||
                sbn.notification.category == "call"
            ) {
                val callerName = title.ifEmpty { "Unknown Caller" }
                val platformName = when {
                    packageName.contains("whatsapp") -> "WhatsApp"
                    packageName.contains("telegram") -> "Telegram"
                    packageName.contains("zoom") -> "Zoom"
                    else -> "Cellular"
                }

                Log.i(TAG, "VoIP Call Notification Intercepted from: $callerName on $platformName")

                // Emit Native Capacitor Event for Real-Time React HUD Hydration
                TrustLineMediaPlugin.emitCallInterceptedEvent(
                    callerName = callerName,
                    callerNumber = text.ifEmpty { "+1 (800) 555-0199" },
                    platform = platformName,
                    initialRiskScore = 15,
                    state = "RINGING"
                )

                // Query Threat Intelligence Ledger Before User Answers
                queryThreatIntelLedger(callerName, packageName)

                // Trigger TrustLine Foreground Capture Service
                val serviceIntent = Intent(this, CallCaptureService::class.java).apply {
                    action = CallCaptureService.ACTION_START_CAPTURE
                    putExtra("callerName", callerName)
                    putExtra("packageName", packageName)
                }
                startService(serviceIntent)
            }
        }
    }

    private fun queryThreatIntelLedger(callerName: String, packageName: String) {
        serviceScope.launch {
            try {
                val jsonPayload = JSONObject().apply {
                    put("callerId", callerName)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = jsonPayload.toString().toRequestBody(mediaType)
                val request = Request.Builder()
                    .url("${CallCaptureService.BACKEND_URL_BASE}/api/intel/lookup")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val respStr = response.body?.string()
                        Log.i(TAG, "Threat intel lookup result for $callerName: $respStr")
                        
                        // Notify Plugin Bridge Listener if match found
                        val eventJson = JSONObject().apply {
                            put("callerName", callerName)
                            put("packageName", packageName)
                            put("intelData", respStr)
                            put("state", "RINGING")
                        }
                        TrustLineMediaPlugin.emitCallStateEvent(eventJson.toString())
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Threat intel lookup error: ${e.message}")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val packageName = sbn?.packageName ?: return
        if (CALL_PACKAGES.contains(packageName)) {
            Log.i(TAG, "Call Notification Removed for $packageName. Stopping capture.")
            val serviceIntent = Intent(this, CallCaptureService::class.java).apply {
                action = CallCaptureService.ACTION_STOP_CAPTURE
            }
            startService(serviceIntent)
        }
    }
}
