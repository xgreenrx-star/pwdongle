#ifndef DUCKYSCRIPT_H
#define DUCKYSCRIPT_H

#include <Arduino.h>

/*
 * DuckyScript module
 * - Parses and executes RubberDucky script format
 * - Compatible with classic DuckyScript syntax (DELAY, STRING, REM, etc.)
 * - Converts DuckyScript commands to USB HID keyboard actions
 */

// Process a single line of DuckyScript
void processDuckyScriptLine(const String& line);

// Process entire DuckyScript text (multi-line)
void processDuckyScript(const String& script);

// Check if a file appears to be DuckyScript format
bool isDuckyScriptFile(const String& content);

#endif
