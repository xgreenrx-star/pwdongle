package com.pwdongle.recorder

import android.util.Log

/**
 * Macro playback engine
 * Parses and executes PWDongle macro files
 */
class MacroPlayer(
    private val onKeyCommand: (String) -> Unit,
    private val onMouseCommand: (String) -> Unit,
    private val onTypeCommand: (String) -> Unit,
    private val onStatusChange: (String) -> Unit,
    private val speedMultiplier: Float = 1f
) {
    
    companion object {
        private const val TAG = "MacroPlayer"
    }
    
    private var isPlaying = false
    
    /**
     * Play macro content line by line
     */
    suspend fun playMacro(content: String) {
        isPlaying = true
        val lines = content.split("\n")
        
        try {
            var eventCount = 0
            for (line in lines) {
                if (!isPlaying) break
                
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("//")) continue
                
                processLine(trimmed)
                eventCount++
            }
            
            onStatusChange("Playback complete! Executed $eventCount commands.")
        } catch (e: Exception) {
            Log.e(TAG, "Playback error: ${e.message}")
            onStatusChange("Playback error: ${e.message}")
        } finally {
            isPlaying = false
        }
    }
    
    private suspend fun processLine(line: String) {
        // Parse {{TOKEN:ARGS}} format
        val match = Regex("\\{\\{([A-Z]+)(?::([^}]*))?\\}\\}").find(line)
        if (match == null) {
            // Raw text - type it
            onTypeCommand(line)
            return
        }
        
        val token = match.groupValues[1]
        val args = match.groupValues[2]
        
        when (token) {
            "DELAY" -> {
                val rawMs = args.toLongOrNull() ?: 100L
                val scaledMs = (rawMs / speedMultiplier).toLong().coerceAtLeast(0)
                kotlinx.coroutines.delay(scaledMs)
                onStatusChange("Delay: ${scaledMs}ms (x${speedMultiplier})")
            }
            "KEY" -> {
                onKeyCommand(args)
                onStatusChange("Key: $args")
            }
            "MOUSE" -> {
                onMouseCommand(args)
                onStatusChange("Mouse: $args")
            }
            "TYPE", "TEXT" -> {
                onTypeCommand(args)
                onStatusChange("Type: $args")
            }
            "GAMEPAD" -> {
                onStatusChange("Gamepad: $args (not supported)")
            }
            "AUDIO" -> {
                onStatusChange("Audio: $args (not supported)")
            }
            else -> {
                Log.w(TAG, "Unknown token: $token")
            }
        }
    }
    
    fun stop() {
        isPlaying = false
    }
    
    fun isPlayingMacro(): Boolean = isPlaying
}
