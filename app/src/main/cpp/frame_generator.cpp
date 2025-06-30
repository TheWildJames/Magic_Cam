#include "frame_generator.h"
#include <android/log.h>
#include <cstring>
#include <cmath>

#define LOG_TAG "FrameGenerator"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

FrameGenerator::FrameGenerator(int width, int height, FramePattern pattern)
    : frame_width(width), frame_height(height), current_pattern(pattern), frame_counter(0) {
    
    frame_size = width * height * 3; // RGB24
    start_time = std::chrono::high_resolution_clock::now();
    
    LOGI("FrameGenerator created: %dx%d, pattern=%d", width, height, static_cast<int>(pattern));
}

FrameGenerator::~FrameGenerator() {
    LOGI("FrameGenerator destroyed");
}

void FrameGenerator::generateFrame(uint8_t* buffer) {
    switch (current_pattern) {
        case FramePattern::TEST_PATTERN:
            generateTestPattern(buffer);
            break;
        case FramePattern::COLOR_BARS:
            generateColorBars(buffer);
            break;
        case FramePattern::IMAGE_FILE:
            generateImageFile(buffer);
            break;
        case FramePattern::VIDEO_FILE:
            generateVideoFile(buffer);
            break;
        case FramePattern::RTMP_STREAM:
            generateRTMPPlaceholder(buffer);
            break;
        default:
            generateTestPattern(buffer);
            break;
    }
    
    frame_counter++;
}

void FrameGenerator::generateTestPattern(uint8_t* buffer) {
    // Generate a moving gradient with frame counter
    for (int y = 0; y < frame_height; y++) {
        for (int x = 0; x < frame_width; x++) {
            // Moving diagonal pattern
            int value = ((x + y + static_cast<int>(frame_counter)) % 255);
            
            uint8_t r = static_cast<uint8_t>(value);
            uint8_t g = static_cast<uint8_t>((value + 85) % 255);
            uint8_t b = static_cast<uint8_t>((value + 170) % 255);
            
            setPixel(buffer, x, y, r, g, b);
        }
    }
    
    // Add frame counter text (simplified)
    if (frame_counter % 30 == 0) { // Update every second at 30fps
        LOGI("Generated test pattern frame %lu", frame_counter);
    }
}

void FrameGenerator::generateColorBars(uint8_t* buffer) {
    // Standard SMPTE color bars
    const int bar_width = frame_width / 8;
    
    // Colors: White, Yellow, Cyan, Green, Magenta, Red, Blue, Black
    uint8_t colors[8][3] = {
        {255, 255, 255}, // White
        {255, 255, 0},   // Yellow
        {0, 255, 255},   // Cyan
        {0, 255, 0},     // Green
        {255, 0, 255},   // Magenta
        {255, 0, 0},     // Red
        {0, 0, 255},     // Blue
        {0, 0, 0}        // Black
    };
    
    for (int y = 0; y < frame_height; y++) {
        for (int x = 0; x < frame_width; x++) {
            int bar_index = x / bar_width;
            if (bar_index >= 8) bar_index = 7;
            
            setPixel(buffer, x, y, colors[bar_index][0], colors[bar_index][1], colors[bar_index][2]);
        }
    }
}

void FrameGenerator::generateImageFile(uint8_t* buffer) {
    // Placeholder: Generate a checkerboard pattern
    const int checker_size = 32;
    
    for (int y = 0; y < frame_height; y++) {
        for (int x = 0; x < frame_width; x++) {
            bool checker = ((x / checker_size) + (y / checker_size)) % 2 == 0;
            uint8_t value = checker ? 255 : 64;
            
            setPixel(buffer, x, y, value, value, value);
        }
    }
}

void FrameGenerator::generateVideoFile(uint8_t* buffer) {
    // Placeholder: Generate animated circles
    auto current_time = std::chrono::high_resolution_clock::now();
    auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(current_time - start_time);
    double time_sec = elapsed.count() / 1000.0;
    
    // Clear buffer to black
    memset(buffer, 0, frame_size);
    
    // Draw animated circle
    int center_x = frame_width / 2 + static_cast<int>(50 * cos(time_sec));
    int center_y = frame_height / 2 + static_cast<int>(50 * sin(time_sec));
    int radius = 30;
    
    for (int y = 0; y < frame_height; y++) {
        for (int x = 0; x < frame_width; x++) {
            int dx = x - center_x;
            int dy = y - center_y;
            double distance = sqrt(dx * dx + dy * dy);
            
            if (distance <= radius) {
                uint8_t intensity = static_cast<uint8_t>(255 * (1.0 - distance / radius));
                setPixel(buffer, x, y, intensity, intensity / 2, intensity / 4);
            }
        }
    }
}

void FrameGenerator::generateRTMPPlaceholder(uint8_t* buffer) {
    // Generate a placeholder pattern when RTMP frames are not available
    auto current_time = std::chrono::high_resolution_clock::now();
    auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(current_time - start_time);
    double time_sec = elapsed.count() / 1000.0;
    
    // Pulsing red background to indicate RTMP mode
    uint8_t intensity = static_cast<uint8_t>(128 + 127 * sin(time_sec * 2));
    
    for (int y = 0; y < frame_height; y++) {
        for (int x = 0; x < frame_width; x++) {
            setPixel(buffer, x, y, intensity, 0, 0);
        }
    }
    
    // Add "RTMP" text pattern in center
    int center_x = frame_width / 2;
    int center_y = frame_height / 2;
    
    // Simple block letters for "RTMP"
    for (int y = center_y - 20; y < center_y + 20; y++) {
        for (int x = center_x - 60; x < center_x + 60; x++) {
            if (x >= 0 && x < frame_width && y >= 0 && y < frame_height) {
                // Simple pattern to represent "RTMP"
                bool is_letter = ((x - center_x + 60) / 30) % 2 == 0;
                if (is_letter && (y - center_y + 20) % 10 < 5) {
                    setPixel(buffer, x, y, 255, 255, 255);
                }
            }
        }
    }
}

void FrameGenerator::setPixel(uint8_t* buffer, int x, int y, uint8_t r, uint8_t g, uint8_t b) {
    if (x >= 0 && x < frame_width && y >= 0 && y < frame_height) {
        int offset = (y * frame_width + x) * 3;
        buffer[offset] = r;
        buffer[offset + 1] = g;
        buffer[offset + 2] = b;
    }
}

void FrameGenerator::drawText(uint8_t* buffer, const char* text, int x, int y) {
    // Simplified text rendering - just a placeholder
    // In a real implementation, you'd use a bitmap font
    (void)buffer; (void)text; (void)x; (void)y;
}