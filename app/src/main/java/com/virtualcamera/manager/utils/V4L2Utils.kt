package com.virtualcamera.manager.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object V4L2Utils {
    private const val TAG = "V4L2Utils"
    
    suspend fun checkV4L2Device(): String = withContext(Dispatchers.IO) {
        try {
            val videoDevice = File("/dev/video0")
            if (videoDevice.exists()) {
                // Try to get device information
                val deviceInfo = RootUtils.executeRootCommand("v4l2-ctl --device=/dev/video0 --info")
                return@withContext if (deviceInfo.contains("ERROR")) {
                    "Available (No Info)"
                } else {
                    "Available"
                }
            } else {
                "Not Available"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking V4L2 device", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun isV4L2LoopbackLoaded(): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = RootUtils.executeRootCommand("lsmod | grep v4l2loopback")
            !result.contains("ERROR") && result.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking v4l2loopback module", e)
            false
        }
    }
    
    suspend fun loadV4L2LoopbackModule(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Loading v4l2loopback module")
            
            val commands = listOf(
                "modprobe v4l2loopback devices=1 video_nr=0 card_label=\"Virtual Camera\" exclusive_caps=1",
                "chmod 666 /dev/video0"
            )
            
            val results = RootUtils.executeRootCommands(commands)
            val success = results.none { it.contains("ERROR") }
            
            if (success) {
                Log.d(TAG, "v4l2loopback module loaded successfully")
            } else {
                Log.e(TAG, "Failed to load v4l2loopback module: $results")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error loading v4l2loopback module", e)
            false
        }
    }
    
    suspend fun checkCameraHAL(): String = withContext(Dispatchers.IO) {
        try {
            // Check if camera HAL is configured for virtual camera
            val halCheck = RootUtils.executeRootCommand("getprop | grep camera")
            
            // This is a simplified check - actual HAL integration is more complex
            return@withContext if (halCheck.contains("ERROR")) {
                "Not Available"
            } else {
                "System Default"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Camera HAL", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun setupVirtualCameraHAL(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Setting up virtual camera HAL integration")
            
            // This would require more complex HAL configuration
            // For now, we'll just ensure the device is accessible
            val commands = listOf(
                "chmod 666 /dev/video0",
                "chown camera:camera /dev/video0"
            )
            
            val results = RootUtils.executeRootCommands(commands)
            val success = results.none { it.contains("ERROR") }
            
            if (success) {
                Log.d(TAG, "Basic HAL setup completed")
            } else {
                Log.e(TAG, "Failed to setup HAL: $results")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up Camera HAL", e)
            false
        }
    }
}