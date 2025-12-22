#ifndef SCRIPTENGINE_H
#define SCRIPTENGINE_H

#include <Arduino.h>
#include <map>
#include <vector>

/*
 * Script Engine module
 * - Advanced scripting with variables, loops, conditionals
 * - GPC (Game Profile Compiler) language support
 * - State machine for control flow
 */

// Script execution context
class ScriptContext {
public:
    std::map<String, int> variables;        // Variable storage (name -> value)
    std::map<String, String> stringVars;    // String variables
    std::vector<int> loopStack;             // Loop iteration counters
    std::vector<size_t> loopStartPositions; // Line numbers for loop starts
    std::vector<String> lines;              // All script lines
    size_t currentLine;                     // Current execution line
    bool skipMode;                          // Skipping to endif/endloop
    int skipDepth;                          // Nesting depth when skipping
    
    ScriptContext() : currentLine(0), skipMode(false), skipDepth(0) {}
    
    void reset() {
        variables.clear();
        stringVars.clear();
        loopStack.clear();
        loopStartPositions.clear();
        lines.clear();
        currentLine = 0;
        skipMode = false;
        skipDepth = 0;
    }
    
    int getVar(const String& name, int defaultVal = 0) {
        auto it = variables.find(name);
        return (it != variables.end()) ? it->second : defaultVal;
    }
    
    void setVar(const String& name, int value) {
        variables[name] = value;
    }
    
    String getStringVar(const String& name, const String& defaultVal = "") {
        auto it = stringVars.find(name);
        return (it != stringVars.end()) ? it->second : defaultVal;
    }
    
    void setStringVar(const String& name, const String& value) {
        stringVars[name] = value;
    }
};

// Execute advanced script with variables, loops, conditionals
void executeAdvancedScript(const String& script);

// Execute single line with context
bool executeScriptLine(ScriptContext& ctx, const String& line);

// Parse and evaluate expressions (arithmetic, comparisons)
int evaluateExpression(ScriptContext& ctx, const String& expr);
bool evaluateCondition(ScriptContext& ctx, const String& condition);

// GPC command execution
void executeGPCCommand(ScriptContext& ctx, const String& command);

// Check if script uses advanced features
bool isAdvancedScript(const String& content);

#endif
