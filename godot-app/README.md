# PWDongle Godot Companion App

Cross-platform mobile app for controlling PWDongle ESP32-S3 device via Bluetooth Low Energy (BLE).

## Features

- **BLE Connection**: Scan and connect to PWDongle via Nordic UART Service
- **Macro Recording**: Live keyboard/mouse capture from smartphone OTG input to ESP32 storage
- **Quick Keys**: Send common keystrokes (Enter, Tab, Ctrl+C, etc.) with one tap
- **Text Typing**: Type arbitrary text strings via USB HID
- **Console Output**: View command responses and status messages from PWDongle

## Requirements

- **Godot Engine**: 4.3 or newer
- **Platform**: Android 8.0+ or iOS 13+ with Bluetooth 4.0+
- **PWDongle Device**: ESP32-S3 running firmware v0.5 or newer with BLE support
- **Bluetooth Permissions**: Location (Android), Bluetooth Always Usage (iOS)

## Project Structure

```
godot-app/
├── project.godot              # Godot 4.3 project configuration
├── scenes/
│   └── main.tscn              # Main UI scene
├── scripts/
│   ├── main.gd                # Main UI controller
│   ├── ble_manager.gd         # BLE abstraction layer
│   ├── ble_android.gd         # Android BLE implementation (JNI)
│   └── ble_ios.gd             # iOS BLE implementation (GDExtension)
├── android/                   # Android-specific files (native plugin)
├── ios/                       # iOS-specific files (GDExtension)
└── BLE_PLUGIN_RESEARCH.md     # BLE plugin implementation guide
```

## Building the App

### Prerequisites
1. Install [Godot 4.3+](https://godotengine.org/download)
2. Clone this repository
3. Open `godot-app/project.godot` in Godot Editor

### Android Export
1. Install Android SDK and Android Export Templates in Godot
2. **Build native BLE plugin** (see [BLE_PLUGIN_RESEARCH.md](BLE_PLUGIN_RESEARCH.md)):
   - Implement `android/plugins/BLEPlugin/BLEManager.java`
   - Build plugin AAR and place in `android/plugins/`
3. Configure Android Export Preset:
   - Min SDK: 26 (Android 8.0)
   - Permissions: Bluetooth, Bluetooth Admin, Bluetooth Scan/Connect, Fine Location
4. Export → Android → APK/AAB

### iOS Export
1. Install iOS Export Templates in Godot
2. **Build GDExtension** (see [BLE_PLUGIN_RESEARCH.md](BLE_PLUGIN_RESEARCH.md)):
   - Implement `ios/BLEPlugin/IOSBluetooth.swift`
   - Build GDExtension framework
3. Configure iOS Export Preset:
   - Target iOS: 13.0+
   - Info.plist: Add Bluetooth usage descriptions
4. Export → iOS → Xcode Project
5. Open Xcode project and build

## Usage

### Connecting to PWDongle
1. Power on PWDongle ESP32-S3
2. Boot to **Bluetooth (BLE)** mode (default boot option)
3. Open PWDongle app on smartphone
4. Tap **"Scan for Devices"**
5. Select PWDongle from device list
6. Tap **"Connect"**
7. Status should show "Connected to PWDongle"

### Recording a Macro
1. Ensure connection is established
2. Connect OTG keyboard/mouse to smartphone
3. Enter filename in "Macro Recording" panel (e.g., `my_macro.txt`)
4. Tap **"Start Recording"**
5. Type keys or move mouse on OTG devices
6. Tap **"Stop Recording"** when done
7. Macro saved to PWDongle SD card with automatic delay timing

### Sending Quick Keys
1. Connect to PWDongle
2. Tap any quick key button (ENTER, TAB, CTRL+C, etc.)
3. Keystroke sent via USB HID to connected PC

### Typing Text
1. Connect to PWDongle
2. Enter text in "Type Text" panel
3. Tap **"Send Text"**
4. Text typed via USB HID to connected PC

## BLE Command Protocol

The app communicates with PWDongle via Nordic UART Service (NUS):
- **Service UUID**: `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
- **RX Characteristic**: `6E400002-B5A3-F393-E0A9-E50E24DCCA9E` (write commands)
- **TX Characteristic**: `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` (read responses)

### Command Format
Commands are line-based (terminated with `\n`):

**System Commands:**
- `HELP` - Get available commands
- `ABOUT` - Get PWDongle version info

**Recording Commands:**
- `RECORD:<filename>` - Start macro recording
- `STOPRECORD` - Stop recording
- `KEY:<key_name>` - Record keystroke (e.g., `KEY:enter`)
- `MOUSE:<action>` - Record mouse action (e.g., `MOUSE:leftclick`)
- `TYPE:<text>` - Record text typing (e.g., `TYPE:hello world`)
- `GAMEPAD:<action>` - Record gamepad input

**Responses:**
- `OK: <message>` - Success
- `ERROR: <message>` - Failure

See [BLE_USAGE.md](../BLE_USAGE.md) in main project for full protocol details.

## Development Notes

### BLE Manager Abstraction
`scripts/ble_manager.gd` provides a platform-agnostic API:
- Auto-loads `ble_android.gd` or `ble_ios.gd` based on OS
- Handles MTU chunking (20 bytes per packet)
- Provides convenience methods for common operations
- Signal-based async architecture

### Platform-Specific Implementations
- **Android**: `ble_android.gd` uses JNI to call native Java plugin
- **iOS**: `ble_ios.gd` uses GDExtension to call CoreBluetooth framework
- Both must implement same interface: `start_scan()`, `connect_to_device()`, `write_characteristic()`, etc.

### Native Plugin Status
⚠️ **Native plugins not yet implemented**. Current GDScript files are stubs that require:
1. Android: JNI plugin with BluetoothLE API
2. iOS: GDExtension with CoreBluetooth framework

See [BLE_PLUGIN_RESEARCH.md](BLE_PLUGIN_RESEARCH.md) for implementation guide.

## Troubleshooting

### "JNI singleton not available" (Android)
- Native BLE plugin not built or not added to export preset
- Follow Android plugin build steps in BLE_PLUGIN_RESEARCH.md

### "IOSBluetooth native class not found" (iOS)
- GDExtension not compiled or not included in export
- Follow iOS GDExtension build steps in BLE_PLUGIN_RESEARCH.md

### Cannot discover PWDongle device
- Ensure PWDongle is in Bluetooth (BLE) mode
- Check Bluetooth permissions granted to app
- On Android 12+, enable "Nearby Devices" permission
- PWDongle must be powered and not already connected to another device

### Connection drops during recording
- BLE connection unstable (check distance, interference)
- PWDongle may have rebooted (check ESP32 serial output)
- Ensure PWDongle firmware is v0.5+

## License

This companion app is licensed under the **GNU General Public License v3.0 (or later)**, consistent with the main PWDongle project. See the root `LICENSE` file for the full text.

## Related Documentation

- [PWDongle Main README](../README.md) - Firmware documentation
- [BLE Usage Guide](../BLE_USAGE.md) - BLE command protocol details
- [BLE Plugin Research](BLE_PLUGIN_RESEARCH.md) - Native plugin implementation guide
