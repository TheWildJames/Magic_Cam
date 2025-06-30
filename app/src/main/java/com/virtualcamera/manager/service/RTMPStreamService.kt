package com.virtualcamera.manager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.virtualcamera.manager.R
import com.virtualcamera.manager.rtmp.RTMPClient
import com.virtualcamera.manager.rtmp.RTMPFrameProcessor
import kotlinx.coroutines.*

class RTMPStreamService : Service() {
    
    companion object {
        private const val TAG = "RTMPStreamService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "rtmp_stream_channel"
    }
    
    private var serviceJob: Job? = null
    private var rtmpClient: RTMPClient? = null
    private var frameProcessor: RTMPFrameProcessor? = null
    private var rtmpUrl = ""
    private var isStreaming = false
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "RTMPStreamService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "RTMPStreamService started")
        
        intent?.getStringExtra("rtmp_url")?.let {
            rtmpUrl = it
        }
        
        if (rtmpUrl.isEmpty()) {
            Log.e(TAG, "RTMP URL is required")
            stopSelf()
            return START_NOT_STICKY
        }
        
        startForeground(NOTIFICATION_ID, createNotification())
        
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                setupRTMPStream()
                startRTMPStreaming()
            } catch (e: Exception) {
                Log.e(TAG, "Error in RTMP service", e)
                stopSelf()
            }
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        Log.d(TAG, "RTMPStreamService destroyed")
        serviceJob?.cancel()
        stopRTMPStreaming()
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private suspend fun setupRTMPStream() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Setting up RTMP stream: $rtmpUrl")
        
        rtmpClient = RTMPClient(rtmpUrl)
        frameProcessor = RTMPFrameProcessor()
        
        if (!rtmpClient!!.connect()) {
            throw RuntimeException("Failed to connect to RTMP server: $rtmpUrl")
        }
        
        Log.d(TAG, "RTMP stream setup completed")
    }
    
    private suspend fun startRTMPStreaming() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting RTMP streaming")
        
        isStreaming = true
        
        frameProcessor?.let { processor ->
            rtmpClient?.let { client ->
                processor.startProcessing(client) { frameData ->
                    // This callback will be called for each processed frame
                    // The frame data can be forwarded to the virtual camera
                    onFrameReceived(frameData)
                }
            }
        }
        
        // Keep service alive while streaming
        while (isStreaming && serviceJob?.isActive == true) {
            delay(1000)
            // Monitor connection status and reconnect if needed
            if (rtmpClient?.isConnected() == false) {
                Log.w(TAG, "RTMP connection lost, attempting reconnect...")
                if (!rtmpClient!!.reconnect()) {
                    Log.e(TAG, "Failed to reconnect to RTMP server")
                    break
                }
            }
        }
    }
    
    private fun onFrameReceived(frameData: ByteArray) {
        // Forward frame data to virtual camera service
        // This would integrate with the VirtualCameraService
        Log.d(TAG, "Received frame: ${frameData.size} bytes")
        
        // Send broadcast to VirtualCameraService with frame data
        val intent = Intent("com.virtualcamera.manager.RTMP_FRAME")
        intent.putExtra("frame_data", frameData)
        sendBroadcast(intent)
    }
    
    private fun stopRTMPStreaming() {
        if (isStreaming) {
            Log.d(TAG, "Stopping RTMP streaming")
            isStreaming = false
            frameProcessor?.stopProcessing()
            rtmpClient?.disconnect()
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RTMP Stream Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "RTMP stream processing service"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RTMP Stream Active")
            .setContentText("Processing stream from $rtmpUrl")
            .setSmallIcon(R.drawable.ic_stream_notification)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}