package com.virtualcamera.manager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.virtualcamera.manager.R
import com.virtualcamera.manager.utils.V4L2Utils
import kotlinx.coroutines.*

class VirtualCameraService : Service() {
    
    companion object {
        private const val TAG = "VirtualCameraService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "virtual_camera_channel"
    }
    
    private var serviceJob: Job? = null
    private var isStreaming = false
    private var videoSource = "Test Pattern"
    private var rtmpUrl = ""
    private var rtmpFrameReceiver: BroadcastReceiver? = null
    
    // Native methods
    external fun initV4L2Device(devicePath: String): Boolean
    external fun startFrameStreaming(pattern: Int): Boolean
    external fun stopFrameStreaming(): Boolean
    external fun closeV4L2Device(): Boolean
    external fun pushRTMPFrame(frameData: ByteArray): Boolean
    
    companion object {
        init {
            try {
                System.loadLibrary("v4l2camera")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupRTMPFrameReceiver()
        Log.d(TAG, "VirtualCameraService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "VirtualCameraService started")
        
        intent?.getStringExtra("video_source")?.let {
            videoSource = it
        }
        
        intent?.getStringExtra("rtmp_url")?.let {
            rtmpUrl = it
        }
        
        startForeground(NOTIFICATION_ID, createNotification())
        
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                setupVirtualCamera()
                startCameraStreaming()
            } catch (e: Exception) {
                Log.e(TAG, "Error in camera service", e)
                stopSelf()
            }
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        Log.d(TAG, "VirtualCameraService destroyed")
        serviceJob?.cancel()
        stopCameraStreaming()
        rtmpFrameReceiver?.let {
            unregisterReceiver(it)
        }
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun setupRTMPFrameReceiver() {
        rtmpFrameReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.virtualcamera.manager.RTMP_FRAME") {
                    val frameData = intent.getByteArrayExtra("frame_data")
                    frameData?.let {
                        // Push RTMP frame to virtual camera
                        pushRTMPFrame(it)
                    }
                }
            }
        }
        
        val filter = IntentFilter("com.virtualcamera.manager.RTMP_FRAME")
        registerReceiver(rtmpFrameReceiver, filter)
    }
    
    private suspend fun setupVirtualCamera() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Setting up virtual camera")
        
        // Load v4l2loopback module if not already loaded
        if (!V4L2Utils.isV4L2LoopbackLoaded()) {
            Log.d(TAG, "Loading v4l2loopback module")
            V4L2Utils.loadV4L2LoopbackModule()
        }
        
        // Initialize V4L2 device
        val devicePath = "/dev/video0"
        if (!initV4L2Device(devicePath)) {
            throw RuntimeException("Failed to initialize V4L2 device: $devicePath")
        }
        
        Log.d(TAG, "V4L2 device initialized successfully")
    }
    
    private suspend fun startCameraStreaming() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting camera streaming with source: $videoSource")
        
        val pattern = when (videoSource) {
            "Test Pattern" -> 0
            "Color Bars" -> 1
            "Image File" -> 2
            "Video File" -> 3
            "RTMP Stream" -> 4
            else -> 0
        }
        
        if (startFrameStreaming(pattern)) {
            isStreaming = true
            Log.d(TAG, "Frame streaming started successfully")
            
            // If RTMP source, start RTMP service
            if (videoSource == "RTMP Stream" && rtmpUrl.isNotEmpty()) {
                val rtmpIntent = Intent(this@VirtualCameraService, RTMPStreamService::class.java)
                rtmpIntent.putExtra("rtmp_url", rtmpUrl)
                startForegroundService(rtmpIntent)
            }
            
            // Keep service alive while streaming
            while (isStreaming && serviceJob?.isActive == true) {
                delay(1000)
                // Update notification or perform periodic tasks
            }
        } else {
            throw RuntimeException("Failed to start frame streaming")
        }
    }
    
    private fun stopCameraStreaming() {
        if (isStreaming) {
            Log.d(TAG, "Stopping camera streaming")
            stopFrameStreaming()
            closeV4L2Device()
            isStreaming = false
            
            // Stop RTMP service if running
            val rtmpIntent = Intent(this, RTMPStreamService::class.java)
            stopService(rtmpIntent)
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Virtual Camera Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Virtual camera streaming service"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val sourceText = if (videoSource == "RTMP Stream" && rtmpUrl.isNotEmpty()) {
            "RTMP: ${rtmpUrl.take(30)}..."
        } else {
            videoSource
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Virtual Camera Active")
            .setContentText("Streaming $sourceText to system camera")
            .setSmallIcon(R.drawable.ic_camera_notification)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}