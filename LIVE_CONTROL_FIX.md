# Live Control Fix - January 7, 2026

## Issue
Live Control keyboard typing from Android app was not working - keystrokes were not appearing on the PC.

## Root Causes
1. **Format Mismatch**: LiveControlFragment sends `KEY:a_DOWN`/`KEY:a_UP` but firmware was trying to process "a_DOWN" as a literal key name
2. **BLE Response Delay**: `sendBLEResponse()` had a `delay(20)` per response, adding 20ms+ latency per keystroke
3. **USB Reinitialization**: `processMacroText()` was calling `startUSBMode(MODE_HID)` on every keystroke, disconnecting USB keyboard

## Fixes Applied

### 1. KEY Command Format Handling (`src/usb.cpp`)
Added suffix stripping to handle LiveControl format:
```cpp
// Strip _DOWN/_UP suffixes for LiveControl format (KEY:a_DOWN -> KEY:a)
if (keyAction.endsWith("_DOWN") || keyAction.endsWith("_UP")) {
  int suffixPos = keyAction.lastIndexOf('_');
  keyAction = keyAction.substring(0, suffixPos);
}
```

### 2. BLE Response Latency (`src/bluetooth.cpp`)
Removed the `delay(20)` from `sendBLEResponse()` to eliminate latency:
```cpp
// Before: delay(20) after each chunk
// After: No delay - let BLE stack handle timing
```

### 3. USB Initialization (`src/usb.cpp`)
**Note**: `startUSBMode(MODE_HID)` was restored in `processMacroText()` for recording/playback functionality. USB is initialized once at boot in BLE mode.

## Testing
- ✅ Live Control keyboard typing works with OTG keyboard connected to Android phone
- ✅ Recording macros still functional
- ✅ Playback macros still functional
- ✅ Build successful with no errors

## Impact
- Live Control now has minimal latency for real-time keyboard control
- Recording and playback functionality preserved
