#ifndef SECURITY_H
#define SECURITY_H

#include <Arduino.h>

/*
 * Security module
 * - Responsible for PIN validation and access control.
 * - `security.cpp` defines the access code arrays (`correctCode` and
 *   `comModeCode`) and the `checkCode()` entry point used by the input
 *   state machine.
 */

// Access codes (defined in security.cpp)
extern int correctCode[4];
extern int comModeCode[4];

// PIN validation
void checkCode();

// Utility validation functions
bool validateCode(const int* code, size_t length);
bool isComModeCode(const int* code, size_t length);
bool isAccessCode(const int* code, size_t length);

// Code management
void setCorrectCode(const int* newCode, size_t length);
void setComModeCode(const int* newCode, size_t length);

// Load persisted correct code from NVS (Preferences)
void loadCorrectCode();
void setCorrectCodePersist(const int* newCode, size_t length);

// Returns true if a login code exists in NVS (Preferences)
bool isLoginCodePersisted();

// Forward declarations (display.cpp)
void showRebootScreen();
void showWrongCodeScreen();
void showDigitScreen();
void drawMenu();

// Forward declarations (storage.cpp)
void loadPasswords();

#endif
