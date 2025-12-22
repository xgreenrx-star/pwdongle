#include "scriptengine.h"
#include <Arduino.h>
#include <USBHIDKeyboard.h>
#include <USBHIDMouse.h>
#include <USBHIDGamepad.h>
#include "usb.h"

// External references
extern USBHIDKeyboard Keyboard;
extern USBHIDMouse Mouse;
extern USBHIDGamepad Gamepad;

// Helper: Trim whitespace
static String trim(const String& s) {
    String result = s;
    result.trim();
    return result;
}

// Helper: Split string by delimiter
static std::vector<String> split(const String& s, char delim) {
    std::vector<String> result;
    int start = 0;
    for (size_t i = 0; i <= s.length(); i++) {
        if (i == s.length() || s[i] == delim) {
            result.push_back(s.substring(start, i));
            start = i + 1;
        }
    }
    return result;
}

// Evaluate arithmetic expression with variables
// Supports: +, -, *, /, %, parentheses, variables
int evaluateExpression(ScriptContext& ctx, const String& expr) {
    String e = trim(expr);
    
    // Check if it's just a number
    bool isNum = true;
    bool hasNeg = e.startsWith("-");
    for (size_t i = (hasNeg ? 1 : 0); i < e.length(); i++) {
        if (e[i] < '0' || e[i] > '9') {
            isNum = false;
            break;
        }
    }
    if (isNum) return e.toInt();
    
    // Check if it's a variable
    if (e.length() > 0 && ((e[0] >= 'a' && e[0] <= 'z') || (e[0] >= 'A' && e[0] <= 'Z') || e[0] == '_')) {
        bool isVar = true;
        for (size_t i = 1; i < e.length(); i++) {
            char c = e[i];
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')) {
                isVar = false;
                break;
            }
        }
        if (isVar) return ctx.getVar(e);
    }
    
    // Simple recursive descent parser
    // For now, support basic operations: +, -, *, /, %
    
    // Find lowest precedence operator (+ or -)
    int parenDepth = 0;
    int opPos = -1;
    char opChar = 0;
    
    for (int i = e.length() - 1; i >= 0; i--) {
        char c = e[i];
        if (c == ')') parenDepth++;
        else if (c == '(') parenDepth--;
        else if (parenDepth == 0 && (c == '+' || c == '-') && i > 0) {
            opPos = i;
            opChar = c;
            break;
        }
    }
    
    // If no +/-, look for */ %
    if (opPos < 0) {
        parenDepth = 0;
        for (int i = e.length() - 1; i >= 0; i--) {
            char c = e[i];
            if (c == ')') parenDepth++;
            else if (c == '(') parenDepth--;
            else if (parenDepth == 0 && (c == '*' || c == '/' || c == '%') && i > 0) {
                opPos = i;
                opChar = c;
                break;
            }
        }
    }
    
    if (opPos > 0) {
        int left = evaluateExpression(ctx, e.substring(0, opPos));
        int right = evaluateExpression(ctx, e.substring(opPos + 1));
        
        switch (opChar) {
            case '+': return left + right;
            case '-': return left - right;
            case '*': return left * right;
            case '/': return (right != 0) ? (left / right) : 0;
            case '%': return (right != 0) ? (left % right) : 0;
        }
    }
    
    // Handle parentheses
    if (e.startsWith("(") && e.endsWith(")")) {
        return evaluateExpression(ctx, e.substring(1, e.length() - 1));
    }
    
    // Default: try to parse as number
    return e.toInt();
}

// Evaluate boolean condition
// Supports: ==, !=, <, >, <=, >=, &&, ||
bool evaluateCondition(ScriptContext& ctx, const String& condition) {
    String c = trim(condition);
    
    // Handle && and ||
    int parenDepth = 0;
    for (size_t i = 0; i < c.length() - 1; i++) {
        if (c[i] == '(') parenDepth++;
        else if (c[i] == ')') parenDepth--;
        else if (parenDepth == 0) {
            if (c.substring(i, i + 2) == "||") {
                bool left = evaluateCondition(ctx, c.substring(0, i));
                bool right = evaluateCondition(ctx, c.substring(i + 2));
                return left || right;
            }
        }
    }
    
    parenDepth = 0;
    for (size_t i = 0; i < c.length() - 1; i++) {
        if (c[i] == '(') parenDepth++;
        else if (c[i] == ')') parenDepth--;
        else if (parenDepth == 0) {
            if (c.substring(i, i + 2) == "&&") {
                bool left = evaluateCondition(ctx, c.substring(0, i));
                bool right = evaluateCondition(ctx, c.substring(i + 2));
                return left && right;
            }
        }
    }
    
    // Handle comparison operators
    const char* ops[] = {"==", "!=", "<=", ">=", "<", ">"};
    for (int opIdx = 0; opIdx < 6; opIdx++) {
        int opLen = (opIdx < 4) ? 2 : 1;
        String opStr = String(ops[opIdx]);
        
        int pos = c.indexOf(opStr);
        if (pos > 0) {
            int left = evaluateExpression(ctx, c.substring(0, pos));
            int right = evaluateExpression(ctx, c.substring(pos + opLen));
            
            switch (opIdx) {
                case 0: return left == right;  // ==
                case 1: return left != right;  // !=
                case 2: return left <= right;  // <=
                case 3: return left >= right;  // >=
                case 4: return left < right;   // <
                case 5: return left > right;   // >
            }
        }
    }
    
    // If no operator, treat as expression != 0
    return evaluateExpression(ctx, c) != 0;
}

// Execute GPC-style command
void executeGPCCommand(ScriptContext& ctx, const String& command) {
    String cmd = trim(command);
    
    // wait(ms) - delay
    if (cmd.startsWith("wait(") && cmd.endsWith(")")) {
        int ms = evaluateExpression(ctx, cmd.substring(5, cmd.length() - 1));
        delay(ms);
        return;
    }
    
    // set_val(button, value) - set gamepad button/axis
    if (cmd.startsWith("set_val(") && cmd.endsWith(")")) {
        String args = cmd.substring(8, cmd.length() - 1);
        int comma = args.indexOf(',');
        if (comma > 0) {
            String btnName = trim(args.substring(0, comma));
            int value = evaluateExpression(ctx, args.substring(comma + 1));
            
            // Map common GPC button names to gamepad
            btnName.toUpperCase();
            if (btnName == "PS4_CROSS" || btnName == "XB1_A") {
                if (value > 0) Gamepad.pressButton(1); else Gamepad.releaseButton(1);
            } else if (btnName == "PS4_CIRCLE" || btnName == "XB1_B") {
                if (value > 0) Gamepad.pressButton(2); else Gamepad.releaseButton(2);
            } else if (btnName == "PS4_SQUARE" || btnName == "XB1_X") {
                if (value > 0) Gamepad.pressButton(3); else Gamepad.releaseButton(3);
            } else if (btnName == "PS4_TRIANGLE" || btnName == "XB1_Y") {
                if (value > 0) Gamepad.pressButton(4); else Gamepad.releaseButton(4);
            }
        }
        return;
    }
    
    // combo_run(name) - placeholder for combo execution
    if (cmd.startsWith("combo_run(")) {
        // Would need combo definitions - for now just delay
        delay(100);
        return;
    }
}

// Execute single script line with context
bool executeScriptLine(ScriptContext& ctx, const String& line) {
    String trimmedLine = trim(line);
    
    // Skip empty lines and comments
    if (trimmedLine.length() == 0 || trimmedLine.startsWith("//") || trimmedLine.startsWith("REM ")) {
        return true;
    }
    
    // Variable assignment: VAR name = value
    if (trimmedLine.startsWith("VAR ") || trimmedLine.startsWith("var ")) {
        int eqPos = trimmedLine.indexOf('=');
        if (eqPos > 0) {
            String varName = trim(trimmedLine.substring(4, eqPos));
            String valueExpr = trim(trimmedLine.substring(eqPos + 1));
            
            // Check if it's a string assignment (quoted)
            if (valueExpr.startsWith("\"") && valueExpr.endsWith("\"")) {
                ctx.setStringVar(varName, valueExpr.substring(1, valueExpr.length() - 1));
            } else {
                int value = evaluateExpression(ctx, valueExpr);
                ctx.setVar(varName, value);
            }
        }
        return true;
    }
    
    // Assignment without VAR keyword: name = value
    if (trimmedLine.indexOf('=') > 0 && !trimmedLine.startsWith("IF ") && !trimmedLine.startsWith("if ")) {
        int eqPos = trimmedLine.indexOf('=');
        String varName = trim(trimmedLine.substring(0, eqPos));
        
        // Check if varName is valid identifier
        if (varName.length() > 0 && ((varName[0] >= 'a' && varName[0] <= 'z') || 
                                      (varName[0] >= 'A' && varName[0] <= 'Z') || 
                                      varName[0] == '_')) {
            String valueExpr = trim(trimmedLine.substring(eqPos + 1));
            
            if (valueExpr.startsWith("\"") && valueExpr.endsWith("\"")) {
                ctx.setStringVar(varName, valueExpr.substring(1, valueExpr.length() - 1));
            } else {
                int value = evaluateExpression(ctx, valueExpr);
                ctx.setVar(varName, value);
            }
            return true;
        }
    }
    
    // IF statement
    if (trimmedLine.startsWith("IF ") || trimmedLine.startsWith("if ")) {
        String condition = trim(trimmedLine.substring(3));
        
        // Remove trailing 'then' if present
        if (condition.endsWith(" THEN") || condition.endsWith(" then")) {
            condition = trim(condition.substring(0, condition.length() - 5));
        }
        
        bool result = evaluateCondition(ctx, condition);
        
        if (!result) {
            ctx.skipMode = true;
            ctx.skipDepth = 1;
        }
        return true;
    }
    
    // ELSE statement
    if (trimmedLine == "ELSE" || trimmedLine == "else") {
        if (ctx.skipDepth == 1) {
            ctx.skipMode = !ctx.skipMode;
        }
        return true;
    }
    
    // ENDIF statement
    if (trimmedLine == "ENDIF" || trimmedLine == "endif") {
        if (ctx.skipMode && ctx.skipDepth == 1) {
            ctx.skipMode = false;
            ctx.skipDepth = 0;
        }
        return true;
    }
    
    // LOOP statement: LOOP count
    if (trimmedLine.startsWith("LOOP ") || trimmedLine.startsWith("loop ")) {
        int count = evaluateExpression(ctx, trimmedLine.substring(5));
        ctx.loopStack.push_back(count);
        ctx.loopStartPositions.push_back(ctx.currentLine);
        return true;
    }
    
    // ENDLOOP statement
    if (trimmedLine == "ENDLOOP" || trimmedLine == "endloop") {
        if (!ctx.loopStack.empty()) {
            ctx.loopStack.back()--;
            
            if (ctx.loopStack.back() > 0) {
                // Continue loop - jump back to loop start
                ctx.currentLine = ctx.loopStartPositions.back();
            } else {
                // Loop finished
                ctx.loopStack.pop_back();
                ctx.loopStartPositions.pop_back();
            }
        }
        return true;
    }
    
    // FOR loop: FOR var = start TO end
    if (trimmedLine.startsWith("FOR ") || trimmedLine.startsWith("for ")) {
        // Simple FOR parsing: FOR i = 0 TO 10
        String forExpr = trimmedLine.substring(4);
        int eqPos = forExpr.indexOf('=');
        int toPos = forExpr.indexOf(" TO ");
        if (toPos < 0) toPos = forExpr.indexOf(" to ");
        
        if (eqPos > 0 && toPos > eqPos) {
            String varName = trim(forExpr.substring(0, eqPos));
            int startVal = evaluateExpression(ctx, forExpr.substring(eqPos + 1, toPos));
            int endVal = evaluateExpression(ctx, forExpr.substring(toPos + 4));
            
            ctx.setVar(varName, startVal);
            ctx.setVar("__FOR_END_" + varName, endVal);
            ctx.setVar("__FOR_VAR", 1); // Mark that we're in a FOR loop
            ctx.loopStartPositions.push_back(ctx.currentLine);
        }
        return true;
    }
    
    // NEXT statement (end of FOR loop)
    if (trimmedLine.startsWith("NEXT ") || trimmedLine.startsWith("next ")) {
        String varName = trim(trimmedLine.substring(5));
        int currentVal = ctx.getVar(varName);
        int endVal = ctx.getVar("__FOR_END_" + varName);
        
        currentVal++;
        ctx.setVar(varName, currentVal);
        
        if (currentVal <= endVal && !ctx.loopStartPositions.empty()) {
            ctx.currentLine = ctx.loopStartPositions.back();
        } else {
            if (!ctx.loopStartPositions.empty()) {
                ctx.loopStartPositions.pop_back();
            }
        }
        return true;
    }
    
    // GPC-style commands
    if (trimmedLine.indexOf('(') > 0 && trimmedLine.endsWith(")")) {
        executeGPCCommand(ctx, trimmedLine);
        return true;
    }
    
    // Pass through to existing processors (DuckyScript or Macro)
    // This allows mixing advanced scripting with existing commands
    processMacroText(trimmedLine);
    
    return true;
}

// Execute advanced script
void executeAdvancedScript(const String& script) {
    ScriptContext ctx;
    ctx.reset();
    
    // Split script into lines
    int start = 0;
    for (size_t i = 0; i <= script.length(); i++) {
        if (i == script.length() || script[i] == '\n') {
            ctx.lines.push_back(script.substring(start, i));
            start = i + 1;
        }
    }
    
    // Execute lines
    while (ctx.currentLine < ctx.lines.size()) {
        String line = ctx.lines[ctx.currentLine];
        
        if (!ctx.skipMode) {
            executeScriptLine(ctx, line);
        } else {
            // In skip mode - only watch for control flow
            String trimmedLine = trim(line);
            if (trimmedLine.startsWith("IF ") || trimmedLine.startsWith("if ")) {
                ctx.skipDepth++;
            } else if (trimmedLine == "ENDIF" || trimmedLine == "endif") {
                ctx.skipDepth--;
                if (ctx.skipDepth == 0) {
                    ctx.skipMode = false;
                }
            } else if (trimmedLine == "ELSE" || trimmedLine == "else") {
                if (ctx.skipDepth == 1) {
                    ctx.skipMode = false;
                }
            }
        }
        
        ctx.currentLine++;
    }
}

// Detect if script uses advanced features
bool isAdvancedScript(const String& content) {
    String upper = content;
    upper.toUpperCase();
    
    return upper.indexOf("VAR ") >= 0 ||
           upper.indexOf("\nIF ") >= 0 ||
           upper.indexOf("LOOP ") >= 0 ||
           upper.indexOf("FOR ") >= 0 ||
           upper.indexOf("WAIT(") >= 0 ||
           upper.indexOf("SET_VAL(") >= 0 ||
           content.indexOf('=') > 0;  // Assignment operator
}
