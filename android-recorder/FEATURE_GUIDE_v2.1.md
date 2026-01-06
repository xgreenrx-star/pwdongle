# PWDongle Android Recorder v2.1 - Feature Guide

## New in v2.1 (January 2026)

This release includes major improvements to reliability, usability, and batch operations.

### 1. Automatic BLE Reconnection

**What it does:** If your PWDongle disconnects unexpectedly, the app will automatically try to reconnect.

**How it works:**
- Detects connection loss (timeout, signal loss, device reset)
- Attempts reconnection with exponential backoff: 1s, 2s, 4s, 8s, 16s
- Up to 5 automatic reconnection attempts
- Shows status messages during reconnection process

**Benefits:**
- No more manual reconnection after temporary signal drops
- Seamless experience when moving out of range and back
- Automatically recovers from device resets

**User notification:**
- "Reconnecting in 2s (attempt 2/5)" - App is trying to reconnect
- "Connected to PWDongle" - Reconnection successful
- "Reconnection failed after 5 attempts" - Manual intervention needed

### 2. Macro Validation Before Playback

**What it does:** Checks your macro for errors before executing it on your PC.

**Validation checks:**
- Syntax errors (malformed commands)
- Unknown commands or tokens
- Invalid delay values (negative or non-numeric)
- Invalid key names
- Missing arguments
- Long delays (>10 seconds) flagged as warnings

**What you see:**
1. **No errors, no warnings:** Brief toast shows command count and estimated duration
2. **Warnings only:** Dialog shows summary with option to proceed or cancel
   - Command count
   - Estimated duration
   - List of warnings (e.g., "Line 12: Long delay 15000ms (15s)")
3. **Errors:** Dialog shows errors with option to play anyway or cancel

**Example warnings:**
- "Line 45: Long delay 30000ms (30s)" - Macro has a 30-second pause
- "Line 23: Unknown key 'F13'" - Key might not be supported
- "Total estimated duration is very long: 180s (3min)" - Long-running macro

**Benefits:**
- Catch typos before they cause unexpected behavior
- Understand what a macro will do before running it
- Know how long a macro will take to execute

### 3. Batch File Operations

**What it does:** Select and operate on multiple macro files at once.

**How to use:**
1. In File Manager screen, tap "Select Multiple" button
2. Checkboxes appear next to each macro file
3. Tap files to select/deselect (or tap checkbox directly)
4. Use action buttons:
   - **Select All** - Toggle all files on/off
   - **Delete (N)** - Delete selected files with confirmation
   - **Play (N)** - Play selected files sequentially (with 2s delay between each)
5. Tap "Cancel" to exit batch mode

**Features:**
- Select from 1 to all visible files
- Confirmation dialog before batch deletion
- Sequential playback with progress updates
- Counters show number of selected files

**Use cases:**
- Clean up old test macros: Select multiple → Delete
- Run a sequence of setup macros: Select in order → Play
- Organize files: Select similar macros → Share/export

**Note:** Batch play currently navigates to playback screen per file (inline execution coming in future update).

### 4. Playback Progress Indicators

**What it does:** Shows real-time progress during macro execution.

**Progress display:**
- **Progress bar:** Visual percentage (0-100%)
- **Progress text:** Detailed status
  - Command count: "45/100 commands"
  - Time: "12s / 25s (~13s left)"
  - Current line number and total lines

**What you see during playback:**
1. Progress bar fills from left to right
2. Text updates show current position: "12/50 commands • 5s / 15s (~10s left)"
3. Status area shows current command: "Wait 2000ms", "Key: enter", "Type: hello"

**Benefits:**
- Know how far along playback is
- Estimate remaining time
- Confirm macro is executing correctly
- Easier to debug long macros

### 5. Signed APK Support

**What it is:** Release builds can now be digitally signed for distribution.

**For developers:**
- Keystore generation script: `android-recorder/keystore/generate-keystore.sh`
- Build signed release: `./gradlew assembleRelease`
- CI/CD ready: GitHub Actions workflow updated

**For users:**
- More secure APK installation
- Ready for Google Play Store submission
- Verifiable app authenticity

**Documentation:** See `android-recorder/keystore/README.md` for full setup instructions.

### 6. Enhanced Error Handling (Firmware)

**What's documented:** Comprehensive firmware enhancement recommendations in `FIRMWARE_ENHANCEMENTS.md`

**Includes:**
- Error code system for structured responses
- Command validation before execution
- Status LED feedback patterns
- Macro execution error recovery
- Timeout and watchdog support
- Memory usage monitoring
- SD card error recovery
- Debug logging system

**Status:** Design phase - implementation planned for firmware v0.6

## Settings You Can Change

### Playback Speed
- Available speeds: 0.5x, 1x, 1.5x, 2x, 3x
- Located in Playback screen
- Affects all delays and typing speed

### Auto-Reconnect
- Always enabled (up to 5 attempts)
- No user configuration needed
- Automatically engages on connection loss

### Batch Mode
- Toggle in File Manager via "Select Multiple" button
- Persists until "Cancel" pressed or action completed

## Troubleshooting

### "Reconnecting in Xs (attempt N/5)"
- **Cause:** Lost connection to PWDongle
- **Solution:** Wait for automatic reconnection, or move closer to device
- **If it fails:** Check PWDongle is powered on and in Bluetooth mode

### "Validation failed: ..."
- **Cause:** Macro has syntax errors
- **Solution:** Edit macro to fix reported errors, or tap "Play Anyway" if errors are acceptable

### "Batch playback complete: 0/5 macros"
- **Cause:** Selected macros failed to load
- **Solution:** Check file names are valid and files exist on phone storage

### Progress bar stuck at same position
- **Cause:** Macro has long delay or is waiting for command completion
- **Solution:** Check current status text for details (e.g., "Wait 30000ms")

### Checkboxes not appearing in File Manager
- **Cause:** Batch mode not activated
- **Solution:** Tap "Select Multiple" button at top of File Manager

## Tips and Best Practices

### Using Validation Effectively
1. Always review warnings before playing macros from unknown sources
2. Check estimated duration matches your expectations
3. Use "Play Anyway" cautiously - fix errors when possible

### Batch Operations
1. Test macros individually before batch playback
2. Order matters - files play in selection order
3. Use batch delete to clean up after bulk recordings

### Progress Monitoring
1. Long delays show in status: "Wait 10000ms" means 10-second pause coming
2. Remaining time is estimate - actual may vary based on system performance
3. Stop button works during delays - no need to wait for completion

### Reconnection
1. First reconnect attempt happens after 1 second
2. If phone screen turns off, reconnection may pause until screen wakes
3. Manual disconnect (Settings → Disconnect) disables auto-reconnect

## Known Limitations

1. **Batch playback** navigates to playback screen for each file (not inline)
2. **Progress time estimates** are approximations based on command parsing
3. **Validation** doesn't catch all logic errors (e.g., infinite loops in advanced scripts)
4. **Reconnection** requires Bluetooth to be enabled on phone
5. **Batch operations** work on phone storage only (device SD files not supported yet)

## Keyboard Shortcuts (Future)

Not yet implemented - planned for v2.2:
- Ctrl+A: Select all (in batch mode)
- Escape: Exit batch mode
- Space: Toggle selection (when file focused)

## Version History

### v2.1 (January 2026)
- ✅ Automatic BLE reconnection with exponential backoff
- ✅ Macro validation before playback
- ✅ Batch file operations (select, delete, play)
- ✅ Playback progress indicators
- ✅ Signed APK build support
- ✅ Firmware enhancement documentation

### v2.0 (Previous)
- Macro recording from smartphone
- File browser with phone/device storage
- Playback speed control
- Auto-navigation after playback

### v1.0 (Initial)
- BLE device connection
- Password management
- Basic macro playback
- Input relay (keyboard/mouse)

## Support

For issues or feature requests:
1. Check TESTING_GUIDE.md for known issues
2. Review logs: Android Logcat filtered for "PWDongle"
3. Check firmware version: PWDongle displays version on boot
4. Create GitHub issue with logs and steps to reproduce
