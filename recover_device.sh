#!/bin/bash
# PWDongle Device Recovery Script

echo "=========================================="
echo "PWDongle Device Recovery Tool"
echo "=========================================="
echo ""
echo "This script will help recover your PWDongle device."
echo ""
echo "BEFORE PROCEEDING:"
echo "1. You must be able to physically access the BOOT button"
echo "2. The device must be connected to USB"
echo ""
read -p "Continue? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    exit 1
fi

echo ""
echo "Step 1: DISCONNECT the PWDongle from USB"
read -p "Press ENTER when disconnected..." 
echo "Waiting 10 seconds..."
sleep 10

echo ""
echo "Step 2: HOLD the BOOT button on the device"
echo "  - Keep holding it!"
read -p "Press ENTER when you're ready..."

echo ""
echo "Step 3: CONNECT the PWDongle to USB (keep BOOT pressed)"
echo "Waiting 2 seconds..."
sleep 2

echo ""
echo "Step 4: RELEASE the BOOT button now"
read -p "Press ENTER after releasing BOOT..."

echo ""
echo "Checking for device in bootloader mode..."
if lsusb | grep -q "303a:1001"; then
    echo "✓ Device detected!"
    echo ""
    echo "Step 5: Now flashing firmware..."
    cd /home/Commodore/Documents/PlatformIO/Projects/PWDongle
    /home/Commodore/.platformio/penv/bin/platformio run --target upload
    
    if [ $? -eq 0 ]; then
        echo ""
        echo "✓✓✓ RECOVERY SUCCESSFUL! ✓✓✓"
        echo ""
        echo "Device should reboot and start normally."
        echo "You can now reconnect and use the app."
    else
        echo ""
        echo "✗ Upload failed. The bootloader might not be responsive."
        echo "You may need JTAG hardware for recovery."
    fi
else
    echo "✗ Device not detected in bootloader mode"
    echo "  Try the steps again, making sure to:"
    echo "  1. Hold BOOT BEFORE connecting USB"
    echo "  2. Keep holding BOOT until upload starts"
    echo "  3. Release BOOT only after you see 'Connecting...' in the upload output"
fi
