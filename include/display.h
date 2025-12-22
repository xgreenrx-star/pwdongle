#ifndef DISPLAY_H
#define DISPLAY_H

#include <Arduino.h>
#include <TFT_eSPI.h>
#include "storage.h"

/*
 * Display module
 * - Provides TFT UI helpers used by the rest of the firmware.
 * - `display.cpp` implements the functions declared here.
 * - Globals used by the display are declared below; ownership is noted
 *   so maintainers know where to change state safely.
 */

// External references (defined in main.cpp)
extern TFT_eSPI tft;
extern String menuItems[MAX_DEVICES];
extern int MENU_ITEM_COUNT;
// Globals owned by `input.cpp` (declared here for consumers of display.h)
// - `input.cpp` is the authoritative owner of these variables.
extern int selectedItem;
extern int currentDigit;
extern int digitIndex;

// Display functions - PIN entry
void showInstructions();
void showDigitScreen();
void showFileNumberPrompt();
void showCountdown(int seconds);

// Display functions - Menu
void drawMenu();

// Display functions - Status messages
void showWrongCodeScreen();
void showRebootScreen();
void showPasswordSentScreen(String password);
void showCDCReadyScreen();
void showStartupMessage(const char* message);
// Display help/commands on the TFT
void showHelpScreen();

// Boot menu
void drawBootMenu(int selectedIndex);

// File selection menu
void drawFileMenu(int selectedIndex, String fileList[], int fileCount);

#endif
