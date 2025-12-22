#ifndef USB_H
#define USB_H

#include <Arduino.h>
#include <USBHIDKeyboard.h>

/*
 * USB module
 * - Handles USB configuration for HID keyboard mode and CDC (serial)
 *   configuration for receiving configuration data from a host.
 * - `Keyboard` is defined in `main.cpp` and used by `usb.cpp`.
 */

#define MODE_HID 0
#define MODE_CDC 1
#define MODE_MSC 2
// Serial RX buffer size used when starting CDC mode
#define BUF_SIZE 1024

// External references (defined in main.cpp)
extern USBHIDKeyboard Keyboard;

// Current USB mode at runtime (MODE_HID or MODE_CDC)
extern int currentUSBMode;

// USB mode initialization
void startUSBMode(int mode);

// HID keyboard operations
void sendPassword(String password);
bool typeTextFileFromSD(const String& baseName);
void processMacroText(const String& text);

// SD file listing
void listSDTextFiles(String fileList[15], int& count);

// CDC serial operations
bool isSerialDataAvailable();
String readSerialData();
void sendSerialResponse(const char* message);
void sendSerialCSV(const String& name, const String& password);

// CDC command processing
void processSerialLine(const String& line);
void processBLELine(const String& line);
void resetSerialState();

// Forward declarations (display.cpp)
void showStartupMessage(const char* message);
void showPasswordSentScreen(String password);
void drawMenu();

#endif
