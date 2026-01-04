# PWDongle Android Recorder v2.0

A full-featured Android application for recording and playing back macros for the PWDongle hardware security device.

## Overview

This is a native Android Kotlin application that provides:

- **Macro Recording**: Record keyboard and mouse input from your Android phone connected to PWDongle
- **Macro Management**: Browse, delete, and share recorded macros
- **Playback Engine**: Play back macros with adjustable speed (0.5x to 3x)
- **Multiple Input Methods**:
  - Hardware USB OTG (external keyboard/mouse)
  - On-Screen Keyboard (40-key layout)
  - Virtual Touchpad (drag, click, scroll)
  - Mixed mode (combination of inputs)
- **Bluetooth Integration**: Communicates with PWDongle via BLE Nordic UART Service
- **File Management**: Stores macros in app-scoped Documents directory with proper permissions handling
- **Adaptive UI**: Material Design with adaptive launcher icons

## Building

### Prerequisites

Installed on this system:
- Android SDK 34 at `/home/Commodore/Android/Sdk`
- Build-tools 34.0.0
- Platform 34
- Gradle 8.5 wrapper
- Java 17 (Temurin JDK at `/home/Commodore/java/jdk-17.0.9+9`)

### Build Commands

From the `android-recorder/` directory:

**Quick build (debug APK):**
```bash
./build-debug.sh
```

**Or manually:**
```bash
JAVA_HOME=/home/Commodore/java/jdk-17.0.9+9 ./gradlew assembleDebug
```

**Output:**
```
app/build/outputs/apk/debug/app-debug.apk
```

## Installation

### On Android Phone (recommended)

1. Enable USB debugging on phone:
   - Settings → About Phone → Tap "Build Number" 7 times
   - Settings → Developer Options → Enable "USB Debugging"

2. Connect phone via USB and install:
   ```bash
   /home/Commodore/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

### On Android Emulator

```bash
# Start emulator
./start-emulator.sh

# Wait ~60 seconds for boot, then install
/home/Commodore/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

### Package Structure

```
com.pwdongle.recorder
├── MainActivity                    # Navigation controller, permissions handling
├── Fragments
│   ├── RecorderFragment           # Main recording UI
│   ├── FileManagerFragment        # Macro file browser
│   ├── PlaybackFragment           # Macro playback with speed control
│   ├── InputFragment              # Input device selection
│   └── SettingsFragment           # Configuration and device pairing
├── Managers
│   ├── BLEManager                 # Bluetooth LE communication
│   ├── MacroFileManager           # File I/O and persistence
│   └── MacroPlayer                # Macro parsing and execution
├── Input Handling
│   ├── InputCaptureService        # USB OTG input capture
│   ├── KeyboardView               # On-screen keyboard (custom View)
│   └── TouchpadView               # Virtual touchpad (custom View)
├── Recording
│   └── MacroRecorder              # Event capture with timing
└── Supporting
    ├── MouseTracker               # Mouse position tracking
    └── (Layout XMLs, Navigation graph, Resources)
```

### Key Classes

**BLEManager**
- Bluetooth LE scanning and connection
- Nordic UART Service communication
- Automatic MTU chunking for large commands
- Status callbacks for UI updates

**MacroFileManager**
- Uses app-scoped external files (no legacy storage permissions)
- Async file I/O with Coroutines
- Metadata tracking (size, modified date)
- FileProvider integration for sharing

**MacroPlayer**
- Parses `{{TOKEN:ARGS}}` macro syntax
- Supports: DELAY, KEY, MOUSE, TYPE, GAMEPAD, AUDIO tokens
- Speed multiplier: scales all delays by selected factor
- Callback-based command dispatch to BLE

**RecorderFragment / PlaybackFragment / etc.**
- Fragment-based UI with Material Design
- Defensive initialization with try-catch error handling
- Graceful handling of missing/unavailable services
- Safe null-checking for optional components (BLE, etc.)

## File Formats

### PWDongle Macro Format

Default format for recorded macros:

```
// Recorded macro: example_macro
// Date: 2026-01-02 12:34:56
// Duration: 5000ms (5.0s)
// Events: 42

{{MOUSE:RESET}}
{{DELAY:100}}
{{TYPE:Hello World}}
{{DELAY:500}}
{{KEY:enter}}
```

**Tokens:**
- `{{DELAY:ms}}` - Wait N milliseconds
- `{{KEY:keyname}}` - Press key (backspace, enter, tab, etc.)
- `{{MOUSE:MOVE:x,y}}` - Move mouse to absolute position
- `{{MOUSE:UP:button}}` - Release mouse button
- `{{MOUSE:DOWN:button}}` - Press mouse button
- `{{MOUSE:SCROLL:amount}}` - Scroll wheel
- `{{TYPE:text}}` - Type text string

## Error Handling

The app includes defensive error handling for:

- **Bluetooth unavailability**: Gracefully disables BLE features with user feedback
- **Permission denials**: Shows warnings and continues with limited functionality
- **File I/O errors**: Displays error messages and returns to home screen
- **Navigation failures**: Catches and logs exceptions without crashing
- **Fragment initialization**: Safe null-checking and lazy initialization

All crashes are prevented by:
- Null-safe operators (`?.`, `?:`)
- Try-catch blocks around critical operations
- Nullable mutable state instead of lateinit
- Safe fragment lookups with `as?` casts

## Testing

### Manual Testing Workflow

1. **Build and install** (see above)
2. **Connect to PWDongle** (via BLE):
   - Tab "Input" → Find PWDongle in device list → Tap to connect
3. **Record a macro**:
   - Tab "Recorder" → Enter filename → Select input method → "Start Recording"
   - Type some text or click buttons → "Stop Recording"
4. **Browse files**:
   - Tab "Files" → See list of recorded macros
5. **Playback**:
   - Tab "Files" → Select macro → "Play" → Adjust speed → "Stop"

### Hardware Input Testing

- **USB OTG**: Connect external keyboard/mouse during recording
- **On-Screen Keyboard**: Tap keys in recording UI
- **Virtual Touchpad**: Drag to move mouse, buttons for clicks

## Permissions

The app requests:

- **Bluetooth**: BLUETOOTH_SCAN, BLUETOOTH_CONNECT (Android 12+)
- **Location**: ACCESS_FINE_LOCATION (required for BLE on Android 10+)
- **Storage**: None (app-scoped external files)

No legacy external storage permissions required.

## Dependencies

```gradle
// AndroidX
androidx.core:core-ktx:1.12.0
androidx.appcompat:appcompat:1.6.1
androidx.constraintlayout:constraintlayout:2.1.4
androidx.fragment:fragment-ktx:1.6.2
com.google.android.material:material:1.11.0
androidx.navigation:navigation-fragment-ktx:2.7.0
androidx.navigation:navigation-ui-ktx:2.7.0

// Bluetooth
androidx.bluetooth:bluetooth:1.0.0-alpha02

// Coroutines
org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3
org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3

// Storage/Files
androidx.documentfile:documentfile:1.0.1

// UI
androidx.recyclerview:recyclerview:1.3.2

// Testing
junit:junit:4.13.2
androidx.test.ext:junit:1.1.5
androidx.test.espresso:espresso-core:3.5.1
```

## Build Configuration

- **compileSdk**: 34
- **targetSdk**: 34
- **minSdk**: 23
- **Kotlin**: 1.9.10
- **AGP**: 8.1.2
- **ViewBinding**: Enabled

## Troubleshooting

### App won't start
- Check device logs: `adb logcat | grep -i "pwdongle\|crash"`
- Ensure permissions are granted
- Verify Android version is 6.0+ (minSdk 23)

### Bluetooth not working
- Enable Bluetooth on device
- Ensure PWDongle is in range and powered on
- Grant BLUETOOTH_SCAN and BLUETOOTH_CONNECT permissions
- On Android 10+, also grant ACCESS_FINE_LOCATION

### Files not saving
- Ensure app has WRITE_EXTERNAL_STORAGE permission (Android <11)
- Check device storage space

### Build fails
- Ensure JAVA_HOME points to Java 17+ with jlink support
- Rebuild with: `./gradlew clean assembleDebug`
- Check for dependency conflicts: `./gradlew dependencies`

## Future Enhancements

- [ ] Speed scaling implementation for MacroPlayer
- [ ] Dark mode support
- [ ] Macro scheduling/automation
- [ ] Cloud sync via Firebase
- [ ] Advanced search and filtering
- [ ] Macro variables and conditional playback
- [ ] Analytics and usage tracking
- [ ] Haptic feedback on recording
- [ ] App shortcuts for frequent macros
- [ ] Widget for quick playback

## Version History

- **v2.0** (2026-01-02): Full-featured Android recorder
  - Multi-fragment navigation
  - Bluetooth LE with Nordic UART
  - Macro recording with timing
  - Speed-controlled playback
  - On-screen keyboard and touchpad
  - Defensive error handling
  
- **v1.0** (Python/Godot versions): Legacy desktop/Godot recorder

## License

Part of the PWDongle project. See main project repository for license details.

## Contact

For issues or feature requests, refer to the PWDongle project documentation.
