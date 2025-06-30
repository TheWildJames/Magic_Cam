#ifndef FRAME_GENERATOR_H
#define FRAME_GENERATOR_H

#include <cstdint>
#include <chrono>

enum class FramePattern {
    TEST_PATTERN = 0,
    COLOR_BARS = 1,
    IMAGE_FILE = 2,
    VIDEO_FILE = 3,
    RTMP_STREAM = 4
};

class FrameGenerator {
public:
    FrameGenerator(int width, int height, FramePattern pattern);
    ~FrameGenerator();
    
    void generateFrame(uint8_t* buffer);
    void setPattern(FramePattern pattern) { current_pattern = pattern; }
    
private:
    int frame_width;
    int frame_height;
    size_t frame_size;
    FramePattern current_pattern;
    uint64_t frame_counter;
    std::chrono::high_resolution_clock::time_point start_time;
    
    void generateTestPattern(uint8_t* buffer);
    void generateColorBars(uint8_t* buffer);
    void generateImageFile(uint8_t* buffer);
    void generateVideoFile(uint8_t* buffer);
    void generateRTMPPlaceholder(uint8_t* buffer);
    
    // Helper functions
    void setPixel(uint8_t* buffer, int x, int y, uint8_t r, uint8_t g, uint8_t b);
    void drawText(uint8_t* buffer, const char* text, int x, int y);
};

#endif // FRAME_GENERATOR_H