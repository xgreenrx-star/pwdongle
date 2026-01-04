# PWDongle Auto-Connect Feature

## Overview

The Android app now includes an intelligent auto-connect feature that automatically searches for and connects to PWDongle on startup. Users can toggle this behavior in Settings to either enable automatic connection or manually select from available devices.

## Features

### Auto-Connect (Default - Enabled)
- **Automatic Device Discovery**: On app startup, the app automatically scans for PWDongle devices
- **Smart Detection**: Looks for devices with "PWDongle" in their name
- **Silent Connection**: Seamlessly connects without user interaction
- **Fallback UI**: If PWDongle is not found, displays available devices for manual selection
- **Status Feedback**: Clear status messages guide users through the connection process

### Manual Connection (When Auto-Connect is Disabled)
- **Device List**: Shows all discovered Bluetooth devices
- **Manual Selection**: Users can browse and select from available devices
- **Connection Control**: "Connect" button to initiate connection to selected device
- **Saved Devices**: Recently connected devices are saved for quick re-connection

## Settings

### Auto-Connect Toggle
Located in the **Settings** tab, the "Auto-Connect to PWDongle" toggle controls the connection behavior:

- **Enabled (Default)**: 
  - App automatically searches for PWDongle on startup
  - User sees "Searching for PWDongle..." message
  - Manual device selection UI is hidden
  - If PWDongle is found, connection happens automatically

- **Disabled**:
  - Device selection UI appears in the Recorder tab
  - User must manually scan for devices (via Settings tab)
  - User must select a device from the list
  - User must press "Connect" button to establish connection

## Implementation Details

### New Classes

#### `PreferencesManager.kt`
Manages app preferences using Android DataStore:
- `autoConnectEnabledFlow`: Observable preference for auto-connect setting (default: true)
- `lastConnectedDeviceFlow`: Tracks the last successfully connected device
- `savedDevicesFlow`: Maintains list of previously connected devices

Methods:
- `setAutoConnectEnabled(enabled: Boolean)`: Toggle auto-connect on/off
- `setLastConnectedDevice(deviceName: String)`: Save last connected device
- `addSavedDevice(deviceName: String)`: Add device to saved list
- `removeSavedDevice(deviceName: String)`: Remove device from saved list

### Modified Classes

#### `RecorderFragment.kt`
Enhanced to support auto-connect workflow:
- Initializes `PreferencesManager`
- Observes `autoConnectEnabledFlow` to determine connection mode
- `autoConnectToPWDongle()`: Automatically searches for PWDongle
  - Scans for all BLE devices
  - Filters for devices containing "PWDongle" in name
  - Auto-connects if found
  - Shows device selection fallback if not found
- `showDeviceSelection()`: Displays device list when auto-connect fails
- Device selection UI toggles visibility based on auto-connect setting

#### `SettingsFragment.kt`
Added auto-connect preference UI:
- `autoConnectSwitch`: Toggle to enable/disable auto-connect
- Real-time preference updates
- Device selection on manual scan
- Saves selected devices for future use

### Updated Layouts

#### `fragment_settings.xml`
New auto-connect section:
- Switch toggle with descriptive text
- Explains auto-connect behavior
- Updated "Scan for Devices" button
- Clearer status messages

#### `fragment_recorder.xml`
New device selection UI:
- `deviceSelector` LinearLayout (hidden by default when auto-connect enabled)
- `deviceSpinner`: Dropdown list of available devices
- `connectButton`: Initiates connection to selected device
- Shows/hides based on auto-connect setting

## Usage Flow

### Scenario 1: Auto-Connect Enabled (Default)

1. User opens app
2. `RecorderFragment` loads and checks preference
3. App automatically starts BLE scan
4. Status shows "Searching for PWDongle..."
5. If PWDongle found:
   - Auto-connects
   - Shows "Connected to PWDongle" status
   - Recording controls become available
6. If PWDongle not found:
   - Shows available devices
   - User can manually select one
   - User presses "Connect" to establish connection

### Scenario 2: Auto-Connect Disabled

1. User opens app
2. Device selection UI appears in Recorder tab
3. User navigates to Settings tab
4. User taps "Scan for Devices" button
5. App scans for available Bluetooth devices
6. User taps a device from the list (e.g., "PWDongle-1234")
7. App connects to selected device
8. Back in Recorder tab, device selection dropdown shows selected device
9. User can press "Connect" to reconnect if needed

### Scenario 3: Changing Auto-Connect Setting

1. User opens Settings tab
2. Toggles "Auto-Connect to PWDongle" switch
3. If enabling: Recorder tab will auto-search on next visit
4. If disabling: Device selection UI appears on next visit to Recorder tab

## Technical Details

### Data Persistence
- Preferences stored using Android DataStore (thread-safe, coroutine-based)
- Namespace: `pwdongle_prefs`
- Keys:
  - `auto_connect_enabled`: Boolean (default: true)
  - `last_connected_device`: String (device name)
  - `saved_devices`: Comma-separated device list

### Coroutine Integration
- Uses `viewLifecycleOwner.lifecycleScope` for safe lifecycle-aware execution
- `Flow<T>` for reactive preference updates
- All preference operations are suspend functions

### BLE Scanning
- Uses `BLEManager.scanForDevices()` with 10-second timeout
- Low-latency scan mode for faster device discovery
- Filters results for "PWDongle" in device name
- Graceful fallback if device not found

### Error Handling
- Permission checks before scanning
- Null-safe device selection
- Graceful degradation if BLE unavailable
- User-friendly error messages

## Configuration

### Default Behavior
Auto-connect is **enabled by default**. To change:

**Disable Auto-Connect by Default:**
In `PreferencesManager.kt`, change:
```kotlin
val autoConnectEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
    preferences[AUTO_CONNECT_ENABLED] ?: false  // Change true to false
}
```

### Custom Device Name Matching
To match different device names, modify `RecorderFragment.autoConnectToPWDongle()`:
```kotlin
// Look for "PWDongle" in device names
val pwdongleDevice = devices.find { it.contains("PWDongle", ignoreCase = true) }

// Or customize with your device name:
val pwdongleDevice = devices.find { it.contains("YourDeviceName", ignoreCase = true) }
```

## Testing

### Test Case 1: Auto-Connect Success
1. Enable auto-connect in settings
2. Ensure PWDongle is powered on and nearby
3. Open app - should connect automatically
4. Verify status shows "Connected to PWDongle"

### Test Case 2: Auto-Connect Failure Fallback
1. Enable auto-connect in settings
2. Ensure PWDongle is powered OFF
3. Open app - should show device selection
4. Select another BLE device from list
5. Verify connection works with selected device

### Test Case 3: Manual Mode
1. Disable auto-connect in settings
2. Open Recorder tab - device selection should be visible
3. Navigate to Settings, scan for devices
4. Select PWDongle from list and connect
5. Return to Recorder tab - should show "Connected"

### Test Case 4: Toggle Auto-Connect
1. Enable auto-connect - verify auto-search occurs
2. Disable auto-connect - verify device selection appears
3. Re-enable auto-connect - verify auto-search resumes

## Future Enhancements

- [ ] Remember last connected device and auto-reconnect if still available
- [ ] Connection timeout with automatic retry
- [ ] Signal strength indicator in device list
- [ ] Device name customization in app
- [ ] Bluetooth connection history/favorites
- [ ] Reconnect button for lost connections
- [ ] Background reconnection attempts

## Troubleshooting

### App doesn't find PWDongle
1. Verify PWDongle is powered on
2. Check Bluetooth is enabled on phone
3. Ensure app has Bluetooth permissions
4. Try manually scanning in Settings
5. Check PWDongle device name in its UI

### Manual connection doesn't work
1. Verify Bluetooth permissions are granted
2. Try toggling Bluetooth off/on
3. Try scanning again in Settings
4. Check device is not already connected

### Can't toggle auto-connect
1. Ensure Settings tab is accessible
2. Check app doesn't crash on toggle
3. Verify DataStore is initialized
4. Check for storage permissions

## Dependencies

- `androidx.datastore:datastore-preferences`: Preference storage
- `androidx.lifecycle:lifecycle-extensions`: Lifecycle awareness
- `androidx.fragment:fragment-ktx`: Fragment extensions
- `kotlinx.coroutines`: Async operations

All dependencies are already included in `app/build.gradle`.
