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
    private val onProgressUpdate: ((Int, Int, Long, Long) -> Unit)? = null, // current, total, elapsedMs, estimatedTotalMs
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
        val lines = content.split("\n").filter { it.trim().isNotEmpty() && !it.trim().startsWith("//") }
        val totalLines = lines.size
        val startTime = System.currentTimeMillis()
        
        // Estimate total duration by scanning for delays
        val estimatedDuration = estimateDuration(content)
        
        try {
            var eventCount = 0
            for ((index, line) in lines.withIndex()) {
                if (!isPlaying) break
                
                val trimmed = line.trim()
                
                // Update progress
                val elapsedMs = System.currentTimeMillis() - startTime
                val progress = ((index + 1) * 100 / totalLines)
                onProgressUpdate?.invoke(index + 1, totalLines, elapsedMs, estimatedDuration)
                
                processLine(trimmed)
                eventCount++
            }
            
            val totalTime = System.currentTimeMillis() - startTime
            onStatusChange("Complete: $eventCount cmds in ${totalTime/1000}s")
        } catch (e: Exception) {
            Log.e(TAG, "Playback error: ${e.message}")
            onStatusChange("Playback error: ${e.message}")
        } finally {
            isPlaying = false
        }
    }
    
    /**
     * Estimate total playback duration in milliseconds
     */
    private fun estimateDuration(content: String): Long {
        var totalMs = 0L
        val lines = content.split("\n")
        
        for (line in lines) {
            val match = Regex("\\{\\{([A-Z]+)(?::([^}]*))?\\}\\}").find(line.trim())
            if (match != null) {
                val token = match.groupValues[1]
                val args = match.groupValues[2]
                
                when (token) {
                    "DELAY" -> {
                        val rawMs = args.toLongOrNull() ?: 100L
                        totalMs += (rawMs / speedMultiplier).toLong()
                    }
                    "TYPE", "TEXT" -> {
                        totalMs += (args.length * 100L / speedMultiplier).toLong() // ~100ms per char
                    }
                    "KEY", "MOUSE" -> {
                        totalMs += 50 // Command delay
                    }
                }
            }
        }
        
        return totalMs
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
                onStatusChange("Wait ${scaledMs}ms")
            }
            "KEY" -> {
                onKeyCommand(args)
                kotlinx.coroutines.delay(50) // Brief delay to allow BLE send
                onStatusChange("Key: $args")
            }
            "MOUSE" -> {
                onMouseCommand(args)
                kotlinx.coroutines.delay(50)
                onStatusChange("Mouse: $args")
            }
            "TYPE", "TEXT" -> {
                onTypeCommand(args)
                kotlinx.coroutines.delay(50)
                onStatusChange("Type: $args")
            }
            "GAMEPAD" -> {
                onStatusChange("Gamepad (no support)")
            }
            "AUDIO" -> {
                onStatusChange("Audio (no support)")
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
