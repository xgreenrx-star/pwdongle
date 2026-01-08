// Main delegator: keep `main.cpp` minimal and delegate functionality
#include <Arduino.h>
#include <TFT_eSPI.h>
#include <Preferences.h>
#include <USB.h>
#include <USBHIDKeyboard.h>
#include <USBHIDMouse.h>
#include <USBHIDGamepad.h>

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
USBHIDMouse Mouse;
USBHIDGamepad Gamepad;

String PASSWORDS[MAX_DEVICES];
String menuItems[MAX_DEVICES];
int MENU_ITEM_COUNT = 0; // will be set from storage on load

bool codeAccepted = false;
bool awaitingFileNumber = false;
int bootMenuSelection = 0;

// File menu state
bool inFileMenu = false;
String fileList[15];
int fileCount = 0;
int fileMenuSelection = 0;

// Boot button configuration (also referenced in input.h)
#ifndef BOOT_BUTTON_PIN
#define BOOT_BUTTON_PIN 0
#endif

void setup() {
  tft.init();
  tft.setRotation(0); // Portrait mode
  tft.fillScreen(TFT_BLACK);
  tft.println("Starting...");
  delay(1000);
  
  showStartupMessage("Starting...");
  //delay(500);
  //Serial.println("Display startup complete");

  // Load persisted login code from NVS (if present)
  loadCorrectCode();

  // Configure the boot button early for countdown check
  pinMode(BOOT_BUTTON_PIN, INPUT_PULLUP);

  // Check for explicit BLE mode boot flag (from entering 0000 code) BEFORE countdown
  prefs.begin("BLE", true);
  bool bootToBLE = prefs.getBool("bootToBLE", false);
  prefs.end();

  // Check for explicit MSC mode boot flag (from entering 0001 code) BEFORE countdown
  initializeMSCFlag();
  if (getBootToMSC()) {
    setBootToMSC(false);
    showStartupMessage("Flash drive mode");
    startUSBMode(MODE_MSC);
    return;
  }

  if (bootToBLE) {
    // Clear explicit flag
    prefs.begin("BLE", false);
    prefs.putBool("bootToBLE", false);
    prefs.end();

    // Initialize USB HID first (needed for TYPE/KEY relay to PC)
    startUSBMode(MODE_HID);

    // Start BLE immediately (skip countdown entirely)
    startBLEMode();

    // Show UI
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

    // Stay in BLE mode; skip further setup paths
    return;
  }

  // 3-second countdown: default to BLE unless button pressed
  bool userInterrupted = false;
  for (int countdown = 3; countdown > 0; countdown--) {
    showCountdown(countdown);
    unsigned long startWait = millis();
    while (millis() - startWait < 1000) {
      if (digitalRead(BOOT_BUTTON_PIN) == LOW) {
        userInterrupted = true;
        break;
      }
      delay(10);
    }
    if (userInterrupted) break;
  }

  // Show boot menu if user interrupted countdown
  if (userInterrupted) {
    bootMenuSelection = 0;
    drawBootMenu(bootMenuSelection);
    
    bool menuConfirmed = false;
    while (!menuConfirmed) {
      handleBootMenuButton(bootMenuSelection, menuConfirmed);
      delay(10);
    }
    
    // Handle selected boot mode
    if (bootMenuSelection == 0) {
      // BLE Mode
      startUSBMode(MODE_HID);
      startBLEMode();
      
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
      return;
      
    } else if (bootMenuSelection == 1) {
      // CDC Mode
      initializeCDCFlag();
      showCDCReadyScreen();
      startUSBMode(MODE_CDC);
      
      unsigned long start = millis();
      const unsigned long timeout = 300000;
      while (millis() - start < timeout) {
        if (isSerialDataAvailable()) {
          String d = readSerialData();
          processSerialLine(d);
        }
        delay(50);
      }
      return;
      
    } else if (bootMenuSelection == 2) {
      // Password Mode - proceed to PIN entry
      showInstructions();
      showDigitScreen();
      
    } else if (bootMenuSelection == 3) {
      // Storage Mode - mount SD as USB mass storage (MSC)
      tft.fillScreen(TFT_CYAN);
      tft.setTextColor(TFT_BLACK, TFT_CYAN);
      tft.setTextSize(2);
      tft.setCursor(10, 40);
      tft.println("STORAGE MODE");
      tft.setTextSize(1);
      tft.setCursor(10, 80);
      tft.println("SD card is now mounted");
      tft.setCursor(10, 100);
      tft.println("as a USB drive.");
      tft.setCursor(10, 120);
      tft.println("Eject before unplugging!");
      startUSBMode(MODE_MSC);
      // Remain here until reboot or mode switch
      while (true) {
        delay(1000);
      }
      
    } else if (bootMenuSelection == 4) {
      // Macro / Text Mode - show file selection menu
      inFileMenu = true;
      listSDTextFiles(fileList, fileCount);
      fileMenuSelection = 0;
      drawFileMenu(fileMenuSelection, fileList, fileCount);
    }
    
    // For Password/Storage/Text File modes, continue to normal HID loop
    return;
  }

  // Enter BLE mode if countdown expires without button
  if (!userInterrupted) {
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
  
  // Ensure CDC boot flag exists and handle CDC-mode boot (explicit flag from code)
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

  // Only show PIN entry UI when NOT in BLE mode and not already handled by boot menu
  if (currentUSBMode == MODE_HID && currentBLEMode == 0 && !codeAccepted && !awaitingFileNumber) {
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
    // Process pending BLE commands with backpressure to prevent starvation
    int processedCount = 0;
    const int MAX_PER_LOOP = 10;  // Limit to 10 commands per iteration
    while (isBLEDataAvailable() && processedCount < MAX_PER_LOOP) {
      String line = readBLEData();
      processBLELine(line);
      processedCount++;
    }
    // No delay - let USB and BLE coexist
    return;  // Don't process HID input in BLE mode
  }

  // Flash drive (MSC) mode: nothing to process in loop
  if (currentUSBMode == MODE_MSC) {
    delay(50);
    return;
  }
  
  if (currentUSBMode == MODE_HID) {
    if (inFileMenu) {
      // Handle file menu navigation
      bool fileConfirmed = false;
      handleFileMenuButton(fileMenuSelection, fileConfirmed, fileCount);
      
      if (fileConfirmed && fileCount > 0) {
        // User selected a file - auto-detect format and type it
        showStartupMessage("Loading file...");
        delay(200);
        
        processTextFileAuto(fileList[fileMenuSelection]);
        
        // Return to file menu
        drawFileMenu(fileMenuSelection, fileList, fileCount);
      }
    } else if (!codeAccepted) {
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

