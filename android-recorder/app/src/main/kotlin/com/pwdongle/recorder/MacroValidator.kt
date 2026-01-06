package com.pwdongle.recorder

import android.util.Log

/**
 * Validates PWDongle macro files before playback
 * Checks syntax, estimates duration, and provides warnings
 */
class MacroValidator {
    
    companion object {
        private const val TAG = "MacroValidator"
    }
    
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
        val estimatedDurationMs: Long = 0,
        val commandCount: Int = 0,
        val hasLongDelays: Boolean = false
    )
    
    /**
     * Validate a macro file content
     */
    fun validate(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var totalDelayMs = 0L
        var commandCount = 0
        var hasLongDelays = false
        
        val lines = content.lines()
        
        if (lines.isEmpty() || content.trim().isEmpty()) {
            errors.add("Empty macro file")
            return ValidationResult(false, errors)
        }
        
        lines.forEachIndexed { index, line ->
            val lineNum = index + 1
            val trimmed = line.trim()
            
            // Skip empty lines and comments
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#")) {
                return@forEachIndexed
            }
            
            // Check for PWDongle macro format {{TOKEN:ARGS}}
            if (trimmed.startsWith("{{") && trimmed.endsWith("}}")) {
                commandCount++
                val content = trimmed.substring(2, trimmed.length - 2)
                val parts = content.split(":", limit = 2)
                val token = parts[0].uppercase()
                val args = if (parts.size > 1) parts[1] else ""
                
                when (token) {
                    "DELAY" -> {
                        try {
                            val delayMs = args.toLong()
                            totalDelayMs += delayMs
                            
                            if (delayMs > 10000) {
                                warnings.add("Line $lineNum: Long delay ${delayMs}ms (${delayMs/1000}s)")
                                hasLongDelays = true
                            }
                            if (delayMs < 0) {
                                errors.add("Line $lineNum: Negative delay not allowed")
                            }
                        } catch (e: NumberFormatException) {
                            errors.add("Line $lineNum: Invalid delay value '$args'")
                        }
                    }
                    "SPEED" -> {
                        try {
                            val speed = args.toInt()
                            if (speed < 1 || speed > 100) {
                                warnings.add("Line $lineNum: Speed $speed out of range (1-100)")
                            }
                        } catch (e: NumberFormatException) {
                            errors.add("Line $lineNum: Invalid speed value '$args'")
                        }
                    }
                    "KEY" -> {
                        if (args.isEmpty()) {
                            errors.add("Line $lineNum: KEY requires key name argument")
                        }
                        // Common key names validation
                        val validKeys = listOf(
                            "enter", "esc", "tab", "space", "backspace", "delete",
                            "up", "down", "left", "right",
                            "home", "end", "pageup", "pagedown",
                            "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "f10", "f11", "f12",
                            "ctrl", "shift", "alt", "gui", "win"
                        )
                        val keyLower = args.lowercase()
                        if (!validKeys.contains(keyLower) && keyLower.length != 1) {
                            warnings.add("Line $lineNum: Unknown key '$args' (may not be supported)")
                        }
                    }
                    "TYPE", "TEXT" -> {
                        if (args.isEmpty()) {
                            warnings.add("Line $lineNum: Empty text to type")
                        }
                        // Estimate typing time (typical ~100ms per character)
                        totalDelayMs += args.length * 100L
                    }
                    "MOUSE" -> {
                        // Format: x,y or button
                        if (args.isEmpty()) {
                            errors.add("Line $lineNum: MOUSE requires arguments")
                        } else if (args.contains(",")) {
                            val coords = args.split(",")
                            if (coords.size != 2) {
                                errors.add("Line $lineNum: MOUSE coordinates require exactly 2 values (x,y)")
                            } else {
                                try {
                                    coords[0].trim().toInt()
                                    coords[1].trim().toInt()
                                } catch (e: NumberFormatException) {
                                    errors.add("Line $lineNum: Invalid mouse coordinates '$args'")
                                }
                            }
                        }
                    }
                    "GAMEPAD" -> {
                        warnings.add("Line $lineNum: Gamepad commands may not be supported by device")
                    }
                    "AUDIO" -> {
                        warnings.add("Line $lineNum: Audio commands may not be supported by device")
                    }
                    else -> {
                        warnings.add("Line $lineNum: Unknown token '$token'")
                    }
                }
            }
            // Check for DuckyScript format
            else if (trimmed.matches(Regex("^(REM|DELAY|STRING|STRINGLN|GUI|CTRL|ALT|SHIFT|ENTER|ESC|TAB)\\b.*", RegexOption.IGNORE_CASE))) {
                commandCount++
                val parts = trimmed.split(Regex("\\s+"), limit = 2)
                val command = parts[0].uppercase()
                
                when (command) {
                    "DELAY" -> {
                        if (parts.size < 2) {
                            errors.add("Line $lineNum: DELAY requires millisecond value")
                        } else {
                            try {
                                val delayMs = parts[1].toLong()
                                totalDelayMs += delayMs
                                if (delayMs > 10000) {
                                    warnings.add("Line $lineNum: Long delay ${delayMs}ms")
                                    hasLongDelays = true
                                }
                            } catch (e: NumberFormatException) {
                                errors.add("Line $lineNum: Invalid DELAY value '${parts[1]}'")
                            }
                        }
                    }
                    "STRING", "STRINGLN" -> {
                        if (parts.size < 2) {
                            warnings.add("Line $lineNum: Empty string to type")
                        } else {
                            totalDelayMs += parts[1].length * 100L
                        }
                    }
                    "REM" -> {
                        // Comments are fine, don't count as commands
                        commandCount--
                    }
                }
            }
            // Check for Advanced scripting format
            else if (trimmed.matches(Regex("^(VAR|IF|ELSE|ENDIF|LOOP|ENDLOOP|FOR|NEXT|wait|set_val)\\b.*", RegexOption.IGNORE_CASE))) {
                commandCount++
                warnings.add("Line $lineNum: Advanced scripting detected - validation limited")
                
                // Check for wait() calls to estimate time
                if (trimmed.contains(Regex("wait\\s*\\(\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE))) {
                    val match = Regex("wait\\s*\\(\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE).find(trimmed)
                    match?.let {
                        val waitMs = it.groupValues[1].toLongOrNull() ?: 0
                        totalDelayMs += waitMs
                    }
                }
            }
            else {
                // Unknown format
                if (trimmed.isNotEmpty()) {
                    warnings.add("Line $lineNum: Unknown command format '$trimmed'")
                }
            }
        }
        
        // Final validation checks
        if (commandCount == 0) {
            errors.add("No valid commands found in macro")
        }
        
        if (totalDelayMs > 300000) { // 5 minutes
            warnings.add("Total estimated duration is very long: ${totalDelayMs/1000}s (${totalDelayMs/60000}min)")
        }
        
        val isValid = errors.isEmpty()
        
        Log.d(TAG, "Validation result: valid=$isValid, commands=$commandCount, duration=${totalDelayMs}ms, errors=${errors.size}, warnings=${warnings.size}")
        
        return ValidationResult(
            isValid = isValid,
            errors = errors,
            warnings = warnings,
            estimatedDurationMs = totalDelayMs,
            commandCount = commandCount,
            hasLongDelays = hasLongDelays
        )
    }
    
    /**
     * Format duration in human-readable format
     */
    fun formatDuration(ms: Long): String {
        return when {
            ms < 1000 -> "${ms}ms"
            ms < 60000 -> String.format("%.1fs", ms / 1000.0)
            else -> String.format("%dm %ds", ms / 60000, (ms % 60000) / 1000)
        }
    }
}
