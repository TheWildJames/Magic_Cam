# Virtual Camera Manager for Android with RTMP Support

A comprehensive Android application that enables system-wide virtual camera functionality using v4l2loopback and Camera HAL integration, now with **RTMP stream input support**. This app requires root access to configure kernel modules and camera interfaces.

## 🎯 Project Overview

This application creates and manages virtual cameras on Android devices by:
- Setting up v4l2loopback devices (`/dev/video0`)
- Integrating with Android's Camera HAL for system-wide camera replacement
- **Processing RTMP streams as video input sources**
- Providing real-time frame streaming with customizable video sources
- Offering a professional UI for camera management and debugging

## 🔧 Core Features

### System Integration
- **Root Permission Management**: Automated root access verification and privilege escalation
- **V4L2 Device Configuration**: Dynamic setup and management of `/dev/video0` virtual camera device
- **Camera HAL Integration**: System-level camera interface replacement for universal app compatibility

### Video Sources
- **Test Patterns**: Built-in test patterns and color bars for testing
- **Static Images**: Support for image file input
- **Video Files**: Local video file playback
- **🆕 RTMP Streams**: Real-time RTMP stream processing and forwarding

### RTMP Features
- **RTMP Client**: Custom RTMP client implementation with handshake support
- **Frame Processing**: Real-time H.264 frame extraction and conversion
- **Stream Management**: Automatic reconnection and error handling
- **Queue Management**: Efficient frame buffering and synchronization

### Technical Features
- **Real-time Streaming**: Native C++ implementation for efficient frame generation and streaming
- **Service Architecture**: Background services ensure continuous camera and RTMP availability
- **Comprehensive Logging**: Detailed debugging information and system status monitoring
- **Material Design UI**: Modern, professional interface with status indicators and controls

## 🚀 Getting Started

### Prerequisites

- **Rooted Android Device**: Root access is mandatory for kernel module loading and HAL configuration
- **Android 7.0+ (API 24+)**: Minimum supported Android version
- **V4L2 Kernel Support**: Device kernel must support Video4Linux2 interfaces
- **Network Access**: Internet connection required for RTMP stream processing
- **Available Storage**: ~50MB for app installation and temporary files

### Installation

1. **Download APK**: Get the latest release from [GitHub Releases](../../releases)
2. **Enable Unknown Sources**: Allow installation from unknown sources in Android settings
3. **Install APK**: `adb install virtual-camera-manager.apk` or install manually
4. **Grant Root Access**: Approve root permission request when prompted
5. **Grant Network Permissions**: Allow internet access for RTMP functionality
6. **Setup Verification**: The app will automatically verify system compatibility

### First Launch Setup

1. **Root Verification**: App automatically checks and requests root access
2. **Kernel Module Check**: Verifies v4l2loopback module availability
3. **Device Permissions**: Configures `/dev/video0` access permissions
4. **HAL Configuration**: Sets up Camera HAL integration (if supported)
5. **Network Test**: Validates internet connectivity for RTMP streams

## 📋 Usage Guide

### RTMP Stream Setup

1. **Configure RTMP URL**: Enter your RTMP stream URL in the format:
   ```
   rtmp://server.com/live/streamkey
   rtmp://192.168.1.100:1935/live/test
   ```

2. **Start RTMP Service**: Tap "Start RTMP Stream" to begin receiving frames
3. **Select RTMP Source**: Choose "RTMP Stream" from the video source dropdown
4. **Start Virtual Camera**: Tap "Start Virtual Camera" to begin streaming to system

### Traditional Video Sources

1. **Select Video Source**: Choose from Test Pattern, Color Bars, Image File, or Video File
2. **Start Service**: Tap "Start Virtual Camera" to begin streaming
3. **Verify Operation**: Check status indicators for successful activation
4. **Test Integration**: Open any camera app to verify virtual camera appears in device list

### Stopping Services

1. **Stop Virtual Camera**: Tap "Stop Virtual Camera" to halt camera streaming
2. **Stop RTMP Stream**: Tap "Stop RTMP Stream" to disconnect from RTMP server
3. **Cleanup**: App automatically cleans up resources and restores system state

### Monitoring and Debugging

- **Status Panel**: Real-time display of root access, V4L2 device, and HAL status
- **RTMP Status**: Connection status and stream information
- **Log Viewer**: Detailed logging of all operations and error conditions
- **System Information**: Device capabilities and configuration details

## 🛠️ Development Setup

### Building from Source

```bash
# Clone repository
git clone https://github.com/your-username/virtual-camera-manager.git
cd virtual-camera-manager

# Build with Gradle
./gradlew assembleDebug

# Install on device
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Dependencies

- **Android SDK**: API Level 34 (Android 14)
- **Android NDK**: Version 25.2.9519653 for native C++ compilation
- **Kotlin**: Version 1.9.10
- **Material Components**: Latest Material Design 3 library
- **RootEncoder**: RTMP streaming library for Android
- **OkHttp**: HTTP client for network operations
- **Gson**: JSON parsing for RTMP metadata

## 🌐 RTMP Implementation Details

### Supported RTMP Features

- **RTMP Handshake**: Full C0/C1/C2/S0/S1/S2 handshake implementation
- **Packet Processing**: Real-time RTMP packet parsing and frame extraction
- **H.264 Support**: Hardware-accelerated H.264 decoding (planned)
- **Reconnection**: Automatic reconnection on connection loss
- **Error Handling**: Comprehensive error detection and recovery

### RTMP URL Formats

```bash
# Standard RTMP
rtmp://server.com/app/streamkey

# RTMP with port
rtmp://server.com:1935/live/streamkey

# Local RTMP server
rtmp://192.168.1.100:1935/live/test

# RTMPS (secure) - planned
rtmps://secure-server.com/live/streamkey
```

### Frame Processing Pipeline

1. **RTMP Connection**: Establish connection to RTMP server
2. **Packet Reception**: Receive and parse RTMP packets
3. **Frame Extraction**: Extract H.264 NAL units from video packets
4. **Format Conversion**: Convert H.264 to RGB24 format
5. **Frame Queuing**: Buffer frames for smooth playback
6. **V4L2 Output**: Write frames to virtual camera device

## 🔒 Security Considerations

### Root Access Requirements

This app requires root access for:
- Loading kernel modules (`insmod`, `modprobe`)
- Creating device files (`/dev/video0`)
- Modifying system permissions (`chmod`, `chown`)
- Configuring Camera HAL interfaces

### Network Security

- **RTMP Streams**: Supports both authenticated and unauthenticated RTMP streams
- **SSL/TLS**: RTMPS support planned for secure streaming
- **Firewall**: Ensure RTMP ports (typically 1935) are accessible
- **Privacy**: RTMP streams are processed locally and not stored

### Security Measures

- **Permission Validation**: Thorough verification of root access before operations
- **Sandboxed Operations**: Native code runs with minimal required privileges
- **Network Isolation**: RTMP processing isolated from system components
- **Cleanup Procedures**: Automatic restoration of system state on app termination
- **SELinux Awareness**: Proper handling of SELinux policies and contexts

## 🐛 Troubleshooting

### Common Issues

**Root Access Denied**
- Ensure device is properly rooted with SuperSU, Magisk, or equivalent
- Grant root permission when prompted by the app
- Check root access with terminal emulator: `su -c "id"`

**V4L2 Device Not Available**
- Verify kernel supports Video4Linux2: `zcat /proc/config.gz | grep V4L2`
- Check for v4l2loopback module: `find /system -name "*v4l2loopback*"`
- Try manual module loading: `su -c "insmod /path/to/v4l2loopback.ko"`

**RTMP Connection Failed**
- Verify RTMP URL format and server accessibility
- Check network connectivity and firewall settings
- Test RTMP stream with external tools (VLC, FFmpeg)
- Ensure RTMP server supports the required codec (H.264)

**Camera Not Recognized by Apps**
- Restart the target camera application
- Check device permissions: `ls -la /dev/video*`
- Verify Camera HAL registration in system logs

**Performance Issues**
- Reduce frame rate in native code (modify sleep duration)
- Lower resolution in V4L2 format configuration
- Check CPU usage and thermal throttling
- Monitor network bandwidth for RTMP streams

### Debug Commands

```bash
# Check module status
lsmod | grep v4l2loopback

# List video devices
ls -la /dev/video*

# Test device access
v4l2-ctl --device=/dev/video0 --info

# Monitor system logs
logcat -s VirtualCamera:* RTMPClient:* RTMPFrameProcessor:*

# Test RTMP stream
ffplay rtmp://your-server.com/live/stream

# Network connectivity
ping your-rtmp-server.com
telnet your-rtmp-server.com 1935
```

## 🔄 GitHub Actions CI/CD

### Automated Workflows

- **Build Verification**: Automatic compilation and testing on every push
- **Security Scanning**: Static analysis and vulnerability detection
- **APK Generation**: Signed release builds for main branch commits
- **Artifact Storage**: Automated upload of build outputs with retention policies
- **RTMP Testing**: Automated testing of RTMP functionality (planned)

### Release Process

1. **Version Tagging**: Create Git tag with semantic versioning
2. **Automated Build**: GitHub Actions triggers release build with RTMP support
3. **Security Validation**: Comprehensive security scan execution
4. **Release Publication**: Automatic GitHub Release creation with APK attachment

## 📁 Project Structure

```
├── app/
│   ├── src/main/
│   │   ├── java/com/virtualcamera/manager/
│   │   │   ├── MainActivity.kt              # Main UI controller
│   │   │   ├── service/
│   │   │   │   ├── VirtualCameraService.kt  # Background camera service
│   │   │   │   └── RTMPStreamService.kt     # RTMP stream processing service
│   │   │   ├── rtmp/
│   │   │   │   ├── RTMPClient.kt            # RTMP client implementation
│   │   │   │   └── RTMPFrameProcessor.kt    # Frame processing engine
│   │   │   └── utils/
│   │   │       ├── RootUtils.kt             # Root access utilities
│   │   │       └── V4L2Utils.kt             # V4L2 device management
│   │   ├── cpp/
│   │   │   ├── v4l2_native.cpp              # Native V4L2 interface
│   │   │   ├── frame_generator.cpp          # Frame generation engine
│   │   │   └── frame_generator.h            # Frame generator header
│   │   └── res/                             # Android resources
├── scripts/
│   ├── setup-v4l2.sh                       # System setup script
│   └── cleanup-v4l2.sh                     # Cleanup script
├── .github/workflows/
│   └── android-ci.yml                      # CI/CD pipeline with RTMP support
└── README.md                               # This file
```

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details on:
- Code style and formatting requirements
- Pull request submission process
- Issue reporting templates
- Development environment setup
- RTMP testing procedures

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## ⚠️ Disclaimer

This application requires root access and modifies system-level components. Use at your own risk. The developers are not responsible for any damage to your device or data loss. Always test on non-production devices first.

RTMP streaming functionality processes network data and may consume significant bandwidth and battery. Monitor your data usage and device performance accordingly.

## 🔗 Additional Resources

- [Android Camera Architecture](https://source.android.com/devices/camera)
- [V4L2 Loopback Documentation](https://github.com/umlaeute/v4l2loopback)
- [RTMP Specification](https://rtmp.veriskope.com/docs/spec/)
- [Android NDK Development](https://developer.android.com/ndk)
- [Material Design Guidelines](https://material.io/design)
- [FFmpeg RTMP Documentation](https://ffmpeg.org/ffmpeg-protocols.html#rtmp)

## 🎥 RTMP Testing Servers

For testing purposes, you can use these public RTMP test servers:
- `rtmp://live.twitch.tv/live/YOUR_STREAM_KEY` (Twitch)
- `rtmp://a.rtmp.youtube.com/live2/YOUR_STREAM_KEY` (YouTube)
- `rtmp://ingest.srs.ossrs.net/live/livestream` (SRS Test Server)

**Note**: Always respect the terms of service of streaming platforms and test servers.