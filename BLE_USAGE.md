# BLE (Bluetooth Low Energy) Connection Guide

## Overview
PWDongle now supports BLE connectivity for smartphone control using the Nordic UART Service (NUS) protocol.

## Activation
1. On the device, enter the BLE mode code: **0000**
2. Device will reboot and display "BLE Ready" with instructions
3. Device advertises as: **PWDongle**

## Smartphone Setup

### Android Apps (Recommended)
1. **Serial Bluetooth Terminal** by Kai Morich
   - Free on Google Play
   - Supports both Bluetooth Classic and BLE
   - Go to "Devices" → Connect to "PWDongle"
   - Set line terminator to "Newline (\n)"

2. **nRF Connect** by Nordic Semiconductor
   - Free on Google Play
   - Scan for "PWDongle"
   - Connect → Enable "Nordic UART Service"
   - Use TX/RX characteristics to send/receive

### iOS Apps
1. **LightBlue** by Punch Through
2. **nRF Connect** by Nordic Semiconductor
3. Any BLE UART terminal app supporting NUS (UUID: 6E400001-B5A3-F393-E0A9-E50E24DCCA9E)

## Commands

All commands are the same as CDC mode:

- **HELP** - Show available commands
- **ABOUT** - Display firmware info
- **PWUPDATE** - Update stored passwords (requires auth)
- **RETRIEVEPW** - Get stored passwords (requires auth)
- **CHANGELOGIN** - Change 4-digit login code

### Example Session
```
> HELP
< OK: Commands:
<   PWUPDATE - update passwords (requires login auth)
<   RETRIEVEPW - retrieve stored passwords (requires login auth)
<   CHANGELOGIN - change the 4-digit login code
< Usage: send command, then follow prompts from device

> PWUPDATE
< OK: Enter the login code to authorize PW update

> 1122
< OK: Authorized. Please send NAME,DATA

> Gmail,mypassword123,Github,githubpass456
< OK: Passwords updated
```

## Technical Details

### BLE Characteristics
- **Service UUID**: `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` (Nordic UART Service)
- **RX Characteristic**: `6E400002-B5A3-F393-E0A9-E50E24DCCA9E` (Write - phone to device)
- **TX Characteristic**: `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` (Notify - device to phone)

### Data Format
- Line-based protocol with newline (`\n`) terminators
- Messages chunked to ~20 bytes (BLE MTU limit)
- Automatic chunking handled by firmware

## Switching Back to Normal Mode
1. Power cycle the device (disconnect USB)
2. Device will boot to normal PIN entry mode
3. Enter normal code (1122) to access password menu
4. Or enter CDC mode code (7273) for USB serial mode

## Troubleshooting

**Can't find "PWDongle" in scan**
- Ensure BLE mode code (0000) was entered
- Check device shows "BLE Ready" on screen
- Some phones scan slowly - wait 10-15 seconds

**Connection drops frequently**
- Move phone closer to device
- Reduce interference from other 2.4GHz devices
- Some phones have aggressive BLE power saving

**Commands not working**
- Ensure line terminator is set to newline (\n)
- Check spelling (commands are case-insensitive)
- Wait for "OK:" or "ERR:" response before next command
