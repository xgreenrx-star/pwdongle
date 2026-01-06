# Firmware Enhancement Recommendations

## Current Status
PWDongle firmware v0.5 is functional with macro recording, BLE control, and script execution. The following enhancements would improve error handling and user feedback.

## Proposed Improvements

### 1. Error Code System

Add standardized error codes for BLE responses:

```cpp
// Error codes
enum ErrorCode {
    ERR_SUCCESS = 0,
    ERR_SD_NOT_AVAILABLE = 1,
    ERR_FILE_NOT_FOUND = 2,
    ERR_INVALID_COMMAND = 3,
    ERR_AUTH_REQUIRED = 4,
    ERR_AUTH_FAILED = 5,
    ERR_FILE_CREATE_FAILED = 6,
    ERR_FILE_WRITE_FAILED = 7,
    ERR_ALREADY_RECORDING = 8,
    ERR_NOT_RECORDING = 9,
    ERR_INVALID_PIN = 10
};

void sendBLEError(ErrorCode code, const String& message) {
    sendBLEResponse("ERROR:" + String(code) + ":" + message);
}

void sendBLESuccess(const String& message = "") {
    if (message.length() > 0) {
        sendBLEResponse("OK:" + message);
    } else {
        sendBLEResponse("OK");
    }
}
```

### 2. Command Validation

Add input validation before execution:

```cpp
bool validateKeyCommand(const String& key) {
    // Check if key is valid
    const char* validKeys[] = {
        "enter", "esc", "tab", "space", ...
    };
    // Return true if valid
}

bool validateFilename(const String& filename) {
    // Check for valid characters, length, extension
    if (filename.length() == 0 || filename.length() > 64) return false;
    if (filename.indexOf("/") >= 0 || filename.indexOf("\\") >= 0) return false;
    return true;
}
```

### 3. Status LED Feedback

Use onboard LED to indicate states:
- **Solid**: Connected and ready
- **Slow blink (1Hz)**: Recording macro
- **Fast blink (5Hz)**: Error condition
- **Off**: Not connected or in storage mode

```cpp
enum LEDPattern {
    LED_OFF,
    LED_SOLID,
    LED_SLOW_BLINK,
    LED_FAST_BLINK
};

void setLEDPattern(LEDPattern pattern);
void updateLED(); // Call in loop()
```

### 4. Macro Execution Error Recovery

Enhance macro playback with better error handling:

```cpp
bool executeMacroLine(const String& line, String& error) {
    if (line.startsWith("{{KEY:")) {
        String key = extractArg(line);
        if (!validateKeyCommand(key)) {
            error = "Invalid key: " + key;
            return false;
        }
        // Execute command
        return true;
    }
    // ... other commands
}

void playMacroFile(const String& filename) {
    File f = SD.open("/" + filename);
    if (!f) {
        sendBLEError(ERR_FILE_NOT_FOUND, filename);
        return;
    }
    
    int lineNum = 0;
    while (f.available()) {
        String line = f.readStringUntil('\n');
        lineNum++;
        
        String error;
        if (!executeMacroLine(line, error)) {
            sendBLEError(ERR_INVALID_COMMAND, "Line " + String(lineNum) + ": " + error);
            // Continue or stop?
        }
    }
    
    f.close();
    sendBLESuccess("Macro complete: " + String(lineNum) + " lines");
}
```

### 5. Timeout and Watchdog

Add timeouts for operations:

```cpp
const unsigned long CMD_TIMEOUT = 30000; // 30 seconds
unsigned long cmdStartTime = 0;

void startCommand() {
    cmdStartTime = millis();
}

bool checkTimeout() {
    if (millis() - cmdStartTime > CMD_TIMEOUT) {
        sendBLEError(ERR_TIMEOUT, "Command timed out");
        resetSerialState();
        return true;
    }
    return false;
}
```

### 6. Memory Usage Monitoring

Add heap monitoring:

```cpp
void reportSystemStatus() {
    sendBLEResponse("STATUS:heap=" + String(ESP.getFreeHeap()) + 
                    ",psram=" + String(ESP.getFreePsram()) +
                    ",uptime=" + String(millis() / 1000) +
                    ",recording=" + String(isRecording ? "yes" : "no"));
}
```

### 7. Detailed Response Protocol

Enhance response format:

```
OK:COMMAND_NAME:RESULT_DATA
ERROR:CODE:MESSAGE
STATUS:KEY1=VAL1,KEY2=VAL2,...
PROGRESS:CURRENT/TOTAL:PERCENT
```

Examples:
- `OK:LIST:file1.txt,file2.txt,file3.txt`
- `ERROR:2:File not found: missing.txt`
- `STATUS:recording=yes,file=test.txt,duration=12s`
- `PROGRESS:45/100:45%`

### 8. Safe Mode Boot

Add boot sequence check:
- If BOOT button held for 10+ seconds during countdown: Enter safe mode
- Safe mode: Disable all macro auto-execution, BLE read-only mode
- Display "SAFE MODE" in red

### 9. SD Card Error Recovery

Improve SD card handling:

```cpp
bool reinitSD() {
    SD.end();
    delay(100);
    return SD.begin(SD_CS_PIN);
}

bool ensureSDHealthy() {
    if (!SD.begin(SD_CS_PIN)) {
        if (reinitSD()) {
            sendBLEResponse("WARN: SD card reinitialized");
            return true;
        }
        sendBLEError(ERR_SD_NOT_AVAILABLE, "SD card failed");
        return false;
    }
    return true;
}
```

### 10. Logging System

Add optional debug logging to SD card:

```cpp
bool loggingEnabled = false;
File logFile;

void logDebug(const String& message) {
    if (!loggingEnabled) return;
    
    if (!logFile) {
        logFile = SD.open("/pwdongle.log", FILE_APPEND);
    }
    
    if (logFile) {
        logFile.print(millis());
        logFile.print(": ");
        logFile.println(message);
        logFile.flush();
    }
}

void enableLogging() {
    loggingEnabled = true;
    sendBLESuccess("Logging enabled to /pwdongle.log");
}
```

## Implementation Priority

**High Priority:**
1. Error code system (immediate feedback improvement)
2. Command validation (prevent crashes)
3. Detailed response protocol (better app integration)

**Medium Priority:**
4. Status LED feedback (visual confirmation)
5. Macro execution error recovery (robustness)
6. Safe mode boot (safety)

**Low Priority:**
7. Timeout and watchdog (edge case handling)
8. Memory monitoring (diagnostics)
9. SD card error recovery (rare failure handling)
10. Logging system (debugging)

## Notes

These enhancements are NOT breaking changes - existing Android app would continue to work while gaining benefits from improved error messages and validation.

Implementation can be incremental - each improvement is independent and can be added separately.
