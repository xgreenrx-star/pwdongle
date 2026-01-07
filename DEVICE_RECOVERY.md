# PWDongle Device Recovery Guide

## Symptom
Device is connected via USB but not responding to serial commands. Error: "Failed to connect to ESP32-S3: No serial data received"

## Possible Causes
1. **Firmware Crash Loop** - The device boots but crashes immediately during initialization
2. **Power Issue** - Brown-out reset due to unstable power supply
3. **USB Connection Issue** - Bad cable or port
4. **Bootloader Corruption** - Less likely but possible

## Recovery Steps

### Step 1: Hardware Reset
1. **Disconnect** PWDongle from USB
2. **Wait** 10 seconds (allows capacitors to discharge)
3. **Reconnect** to USB
4. **Wait** 5 seconds for device to enumerate
5. **Try uploading firmware** again

### Step 2: Force Bootloader Mode
If Step 1 doesn't work, force the device into download mode:

1. **Disconnect** PWDongle from USB
2. **Hold** the BOOT button (GPIO0) on the device
3. **Connect** to USB (while still holding BOOT)
4. **Wait** 2 seconds
5. **Release** BOOT button
6. **Immediately** attempt upload with:
   ```bash
   cd /home/Commodore/Documents/PlatformIO/Projects/PWDongle
   /home/Commodore/.platformio/penv/bin/platformio run --target upload
   ```

### Step 3: ESP32 JTAG Recovery (Advanced)
If the above steps don't work, you may need JTAG hardware:
- Requires: ESP-PROG or J-Link debugger
- Cost: $20-100
- Allows: Direct flash without ROM bootloader

## Diagnosis: What Went Wrong?

The device became unresponsive after firmware flashes on 2026-01-07. Likely cause:

**Hypothesis**: USB initialization hanging or crash in early startup
- Code path: `main.cpp::setup()` → TFT init → USB mode selection → BLE init
- The device USB enumeration worked (device shows in lsusb) but it doesn't respond to serial at all
- This suggests the device is either:
  - Stuck in an infinite loop
  - Crashing in the bootloader or early Arduino setup
  - Experiencing watchdog reset loop

## How to Test Different Firmware Components

Once you recover the device, test with a minimal firmware:

```cpp
// Minimal diagnostic firmware (minimal_test.ino):
void setup() {
  Serial.begin(115200);
  delay(2000);
  Serial.println("Device booted successfully!");
}

void loop() {
  Serial.println("Alive");
  delay(1000);
}
```

This can help isolate whether the issue is:
- USB CDC initialization (Serial.begin)
- TFT display (lots of SPI traffic)
- BLE stack
- SD card initialization
- NVS Preferences

## For Future Development

To avoid this issue:
1. **Watchdog Timer** - Ensure watchdog is fed in main loop (prevents reboot loop)
2. **Brownout Protection** - Check power supply stability
3. **Gradual Initialization** - Add Serial.println() at each init step to diagnose early crashes
4. **Timeout Protection** - Set timeouts on SD card init, BLE init, etc.

## Contact/Support

If you cannot recover the device after Step 2, you likely need:
- JTAG hardware for recovery
- Or a replacement ESP32-S3 module

The device itself is likely not permanently damaged - it's just in an unbootable state that requires bootloader-level access to fix.
