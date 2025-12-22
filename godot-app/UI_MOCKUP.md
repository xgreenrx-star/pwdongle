# UI Mockup for PWDongle Controller App

## Screen Layout (Portrait: 1080x2400)

```
┌─────────────────────────────────────┐
│                                     │
│       PWDongle Controller           │ ← Title (Cyan, 32pt)
│                                     │
├─────────────────────────────────────┤
│                                     │
│      Status: Not Connected          │ ← Status Panel (Red/Green)
│                                     │
├─────────────────────────────────────┤
│  Connection                         │ ← Cyan section header
│                                     │
│  ┌───────────────────────────────┐  │
│  │ PWDongle [AA:BB:CC:DD:EE:FF] │  │
│  │ PWDongle-2 [11:22:33:44:55] │  │ ← Device List (ItemList)
│  │ ESP32-BLE [66:77:88:99:AA]  │  │   Scrollable, 150px height
│  └───────────────────────────────┘  │
│                                     │
│  [  Scan for Devices  ]             │ ← Scan Button (full width)
│  [      Connect       ]             │ ← Connect Button
│  [    Disconnect      ]             │ ← Disconnect (hidden when not connected)
│                                     │
├─────────────────────────────────────┤
│  Macro Recording                    │ ← Cyan section header
│                                     │
│  ┌───────────────────────────────┐  │
│  │ my_macro.txt                  │  │ ← Filename input (LineEdit)
│  └───────────────────────────────┘  │
│                                     │
│  [   Start Recording   ]            │ ← Record Button (full width)
│  [   Stop Recording    ]            │ ← Stop Button (red, hidden initially)
│                                     │
│  RECORDING: my_macro.txt            │ ← Recording status (red when active)
│                                     │
├─────────────────────────────────────┤
│  Quick Keys                         │ ← Cyan section header
│                                     │
│  [ ENTER ] [ TAB   ] [ BKSP  ] [ ESC  ] │
│  [ UP    ] [ DOWN  ] [ LEFT  ] [ RIGHT] │ ← 4-column grid
│  [CTRL+C ] [CTRL+V ] [CTRL+Z ] [ALT+TB] │   of quick key buttons
│  [ WIN+R ] [  F5   ] [ ... ] [ ... ] │   (dynamic based on keys)
│                                     │
├─────────────────────────────────────┤
│  Type Text                          │ ← Cyan section header
│                                     │
│  ┌───────────────────────────────┐  │
│  │ Enter text to type...         │  │ ← Multi-line text input
│  │                               │  │   (TextEdit, 120px height)
│  │                               │  │
│  └───────────────────────────────┘  │
│                                     │
│  [      Send Text      ]            │ ← Send button (full width)
│                                     │
├─────────────────────────────────────┤
│  Console                            │ ← Cyan section header
│                                     │
│  ┌───────────────────────────────┐  │
│  │[10:23:45] Scanning...         │  │
│  │[10:23:48] Found: PWDongle     │  │ ← Console output
│  │[10:23:50] Connected           │  │   (TextEdit, readonly, 200px)
│  │[10:24:01] ← OK: Recording     │  │   Auto-scrolls to bottom
│  │[10:24:15] ← OK: Stopped       │  │
│  └───────────────────────────────┘  │
│                                     │
└─────────────────────────────────────┘
```

## Color Scheme

- **Background**: Dark blue-gray (`Color(0.1, 0.1, 0.15, 1)`)
- **Section Headers**: Cyan (`Color(0, 0.8, 1, 1)`) - 18pt font
- **Title**: Cyan - 32pt font, centered
- **Status Panel**:
  - Not Connected: Red (`Color(0.8, 0, 0, 1)`)
  - Connected: Green (`Color(0, 0.8, 0, 1)`)
  - 20pt font, centered
- **Normal Text**: White/light gray - 16pt
- **Recording Status**: Red when active - 16pt, centered
- **Buttons**: Default Godot theme with hover/pressed states
- **Console**: Monospace font, white text on dark background

## Component Details

### 1. Title Bar
- **Type**: Label
- **Text**: "PWDongle Controller"
- **Alignment**: Center
- **Font Size**: 32pt
- **Color**: Cyan

### 2. Status Panel
- **Type**: PanelContainer > Label
- **States**:
  - "Not Connected" (Red)
  - "Connected to PWDongle" (Green)
  - "Scanning..." (Yellow)
- **Font Size**: 20pt
- **Alignment**: Center

### 3. Connection Panel
- **Device List** (ItemList):
  - Height: 150px
  - Shows: "DeviceName [Address]"
  - Single selection mode
- **Scan Button**: Full width, enabled when disconnected
- **Connect Button**: Full width, enabled when device selected
- **Disconnect Button**: Full width, visible only when connected

### 4. Recording Panel
- **Filename Input** (LineEdit):
  - Placeholder: "Enter filename (e.g. my_macro.txt)"
  - Disabled during recording
- **Record Button**: Full width, disabled when disconnected
- **Stop Record Button**: Full width, red text, hidden initially
- **Recording Status** (Label):
  - Shows: "RECORDING: filename.txt" (red)
  - Hidden when not recording

### 5. Quick Keys Panel
- **Grid Container**: 4 columns
- **Buttons**: Dynamic, generated from key list
  - Keys: enter, tab, backspace, escape, arrow keys, ctrl+c/v/z, alt+tab, win+r, f5, etc.
  - Text: Uppercase key names (e.g., "ENTER", "CTRL+C")
  - All disabled when disconnected

### 6. Type Text Panel
- **Text Input** (TextEdit):
  - Height: 120px
  - Placeholder: "Enter text to type..."
  - Multi-line enabled
  - Disabled when disconnected
- **Send Button**: Full width, disabled when disconnected or text empty

### 7. Console Panel
- **Console Output** (TextEdit):
  - Height: 200px
  - Read-only: true
  - Wrap mode: Word wrap
  - Font: Monospace
  - Format: `[HH:MM:SS] Message`
  - Auto-scrolls to bottom

## Interaction Flow

### Connection Flow
1. User taps "Scan for Devices"
2. Device list populates with discovered devices
3. User taps device in list → Selects it
4. User taps "Connect"
5. Status changes to "Connected to PWDongle" (green)
6. Disconnect button becomes visible
7. Recording and quick actions become enabled

### Recording Flow
1. User enters filename (e.g., "test_macro.txt")
2. User taps "Start Recording"
3. Recording status appears: "RECORDING: test_macro.txt" (red)
4. Stop Record button replaces Record button
5. User performs actions (quick keys, text typing, OTG input)
6. User taps "Stop Recording"
7. Recording status disappears
8. Record button reappears

### Quick Key Flow
1. User taps any quick key button (e.g., "ENTER")
2. Console logs: `[HH:MM:SS] Sending key: enter`
3. BLE command sent: `KEY:enter\n`
4. Response logged: `[HH:MM:SS] ← OK: Key sent`

### Text Typing Flow
1. User types text in text input field
2. User taps "Send Text"
3. Console logs: `[HH:MM:SS] Sending text: <text>`
4. BLE command sent: `TYPE:<text>\n`
5. Text input clears
6. Response logged: `[HH:MM:SS] ← OK: Text sent`

## Responsive Behavior

- **Portrait orientation**: Fixed layout as shown
- **Landscape**: Not supported (orientation locked)
- **Scrolling**: Main VBoxContainer scrolls if content exceeds screen height
- **Touch targets**: All buttons 48dp minimum height (Android guidelines)
- **Margins**: 20px padding around edges

## Accessibility

- **Color contrast**: High contrast (cyan/white on dark background)
- **Text size**: Minimum 16pt for readability
- **Button size**: Large touch targets (48dp+)
- **Status indicators**: Color + text (red/green + "Connected"/"Disconnected")

## Scene Node Tree (Godot)

```
Main (Control)
├── BLEManager (Node, script: ble_manager.gd)
├── ColorRect (background)
└── MarginContainer (20px margins)
    └── VBoxContainer (15px separation)
        ├── TitleLabel (Label, "PWDongle Controller")
        ├── StatusPanel (PanelContainer)
        │   └── StatusLabel (Label, "Not Connected")
        ├── ConnectionPanel (PanelContainer)
        │   └── VBoxContainer
        │       ├── Label ("Connection")
        │       ├── DeviceList (ItemList, 150px height)
        │       ├── ScanButton (Button)
        │       ├── ConnectButton (Button)
        │       └── DisconnectButton (Button, initially hidden)
        ├── RecordingPanel (PanelContainer)
        │   └── VBoxContainer
        │       ├── Label ("Macro Recording")
        │       ├── FilenameInput (LineEdit)
        │       ├── RecordButton (Button)
        │       ├── StopRecordButton (Button, initially hidden)
        │       └── RecordingStatus (Label, initially empty)
        ├── QuickActionsPanel (PanelContainer)
        │   └── VBoxContainer
        │       ├── Label ("Quick Keys")
        │       └── GridContainer (4 columns)
        │           └── [Buttons generated dynamically]
        ├── TypePanel (PanelContainer)
        │   └── VBoxContainer
        │       ├── Label ("Type Text")
        │       ├── TextInput (TextEdit, 120px height)
        │       └── SendButton (Button)
        └── ConsolePanel (PanelContainer)
            └── VBoxContainer
                ├── Label ("Console")
                └── ConsoleOutput (TextEdit, 200px height, readonly)
```

## Implementation Notes

This UI mockup is fully implemented in `scenes/main.tscn` with corresponding controller logic in `scripts/main.gd`. The scene tree matches the structure shown above, with all visual properties (colors, fonts, sizes) configured in the scene file.
