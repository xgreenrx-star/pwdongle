# PWDongle Python/Termux Recorder

Python-based proof-of-concept for recording keyboard and mouse input and transmitting to PWDongle via Bluetooth LE.

## Features

- Bluetooth LE scanning and connection
- Real-time keyboard and mouse capture
- Automatic timing and delay recording
- PWDongle macro format generation
- Works on Linux, Android (Termux), and potentially macOS/Windows

## Requirements

- Python 3.7+
- `bleak` - Bluetooth LE library
- `pynput` - Keyboard and mouse capture

## Installation

### Desktop Linux

```bash
# Install dependencies
sudo apt install python3 python3-pip bluez

# Install Python packages
pip3 install bleak pynput
```

### Android (Termux)

```bash
# Update packages
pkg update && pkg upgrade

# Install Python
pkg install python

# Install dependencies
pip install bleak pynput

# Grant Termux permissions
# Settings -> Apps -> Termux -> Permissions
# Enable: Location, Nearby devices (Bluetooth)
```

### macOS

```bash
# Install Python if needed
brew install python3

# Install packages
pip3 install bleak pynput
```

### Windows

```bash
# Install Python from python.org

# Install packages
pip install bleak pynput
```

## Usage

### Basic Recording

```bash
# Run the recorder
python3 pwdongle_recorder.py

# Follow prompts:
# 1. Select PWDongle device from scan results
# 2. Enter filename for macro
# 3. Position mouse at top-left corner
# 4. Press ENTER to start recording
# 5. Perform actions
# 6. Press ESC to stop recording
```

### Output

The script generates a `.txt` file in PWDongle macro format:

```
// Recorded macro: test_macro
// Date: 2026-01-01 12:34:56
// Duration: 5.23s
// Events: 42

{{MOUSE:RESET}}
{{DELAY:100}}

{{MOUSE:MOVE:100,200}}
{{DELAY:150}}
{{MOUSE:CLICK:left}}
{{DELAY:200}}
{{KEY:tab}}
{{KEY:enter}}
```

## Troubleshooting

### Bluetooth Issues (Linux)

```bash
# Check Bluetooth status
bluetoothctl power on
bluetoothctl scan on

# If permission errors
sudo usermod -a -G bluetooth $USER
```

### Termux Permissions (Android)

1. Open Android Settings
2. Apps → Termux → Permissions
3. Enable:
   - Location (required for BLE scanning)
   - Nearby devices / Bluetooth (Android 12+)

### Mouse/Keyboard Capture (Linux)

May require X11 or Wayland access. Run from terminal emulator with proper display access.

### macOS Security

System Preferences → Security & Privacy → Privacy:
- Input Monitoring: Allow Terminal/Python
- Bluetooth: Allow Python

## Advanced Usage

### Custom Delay Threshold

Edit `DELAY_THRESHOLD_MS` in script (default: 50ms):

```python
DELAY_THRESHOLD_MS = 100  # Larger threshold = fewer delay commands
```

### Testing Commands

```bash
# Send individual commands
python3 -c "
import asyncio
from pwdongle_recorder import PWDongleRecorder

async def test():
    r = PWDongleRecorder()
    await r.connect('YOUR_DEVICE_ADDRESS')
    await r.send_command('HELP')
    await asyncio.sleep(2)
    await r.disconnect()

asyncio.run(test())
"
```

## Limitations

- Mouse position tracking depends on OS cursor API
- Some special keys may not map correctly
- BLE latency may affect timing accuracy
- Requires active display/desktop session for input capture

## Integration with PWDongle

This script demonstrates the recording protocol. The generated macro files can be:
1. Manually copied to PWDongle SD card
2. Uploaded via future file transfer feature
3. Used as reference for Android app implementation

## License

Same as PWDongle project
