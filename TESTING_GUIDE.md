# PWDongle v0.5 Testing Guide

## Pre-Testing Setup

### ✅ Firmware Status
- **Built & Flashed**: PWDongle v0.5 (Macro Recording)
- **Key Features**: Macro recording via BLE, file browser, advanced scripting & DuckyScript auto‑detection
- **APK Ready**: `android-recorder/app/build/outputs/apk/debug/app-debug.apk`

### Installation Steps

#### 1. Install APK on Android Phone
```bash
# Via USB with ADB
adb install /home/Commodore/Documents/PlatformIO/Projects/PWDongle/android-recorder/app/build/outputs/apk/debug/app-debug.apk

# OR manually via Android Studio
# Open android-recorder project → Run → Select Device
```

#### 2. Prepare PWDongle
- Device should power on and show 3-second boot countdown on TFT
- Let it boot to Bluetooth (BLE) mode (or press BOOT to select from menu)
- Ensure SD card is inserted and functional

---

## Testing Checklist

### Phase 1: BLE Connectivity

**Test 1.1: App Launches**
- [ ] Open android-recorder on phone
- [ ] App should show "RecorderFragment" with device status
- [ ] Check logs for BLE initialization

**Test 1.2: Auto-Connect**
- [ ] Toggle auto-connect in Settings
- [ ] Restart app
- [ ] App should search for and find PWDongle
- [ ] Status should show "Connected to PWDongle"

**Test 1.3: Manual Connection**
- [ ] Disable auto-connect
- [ ] Open RecorderFragment
- [ ] Device selector spinner should populate devices
- [ ] Tap "Connect" button
- [ ] Status should show "Connected to PWDongle"

### Phase 2: Input & Recording

**Test 2.1: On-Screen Keyboard**
- [ ] Tap "Input Method" → "On-Screen Keyboard"
- [ ] Verify QWERTY layout displays
- [ ] Test typing alphabet characters
- [ ] Check shift key (should highlight)
- [ ] Test modifier keys (Ctrl, Alt, Gui)
- [ ] Type on connected computer should type via USB HID

**Test 2.2: Macro Recording**
- [ ] In Recorder screen, enter a filename
- [ ] Tap "Start Recording" (BLE sends `RECORD:<name>`) 
- [ ] Perform actions using on‑screen keyboard/touchpad or USB OTG devices
- [ ] Tap "Stop Recording" (BLE sends `STOPRECORD`) 
- [ ] Verify file is saved on the device SD card

### Phase 3: SD Card File Browser (Device)

**Test 3.1: File Listing**
- [ ] Use Boot Menu → Storage or Macro/Text mode
- [ ] Verify up to 15 `.txt` files are listed
- [ ] Scroll with short press, long press to select

**Test 3.2: File Execution**
- [ ] Select a macro file
- [ ] Long press to execute via USB HID
- [ ] Verify typing/mouse/gamepad actions occur on the connected PC

### Phase 4: BLE Responses

**Test 4.1: Response Handling**
- [ ] Watch BLE for response messages
- [ ] `OK:` prefix for success, `ERROR:` for failures
- [ ] App updates status text appropriately

### Phase 5: Firmware Commands (via BLE Terminal or App)

**Test 5.1: HELP Command** ✅
```
Send: HELP
Expected Response:
  OK: Commands:
  PWUPDATE - update passwords
  RETRIEVEPW - retrieve stored passwords
  CHANGELOGIN - change login code
  RECORD:filename - start recording
  STOPRECORD - stop recording
  PLAY:filename - play macro
  LIST - list files
  SAVE_MACRO:filename - save from BLE to SD ← NEW
  ...
```

Note: Use `RECORD:<name>` and `STOPRECORD` for creating macros; playback is selected from the SD file browser in device modes.

### Phase 6: Error Handling

**Test 6.1: Invalid Commands**
- [ ] Send gibberish command
- [ ] Device should respond with error or ignore gracefully
- [ ] App doesn't crash

**Test 6.2: Disconnection During Upload**
- [ ] Start saving macro to device
- [ ] Disconnect BLE halfway
- [ ] App should show error
- [ ] App can reconnect and retry

**Test 6.3: Filename Conflicts**
- [ ] Save macro with same name twice
- [ ] Verify second save overwrites or shows warning

### Phase 7: Edge Cases

**Test 7.1: Large Macro Upload**
- [ ] Create macro with 100+ lines
- [ ] Test chunking & MTU (20-byte packets)
- [ ] Verify all lines received intact

**Test 7.2: Special Characters**
- [ ] Macro with spaces, symbols, special keys
- [ ] Record and play back
- [ ] Verify exact reproduction

**Test 7.3: Empty Macro**
- [ ] Save empty macro to device
- [ ] Verify LIST shows file
- [ ] PLAY returns without error

**Test 7.4: No SD Card**
- [ ] Remove SD card from PWDongle
- [ ] Try SAVE_MACRO
- [ ] Device should return: "ERROR: SD card not available"
- [ ] App shows error in status

---

## Expected Behaviors

### BLE Communication
- **Chunking**: Messages > 20 bytes split automatically
- **Newlines**: Commands end with `\n`, responses may be multi-line
- **Timeout**: No response after 5 seconds shows connection error
- **Callback**: `sendCommandWithResponse()` waits for full response before callback

### SD Card File Format
```
/sdcard/macro1.txt  (PWDongle macro format)
/sdcard/macro2.txt
/sdcard/uploaded_from_phone.txt
...
```

### UI State Transitions
1. **Phone Tab** → Lists local macros → Play/Delete/Share buttons
2. **Device Tab** → Disabled if not connected → Lists device files when connected
3. **Save to Device** → Dialog → Upload → Refresh device list
4. **Play on Device** → Status update → Macro executes → Status confirmation

---

## Debugging Tips

### Check Firmware Serial Output
```bash
pio device monitor --baud 115200
# Look for SAVE_MACRO handler logs
```

### Verify BLE Commands (in Android)
- Logcat: `logcat | grep BLEManager`
- Look for `sendCommand()` and `Received:` logs

### Check SD Card Files
```bash
# Via PWDongle USB MSC mode or PC
ls /media/sdcard/
```

### Verify Macro Format
```bash
# On PWDongle via CDC mode (terminal)
cat /sdcard/test_macro.txt
```

---

## Success Criteria ✅

**Firmware**:
- [x] Builds without errors
- [x] Flashes successfully
- [ ] BLE connects from phone
- [ ] SAVE_MACRO receives content and writes to SD
- [ ] LIST shows uploaded files
- [ ] PLAY executes with USB HID output

**Android App**:
- [x] Builds APK without errors
- [ ] Installs on phone
- [ ] BLE connects to PWDongle
- [ ] File browser shows phone macros
- [ ] Device tab switches and lists device files
- [ ] Save to Device uploads macro
- [ ] Play on Device executes macro
- [ ] Status messages update correctly

---

## Common Issues & Fixes

| Issue | Cause | Solution |
|-------|-------|----------|
| App can't find device | BLE off / wrong name | Enable Bluetooth, check SSID in firmware |
| SAVE_MACRO times out | File write failure | Check SD card, verify permissions |
| LIST shows nothing | Empty SD / wrong path | Insert SD card, verify format (FAT32) |
| Playback doesn't type | USB HID not active | Ensure PLAY command executed, check GATT |
| App crashes on upload | Large file | Implement chunking / check buffer size |
| Status text not updating | Callback not fired | Check BLE response format (newline?) |

---

## Next Steps After Testing

1. **If All Tests Pass**:
   - Release v0.5.1 firmware
   - Release android-recorder v2.0
   - Document feature in README

2. **If Failures Found**:
   - Capture logs/screenshots
   - Identify root cause
   - Create bug fix PR
   - Re-test regression

3. **Future Enhancements** (based on testing):
   - DELETE command for device files
   - Batch upload/download
   - Macro sync indicator
   - Device storage info display

---

**Testing Session**: January 3, 2026  
**Firmware Version**: v0.5.1 with SAVE_MACRO  
**App Version**: android-recorder v2.0 with Device Tab
