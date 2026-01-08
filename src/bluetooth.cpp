#include "bluetooth.h"
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <USBHIDKeyboard.h>
#include <SD.h>
#include <SD_MMC.h>
#include "display.h"

// Nordic UART Service (NUS) UUIDs - widely supported by BLE terminal apps
#define SERVICE_UUID           "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_RX "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"  // Phone writes to this
#define CHARACTERISTIC_UUID_TX "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"  // Phone reads from this

// External reference to Keyboard object
extern USBHIDKeyboard Keyboard;

// External SD card status
extern bool sdUseMMC;
extern bool sdReady;

static BLEServer *pServer = nullptr;
static BLECharacteristic *pTxCharacteristic = nullptr;
static BLECharacteristic *pRxCharacteristic = nullptr;
static bool deviceConnected = false;
static String rxBuffer = "";
int currentBLEMode = 0;  // 0 = off, 1 = active
int dualModeActive = 0;  // 0 = BLE commands only, 1 = BLE + USB HID dual mode

class ServerCallbacks: public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) {
    deviceConnected = true;
  }
  
  void onDisconnect(BLEServer* pServer) {
    deviceConnected = false;
    // Restart advertising so phone can reconnect
    BLEDevice::startAdvertising();
  }
};

class RxCallbacks: public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pCharacteristic) {
    std::string rxValue = pCharacteristic->getValue();
    if (rxValue.length() > 0) {
      for (size_t i = 0; i < rxValue.length(); i++) {
        rxBuffer += (char)rxValue[i];
      }
    }
  }
};

// Helper to type text via USB HID (for dual-mode keystroke relay)
static void typeViaHID(const String& text) {
  Serial.print("typeViaHID called with: ");
  Serial.println(text);
  Serial.print("dualModeActive = ");
  Serial.println(dualModeActive);
  
  for (int i = 0; i < text.length(); i++) {
    char c = text[i];
    if (c == '\n') {
      Keyboard.press(KEY_RETURN);
      delay(50);
      Keyboard.release(KEY_RETURN);
    } else if (c == '\t') {
      Keyboard.press(KEY_TAB);
      delay(50);
      Keyboard.release(KEY_TAB);
    } else {
      Keyboard.print(String(c));
    }
    delay(10);  // Small delay between characters
  }
}

// Helper to send special keys via USB HID
static void sendKeyViaHID(const String& keyName) {
  String key = keyName;
  key.toLowerCase();
  
  Serial.print("sendKeyViaHID called with: ");
  Serial.println(key);
  
  // Single keys
  if (key == "enter" || key == "return") {
    Keyboard.press(KEY_RETURN);
    delay(50);
    Keyboard.release(KEY_RETURN);
  }
  else if (key == "backspace") {
    Keyboard.press(KEY_BACKSPACE);
    delay(50);
    Keyboard.release(KEY_BACKSPACE);
  }
  else if (key == "delete") {
    Keyboard.press(KEY_DELETE);
    delay(50);
    Keyboard.release(KEY_DELETE);
  }
  else if (key == "tab") {
    Keyboard.press(KEY_TAB);
    delay(50);
    Keyboard.release(KEY_TAB);
  }
  else if (key == "escape") {
    Keyboard.press(KEY_ESC);
    delay(50);
    Keyboard.release(KEY_ESC);
  }
  else if (key == "up") {
    Keyboard.press(KEY_UP_ARROW);
    delay(50);
    Keyboard.release(KEY_UP_ARROW);
  }
  else if (key == "down") {
    Keyboard.press(KEY_DOWN_ARROW);
    delay(50);
    Keyboard.release(KEY_DOWN_ARROW);
  }
  else if (key == "left") {
    Keyboard.press(KEY_LEFT_ARROW);
    delay(50);
    Keyboard.release(KEY_LEFT_ARROW);
  }
  else if (key == "right") {
    Keyboard.press(KEY_RIGHT_ARROW);
    delay(50);
    Keyboard.release(KEY_RIGHT_ARROW);
  }
  else if (key == "home") {
    Keyboard.press(KEY_HOME);
    delay(50);
    Keyboard.release(KEY_HOME);
  }
  else if (key == "end") {
    Keyboard.press(KEY_END);
    delay(50);
    Keyboard.release(KEY_END);
  }
  else if (key == "pageup") {
    Keyboard.press(KEY_PAGE_UP);
    delay(50);
    Keyboard.release(KEY_PAGE_UP);
  }
  else if (key == "pagedown") {
    Keyboard.press(KEY_PAGE_DOWN);
    delay(50);
    Keyboard.release(KEY_PAGE_DOWN);
  }
  // Modifier combinations
  else if (key.startsWith("ctrl+")) {
    String char_key = key.substring(5);
    Keyboard.press(KEY_LEFT_CTRL);
    Keyboard.press(char_key[0]);
    delay(50);
    Keyboard.release(char_key[0]);
    Keyboard.release(KEY_LEFT_CTRL);
  }
  else if (key.startsWith("alt+")) {
    String char_key = key.substring(4);
    Keyboard.press(KEY_LEFT_ALT);
    Keyboard.press(char_key[0]);
    delay(50);
    Keyboard.release(char_key[0]);
    Keyboard.release(KEY_LEFT_ALT);
  }
  else if (key.startsWith("shift+")) {
    String char_key = key.substring(6);
    Keyboard.press(KEY_LEFT_SHIFT);
    Keyboard.press(char_key[0]);
    delay(50);
    Keyboard.release(char_key[0]);
    Keyboard.release(KEY_LEFT_SHIFT);
  }
}

void startBLEMode() {
  // Initialize BLE with device name
  BLEDevice::init("PWDongle");
  
  // Create BLE Server
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new ServerCallbacks());
  
  // Create BLE Service (Nordic UART Service)
  BLEService *pService = pServer->createService(SERVICE_UUID);
  
  // TX characteristic (device transmits to phone)
  pTxCharacteristic = pService->createCharacteristic(
    CHARACTERISTIC_UUID_TX,
    BLECharacteristic::PROPERTY_NOTIFY
  );
  pTxCharacteristic->addDescriptor(new BLE2902());
  
  // RX characteristic (device receives from phone)
  pRxCharacteristic = pService->createCharacteristic(
    CHARACTERISTIC_UUID_RX,
    BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR
  );
  pRxCharacteristic->setCallbacks(new RxCallbacks());
  
  // Start the service
  pService->start();
  
  // Configure and start advertising
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMinInterval(0x20);  // 20ms
  pAdvertising->setMaxInterval(0x40);  // 40ms
  
  // Start advertising
  BLEDevice::startAdvertising();
  
  currentBLEMode = 1;
  dualModeActive = 1;  // Enable dual-mode: BLE + USB HID
  
  Serial.println("BLE Started - Advertising as: PWDongle");
  Serial.println("Dual-mode active: BLE commands + USB HID keyboard relay");
}

void stopBLEMode() {
  if (pServer && deviceConnected) {
    pServer->disconnect(pServer->getConnId());
  }
  BLEDevice::deinit(true);
  currentBLEMode = 0;
  deviceConnected = false;
  rxBuffer = "";
}

bool isBLEDataAvailable() {
  if (currentBLEMode == 0) return false;
  return rxBuffer.indexOf('\n') >= 0;
}

String readBLEData() {
  int newlineIdx = rxBuffer.indexOf('\n');
  if (newlineIdx >= 0) {
    String line = rxBuffer.substring(0, newlineIdx);
    rxBuffer = rxBuffer.substring(newlineIdx + 1);
    return line;
  }
  return "";
}

void sendBLEResponse(const String& msg) {
  if (deviceConnected && pTxCharacteristic && currentBLEMode == 1) {
    String msgWithNewline = msg + "\n";
    
    // BLE can only send ~20 bytes at a time (MTU size), so chunk if needed
    const int chunkSize = 20;
    int pos = 0;
    while (pos < msgWithNewline.length()) {
      int len = min(chunkSize, (int)(msgWithNewline.length() - pos));
      String chunk = msgWithNewline.substring(pos, pos + len);
      pTxCharacteristic->setValue(chunk.c_str());
      pTxCharacteristic->notify();
      pos += len;
    }
  }
}

void sendBLECSV(const String& name, const String& password) {
  if (deviceConnected && pTxCharacteristic && currentBLEMode == 1) {
    String csv = name + "," + password + "\n";
    
    // Chunk if needed
    const int chunkSize = 20;
    int pos = 0;
    while (pos < csv.length()) {
      int len = min(chunkSize, (int)(csv.length() - pos));
      String chunk = csv.substring(pos, pos + len);
      pTxCharacteristic->setValue(chunk.c_str());
      pTxCharacteristic->notify();
      pos += len;
    }
  }
}

bool isBLEConnected() {
  return deviceConnected && currentBLEMode == 1;
}

String getBLEDeviceName() {
  return "PWDongle";
}

// Public functions for BLE command processor to use
void relayTypeToPC(const String& text) {
  Serial.print("relayTypeToPC called, dualModeActive=");
  Serial.println(dualModeActive);
  if (dualModeActive) {
    typeViaHID(text);
    Serial.println("Text sent via HID");
  } else {
    Serial.println("dualModeActive is 0, skipping");
  }
}

void relayKeyToPC(const String& keyName) {
  Serial.print("relayKeyToPC called, dualModeActive=");
  Serial.println(dualModeActive);
  if (dualModeActive) {
    sendKeyViaHID(keyName);
    Serial.println("Key sent via HID");
  } else {
    Serial.println("dualModeActive is 0, skipping");
  }
}

// Macro recording functionality
bool isRecording = false;
String recordingFilename = "";
File recordingFile;
unsigned long recordingStartTime = 0;
unsigned long lastActionTime = 0;

void startMacroRecording(const String& filename) {
  if (isRecording) {
    stopMacroRecording();
  }
  
  recordingFilename = filename;
  
  // Ensure filename has .txt extension
  if (!recordingFilename.endsWith(".txt")) {
    recordingFilename += ".txt";
  }
  
  // Ensure SD card is initialized before recording
  // This is defined in usb.cpp but we need extern access
  extern bool ensureSDReadyForRecording();
  if (!ensureSDReadyForRecording()) {
    sendBLEResponse("ERROR: SD card not available");
    Serial.println("SD card initialization failed");
    return;
  }
  
  // Open file for writing
  String filepath = "/" + recordingFilename;
  
  if (sdUseMMC) {
    recordingFile = SD_MMC.open(filepath.c_str(), FILE_WRITE);
  } else {
    recordingFile = SD.open(filepath.c_str(), FILE_WRITE);
  }
  
  if (!recordingFile) {
    sendBLEResponse("ERROR: Cannot create file on SD card");
    Serial.println("Failed to create recording file");
    return;
  }
  
  isRecording = true;
  recordingStartTime = millis();
  lastActionTime = recordingStartTime;
  
  // Display recording screen
  showRecordingScreen(recordingFilename);
  
  sendBLEResponse("OK: Recording started to " + recordingFilename);
  Serial.println("Macro recording started: " + recordingFilename);
}

void stopMacroRecording() {
  if (!isRecording) {
    sendBLEResponse("ERROR: Not currently recording");
    return;
  }
  
  if (recordingFile) {
    recordingFile.close();
  }
  
  isRecording = false;
  unsigned long duration = (millis() - recordingStartTime) / 1000;
  
  // Display completion screen
  showRecordingStopped(recordingFilename, duration);
  
  sendBLEResponse("OK: Recording saved to " + recordingFilename + " (" + String(duration) + "s)");
  Serial.println("Macro recording stopped. Duration: " + String(duration) + "s");
  
  recordingFilename = "";
}

void recordAction(const String& action) {
  if (!isRecording || !recordingFile) {
    return;
  }
  
  // Calculate delay since last action
  unsigned long currentTime = millis();
  unsigned long delaySinceLastAction = currentTime - lastActionTime;
  
  // Always write delay for accurate timing reproduction (even short delays)
  // This captures typing speed, pauses between keystrokes, etc.
  if (delaySinceLastAction > 0) {
    recordingFile.println("{{DELAY:" + String(delaySinceLastAction) + "}}");
  }
  
  // Write the action
  recordingFile.println(action);
  
  lastActionTime = currentTime;
  
  Serial.println("Recorded: " + action + " (delay: " + String(delaySinceLastAction) + "ms)");
}
