# Quick Start: Auto-Connect Feature

## For Users

### Enable Auto-Connect (Default)
- When you open the app, it automatically searches for PWDongle
- If found, it connects automatically
- You can start recording right away!

### Disable Auto-Connect
1. Open the **Settings** tab (gear icon in bottom navigation)
2. Scroll up to find **"Auto-Connect to PWDongle"**
3. Toggle the switch OFF
4. Now device selection will appear

### Manually Select a Device
1. Go to **Settings** tab
2. Tap **"Scan for Devices"** button
3. Wait for scan to complete (10 seconds)
4. Tap a device from the list (e.g., "PWDongle-1234")
5. Go back to **Recorder** tab
6. The device selector will show at the top
7. Select your device from the dropdown
8. Tap **"Connect"** button
9. Wait for "Connected" status
10. Start recording!

## For Developers

### How Auto-Connect Works

```kotlin
// In RecorderFragment.kt
override fun onViewCreated(...) {
    // Observe the auto-connect preference
    preferencesManager.autoConnectEnabledFlow.collect { autoConnectEnabled ->
        if (autoConnectEnabled) {
            // Hide device selection, start auto-search
            deviceSelector.visibility = View.GONE
            autoConnectToPWDongle()
        } else {
            // Show device selection, wait for user input
            deviceSelector.visibility = View.VISIBLE
        }
    }
}

// Auto-connect function searches for PWDongle
private fun autoConnectToPWDongle() {
    bleManager?.scanForDevices { devices ->
        // Find device with "PWDongle" in name
        val pwdongleDevice = devices.find { 
            it.contains("PWDongle", ignoreCase = true) 
        }
        
        if (pwdongleDevice != null) {
            // Auto-connect to PWDongle
            bleManager?.connectToDevice(pwdongleDevice) { ... }
        } else {
            // Fallback: show available devices for manual selection
            showDeviceSelection(devices)
        }
    }
}
```

### File Locations

| File | Purpose |
|------|---------|
| `PreferencesManager.kt` | Manages preferences (new) |
| `RecorderFragment.kt` | Auto-connect logic (modified) |
| `SettingsFragment.kt` | Settings UI (modified) |
| `fragment_recorder.xml` | Recorder UI with device selector (modified) |
| `fragment_settings.xml` | Settings UI with toggle (modified) |

### Customize Device Name Matching

In `RecorderFragment.autoConnectToPWDongle()`:

```kotlin
// Default: matches "PWDongle"
val pwdongleDevice = devices.find { 
    it.contains("PWDongle", ignoreCase = true) 
}

// Example: match "MyDevice"
val pwdongleDevice = devices.find { 
    it.contains("MyDevice", ignoreCase = true) 
}

// Example: exact match
val pwdongleDevice = devices.find { it == "PWDongle_Device_1" }

// Example: multiple names
val pwdongleDevice = devices.find { device ->
    device.contains("PWDongle", ignoreCase = true) || 
    device.contains("ESP32", ignoreCase = true)
}
```

### Change Default Preference

In `PreferencesManager.kt`:

```kotlin
// Make auto-connect disabled by default
val autoConnectEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
    preferences[AUTO_CONNECT_ENABLED] ?: false  // Changed from true
}
```

## Testing Checklist

- [ ] App auto-connects when PWDongle is on
- [ ] Device selection appears if PWDongle not found
- [ ] Auto-connect toggle works in Settings
- [ ] Preference persists after app restart
- [ ] Manual connection works when auto-connect disabled
- [ ] Devices are saved in device list
- [ ] Status messages update correctly
- [ ] Recording works after connection
- [ ] Disconnection is handled gracefully

## Troubleshooting

### App doesn't find PWDongle
- [ ] Is PWDongle powered on?
- [ ] Is Bluetooth enabled on phone?
- [ ] Check app has Bluetooth permissions
- [ ] Try manual scan in Settings

### Can't toggle auto-connect setting
- [ ] Refresh Settings page
- [ ] Check for app crashes in logs
- [ ] Ensure storage permissions granted

### Manual connection fails
- [ ] Try toggling Bluetooth on/off
- [ ] Rescan for devices
- [ ] Try different device
- [ ] Check PWDongle is in pairing mode

## Key Classes

### PreferencesManager
```kotlin
// Initialize
val preferencesManager = PreferencesManager(context)

// Read preference (reactive)
preferencesManager.autoConnectEnabledFlow.collect { enabled ->
    // enabled is a Boolean
}

// Update preference (suspend function)
preferencesManager.setAutoConnectEnabled(true)
preferencesManager.setLastConnectedDevice("PWDongle-1234")
```

### RecorderFragment Methods

| Method | Purpose |
|--------|---------|
| `autoConnectToPWDongle()` | Auto-search and connect |
| `showDeviceSelection()` | Show fallback device list |
| Various UI methods | Handle recording logic |

## Performance Notes

- BLE scan takes ~10 seconds (timeout)
- Auto-connect runs on coroutine (non-blocking)
- Preferences stored efficiently with DataStore
- No impact on recording performance

## Next Steps

1. Build and test the app
2. Verify auto-connect works with your PWDongle
3. Test toggle in Settings
4. Try manual selection fallback
5. Check status messages are clear
6. Review logs for any errors

## Support

For issues or questions:
1. Check `AUTO_CONNECT_FEATURE.md` for detailed documentation
2. Review the test cases in that document
3. Check Android logs for errors
4. Verify Bluetooth permissions in app settings
