# PWDongle Recorder - Android Build & Deployment Guide

## Project Status

The complete Kotlin Android application has been created with all source code, layouts, and build configuration files. However, to compile and build the APK, you'll need to use Android Studio or set up a proper Android development environment.

## Project Structure

```
android-recorder/
├── app/
│   ├── src/main/
│   │   ├── kotlin/com/pwdongle/recorder/
│   │   │   ├── MainActivity.kt
│   │   │   ├── RecorderFragment.kt
│   │   │   ├── FileManagerFragment.kt
│   │   │   ├── PlaybackFragment.kt
│   │   │   ├── InputFragment.kt
│   │   │   ├── SettingsFragment.kt
│   │   │   ├── BLEManager.kt
│   │   │   ├── MacroRecorder.kt
│   │   │   ├── MacroFileManager.kt
│   │   │   ├── MacroPlayer.kt
│   │   │   ├── KeyboardView.kt
│   │   │   └── TouchpadView.kt
│   │   ├── res/
│   │   │   ├── layout/          (8 XML layout files)
│   │   │   ├── menu/            (Bottom navigation menu)
│   │   │   ├── navigation/      (Navigation graph)
│   │   │   ├── values/          (strings, colors, styles)
│   │   │   └── xml/             (FileProvider configuration)
│   │   └── AndroidManifest.xml
│   ├── build.gradle             (App module configuration)
│   └── proguard-rules.pro       (Code obfuscation rules)
├── gradle.properties            (Gradle configuration)
├── settings.gradle              (Root project configuration)
└── build.gradle                 (Root build configuration)
```

## Build Methods

### Option 1: Android Studio (Recommended)

1. **Install Android Studio**: Download from https://developer.android.com/studio
2. **Open Project**: File → Open → Select the `android-recorder` folder
3. **Configure SDK**: Android Studio will prompt you to install Android SDK if needed
   - Accept the default SDK installation location
   - Install API 33 (target SDK)
   - Install API 23 (minimum SDK)
4. **Build**: Build → Make Project
5. **Run/Debug**: Select Run → Run 'app' to deploy to emulator or physical device

### Option 2: Command Line (Linux/Mac/Windows)

#### Prerequisites
- Install Java Development Kit (JDK 11+)
- Install Android SDK (or Android Command Line Tools)
- Set environment variables:
  ```bash
  export ANDROID_HOME=$HOME/Android/Sdk
  export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/tools/bin
  ```

#### Build Steps
```bash
cd android-recorder

# Create gradle wrapper (first time only)
gradle wrapper --gradle-version 8.5

# Build debug APK
./gradlew clean assembleDebug

# Build release APK (requires keystore)
./gradlew clean assembleRelease
```

**Output:**
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`

### Option 3: Cloud Build (GitHub Actions)

Create `.github/workflows/android-build.yml`:

```yaml
name: Build Android APK

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 11
        uses: actions/setup-java@v3
        with:
          java-version: '11'
          distribution: 'temurin'
      
      - name: Grant execute permission for gradlew
        run: chmod +x android-recorder/gradlew
      
      - name: Build APK
        run: cd android-recorder && ./gradlew assembleDebug
      
      - name: Upload APK
        uses: actions/upload-artifact@v3
        with:
          name: app-debug
          path: android-recorder/app/build/outputs/apk/debug/app-debug.apk
```

## Installation on Device

### Via USB Debugging
```bash
# Enable USB Debugging on Android device
# Settings → Developer Options → USB Debugging

# Connect device via USB
adb devices  # Verify connection

# Install debug APK
adb install app-debug.apk

# OR: Install from Android Studio
#   Run → Run 'app' → Select device
```

### Via APK File
1. Transfer `app-debug.apk` to Android device
2. Open file manager on device
3. Tap APK file to install
4. Grant permissions when prompted

## Features Implemented

### ✅ Macro Recording
- Start/stop recording with filename input
- Support for multiple input methods:
  - Hardware OTG USB input
  - On-screen 40-key keyboard
  - Virtual touchpad with mouse control
  - Mixed input mode
- Automatic delay capture (>50ms threshold)
- Saves to `Documents/PWDongle/` with metadata

### ✅ File Management
- Browse saved macro files
- File metadata (size, modification date)
- Delete and rename operations
- Share via Android Share Sheet
- RecyclerView with search support (layout prepared)

### ✅ Macro Playback
- Execute saved macros with speed control (0.5x to 3x)
- Support for PWDongle macro format (`{{TOKEN:ARGS}}`)
- Sends commands to device via Bluetooth LE
- Real-time progress display

### ✅ Bluetooth Integration
- Nordic UART Service (NUS) support
- Auto-connect to paired PWDongle device
- Command transmission with proper framing
- Notification callbacks

### ✅ Custom Input Views
- **KeyboardView**: 40-key on-screen keyboard with special keys
- **TouchpadView**: Virtual touchpad with drag-to-move, buttons, and scroll
- Touch event handling with visual feedback
- Integration with recording system

### ✅ UI/UX
- Fragment-based navigation (5 tabs)
- Bottom navigation bar
- Material Design 3 components
- Responsive layouts
- Dark mode ready

## Permissions & Configuration

### Android Permissions Required
- `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`
- `ACCESS_FINE_LOCATION` (required for BLE scanning)
- `WRITE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE` (macro files)
- `INTERNET` (optional, for future cloud features)

### Configuration Files
- **AndroidManifest.xml**: Declares activities, permissions, FileProvider
- **build.gradle**: Dependencies, compileSdk 33, targetSdk 33, minSdk 23
- **strings.xml**: UI text (internationalization ready)
- **styles.xml**: Material Design theme
- **colors.xml**: Color palette
- **file_paths.xml**: FileProvider paths for Documents/PWDongle

## Troubleshooting

### Build Errors

**Error**: `ANDROID_HOME not set`
```bash
export ANDROID_HOME=$HOME/Android/Sdk
```

**Error**: `No SDK found`
- Use Android Studio → SDK Manager to install API 33
- Or: `sdkmanager "platforms;android-33" "build-tools;33.0.0"`

**Error**: `Gradle wrapper not found`
```bash
gradle wrapper --gradle-version 8.5
chmod +x gradlew
```

### Runtime Issues

**Problem**: App crashes on startup
- Check LogCat: `adb logcat | grep PWDongle`
- Common causes:
  - Missing permissions (grant via Settings)
  - File system access issues (check Documents folder exists)
  - Bluetooth device not paired

**Problem**: Bluetooth not connecting
- Ensure PWDongle device is powered and in Bluetooth mode
- Check device appears in Android Settings → Bluetooth
- Verify app has Bluetooth permissions
- Check BLE UART service UUID matches device

**Problem**: Files not saving
- Check app has `WRITE_EXTERNAL_STORAGE` permission
- Verify Documents folder is accessible
- Check device has sufficient storage

## Development Workflow

### Making Changes
1. Edit Kotlin source files in `app/src/main/kotlin/`
2. Edit layouts in `app/src/main/res/layout/`
3. Update strings in `app/src/main/res/values/strings.xml`
4. Run: `./gradlew assembleDebug` (or use Android Studio)

### Testing
1. Connect Android device (or start emulator)
2. Run: `./gradlew installDebug` (or Android Studio's Run button)
3. Test each feature:
   - Recording with different input methods
   - File browser (list, delete, rename)
   - Playback with speed control
   - Bluetooth connectivity
   - Settings/preferences

### Publishing
1. Generate signed APK:
   ```bash
   ./gradlew assembleRelease
   ```
2. Sign APK (Android Studio provides UI for this)
3. Upload to Google Play Store, GitHub Releases, or distribute directly

## Dependencies

### AndroidX & Material
- androidx.appcompat:appcompat (1.6.1)
- androidx.fragment:fragment-ktx (1.6.2)
- androidx.navigation:navigation-fragment-ktx (2.7.0)
- androidx.constraintlayout:constraintlayout (2.1.4)
- com.google.android.material:material (1.11.0)

### Bluetooth & Coroutines
- androidx.bluetooth:bluetooth (1.1.1)
- kotlinx-coroutines-android (1.7.3)
- kotlinx-coroutines-core (1.7.3)

### File Management
- androidx.documentfile:documentfile (1.0.1)
- androidx.datastore:datastore-preferences (1.0.0)

See `app/build.gradle` for complete dependency list.

## Next Steps

1. **Build the APK**:
   - Using Android Studio: Open project → Build → Make Project
   - Via command line: `cd android-recorder && ./gradlew assembleDebug`

2. **Test on Device**:
   - Install: `adb install app-debug.apk`
   - Verify all permissions are granted
   - Test each feature (recorder, playback, file manager, etc.)

3. **Deploy to Store** (Optional):
   - Create release build: `./gradlew assembleRelease`
   - Sign APK with keystore
   - Upload to Google Play Store

4. **Continuous Integration** (Optional):
   - Add GitHub Actions workflow for automatic builds
   - Push to repository, APK automatically generated

## Support & Debugging

For issues or questions:
- Check device LogCat: `adb logcat | grep -E "(PWDongle|Error|Exception)"`
- Review Gradle output for compilation errors
- Verify permissions in Android Settings → Apps → PWDongle Recorder
- Test Bluetooth connection via Android Settings

---

**Project**: PWDongle Bluetooth Macro Recorder
**Version**: 2.0
**Language**: Kotlin
**Target API**: 33 (Android 13+)
**Minimum API**: 23 (Android 6.0+)
**Status**: Complete - Ready to Build & Deploy
