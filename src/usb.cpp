#include <Arduino.h>
#include <USB.h>
#include <USBHIDKeyboard.h>
#include <SD.h>
#include <SD_MMC.h>
#include <SPI.h>
#include <stdio.h>
#include "usb.h"
#include "security.h"
#include "display.h"

// External references (defined in main.cpp)
extern USBHIDKeyboard Keyboard;

// Runtime USB mode
int currentUSBMode = MODE_HID;

// Command processing state
enum SerialCmdState {
  CMD_IDLE = 0,
  CMD_PWUPDATE_WAIT_CODE,
  CMD_PWUPDATE_WAIT_DATA,
  CMD_RETRIEVEPW_WAIT_CODE,
  CMD_CHANGELOGIN_WAIT_OLD,
  CMD_CHANGELOGIN_WAIT_NEW
};

static SerialCmdState serialState = CMD_IDLE;

// Temp storage for operations
static int candidateOldCode[4];

// Helpers
static void parseFourDigitString(const String &s, int out[4]) {
  for (int i = 0; i < 4; ++i) out[i] = 0;
  int idx = 0;
  for (size_t i = 0; i < s.length() && idx < 4; ++i) {
    char c = s.charAt(i);
    if (c >= '0' && c <= '9') {
      out[idx++] = c - '0';
    }
  }
}

// Storage and security APIs
#include "storage.h"
#include "bluetooth.h"
// setCorrectCode and isAccessCode are declared in security.h

void resetSerialState() {
  serialState = CMD_IDLE;
}

// BLE command processor (mirrors serial commands + keystroke relay)
void processBLELine(const String& rawLine) {
  String line = rawLine;
  line.trim();
  
  Serial.print("BLE CMD: ");
  Serial.println(line);
  
  if (line.length() == 0) return;

  // Keystroke relay commands (dual-mode)
  if (line.startsWith("TYPE:")) {
    String text = line.substring(5);
    Serial.print("TYPE command - text: ");
    Serial.println(text);
    relayTypeToPC(text);
    Serial.println("About to send BLE response");
    sendBLEResponse("OK: Typed to PC");
    Serial.println("BLE response sent");
    return;
  }
  if (line.startsWith("KEY:")) {
    String keyName = line.substring(4);
    Serial.print("KEY command - key: ");
    Serial.println(keyName);
    relayKeyToPC(keyName);
    Serial.println("About to send BLE response");
    sendBLEResponse("OK: Key sent to PC");
    Serial.println("BLE response sent");
    return;
  }

  // Regular BLE commands
  if (serialState == CMD_IDLE) {
    if (line.equalsIgnoreCase("HELP")) {
      sendBLEResponse("OK: Commands:");
      sendBLEResponse("  PWUPDATE - update passwords (requires login auth)");
      sendBLEResponse("  RETRIEVEPW - retrieve stored passwords (requires login auth)");
      sendBLEResponse("  CHANGELOGIN - change the 4-digit login code");
      sendBLEResponse("  TYPE:text - type text on connected PC");
      sendBLEResponse("  KEY:enter - send key to PC (enter,backspace,delete,tab,escape)");
      sendBLEResponse("  KEY:ctrl+c - send key combo (ctrl+,alt+,shift+)");
      sendBLEResponse("Usage: send command, then follow prompts from device");
      showHelpScreen();
      return;
    }
    if (line.equalsIgnoreCase("ABOUT")) {
      char buf[128];
      snprintf(buf, sizeof(buf), "OK: PWDongle firmware v0.1 - built %s %s", __DATE__, __TIME__);
      sendBLEResponse(buf);
      sendBLEResponse("Board: ESP32-S3");
      sendBLEResponse("Library: TFT_eSPI + BLE");
      sendBLEResponse("Mode: BLE (with USB HID relay)");
      sendBLEResponse("Login code: **** (masked)");
      bool persisted = isLoginCodePersisted();
      sendBLEResponse(persisted ? "Persisted: Yes" : "Persisted: No");
      return;
    }
    if (line.equalsIgnoreCase("PWUPDATE")) {
      sendBLEResponse("OK: Enter the login code to authorize PW update");
      serialState = CMD_PWUPDATE_WAIT_CODE;
      return;
    }
    if (line.equalsIgnoreCase("RETRIVEPW") || line.equalsIgnoreCase("RETRIEVEPW")) {
      sendBLEResponse("OK: Enter the login code");
      serialState = CMD_RETRIEVEPW_WAIT_CODE;
      return;
    }
    if (line.equalsIgnoreCase("CHANGELOGIN")) {
      sendBLEResponse("OK: Enter current login code.");
      serialState = CMD_CHANGELOGIN_WAIT_OLD;
      return;
    }
    sendBLEResponse("ERR: Unknown command");
    return;
  }

  // State-specific handling
  if (serialState == CMD_PWUPDATE_WAIT_CODE) {
    int code[4];
    parseFourDigitString(line, code);
    if (isAccessCode(code, 4)) {
      sendBLEResponse("OK: Authorized. Please send NAME,DATA");
      serialState = CMD_PWUPDATE_WAIT_DATA;
    } else {
      sendBLEResponse("ERR: Incorrect code");
      resetSerialState();
    }
    return;
  }

  if (serialState == CMD_PWUPDATE_WAIT_DATA) {
    parseAndStoreData(line);
    sendBLEResponse("OK: Passwords updated");
    resetSerialState();
    return;
  }

  if (serialState == CMD_RETRIEVEPW_WAIT_CODE) {
    int code[4];
    parseFourDigitString(line, code);
    if (isAccessCode(code, 4)) {
      int count = getDeviceCount();
      for (int i = 0; i < count; i++) {
        sendBLECSV(getDeviceName(i), getDevicePassword(i));
      }
      sendBLEResponse("OK: Retrieved passwords");
      resetSerialState();
    } else {
      sendBLEResponse("ERR: Incorrect code");
      resetSerialState();
    }
    return;
  }

  if (serialState == CMD_CHANGELOGIN_WAIT_OLD) {
    parseFourDigitString(line, candidateOldCode);
    if (isAccessCode(candidateOldCode, 4)) {
      sendBLEResponse("OK: Code accepted. Please enter the new code.");
      serialState = CMD_CHANGELOGIN_WAIT_NEW;
    } else {
      sendBLEResponse("ERR: Incorrect code");
      resetSerialState();
    }
    return;
  }

  if (serialState == CMD_CHANGELOGIN_WAIT_NEW) {
    int newCode[4];
    parseFourDigitString(line, newCode);
    setCorrectCodePersist(newCode, 4);
    sendBLEResponse("OK: New login code set");
    resetSerialState();
    return;
  }
}

void processSerialLine(const String& rawLine) {
  String line = rawLine;
  line.trim();
  
  if (line.length() == 0) return;

  if (serialState == CMD_IDLE) {
    if (line.equalsIgnoreCase("HELP")) {
      sendSerialResponse("OK: Commands:");
      sendSerialResponse("  PWUPDATE - update passwords (requires login auth)");
      sendSerialResponse("  RETRIEVEPW - retrieve stored passwords (requires login auth)");
      sendSerialResponse("  CHANGELOGIN - change the 4-digit login code");
      sendSerialResponse("Usage: send command, then follow prompts from device");
      // Also show the same help on the TFT display
      showHelpScreen();
      return;
    }
    if (line.equalsIgnoreCase("ABOUT")) {
      char buf[128];
      snprintf(buf, sizeof(buf), "OK: PWDongle firmware v0.1 - built %s %s", __DATE__, __TIME__);
      sendSerialResponse(buf);
      sendSerialResponse("Board: ESP32-S3");
      sendSerialResponse("Library: TFT_eSPI");

      // Report masked login code and whether it's persisted
      extern int correctCode[4];
      char maskBuf[16];
      // Always mask the digits for security
      snprintf(maskBuf, sizeof(maskBuf), "****");
      sendSerialResponse("Login code: **** (masked)");
      bool persisted = isLoginCodePersisted();
      if (persisted) {
        sendSerialResponse("Persisted: Yes");
      } else {
        sendSerialResponse("Persisted: No");
      }
      return;
    }
    if (line.equalsIgnoreCase("PWUPDATE")) {
      // Require authentication before allowing password update
      sendSerialResponse("OK: Enter the login code to authorize PW update");
      serialState = CMD_PWUPDATE_WAIT_CODE;
      return;
    }
    if (line.equalsIgnoreCase("RETRIVEPW") || line.equalsIgnoreCase("RETRIEVEPW")) {
      sendSerialResponse("OK: Enter the login code");
      serialState = CMD_RETRIEVEPW_WAIT_CODE;
      return;
    }
    if (line.equalsIgnoreCase("CHANGELOGIN")) {
      sendSerialResponse("OK: Enter current login code.");
      serialState = CMD_CHANGELOGIN_WAIT_OLD;
      return;
    }
    // Unknown command: ignore or echo
    sendSerialResponse("ERR: Unknown command");
    return;
  }

  // State-specific handling
  if (serialState == CMD_PWUPDATE_WAIT_CODE) {
    int code[4];
    parseFourDigitString(line, code);
    if (isAccessCode(code, 4)) {
      sendSerialResponse("OK: Authorized. Please send NAME,DATA");
      serialState = CMD_PWUPDATE_WAIT_DATA;
    } else {
      sendSerialResponse("ERR: Incorrect code");
      resetSerialState();
    }
    return;
  }

  if (serialState == CMD_PWUPDATE_WAIT_DATA) {
    // Expect CSV name,password pairs
    parseAndStoreData(line);
    sendSerialResponse("OK: Passwords updated");
    resetSerialState();
    return;
  }

  if (serialState == CMD_RETRIEVEPW_WAIT_CODE) {
    int code[4];
    parseFourDigitString(line, code);
    if (isAccessCode(code, 4)) {
      // Send stored pairs
      int count = getDeviceCount();
      for (int i = 0; i < count; i++) {
        sendSerialCSV(getDeviceName(i), getDevicePassword(i));
      }
      sendSerialResponse("OK: Retrieved passwords");
      resetSerialState();
    } else {
      sendSerialResponse("ERR: Incorrect code");
      resetSerialState();
    }
    return;
  }

  if (serialState == CMD_CHANGELOGIN_WAIT_OLD) {
    parseFourDigitString(line, candidateOldCode);
    if (isAccessCode(candidateOldCode, 4)) {
      sendSerialResponse("OK: Code accepted. Please enter the new code.");
      serialState = CMD_CHANGELOGIN_WAIT_NEW;
    } else {
      sendSerialResponse("ERR: Incorrect code");
      resetSerialState();
    }
    return;
  }

  if (serialState == CMD_CHANGELOGIN_WAIT_NEW) {
    int newCode[4];
    parseFourDigitString(line, newCode);
    setCorrectCodePersist(newCode, 4);
    sendSerialResponse("OK: New login code set");
    resetSerialState();
    return;
  }
}


void startUSBMode(int mode) {
  if (mode == MODE_HID) {
    // HID Keyboard mode
    USB.manufacturerName("Narcean Technologies");
    USB.serialNumber("SN-0000001");
    USB.productName("PWDongle v0.1 HID");
    Keyboard.begin();
    USB.begin();
    currentUSBMode = MODE_HID;

  } else if (mode == MODE_CDC) {
    // CDC Serial mode
    USB.manufacturerName("Narcean Technologies");
    USB.serialNumber("SN-0000001");
    USB.productName("PWDongle v0.1 CDC");
   
    Serial.begin(115200);
    Serial.setRxBufferSize(BUF_SIZE);
    Serial.setTxBufferSize(BUF_SIZE);
    currentUSBMode = MODE_CDC;
    
  }
}

void sendPassword(String password) {
  showStartupMessage("Starting HID MODE...");
  delay(1000);
  startUSBMode(MODE_HID);

  Keyboard.println(password); // Types the string and presses Enter

  // Visual feedback on screen
  showPasswordSentScreen(password);
  drawMenu(); // Return to menu display
}

bool isSerialDataAvailable() {
  return Serial.available() > 0;
}

String readSerialData() {
  String receivedData = Serial.readStringUntil('\n');
  return receivedData;
}

void sendSerialResponse(const char* message) {
  Serial.println(message);
}

void sendSerialCSV(const String& name, const String& password) {
  Serial.print(name);
  Serial.print(",");
  Serial.println(password);
}

#ifndef SD_CS_PIN
#define SD_CS_PIN 5
#endif

static bool sdUseMMC = false;
static bool sdReady = false;
static SPIClass sdSPI(HSPI);

static bool ensureSDReady() {
  // Try SD_MMC with known board pins first; if that fails, try SPI SD.
  if (sdReady) return true;

  // Configure SD_MMC pins for ESP32-S3-LCD-1.47 (4-bit bus)
  const int sdClk = 14;
  const int sdCmd = 15;
  const int sdD0  = 16;
  const int sdD1  = 18;
  const int sdD2  = 17;
  const int sdD3  = 21;
  SD_MMC.setPins(sdClk, sdCmd, sdD0, sdD1, sdD2, sdD3);
  // Try 4-bit mode (mode1bit = false)
  if (SD_MMC.begin("/sdcard", false)) {
    sdUseMMC = true;
    sdReady = true;
    return true;
  }

  // Fallback to SPI SD on HSPI with candidate pins
  struct SpiPins { int cs, miso, mosi, sclk; };
  const SpiPins candidates[] = {
    {39, 38, 45, 40}, // Common on ESP32-S3 LCD boards: CS=39, MISO=38, MOSI=45, SCLK=40
    {5,  38, 45, 40}, // If CS wired to 5
  };

  for (auto cfg : candidates) {
    sdSPI.end();
    sdSPI.begin(cfg.sclk, cfg.miso, cfg.mosi, cfg.cs);
    if (SD.begin(cfg.cs, sdSPI, 25000000)) {
      sdUseMMC = false;
      sdReady = true;
      return true;
    }
  }
  return false;
}

bool typeTextFileFromSD(const String& baseName) {
  // Ensure HID is active for typing
  startUSBMode(MODE_HID);

  if (!ensureSDReady()) {
    showStartupMessage("SD init failed");
    delay(800);
    return false;
  }

  String filename = "/" + baseName + ".txt";
  File f;
  // Open via whichever backend was initialized
  if (sdUseMMC) {
    f = SD_MMC.open(filename.c_str(), FILE_READ);
  } else {
    f = SD.open(filename.c_str(), FILE_READ);
  }

  if (!f) {
    showStartupMessage("File not found");
    delay(800);
    return false;
  }

  // Raw typing: send every byte as-is, including control chars (CR, LF)
  showStartupMessage("Typing file...");
  delay(300);

  const size_t BUF_SZ = 256;
  uint8_t buf[BUF_SZ];
  while (true) {
    int n = f.read(buf, BUF_SZ);
    if (n <= 0) break;
    for (int i = 0; i < n; ++i) {
      Keyboard.write(buf[i]);
      delay(3); // small pacing to avoid overruns
    }
  }
  f.close();

  showStartupMessage("File typed");
  delay(600);
  return true;
}
void listSDTextFiles(String fileList[15], int& count) {
  // Scan SD for .txt files and populate fileList (up to 15)
  count = 0;
  if (!ensureSDReady()) return;

  File root;
  if (sdUseMMC) {
    root = SD_MMC.open("/");
  } else {
    root = SD.open("/");
  }
  if (!root || !root.isDirectory()) return;

  File file;
  while (file = root.openNextFile()) {
    if (!file.isDirectory()) {
      String name = String(file.name());
      // Check if it ends with .txt
      if (name.endsWith(".txt")) {
        // Extract the base name (remove .txt)
        String base = name.substring(0, name.length() - 4);
        fileList[count] = base;
        count++;
        if (count >= 15) break;
      }
    }
    file.close();
  }
  root.close();

  // Sort the collected basenames numerically if possible, otherwise lexicographically
  auto isDigits = [](const String& s) {
    if (s.length() == 0) return false;
    for (size_t i = 0; i < s.length(); ++i) {
      char c = s[i];
      if (c < '0' || c > '9') return false;
    }
    return true;
  };

  for (int i = 0; i < count; ++i) {
    for (int j = i + 1; j < count; ++j) {
      bool di = isDigits(fileList[i]);
      bool dj = isDigits(fileList[j]);
      bool doSwap = false;
      if (di && dj) {
        if (fileList[i].toInt() > fileList[j].toInt()) doSwap = true;
      } else {
        if (fileList[i].compareTo(fileList[j]) > 0) doSwap = true;
      }
      if (doSwap) {
        String tmp = fileList[i];
        fileList[i] = fileList[j];
        fileList[j] = tmp;
      }
    }
  }
}
