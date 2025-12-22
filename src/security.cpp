#include <Arduino.h>
#include <Preferences.h>
#include "security.h"
#include "input.h"
#include "display.h"
#include "usb.h"
#include "input.h"
#include "storage.h"

// External references (defined in main.cpp)
extern Preferences prefs;
extern bool codeAccepted;
extern bool awaitingFileNumber;

// Access codes
int correctCode[4] = {1, 1, 2, 2};
int comModeCode[4] = {7, 2, 7, 3};
int bleModeCode[4] = {0, 0, 0, 0};
int fileModeCode[4] = {5, 5, 5, 0};
int mscModeCode[4] = {0, 0, 0, 1};

// External state from input.cpp
extern int enteredCode[4];
extern int digitIndex;
extern int currentDigit;

void checkCode() {
  // If we are awaiting a file number, interpret the entered code as the filename
  if (awaitingFileNumber) {
    // Build base name as four digits
    char buf[5];
    for (int i = 0; i < 4; i++) buf[i] = char('0' + enteredCode[i]);
    buf[4] = '\0';

    // Type the file via HID
    String base = String(buf);
    bool okType = typeTextFileFromSD(base);
    if (!okType) {
      // Show error feedback and pause briefly
      showStartupMessage("Typing failed");
      delay(800);
    } else {
      showStartupMessage("File typed");
      delay(600);
    }

    // Stay in file-number mode to allow another file entry
    resetInputState();
    showFileNumberPrompt();
    showDigitScreen();
    return;
  }

  bool ok = true;
  bool comMode = true;
  bool bleMode = true;
  bool fileMode = true;
  bool mscMode = true;

  for (int i = 0; i < 4; i++) {
    if (enteredCode[i] != correctCode[i]){
      ok = false;
    }
    if (enteredCode[i] != comModeCode[i]){
      comMode = false;
    }
    if (enteredCode[i] != bleModeCode[i]){
      bleMode = false;
    }
    if (enteredCode[i] != fileModeCode[i]){
      fileMode = false;
    }
    if (enteredCode[i] != mscModeCode[i]){
      mscMode = false;
    }
  }
  
  if (bleMode) {
    // Switch to BLE mode on next boot
    prefs.begin("BLE", false);
    prefs.putBool("bootToBLE", true);
    prefs.end();

    // Show confirmation
    showRebootScreen();
    ESP.restart();
  }

  if (fileMode) {
    // Enter file number entry mode
    awaitingFileNumber = true;
    resetInputState();
    showFileNumberPrompt();
    showDigitScreen();
    return;
  }

  if (mscMode) {
    // Switch to flash drive (MSC) mode on next boot
    setBootToMSC(true);
    showStartupMessage("Switching to flash drive mode");
    delay(1000);
    ESP.restart();
  }
  
  if (comMode) {
    // Switch to CDC mode on next boot
    prefs.begin("CDC", false);
    prefs.putBool("bootToCDC", true);
    prefs.end();

    // Show confirmation
    showRebootScreen();
    ESP.restart();
  }
  
  if (ok) {
    codeAccepted = true;
    loadPasswords();
    drawMenu();
  } else {
    // Wrong code - display error and reset input
    showWrongCodeScreen();
    
    // Reset input state (clears masked/accepted flags)
    resetInputState();
    showDigitScreen();
  }
}

bool validateCode(const int* code, size_t length) {
  if (length != 4) {
    return false;
  }
  
  for (int i = 0; i < 4; i++) {
    if (code[i] < 0 || code[i] > 9) {
      return false;
    }
  }
  return true;
}

bool isComModeCode(const int* code, size_t length) {
  if (length != 4) {
    return false;
  }
  
  for (int i = 0; i < 4; i++) {
    if (code[i] != comModeCode[i]) {
      return false;
    }
  }
  return true;
}

bool isAccessCode(const int* code, size_t length) {
  if (length != 4) {
    return false;
  }
  
  for (int i = 0; i < 4; i++) {
    if (code[i] != correctCode[i]) {
      return false;
    }
  }
  return true;
}

void setCorrectCode(const int* newCode, size_t length) {
  if (length == 4) {
    for (int i = 0; i < 4; i++) {
      correctCode[i] = newCode[i];
    }
  }
}

void loadCorrectCode() {
  // Load persisted correct code from Preferences namespace "SEC" if present
  prefs.begin("SEC", true); // read-only where possible
  if (prefs.isKey("code0") && prefs.isKey("code1") && prefs.isKey("code2") && prefs.isKey("code3")) {
    correctCode[0] = prefs.getInt("code0", correctCode[0]);
    correctCode[1] = prefs.getInt("code1", correctCode[1]);
    correctCode[2] = prefs.getInt("code2", correctCode[2]);
    correctCode[3] = prefs.getInt("code3", correctCode[3]);
  } else {
    // No persisted code found; write the default to NVS so future boots have it
    prefs.end();
    prefs.begin("SEC", false);
    prefs.putInt("code0", correctCode[0]);
    prefs.putInt("code1", correctCode[1]);
    prefs.putInt("code2", correctCode[2]);
    prefs.putInt("code3", correctCode[3]);
  }
  prefs.end();
}

void setPersistedCorrectCode(const int* newCode) {
  // Helper to persist the code to NVS
  prefs.begin("SEC", false);
  prefs.putInt("code0", newCode[0]);
  prefs.putInt("code1", newCode[1]);
  prefs.putInt("code2", newCode[2]);
  prefs.putInt("code3", newCode[3]);
  prefs.end();
}

// Persist when setting via API
void setCorrectCodePersist(const int* newCode, size_t length) {
  if (length == 4) {
    setCorrectCode(newCode, length);
    setPersistedCorrectCode(newCode);
  }
}

bool isLoginCodePersisted() {
  prefs.begin("SEC", true);
  bool ok = prefs.isKey("code0") && prefs.isKey("code1") && prefs.isKey("code2") && prefs.isKey("code3");
  prefs.end();
  return ok;
}

void setComModeCode(const int* newCode, size_t length) {
  if (length == 4) {
    for (int i = 0; i < 4; i++) {
      comModeCode[i] = newCode[i];
    }
  }
}
