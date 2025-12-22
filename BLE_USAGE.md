# BLE (Bluetooth Low Energy) Connection Guide

## Overview
PWDongle supports BLE connectivity for smartphone control using the Nordic UART Service (NUS) protocol. **BLE mode is now the default boot behavior** for maximum convenience.

## Activation

### Automatic (Default)
1. Power on the device
2. Wait for the 3-second countdown to complete (displays "Starting Bluetooth" with countdown)
3. Device automatically enters Bluetooth mode and displays "BLE ACTIVE"
4. Device advertises as: **PWDongle**

### Manual Selection (Boot Menu)
1. Power on the device
2. **Press BOOT button during the countdown**
3. Boot menu appears with 5 options:
   - **Bluetooth (BLE)** - Smartphone control
   - Terminal (CDC) - Serial configuration
   - Password Mode - PIN entry for password menu
   - Storage Mode - Browse SD card files
   - Macro / Text - Browse SD card macro files
4. Short press to scroll, long press to select **Bluetooth (BLE)**

### Legacy Code Method
- Enter code **0000** in Password Mode to force Bluetooth on next boot

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

### System Commands

- **HELP** - Show available commands
- **ABOUT** - Display firmware info
- **PWUPDATE** - Update stored passwords (requires auth)
- **RETRIEVEPW** - Get stored passwords (requires auth)
- **CHANGELOGIN** - Change 4-digit login code

### Macro Input (NEW!)

BLE now supports **full macro syntax** - identical to SD text files. Any text you send is typed via USB HID with embedded token support:

**Macro Tokens:**
- `{{DELAY:ms}}` - Pause execution
- `{{SPEED:ms}}` - Change typing speed (0-200ms per character)
- `{{KEY:name}}` - Press special keys (enter, tab, f5, etc.)
- `{{KEY:combo}}` - Multi-modifier combinations (ctrl+shift+esc, win+r)
- `{{TEXT:...}}` - Literal text (bypass special chars)
- `{{MOUSE:MOVE dx dy}}` - Move mouse cursor
- `{{MOUSE:CLICK left|right|middle}}` - Mouse clicks
- `{{MOUSE:SCROLL n}}` - Mouse wheel (positive=up, negative=down)
- `{{GAMEPAD:PRESS|RELEASE button}}` - Gamepad buttons (a, b, x, y, lb, rb, etc.)
- `{{GAMEPAD:DPAD direction}}` - D-pad (up, down, left, right)
- `{{GAMEPAD:LS|RS x y}}` - Analog sticks (-127 to 127)
- `{{GAMEPAD:LT|RT value}}` - Triggers (-127 to 127)
- `{{AUDIO:VOLUP|VOLDOWN}}` - Volume control
- `{{AUDIO:MUTE|PLAY|STOP|NEXT|PREV}}` - Media keys

**Plain text without tokens is typed directly** - no command prefix needed!

See the main `README.md` for complete syntax and examples. Sample files in `samples/` show all features.

**Tip: SD Text Typing**
- Type files from microSD without Bluetooth: Select **Storage Mode** or **Macro / Text** from boot menu
- Scrollable menu shows up to 15 `.txt` files
- Short press to scroll, long press to type selected file over USB HID

### Example Session
```
> HELP
< OK: Commands:
<   PWUPDATE - update passwords (requires login auth)
<   RETRIEVEPW - retrieve stored passwords (requires login auth)
<   CHANGELOGIN - change the 4-digit login code
< Macro syntax: {{KEY:name}}, {{DELAY:ms}}, {{MOUSE:...}}, {{GAMEPAD:...}}, {{AUDIO:...}}
< Any text without command prefix is typed via USB HID

> Hello World{{KEY:enter}}
< OK: Processed

> {{KEY:win+r}}{{DELAY:200}}notepad{{KEY:enter}}
< OK: Processed

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

## Switching to Other Modes

### To Password Menu
1. Power cycle the device (disconnect USB)
2. **Press BOOT button during the 3-second countdown**
3. Select **Password Mode** from boot menu
4. Enter login code (1122) to access password menu

### To Terminal (CDC) Mode
**Option 1: Boot Menu**
1. Power cycle and press BOOT during countdown
2. Select **Terminal (CDC)** from boot menu
3. Long press to confirm

**Option 2: Legacy Code**
1. Access Password Mode from boot menu
2. Enter CDC mode code (7273)
3. Device reboots to USB serial configuration mode

### To Storage/Macro Mode
1. Power cycle and press BOOT during countdown
2. Select **Storage Mode** or **Macro / Text**
3. Long press to confirm
4. Browse and type SD card files

### Back to Bluetooth Mode
- Power cycle the device and let countdown complete (default behavior)
- Or press BOOT during countdown, select **Bluetooth (BLE)** from menu

## Troubleshooting

**Can't find "PWDongle" in scan**
- Ensure countdown completed without button press (Bluetooth is default)
- Or manually select **Bluetooth (BLE)** from boot menu
- Check device shows "BLE ACTIVE" on screen
- Some phones scan slowly - wait 10-15 seconds
- If you pressed BOOT during countdown, you need to select Bluetooth from menu

**Connection drops frequently**
- Move phone closer to device
- Reduce interference from other 2.4GHz devices
- Some phones have aggressive BLE power saving

**Commands not working**
- Ensure line terminator is set to newline (\n)
- Check spelling (commands are case-insensitive)
- Wait for "OK:" or "ERR:" response before next command
