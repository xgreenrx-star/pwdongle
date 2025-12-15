// Main delegator: keep `main.cpp` minimal and delegate functionality
#include <Arduino.h>
#include <TFT_eSPI.h>
#include <Preferences.h>
#include <USB.h>
#include <USBHIDKeyboard.h>

#include "display.h"
#include "input.h"
#include "security.h"
#include "storage.h"
#include "usb.h"
#include "bluetooth.h"

// Core shared objects and state (defined here, referenced by modules)
TFT_eSPI tft = TFT_eSPI();
Preferences prefs; // NVS storage
USBHIDKeyboard Keyboard;

String PASSWORDS[MAX_DEVICES];
String menuItems[MAX_DEVICES];
int MENU_ITEM_COUNT = 0; // will be set from storage on load

bool codeAccepted = false;

// Boot button configuration (also referenced in input.h)
#ifndef BOOT_BUTTON_PIN
#define BOOT_BUTTON_PIN 0
#endif

void setup() {
  tft.init();
  tft.setRotation(0); // Portrait
  tft.fillScreen(TFT_BLACK);
  delay(500);
  tft.fillScreen(TFT_BLUE);
  delay(500);
  tft.fillScreen(TFT_BLACK);
  
  showStartupMessage("Starting...");
  //delay(500);
  //Serial.println("Display startup complete");

  // Load persisted login code from NVS (if present)
  loadCorrectCode();

  // Configure the boot button early for countdown check
  pinMode(BOOT_BUTTON_PIN, INPUT_PULLUP);

  // 3-second countdown: default to BLE unless button pressed
  bool userWantsPinEntry = false;
  for (int countdown = 3; countdown > 0; countdown--) {
    showCountdown(countdown);
    unsigned long startWait = millis();
    while (millis() - startWait < 1000) {
      if (digitalRead(BOOT_BUTTON_PIN) == LOW) {
        userWantsPinEntry = true;
        break;
      }
      delay(10);
    }
    if (userWantsPinEntry) break;
  }

  // Check for explicit BLE mode boot flag (from entering 0000 code)
  prefs.begin("BLE", true);
  bool bootToBLE = prefs.getBool("bootToBLE", false);
  prefs.end();
  
  // Enter BLE mode if: countdown expired without button OR explicit flag set
  if (bootToBLE || !userWantsPinEntry) {
    // Clear explicit flag if it was set
    if (bootToBLE) {
      prefs.begin("BLE", false);
      prefs.putBool("bootToBLE", false);
      prefs.end();
    }
    
    // Initialize USB HID first (needed for TYPE/KEY relay to PC)
    startUSBMode(MODE_HID);
    
    // Start BLE
    startBLEMode();
    
    // Then show UI
    tft.fillScreen(TFT_GREEN);
    tft.setTextColor(TFT_BLACK, TFT_GREEN);
    tft.setTextSize(2);
    tft.setCursor(10, 40);
    tft.println("BLE ACTIVE");
    tft.setCursor(10, 70);
    tft.setTextSize(1);
    tft.println("");
    tft.println("Scan for:");
    tft.setTextSize(2);
    tft.println("  PWDongle");
    tft.setTextSize(1);
    tft.println("");
    tft.println("Using BLE terminal app:");
    tft.println("- Serial Bluetooth Term");
    tft.println("- nRF Connect");
    tft.println("- LightBlue (iOS)");
    tft.println("");
    tft.println("Advertising now...");
    
    // Exit BLE boot screen and continue to main loop
    // Don't set codeAccepted=true; instead, set a flag so loop knows we're in BLE-only mode
    // In the loop, BLE mode won't process HID button input
  }
  
  // Ensure CDC boot flag exists and handle CDC-mode boot
  initializeCDCFlag();
  if (getBootToCDC()) {
    // Clear flag for next boot and enter CDC mode
    setBootToCDC(false);
    showCDCReadyScreen();
    startUSBMode(MODE_CDC);
    
    // Wait briefly for incoming CSV data from host (non-blocking-ish)
    unsigned long start = millis();
    // Wait up to 5 minutes (300000 ms) to allow host to send CSV data
    const unsigned long timeout = 300000;
    while (millis() - start < timeout) {
      
      if (isSerialDataAvailable()) { //isSerialDataAvailable() is returning false
        tft.fillScreen(TFT_BROWN);
        String d = readSerialData();
        //parseAndStoreData(d);
        processSerialLine(d);
        //break;
      }
      delay(50);
    }
  }

  // Only show PIN entry UI when NOT in BLE mode
  if (currentUSBMode == MODE_HID && currentBLEMode == 0) {
  // Initial UI
    showInstructions();
    showDigitScreen();
  }
}

// -------------------------- MAIN LOOP ------------------------
void loop() {
  extern int currentUSBMode;
  
  // BLE mode takes priority - no HID input processing
  if (currentBLEMode == 1) {
    while (isBLEDataAvailable()) {
      String line = readBLEData();
      processBLELine(line);
    }
    delay(10);
    return;  // Don't process HID input in BLE mode
  }
  
  if (currentUSBMode == MODE_HID) {
    if (!codeAccepted) {
      readButton();
    } else {
      // Menu handling (short press to scroll, hold to send)
      handleMenuButton();
    }
  }
  // If we're in CDC/serial mode, process incoming serial lines as commands
  
  if (currentUSBMode == MODE_CDC) {
    while (isSerialDataAvailable()) {
      String line = readSerialData();
      
      processSerialLine(line);
    }
  }
}

