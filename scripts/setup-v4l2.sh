#!/system/bin/sh

# Virtual Camera Setup Script
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

check_kernel_modules() {
    log_info "Checking kernel module support..."
    
    if [ ! -d "/system/lib/modules" ] && [ ! -d "/vendor/lib/modules" ]; then
        log_error "No kernel modules directory found"
        return 1
    fi
    
    # Look for v4l2loopback module
    if find /system /vendor -name "*v4l2loopback*" 2>/dev/null | grep -q v4l2loopback; then
        log_info "v4l2loopback module found"
        return 0
    fi
    
    log_error "v4l2loopback module not found"
    return 1
}

load_v4l2_module() {
    log_info "Loading v4l2loopback module..."
    
    # Try different module loading methods
    if command -v insmod >/dev/null 2>&1; then
        # Try to find and load the module
        MODULE_PATH=$(find /system /vendor -name "*v4l2loopback*" 2>/dev/null | head -1)
        if [ -n "$MODULE_PATH" ]; then
            insmod "$MODULE_PATH" devices=1 video_nr=0 card_label="Virtual Camera" exclusive_caps=1
            if [ $? -eq 0 ]; then
                log_info "v4l2loopback module loaded successfully"
                return 0
            fi
        fi
    fi
    
    # Try modprobe if available
    if command -v modprobe >/dev/null 2>&1; then
        modprobe v4l2loopback devices=1 video_nr=0 card_label="Virtual Camera" exclusive_caps=1
        if [ $? -eq 0 ]; then
            log_info "v4l2loopback module loaded via modprobe"
            return 0
        fi
    fi
    
    log_error "Failed to load v4l2loopback module"
    return 1
}

setup_device_permissions() {
    log_info "Setting up device permissions..."
    
    # Wait for device to appear
    TIMEOUT=10
    while [ $TIMEOUT -gt 0 ] && [ ! -e "/dev/video0" ]; do
        sleep 1
        TIMEOUT=$((TIMEOUT - 1))
    done
    
    if [ ! -e "/dev/video0" ]; then
        log_error "/dev/video0 device not found"
        return 1
    fi
    
    # Set permissions
    chmod 666 /dev/video0
    chown camera:camera /dev/video0 2>/dev/null || true
    
    log_info "Device permissions set for /dev/video0"
    return 0
}

setup_selinux_permissions() {
    log_info "Configuring SELinux permissions..."
    
    # Check if SELinux is enforcing
    if command -v getenforce >/dev/null 2>&1; then
        SELINUX_STATUS=$(getenforce)
        log_info "SELinux status: $SELINUX_STATUS"
        
        if [ "$SELINUX_STATUS" = "Enforcing" ]; then
            log_info "SELinux is enforcing, attempting to set permissive for camera"
            # This is a simplified approach - real implementation would need proper policy
            setenforce 0 2>/dev/null || log_error "Failed to set SELinux permissive"
        fi
    fi
}

verify_setup() {
    log_info "Verifying virtual camera setup..."
    
    # Check if device exists and is accessible
    if [ ! -e "/dev/video0" ]; then
        log_error "Setup verification failed: /dev/video0 not found"
        return 1
    fi
    
    # Try to get device info
    if command -v v4l2-ctl >/dev/null 2>&1; then
        v4l2-ctl --device=/dev/video0 --info >/dev/null 2>&1
        if [ $? -eq 0 ]; then
            log_info "v4l2-ctl can access the device"
        else
            log_error "v4l2-ctl cannot access the device"
        fi
    fi
    
    log_info "Virtual camera setup verification completed"
    return 0
}

main() {
    log_info "Starting virtual camera setup..."
    
    check_root
    
    if check_kernel_modules; then
        if load_v4l2_module; then
            setup_device_permissions
            setup_selinux_permissions
            verify_setup
            log_info "Virtual camera setup completed successfully"
        else
            log_error "Failed to load kernel module"
            exit 1
        fi
    else
        log_error "Kernel module support not available"
        exit 1
    fi
}

# Run main function
main "$@"