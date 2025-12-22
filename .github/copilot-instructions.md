# PWDongle AI Agent Instructions

## Project Overview
PWDongle is an ESP32-S3 firmware project that implements:
- **Password Manager**: Hardware-authenticated password storage with USB HID typing
- **Advanced Scripting Device**: Three script formats (Advanced, DuckyScript, Macro) with variables, loops, conditionals
- **Macro Recording**: Live recording of keyboard/mouse input from smartphone via BLE
- **Multi-Mode Boot**: Bluetooth (default), Terminal (CDC), Password, Storage, Macro/Text modes
- **3.3" TFT Display**: Visual UI with boot menu, file browser, PIN entry, recording status

The device operates as a dual/triple-mode USB device (HID keyboard/mouse/gamepad + CDC serial + MSC mass storage).

## Architecture & Data Flow

### Hardware Components
- **ESP32-S3-DevKitM-1** or **ESP32-S3-LCD-1.47**: Main microcontroller running Arduino framework
- **TFT_eSPI (ST7789)**: 172x320 SPI display for UI
- **Boot button (GPIO 0)**: Multi-function input - menu navigation (short press), selection (long press 600ms)
- **USB**: Triple-mode support (HID keyboard/mouse/gamepad, CDC serial, MSC mass storage)
- **microSD Card**: FAT32 storage for macro files and recordings
- **BLE**: Nordic UART Service for smartphone control and macro recording

### Boot Sequence (Current v0.5)
1. Initialize TFT, NVS, SD card
2. Display 3-second countdown "Starting Bluetooth in X..."
3. **If BOOT pressed during countdown**: Show boot menu with 5 options
   - Bluetooth (BLE) - Default, smartphone control with keystroke relay
   - Terminal (CDC) - Serial configuration mode
   - Password Mode - PIN entry for password menu (legacy)
   - Storage Mode - Browse and type SD card files
   - Macro / Text - Browse and type macro files (identical to Storage)
4. **If countdown completes**: Automatically enter Bluetooth (BLE) mode
5. **Boot menu navigation**: Short press to scroll, long press (600ms) to select

### Modular Code Architecture

The project uses a clean module-based architecture (refactored from original single-file design):

**Core Modules:**
- `src/main.cpp` - Setup, main loop, mode orchestration
- `src/display.cpp` / `include/display.h` - All TFT UI functions (boot menu, file browser, PIN screens, recording screens)
- `src/input.cpp` / `include/input.h` - Button handling with debouncing (50ms) and hold detection (600ms)
- `src/security.cpp` / `include/security.h` - PIN validation, access codes (1122, 7273, 0000, 0001)
- `src/storage.cpp` / `include/storage.h` - NVS password storage operations
- `src/usb.cpp` / `include/usb.h` - USB HID/CDC/MSC modes, macro processing, format detection
- `src/bluetooth.cpp` / `include/bluetooth.h` - BLE UART, keystroke relay, **macro recording** (NEW v0.5)
- `src/duckyscript.cpp` / `include/duckyscript.h` - RubberDucky script parser (v0.3.1)
- `src/scriptengine.cpp` / `include/scriptengine.h` - Advanced scripting with variables/loops/conditionals (v0.4)

### Macro Recording System (NEW v0.5)

### Macro Recording System (NEW v0.5)

**Components:**
- `bluetooth.cpp`: Recording state management, file I/O, timing capture
  - `startMacroRecording(filename)` - Opens SD file, writes header, initializes timing
  - `stopMacroRecording()` - Closes file, displays duration, resets state
  - `recordAction(action)` - Calculates delays (>50ms threshold), writes {{DELAY:ms}} + action
  - State: `isRecording`, `recordingFilename`, `recordingFile`, `recordingStartTime`, `lastActionTime`
- `usb.cpp`: BLE command routing for recording
  - `RECORD:filename` - Starts recording session
  - `STOPRECORD` - Ends recording session
  - `KEY:`, `MOUSE:`, `TYPE:`, `GAMEPAD:` - Commands intercepted during recording
- `display.cpp`: Recording UI
  - `showRecordingScreen(filename)` - Shows "RECORDING" screen with filename and command hints
  - `showRecordingStopped(filename, duration)` - Shows completion screen

**Recording Workflow:**
1. User sends `RECORD:filename` via BLE → `startMacroRecording()` opens `/filename.txt`
2. User sends actions (`KEY:enter`, `TYPE:text`, etc.) → `recordAction()` writes to file with automatic delays
3. User sends `STOPRECORD` → `stopMacroRecording()` closes file, shows duration
4. File saved in PWDongle macro format on SD card, ready for playback

**File Format:** PWDongle macro syntax with automatic `{{DELAY:ms}}` timing between actions (50ms threshold).

### Script Format Detection & Processing

**Three-tier format detection** (priority order):
1. **Advanced Scripting** - Detected: `VAR`, `IF`, `LOOP`, `FOR`, `wait(`, `set_val(`
   - Variables: Integer/string with arithmetic expressions
   - Conditionals: IF/ELSE/ENDIF with comparison/logical operators
   - Loops: LOOP/ENDLOOP, FOR/NEXT with nesting
   - GPC syntax: `wait(ms)`, `set_val(button, value)`
   - Engine: `scriptengine.cpp` (500+ lines)

2. **DuckyScript** - Detected: `REM`, `DELAY`, `STRING`, `GUI`, `CTRL`, `ALT`
   - RubberDucky/BadUSB compatible
   - Commands: STRING, STRINGLN, DELAY, key names, modifiers
   - Parser: `duckyscript.cpp` (240+ lines)

3. **PWDongle Macro** - Default format
   - `{{TOKEN[:ARGS]}}` syntax
   - Tokens: DELAY, SPEED, KEY, TEXT, MOUSE, GAMEPAD, AUDIO
   - Processor: `usb.cpp` macro parser

**Auto-detection:** `processTextFileAuto()` in `usb.cpp` scans file for format keywords, selects appropriate engine.

### Critical Code Sections

**Boot Menu** (`display.cpp`: `showBootMenu()`, `input.cpp`: button state machine)
- 5 options displayed with scroll indicator
- Short press increments selection, long press (600ms) confirms
- Delegates to mode-specific handlers in `main.cpp`

**PIN Entry State Machine** (`input.cpp`: `readButton()`, `security.cpp`: `checkCode()`)
- Uses button hold detection (debounce: 50ms, threshold: 600ms)
- Short press increments digit 0-9 (cycles), long press confirms
- Two access codes: `correctCode[]` (device access) and `comModeCode[]` (reboot to CDC)
- Wrong code triggers red "WRONG CODE!" screen and resets input

**USB Mode Switching** (`usb.cpp`: `startUSBMode()`)
- HID mode: Sets manufacturerName, serialNumber, productName ("PWDongle v0.5 HID"), initializes `Keyboard`, `Mouse`, `Gamepad`
- CDC mode: Initializes `Serial` with 115200 baud and 1024-byte RX buffer ("PWDongle v0.5 CDC")
- MSC mode: Mounts SD card as USB mass storage
- Mode persists until physically switched OR CDC mode triggers reboot via access code `7273`

**BLE Command Processing** (`usb.cpp`: `processBLELine()`)
- System commands: HELP, ABOUT, PWUPDATE, RETRIEVEPW, CHANGELOGIN
- Recording commands: RECORD:filename, STOPRECORD
- Macro commands: KEY:, MOUSE:, TYPE:, GAMEPAD:, AUDIO:
- **Recording mode**: When `isRecording==true`, all macro commands route to `recordAction()` instead of execution
- Response protocol: `sendBLEResponse()` sends "OK:" or "ERROR:" messages back to smartphone

**SD Card File Browser** (`usb.cpp`: `showFileMenu()`, `display.cpp`: menu rendering)
- Scans SD root for `.txt` files (up to 15 files)
- Displays 9 files per screen with scroll indicator
- File selection triggers `processTextFileAuto()` for format detection and execution
- Returns to menu after each file execution for quick re-run
- HID mode: Sets manufacturerName, serialNumber, productName, initializes `Keyboard` object
- CDC mode: Initializes `Serial` with 115200 baud and 1024-byte RX buffer
- Mode persists until physically switched OR CDC mode triggers reboot via `comModeCode`

**Data Persistence** (NVS Preferences)
- Namespace `"devstore"`: Stores up to 10 device name-password pairs (increased from 3)
  - Keys: `device_0`, `password_0`, `device_1`, `password_1`, etc.
  - Count key: `count` (tracks number of stored pairs)
- Namespace `"CDC"`: Boot mode flag (`bootToCDC`)
- Namespace `"BLE"`: Bluetooth boot flag
- Namespace `"SEC"`: Persisted login code
- CDC receives CSV-formatted data: `name1,password1,name2,password2,...`

### Menu & Password Sending Flow
1. After PIN accepted: `loadPasswords()` retrieves credentials from NVS
2. Menu displays up to 10 items (expanded in v0.5), selected via button presses
3. Holding button on selected item calls `sendPassword()`:
   - Switches to HID mode (USB keyboard)
   - Calls `Keyboard.println(password)` (types password + Enter)
   - Displays green confirmation, returns to menu

## Developer Workflows

### Build & Upload
```bash
# Build firmware
platformio run

# Upload to ESP32-S3-DevKitM-1 or ESP32-S3-LCD-1.47
platformio run --target upload

# Serial monitor (debugging)
platformio device monitor --baud 115200
```

### Debugging Workflows
- **TFT display messages**: Primary debugging output (see `tft.println()`)
- **Serial output**: Available in CDC mode or via debug serial
- **NVS inspection**: Use `prefs.begin()`, iterate with keys
- **Code structure**: Modular with separate files for each subsystem—see module list above

### Testing
Manual testing workflow:
1. Build and upload via `platformio run --target upload`
2. Observe 3-second countdown on TFT display
3. Test boot menu: Press BOOT during countdown, navigate with short/long press
4. Test BLE mode: Let countdown complete, connect smartphone via BLE UART app
5. Test macro recording: Send `RECORD:test`, `KEY:enter`, `STOPRECORD`, verify file on SD
6. Test file browser: Boot to Storage/Macro mode, scroll files, execute with long press
7. Test script formats: Create `.txt` files with Advanced/DuckyScript/Macro syntax, verify auto-detection

## Key Conventions & Patterns

### Global State Management
- **Mode state**: Controlled via boot menu selection or countdown completion
- **UI state**: Multiple states - boot menu, file browser, PIN entry, recording, password menu
- **Input state**: `digitIndex`, `currentDigit`, `enteredCode[]` persist across button presses
- **Display orientation**: Portrait (rotation 0) for menus, landscape (rotation 1) for instructions
- **Recording state**: `isRecording` boolean gates macro recording behavior

### Button Input Pattern
```cpp
// Button state is stateful across loop iterations
// Uses debouncing (50ms) + hold threshold detection (600ms)
// Pattern: check state change → debounce → dispatch (short press vs long press)
readButton();  // Handles PIN entry logic
// OR
scrollMenu(); // For password menu or file browser (different button handler in loop())
```

### Color Coding Convention
- **TFT_BLACK**: Background
- **TFT_CYAN**: Section titles
- **TFT_WHITE**: Normal menu text
- **TFT_YELLOW**: Instructions/hints
- **TFT_GREEN**: Success (connection ready, message sent)
- **TFT_RED**: Error (wrong code)

### NVS Key Naming
- Device keys: `device_<index>` (0-9, increased from 0-2)
- Password keys: `password_<index>` (0-9)
- Boolean flags: Namespace-scoped (e.g., `"CDC"` namespace for `bootToCDC`)
- Count tracking: `count` key in `"devstore"` namespace

## Critical Integration Points

### TFT_eSPI Library
- Initialized via `tft.init()`, rotation set via `tft.setRotation()`
- Font and text size set before each output to ensure consistent display
- Screen cleared with `tft.fillScreen(TFT_COLOR)` before major state changes
- No double-buffering; timing delays ensure visual stability

### USB HID/Serial/Mass Storage
- **HID Mode**: `Keyboard.println()`, `Mouse.move()`, `Gamepad.press()` - Types/controls via USB
- **CDC Mode**: `Serial.readStringUntil('\n')` - Reads CSV-formatted credential data
- **MSC Mode**: SD card mounted as USB flash drive for file management
- USB mode MUST be set via `startUSBMode()` before any HID/Serial operations
- Mode cannot be switched mid-operation; requires reboot for CDC mode

### BLE (Bluetooth Low Energy)
- **Nordic UART Service (NUS)**: UUID 6E400001-B5A3-F393-E0A9-E50E24DCCA9E
- **RX Characteristic**: Receives commands from smartphone
- **TX Characteristic**: Sends responses back to smartphone
- Line-based protocol with `\n` terminators, 20-byte MTU chunking
- Keystroke relay: Commands from phone typed on PC via USB HID
- Macro recording: Commands saved to SD card with automatic timing

### Arduino Framework Specifics
- `millis()`: Used for button debouncing and hold threshold timing
- `digitalWrite()`, `digitalRead()`: Standard GPIO operations
- `ESP.restart()`: Hard restart (used when switching to CDC mode)
- `nvs_flash.h`, `Preferences.h`: NVS storage abstraction

## Common Modification Points

1. **Adding/removing passwords**: Modify `MENU_ITEM_COUNT` (currently 10) and array sizes in `storage.cpp`
2. **Changing PIN codes**: Update `correctCode[]` and `comModeCode[]` arrays in `security.cpp`
3. **Adjusting display text**: Search for `tft.println()` calls in `display.cpp`; text size set before each output
4. **Button timing**: Adjust `DEBOUNCE_DELAY` (50ms) or `HOLD_THRESHOLD` (600ms) in `input.cpp`
5. **CSV parsing**: Modify `parseAndStoreData()` in `storage.cpp` to change delimiter or data format
6. **Script format keywords**: Update detection logic in `usb.cpp`: `processTextFileAuto()`
7. **Recording timing threshold**: Modify 50ms delay threshold in `bluetooth.cpp`: `recordAction()`
8. **File browser capacity**: Change max file count (15) in `usb.cpp`: `showFileMenu()`

## Version History & Features

### v0.5 (Current) - Macro Recording
- **Macro Recording**: Live keyboard/mouse capture from smartphone OTG to SD card
- **Recording Commands**: RECORD:filename, STOPRECORD, automatic delay capture (>50ms)
- **Recording UI**: "RECORDING" screen with filename, "RECORDING COMPLETE" with duration
- **File Format**: PWDongle macro with automatic {{DELAY:ms}} timing
- Flash: 1.13MB (33.7%), RAM: 58KB (17.9%)

### v0.4 - Advanced Scripting
- **Variables**: Integer/string with arithmetic expressions
- **Conditionals**: IF/ELSE/ENDIF with comparison/logical operators
- **Loops**: LOOP/ENDLOOP, FOR/NEXT with nesting
- **GPC Syntax**: wait(), set_val() for gaming automation
- **Script Engine**: 500+ line parser and executor
- Flash: 1.12MB (33.5%), RAM: 58KB (17.8%)

### v0.3.1 - DuckyScript Support
- **RubberDucky Compatibility**: STRING, DELAY, REM, key combinations
- **Auto-Detection**: Scans for DuckyScript vs Macro format keywords
- **Parser**: 240+ line DuckyScript processor
- Flash: 1.11MB (33.2%)

### v0.3 - Boot Menu & File Browser
- **Boot Menu**: 5 options (Bluetooth, Terminal, Password, Storage, Macro/Text)
- **File Browser**: Scrollable SD card `.txt` file selection (9 visible, 15 max)
- **Auto-Boot**: 3-second countdown to Bluetooth (default)
- Flash: 1.1MB (33.1%)

## Refactoring Notes

**Current Architecture (v0.5):**
The project has been fully refactored into a modular architecture:

**Current Architecture (v0.5):**
The project has been fully refactored into a modular architecture:

**Implemented modules:**
- `src/display.cpp` / `include/display.h`: All TFT operations (boot menu, file browser, PIN entry, recording screens)
- `src/input.cpp` / `include/input.h`: Button handling (debouncing, hold detection, menu scrolling)
- `src/security.cpp` / `include/security.h`: PIN validation, access code logic
- `src/storage.cpp` / `include/storage.h`: NVS operations (password storage, CSV parsing)
- `src/usb.cpp` / `include/usb.h`: USB HID/CDC/MSC modes, macro processing, format auto-detection
- `src/bluetooth.cpp` / `include/bluetooth.h`: BLE UART, keystroke relay, macro recording
- `src/duckyscript.cpp` / `include/duckyscript.h`: RubberDucky script parser
- `src/scriptengine.cpp` / `include/scriptengine.h`: Advanced scripting engine (variables/loops/conditionals)
- `src/main.cpp`: Minimal orchestration—`setup()` and `loop()` delegate to modules

**Key patterns maintained:**
- Global state (`tft`, `prefs`, button timings) accessible via extern declarations
- Each module owns its function implementations
- Single-threaded execution model preserved for simplicity
- USB mode and NVS namespace strings centralized in module headers

**Shared variables:**
- `sdUseMMC` (bool): SD card mode flag (SPI vs MMC) - declared in `usb.cpp`, extern in `bluetooth.cpp`
- `isRecording` (bool): Recording state flag - declared in `bluetooth.cpp`, checked in `usb.cpp`

This architecture provides excellent maintainability while preserving the single-threaded simplicity of the original design.

## Dependency Notes
- **TFT_eSPI v2.5.43**: Pinout configured via project settings (SPI pins vary by board)
- **ESP32 BLE Arduino v2.0.0**: Bluetooth Low Energy support
- **SD v2.0.0**: SD card file operations (SPI mode)
- **SD_MMC v2.0.0**: SD card file operations (MMC mode, ESP32-S3-LCD-1.47)
- **USB v2.0.0**: HID keyboard/mouse/gamepad, CDC serial, MSC mass storage
- **Preferences v2.0.0**: NVS (non-volatile storage) abstraction
- Arduino framework for ESP32-S3 provides USB HID/CDC/MSC stack
