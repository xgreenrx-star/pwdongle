#include <Arduino.h>
#include "input.h"

// Button timing and state
unsigned long lastButtonPressTime = 0;
bool buttonHeld = false;
static bool pressed = false;
static unsigned long pressStart = 0;

// Input buffer state
int enteredCode[4];
int digitIndex = 0;
int currentDigit = 0;
// Which digits have been accepted (long press) and should be masked
bool digitAccepted[4] = { false, false, false, false };

// Menu state
int selectedItem = 0;

// Boot menu state
static int bootMenuSelection = 0;
static bool bootMenuPressed = false;
static unsigned long bootMenuPressStart = 0;

void scrollMenu() {
  selectedItem++;
  if (selectedItem >= MENU_ITEM_COUNT) {
    selectedItem = 0;
  }
  drawMenu();
}

void incrementDigit() {
  currentDigit = (currentDigit + 1) % 10;
  showDigitScreen();
}

void acceptDigit() {
  enteredCode[digitIndex] = currentDigit;
 
  digitIndex++;

  // Mark the just-entered digit as accepted (mask it)
  if (digitIndex > 0 && digitIndex <= 4) {
    digitAccepted[digitIndex - 1] = true;
  }

  if (digitIndex >= 4) {
    checkCode();
    return;
  }

  currentDigit = 0;
  showDigitScreen();
}

void readButton() {
  int state = digitalRead(BOOT_BUTTON_PIN);

  if (state == LOW) {
    
    if (!pressed) {
      pressed = true;
      pressStart = millis();
      
    }
    unsigned long pressTime = millis() - pressStart;
    if (pressTime > HOLD_THRESHOLD) {
      // While holding, refresh the digit screen to reflect the current digit
      showDigitScreen();
    }
  } else {
    if (pressed) {
      unsigned long pressTime = millis() - pressStart;

      if (pressTime > HOLD_THRESHOLD) {
        acceptDigit();
      } else if (pressTime > DEBOUNCE_DELAY) {
        incrementDigit();
      }

      pressed = false;
    }
  }
}

void handleMenuButton() {
  static int lastButtonState = HIGH;
  int currentButtonState = digitalRead(BOOT_BUTTON_PIN);
  unsigned long currentTime = millis();
  
  // Debounce the button press/release
  if (currentButtonState != lastButtonState) {
    if (currentTime - lastButtonPressTime > DEBOUNCE_DELAY) {
      lastButtonPressTime = currentTime;

      if (currentButtonState == LOW) {
        // Button was pressed
      } else {
        // Button was released
        if (!buttonHeld) {
          // It was a short press, so scroll the menu
          scrollMenu();
        }
        buttonHeld = false; // Reset held status
      }
    }
  }
  lastButtonState = currentButtonState;

  // Check if the button is currently held down for the hold threshold duration
  if (currentButtonState == LOW && !buttonHeld) {
    if (currentTime - lastButtonPressTime > HOLD_THRESHOLD) {
      buttonHeld = true; // Mark as held
      sendPassword(PASSWORDS[selectedItem]);
    }
  };
}

void resetInputState() {
  digitIndex = 0;
  currentDigit = 0;
  pressed = false;
  buttonHeld = false;
  selectedItem = 0;
  // Clear accepted/masked digits
  for (int i = 0; i < 4; i++) digitAccepted[i] = false;
}

int getSelectedItem() {
  return selectedItem;
}

int getCurrentDigit() {
  return currentDigit;
}

int getDigitIndex() {
  return digitIndex;
}

void handleBootMenuButton(int& selection, bool& confirmed) {
  int state = digitalRead(BOOT_BUTTON_PIN);
  confirmed = false;

  if (state == LOW) {
    if (!bootMenuPressed) {
      bootMenuPressed = true;
      bootMenuPressStart = millis();
    }
  } else {
    if (bootMenuPressed) {
      unsigned long pressTime = millis() - bootMenuPressStart;

      if (pressTime > HOLD_THRESHOLD) {
        // Long press - confirm selection
        confirmed = true;
      } else if (pressTime > DEBOUNCE_DELAY) {
        // Short press - scroll menu
        selection = (selection + 1) % 5;
        drawBootMenu(selection);
      }

      bootMenuPressed = false;
    }
  }
}

void handleFileMenuButton(int& selection, bool& confirmed, int maxItems) {
  int state = digitalRead(BOOT_BUTTON_PIN);
  confirmed = false;
  
  static bool fileMenuPressed = false;
  static unsigned long fileMenuPressStart = 0;

  if (state == LOW) {
    if (!fileMenuPressed) {
      fileMenuPressed = true;
      fileMenuPressStart = millis();
    }
  } else {
    if (fileMenuPressed) {
      unsigned long pressTime = millis() - fileMenuPressStart;

      if (pressTime > HOLD_THRESHOLD) {
        // Long press - confirm selection
        confirmed = true;
      } else if (pressTime > DEBOUNCE_DELAY) {
        // Short press - scroll to next file
        if (maxItems > 0) {
          selection = (selection + 1) % maxItems;
          // Need to redraw menu with new selection
          extern String fileList[15];
          extern int fileCount;
          drawFileMenu(selection, fileList, fileCount);
        }
      }

      fileMenuPressed = false;
    }
  }
}
