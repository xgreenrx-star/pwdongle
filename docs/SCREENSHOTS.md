# Screenshots and Photos

This folder holds UI screenshots/photos for the README.

## Android Recorder – Recorder Screen (v0.5)

- Target file path: `docs/recorder-screen.png`
- Description: Recorder screen showing status, filename, input method, Start/Stop, and new Keyboard/Touchpad buttons

### How to Capture
1. Build and install the Android app (see android-recorder README)
2. Open the app → Recorder screen
3. Ensure BLE status shows connected (optional)
4. Capture the screen with both "⌨️ Keyboard" and "🖱️ Touchpad" buttons visible
5. Save as `recorder-screen.png` in `docs/`

## On-Screen Keyboard Dialog

- Target file path: `docs/keyboard-dialog.png`
- Description: Custom keyboard view; demonstrate typing a few characters

### How to Capture
1. On Recorder screen, tap "⌨️ Keyboard"
2. Show the dialog with keys visible
3. Optionally highlight Shift/Caps behavior
4. Save as `keyboard-dialog.png` in `docs/`

## Touchpad Dialog

- Target file path: `docs/touchpad-dialog.png`
- Description: Virtual touchpad view with move/click/scroll interactions

### How to Capture
1. On Recorder screen, tap "🖱️ Touchpad"
2. Show the dialog with touchpad area visible
3. Save as `touchpad-dialog.png` in `docs/`

## PIN Entry Dialog (Android App)

- Target file path: `docs/pin-dialog.png`
- Description: 4-digit numeric PIN dialog (digit-only enforcement)

### How to Capture
1. Launch app; when prompted, show the PIN dialog
2. Ensure numeric keypad is visible (if possible)
3. Save as `pin-dialog.png` in `docs/`

## File Selection Menu (Storage/Macro Mode)

- Target file path in README: `docs/file-menu-ui.png`
- Description: Scrollable menu showing SD card `.txt` files

### How to Capture
1. Flash the latest firmware and power the device
2. Press BOOT button during the 3-second countdown
3. Select **Storage Mode** or **Macro / Text** from boot menu
4. Long-press to confirm
5. Ensure microSD card with `.txt` files is inserted
6. Use your phone to take a clear photo of the scrollable file menu
7. Crop to the screen, keep the file list and scroll indicators visible
8. Save as `file-menu-ui.png` in the `docs/` folder

## Boot Menu

- Target file path: `docs/boot-menu.png`
- Description: 5-option boot menu with Bluetooth, Terminal, Password, Storage, Macro/Text

### How to Capture
1. Power on device
2. Press BOOT button during countdown
3. Photo the boot menu showing all 5 options
4. Save as `boot-menu.png`

### Image Guidelines
- Format: PNG
- Suggested width: 640–1280 px (GitHub will scale down)
- Keep glare/reflections minimal
- Ensure text is readable (auto-focus, no motion blur)

Once the PNG files are added, update the README to display them.
