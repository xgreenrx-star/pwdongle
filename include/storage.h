#ifndef STORAGE_H
#define STORAGE_H

#include <Arduino.h>

/*
 * Storage module
 * - Wraps NVS (Preferences) operations used to persist device names
 *   and passwords. Uses namespace `devstore` for device pairs and
 *   `CDC` for the boot-to-CDC flag.
 * - `MAX_DEVICES` limits how many pairs are stored in NVS and loaded
 *   into RAM at runtime.
 */

#define MAX_DEVICES 10

// Device data persistence
void storeDeviceData(int index, const String &device, const String &password);
void loadPasswords();
void parseAndStoreData(String data);
void clearAllDevices();

// Device access
int getDeviceCount();
String getDeviceName(int index);
String getDevicePassword(int index);

// Boot mode persistence
bool setBootToCDC(bool value);
bool getBootToCDC();
bool initializeCDCFlag();

#endif
