package com.pwdongle.recorder

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import android.util.Log

/**
 * Input Capture Service
 * 
 * Captures keyboard and mouse events from USB OTG devices
 * Requires USB host mode and proper permissions
 */
class InputCaptureService(
    private val context: Context,
    private val onKeyEvent: (keyCode: Int, action: Int) -> Unit,
    private val onMouseMove: (x: Int, y: Int) -> Unit,
    private val onMouseButton: (button: Int, isDown: Boolean) -> Unit,
    private val onMouseScroll: (amount: Int) -> Unit
) {
    
    companion object {
        private const val TAG = "InputCapture"
    }
    
    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private var isCapturing = false
    
    // Track current mouse position
    private var mouseX = 0
    private var mouseY = 0
    
    // Input device listener
    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            val device = InputDevice.getDevice(deviceId)
            Log.d(TAG, "Input device added: ${device?.name} (ID: $deviceId)")
            
            // Check if it's a keyboard or mouse
            val sources = device?.sources ?: 0
            if (sources and InputDevice.SOURCE_KEYBOARD != 0) {
                Log.d(TAG, "Keyboard detected: ${device?.name ?: "Unknown"}")
            }
            if (sources and InputDevice.SOURCE_MOUSE != 0) {
                Log.d(TAG, "Mouse detected: ${device?.name ?: "Unknown"}")
            }
        }
        
        override fun onInputDeviceRemoved(deviceId: Int) {
            Log.d(TAG, "Input device removed: $deviceId")
        }
        
        override fun onInputDeviceChanged(deviceId: Int) {
            Log.d(TAG, "Input device changed: $deviceId")
        }
    }
    
    fun start() {
        if (isCapturing) return
        
        isCapturing = true
        inputManager.registerInputDeviceListener(deviceListener, null)
        
        Log.d(TAG, "Input capture started")
        
        // List current input devices
        val deviceIds = InputDevice.getDeviceIds()
        Log.d(TAG, "Available input devices: ${deviceIds.size}")
        for (deviceId in deviceIds) {
            val device = InputDevice.getDevice(deviceId)
            Log.d(TAG, "  - ${device?.name} (ID: $deviceId, Sources: ${device?.sources})")
        }
    }
    
    fun stop() {
        if (!isCapturing) return
        
        isCapturing = false
        inputManager.unregisterInputDeviceListener(deviceListener)
        
        Log.d(TAG, "Input capture stopped")
    }
    
    /**
     * Handle key events from USB keyboard
     * Call this from Activity's dispatchKeyEvent()
     */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!isCapturing) return false
        
        // Check if event is from external keyboard
        val device = event.device
        if (device == null || device.sources and InputDevice.SOURCE_KEYBOARD == 0) {
            return false
        }
        
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                Log.d(TAG, "Key down: ${event.keyCode}")
                onKeyEvent(event.keyCode, KeyEvent.ACTION_DOWN)
            }
            KeyEvent.ACTION_UP -> {
                Log.d(TAG, "Key up: ${event.keyCode}")
                onKeyEvent(event.keyCode, KeyEvent.ACTION_UP)
            }
        }
        
        return true // Consume the event
    }
    
    /**
     * Handle generic motion events from USB mouse
     * Call this from Activity's dispatchGenericMotionEvent()
     */
    fun handleMotionEvent(event: MotionEvent): Boolean {
        if (!isCapturing) return false
        
        // Check if event is from external mouse
        val device = event.device
        if (device == null || device.sources and InputDevice.SOURCE_MOUSE == 0) {
            return false
        }
        
        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                val x = event.x.toInt()
                val y = event.y.toInt()
                
                if (x != mouseX || y != mouseY) {
                    mouseX = x
                    mouseY = y
                    onMouseMove(x, y)
                }
            }
            MotionEvent.ACTION_HOVER_MOVE -> {
                val x = event.x.toInt()
                val y = event.y.toInt()
                
                if (x != mouseX || y != mouseY) {
                    mouseX = x
                    mouseY = y
                    onMouseMove(x, y)
                }
            }
            MotionEvent.ACTION_BUTTON_PRESS -> {
                val button = getButtonIndex(event.actionButton)
                Log.d(TAG, "Mouse button down: $button")
                onMouseButton(button, true)
            }
            MotionEvent.ACTION_BUTTON_RELEASE -> {
                val button = getButtonIndex(event.actionButton)
                Log.d(TAG, "Mouse button up: $button")
                onMouseButton(button, false)
            }
            MotionEvent.ACTION_SCROLL -> {
                val vscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL).toInt()
                if (vscroll != 0) {
                    Log.d(TAG, "Mouse scroll: $vscroll")
                    onMouseScroll(vscroll)
                }
            }
        }
        
        return true // Consume the event
    }
    
    private fun getButtonIndex(button: Int): Int {
        return when (button) {
            MotionEvent.BUTTON_PRIMARY -> 0    // Left
            MotionEvent.BUTTON_SECONDARY -> 1  // Right
            MotionEvent.BUTTON_TERTIARY -> 2   // Middle
            else -> -1
        }
    }
}
