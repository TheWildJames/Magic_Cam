package com.virtualcamera.manager

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.virtualcamera.manager.databinding.ActivityMainBinding
import com.virtualcamera.manager.service.VirtualCameraService
import com.virtualcamera.manager.service.RTMPStreamService
import com.virtualcamera.manager.utils.RootUtils
import com.virtualcamera.manager.utils.V4L2Utils
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private var isServiceRunning = false
    private var isRTMPServiceRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        checkSystemStatus()
    }
    
    private fun setupUI() {
        // Setup video source dropdown
        val videoSources = arrayOf("Test Pattern", "Color Bars", "Image File", "Video File", "RTMP Stream")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, videoSources)
        (binding.videoSourceSpinner as AutoCompleteTextView).setAdapter(adapter)
        
        // Setup RTMP URL input visibility
        binding.videoSourceSpinner.setOnItemClickListener { _, _, position, _ ->
            val isRTMP = videoSources[position] == "RTMP Stream"
            binding.rtmpUrlLayout.visibility = if (isRTMP) android.view.View.VISIBLE else android.view.View.GONE
        }
        
        binding.startButton.setOnClickListener {
            startVirtualCamera()
        }
        
        binding.stopButton.setOnClickListener {
            stopVirtualCamera()
        }
        
        binding.startRtmpButton.setOnClickListener {
            startRTMPService()
        }
        
        binding.stopRtmpButton.setOnClickListener {
            stopRTMPService()
        }
        
        binding.logsButton.setOnClickListener {
            // TODO: Open logs activity
            appendLog("Logs feature coming soon...")
        }
    }
    
    private fun checkSystemStatus() {
        lifecycleScope.launch {
            appendLog("Checking system status...")
            
            // Check root access
            val hasRoot = RootUtils.checkRootAccess()
            updateRootStatus(hasRoot)
            
            if (hasRoot) {
                // Check V4L2 device
                val v4l2Status = V4L2Utils.checkV4L2Device()
                updateV4L2Status(v4l2Status)
                
                // Check HAL status
                val halStatus = V4L2Utils.checkCameraHAL()
                updateHALStatus(halStatus)
            } else {
                appendLog("Root access required for full functionality")
            }
        }
    }
    
    private fun updateRootStatus(hasRoot: Boolean) {
        binding.rootStatusText.text = if (hasRoot) {
            getString(R.string.root_granted)
        } else {
            getString(R.string.root_denied)
        }
        binding.rootStatusText.setTextColor(
            getColor(if (hasRoot) R.color.success else R.color.error)
        )
        appendLog("Root access: ${if (hasRoot) "GRANTED" else "DENIED"}")
    }
    
    private fun updateV4L2Status(status: String) {
        binding.v4l2StatusText.text = status
        val isAvailable = status.contains("Available")
        binding.v4l2StatusText.setTextColor(
            getColor(if (isAvailable) R.color.success else R.color.error)
        )
        appendLog("V4L2 device: $status")
    }
    
    private fun updateHALStatus(status: String) {
        binding.halStatusText.text = status
        val isConfigured = status.contains("Configured")
        binding.halStatusText.setTextColor(
            getColor(if (isConfigured) R.color.success else R.color.warning)
        )
        appendLog("Camera HAL: $status")
    }
    
    private fun startVirtualCamera() {
        if (!RootUtils.checkRootAccess()) {
            appendLog("ERROR: Root access required")
            return
        }
        
        val videoSource = binding.videoSourceSpinner.text.toString()
        appendLog("Starting virtual camera service with source: $videoSource")
        
        val intent = Intent(this, VirtualCameraService::class.java)
        intent.putExtra("video_source", videoSource)
        
        if (videoSource == "RTMP Stream") {
            val rtmpUrl = binding.rtmpUrlInput.text.toString().trim()
            if (rtmpUrl.isEmpty()) {
                appendLog("ERROR: RTMP URL is required")
                return
            }
            intent.putExtra("rtmp_url", rtmpUrl)
        }
        
        startForegroundService(intent)
        
        isServiceRunning = true
        binding.startButton.isEnabled = false
        binding.stopButton.isEnabled = true
        appendLog("Virtual camera service started")
    }
    
    private fun stopVirtualCamera() {
        appendLog("Stopping virtual camera service...")
        val intent = Intent(this, VirtualCameraService::class.java)
        stopService(intent)
        
        isServiceRunning = false
        binding.startButton.isEnabled = true
        binding.stopButton.isEnabled = false
        appendLog("Virtual camera service stopped")
    }
    
    private fun startRTMPService() {
        val rtmpUrl = binding.rtmpUrlInput.text.toString().trim()
        if (rtmpUrl.isEmpty()) {
            appendLog("ERROR: RTMP URL is required")
            return
        }
        
        appendLog("Starting RTMP stream service...")
        val intent = Intent(this, RTMPStreamService::class.java)
        intent.putExtra("rtmp_url", rtmpUrl)
        startForegroundService(intent)
        
        isRTMPServiceRunning = true
        binding.startRtmpButton.isEnabled = false
        binding.stopRtmpButton.isEnabled = true
        appendLog("RTMP stream service started")
    }
    
    private fun stopRTMPService() {
        appendLog("Stopping RTMP stream service...")
        val intent = Intent(this, RTMPStreamService::class.java)
        stopService(intent)
        
        isRTMPServiceRunning = false
        binding.startRtmpButton.isEnabled = true
        binding.stopRtmpButton.isEnabled = false
        appendLog("RTMP stream service stopped")
    }
    
    private fun appendLog(message: String) {
        runOnUiThread {
            val currentText = binding.statusLog.text.toString()
            val timestamp = System.currentTimeMillis()
            val newText = "$currentText\n[${timestamp % 100000}] $message"
            binding.statusLog.text = newText
            
            // Scroll to bottom
            binding.statusLog.post {
                val scrollAmount = binding.statusLog.layout?.getLineTop(binding.statusLog.lineCount) ?: 0
                if (scrollAmount > binding.statusLog.height) {
                    binding.statusLog.scrollTo(0, scrollAmount - binding.statusLog.height)
                }
            }
        }
    }
}