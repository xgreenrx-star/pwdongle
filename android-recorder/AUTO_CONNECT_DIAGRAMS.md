# Auto-Connect Feature - Visual Guide

## User Flow Diagram

```
┌─────────────────────────────────────────────────────────┐
│              App Starts / Recorder Tab Opens             │
└────────────────────────┬────────────────────────────────┘
                         │
                         ▼
          ┌──────────────────────────────┐
          │ Check Auto-Connect Preference │
          │   (from PreferencesManager)   │
          └──────────┬────────────────────┘
                     │
          ┌──────────┴──────────┐
          │                     │
       YES│                     │NO
          │                     │
          ▼                     ▼
    ┌────────────┐       ┌──────────────────┐
    │ Auto-Mode  │       │  Manual-Mode     │
    │ (Enabled)  │       │  (Disabled)      │
    └─────┬──────┘       └────────┬─────────┘
          │                       │
          ▼                       ▼
    ┌────────────────┐    ┌──────────────────────┐
    │ Start BLE Scan │    │ Show Device Selector │
    │ (10 sec timeout)     │ (Hidden by default)  │
    └────────┬───────┘    └──────────┬───────────┘
             │                       │
             ▼                       ▼
    ┌─────────────────────┐  ┌────────────────────┐
    │Find "PWDongle" in   │  │ User taps "Scan"   │
    │  discovered devices │  │ in Settings tab    │
    └────────┬────────────┘  └─────┬──────────────┘
             │                     │
       ┌─────┴─────┐              ▼
       │           │       ┌───────────────┐
    FOUND       NOT FOUND  │ BLE Scan (10s)│
       │           │       └───────┬───────┘
       │           │               │
       ▼           ▼               ▼
    ┌────────┐  ┌──────────┐  ┌────────────┐
    │  Auto  │  │ Fallback │  │   Show     │
    │Connect │  │  to show │  │   Devices  │
    └────┬───┘  │ Device   │  │   List     │
         │      │ Selection│  └──────┬─────┘
         │      └────┬─────┘         │
         │           │              │
         ▼           ▼              ▼
    ┌──────────────────────────────────────┐
    │     User Selects Device from List     │
    └──────────────┬───────────────────────┘
                   │
                   ▼
           ┌──────────────────┐
           │ Tap "Connect"    │
           │    Button        │
           └────────┬─────────┘
                    │
                    ▼
           ┌──────────────────┐
           │ BLE Connection   │
           │ to Selected      │
           │ Device           │
           └────────┬─────────┘
                    │
                    ▼
        ┌──────────────────────┐
        │"Connected" Status    │
        │Recording Controls    │
        │    Available         │
        └──────────────────────┘
```

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     Android App (MainActivity)                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────────────────────────────────────────┐      │
│  │  RecorderFragment  (Main UI)                        │      │
│  ├──────────────────────────────────────────────────────┤      │
│  │  • Observes autoConnectEnabledFlow                  │      │
│  │  • Calls autoConnectToPWDongle()                    │      │
│  │  • Shows device selector when needed                │      │
│  │  • Handles recording logic                          │      │
│  │  • Owns: statusText, recordButton, etc.            │      │
│  └──────────────┬───────────────────────────────────────┘      │
│                 │                                               │
│                 │ uses                                          │
│                 │                                               │
│  ┌──────────────▼───────────────┐  ┌──────────────────┐       │
│  │  PreferencesManager           │  │  BLEManager      │       │
│  ├───────────────────────────────┤  ├──────────────────┤       │
│  │• autoConnectEnabledFlow       │  │• scanForDevices()│       │
│  │  (Boolean Flow)               │  │• connectToDevice │       │
│  │• lastConnectedDeviceFlow      │  │                  │       │
│  │  (String? Flow)               │  │ (from existing)  │       │
│  │• savedDevicesFlow             │  │                  │       │
│  │  (List<String> Flow)          │  │                  │       │
│  │                               │  │                  │       │
│  │ Uses DataStore for storage    │  │ Uses BLE scanning│       │
│  └───────────────────────────────┘  │ and GATT ops    │       │
│                                      └──────────────────┘       │
│                                                                  │
│  ┌──────────────────────────────────────────────────────┐      │
│  │  SettingsFragment  (Settings UI)                    │      │
│  ├──────────────────────────────────────────────────────┤      │
│  │  • Shows autoConnectSwitch toggle                   │      │
│  │  • Updates preference on toggle                     │      │
│  │  • Shows device list after scan                     │      │
│  │  • Handles device selection                         │      │
│  └──────────────────────────────────────────────────────┘      │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

                     ▼ reads/writes

┌─────────────────────────────────────────────────────────────────┐
│                   Android DataStore                             │
│                  (pwdongle_prefs)                              │
├─────────────────────────────────────────────────────────────────┤
│  • auto_connect_enabled: Boolean (default: true)               │
│  • last_connected_device: String                               │
│  • saved_devices: String (comma-separated)                     │
└─────────────────────────────────────────────────────────────────┘
```

## State Machine Diagram

```
┌─────────────────────────────────────────┐
│        App Initialization                │
└────────────────────┬────────────────────┘
                     │
                     ▼
        ┌─────────────────────────┐
        │ Check Auto-Connect      │
        │ Preference              │
        └─────┬────────────┬──────┘
              │            │
          enabled      disabled
              │            │
              ▼            ▼
     ┌──────────────┐  ┌──────────────┐
     │  SEARCHING   │  │  WAITING     │
     └──────┬───────┘  │  FOR DEVICE  │
            │          │  SELECTION   │
            │          └──────┬───────┘
            │                 │
            ▼                 │
     ┌─────────────────┐     │
     │Found PWDongle?  │     │
     └────┬──────┬─────┘     │
          │      │           │
        YES    NO            │
          │      │           │
          │      ▼           │
          │    ┌──────────────┼─────────────────┐
          │    │Show Device Selection (Fallback)│
          │    └────────┬─────────────────────┘
          │             │
          ▼             ▼
     ┌──────────────┐┌──────────────┐
     │  CONNECTING │││ USER SELECTS ││
     │ TO PWDONGLE ││ DEVICE       ││
     └──────┬───────┘└────┬────────┘
            │             │
            │             ▼
            │       ┌────────────┐
            │       │ CONNECTING │
            │       │ TO SELECTED│
            │       └──────┬─────┘
            │              │
            └──────┬───────┘
                   │
                   ▼
        ┌──────────────────┐
        │   CONNECTED      │
        │ (Ready to Record)│
        └──────────────────┘
```

## Data Flow Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                    RecorderFragment                           │
└──────┬──────────────────────────────────────────────────────┘
       │
       │ .collect()
       ▼
┌──────────────────────────────────────────────────────────────┐
│        preferencesManager.autoConnectEnabledFlow              │
│                 (Flow<Boolean>)                               │
└──────┬──────────────────────────────────────────────────────┘
       │
       │ emits: true/false
       ▼
┌──────────────────────────────────────────────────────────────┐
│             PreferencesManager                                │
│                                                               │
│  reads from:  ┌────────────────────────────────────────┐    │
│               │  Android DataStore                      │    │
│               │  ("pwdongle_prefs" namespace)           │    │
│               │  key: "auto_connect_enabled"            │    │
│               └────────────────────────────────────────┘    │
│                                                               │
│  default value: true                                         │
│                                                               │
└──────────────────────────────────────────────────────────────┘

                          ▲
                          │ .setAutoConnectEnabled()
                          │
        ┌─────────────────┴───────────────────┐
        │                                     │
        ▼                                     ▼
┌─────────────────┐              ┌─────────────────┐
│ SettingsFragment│              │ RecorderFragment│
│                 │              │                 │
│ User toggles    │              │ (could also be  │
│ autoConnectSwitch               │  set here)      │
└─────────────────┘              └─────────────────┘
```

## Device Selection Flow

```
┌─────────────────────────────────────────────────────┐
│   Scan for Devices (BLEManager.scanForDevices)     │
└────────────────────┬────────────────────────────────┘
                     │
                     │ Scan timeout 10 seconds
                     ▼
          ┌─────────────────────────┐
          │  List of BLE Devices     │
          │  ["Device1", "Device2",  │
          │   "PWDongle-123", ...]   │
          └────────────┬────────────┘
                       │
                       ▼
        ┌──────────────────────────────┐
        │  Filter for "PWDongle"        │
        │  (case-insensitive)           │
        └──────────┬────────────────────┘
                   │
          ┌────────┴────────┐
          │                 │
       FOUND            NOT FOUND
          │                 │
          ▼                 ▼
   ┌────────────┐   ┌──────────────────┐
   │ Auto-Connect│  │ Show all devices │
   │  to it      │  │ for manual       │
   │ (no UI)     │  │ selection        │
   └────────────┘  └────────┬─────────┘
                            │
                            ▼
                  ┌──────────────────────┐
                  │ User selects from    │
                  │ deviceSpinner        │
                  │ (Dropdown)           │
                  └────────┬─────────────┘
                           │
                           ▼
                  ┌──────────────────────┐
                  │ User taps "Connect"  │
                  │ Button               │
                  └────────┬─────────────┘
                           │
                           ▼
                  ┌──────────────────────┐
                  │ BLE Connection       │
                  │ (BLEManager)         │
                  └──────────────────────┘
```

## Layout Visibility Control

```
┌─────────────────────────────────────────────────────┐
│          Fragment Recorder                           │
│                                                     │
│  ┌──────────────────────────────────────────────┐  │
│  │ Status Text                                  │  │
│  │ "Searching for PWDongle..." OR              │  │
│  │ "Auto-connect DISABLED - Select Device"    │  │
│  └──────────────────────────────────────────────┘  │
│                                                     │
│  ┌──────────────────────────────────────────────┐  │
│  │  Device Selector (deviceSelector)            │  │
│  │  Visibility: GONE or VISIBLE                 │  │
│  │  ┌──────────────────────────────────────┐   │  │
│  │  │ Spinner (deviceSpinner)              │   │  │
│  │  │ "Select PWDongle Device:"            │   │  │
│  │  │ ["Device1", "Device2", ...]         │   │  │
│  │  └──────────────────────────────────────┘   │  │
│  │  ┌──────────────────────────────────────┐   │  │
│  │  │ Button: Connect                      │   │  │
│  │  └──────────────────────────────────────┘   │  │
│  └──────────────────────────────────────────────┘  │
│                                                     │
│         [Based on autoConnectEnabled]              │
│          true  → GONE                              │
│          false → VISIBLE                           │
│                                                     │
│  Rest of Recorder UI (always visible)              │
│  - Filename input                                  │
│  - Input method selector                           │
│  - Record button                                   │
│  - Status indicators                               │
└─────────────────────────────────────────────────────┘
```

## Preference Toggle Flow

```
┌──────────────────────────────────┐
│    SettingsFragment              │
│                                  │
│  ┌────────────────────────────┐  │
│  │ Auto-Connect Switch        │  │
│  │  [Toggle Button]           │  │
│  │                            │  │
│  │ "When enabled, the app     │  │
│  │  will automatically        │  │
│  │  search for PWDongle..."   │  │
│  └────────┬───────────────────┘  │
└───────────┼──────────────────────┘
            │
            │ onCheckedChangeListener
            │
            ▼
┌──────────────────────────────────┐
│   PreferencesManager              │
│   setAutoConnectEnabled()         │
│                                   │
│   launches coroutine:             │
│   dataStore.edit { prefs ->       │
│     prefs[AUTO_CONNECT] = checked │
│   }                               │
└────────────┬─────────────────────┘
             │
             ▼
┌──────────────────────────────────┐
│   DataStore Writes to Disk        │
│   (pwdongle_prefs)                │
│                                   │
│   File: .../datastore/            │
│         pwdongle_prefs.preferences_pb
└────────────┬─────────────────────┘
             │
             ▼ broadcasts update
┌──────────────────────────────────┐
│   All Observers (Flow.collect)    │
│   get notified of change          │
│                                   │
│   RecorderFragment receives       │
│   new value and updates UI        │
└──────────────────────────────────┘
```

This visual guide helps understand the complete auto-connect implementation!
