#include "duckyscript.h"
#include <Arduino.h>
#include <USBHIDKeyboard.h>
#include <vector>

// External keyboard reference from main.cpp
extern USBHIDKeyboard Keyboard;

// DuckyScript key mappings
static const struct {
  const char* name;
  uint8_t keycode;
} duckyKeyMap[] = {
  {"ENTER", KEY_RETURN},
  {"ESCAPE", KEY_ESC},
  {"ESC", KEY_ESC},
  {"BACKSPACE", KEY_BACKSPACE},
  {"TAB", KEY_TAB},
  {"SPACE", ' '},
  {"DELETE", KEY_DELETE},
  {"DEL", KEY_DELETE},
  {"HOME", KEY_HOME},
  {"INSERT", KEY_INSERT},
  {"END", KEY_END},
  {"PAGEUP", KEY_PAGE_UP},
  {"PAGEDOWN", KEY_PAGE_DOWN},
  {"UPARROW", KEY_UP_ARROW},
  {"DOWNARROW", KEY_DOWN_ARROW},
  {"LEFTARROW", KEY_LEFT_ARROW},
  {"RIGHTARROW", KEY_RIGHT_ARROW},
  {"UP", KEY_UP_ARROW},
  {"DOWN", KEY_DOWN_ARROW},
  {"LEFT", KEY_LEFT_ARROW},
  {"RIGHT", KEY_RIGHT_ARROW},
  {"F1", KEY_F1},
  {"F2", KEY_F2},
  {"F3", KEY_F3},
  {"F4", KEY_F4},
  {"F5", KEY_F5},
  {"F6", KEY_F6},
  {"F7", KEY_F7},
  {"F8", KEY_F8},
  {"F9", KEY_F9},
  {"F10", KEY_F10},
  {"F11", KEY_F11},
  {"F12", KEY_F12},
  {"CAPSLOCK", KEY_CAPS_LOCK},
  {"GUI", KEY_LEFT_GUI},
  {"WINDOWS", KEY_LEFT_GUI},
  {nullptr, 0}
};

// Modifier key mappings
static const struct {
  const char* name;
  uint8_t keycode;
} duckyModifierMap[] = {
  {"CTRL", KEY_LEFT_CTRL},
  {"CONTROL", KEY_LEFT_CTRL},
  {"SHIFT", KEY_LEFT_SHIFT},
  {"ALT", KEY_LEFT_ALT},
  {"GUI", KEY_LEFT_GUI},
  {"WINDOWS", KEY_LEFT_GUI},
  {"COMMAND", KEY_LEFT_GUI},
  {nullptr, 0}
};

// Helper to find key code by name
static uint8_t findKeyCode(const String& keyName) {
  String upper = keyName;
  upper.toUpperCase();
  
  for (int i = 0; duckyKeyMap[i].name != nullptr; i++) {
    if (upper.equals(duckyKeyMap[i].name)) {
      return duckyKeyMap[i].keycode;
    }
  }
  
  // Single character
  if (keyName.length() == 1) {
    return (uint8_t)keyName.charAt(0);
  }
  
  return 0;
}

// Helper to find modifier code
static uint8_t findModifierCode(const String& modName) {
  String upper = modName;
  upper.toUpperCase();
  
  for (int i = 0; duckyModifierMap[i].name != nullptr; i++) {
    if (upper.equals(duckyModifierMap[i].name)) {
      return duckyModifierMap[i].keycode;
    }
  }
  return 0;
}

// Parse and execute a single DuckyScript line
void processDuckyScriptLine(const String& line) {
  String trimmed = line;
  trimmed.trim();
  
  // Skip empty lines and comments
  if (trimmed.length() == 0 || trimmed.startsWith("REM ")) {
    return;
  }
  
  // DELAY command
  if (trimmed.startsWith("DELAY ")) {
    int delayMs = trimmed.substring(6).toInt();
    delay(delayMs);
    return;
  }
  
  // DEFAULT_DELAY command (set default delay between commands)
  if (trimmed.startsWith("DEFAULT_DELAY ") || trimmed.startsWith("DEFAULTDELAY ")) {
    // Store this for future use - for now just skip
    return;
  }
  
  // STRING command - type literal text
  if (trimmed.startsWith("STRING ")) {
    String text = trimmed.substring(7);
    Keyboard.print(text);
    delay(10);
    return;
  }
  
  // STRINGLN command - type literal text with enter
  if (trimmed.startsWith("STRINGLN ")) {
    String text = trimmed.substring(9);
    Keyboard.println(text);
    delay(10);
    return;
  }
  
  // REPEAT command
  if (trimmed.startsWith("REPEAT ")) {
    // Would need to track previous command - simplified: just delay
    int count = trimmed.substring(7).toInt();
    delay(count * 10);
    return;
  }
  
  // Key combination or single key
  std::vector<uint8_t> modifiers;
  std::vector<String> parts;
  
  // Split by space for compound commands
  int start = 0;
  while (true) {
    int sp = trimmed.indexOf(' ', start);
    if (sp < 0) {
      parts.push_back(trimmed.substring(start));
      break;
    }
    parts.push_back(trimmed.substring(start, sp));
    start = sp + 1;
  }
  
  if (parts.size() == 0) return;
  
  // Check for single key press
  if (parts.size() == 1) {
    String keyName = parts[0];
    uint8_t keycode = findKeyCode(keyName);
    
    if (keycode != 0) {
      Keyboard.press(keycode);
      delay(50);
      Keyboard.release(keycode);
      delay(10);
    }
    return;
  }
  
  // Multi-part: modifiers + key
  // All but last are modifiers
  for (size_t i = 0; i < parts.size() - 1; i++) {
    uint8_t mod = findModifierCode(parts[i]);
    if (mod != 0) {
      modifiers.push_back(mod);
    }
  }
  
  // Last part is the key
  String keyName = parts[parts.size() - 1];
  uint8_t keycode = findKeyCode(keyName);
  
  // Press modifiers
  for (auto mod : modifiers) {
    Keyboard.press(mod);
  }
  
  // Press main key
  if (keycode != 0) {
    Keyboard.press(keycode);
    delay(50);
    Keyboard.release(keycode);
  }
  
  // Release modifiers
  for (auto mod : modifiers) {
    Keyboard.release(mod);
  }
  
  delay(10);
}

// Process entire DuckyScript (multi-line)
void processDuckyScript(const String& script) {
  int start = 0;
  while (start < script.length()) {
    int end = script.indexOf('\n', start);
    if (end < 0) end = script.length();
    
    String line = script.substring(start, end);
    line.trim();
    
    if (line.length() > 0) {
      processDuckyScriptLine(line);
    }
    
    start = end + 1;
  }
}

// Detect if content looks like DuckyScript
bool isDuckyScriptFile(const String& content) {
  // Check for common DuckyScript commands
  return content.indexOf("REM ") >= 0 ||
         content.indexOf("DELAY ") >= 0 ||
         content.indexOf("STRING ") >= 0 ||
         content.indexOf("GUI ") >= 0 ||
         content.indexOf("CTRL ") >= 0 ||
         content.indexOf("ALT ") >= 0 ||
         content.indexOf("ENTER") >= 0;
}
