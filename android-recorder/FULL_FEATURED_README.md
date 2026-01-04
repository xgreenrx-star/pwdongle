# PWDongle Macro Recorder v2.0 - Full-Featured Kotlin App

Complete Android macro recorder application with persistent storage, file management, playback, and multi-input support.

## Features

### 🎙️ Recording
- **Hardware Input (USB OTG)**: Connect USB keyboard and mouse to smartphone via OTG adapter
- **On-Screen Keyboard**: Soft keyboard for text input and key commands
- **On-Screen Touchpad**: Virtual mouse pad for cursor control
- **Mixed Mode**: Combine hardware and on-screen input simultaneously
- **Real-time Relay**: Commands sent to PWDongle instantly during recording
- **Accurate Timing**: Automatic delay capture (configurable threshold)

### 📁 File Management
- **Persistent Storage**: Save macros to Documents/PWDongle directory
- **Browse Macros**: Scrollable list with metadata (size, last modified)
- **Delete Macros**: Remove unwanted recordings
- **Share Macros**: Export to other apps via Share intent

### ▶️ Playback
- **Speed Control**: Adjust playback speed (0.5x, 1x, 1.5x, 2x, 3x)
- **Macro Viewer**: Display macro content with syntax highlighting
- **Real-time Status**: Monitor playback progress and events
- **Stop Control**: Cancel playback at any time

### ⚙️ Settings
- **Device Pairing**: Scan for and connect to PWDongle
- **Recording Configuration**: Adjust delay threshold (10-200ms)
- **Saved Profiles**: Remember favorite settings

### 🔄 Connectivity
- **Bluetooth LE**: Nordic UART Service (NUS) communication
- **Auto-connect**: Remember last connected device
- **Status Monitoring**: Real-time connection status display
- **Command Queue**: Reliable command transmission with retries

## Project Structure

```
android-recorder/
├── src/main/
│   ├── kotlin/com/pwdongle/recorder/
│   │   ├── MainActivity.kt              # Main activity with navigation
│   │   ├── RecorderFragment.kt          # Recording UI and control
│   │   ├── FileManagerFragment.kt       # File browser and management
│   │   ├── PlaybackFragment.kt          # Macro playback
│   │   ├── InputFragment.kt             # Input device selection
│   │   ├── SettingsFragment.kt          # App settings
│   │   ├── BLEManager.kt                # Bluetooth communication (updated)
│   │   ├── MacroRecorder.kt             # Recording state machine (updated)
│   │   ├── MacroFileManager.kt          # File I/O operations
│   │   ├── MacroPlayer.kt               # Playback engine
│   │   ├── KeyboardView.kt              # On-screen keyboard
│   │   ├── TouchpadView.kt              # On-screen touchpad
│   │   ├── MouseTracker.kt              # Position tracking
│   │   └── InputCaptureService.kt       # USB OTG input handling (updated)
│   ├── res/
│   │   ├── layout/
│   │   │   ├── activity_main.xml        # Main activity layout
│   │   │   ├── fragment_recorder.xml    # Recording screen
│   │   │   ├── fragment_file_manager.xml
│   │   │   ├── fragment_playback.xml
│   │   │   ├── fragment_input.xml
│   │   │   ├── fragment_settings.xml
│   │   │   └── item_macro_file.xml      # File list item
│   │   ├── menu/
│   │   │   └── bottom_nav_menu.xml      # Bottom navigation
│   │   └── navigation/
│   │       └── nav_graph.xml            # Navigation graph
│   └── AndroidManifest.xml
├── build.gradle                         # Dependencies and build config
├── settings.gradle
└── README.md                            # This file
```

## Installation & Setup

### Prerequisites
- Android Studio 2022.1+
- Android SDK 23+ (API 23, Android 6.0)
- Gradle 7.0+
- Kotlin 1.8+

### Build

```bash
# Clone and navigate to the android-recorder directory
cd android-recorder

# Build APK
./gradlew assembleDebug

# Or use Android Studio
# File → Open → Select android-recorder folder
# Build → Make Project
```

### Install

```bash
# Via command line
adb install app/build/outputs/apk/debug/app-debug.apk

# Or use Android Studio
# Run → Run 'app' on selected device
```

## Usage Guide

### Recording a Macro

1. **Launch App** - Open "PWDongle Macro Recorder"

2. **Connect to PWDongle**
   - Tap "Settings" → "Scan for PWDongle"
   - Select device from list
   - Wait for connection confirmation

3. **Choose Input Method** (Recorder tab)
   - **Hardware (USB OTG)**: Connect keyboard/mouse to OTG adapter
   - **On-Screen Keyboard**: Virtual keyboard for text/commands
   - **Touchpad Only**: Virtual mouse pad
   - **Mixed Mode**: Both hardware and on-screen

4. **Record**
   - Enter macro filename (e.g., "login_sequence")
   - Tap "Start Recording" (button turns red)
   - Perform actions on keyboard/mouse
   - Tap "Stop Recording" (button turns green)
   - Macro automatically saved

### Playing a Macro

1. **Files Tab** - Browse saved macros
2. **Select Macro** - Tap "▶ Play" button
3. **Adjust Speed** (optional)
   - Select speed from dropdown (default 1x)
4. **Start Playback**
   - Tap "Play" button
   - Watch progress in status area
   - Tap "Stop" to cancel

### Managing Files

- **View**: See file size and last modified date
- **Delete**: Tap "🗑 Delete", confirm deletion
- **Share**: Tap "📤 Share" to send to other apps
- **Open in Editor**: Long-press to open with text editor

### Input Device Selection

#### Hardware (USB OTG)
- Connect USB keyboard/mouse to OTG adapter
- App auto-detects input devices
- Recommended for reliability and speed

#### On-Screen Keyboard
- Tap keys to type characters
- Special keys: ⌫ (backspace), ⏎ (enter)
- Number row: 1-9, 0
- Qwerty layout
- For detailed input use on-screen only

#### On-Screen Touchpad
- Slide to move mouse
- Left/Right/Middle buttons for clicks
- ↑ ↓ for scrolling
- Relative positioning (delta-based)
- Best for mouse-heavy macros

#### Mixed Mode
- Use hardware keyboard + on-screen touchpad
- Or use on-screen keyboard + hardware mouse
- Useful when one input type unavailable

## Macro File Format

Macros are stored as plain text in PWDongle format:

```
// Recorded macro: login_sequence
// Date: 2026-01-02 14:30:00
// Duration: 5200ms (5.2s)
// Events: 18

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

{{DELAY:500}}
{{MOUSE:MOVE:600,400}}
{{MOUSE:CLICK:left}}
```

### Supported Commands

| Command | Example | Notes |
|---------|---------|-------|
| `{{DELAY:ms}}` | `{{DELAY:500}}` | Wait 500ms |
| `{{KEY:name}}` | `{{KEY:enter}}` | Press key |
| `{{MOUSE:MOVE:x,y}}` | `{{MOUSE:MOVE:500,300}}` | Move to absolute position |
| `{{MOUSE:RESET}}` | `{{MOUSE:RESET}}` | Move to (0,0) |
| `{{MOUSE:CLICK:button}}` | `{{MOUSE:CLICK:left}}` | Click mouse button |
| `{{TYPE:text}}` | `{{TYPE:hello}}` | Type text |
| `{{MOUSE:SCROLL:n}}` | `{{MOUSE:SCROLL:3}}` | Scroll (pos=up, neg=down) |

## Key Permissions

- **BLUETOOTH** / **BLUETOOTH_SCAN** / **BLUETOOTH_CONNECT** - BLE communication
- **ACCESS_FINE_LOCATION** - Required for BLE scanning (Android 10+)
- **READ_EXTERNAL_STORAGE** / **WRITE_EXTERNAL_STORAGE** - Macro file storage
- **USB_ACCESSORY** - Optional, for USB OTG host mode

## Architecture

### MVVM Pattern
- **Fragments** - UI layer (display state)
- **ViewModel** - State management (future enhancement)
- **Managers** - Business logic (BLE, file I/O, macro processing)

### Threading
- **Main Thread** - UI updates, fragments
- **Coroutines** - Async file I/O, BLE operations
- **Dispatchers** - IO for file ops, Main for UI

### Bluetooth
- **Nordic UART Service** - Standard BLE communication protocol
- **MTU Chunking** - Handles large commands (20-byte chunks)
- **Notification Listeners** - Receive responses from PWDongle

## Customization

### Change On-Screen Keyboard Layout

Edit `KeyboardView.kt`:
```kotlin
private val keyRows = listOf(
    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
    // Customize rows as needed
)
```

### Adjust Recording Delay Threshold

Edit `SettingsFragment.kt`:
```kotlin
delayThresholdSlider.setOnSeekBarChangeListener { progress ->
    DELAY_THRESHOLD_MS = progress  // Configurable in real-time
}
```

### Change File Storage Location

Edit `MacroFileManager.kt`:
```kotlin
private val macroDir: File by lazy {
    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), MACRO_DIR)
}
```

## Testing

### Unit Testing
```bash
./gradlew test
```

### UI Testing
```bash
./gradlew connectedAndroidTest
```

### Manual Testing Checklist
- [ ] Connect to PWDongle via BLE
- [ ] Record with hardware keyboard
- [ ] Record with on-screen keyboard
- [ ] Record with on-screen touchpad
- [ ] Save macro to file
- [ ] Load macro from files
- [ ] Play back at 1x speed
- [ ] Play back at 2x speed
- [ ] Delete macro file
- [ ] Share macro via email
- [ ] Verify macro syntax in text editor

## Troubleshooting

### App Crashes on Start
- Grant all requested permissions
- Check Android version (6.0+ required)
- Try clearing app cache: Settings → Apps → PWDongle Recorder → Storage → Clear Cache

### Bluetooth Won't Connect
- Enable Bluetooth on device
- Ensure PWDongle is powered on and advertising
- Try forgetting device and re-pairing
- Check permission: Settings → Apps → PWDongle Recorder → Permissions → Nearby devices

### Input Not Working
- **Hardware**: Verify OTG adapter and USB keyboard/mouse are compatible
- **On-Screen**: Ensure fragment is in focus (no dialogs open)
- **Both**: Check app permissions for USB access

### Macros Not Saving
- Check storage permissions
- Verify Documents folder exists
- Try creating Documents/PWDongle folder manually
- Check available storage space

### Playback Too Fast/Slow
- Adjust speed slider before playing
- Edit delay values in macro file manually
- Verify PWDongle is responsive (check via serial terminal)

## Future Enhancements

- [ ] Macro scheduling and automation
- [ ] Cloud sync (Google Drive, Dropbox)
- [ ] Macro templates library
- [ ] Conditional execution (if/else)
- [ ] Variable support
- [ ] Macro sharing marketplace
- [ ] Analytics (playback stats, usage metrics)
- [ ] Dark mode
- [ ] Landscape keyboard layout
- [ ] Voice-to-text input

## Performance Notes

- **App Size**: ~8-10 MB (debug), ~4-5 MB (release)
- **Memory Usage**: 30-60 MB typical
- **Battery**: BLE is low-power; expect 3-5 hours with continuous recording
- **Storage**: 100KB per hour of recording (at 50ms delay threshold)

## License

Same as PWDongle project (check LICENSE file)

## Support

For issues or feature requests:
1. Check this README
2. Review troubleshooting section
3. Open GitHub issue with:
   - Device model and Android version
   - Detailed error message/logs
   - Steps to reproduce
   - Expected vs actual behavior

## Changelog

### v2.0 (Current)
- Complete rewrite in Kotlin with fragments
- On-screen keyboard and touchpad
- Persistent file storage with browser
- Macro playback with speed control
- MVVM architecture foundation
- Material Design UI
- Multi-input support
- Enhanced BLE communication

### v1.0
- Basic recording and BLE communication
- Python and Android prototypes
