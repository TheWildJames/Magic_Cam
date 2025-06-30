package com.virtualcamera.manager.rtmp

import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class RTMPFrameProcessor {
    
    companion object {
        private const val TAG = "RTMPFrameProcessor"
        
        // RTMP message types
        private const val RTMP_MSG_AUDIO = 8
        private const val RTMP_MSG_VIDEO = 9
        private const val RTMP_MSG_DATA = 18
        
        // Video frame types
        private const val VIDEO_FRAME_KEY = 1
        private const val VIDEO_FRAME_INTER = 2
        
        // Video codecs
        private const val VIDEO_CODEC_H264 = 7
    }
    
    private var processingJob: Job? = null
    private val isProcessing = AtomicBoolean(false)
    private var frameCallback: ((ByteArray) -> Unit)? = null
    
    fun startProcessing(rtmpClient: RTMPClient, onFrameReceived: (ByteArray) -> Unit) {
        if (isProcessing.get()) {
            Log.w(TAG, "Frame processing already started")
            return
        }
        
        frameCallback = onFrameReceived
        isProcessing.set(true)
        
        processingJob = CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "Starting RTMP frame processing")
            
            try {
                while (isProcessing.get() && rtmpClient.isConnected()) {
                    val packet = rtmpClient.readPacket()
                    
                    if (packet != null) {
                        processPacket(packet)
                    } else {
                        // No packet received, small delay to prevent busy waiting
                        delay(10)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in frame processing loop", e)
            } finally {
                Log.d(TAG, "Frame processing stopped")
            }
        }
    }
    
    fun stopProcessing() {
        isProcessing.set(false)
        processingJob?.cancel()
        frameCallback = null
        Log.d(TAG, "Frame processing stopped")
    }
    
    private fun processPacket(packet: RTMPPacket) {
        when (packet.messageType) {
            RTMP_MSG_VIDEO -> {
                processVideoPacket(packet)
            }
            RTMP_MSG_AUDIO -> {
                processAudioPacket(packet)
            }
            RTMP_MSG_DATA -> {
                processDataPacket(packet)
            }
            else -> {
                Log.v(TAG, "Ignoring packet type: ${packet.messageType}")
            }
        }
    }
    
    private fun processVideoPacket(packet: RTMPPacket) {
        if (packet.payload.isEmpty()) return
        
        try {
            val firstByte = packet.payload[0].toInt() and 0xFF
            val frameType = (firstByte shr 4) and 0x0F
            val codecId = firstByte and 0x0F
            
            Log.v(TAG, "Video packet - Frame type: $frameType, Codec: $codecId, Size: ${packet.payload.size}")
            
            if (codecId == VIDEO_CODEC_H264) {
                processH264Packet(packet, frameType)
            } else {
                Log.w(TAG, "Unsupported video codec: $codecId")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing video packet", e)
        }
    }
    
    private fun processH264Packet(packet: RTMPPacket, frameType: Int) {
        if (packet.payload.size < 5) return
        
        try {
            val avcPacketType = packet.payload[1].toInt() and 0xFF
            val compositionTime = ((packet.payload[2].toInt() and 0xFF) shl 16) or
                                 ((packet.payload[3].toInt() and 0xFF) shl 8) or
                                 (packet.payload[4].toInt() and 0xFF)
            
            when (avcPacketType) {
                0 -> {
                    // AVC sequence header (SPS/PPS)
                    Log.d(TAG, "Received AVC sequence header")
                    processAVCSequenceHeader(packet.payload)
                }
                1 -> {
                    // AVC NALU
                    Log.v(TAG, "Received AVC NALU, frame type: $frameType, composition time: $compositionTime")
                    processAVCNALU(packet.payload, frameType)
                }
                2 -> {
                    // AVC end of sequence
                    Log.d(TAG, "Received AVC end of sequence")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing H264 packet", e)
        }
    }
    
    private fun processAVCSequenceHeader(payload: ByteArray) {
        // Extract SPS/PPS from AVC sequence header
        // This is needed for proper H.264 decoding
        Log.d(TAG, "Processing AVC sequence header, size: ${payload.size}")
        
        // For now, we'll just log the sequence header
        // In a full implementation, you'd parse and store SPS/PPS
    }
    
    private fun processAVCNALU(payload: ByteArray, frameType: Int) {
        if (payload.size < 9) return // Minimum size for NALU data
        
        try {
            // Extract NALU data (skip RTMP video header)
            val naluData = payload.copyOfRange(5, payload.size)
            
            // Convert frame to RGB format for virtual camera
            val rgbFrame = convertH264ToRGB(naluData, frameType)
            
            if (rgbFrame != null) {
                frameCallback?.invoke(rgbFrame)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing AVC NALU", e)
        }
    }
    
    private fun convertH264ToRGB(naluData: ByteArray, frameType: Int): ByteArray? {
        // This is a placeholder for H.264 to RGB conversion
        // In a real implementation, you would:
        // 1. Use MediaCodec to decode H.264 frames
        // 2. Convert YUV to RGB
        // 3. Resize to target resolution if needed
        
        Log.v(TAG, "Converting H264 frame to RGB, size: ${naluData.size}, type: $frameType")
        
        // For now, return a placeholder RGB frame (640x480x3)
        val width = 640
        val height = 480
        val rgbFrame = ByteArray(width * height * 3)
        
        // Generate a simple test pattern based on frame type
        val color = if (frameType == VIDEO_FRAME_KEY) 255 else 128
        for (i in rgbFrame.indices step 3) {
            rgbFrame[i] = color.toByte()     // R
            rgbFrame[i + 1] = (color / 2).toByte() // G
            rgbFrame[i + 2] = (color / 4).toByte() // B
        }
        
        return rgbFrame
    }
    
    private fun processAudioPacket(packet: RTMPPacket) {
        // Audio processing not needed for video-only virtual camera
        Log.v(TAG, "Audio packet received, size: ${packet.payload.size}")
    }
    
    private fun processDataPacket(packet: RTMPPacket) {
        // Process metadata packets
        Log.v(TAG, "Data packet received, size: ${packet.payload.size}")
        
        // These packets often contain stream metadata like resolution, framerate, etc.
        // You could parse this information to configure the virtual camera accordingly
    }
}