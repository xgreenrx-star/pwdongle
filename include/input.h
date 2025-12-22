#ifndef INPUT_H
#define INPUT_H

#include <Arduino.h>
#include <TFT_eSPI.h>
#include "storage.h"

/*
 * Input module
 * - Handles the boot/button input state machine and menu navigation.
 * - `input.cpp` owns the runtime input state variables declared below
+ *   (`enteredCode`, `digitIndex`, `currentDigit`, `selectedItem`).
 */

// External references (defined in main.cpp)
extern TFT_eSPI tft;
extern String PASSWORDS[MAX_DEVICES];
extern int MENU_ITEM_COUNT;

// Button configuration
#define BOOT_BUTTON_PIN 0
#define DEBOUNCE_DELAY 50      // ms
#define HOLD_THRESHOLD 600     // ms

// Input state (owned by input.cpp)
extern int enteredCode[4];
extern int digitIndex;
extern int currentDigit;
extern int selectedItem;
extern bool digitAccepted[4];

// PIN entry functions
void readButton();
void incrementDigit();
void acceptDigit();

// Menu navigation functions
void scrollMenu();
void handleMenuButton();

// Boot menu functions
void handleBootMenuButton(int& selection, bool& confirmed);

// File menu functions
void handleFileMenuButton(int& selection, bool& confirmed, int maxItems);

// Utility functions
void resetInputState();
int getSelectedItem();
int getCurrentDigit();
int getDigitIndex();

// Forward declarations (security.cpp)
void checkCode();

// Forward declarations (display.cpp)
void showDigitScreen();
void drawMenu();
void drawBootMenu(int selectedIndex);
void drawFileMenu(int selectedIndex, String fileList[], int fileCount);

// Forward declarations (usb.cpp)
void sendPassword(String password);

#endif
