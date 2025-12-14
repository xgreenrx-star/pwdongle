#ifndef BLUETOOTH_H
#define BLUETOOTH_H

#include <Arduino.h>

// BLE mode constant
#define MODE_BLE 2

// BLE functions
void startBLEMode();
void stopBLEMode();
bool isBLEDataAvailable();
String readBLEData();
void sendBLEResponse(const String& msg);
void sendBLECSV(const String& name, const String& password);
bool isBLEConnected();
String getBLEDeviceName();

// Dual-mode: relay keystrokes to PC via USB HID
void relayTypeToPC(const String& text);
void relayKeyToPC(const String& keyName);

// External state
extern int currentBLEMode;
extern int dualModeActive;

#endif // BLUETOOTH_H
