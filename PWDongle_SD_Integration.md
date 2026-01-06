# PWDongle SD Card Integration Summary

## Overview
PWDongle stores macro files on the microSD card. Files can be recorded over BLE using the Android app or typed from SD using the device’s built‑in file browser.

## Firmware (v0.5)

### Macro Recording via BLE
- `RECORD:filename` → opens `/filename.txt` on SD and begins recording
- `KEY:…`, `TYPE:…`, `MOUSE:…`, `GAMEPAD:…` → actions are time‑stamped and written
- `STOPRECORD` → closes the file and reports duration
- Automatic delay capture when gaps exceed 50ms

### File Browser (Device Modes)
- Boot Menu → Storage or Macro/Text lists up to 15 `.txt` files
- Short press scrolls, long press executes selected file
- Auto‑detects Advanced Scripting, DuckyScript, or Macro format

## Android App (android‑recorder)

- Provides Recorder screen with direct access to on‑screen keyboard and touchpad
- Supports USB OTG input for external keyboard/mouse
- Sends BLE commands to start/stop recording; files saved to device SD
- Spacebar reliability: on‑screen keyboard emits space via `KEY:space`
- PIN input is digit‑only; password entries disallow commas and allow other special characters

## Usage Workflow

1. Let device boot to BLE (default) and connect from the app
2. From Recorder screen, enter a filename and tap Start → `RECORD:<name>`
3. Perform actions (TYPE/KEY/MOUSE/GAMEPAD) using on‑screen or OTG inputs
4. Tap Stop → `STOPRECORD`; verify file present in device SD
5. Use Boot Menu → Storage or Macro/Text to browse and execute the file

## Testing Checklist

- [ ] Build and flash firmware
- [ ] Build and install Android app
- [ ] Connect over BLE and record a short macro
- [ ] Browse SD files from device mode and execute macro
- [ ] Confirm timing and actions (keyboard/mouse/gamepad) match expectations

### Android App:
- `app/src/main/kotlin/com/pwdongle/recorder/FileManagerFragment.kt` - Complete rewrite
- `app/src/main/kotlin/com/pwdongle/recorder/BLEManager.kt` - Added response callback system
- `app/src/main/res/layout/fragment_file_manager.xml` - Added tab toggle
- `app/build.gradle` - Added lint rules for clean build

---

## Compilation Status

✅ **PWDongle Firmware**: Flash 33.9%, RAM 17.9%
✅ **Android App**: BUILD SUCCESSFUL (85 tasks)

All code compiles without errors. Ready for flashing and testing.
