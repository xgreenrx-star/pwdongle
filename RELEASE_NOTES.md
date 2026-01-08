# PWDongle v0.5.1 Release Notes

## Highlights

- **Live Control Fix**: Fixed Live Control keyboard typing from Android app with OTG keyboard. Added support for `KEY:keyname_DOWN`/`KEY:keyname_UP` format by stripping suffixes. Removed 20ms BLE response delay for minimal latency real-time control.
- Macro Recording (Device): Start with `RECORD:<filename>`, stop with `STOPRECORD`. Automatic `{{DELAY:ms}}` timing captured (>50ms) and saved to SD in PWDongle macro format. Recording UI screens added for status and completion.
- Android Recorder (App): Added on-screen Keyboard and Touchpad buttons on the Recorder screen for quick access. Space key reliability fixed (maps text space to `KEY:space`). PIN entry is numeric-only; password entries disallow commas while allowing other special characters.
- Script Auto-Detection: Advanced Scripting, DuckyScript, and PWDongle Macro formats auto-detected for file playback from SD.
- Docs Refresh: Updated README, BLE usage, Testing Guide, SD integration docs, and added screenshot capture guidance/placeholders.
- CI/Release Automation: GitHub Actions builds the debug APK on tag push and attaches it to the GitHub Release.

## Install the Android Recorder

1. Download `app-debug.apk` from this release.
2. Enable installation from unknown sources on your Android device.
3. Install the APK and open the app.
4. Connect to PWDongle via BLE (Nordic UART Service) from the app.

## Usage Notes

- Macro recording via phone: `RECORD:test`, then send actions (Keyboard/Mouse/Text), then `STOPRECORD`. The file is saved on the device SD card and playable via the device file browser.
- On-screen keyboard: Use the Recorder screen button; space is sent reliably via key command. Text is chunked and newline-terminated over BLE.
- Password management: Names and passwords are accepted except for commas (`,`). PIN entry is strictly digits.

## Known Items

- Screenshots in docs are placeholders; PNGs will be added in a follow-up.
- The CI attaches the debug APK; a signed/release variant can be added later if needed.

## Changelog Summary

- v0.5 adds macro recording, recorder UI improvements, input validation, and documentation updates.
