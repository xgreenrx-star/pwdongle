# PWDongle Macro Automation System

## Overview

Complete implementation of automated macro recording system for PWDongle, allowing users to record keyboard and mouse actions from a smartphone and replay them on a PC.

## Architecture

```
┌─────────────────┐         BLE          ┌──────────────────┐
│   Smartphone    │ ◄─────────────────► │    PWDongle      │
│   (Recorder)    │   Nordic UART        │   (ESP32-S3)     │
│                 │                      │                  │
│  USB OTG Input  │                      │   USB HID Output │
│  ┌───────────┐  │                      │   ┌────────────┐ │
│  │ Keyboard  │──┼──┐                ┌──┼──►│     PC     │ │
│  │  Mouse    │──┼──┘                │  │   └────────────┘ │
│  └───────────┘  │                   │  │                  │
└─────────────────┘                   │  │   SD Card Storage│
                                      │  │   (Macro Files)  │
                                      │  └──────────────────┘
                                      │
                                      └─► Relay keystrokes/mouse
                                          Record with timing
```

## Implementation Complete

### 1. ✅ Enhanced Mouse Commands (PWDongle Firmware)

**File:** `src/usb.cpp`

**New Commands:**
- `MOUSE:RESET` - Move cursor to (0,0) top-left
- `MOUSE:MOVE:x,y` - Absolute positioning
- `MOUSE:MOVE_REL:dx,dy` - Relative movement
- `MOUSE:DOWN:button` / `MOUSE:UP:button` - Button press/release
- `MOUSE:CLICK:button` - Full click (down+up)
- `MOUSE:SCROLL:amount` - Scroll wheel

**Features:**
- Absolute position tracking with `mouseX` and `mouseY` globals
- Chunked movement for distances >127 pixels (USB HID limitation)
- Supports both comma and space delimiters for compatibility
- Updated in both macro processor locations (lines 789 and 1236)

**Usage Example:**
```
{{MOUSE:RESET}}
{{DELAY:100}}
{{MOUSE:MOVE:500,300}}
{{DELAY:50}}
{{MOUSE:CLICK:left}}
```

### 2. ✅ Android App Structure (Kotlin)

**Location:** `android-recorder/`

**Files Created:**
- `MainActivity.kt` - Main UI and permission handling (259 lines)
- `BLEManager.kt` - Nordic UART Service communication (199 lines)
- `InputCaptureService.kt` - USB OTG input capture (173 lines)
- `MacroRecorder.kt` - Recording state machine (146 lines)
- `MouseTracker.kt` - Position tracking (27 lines)
- `activity_main.xml` - UI layout
- `AndroidManifest.xml` - Permissions and configuration
- `README.md` - Setup and build instructions

**Key Features:**
- BLE device scanning and connection to PWDongle
- USB OTG keyboard and mouse event capture
- Real-time command relay to PWDongle
- Automatic timing capture (>50ms threshold)
- Position validation (ensures mouse at top-left before recording)
- Macro file generation with PWDongle format

**Permissions Required:**
- Bluetooth (BLUETOOTH, BLUETOOTH_SCAN, BLUETOOTH_CONNECT)
- Location (for BLE scanning on Android 10+)
- USB OTG access

**Dependencies:**
- Nordic BLE library: `no.nordicsemi.android:ble:2.6.1`
- Kotlin coroutines for async operations

### 3. ✅ Python/Termux Proof-of-Concept

**Location:** `python-recorder/`

**Files Created:**
- `pwdongle_recorder.py` - Full recording script (345 lines)
- `README.md` - Installation and usage guide

**Features:**
- Cross-platform (Linux, macOS, Windows, Android/Termux)
- BLE scanning and connection using `bleak`
- Keyboard/mouse capture using `pynput`
- Automatic delay calculation
- PWDongle macro format generation
- Interactive CLI interface

**Usage:**
```bash
# Install dependencies
pip install bleak pynput

# Run recorder
python3 pwdongle_recorder.py

# Follow prompts to connect, record, and save
```

## Recording Workflow

### Smartphone App (Android)

1. **Connect Hardware:**
   - Plug USB OTG adapter into Android device
   - Connect USB keyboard and mouse to OTG adapter

2. **Connect to PWDongle:**
   - Launch app
   - Tap "Scan for PWDongle"
   - Select device from dropdown
   - Tap "Connect"

3. **Prepare Recording:**
   - Position mouse at top-left corner of screen
   - Enter filename (e.g., "login_macro")

4. **Record:**
   - Tap "Start Recording" (button turns red)
   - Perform actions on keyboard/mouse
   - Actions are relayed to PC in real-time via PWDongle
   - Timing is automatically captured

5. **Complete:**
   - Tap "Stop Recording" (button turns green)
   - Macro saved to PWDongle SD card as `filename.txt`

### Python Script (Desktop/Termux)

1. **Run Script:**
   ```bash
   python3 pwdongle_recorder.py
   ```

2. **Connect:**
   - Script scans for PWDongle devices
   - Select device from list
   - Enter filename

3. **Record:**
   - Position mouse at top-left
   - Press ENTER to start
   - Perform actions
   - Press ESC to stop

4. **Output:**
   - Macro saved locally as `filename.txt`
   - Transfer to PWDongle SD card manually

## Macro Format

**Generated Format:**
```
// Recorded macro: login_sequence
// Date: 2026-01-01 12:34:56
// Duration: 5.23s
// Events: 42

{{MOUSE:RESET}}
{{DELAY:100}}

{{MOUSE:MOVE:500,300}}
{{DELAY:150}}
{{MOUSE:CLICK:left}}
{{DELAY:200}}
{{KEY:tab}}
{{KEY:enter}}
{{TYPE:username}}
{{KEY:tab}}
{{TYPE:password}}
{{KEY:enter}}
```

**Key Points:**
- Always starts with `{{MOUSE:RESET}}` to move cursor to (0,0)
- Delays automatically inserted when >50ms between actions
- Absolute mouse positions for accuracy
- Compatible with existing PWDongle macro processor

## Technical Details

### BLE Communication

**Protocol:** Nordic UART Service (NUS)
- Service UUID: `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
- RX Characteristic: `6E400002-...` (Write to PWDongle)
- TX Characteristic: `6E400003-...` (Notifications from PWDongle)

**Commands:**
```
RECORD:filename\n        # Start recording
KEY:keyname\n            # Record keystroke
MOUSE:MOVE:x,y\n         # Record mouse position
MOUSE:CLICK:left\n       # Record mouse click
STOPRECORD\n             # Stop recording
```

### Mouse Position Tracking

**Problem:** USB HID mouse reports are relative (dx, dy), but accurate replay requires absolute positions.

**Solution:**
1. Recorder tracks absolute position from OS cursor API
2. Transmits absolute coordinates via BLE: `MOUSE:MOVE:x,y`
3. PWDongle maintains internal position tracker (`mouseX`, `mouseY`)
4. Calculates deltas and sends chunked HID reports
5. Macro always starts with `{{MOUSE:RESET}}` for consistent origin

### Timing Accuracy

**Delay Threshold:** 50ms minimum
- Prevents excessive delay commands for rapid typing
- Balances timing accuracy with file size
- User-configurable in both Android and Python implementations

**Timing Capture:**
- Android: `System.currentTimeMillis()`
- Python: `time.time()`
- PWDongle: `millis()` (Arduino)

## Next Steps (Future Enhancements)

### Firmware
- [ ] Add screen resolution configuration (currently 1920x1080 default)
- [ ] Implement file upload via BLE (send macro from phone to SD card)
- [ ] Add playback speed control (fast-forward/slow-motion)

### Android App
- [ ] Build and test APK
- [ ] Add file browser for saved macros
- [ ] Implement macro editing
- [ ] Add macro playback preview

### Python Script
- [ ] Add GUI version using tkinter
- [ ] Implement direct file upload to PWDongle
- [ ] Add recording pause/resume

## Testing

### Firmware Changes
```bash
cd /home/Commodore/Documents/PlatformIO/Projects/PWDongle
platformio run --target upload
```

### Android App
```bash
cd android-recorder
# Import into Android Studio
# Build → Make Project
# Run → Run on device
```

### Python Script
```bash
cd python-recorder
python3 pwdongle_recorder.py
```

## Files Modified/Created

### Modified
- `src/usb.cpp` - Enhanced mouse commands (4 locations)

### Created
- `android-recorder/` - Complete Android app (7 files)
- `python-recorder/` - Python implementation (2 files)
- `samples/AIMStest.txt` - Test macro file

## Summary

The automated macro generation system is fully implemented and ready for testing:

1. **PWDongle firmware** has enhanced mouse commands with absolute positioning
2. **Android app** provides full OTG capture with BLE transmission
3. **Python script** offers cross-platform proof-of-concept

All components follow the PWDongle macro format and work together to enable accurate recording and playback of keyboard and mouse actions.
