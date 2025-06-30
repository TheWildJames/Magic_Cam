#include <jni.h>
#include <string>
#include <android/log.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <linux/videodev2.h>
#include <cstring>
#include <thread>
#include <atomic>
#include <queue>
#include <mutex>
#include "frame_generator.h"

#define LOG_TAG "V4L2Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static int v4l2_fd = -1;
static std::atomic<bool> streaming(false);
static std::thread streaming_thread;
static FrameGenerator* frame_generator = nullptr;

// RTMP frame queue
static std::queue<std::vector<uint8_t>> rtmp_frame_queue;
static std::mutex rtmp_queue_mutex;
static std::atomic<bool> use_rtmp_frames(false);

extern "C" JNIEXPORT jboolean JNICALL
Java_com_virtualcamera_manager_service_VirtualCameraService_initV4L2Device(
        JNIEnv *env, jobject /* this */, jstring device_path) {
    
    const char *path = env->GetStringUTFChars(device_path, nullptr);
    LOGI("Initializing V4L2 device: %s", path);
    
    // Open the device
    v4l2_fd = open(path, O_RDWR | O_NONBLOCK);
    if (v4l2_fd < 0) {
        LOGE("Failed to open device %s: %s", path, strerror(errno));
        env->ReleaseStringUTFChars(device_path, path);
        return JNI_FALSE;
    }
    
    // Query device capabilities
    struct v4l2_capability cap;
    if (ioctl(v4l2_fd, VIDIOC_QUERYCAP, &cap) < 0) {
        LOGE("Failed to query device capabilities: %s", strerror(errno));
        close(v4l2_fd);
        v4l2_fd = -1;
        env->ReleaseStringUTFChars(device_path, path);
        return JNI_FALSE;
    }
    
    LOGI("Device: %s", cap.card);
    LOGI("Driver: %s", cap.driver);
    LOGI("Version: %u.%u.%u", (cap.version >> 16) & 0xFF, 
         (cap.version >> 8) & 0xFF, cap.version & 0xFF);
    
    // Set format
    struct v4l2_format fmt;
    memset(&fmt, 0, sizeof(fmt));
    fmt.type = V4L2_BUF_TYPE_VIDEO_OUTPUT;
    fmt.fmt.pix.width = 640;
    fmt.fmt.pix.height = 480;
    fmt.fmt.pix.pixelformat = V4L2_PIX_FMT_RGB24;
    fmt.fmt.pix.field = V4L2_FIELD_NONE;
    
    if (ioctl(v4l2_fd, VIDIOC_S_FMT, &fmt) < 0) {
        LOGE("Failed to set format: %s", strerror(errno));
        close(v4l2_fd);
        v4l2_fd = -1;
        env->ReleaseStringUTFChars(device_path, path);
        return JNI_FALSE;
    }
    
    LOGI("V4L2 device initialized successfully");
    env->ReleaseStringUTFChars(device_path, path);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_virtualcamera_manager_service_VirtualCameraService_startFrameStreaming(
        JNIEnv *env, jobject /* this */, jint pattern) {
    
    if (v4l2_fd < 0) {
        LOGE("V4L2 device not initialized");
        return JNI_FALSE;
    }
    
    if (streaming.load()) {
        LOGI("Streaming already active");
        return JNI_TRUE;
    }
    
    LOGI("Starting frame streaming with pattern: %d", pattern);
    
    // Check if RTMP mode
    if (pattern == 4) {
        use_rtmp_frames.store(true);
        LOGI("RTMP streaming mode enabled");
    } else {
        use_rtmp_frames.store(false);
        // Create frame generator for non-RTMP sources
        frame_generator = new FrameGenerator(640, 480, static_cast<FramePattern>(pattern));
    }
    
    streaming.store(true);
    
    // Start streaming thread
    streaming_thread = std::thread([]() {
        const size_t frame_size = 640 * 480 * 3; // RGB24
        uint8_t* frame_buffer = new uint8_t[frame_size];
        
        LOGI("Streaming thread started");
        
        while (streaming.load()) {
            bool frame_ready = false;
            
            if (use_rtmp_frames.load()) {
                // Try to get RTMP frame from queue
                std::lock_guard<std::mutex> lock(rtmp_queue_mutex);
                if (!rtmp_frame_queue.empty()) {
                    auto rtmp_frame = rtmp_frame_queue.front();
                    rtmp_frame_queue.pop();
                    
                    if (rtmp_frame.size() == frame_size) {
                        memcpy(frame_buffer, rtmp_frame.data(), frame_size);
                        frame_ready = true;
                    } else {
                        LOGE("RTMP frame size mismatch: %zu expected %zu", rtmp_frame.size(), frame_size);
                    }
                }
            } else {
                // Generate frame using frame generator
                if (frame_generator) {
                    frame_generator->generateFrame(frame_buffer);
                    frame_ready = true;
                }
            }
            
            if (frame_ready) {
                // Write frame to V4L2 device
                ssize_t bytes_written = write(v4l2_fd, frame_buffer, frame_size);
                if (bytes_written < 0) {
                    if (errno != EAGAIN && errno != EWOULDBLOCK) {
                        LOGE("Error writing frame: %s", strerror(errno));
                        break;
                    }
                } else if (bytes_written != static_cast<ssize_t>(frame_size)) {
                    LOGE("Partial frame write: %zd/%zu bytes", bytes_written, frame_size);
                }
            }
            
            // 30 FPS
            std::this_thread::sleep_for(std::chrono::milliseconds(33));
        }
        
        delete[] frame_buffer;
        LOGI("Streaming thread stopped");
    });
    
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_virtualcamera_manager_service_VirtualCameraService_stopFrameStreaming(
        JNIEnv *env, jobject /* this */) {
    
    LOGI("Stopping frame streaming");
    streaming.store(false);
    use_rtmp_frames.store(false);
    
    if (streaming_thread.joinable()) {
        streaming_thread.join();
    }
    
    if (frame_generator) {
        delete frame_generator;
        frame_generator = nullptr;
    }
    
    // Clear RTMP frame queue
    std::lock_guard<std::mutex> lock(rtmp_queue_mutex);
    while (!rtmp_frame_queue.empty()) {
        rtmp_frame_queue.pop();
    }
    
    LOGI("Frame streaming stopped");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_virtualcamera_manager_service_VirtualCameraService_pushRTMPFrame(
        JNIEnv *env, jobject /* this */, jbyteArray frame_data) {
    
    if (!use_rtmp_frames.load()) {
        return JNI_FALSE;
    }
    
    jsize frame_size = env->GetArrayLength(frame_data);
    if (frame_size != 640 * 480 * 3) {
        LOGE("Invalid RTMP frame size: %d", frame_size);
        return JNI_FALSE;
    }
    
    jbyte* frame_bytes = env->GetByteArrayElements(frame_data, nullptr);
    if (frame_bytes == nullptr) {
        LOGE("Failed to get RTMP frame data");
        return JNI_FALSE;
    }
    
    // Add frame to queue
    std::lock_guard<std::mutex> lock(rtmp_queue_mutex);
    
    // Limit queue size to prevent memory issues
    if (rtmp_frame_queue.size() > 10) {
        rtmp_frame_queue.pop(); // Remove oldest frame
    }
    
    std::vector<uint8_t> frame_vector(frame_bytes, frame_bytes + frame_size);
    rtmp_frame_queue.push(std::move(frame_vector));
    
    env->ReleaseByteArrayElements(frame_data, frame_bytes, JNI_ABORT);
    
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_virtualcamera_manager_service_VirtualCameraService_closeV4L2Device(
        JNIEnv *env, jobject /* this */) {
    
    LOGI("Closing V4L2 device");
    
    if (v4l2_fd >= 0) {
        close(v4l2_fd);
        v4l2_fd = -1;
    }
    
    LOGI("V4L2 device closed");
    return JNI_TRUE;
}