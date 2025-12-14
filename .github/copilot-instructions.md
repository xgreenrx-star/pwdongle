# PWDongle AI Agent Instructions

## Project Overview
PWDongle is an ESP32-S3 firmware project that implements a secure hardware password manager. It operates as a dual-mode USB device with a 3.3" TFT display, providing both:
- **HID Mode**: Types passwords via USB keyboard emulation
- **CDC Mode**: Serial communication for configuration

The device uses hardware authentication (4-digit PIN entry via physical button) before granting access to stored credentials.

## Architecture & Data Flow

### Hardware Components
- **ESP32-S3-DevKitM-1**: Main microcontroller running Arduino framework
- **TFT_eSPI**: 3.3" color display for UI (via SPI)
- **Boot button (GPIO 0)**: Multi-function input - increments digits (short press), confirms entry (long press)
- **USB**: Dual-mode (HID keyboard emulation or serial CDC)

### Boot Sequence
1. Initialize TFT, NVS (non-volatile storage)
2. Check `bootToCDC` flag in Preferences namespace `"CDC"`
3. If true: Enter CDC mode, display green "Ready to connect" screen, wait for serial configuration
4. If false: Show instructions, present 4-digit PIN entry interface
5. After PIN accepted: Load credentials from NVS namespace `"devstore"`, display password menu

### Three Critical Code Sections

**PIN Entry State Machine** (`readButton()`, `checkCode()`)
- Uses button hold detection (debounce: 50ms, threshold: 600ms)
- Short press increments digit 0-9 (cycles), long press confirms
- Two access codes: `correctCode[]` (device access) and `comModeCode[]` (reboot to CDC)
- Wrong code triggers red "WRONG CODE!" screen and resets input

**USB Mode Switching** (`startUSBMode()`)
- HID mode: Sets manufacturerName, serialNumber, productName, initializes `Keyboard` object
- CDC mode: Initializes `Serial` with 115200 baud and 1024-byte RX buffer
- Mode persists until physically switched OR CDC mode triggers reboot via `comModeCode`

**Data Persistence** (NVS Preferences)
- Namespace `"devstore"`: Stores up to 3 device name-password pairs
  - Keys: `device_0`, `password_0`, `device_1`, `password_1`, etc.
  - Count key: `count` (tracks number of stored pairs)
- Namespace `"CDC"`: Boot mode flag (`bootToCDC`)
- CDC receives CSV-formatted data: `name1,password1,name2,password2,...`

### Menu & Password Sending Flow
1. After PIN accepted: `loadPasswords()` retrieves credentials from NVS
2. Menu displays 3 items (`MENU_ITEM_COUNT`), selected via button presses
3. Holding button on selected item calls `sendPassword()`:
   - Switches to HID mode (USB keyboard)
   - Calls `Keyboard.println(password)` (types password + Enter)
   - Displays green confirmation, returns to menu

## Developer Workflows

### Build & Upload
```bash
# Build firmware
platformio run

# Upload to ESP32-S3-DevKitM-1
platformio run --target upload

# Serial monitor (debugging)
platformio device monitor --baud 115200
```

### Debugging Workflows
- **TFT display messages**: Primary debugging output (see `tft.println()`)
- **Serial output**: Available in CDC mode or via debug serial
- **NVS inspection**: Use `prefs.begin()`, iterate with `prefs.keys()` (not in current code)
- **Code structure**: All logic in single `main.cpp` file—search for function names

### Testing
Test directory exists but appears empty. Manual testing required:
1. Build and upload via `platformio run --target upload`
2. Connect to device, observe TFT display for PIN entry
3. Enter correct 4-digit code to reach menu
4. Connect USB cable to verify HID keyboard output
5. Use CDC reboot code to test mode switching

## Key Conventions & Patterns

### Global State Management
- **Mode state**: Implicit—controlled via `startUSBMode()` and checked at boot via Preferences flag
- **UI state**: `codeAccepted` boolean gates access; `selectedItem` tracks menu position
- **Input state**: `digitIndex`, `currentDigit`, `enteredCode[]` persist across button presses
- **Display orientation**: Portrait (rotation 0) for main menu, landscape (rotation 1) for instructions

### Button Input Pattern
```cpp
// Button state is stateful across loop iterations
// Uses debouncing + hold threshold detection
// Pattern: check state change → debounce → dispatch (short press vs long press)
readButton();  // Handles PIN entry logic
// OR
scrollMenu(); // For password menu (different button handler in loop())
```

### Color Coding Convention
- **TFT_BLACK**: Background
- **TFT_CYAN**: Section titles
- **TFT_WHITE**: Normal menu text
- **TFT_YELLOW**: Instructions/hints
- **TFT_GREEN**: Success (connection ready, message sent)
- **TFT_RED**: Error (wrong code)

### NVS Key Naming
- Device keys: `device_<index>` (0-2)
- Password keys: `password_<index>` (0-2)
- Boolean flags: Namespace-scoped (e.g., `"CDC"` namespace for `bootToCDC`)

## Critical Integration Points

### TFT_eSPI Library
- Initialized via `tft.init()`, rotation set via `tft.setRotation()`
- Font and text size set before each output to ensure consistent display
- Screen cleared with `tft.fillScreen(TFT_COLOR)` before major state changes
- No double-buffering; timing delays ensure visual stability

### USB Keyboard/Serial
- `Keyboard.println()`: Types string and presses Enter (HID mode only)
- `Serial.readStringUntil('\n')`: Reads CSV-formatted credential data (CDC mode)
- USB mode MUST be set via `startUSBMode()` before any HID/Serial operations
- Mode cannot be switched mid-operation; requires reboot for CDC mode

### Arduino Framework Specifics
- `millis()`: Used for button debouncing and hold threshold timing
- `digitalWrite()`, `digitalRead()`: Standard GPIO operations
- `ESP.restart()`: Hard restart (used when switching to CDC mode)
- `nvs_flash.h`, `Preferences.h`: NVS storage abstraction

## Common Modification Points

1. **Adding/removing passwords**: Modify `MENU_ITEM_COUNT` (currently 3) and array sizes
2. **Changing PIN codes**: Update `correctCode[]` and `comModeCode[]` arrays
3. **Adjusting display text**: Search for `tft.println()` calls; text size set before each output
4. **Button timing**: Adjust `DEBOUNCE_DELAY` (50ms) or `HOLD_THRESHOLD` (600ms)
5. **CSV parsing**: Modify `parseAndStoreData()` to change delimiter or data format

## Refactoring for Multi-File Architecture

If expanding beyond current scope, consider splitting `main.cpp` into logical modules:

**Suggested module breakdown:**
- `src/display.cpp` / `include/display.h`: All TFT operations (`showInstructions()`, `drawMenu()`, `showDigitScreen()`)
- `src/input.cpp` / `include/input.h`: Button handling (`readButton()`, `scrollMenu()`, `incrementDigit()`)
- `src/security.cpp` / `include/security.h`: PIN logic (`checkCode()`, access code validation)
- `src/storage.cpp` / `include/storage.h`: NVS operations (`storeDeviceData()`, `loadPasswords()`, `parseAndStoreData()`)
- `src/usb.cpp` / `include/usb.h`: USB mode switching (`startUSBMode()`, `sendPassword()`)
- `src/main.cpp`: Minimal—only `setup()` and `loop()` that delegate to modules

**Key pattern for modular refactoring:**
- Keep global state (`tft`, `prefs`, button timings) in `main.cpp` or central header
- Each module owns its function implementations but calls back to shared globals
- Maintain current file-static variables pattern—don't expose module state if not needed
- USB mode and NVS namespace strings should be `#define` in a central config header

This preserves single-threaded simplicity while improving maintainability.

## Dependency Notes
- **TFT_eSPI v2.5.43**: Pinout configured via project settings (SPI pins vary by board)
- **Arduino core for ESP32**: Provides USB HID/CDC stack
- No external dependencies beyond `platformio.ini` declaration
