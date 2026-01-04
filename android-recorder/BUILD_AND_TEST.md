# Build and Test Checklist

## Pre-Build Verification

### Code Quality
- [x] Created `PreferencesManager.kt` with proper documentation
- [x] Enhanced `RecorderFragment.kt` with auto-connect logic
- [x] Enhanced `SettingsFragment.kt` with toggle UI
- [x] Updated `fragment_recorder.xml` with device selector
- [x] Updated `fragment_settings.xml` with auto-connect toggle
- [ ] Review code for any syntax errors
- [ ] Verify all imports are correct
- [ ] Check for null safety issues

### Dependencies
- [x] All required dependencies already in `build.gradle`:
  - androidx.datastore:datastore-preferences
  - androidx.lifecycle:lifecycle-extensions
  - kotlinx.coroutines
- [ ] No new dependencies needed
- [ ] Gradle sync completes successfully

## Build Steps

1. **Clean Project**
   ```bash
   cd /home/Commodore/Documents/PlatformIO/Projects/PWDongle/android-recorder
   ./gradlew clean
   ```
   - [ ] Command completes without errors
   - [ ] `build/` directory is removed

2. **Build Debug APK**
   ```bash
   ./gradlew assembleDebug
   ```
   - [ ] Build completes successfully
   - [ ] No compilation errors
   - [ ] APK generated at `app/build/outputs/apk/debug/app-debug.apk`
   - [ ] File size reasonable (not unusually large)

3. **Run Tests (if available)**
   ```bash
   ./gradlew test
   ```
   - [ ] Unit tests pass
   - [ ] No test failures

4. **Lint Check (Optional)**
   ```bash
   ./gradlew lint
   ```
   - [ ] No critical issues
   - [ ] Warnings are acceptable

## Installation Steps

1. **Connect Device**
   - [ ] Android phone/tablet connected via USB
   - [ ] USB debugging enabled
   - [ ] Device recognized by `adb devices`

2. **Install APK**
   ```bash
   ./gradlew installDebug
   ```
   - [ ] Installation succeeds
   - [ ] "installed" message appears
   - [ ] App icon visible on home screen

## Functional Testing

### Test 1: Auto-Connect Enabled (Default Behavior)

**Setup**:
- [ ] App freshly installed
- [ ] PWDongle powered ON and within Bluetooth range
- [ ] Phone Bluetooth enabled
- [ ] App Bluetooth permissions granted

**Test Steps**:
1. [ ] Open app
2. [ ] Recorder tab is displayed
3. [ ] Status shows "Auto-connect ENABLED - Searching for PWDongle..."
4. [ ] Device selection UI is HIDDEN (not visible)
5. [ ] Wait ~10 seconds for scan to complete
6. [ ] Status changes to "Connected to PWDongle" (if found)
7. [ ] Record button becomes enabled
8. [ ] Recording functionality works

**Expected Results**:
- [ ] App finds PWDongle automatically
- [ ] Connection happens without user action
- [ ] Status updates are clear
- [ ] Recording controls are available

### Test 2: Auto-Connect Fallback (Device Not Found)

**Setup**:
- [ ] App freshly installed
- [ ] PWDongle powered OFF or out of range
- [ ] Other Bluetooth devices nearby (optional, for testing fallback)

**Test Steps**:
1. [ ] Open app
2. [ ] Status shows "Auto-connect ENABLED - Searching for PWDongle..."
3. [ ] Wait ~10 seconds
4. [ ] Status shows device selection fallback
5. [ ] Device list appears
6. [ ] User can select device from list
7. [ ] Can establish connection to selected device

**Expected Results**:
- [ ] App searches but doesn't find PWDongle
- [ ] Gracefully falls back to manual selection
- [ ] Devices are discovered and listed
- [ ] Manual connection works

### Test 3: Auto-Connect Toggle (Settings)

**Setup**:
- [ ] App installed and running

**Test Steps**:
1. [ ] Navigate to Settings tab
2. [ ] Scroll up to find "Auto-Connect to PWDongle"
3. [ ] Toggle is present and functional
4. [ ] Toggle shows current state (ON by default)
5. [ ] Click toggle to turn OFF
6. [ ] Status message updates to mention manual selection
7. [ ] Return to Recorder tab
8. [ ] Device selector UI appears
9. [ ] Go back to Settings
10. [ ] Toggle OFF is still selected
11. [ ] Toggle back ON
12. [ ] Go to Recorder tab
13. [ ] Auto-search begins again

**Expected Results**:
- [ ] Toggle works smoothly
- [ ] UI updates respond correctly
- [ ] Preference persists across navigation
- [ ] Status messages are accurate

### Test 4: Preference Persistence

**Setup**:
- [ ] App running with auto-connect disabled (toggle OFF)

**Test Steps**:
1. [ ] Note that preference is set to OFF
2. [ ] Close app completely
3. [ ] Open app again
4. [ ] Go to Settings tab
5. [ ] Check toggle position

**Expected Results**:
- [ ] Toggle is still OFF
- [ ] Preference persisted to DataStore
- [ ] App remembers user's choice

### Test 5: Manual Device Selection

**Setup**:
- [ ] Auto-connect disabled in Settings
- [ ] Other Bluetooth devices available

**Test Steps**:
1. [ ] Go to Settings tab
2. [ ] Tap "Scan for Devices" button
3. [ ] Wait 10 seconds for scan
4. [ ] Device list populates
5. [ ] Select a device from list
6. [ ] Go to Recorder tab
7. [ ] Device selector shows selected device
8. [ ] Tap "Connect" button
9. [ ] Status updates with connection attempt
10. [ ] If compatible, connection succeeds

**Expected Results**:
- [ ] Scan finds all nearby devices
- [ ] Device selection works
- [ ] Selected device is remembered
- [ ] Manual connection succeeds for valid devices

### Test 6: Recording with Auto-Connect

**Setup**:
- [ ] Auto-connect enabled
- [ ] PWDongle connected (from auto-connect)

**Test Steps**:
1. [ ] Status shows "Connected to PWDongle"
2. [ ] Enter filename in text field
3. [ ] Tap "Start Recording"
4. [ ] Perform keyboard/mouse input (if OTG enabled)
5. [ ] Tap "Stop Recording"
6. [ ] Check file was saved
7. [ ] Verify macro content is correct

**Expected Results**:
- [ ] Recording starts without manual connection step
- [ ] Input is captured correctly
- [ ] File saves successfully
- [ ] Auto-connect saves time

### Test 7: Device Switching

**Setup**:
- [ ] App connected to one device
- [ ] Multiple Bluetooth devices available

**Test Steps**:
1. [ ] Go to Settings, manually disconnect current device
2. [ ] Scan for different device
3. [ ] Select and connect to different device
4. [ ] Verify connection succeeds
5. [ ] Return to Recorder tab
6. [ ] Status shows new device name

**Expected Results**:
- [ ] Can switch between devices
- [ ] Device list updates correctly
- [ ] New connections work properly

### Test 8: UI Responsiveness

**Test Steps**:
1. [ ] Toggle auto-connect rapidly
2. [ ] Tab between Settings and Recorder
3. [ ] Perform other UI interactions during scan
4. [ ] Check status messages update smoothly

**Expected Results**:
- [ ] App remains responsive
- [ ] No freezing or crashes
- [ ] All animations/transitions smooth
- [ ] No ANR (Application Not Responding) warnings

## Regression Testing

### Existing Features (Ensure Not Broken)

- [ ] Recording functionality still works
- [ ] File browser works
- [ ] Playback works
- [ ] Input method selection works
- [ ] Manual device scan (in Settings) works
- [ ] Other fragments load correctly
- [ ] Navigation between tabs works

## Error Handling Tests

### Test 1: Bluetooth Disabled
- [ ] Turn off device Bluetooth
- [ ] Open app
- [ ] Check error message is clear
- [ ] App doesn't crash

### Test 2: Permissions Missing
- [ ] Revoke Bluetooth permissions
- [ ] Open app
- [ ] Check permission request dialog
- [ ] Grant permission when prompted

### Test 3: Device Disconnects
- [ ] Connect to PWDongle
- [ ] Turn off PWDongle mid-recording
- [ ] App should handle gracefully
- [ ] Status updates to disconnected

## Performance Tests

- [ ] App launch time acceptable (~2 seconds)
- [ ] Scan completion ~10 seconds
- [ ] Auto-connect detection fast (< 1 second when found)
- [ ] UI responsive during scan
- [ ] No memory leaks (check logcat)
- [ ] Battery drain reasonable

## Documentation Review

- [ ] AUTO_CONNECT_FEATURE.md is complete
- [ ] AUTO_CONNECT_QUICKSTART.md is helpful
- [ ] AUTO_CONNECT_DIAGRAMS.md is clear
- [ ] Code comments are present
- [ ] Function documentation is complete

## Final Checklist

- [ ] All source files compile without errors
- [ ] All tests pass
- [ ] APK builds and installs successfully
- [ ] All functional tests pass
- [ ] No crashes or ANRs
- [ ] No regression in existing features
- [ ] User-facing messages are clear
- [ ] Documentation is complete
- [ ] Code is ready for production

## Build Artifacts

After successful build:
- [ ] `app/build/outputs/apk/debug/app-debug.apk` exists
- [ ] Size is reasonable (typically 5-10MB for Android app)
- [ ] APK can be shared/distributed

## Post-Deploy Steps

1. [ ] Update version number in `build.gradle`
2. [ ] Tag release in version control
3. [ ] Update CHANGELOG
4. [ ] Consider creating a GitHub Release
5. [ ] Notify users of new feature

## Quick Commands

```bash
# Clean and rebuild
./gradlew clean assembleDebug

# Build and install to device
./gradlew installDebug

# Run app
./gradlew installDebug  # then manually open on device

# View logs
adb logcat | grep "RecorderFragment\|SettingsFragment\|PreferencesManager\|BLEManager"

# Uninstall app
adb uninstall com.pwdongle.recorder

# View DataStore preferences (via adb shell)
adb shell "run-as com.pwdongle.recorder cat /data/data/com.pwdongle.recorder/files/datastore/pwdongle_prefs.preferences_pb" | od -An -tx1
```

## Notes

- Auto-connect defaults to **enabled** (can be changed)
- Preferences stored in DataStore (not SharedPreferences)
- All BLE operations are safe for Android 12+
- Scan timeout is 10 seconds (configurable)
- Device name matching is case-insensitive

---

**Testing Status**: [ ] Not Started  [ ] In Progress  [ ] Completed

**Date Started**: ________  
**Date Completed**: ________  
**Tester Name**: ________  
**Notes**: ________________________________________
