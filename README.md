# PWDongle

Secure ESP32-S3 hardware password manager and advanced scripting device with TFT display, PIN authentication, USB HID typing, CDC configuration, and BLE smartphone control.

## Features

### Core Features
- **Default BLE Boot** - 3-second countdown auto-boots to BLE mode for smartphone connectivity
- **4-Digit PIN Authentication** - Hardware button entry with masked digits for security
- **Password Storage** - Store up to 10 device/password pairs in non-volatile memory
- **USB HID Keyboard Mode** - Types passwords directly to connected PC
- **USB CDC Serial Mode** - Configure passwords via serial commands from PC
- **BLE UART Mode** - Control from smartphone using Nordic UART Service
- **Dual-Mode Keystroke Relay** - Send keystrokes from phone → ESP32 → PC via USB HID
- **3.3" TFT Display** - Visual UI for PIN entry and menu navigation
- **Persistent Configuration** - Login codes and passwords survive reboots

### Advanced Scripting (v0.4+)
- **Variables & Expressions** - Integer and string variables with arithmetic operations
- **Conditionals** - IF/ELSE/ENDIF logic with comparison operators
- **Loops** - LOOP/ENDLOOP and FOR/NEXT constructs with nesting support
- **Three Script Formats** - Auto-detects Advanced Scripting, DuckyScript, or Macro format
- **GPC Syntax** - Game Profile Compiler compatibility for gaming automation
- **SD Card Execution** - Run scripts from microSD with automatic format detection
- **Macro Recording** - Record live keyboard/mouse input from smartphone (OTG) to SD card

## Hardware

- **Board**: ESP32-S3-DevKitM-1 or ESP32-S3-LCD-1.47
- **Display**: ST7789 TFT (172x320, SPI)
- **Button**: Boot button (GPIO 0) for input
- **USB**: Dual-mode HID + CDC support

### Pin Configuration (ESP32-S3-LCD-1.47)
```
Display:
- MOSI: GPIO 45
- SCLK: GPIO 40
- CS:   GPIO 42
- DC:   GPIO 41
- BL:   GPIO 46
- RST:  Not connected

Button:
- GPIO 0 (Boot button)
```

## Installation

### Option 1: Flash Pre-compiled Binary (Recommended)

Download the latest release from the [releases folder](releases/):
- **v0.5** `PWDongle-v0.5-esp32s3.bin` (1.13MB) - **Latest with Macro Recording**
- v0.4 `PWDongle-v0.4-esp32s3.bin` (1.12MB) - Advanced Scripting
- v0.3.1 `PWDongle-v0.3.1-esp32s3.bin` (1.11MB) - DuckyScript support
- v0.3 `PWDongle-v0.3-esp32s3.bin` (1.1MB) - Boot menu and file browser

#### Using esptool.py (Linux/Mac/Windows)
```bash
# Install esptool
pip install esptool

# Flash to ESP32-S3 (replace /dev/ttyUSB0 with your port)
esptool.py --chip esp32s3 --port /dev/ttyUSB0 --baud 460800 write_flash 0x0 PWDongle-v0.5-esp32s3.bin
```

#### Using ESP Flash Download Tool (Windows)
1. Download [Flash Download Tools](https://www.espressif.com/en/support/download/other-tools)
2. Select ESP32-S3
3. Add binary file at address `0x0`
4. Select COM port and click START

#### Finding Your Port
- **Linux**: Usually `/dev/ttyUSB0` or `/dev/ttyACM0` (check with `ls /dev/tty*`)
- **Mac**: Usually `/dev/cu.usbserial-*` or `/dev/cu.wchusbserial*`
- **Windows**: Check Device Manager for COM port (e.g., `COM3`)

### Option 2: Build from Source

#### Prerequisites
- [PlatformIO](https://platformio.org/) installed
- ESP32-S3 board connected via USB

#### Commands
```bash
# Build firmware
platformio run

# Upload to device
platformio run --target upload

# Monitor serial output
platformio device monitor --baud 115200
```

## Usage

### Boot Behavior

On power-up, the device displays a **3-second countdown** and automatically boots to Bluetooth (BLE) mode for smartphone connectivity:

- **Default**: Let countdown complete → Bluetooth mode starts
- **Override**: Press BOOT button during countdown → **Boot Menu** appears

#### Boot Menu

When you interrupt the countdown, a menu with 5 boot options appears:

1. **Bluetooth (BLE)** - Smartphone control with keystroke relay
2. **Terminal (CDC)** - Serial configuration mode
3. **Password Mode** - Enter PIN to access password menu
4. **Storage Mode** - Browse and type files from SD card
5. **Macro / Text** - Browse and type macro files from SD card

**Navigation:**
- **Short press**: Scroll through menu items
- **Long press** (>600ms): Select highlighted option

#### Why Bluetooth is Default?
Bluetooth mode provides smartphone control with keystroke relay, making it the most versatile mode for everyday use. All other modes are instantly accessible via the boot menu.

### Access Codes (Legacy PIN Entry)

These codes are only used in **Password Mode** selected from the boot menu:

| Code | Function |
|------|----------|
| `1122` | Normal access (default login) |
| `7273` | Reboot to Terminal (CDC) mode |
| `0000` | Force Bluetooth mode on next boot |
| `0001` | Reboot to Flash Drive (MSC) mode |

**Note**: The old `5550` file mode code is deprecated. Use **Storage Mode** or **Macro / Text** from the boot menu instead.

### Password Mode
1. Power on device
2. **Press BOOT button during countdown** to access boot menu
3. Select **Password Mode** and long-press to confirm
4. Enter login code `1122` using boot button
   - Short press: increment digit (0-9)
   - Long press (>600ms): confirm digit
5. Select password from menu
6. Hold button to type password via USB HID

### Terminal Mode (USB Serial Configuration)
1. Press BOOT during countdown, select **Terminal (CDC)**, long-press to confirm
2. Connect serial terminal at 115200 baud
3. Available commands:
   - `HELP` - Show command list
   - `ABOUT` - Firmware info
   - `PWUPDATE` - Update passwords (requires auth)
   - `RETRIEVEPW` - Get stored passwords (requires auth)
   - `CHANGELOGIN` - Change 4-digit login code

**Alternative**: Enter code `7273` in Password Mode to reboot directly to Terminal mode.

Example:
```
> PWUPDATE
< OK: Enter the login code to authorize PW update
> 1122
< OK: Authorized. Please send NAME,DATA
> Gmail,mypass123,Github,token456
< OK: Passwords updated
```

### Bluetooth Mode (Smartphone Control)
1. **Default**: Let 3-second countdown complete
   - **OR**: Press BOOT during countdown, select **Bluetooth (BLE)**, long-press to confirm
   - **OR**: Enter code `0000` in Password Mode for explicit boot
2. Device shows "BLE ACTIVE" and advertises as "PWDongle"
3. Connect from smartphone using BLE UART terminal app:
   - Android: "Serial Bluetooth Terminal" or "nRF Connect"
   - iOS: "LightBlue" or "nRF Connect"
4. Available commands (same as Terminal mode, plus):
   - `TYPE:text` - Type text on connected PC
   - `KEY:enter` - Send special key (enter, tab, backspace, etc.)
   - `KEY:ctrl+c` - Send key combination

Example keystroke relay:
```
> TYPE:username
< OK: Typed to PC
> KEY:tab
< OK: Key sent to PC
> TYPE:password
< OK: Typed to PC
> KEY:enter
< OK: Key sent to PC
```

**See `BLE_USAGE.md` for complete Bluetooth documentation including macro recording feature.**

### SD Card File Typing (Storage & Macro / Text Modes)

**New**: Browse and type text files from SD card using a scrollable menu instead of entering file numbers.

#### How to Use
1. Press BOOT button during countdown to access boot menu
2. Select **Storage Mode** or **Macro / Text** (they work identically)
3. Long-press to confirm
4. Device scans SD card and displays up to 15 `.txt` files
5. **Short press** - scroll through available files
6. **Long press** - type the selected file via USB HID keyboard
7. After typing completes, menu returns for next file selection

#### File Menu Features
- Displays up to 9 files at once with scrolling
- Shows file counter (e.g., "3/12")
- Automatically truncates long filenames
- Files sorted numerically (0001.txt, 0002.txt, etc.)
- Returns to menu after each file for quick re-typing

#### File Requirements
- Files must be in SD card root directory
- Use `.txt` extension
- Recommended naming: `NNNN.txt` (4 digits) for easy sorting
- FAT32 format recommended for SD card

#### Supported Formats

PWDongle automatically detects and processes three script formats:

**1. Advanced Scripting (Variables, Loops, Conditionals)**

Full-featured scripting language with programming constructs. Automatically detected when file contains variables, loops, or conditionals.

**Variables:**
```
VAR counter = 0
VAR name = "test"
x = 10
y = x + 5
```

**Conditionals:**
```
IF x > 5
    STRING X is greater than 5{{KEY:enter}}
ELSE
    STRING X is 5 or less{{KEY:enter}}
ENDIF
```

**Loops:**
```
LOOP 5
    STRING Hello{{KEY:enter}}
    {{DELAY:200}}
ENDLOOP

FOR i = 1 TO 10
    STRING Iteration{{KEY:enter}}
NEXT i
```

**Expressions:**
- Arithmetic: `+`, `-`, `*`, `/`, `%`
- Comparisons: `==`, `!=`, `<`, `>`, `<=`, `>=`
- Logical: `&&`, `||`
- Parentheses for grouping

**GPC (Game Profile Compiler) Syntax:**
```
wait(500)              // Delay in milliseconds
set_val(XB1_A, 100)   // Set gamepad button/axis
```

**Example Advanced Script:**
```
VAR x = 10
{{KEY:win+r}}
{{DELAY:200}}
STRING notepad{{KEY:enter}}
{{DELAY:500}}

IF x > 5
    STRING X is greater!{{KEY:enter}}
ENDIF

FOR i = 1 TO 3
    STRING Loop iteration{{KEY:enter}}
    wait(300)
NEXT i
```

**2. DuckyScript (RubberDucky Compatible)**

Classic BadUSB script format. Automatically detected when file contains DuckyScript keywords.

**Supported Commands:**
- `REM comment` - Comments (ignored)
- `STRING text` - Type literal text
- `STRINGLN text` - Type text with Enter
- `DELAY ms` - Pause for milliseconds
- `ENTER`, `TAB`, `ESCAPE`, `SPACE` - Special keys
- `UP`, `DOWN`, `LEFT`, `RIGHT` - Arrow keys  
- `CTRL key`, `ALT key`, `SHIFT key`, `GUI key` - Key combinations
- `F1` through `F12` - Function keys
- `WINDOWS` / `GUI` - Windows/Command key

**Example DuckyScript:**
```
REM Open calculator on Windows
DELAY 500
GUI r
DELAY 200
STRING calc
ENTER
```

**3. PWDongle Macro Format**

Advanced macro language with `{{TOKEN}}` syntax. See [Macro Syntax](#macro-syntax) below for full reference.

### SD Text File Typing

Type the contents of text files stored on the microSD card via USB HID. Files support **macros** for delays, special keys, and more.

#### Macro Syntax

Embed control tokens directly in text files using `{{COMMAND[:ARGS]}}` format. Normal text is typed as-is; only tokens are interpreted.

**Core Macros:**
- `{{DELAY:ms}}` – Pause for specified milliseconds (0–5000 ms clamped). Example: `{{DELAY:500}}`
- `{{SPEED:ms}}` – Set per-character typing delay (0–200 ms clamped). Example: `{{SPEED:10}}`
- `{{KEY:name}}` – Send a special key or key combination. Examples: `{{KEY:enter}}`, `{{KEY:tab}}`, `{{KEY:ctrl+s}}`
- `{{TEXT:...}}` – Type literal text (useful for embedding braces). Example: `{{TEXT:Hello {world}}}`

**Escaping:**
- `\{{` – Literal `{{`
- `\}}` – Literal `}}`

**Example File Content:**
```
Hello {{DELAY:500}}world!{{KEY:enter}}
{{SPEED:10}}Slower typing...{{KEY:ctrl+s}}{{DELAY:300}}{{KEY:enter}}
```

#### Macro Quick Reference

| Token | Syntax | Description | Example |
|---|---|---|---|
| Delay | `{{DELAY:ms}}` | Pause for `ms` milliseconds (0–5000 clamp) | `{{DELAY:500}}` |
| Speed | `{{SPEED:ms}}` | Per-character delay while typing (0–200 clamp) | `{{SPEED:10}}` |
| Text | `{{TEXT:...}}` | Type literal text, useful for braces | `{{TEXT:Hello {world}}}` |
| Key | `{{KEY:name}}` | Special keys | `{{KEY:enter}}`, `{{KEY:tab}}` |
| Key Combo | `{{KEY:mods+key}}` | Multiple modifiers + key | `{{KEY:ctrl+shift+esc}}`, `{{KEY:win+r}}` |
| Mouse Move | `{{MOUSE:MOVE dx dy}}` | Relative cursor movement | `{{MOUSE:MOVE 100 -50}}` |
| Mouse Click | `{{MOUSE:CLICK left|right|middle}}` | Mouse button click | `{{MOUSE:CLICK right}}` |
| Mouse Scroll | `{{MOUSE:SCROLL n}}` | Scroll `n` steps (+up / -down) | `{{MOUSE:SCROLL -3}}` |
| Gamepad Button | `{{GAMEPAD:PRESS/RELEASE btn}}` | A,B,X,Y, LB/RB, LT/RT, SELECT/BACK, START, HOME/MODE, LS/RS | `{{GAMEPAD:PRESS a}}` |
| Gamepad DPad | `{{GAMEPAD:DPAD dir}}` | `up,down,left,right,center` (+ diagonals) | `{{GAMEPAD:DPAD upright}}` |
| Gamepad Sticks | `{{GAMEPAD:LS x y}}`, `{{GAMEPAD:RS z rz}}` | Analog values in [-127,127] | `{{GAMEPAD:LS 50 -20}}` |
| Gamepad Triggers | `{{GAMEPAD:LT v}}`, `{{GAMEPAD:RT v}}` | Analog values in [-127,127] | `{{GAMEPAD:RT 80}}` |
| Audio Volume | `{{AUDIO:VOLUP[:n]}}`, `{{AUDIO:VOLDOWN[:n]}}` | Adjust volume by `n` steps | `{{AUDIO:VOLUP:3}}` |
| Audio Control | `{{AUDIO:MUTE}}`, `{{AUDIO:PLAY}}`, `{{AUDIO:STOP}}`, `{{AUDIO:NEXT}}`, `{{AUDIO:PREV}}` | Media control | `{{AUDIO:MUTE}}` |
| Escaping | `\{{`, `\}}` | Type literal braces | `\{{TEXT\}}` |

#### Supported Keys

**Basic Navigation:**
- `up`, `down`, `left`, `right`
- `home`, `end`, `pageup`, `pagedown`

**Editing:**
- `enter` / `return`, `backspace`, `delete`, `tab`, `escape` / `esc`, `insert` / `ins`

**Function Keys:**
- `f1` through `f12`

**Lock Keys:**
- `capslock` / `caps`, `numlock` / `num`, `scrolllock` / `scroll`

**Print/Pause:**
- `printscreen` / `print`, `pause` / `break`

**Numpad (Keypad):**
- `kp0` through `kp9` (or `numpad0` through `numpad9`)
- `kp_add` / `numpad_add`, `kp_subtract` / `numpad_subtract`
- `kp_multiply` / `numpad_multiply`, `kp_divide` / `numpad_divide`
- `kp_decimal` / `numpad_decimal` / `kp_dot`, `kp_enter` / `numpad_enter`

**GUI/Windows Keys:**
- `win` / `windows`, `rwin` / `rwindows`
- `menu` / `app` (Application/Context menu key)

**Modifier Keys (Left):**
- `ctrl`, `alt`, `shift`
- Combos: `ctrl+X`, `alt+X`, `shift+X` (where X is any character)

**Modifier Keys (Right):**
- `rctrl` / `rcontrol`, `ralt` / `raltgr`, `rshift`
- Combos: `rctrl+X`, `ralt+X`, `rshift+X`
- GUI combos: `win+X`, `rwin+X`

**Media Keys** (if supported by your system):
- `play` / `playpause`, `stop`, `next` / `nexttrack`, `prev` / `prevtrack`
- `volup` / `volumeup`, `voldown` / `volumedown`, `mute` / `volumemute`

**Common Examples:**
- `{{KEY:win+e}}` – Open Windows Explorer
- `{{KEY:ctrl+alt+delete}}` – Open Task Manager (Windows)
- `{{KEY:f5}}` – Refresh page/app
- `{{DELAY:1000}}` – Wait 1 second
- `{{SPEED:50}}` – Slow down typing (50ms per character)

#### Mouse Macros (Phase 2)

Control the mouse cursor and perform mouse actions embedded in text files.

**Mouse Commands:**
- `{{MOUSE:MOVE dx dy}}` – Move mouse cursor by `dx` and `dy` pixels (relative movement). Positive dx = right, negative dx = left; positive dy = down, negative dy = up. Example: `{{MOUSE:MOVE 100 50}}`
- `{{MOUSE:CLICK button}}` – Click a mouse button (`left`, `right`, or `middle`). Example: `{{MOUSE:CLICK left}}`
- `{{MOUSE:SCROLL n}}` – Scroll by `n` clicks. Positive n = scroll up, negative n = scroll down. Example: `{{MOUSE:SCROLL 3}}`

**Example File Content with Mouse:**
```
Testing mouse control{{DELAY:500}}{{MOUSE:MOVE 100 0}}{{KEY:enter}}
{{MOUSE:CLICK left}}{{DELAY:300}}{{MOUSE:SCROLL 5}}
Right-click menu:{{MOUSE:CLICK right}}
```

**Notes:**
- Mouse commands are ignored in BLE mode; they only work when typing via USB HID
- Relative movements are in pixels and depend on cursor speed settings in your OS
- Scrolling typically applies to the focused window

#### Gamepad Macros

Control a USB HID Gamepad mapped to standard Linux-style button names.

**Buttons:**
- `{{GAMEPAD:PRESS a}}`, `{{GAMEPAD:RELEASE a}}` – also supports aliases: `b,x,y,lb,rb,lt,rt,select/back,start,home/mode,ls,rs`

**D-Pad (Hat):**
- `{{GAMEPAD:DPAD up}}`, `down`, `left`, `right`, `center`
- Diagonals: `upright`, `upleft`, `downright`, `downleft`

**Analog Sticks:**
- `{{GAMEPAD:LS x y}}` – left stick; `x` and `y` in [-127,127]
- `{{GAMEPAD:RS z rz}}` – right stick; `z` and `rz` in [-127,127]

**Triggers:**
- `{{GAMEPAD:LT v}}`, `{{GAMEPAD:RT v}}` – values `v` in [-127,127]

**Example:**
```
{{GAMEPAD:PRESS a}}{{DELAY:200}}{{GAMEPAD:RELEASE a}}
{{GAMEPAD:DPAD right}}{{DELAY:100}}{{GAMEPAD:DPAD center}}
{{GAMEPAD:LS 50 -20}}{{DELAY:200}}{{GAMEPAD:RS -30 10}}
{{GAMEPAD:LT 80}}{{DELAY:100}}{{GAMEPAD:RT 60}}
```

#### Audio Macros

Control media playback and volume using HID media keys.

**Commands:**
- `{{AUDIO:VOLUP[:n]}}` – increase volume `n` steps (default 1)
- `{{AUDIO:VOLDOWN[:n]}}` – decrease volume `n` steps (default 1)
- `{{AUDIO:MUTE}}` – toggle mute
- `{{AUDIO:PLAY}}` / `{{AUDIO:STOP}}` – play/pause or stop
- `{{AUDIO:NEXT}}` / `{{AUDIO:PREV}}` – next/previous track

Note: Some hosts may not support all media keys; unsupported keys are safely ignored.

**Example:**
```
{{AUDIO:VOLUP:3}}{{DELAY:300}}{{AUDIO:MUTE}}{{DELAY:500}}{{AUDIO:PLAY}}{{DELAY:1000}}{{AUDIO:NEXT}}
```

#### Advanced Key Combos

Use multiple modifiers with a named key or a single character.

**Syntax:**
- `{{KEY:ctrl+shift+alt+f5}}`, `{{KEY:win+r}}`, `{{KEY:rctrl+shift+tab}}`

**Examples:**
- `{{KEY:win+r}}{{TEXT:cmd}}{{KEY:enter}}` – Run dialog and open Command Prompt
- `{{KEY:ctrl+shift+esc}}` – Open Task Manager (Windows)

### Samples

Example macro and script files are available in `samples/`:

**Macro Format:**
- `0092.txt` – Audio control demo (volume, mute, play, next)
- `0093.txt` – Gamepad demo (buttons, DPAD, sticks, triggers)
- `0094.txt` – Advanced key combos (Win+R, Ctrl+Shift+Esc)

**DuckyScript Format:**
- `ducky_calc.txt` – Opens Windows calculator
- `ducky_notepad.txt` – Opens notepad and types message

**Advanced Scripting:**
- `advanced_calc.txt` – Variables and conditionals demo
- `gpc_example.txt` – GPC syntax with FOR loops
- `nested_loops.txt` – Nested loop structures with multiplication table

To run a sample:
- Copy the files to the SD card root (FAT32 format)
- Boot to **Storage Mode** or **Macro / Text** from the boot menu
- Scroll to the desired file and long-press to execute
- PWDongle automatically detects the script format

## Advanced Scripting Reference

### Macro Recording (NEW in v0.5)

**Record live keyboard/mouse input from smartphone with OTG adapters to create macros automatically.**

#### Why Recording?
- **No coding required** - Just perform actions naturally
- **Perfect timing** - Automatic delay capture between actions
- **Live editing** - See your macro being written in real-time
- **Universal input** - Any USB keyboard/mouse works via OTG

#### Hardware Setup
1. **Smartphone** with OTG support (most Android/iOS devices)
2. **USB OTG adapter** (USB-C or Lightning to USB-A)
3. **USB keyboard** and/or **USB mouse**
4. **PWDongle** connected to computer via USB
5. **BLE connection** between smartphone and PWDongle

#### Recording Workflow

**Step 1: Start BLE Connection**
- Power on PWDongle (let countdown complete for automatic BLE mode)
- Open BLE UART app on smartphone (e.g., "Serial Bluetooth Terminal")
- Connect to "PWDongle"

**Step 2: Begin Recording**
```
> RECORD:my_macro
< OK: Recording started to my_macro.txt
```
PWDongle display shows "RECORDING" screen with filename.

**Step 3: Perform Actions on OTG Keyboard/Mouse**
- Connect keyboard/mouse to smartphone via OTG adapter
- Type text, press keys, move mouse - PWDongle records all actions
- Send commands via BLE app:
  - `KEY:keyname` - Records key press
  - `MOUSE:action` - Records mouse movement/click
  - `TYPE:text` - Records text typing
  - `GAMEPAD:action` - Records gamepad input

**Step 4: Stop Recording**
```
> STOPRECORD
< OK: Recording saved to my_macro.txt (5s)
```
Display shows "RECORDING COMPLETE" with duration.

**Step 5: Playback**
- Power cycle PWDongle
- Press BOOT during countdown → Select **Macro / Text**
- Scroll to `my_macro.txt` and long-press to execute

#### Recording Commands

| Command | Description | Example |
|---------|-------------|---------|
| `RECORD:filename` | Start recording to SD card | `RECORD:login_sequence` |
| `STOPRECORD` | Stop and save recording | `STOPRECORD` |
| `KEY:keyname` | Record key press | `KEY:enter`, `KEY:ctrl+c` |
| `MOUSE:action` | Record mouse action | `MOUSE:MOVE 100 50`, `MOUSE:CLICK left` |
| `TYPE:text` | Record text typing | `TYPE:username@example.com` |
| `GAMEPAD:action` | Record gamepad input | `GAMEPAD:PRESS a` |

**Note:** Delays between actions are automatically recorded (>50ms threshold).

#### Example Recording Session

**Scenario:** Record a login sequence

```
> RECORD:auto_login
< OK: Recording started to auto_login.txt

> KEY:win+r
< (Recorded: {{KEY:win+r}} with timestamp)

> TYPE:chrome
< (Recorded: chrome with 300ms delay)

> KEY:enter
< (Recorded: {{KEY:enter}} with 200ms delay)

> STOPRECORD
< OK: Recording saved to auto_login.txt (2s)
```

**Generated file (`/auto_login.txt`):**
```
// Recorded macro
// Format: PWDongle Macro

{{KEY:win+r}}
{{DELAY:300}}
chrome
{{DELAY:200}}
{{KEY:enter}}

// End of recording
```

#### Tips for Recording

- **Use descriptive filenames** - `login_macro`, `game_combo`, `text_template`
- **Test in pieces** - Record short sequences, test, then combine
- **Clean up files** - Delete failed recordings to keep SD organized
- **Check timing** - Some apps need longer delays; edit file to adjust
- **Combine recordings** - Manually merge multiple recordings into one file

### Script Format Detection

PWDongle automatically detects three script formats in priority order:

1. **Advanced Scripting** - Detected when file contains: `VAR`, `IF`, `LOOP`, `FOR`, `wait(`, `set_val(`
2. **DuckyScript** - Detected when file contains: `REM`, `DELAY`, `STRING`, `GUI`, `CTRL`, `ALT`
3. **Macro Format** - Default format using `{{TOKEN}}` syntax

You can mix formats in a single file - Advanced Scripting can include Macro tokens and DuckyScript commands.

### Variables

**Declaration:**
```
VAR counter = 0
VAR name = "text"
x = 10              // Shorthand (no VAR keyword)
```

**Types:**
- Integer variables: Store numeric values (-2147483648 to 2147483647)
- String variables: Store text (enclosed in quotes)

**Usage in Expressions:**
```
VAR total = x + y * 2
VAR result = (counter + 5) / 3
```

### Expressions

**Arithmetic Operators:**
- `+` Addition
- `-` Subtraction
- `*` Multiplication
- `/` Division (integer)
- `%` Modulo (remainder)

**Operator Precedence:**
1. Parentheses `( )`
2. Multiplication, Division, Modulo `*`, `/`, `%`
3. Addition, Subtraction `+`, `-`

**Examples:**
```
VAR x = 5 + 3 * 2      // Result: 11
VAR y = (5 + 3) * 2    // Result: 16
VAR z = 10 % 3         // Result: 1
```

### Conditionals

**IF Statement:**
```
IF condition
    // Commands when true
ENDIF
```

**IF-ELSE Statement:**
```
IF condition
    // Commands when true
ELSE
    // Commands when false
ENDIF
```

**Comparison Operators:**
- `==` Equal to
- `!=` Not equal to
- `<` Less than
- `>` Greater than
- `<=` Less than or equal
- `>=` Greater than or equal

**Logical Operators:**
- `&&` Logical AND
- `||` Logical OR

**Examples:**
```
IF x > 5
    STRING X is greater than 5{{KEY:enter}}
ENDIF

IF x >= 10 && y < 20
    STRING Both conditions true{{KEY:enter}}
ELSE
    STRING At least one false{{KEY:enter}}
ENDIF
```

**Nested Conditionals:**
```
IF x > 0
    IF y > 0
        STRING Both positive{{KEY:enter}}
    ELSE
        STRING X positive, Y not{{KEY:enter}}
    ENDIF
ENDIF
```

### Loops

**Simple LOOP:**
```
LOOP count
    // Commands to repeat
ENDLOOP
```

**Example:**
```
VAR i = 0
LOOP 5
    STRING Iteration{{KEY:enter}}
    {{DELAY:200}}
ENDLOOP
```

**FOR Loop:**
```
FOR variable = start TO end
    // Commands to repeat
NEXT variable
```

**Example:**
```
FOR i = 1 TO 10
    STRING Loop: {{KEY:enter}}
    {{DELAY:100}}
NEXT i
```

**Nested Loops:**
```
FOR x = 1 TO 3
    FOR y = 1 TO 3
        // Inner loop body
        {{DELAY:100}}
    NEXT y
NEXT x
```

### GPC (Game Profile Compiler) Syntax

Compatible subset of GPC language for gaming automation:

**wait(ms)** - Delay in milliseconds
```
wait(500)     // Wait 500ms
wait(1000)    // Wait 1 second
```

**set_val(button, value)** - Set gamepad button/axis
```
set_val(XB1_A, 100)      // Press A button
set_val(XB1_A, 0)        // Release A button
set_val(PS4_CROSS, 100)  // PS4 Cross button
```

**Supported Buttons:**
- Xbox: `XB1_A`, `XB1_B`, `XB1_X`, `XB1_Y`
- PlayStation: `PS4_CROSS`, `PS4_CIRCLE`, `PS4_SQUARE`, `PS4_TRIANGLE`

**combo_run(name)** - Execute combo (placeholder - requires combo definition)

### Complete Script Example

```
// Advanced Script: Automated Form Filling
VAR field_count = 5
VAR delay = 300

// Open application
{{KEY:win+r}}
{{DELAY:200}}
STRING notepad{{KEY:enter}}
wait(500)

// Type header
STRING Automated Entry{{KEY:enter}}
STRING ================={{KEY:enter}}
{{KEY:enter}}

// Loop through fields
FOR i = 1 TO field_count
    STRING Field {{DELAY:100}}
    
    IF i == field_count
        STRING (last)
    ELSE
        STRING (continued)
    ENDIF
    
    {{KEY:enter}}
    wait(delay)
NEXT i

// Close
{{KEY:enter}}
STRING Script complete!{{KEY:enter}}
```

### Script Best Practices

1. **Start with delays** - Give applications time to load: `{{DELAY:500}}`
2. **Use variables** - Store repeated values: `VAR delay = 300`
3. **Test incrementally** - Build scripts step by step
4. **Comment your code** - Use `//` or `REM` for notes
5. **Handle timing** - Different systems run at different speeds
6. **Mix formats** - Combine Advanced, Ducky, and Macro syntax as needed
7. **Validate conditions** - Always test IF statements with different values
8. **Keep loops bounded** - Avoid infinite loops with reasonable counts

### Troubleshooting Scripts

**Script doesn't run:**
- Check file is in SD card root directory
- Verify `.txt` extension
- Ensure FAT32 format on SD card

**Commands execute incorrectly:**
- Add delays between commands: `{{DELAY:200}}`
- Check syntax (case-insensitive for keywords)
- Verify variable assignments have correct operators

**Variables not working:**
- Ensure variable is declared before use
- Check arithmetic operator precedence
- Use parentheses for complex expressions

**Loops repeat wrong number of times:**
- Verify loop count expression: `LOOP 5` or `FOR i = 1 TO 10`
- Check for early exits in conditionals
- Ensure ENDLOOP/NEXT matches LOOP/FOR

## Project Structure

```
PWDongle/
├── include/
│   ├── bluetooth.h      # BLE UART + keystroke relay
│   ├── duckyscript.h    # RubberDucky script parser
│   ├── scriptengine.h   # Advanced scripting engine (NEW v0.4)
│   ├── display.h        # TFT UI functions
│   ├── input.h          # Button handling & PIN entry
│   ├── security.h       # PIN validation & persistence
│   ├── storage.h        # NVS password storage
│   └── usb.h            # USB HID/CDC/MSC + macro processing
├── src/
│   ├── bluetooth.cpp    # BLE implementation
│   ├── display.cpp      # TFT rendering (boot menu, file browser)
│   ├── duckyscript.cpp  # DuckyScript parser & executor (NEW v0.3.1)
│   ├── input.cpp        # Button state machine
│   ├── main.cpp         # Setup & main loop
│   ├── scriptengine.cpp # Script engine with variables/loops/conditionals (NEW v0.4)
│   ├── security.cpp     # Access codes
│   ├── storage.cpp      # NVS operations
│   └── usb.cpp          # USB modes + format auto-detection
├── lib/
│   └── TFT_eSPI/
│       └── User_Setup.h # Display driver config
├── samples/             # Example scripts
│   ├── 0092.txt         # Audio control demo
│   ├── 0093.txt         # Gamepad demo
│   ├── 0094.txt         # Key combo demo
│   ├── ducky_calc.txt   # DuckyScript calculator
│   ├── ducky_notepad.txt # DuckyScript notepad
│   ├── advanced_calc.txt # Variables & conditionals
│   ├── gpc_example.txt  # GPC syntax demo
│   ├── nested_loops.txt # Nested loop example
│   └── recorded_example.txt # Macro recording example (NEW v0.5)
├── releases/            # Pre-compiled binaries
│   ├── PWDongle-v0.5-esp32s3.bin     # Latest (1.13MB)
│   ├── PWDongle-v0.4-esp32s3.bin     # Advanced Scripting
│   ├── PWDongle-v0.3.1-esp32s3.bin   # DuckyScript
│   └── PWDongle-v0.3-esp32s3.bin     # Boot menu
├── boards/
│   └── esp32-s3-lcd-1.47.json  # Custom board definition
├── platformio.ini       # PlatformIO configuration
├── BLE_USAGE.md        # Detailed BLE guide
└── README.md           # This file
```

## Technical Details

### Storage (NVS)
- **Namespace "devstore"**: Up to 10 device/password pairs
- **Namespace "CDC"**: CDC boot flag
- **Namespace "BLE"**: BLE boot flag
- **Namespace "SEC"**: Persisted login code

### BLE Service
- **Service UUID**: `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` (Nordic UART)
- **RX Characteristic**: `6E400002-B5A3-F393-E0A9-E50E24DCCA9E` (Write)
- **TX Characteristic**: `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` (Notify)

### Dependencies
- `bodmer/TFT_eSPI@^2.5.43`
- `ESP32 BLE Arduino@2.0.0` (included in esp32 framework)
- Arduino framework for ESP32-S3

## Security Notes

- Default login code `1122` should be changed via `CHANGELOGIN` command
- PIN entry digits masked after acceptance (show as `*`)
- Passwords stored in plain text in NVS (device-local only)
- No encryption over BLE UART (consider security implications)

## Troubleshooting

**Bluetooth device not appearing in scan:**
- Let countdown complete or select Bluetooth from boot menu
- Check phone's Location permission (required for BLE on Android)
- Try moving phone closer to device
- Use "nRF Connect" app for most reliable scanning

**TYPE/KEY commands not working:**
- Verify device shows "BLE ACTIVE" screen
- Ensure PC recognizes device as USB keyboard
- Check that both Bluetooth AND USB are connected

**SD files not appearing in menu:**
- Ensure the SD card is inserted and formatted (FAT32 recommended)
- Place `.txt` files in the SD card root directory
- Up to 15 files are displayed
- Verify files have `.txt` extension

**Boot menu not appearing:**
- Press BOOT button **during** the 3-second countdown (not before/after)
- Hold button briefly until boot menu appears
- Try shorter press if menu doesn't appear

**Passwords not persisting:**
- Use `ABOUT` command to verify persistence status
- Check NVS has not been erased (re-flash with `--erase-all` if needed)

**Script not executing:**
- Verify file is on SD card root with `.txt` extension
- Check script syntax (case-insensitive for keywords)
- Add delays between commands: `{{DELAY:200}}` or `wait(200)`
- Test with simple script first to verify SD card works
- Check TFT display for format detection message

## Version History

### v0.5 (Current) - Macro Recording
- **Live Recording**: Record keyboard/mouse input from smartphone OTG to SD card
- **BLE Commands**: RECORD:filename, STOPRECORD with automatic delay capture
- **Recording UI**: "RECORDING" screen with filename, "RECORDING COMPLETE" with duration
- **File Format**: PWDongle macro syntax with automatic {{DELAY:ms}} timing (>50ms threshold)
- **Sample File**: recorded_example.txt demonstrates recorded macro format
- Flash: 1.13MB (33.7%), RAM: 58KB (17.9%)

### v0.4 - Advanced Scripting
- **Variables**: Integer and string variable support
- **Conditionals**: IF/ELSE/ENDIF with comparison operators
- **Loops**: LOOP/ENDLOOP and FOR/NEXT constructs
- **Expressions**: Arithmetic (+, -, *, /, %) and logical (&&, ||) operators
- **GPC Syntax**: Game Profile Compiler compatibility (wait, set_val)
- **Script Engine**: 500+ lines of parser and executor
- **Format Detection**: Auto-detect Advanced > DuckyScript > Macro
- **Sample Scripts**: 3 new advanced scripting examples
- Flash: 1.12MB (33.5%), RAM: 58KB (17.8%)

### v0.3.1 - DuckyScript Support
- **RubberDucky**: Full BadUSB DuckyScript compatibility
- **Auto-Detection**: Detects DuckyScript vs Macro format
- **Commands**: STRING, DELAY, REM, key combinations
- **Parser**: 240+ lines of DuckyScript processor
- **Sample Scripts**: ducky_calc.txt, ducky_notepad.txt
- Flash: 1.11MB (33.2%)

### v0.3 - Boot Menu & File Browser
- **Boot Menu**: 5-option menu (Bluetooth, Terminal, Password, Storage, Macro/Text)
- **File Browser**: Scrollable SD card file selection (9 files visible)
- **MSC Mode**: Flash drive mode with code 0001
- **Auto-Boot**: 3-second countdown to Bluetooth mode
- **UI Updates**: Renamed modes (BLE→Bluetooth, CDC→Terminal)
- **Documentation**: Complete README and BLE_USAGE updates
- Flash: 1.1MB (33.1%)

### v0.2 and Earlier
- Initial password manager functionality
- USB HID keyboard typing
- CDC serial configuration
- BLE smartphone control with keystroke relay
- Macro language with {{TOKEN}} syntax
- Mouse, gamepad, and audio control macros
- 4-digit PIN authentication
- NVS password storage

## License

This project is open source. See repository for details.

## Contributing

Contributions welcome! Please open issues or pull requests on GitHub.
