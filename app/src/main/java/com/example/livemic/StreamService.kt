package com.example.livemic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * StreamService manages the lifecycle of audio streaming as a foreground service.
 *
 * This service runs in the foreground with a persistent notification, allowing
 * continuous audio capture and playback even when the app is backgrounded.
 *
 * Responsibilities:
 * - Create and manage AudioStreamer instance
 * - Display foreground service notification
 * - Handle START and STOP intents
 * - Ensure proper resource cleanup on termination
 */
class StreamService : Service() {

    companion object {
        const val CHANNEL_ID = "livemic_channel"
        const val NOTIF_ID = 1001
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val TAG = "StreamService"
    }

    private var streamer: AudioStreamer? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        when (intent?.action) {
            ACTION_START -> startStreaming()
            ACTION_STOP -> stopStreaming()
        }

        // Return START_STICKY to ensure service restarts if killed
        return START_STICKY
    }

    /**
     * Start audio streaming with foreground service notification
     */
    private fun startStreaming() {
        if (streamer != null) {
            Log.w(TAG, "Audio streamer already running")
            return
        }

        try {
            val notification = buildNotification("Audio streaming active")
            startForeground(NOTIF_ID, notification)

            streamer = AudioStreamer(this)
            streamer?.start()

            Log.i(TAG, "Audio streaming started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start streaming", e)
            stopSelf()
        }
    }

    /**
     * Stop audio streaming and remove foreground service notification
     */
    private fun stopStreaming() {
        if (streamer == null) {
            Log.w(TAG, "Audio streamer not running")
            return
        }

        try {
            streamer?.stop()
            streamer = null

            stopForeground(STOP_FOREGROUND_REMOVE)
            Log.i(TAG, "Audio streaming stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping streaming", e)
        } finally {
            stopSelf()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        stopStreaming()
        super.onDestroy()
    }

    /**
     * Build notification for foreground service
     */
    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LiveMic")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)  // Prevent swipe-to-dismiss
            .build()
    }

    /**
     * Create notification channel (required for Android 8+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LiveMic Audio Streaming",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Audio streaming to Bluetooth device"

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)

            Log.d(TAG, "Notification channel created")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
