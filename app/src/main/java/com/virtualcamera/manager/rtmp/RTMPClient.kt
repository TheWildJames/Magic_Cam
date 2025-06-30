package com.virtualcamera.manager.rtmp

import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.Socket
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class RTMPClient(private val rtmpUrl: String) {
    
    companion object {
        private const val TAG = "RTMPClient"
        private const val RTMP_DEFAULT_PORT = 1935
        private const val CONNECTION_TIMEOUT = 10000
        private const val READ_TIMEOUT = 5000
    }
    
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val isConnected = AtomicBoolean(false)
    private val isReconnecting = AtomicBoolean(false)
    
    private var host: String = ""
    private var port: Int = RTMP_DEFAULT_PORT
    private var app: String = ""
    private var streamKey: String = ""
    
    init {
        parseRTMPUrl()
    }
    
    private fun parseRTMPUrl() {
        try {
            val uri = URI(rtmpUrl)
            host = uri.host ?: ""
            port = if (uri.port > 0) uri.port else RTMP_DEFAULT_PORT
            
            val path = uri.path?.removePrefix("/") ?: ""
            val pathParts = path.split("/")
            
            if (pathParts.isNotEmpty()) {
                app = pathParts[0]
                if (pathParts.size > 1) {
                    streamKey = pathParts.drop(1).joinToString("/")
                }
            }
            
            Log.d(TAG, "Parsed RTMP URL - Host: $host, Port: $port, App: $app, Stream: $streamKey")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse RTMP URL: $rtmpUrl", e)
        }
    }
    
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Connecting to RTMP server: $host:$port")
            
            socket = Socket().apply {
                soTimeout = READ_TIMEOUT
                connect(java.net.InetSocketAddress(host, port), CONNECTION_TIMEOUT)
            }
            
            inputStream = socket?.getInputStream()
            outputStream = socket?.getOutputStream()
            
            // Perform RTMP handshake
            if (performHandshake()) {
                isConnected.set(true)
                Log.d(TAG, "Successfully connected to RTMP server")
                return@withContext true
            } else {
                Log.e(TAG, "RTMP handshake failed")
                disconnect()
                return@withContext false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to RTMP server", e)
            disconnect()
            return@withContext false
        }
    }
    
    private suspend fun performHandshake(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Simplified RTMP handshake implementation
            // In a production environment, you'd want a full RTMP implementation
            
            val c0c1 = ByteArray(1537)
            c0c1[0] = 0x03 // RTMP version
            
            // Fill C1 with random data and timestamp
            val timestamp = (System.currentTimeMillis() / 1000).toInt()
            val timestampBytes = ByteBuffer.allocate(4).putInt(timestamp).array()
            System.arraycopy(timestampBytes, 0, c0c1, 1, 4)
            
            // Send C0+C1
            outputStream?.write(c0c1)
            outputStream?.flush()
            
            // Read S0+S1+S2
            val s0s1s2 = ByteArray(3073)
            var totalRead = 0
            while (totalRead < s0s1s2.size) {
                val bytesRead = inputStream?.read(s0s1s2, totalRead, s0s1s2.size - totalRead) ?: -1
                if (bytesRead == -1) break
                totalRead += bytesRead
            }
            
            if (totalRead < 3073) {
                Log.e(TAG, "Incomplete handshake response: $totalRead bytes")
                return@withContext false
            }
            
            // Send C2 (echo of S1)
            val c2 = ByteArray(1536)
            System.arraycopy(s0s1s2, 1, c2, 0, 1536)
            outputStream?.write(c2)
            outputStream?.flush()
            
            Log.d(TAG, "RTMP handshake completed successfully")
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "RTMP handshake failed", e)
            return@withContext false
        }
    }
    
    suspend fun readPacket(): RTMPPacket? = withContext(Dispatchers.IO) {
        try {
            if (!isConnected.get()) return@withContext null
            
            // Read RTMP packet header
            val headerByte = inputStream?.read() ?: return@withContext null
            if (headerByte == -1) return@withContext null
            
            val chunkStreamId = headerByte and 0x3F
            val headerType = (headerByte shr 6) and 0x03
            
            // Read timestamp, message length, message type, and stream ID based on header type
            val packet = RTMPPacket()
            packet.chunkStreamId = chunkStreamId
            packet.headerType = headerType
            
            when (headerType) {
                0 -> {
                    // Type 0: 11 bytes
                    val header = ByteArray(11)
                    if (inputStream?.read(header) != 11) return@withContext null
                    
                    packet.timestamp = ByteBuffer.wrap(header, 0, 3).int and 0xFFFFFF
                    packet.messageLength = ByteBuffer.wrap(header, 3, 3).int and 0xFFFFFF
                    packet.messageType = header[6].toInt() and 0xFF
                    packet.streamId = ByteBuffer.wrap(header, 7, 4).int
                }
                1 -> {
                    // Type 1: 7 bytes
                    val header = ByteArray(7)
                    if (inputStream?.read(header) != 7) return@withContext null
                    
                    packet.timestamp = ByteBuffer.wrap(header, 0, 3).int and 0xFFFFFF
                    packet.messageLength = ByteBuffer.wrap(header, 3, 3).int and 0xFFFFFF
                    packet.messageType = header[6].toInt() and 0xFF
                }
                2 -> {
                    // Type 2: 3 bytes
                    val header = ByteArray(3)
                    if (inputStream?.read(header) != 3) return@withContext null
                    
                    packet.timestamp = ByteBuffer.wrap(header, 0, 3).int and 0xFFFFFF
                }
                3 -> {
                    // Type 3: 0 bytes (use previous packet info)
                }
            }
            
            // Read message payload
            if (packet.messageLength > 0) {
                packet.payload = ByteArray(packet.messageLength)
                var totalRead = 0
                while (totalRead < packet.messageLength) {
                    val bytesRead = inputStream?.read(packet.payload, totalRead, packet.messageLength - totalRead) ?: -1
                    if (bytesRead == -1) break
                    totalRead += bytesRead
                }
                
                if (totalRead < packet.messageLength) {
                    Log.w(TAG, "Incomplete packet payload: $totalRead/${packet.messageLength} bytes")
                    return@withContext null
                }
            }
            
            return@withContext packet
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read RTMP packet", e)
            return@withContext null
        }
    }
    
    suspend fun reconnect(): Boolean = withContext(Dispatchers.IO) {
        if (isReconnecting.get()) return@withContext false
        
        isReconnecting.set(true)
        try {
            Log.d(TAG, "Attempting to reconnect to RTMP server")
            disconnect()
            delay(2000) // Wait before reconnecting
            return@withContext connect()
        } finally {
            isReconnecting.set(false)
        }
    }
    
    fun disconnect() {
        try {
            isConnected.set(false)
            inputStream?.close()
            outputStream?.close()
            socket?.close()
            
            inputStream = null
            outputStream = null
            socket = null
            
            Log.d(TAG, "Disconnected from RTMP server")
        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect", e)
        }
    }
    
    fun isConnected(): Boolean = isConnected.get()
}

data class RTMPPacket(
    var chunkStreamId: Int = 0,
    var headerType: Int = 0,
    var timestamp: Int = 0,
    var messageLength: Int = 0,
    var messageType: Int = 0,
    var streamId: Int = 0,
    var payload: ByteArray = ByteArray(0)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RTMPPacket

        if (chunkStreamId != other.chunkStreamId) return false
        if (headerType != other.headerType) return false
        if (timestamp != other.timestamp) return false
        if (messageLength != other.messageLength) return false
        if (messageType != other.messageType) return false
        if (streamId != other.streamId) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = chunkStreamId
        result = 31 * result + headerType
        result = 31 * result + timestamp
        result = 31 * result + messageLength
        result = 31 * result + messageType
        result = 31 * result + streamId
        result = 31 * result + payload.contentHashCode()
        return result
    }
}