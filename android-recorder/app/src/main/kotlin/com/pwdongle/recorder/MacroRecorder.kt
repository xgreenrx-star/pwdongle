package com.pwdongle.recorder

import android.util.Log

/**
 * Macro Recorder
 * 
 * Records keyboard and mouse events with accurate timing
 * Generates PWDongle macro format with delays
 */
class MacroRecorder {
    
    companion object {
        private const val TAG = "MacroRecorder"
        private const val DELAY_THRESHOLD_MS = 200 // Only record delays >200ms (firmware processing adds ~50-100ms per command)
    }
    
    private var isRecording = false
    private var filename: String = ""
    private val events = mutableListOf<RecordedEvent>()
    private var startTime: Long = 0
    private var lastEventTime: Long = 0
    
    sealed class RecordedEvent {
        data class Key(val name: String, val isDown: Boolean, val timestamp: Long) : RecordedEvent()
        data class MouseMove(val x: Int, val y: Int, val timestamp: Long) : RecordedEvent()
        data class MouseButton(val button: String, val isDown: Boolean, val timestamp: Long) : RecordedEvent()
        data class MouseScroll(val amount: Int, val timestamp: Long) : RecordedEvent()
        data class MouseHScroll(val amount: Int, val timestamp: Long) : RecordedEvent()
        data class Delay(val ms: Long) : RecordedEvent()
    }
    
    fun startRecording(filename: String) {
        this.filename = filename
        this.isRecording = true
        this.events.clear()
        this.startTime = System.currentTimeMillis()
        this.lastEventTime = startTime
        
        Log.d(TAG, "Recording started: $filename")
    }
    
    fun stopRecording(): List<String> {
        isRecording = false
        val duration = System.currentTimeMillis() - startTime
        Log.d(TAG, "Recording stopped. Duration: ${duration}ms, Events: ${events.size}")
        
        return generateMacro()
    }
    
    fun recordKey(keyName: String, action: Int) {
        if (!isRecording) return
        
        val now = System.currentTimeMillis()
        addDelayIfNeeded(now)
        
        // Only record key down events (PWDongle KEY command does press+release)
        if (action == android.view.KeyEvent.ACTION_DOWN) {
            events.add(RecordedEvent.Key(keyName, true, now))
            Log.d(TAG, "Recorded key: $keyName")
        }
        
        lastEventTime = now
    }
    
    fun recordMouseMove(x: Int, y: Int) {
        if (!isRecording) return
        
        val now = System.currentTimeMillis()
        addDelayIfNeeded(now)
        
        events.add(RecordedEvent.MouseMove(x, y, now))
        lastEventTime = now
    }
    
    fun recordMouseButton(button: String, isDown: Boolean) {
        if (!isRecording) return
        
        val now = System.currentTimeMillis()
        addDelayIfNeeded(now)
        
        events.add(RecordedEvent.MouseButton(button, isDown, now))
        Log.d(TAG, "Recorded mouse button: $button ${if (isDown) "down" else "up"}")
        
        lastEventTime = now
    }
    
    fun recordMouseScroll(amount: Int) {
        if (!isRecording) return
        
        val now = System.currentTimeMillis()
        addDelayIfNeeded(now)
        
        events.add(RecordedEvent.MouseScroll(amount, now))
        Log.d(TAG, "Recorded scroll: $amount")
        
        lastEventTime = now
    }

    fun recordMouseHScroll(amount: Int) {
        if (!isRecording) return
        val now = System.currentTimeMillis()
        addDelayIfNeeded(now)
        events.add(RecordedEvent.MouseHScroll(amount, now))
        Log.d(TAG, "Recorded hscroll: $amount")
        lastEventTime = now
    }
    
    private fun addDelayIfNeeded(now: Long) {
        val delay = now - lastEventTime
        if (delay >= DELAY_THRESHOLD_MS) {
            events.add(RecordedEvent.Delay(delay))
        }
    }
    
    private fun generateMacro(): List<String> {
        val macro = mutableListOf<String>()
        
        // Add header comment
        macro.add("// Recorded macro: $filename")
        macro.add("// Duration: ${System.currentTimeMillis() - startTime}ms")
        macro.add("// Events: ${events.size}")
        macro.add("")
        
        // Add initial RESET to move mouse to (0,0)
        macro.add("{{MOUSE:RESET}}")
        macro.add("{{DELAY:100}}")
        macro.add("")
        
        // Convert events to macro format
        for (event in events) {
            when (event) {
                is RecordedEvent.Delay -> {
                    macro.add("{{DELAY:${event.ms}}}")
                }
                is RecordedEvent.Key -> {
                    macro.add("{{KEY:${event.name}}}")
                }
                is RecordedEvent.MouseMove -> {
                    macro.add("{{MOUSE:MOVE:${event.x},${event.y}}}")
                }
                is RecordedEvent.MouseButton -> {
                    val action = if (event.isDown) "DOWN" else "UP"
                    macro.add("{{MOUSE:$action:${event.button}}}")
                }
                is RecordedEvent.MouseScroll -> {
                    macro.add("{{MOUSE:SCROLL:${event.amount}}}")
                }
                is RecordedEvent.MouseHScroll -> {
                    macro.add("{{MOUSE:HSCROLL:${event.amount}}}")
                }
            }
        }
        
        return macro
    }
    
    fun getMacroAsString(): String {
        return generateMacro().joinToString("\n")
    }
}
