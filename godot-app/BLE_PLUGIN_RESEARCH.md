# PWDongle Godot App - BLE Plugin Research

## Overview
This document outlines available BLE (Bluetooth Low Energy) plugin options for Godot 4.x to enable communication with the PWDongle ESP32-S3 device via Nordic UART Service.

## Plugin Requirements
- **Godot Version**: 4.3 or newer
- **Platforms**: Android and iOS
- **BLE Profile**: Nordic UART Service (NUS)
  - Service UUID: `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
  - RX Characteristic: `6E400002-B5A3-F393-E0A9-E50E24DCCA9E` (write)
  - TX Characteristic: `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` (notify)
- **Key Features**: Device scanning, connection management, characteristic read/write, notifications

---

## Option 1: Custom JNI/GDExtension (Recommended)

### Approach
Create platform-specific native plugins:
- **Android**: Java/Kotlin JNI plugin using Android BluetoothLE API
- **iOS**: Swift/Objective-C GDExtension using CoreBluetooth framework

### Pros
- Full control over BLE API access
- Optimized for PWDongle's specific needs (Nordic UART)
- No third-party dependencies
- Best performance and reliability

### Cons
- Requires native development knowledge (Java/Kotlin for Android, Swift/Obj-C for iOS)
- More initial development time
- Must maintain separate codebases for Android and iOS

### Implementation Outline

#### Android (JNI Plugin)
1. **Create Android module** in `godot-app/android/plugins/BLEPlugin/`
2. **Java classes**:
   - `BLEManager.java`: Main singleton class exposed to Godot
   - `BLEScanner.java`: Device discovery using `BluetoothLeScanner`
   - `BLEConnection.java`: Connection management, GATT operations
3. **Methods exposed to GDScript**:
   - `startBleScan()`, `stopBleScan()`
   - `connectToDevice(String address)`
   - `disconnect()`
   - `writeCharacteristic(String serviceUUID, String charUUID, byte[] data)`
   - `subscribeToCharacteristic(String serviceUUID, String charUUID)`
4. **Callbacks to Godot**:
   - `_on_device_found_callback(deviceName, deviceAddress)`
   - `_on_connection_state_changed_callback(connected)`
   - `_on_characteristic_changed_callback(charUUID, data)`
   - `_on_ble_error_callback(errorMessage)`
5. **Permissions** (AndroidManifest.xml):
   ```xml
   <uses-permission android:name="android.permission.BLUETOOTH" />
   <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
   <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
   <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
   <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
   ```

#### iOS (GDExtension)
1. **Create GDExtension** in `godot-app/ios/BLEPlugin/`
2. **Swift/Objective-C classes**:
   - `IOSBluetooth`: Main class implementing `CBCentralManagerDelegate`, `CBPeripheralDelegate`
   - Wrapped as GDExtension using Godot's C++ bridge
3. **Methods exposed to GDScript**:
   - `start_scan()`, `stop_scan()`
   - `connect_to_device(String uuid)`
   - `disconnect()`
   - `write_characteristic(String serviceUUID, String charUUID, PackedByteArray data)`
   - `subscribe_to_characteristic(String serviceUUID, String charUUID)`
4. **Signals emitted**:
   - `on_device_found`, `on_connection_changed`, `on_characteristic_changed`, `on_error`
5. **Info.plist entries**:
   ```xml
   <key>NSBluetoothAlwaysUsageDescription</key>
   <string>PWDongle needs Bluetooth to communicate with the device</string>
   <key>NSBluetoothPeripheralUsageDescription</key>
   <string>PWDongle needs Bluetooth to connect to the PWDongle device</string>
   ```

### Resources
- [Godot Android Plugins Guide](https://docs.godotengine.org/en/stable/tutorials/platform/android/android_plugin.html)
- [Godot GDExtension Documentation](https://docs.godotengine.org/en/stable/tutorials/scripting/gdextension/index.html)
- [Android BluetoothLE API](https://developer.android.com/guide/topics/connectivity/bluetooth/ble-overview)
- [iOS CoreBluetooth Framework](https://developer.apple.com/documentation/corebluetooth)

---

## Option 2: godot-bluetooth-le (Community Plugin)

### Repository
- GitHub: `https://github.com/ppiastucki/godot-bluetooth-le`
- Status: Godot 3.x (may need porting to Godot 4.x)

### Features
- Android and iOS support
- BLE scanning, connection, GATT operations
- Characteristic read/write/notify

### Pros
- Community-maintained
- Covers both platforms
- Open source (can modify if needed)

### Cons
- **Godot 3.x only** (as of last check) - would require porting to Godot 4.x
- May have limited documentation
- Dependency on community maintenance
- May not be optimized for Nordic UART Service

### Usage (if ported to Godot 4.x)
```gdscript
var ble = Bluetooth.new()
ble.connect("device_found", _on_device_found)
ble.start_scan()
```

---

## Option 3: GodotBLE (Third-Party Plugin)

### Repository
- GitHub: Search for "godot ble" or "godot bluetooth"
- Status: Check for Godot 4.x compatibility

### Notes
- Several community plugins exist with varying levels of maintenance
- Most target Godot 3.x and may need porting
- Research current status before adopting

---

## Option 4: WebBluetooth (Web Platform Only)

### Approach
Export Godot app to HTML5 and use Web Bluetooth API

### Pros
- No native plugins required
- Cross-platform (Chrome, Edge on desktop/Android)
- JavaScript BLE API is well-documented

### Cons
- **Not supported on iOS** (Safari doesn't support Web Bluetooth)
- Requires HTTPS hosting
- Web app limitations (no background operation)
- Not suitable for PWDongle use case (needs native mobile app)

### Verdict
**Not recommended** for PWDongle - requires native Android/iOS apps

---

## Recommended Approach for PWDongle

### Phase 1: Android-First Development
1. **Implement custom Android JNI plugin** (Option 1)
   - Focus on Nordic UART Service specifically
   - Minimal API surface (scan, connect, write, subscribe)
   - Test with PWDongle ESP32-S3 device
2. **Integrate with Godot app**
   - Wire `ble_android.gd` to JNI plugin
   - Test all recording commands (RECORD:, KEY:, MOUSE:, TYPE:, STOPRECORD)

### Phase 2: iOS Support
1. **Implement GDExtension for iOS** (Option 1)
   - Use CoreBluetooth framework
   - Match Android plugin API for consistency
2. **Test on iOS device**

### Phase 3: Abstraction Layer
1. **Platform-agnostic BLEManager** (`ble_manager.gd`)
   - Already implemented in current codebase
   - Loads `ble_android.gd` or `ble_ios.gd` based on platform
   - High-level API: `start_recording()`, `send_key()`, etc.

---

## Implementation Priority

1. ✅ **BLEManager abstraction layer** (completed)
2. ⏳ **Android JNI plugin** (next step)
   - `android/plugins/BLEPlugin/BLEManager.java`
   - Gradle build configuration
   - Export template integration
3. ⏳ **iOS GDExtension** (future)
   - `ios/BLEPlugin/IOSBluetooth.swift`
   - Xcode project setup
   - Export template integration

---

## Testing Strategy

1. **Android emulator limitations**: BLE requires physical device
2. **Test devices**: Android phone with Bluetooth 4.0+, iOS device with iOS 13+
3. **Test scenarios**:
   - Scan and discover PWDongle
   - Connect and subscribe to TX characteristic
   - Send commands to RX characteristic (RECORD:test, KEY:enter, STOPRECORD)
   - Receive responses from TX characteristic (OK:, ERROR:)
4. **PWDongle firmware**: Use v0.5 with BLE support

---

## References

- [Nordic UART Service Specification](https://developer.nordicsemi.com/nRF_Connect_SDK/doc/latest/nrf/libraries/bluetooth_services/services/nus.html)
- [Android BLE Guide](https://developer.android.com/guide/topics/connectivity/bluetooth/ble-overview)
- [iOS CoreBluetooth Guide](https://developer.apple.com/documentation/corebluetooth)
- [Godot Android Plugin Tutorial](https://docs.godotengine.org/en/stable/tutorials/platform/android/android_plugin.html)
- [Godot GDExtension Docs](https://docs.godotengine.org/en/stable/tutorials/scripting/gdextension/index.html)

---

## Conclusion

**Recommended**: Develop custom native plugins (Option 1) for full control and optimal Nordic UART Service integration. Start with Android JNI plugin, followed by iOS GDExtension. The abstraction layer (`ble_manager.gd`) is already designed to support this approach.
