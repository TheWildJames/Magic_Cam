#!/system/bin/sh

# Virtual Camera Cleanup Script
# Requires root access

LOG_TAG="VirtualCamera"

log_info() {
    echo "[INFO] $1"
    log -t "$LOG_TAG" "$1"
}

log_error() {
    echo "[ERROR] $1"
    log -t "$LOG_TAG" "$1"
}

check_root() {
    if [ "$(id -u)" != "0" ]; then
        log_error "This script must be run as root"
        exit 1
    fi
    log_info "Root access confirmed"
}

stop_virtual_camera_service() {
    log_info "Stopping virtual camera service..."
    
    # Kill any processes using the virtual camera
    PIDS=$(lsof /dev/video0 2>/dev/null | awk 'NR>1 {print $2}' | sort -u)
    
    if [ -n "$PIDS" ]; then
        log_info "Stopping processes using /dev/video0: $PIDS"
        echo "$PIDS" | xargs kill -TERM 2>/dev/null || true
        sleep 2
        echo "$PIDS" | xargs kill -KILL 2>/dev/null || true
    fi
}

unload_v4l2_module() {
    log_info "Unloading v4l2loopback module..."
    
    # Check if module is loaded
    if lsmod | grep -q v4l2loopback; then
        # Try rmmod first
        if command -v rmmod >/dev/null 2>&1; then
            rmmod v4l2loopback 2>/dev/null
            if [ $? -eq 0 ]; then
                log_info "v4l2loopback module unloaded successfully"
                return 0
            fi
        fi
        
        # Try modprobe -r
        if command -v modprobe >/dev/null 2>&1; then
            modprobe -r v4l2loopback 2>/dev/null
            if [ $? -eq 0 ]; then
                log_info "v4l2loopback module unloaded via modprobe"
                return 0
            fi
        fi
        
        log_error "Failed to unload v4l2loopback module"
        return 1
    else
        log_info "v4l2loopback module is not loaded"
        return 0
    fi
}

cleanup_device_files() {
    log_info "Cleaning up device files..."
    
    # Remove device files if they exist
    if [ -e "/dev/video0" ]; then
        rm -f /dev/video0
        log_info "Removed /dev/video0"
    fi
    
    # Clean up any temporary files
    rm -rf /tmp/virtual_camera_* 2>/dev/null || true
}

restore_selinux() {
    log_info "Restoring SELinux settings..."
    
    if command -v getenforce >/dev/null 2>&1; then
        SELINUX_STATUS=$(getenforce)
        if [ "$SELINUX_STATUS" = "Permissive" ]; then
            log_info "Restoring SELinux to enforcing mode"
            setenforce 1 2>/dev/null || log_error "Failed to restore SELinux enforcing"
        fi
    fi
}

verify_cleanup() {
    log_info "Verifying cleanup..."
    
    # Check if module is unloaded
    if ! lsmod | grep -q v4l2loopback; then
        log_info "v4l2loopback module is not loaded"
    else
        log_error "v4l2loopback module is still loaded"
    fi
    
    # Check if device file is removed
    if [ ! -e "/dev/video0" ]; then
        log_info "/dev/video0 device file removed"
    else
        log_error "/dev/video0 device file still exists"
    fi
    
    log_info "Cleanup verification completed"
}

main() {
    log_info "Starting virtual camera cleanup..."
    
    check_root
    stop_virtual_camera_service
    unload_v4l2_module
    cleanup_device_files
    restore_selinux
    verify_cleanup
    
    log_info "Virtual camera cleanup completed"
}

# Run main function
main "$@"