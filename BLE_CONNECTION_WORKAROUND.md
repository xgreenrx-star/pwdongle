# PWDongle BLE Connection Workaround

## Issue

PWDongle device not appearing in Android's BLE device discovery, even though it is bonded on the phone and advertising.

**Root Cause**: The phone's Bluetooth database has a corrupted bond record for PWDongle (`bond_type:BOND_TYPE_UNKNOWN`), which prevents it from being returned by Android's `bluetoothAdapter.bondedDevices` API, even though the device exists in the system database.

## Solution

### Option 1: Manual MAC Address Connection (RECOMMENDED)

The android-recorder app now supports connecting directly by MAC address when the device isn't discoverable via standard scan APIs.

**Steps**:
1. Launch the android-recorder app
2. Wait for auto-scan to fail (if PWDongle not found)
3. The app will show "PWDongle not auto-detected. Select device below"
4. In the device selector dropdown, choose **"Enter MAC address manually..."**
5. Enter PWDongle's MAC address: `xx:xx:xx:xx:f2:e9` (or whatever your PWDongle's address is)
6. Click "Connect"
7. App should connect successfully

**Finding PWDongle's MAC Address**:
- Check the device itself (may be printed on label)
- Use `adb shell dumpsys bluetooth_manager` and search for "PWDongle" - it will show the MAC
- Or reconnect from Android Settings > Bluetooth if it prompts for the address

### Option 2: Remove and Re-pair PWDongle

If you want to fix the corrupted bond record:

1. On phone: Go to Settings > Bluetooth
2. Find PWDongle in paired devices
3. Remove/Unpair it completely
4. Restart Bluetooth on phone
5. Re-pair PWDongle fresh
6. Then use the app's standard auto-connect

**Note**: Steps vary by Android version. On some versions, you may need to "Forget" the device and scan for it fresh.

### Option 3: Clear Phone's Bluetooth Database (Nuclear Option)

If bonding is completely corrupted:

```bash
adb shell pm clear com.android.bluetooth
adb shell cmd bluetooth_manager disable
adb shell cmd bluetooth_manager enable
```

**Warning**: This clears ALL Bluetooth pairings. You'll need to re-pair all devices.

## Technical Details

**Why This Happens**:
- Android's Bluetooth stack stores bond records in a database
- If a bond record becomes corrupted (invalid `bond_type`), the API doesn't return it
- The system can still see the device via GATT callbacks but won't expose it via public APIs
- The app's BLE scan callback also won't find the device if it's not broadcasting

**App Changes**:
- Added `connectToDeviceByMAC()` method in BLEManager
- Added MAC address entry dialog in RecorderFragment
- Device selector now includes "Enter MAC address manually..." option
- Pre-populated with known PWDongle address format for convenience

## Testing

After connecting via MAC:
1. **Verify connection**: Status shows "Connected to PWDongle"
2. **Test command flow**: Try "Start Recording" to test BLE communication
3. **Save macro**: Confirm RECORD/STOPRECORD commands work

## Files Modified

- `app/src/main/kotlin/com/pwdongle/recorder/BLEManager.kt` - Added `connectToDeviceByMAC()`
- `app/src/main/kotlin/com/pwdongle/recorder/RecorderFragment.kt` - Added MAC entry dialog and manual connection option

## Future Improvements

Potential enhancements:
1. Store last-used MAC address in preferences for quick reconnect
2. Scan for devices by MAC periodically if standard scan fails
3. Detect corrupted bonds and offer repair flow automatically
4. Support wildcard MAC matching for variations of PWDongle devices
