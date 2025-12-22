#include <Arduino.h>
#include <TFT_eSPI.h>
#include "display.h"

// From main.cpp
extern bool awaitingFileNumber;
extern int bootMenuSelection;
extern String fileList[15];
extern int fileCount;
extern int bootMenuSelection;

// Declarations are provided by include/display.h

void showInstructions() {
  tft.setRotation(1); // Landscape
  tft.fillScreen(TFT_BLACK);
  tft.setCursor(10, 120);
  tft.setTextSize(2);
  tft.setTextColor(TFT_YELLOW, TFT_BLACK);

  tft.println("  Enter boot code");
  tft.println("  Short press = +1");
  tft.println("  Long press  = OK");
  delay(1000);
}

void showFileNumberPrompt() {
  tft.setRotation(1); // Landscape
  tft.fillScreen(TFT_BLACK);
  tft.setCursor(10, 100);
  tft.setTextSize(2);
  tft.setTextColor(TFT_CYAN, TFT_BLACK);
  tft.println("Enter file number");
  tft.setTextSize(1);
  tft.setTextColor(TFT_YELLOW, TFT_BLACK);
  tft.println("");
  tft.println("Example: 0001 -> types 0001.txt");
  tft.println("Short press: +1, Long: OK");
  delay(600);
}

void showCountdown(int seconds) {
  tft.fillScreen(TFT_BLACK);
  tft.setRotation(0);
  tft.setTextSize(2);
  tft.setTextColor(TFT_CYAN, TFT_BLACK);
  tft.setCursor(10, 40);
  tft.println("Starting Bluetooth");
  tft.println("");
  tft.setTextColor(TFT_YELLOW, TFT_BLACK);
  tft.println("Press BOOT button");
  tft.println("for PIN entry");
  tft.println("");
  tft.setTextSize(4);
  tft.setTextColor(TFT_WHITE, TFT_BLACK);
  tft.setCursor(70, 160);
  tft.printf("%d", seconds);
}

void showDigitScreen() {
  tft.setRotation(0); // Portrait mode
  tft.fillScreen(TFT_BLACK);

  // If we're in file-number mode, show inline instructions on the same screen
  if (awaitingFileNumber) {
    tft.setTextColor(TFT_YELLOW, TFT_BLACK);
    tft.setCursor(10, 140);
    tft.setTextSize(1);
    tft.setTextFont(2);
    tft.println("File mode: enter number");
    tft.println("Example: 0001 -> 0001.txt");
    
    // Show available SD card text files (up to 15)
    extern void listSDTextFiles(String fileList[15], int& count);
    String sdFiles[15];
    int fileCount = 0;
    listSDTextFiles(sdFiles, fileCount);
    
    if (fileCount > 0) {
      tft.setTextColor(TFT_GREEN, TFT_BLACK);
      tft.setTextSize(1);
      tft.setTextFont(2); // larger, readable font

      // Header
      tft.setCursor(10, 165);
      tft.println("Available:");

      // Two-column layout
      const int leftX = 10;
      const int rightX = 100; // second column start
      const int startY = 185;
      const int rowH = 16; // line height for font 2

      int rows = (fileCount + 1) / 2; // fill left column first, then right
      for (int r = 0; r < rows; ++r) {
        int li = r;
        int ri = r + rows;

        // Left column item
        if (li < fileCount) {
          tft.setCursor(leftX, startY + r * rowH);
          tft.printf("%s", sdFiles[li].c_str());
        }

        // Right column item
        if (ri < fileCount) {
          tft.setCursor(rightX, startY + r * rowH);
          tft.printf("%s", sdFiles[ri].c_str());
        }
      }
    } else {
      tft.setTextColor(TFT_RED, TFT_BLACK);
      tft.setCursor(10, 175);
      tft.setTextSize(1);
      tft.setTextFont(2);
      tft.println("No .txt files found");
    }
  }
  
  tft.setTextColor(TFT_WHITE, TFT_BLACK);
  tft.setCursor(10, 20);
  tft.setTextSize(1);
  tft.setTextFont(2);
  tft.printf("Digit %d of 4", digitIndex + 1);

  // Draw four fixed digit slots so digits do not shift position while entering
  // Tighter spacing so all four digits fit on one row on small displays
  const int baseX = 34;
  const int y = 80;
  const int spacing = 36;

  // Access input state from input.cpp
  extern int enteredCode[4];
  extern int digitIndex;
  extern int currentDigit;
  extern bool digitAccepted[4];

  tft.setTextSize(3);
  for (int i = 0; i < 4; i++) {
    int x = baseX + i * spacing;
    tft.setCursor(x, y);
    if (i == digitIndex) {
      tft.setTextColor(TFT_CYAN, TFT_BLACK);
      tft.printf("%d", currentDigit);
    } else {
      tft.setTextColor(TFT_WHITE, TFT_BLACK);
      if (i < digitIndex) {
        // Show entered digit or mask with '*' if accepted
        if (digitAccepted[i]) {
          tft.print("*");
        } else {
          tft.printf("%d", enteredCode[i]);
        }
      } else {
        // Placeholder for not-yet-entered digits
        tft.print("_");
      }
    }
  }
}

void drawMenu() {
  tft.setRotation(0); 
  tft.fillScreen(TFT_BLACK);
  tft.setTextSize(1);
  tft.setTextFont(2);
  tft.setCursor(10, 10);
  tft.setTextColor(TFT_CYAN);
  tft.println("Select Password:");

  for (int i = 0; i < MENU_ITEM_COUNT; i++) {
    tft.setCursor(10, 40 + i * 30); // Position items vertically
    if (i == selectedItem) {
      tft.setTextColor(TFT_BLACK, TFT_WHITE); // Highlighted
      tft.print("> ");
    } else {
      tft.setTextColor(TFT_WHITE, TFT_BLACK); // Normal
      tft.print("  ");
    }
    tft.println(menuItems[i]);
  }
  tft.setCursor(10, 150);
  tft.setTextColor(TFT_YELLOW);
  tft.println("Hold to Send");
}

void showWrongCodeScreen() {
  tft.fillScreen(TFT_BLACK);
  tft.setTextColor(TFT_RED, TFT_BLACK);
  tft.setTextSize(2);
  tft.setCursor(20, 60);
  tft.println("WRONG CODE!");

  delay(1500);
  tft.fillScreen(TFT_BLACK);
}

void showRebootScreen() {
  tft.fillScreen(TFT_BLACK);
  tft.setCursor(20, 80);
  tft.setTextSize(3);
  tft.setTextColor(TFT_WHITE);
  tft.println("Wait...");
  delay(1000);
}

void showPasswordSentScreen(String password) {
  tft.fillScreen(TFT_GREEN);
  tft.setCursor(10, 50);
  tft.setTextColor(TFT_BLACK);
  tft.setTextSize(1);
  tft.setTextFont(2);
  tft.println("SENT:");
  tft.println(password);
  delay(1000); // Display message for a second
}

void showCDCReadyScreen() {
  tft.fillScreen(TFT_GREEN);
  tft.setTextColor(TFT_BLACK, TFT_GREEN);
  tft.setTextSize(1);
  tft.setTextFont(2);
  tft.setCursor(30, 30);
  tft.println("CDC MODE READY");
  tft.setCursor(1,80);
  tft.println("  Waiting for connection.");
}

void showStartupMessage(const char* message) {
  tft.println(message);
}

void showHelpScreen() {
  tft.setRotation(0);
  tft.fillScreen(TFT_BLACK);
  tft.setTextFont(2);
  tft.setTextSize(1);
  tft.setTextColor(TFT_CYAN, TFT_BLACK);
  tft.setCursor(10, 10);
  tft.println("Commands:");
  tft.setTextColor(TFT_WHITE, TFT_BLACK);
  tft.setCursor(10, 40);
  tft.println("HELP - show this screen");
  tft.setCursor(10, 70);
  tft.println("ABOUT - firmware info");
  tft.setCursor(10, 100);
  tft.println("PWUPDATE - update passwords (requires code)");
  tft.setCursor(10, 130);
  tft.println("RETRIEVEPW - retrieve stored passwords");
  tft.setCursor(10, 160);
  tft.println("CHANGELOGIN - change 4-digit login");
  tft.setCursor(10, 200);
  tft.setTextColor(TFT_YELLOW, TFT_BLACK);
  tft.println("Follow serial prompts after sending a command");
}

void drawBootMenu(int selectedIndex) {
  tft.setRotation(0); // Portrait
  tft.fillScreen(TFT_BLACK);
  
  tft.setTextSize(2);
  tft.setTextColor(TFT_CYAN, TFT_BLACK);
  tft.setCursor(10, 10);
  tft.println("Boot Menu");
  
  tft.setTextSize(1);
  tft.setTextFont(2);
  
  const char* menuOptions[] = {
    "Bluetooth (BLE)",
    "Terminal (CDC)",
    "Password Mode",
    "Storage Mode",
    "Macro / Text"
  };
  
  int startY = 50;
  int lineHeight = 24;
  
  for (int i = 0; i < 5; i++) {
    int y = startY + (i * lineHeight);
    
    if (i == selectedIndex) {
      // Highlight selected item
      tft.fillRect(5, y - 2, 160, lineHeight - 4, TFT_DARKGREY);
      tft.setTextColor(TFT_YELLOW, TFT_DARKGREY);
      tft.setCursor(10, y);
      tft.print("> ");
    } else {
      tft.setTextColor(TFT_WHITE, TFT_BLACK);
      tft.setCursor(10, y);
      tft.print("  ");
    }
    
    tft.println(menuOptions[i]);
  }
  
  tft.setTextSize(1);
  tft.setTextColor(TFT_YELLOW, TFT_BLACK);
  tft.setCursor(10, 200);
  tft.println("Short: scroll");
  tft.setCursor(10, 215);
  tft.println("Long: select");
}

void drawFileMenu(int selectedIndex, String fileList[], int fileCount) {
  tft.setRotation(0); // Portrait
  tft.fillScreen(TFT_BLACK);
  
  tft.setTextSize(2);
  tft.setTextColor(TFT_CYAN, TFT_BLACK);
  tft.setCursor(10, 10);
  tft.println("Select File");
  
  if (fileCount == 0) {
    tft.setTextSize(1);
    tft.setTextFont(2);
    tft.setTextColor(TFT_RED, TFT_BLACK);
    tft.setCursor(10, 50);
    tft.println("No .txt files found");
    tft.println("on SD card");
    return;
  }
  
  tft.setTextSize(1);
  tft.setTextFont(2);
  
  int startY = 50;
  int lineHeight = 20;
  int maxVisible = 9; // Maximum files visible at once
  
  // Calculate scroll window
  int scrollStart = 0;
  if (fileCount > maxVisible) {
    scrollStart = max(0, min(selectedIndex - maxVisible/2, fileCount - maxVisible));
  }
  
  for (int i = 0; i < min(fileCount, maxVisible); i++) {
    int fileIdx = scrollStart + i;
    if (fileIdx >= fileCount) break;
    
    int y = startY + (i * lineHeight);
    
    if (fileIdx == selectedIndex) {
      // Highlight selected item
      tft.fillRect(5, y - 2, 160, lineHeight - 2, TFT_DARKGREY);
      tft.setTextColor(TFT_YELLOW, TFT_DARKGREY);
      tft.setCursor(10, y);
      tft.print("> ");
    } else {
      tft.setTextColor(TFT_WHITE, TFT_BLACK);
      tft.setCursor(10, y);
      tft.print("  ");
    }
    
    // Truncate long filenames if needed
    String displayName = fileList[fileIdx];
    if (displayName.length() > 12) {
      displayName = displayName.substring(0, 12);
    }
    tft.print(displayName);
    tft.println(".txt");
  }
  
  // Show scroll indicator if needed
  if (fileCount > maxVisible) {
    tft.setTextSize(1);
    tft.setTextColor(TFT_YELLOW, TFT_BLACK);
    tft.setCursor(10, 230);
    tft.printf("%d/%d", selectedIndex + 1, fileCount);
  }
  
  tft.setTextSize(1);
  tft.setTextColor(TFT_YELLOW, TFT_BLACK);
  tft.setCursor(10, 250);
  tft.println("Short: scroll");
  tft.setCursor(10, 265);
  tft.println("Long: type file");
}
