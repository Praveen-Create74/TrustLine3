package com.trustline.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Base64
import android.util.Log
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

@CapacitorPlugin(name = "TrustLineMediaPlugin")
class TrustLineMediaPlugin : Plugin(), CallCaptureService.MediaChunkListener {

    override fun load() {
        super.load()
        instance = this
        CallCaptureService.listener = this
    }

    @PluginMethod
    fun requestMediaProjection(call: PluginCall) {
        val activity = activity ?: run {
            call.reject("Activity unavailable")
            return
        }

        val projectionManager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val permissionIntent = projectionManager.createScreenCaptureIntent()

        startActivityForResult(call, permissionIntent, "handleMediaProjectionResult")
    }

    @PluginMethod
    fun handleMediaProjectionResult(call: PluginCall) {
        val resultCode = call.getInt("resultCode") ?: Activity.RESULT_CANCELED

        if (resultCode == Activity.RESULT_OK && pendingIntentData != null) {
            val serviceIntent = Intent(context, CallCaptureService::class.java).apply {
                action = CallCaptureService.ACTION_START_CAPTURE
                putExtra(CallCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(CallCaptureService.EXTRA_RESULT_DATA, pendingIntentData)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            call.resolve(JSObject().put("status", "started"))
        } else {
            call.reject("Media projection permission denied")
        }
    }

    @PluginMethod
    fun stopCaptureService(call: PluginCall) {
        val serviceIntent = Intent(context, CallCaptureService::class.java).apply {
            action = CallCaptureService.ACTION_STOP_CAPTURE
        }
        context.startService(serviceIntent)
        call.resolve(JSObject().put("status", "stopped"))
    }

    @PluginMethod
    fun checkOverlayPermission(call: PluginCall) {
        val hasPermission = Settings.canDrawOverlays(context)
        call.resolve(JSObject().put("granted", hasPermission))
    }

    @PluginMethod
    fun requestOverlayPermission(call: PluginCall) {
        if (!Settings.canDrawOverlays(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            activity.startActivity(intent)
        }
        call.resolve()
    }

    @PluginMethod
    fun triggerHapticAlert(call: PluginCall) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 250, 150, 250, 150, 500),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        }
        call.resolve()
    }

    @PluginMethod
    fun processQuickScanFile(call: PluginCall) {
        val fileUriStr = call.getString("filePath") ?: call.getString("fileUri")
        val mimeType = call.getString("mimeType") ?: "audio/mpeg"
        val fileName = call.getString("fileName") ?: "quick_scan_media"

        if (fileUriStr == null) {
            call.reject("Missing fileUri/filePath parameter")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val uri = Uri.parse(fileUriStr)
                
                // 1. Copy the URI safely to a temporary cache file to avoid RAM OOM crashes
                val tempFile = File(context.cacheDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output) // Streams data without loading it all into memory
                    }
                }

                val sizeMbStr = String.format(Locale.US, "%.2f MB", tempFile.length() / (1024.0 * 1024.0))
                val mediaCategory = if (mimeType.startsWith("video/")) "video" else if (mimeType.startsWith("image/")) "image" else "audio"

                // Target URL: http://10.0.2.2:3000 for Android Emulator
                val targetUrl = "${CallCaptureService.BACKEND_URL_BASE}/api/forensics/quick-scan"
                Log.i("TrustLineNet", "Dispatching QuickScan MULTIPART request to $targetUrl (Size: $sizeMbStr)")

                // 2. Build a Multipart request (No Base64 String Inflation!)
                val mediaType = mimeType.toMediaTypeOrNull()
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("type", mediaCategory)
                    .addFormDataPart("fileName", fileName)
                    .addFormDataPart("mimeType", mimeType)
                    .addFormDataPart("fileSize", sizeMbStr)
                    .addFormDataPart(
                        "media", // This MUST match the Multer key in your Node.js server
                        fileName,
                        tempFile.asRequestBody(mediaType)
                    )
                    .build()

                val request = Request.Builder()
                    .url(targetUrl)
                    .header("Accept", "application/json")
                    .post(requestBody)
                    .build()

                // 3. Set realistic timeouts for a 50MB upload (120 seconds)
                val httpClient = OkHttpClient.Builder()
                    .connectTimeout(120, TimeUnit.SECONDS)
                    .writeTimeout(120, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val respBody = response.body?.string() ?: "{}"
                        Log.i("TrustLineNet", "QuickScan response received: $respBody")
                        call.resolve(JSObject(respBody))
                    } else {
                        Log.e("TrustLineNet", "Backend returned HTTP code: ${response.code} ${response.message}")
                        call.reject("Backend returned HTTP code: ${response.code}")
                    }
                }
                
                // Clean up the temp file
                if (tempFile.exists()) tempFile.delete()

            } catch (e: Exception) {
                Log.e("TrustLineNet", "Error processing QuickScan request: ${e.message}", e)
                call.reject("Error processing QuickScan file: ${e.message}")
            }
        }
    }

    override fun onChunkAvailable(audioBase64: String, videoBase64: String?, dspLoad: Double, latencyMs: Long) {
        val eventData = JSObject().apply {
            put("audioChunk", audioBase64)
            put("videoChunk", videoBase64)
            put("dspLoad", dspLoad)
            put("latencyMs", latencyMs)
            put("timestamp", System.currentTimeMillis())
        }
        notifyListeners("onMediaChunk", eventData)
    }

    companion object {
        var pendingIntentData: Intent? = null
        var instance: TrustLineMediaPlugin? = null

        fun emitCallInterceptedEvent(
            callerName: String,
            callerNumber: String,
            platform: String,
            initialRiskScore: Int,
            state: String = "RINGING"
        ) {
            instance?.let { plugin ->
                val eventData = JSObject().apply {
                    put("callerName", callerName)
                    put("callerNumber", callerNumber)
                    put("platform", platform)
                    put("initialRiskScore", initialRiskScore)
                    put("state", state)
                    put("timestamp", System.currentTimeMillis())
                }
                Log.i("TrustLineMediaPlugin", "Emitting onCallIntercepted event: $eventData")
                plugin.notifyListeners("onCallIntercepted", eventData)
            }
        }

        fun emitCallStateEvent(jsonStr: String) {
            instance?.let { plugin ->
                val eventData = JSObject().apply {
                    put("payload", jsonStr)
                    put("timestamp", System.currentTimeMillis())
                }
                plugin.notifyListeners("onCallStateChanged", eventData)
            }
        }
    }
}
