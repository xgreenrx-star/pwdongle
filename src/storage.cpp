#include <Arduino.h>
#include <Preferences.h>
#include "storage.h"

// External references (defined in main.cpp)
extern Preferences prefs;
extern String PASSWORDS[MAX_DEVICES];
extern String menuItems[MAX_DEVICES];
extern int MENU_ITEM_COUNT;

#define DEVSTORE_NAMESPACE "devstore"
#define CDC_NAMESPACE "CDC"
#define MSC_NAMESPACE "MSC"

void storeDeviceData(int index, const String &device, const String &password) {
  prefs.begin(DEVSTORE_NAMESPACE, false);  // writable

  // Update count if new index is higher
  int oldCount = prefs.getInt("count", 0);
  if (index >= oldCount) {
    prefs.putInt("count", index + 1);
  }

  String keyDevice = "device_" + String(index);
  String keyPassword = "password_" + String(index);

  prefs.putString(keyDevice.c_str(), device.c_str());
  prefs.putString(keyPassword.c_str(), password.c_str());

  prefs.end();
}

void loadPasswords() {
  prefs.begin(DEVSTORE_NAMESPACE, true);  // read-only

  int count = prefs.getInt("count", 0);
  int toLoad = count;
  if (toLoad > MAX_DEVICES) toLoad = MAX_DEVICES;

  MENU_ITEM_COUNT = toLoad;

  for (int i = 0; i < toLoad; i++) {
    String keyDevice = "device_" + String(i);
    String keyPassword = "password_" + String(i);

    String dev = prefs.getString(keyDevice.c_str(), "");
    String pass = prefs.getString(keyPassword.c_str(), "");

    menuItems[i] = dev;
    PASSWORDS[i] = pass;  
  }

  // Clear remaining slots
  for (int i = toLoad; i < MAX_DEVICES; i++) {
    menuItems[i] = "";
    PASSWORDS[i] = "";
  }

  prefs.end();
}

void parseAndStoreData(String data) {
  // Streamed CSV parsing: process name,password pairs one at a time
  // This avoids large temporary arrays and reduces stack usage.
  
  // First, clear all old password data to prevent stale entries
  prefs.begin(DEVSTORE_NAMESPACE, false);  // writable
  for (int i = 0; i < MAX_DEVICES; i++) {
    String keyDevice = "device_" + String(i);
    String keyPassword = "password_" + String(i);
    if (prefs.isKey(keyDevice.c_str())) {
      prefs.remove(keyDevice.c_str());
    }
    if (prefs.isKey(keyPassword.c_str())) {
      prefs.remove(keyPassword.c_str());
    }
  }
  prefs.end();
  
  int pairs = 0;

  while (data.length() > 0 && pairs < MAX_DEVICES) {
    // Extract device name
    int idx = data.indexOf(',');
    if (idx == -1) break; // no more complete pairs
    String devName = data.substring(0, idx);
    devName.trim();
    data = data.substring(idx + 1);

    // Extract password
    idx = data.indexOf(',');
    String password;
    if (idx == -1) {
      // If no delimiter, the rest of the string is the password
      password = data;
      data = "";
    } else {
      password = data.substring(0, idx);
      data = data.substring(idx + 1);
    }
    password.trim();

    // Skip entries with blank device names or passwords
    if (devName.length() == 0 || password.length() == 0) {
      continue;
    }

    // Store the device name and password in NVS
    storeDeviceData(pairs, devName, password);
    pairs++;
  }

  // Persist the final count and refresh runtime arrays
  prefs.begin(DEVSTORE_NAMESPACE, false);
  prefs.putInt("count", pairs);
  prefs.end();

  MENU_ITEM_COUNT = pairs;
  loadPasswords();
}

void clearAllDevices() {
  prefs.begin(DEVSTORE_NAMESPACE, false);  // writable
  prefs.clear();
  prefs.end();
}

int getDeviceCount() {
  prefs.begin(DEVSTORE_NAMESPACE, true);  // read-only
  int count = prefs.getInt("count", 0);
  prefs.end();
  return count;
}

String getDeviceName(int index) {
  if (index < 0 || index >= MAX_DEVICES) {
    return "";
  }
  
  prefs.begin(DEVSTORE_NAMESPACE, true);  // read-only
  String keyDevice = "device_" + String(index);
  String dev = prefs.getString(keyDevice.c_str(), "");
  prefs.end();
  
  return dev;
}

String getDevicePassword(int index) {
  if (index < 0 || index >= MAX_DEVICES) {
    return "";
  }
  
  prefs.begin(DEVSTORE_NAMESPACE, true);  // read-only
  String keyPassword = "password_" + String(index);
  String pass = prefs.getString(keyPassword.c_str(), "");
  prefs.end();
  
  return pass;
}

bool setBootToCDC(bool value) {
  prefs.begin(CDC_NAMESPACE, false);  // writable
  prefs.putBool("bootToCDC", value);
  prefs.end();
  return true;
}

bool getBootToCDC() {
  prefs.begin(CDC_NAMESPACE, true);  // read-only
  bool bootToCDC = prefs.getBool("bootToCDC", false);
  prefs.end();
  return bootToCDC;
}

bool initializeCDCFlag() {
  prefs.begin(CDC_NAMESPACE, false);  // writable
  
  if (!prefs.isKey("bootToCDC")) {
    prefs.putBool("bootToCDC", false);
    prefs.end();
    return true;  // First initialization
  }
  
  prefs.end();
  return false;  // Already initialized
}

bool setBootToMSC(bool value) {
  prefs.begin(MSC_NAMESPACE, false);
  prefs.putBool("bootToMSC", value);
  prefs.end();
  return true;
}

bool getBootToMSC() {
  prefs.begin(MSC_NAMESPACE, true);
  bool bootToMSC = prefs.getBool("bootToMSC", false);
  prefs.end();
  return bootToMSC;
}

bool initializeMSCFlag() {
  prefs.begin(MSC_NAMESPACE, false);
  if (!prefs.isKey("bootToMSC")) {
    prefs.putBool("bootToMSC", false);
    prefs.end();
    return true;
  }
  prefs.end();
  return false;
}
