package com.pwdongle.recorder

import android.app.Dialog
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Fragment for live keyboard/mouse control via PWDongle
 * Sends commands in real-time without recording to a file
 */
class LiveControlFragment : Fragment() {
    
    private var statusText: TextView? = null
    private var eventLogText: TextView? = null
    private var startButton: Button? = null
    private var stopButton: Button? = null
    private var keyboardButton: Button? = null
    private var touchpadButton: Button? = null
    private var inputMethodSpinner: Spinner? = null
    
    private var isLiveControlActive = false
    private var logEventsEnabled = true  // Can be disabled to reduce overhead
    private val eventLog = mutableListOf<String>()
    private var bleManager: BLEManager? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_live_control, container, false)
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        statusText = view.findViewById(R.id.statusText)
        eventLogText = view.findViewById(R.id.eventLogText)
        startButton = view.findViewById(R.id.startButton)
        stopButton = view.findViewById(R.id.stopButton)
        keyboardButton = view.findViewById(R.id.keyboardButton)
        touchpadButton = view.findViewById(R.id.touchpadButton)
        inputMethodSpinner = view.findViewById(R.id.inputMethodSpinner)
        
        // Setup input method spinner
        val inputMethods = arrayOf("USB OTG Keyboard/Mouse", "On-Screen Keyboard", "On-Screen Touchpad")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, inputMethods)
        inputMethodSpinner?.adapter = adapter
        
        // Setup button listeners
        startButton?.setOnClickListener { startLiveControl() }
        stopButton?.setOnClickListener { stopLiveControl() }
        keyboardButton?.setOnClickListener { showKeyboardDialog() }
        touchpadButton?.setOnClickListener { showTouchpadDialog() }
        
        // Get BLE manager from activity context
        bleManager = try {
            BLEManager.getInstance(requireContext()) { status ->
                updateStatus(status)
            }
        } catch (e: Exception) {
            logEvent("ERROR: Could not initialize BLE Manager")
            null
        }
        
        updateStatus()
        logEvent("Live Control ready")
    }
    
    private fun startLiveControl() {
        if (bleManager?.isConnected() != true) {
            updateStatus("Not connected to PWDongle")
            logEvent("ERROR: Connect to PWDongle first")
            return
        }
        
        isLiveControlActive = true
        logEventsEnabled = false  // Disable logging to reduce latency
        startButton?.isEnabled = false
        stopButton?.isEnabled = true
        inputMethodSpinner?.isEnabled = false
        
        updateStatus("Live Control: ACTIVE")
        logEvent("Live Control STARTED")
        
        // Register as keyboard/mouse event listener
        (activity as? MainActivity)?.setKeyboardEventListener(object : KeyboardEventListener {
            override fun onKeyboardEvent(keyCode: Int, action: Int): Boolean {
                if (isLiveControlActive) {
                    sendKeyboardCommand(keyCode, action)
                }
                return false
            }
        })
        
        (activity as? MainActivity)?.setMouseEventListener(object : MouseEventListener {
            override fun onMouseEvent(x: Int, y: Int, action: Int): Boolean {
                if (isLiveControlActive) {
                    sendMouseCommand(x, y, action)
                }
                return false
            }
        })
    }
    
    private fun stopLiveControl() {
        isLiveControlActive = false
        logEventsEnabled = true  // Re-enable logging
        startButton?.isEnabled = true
        stopButton?.isEnabled = false
        inputMethodSpinner?.isEnabled = true
        
        updateStatus("Live Control: STOPPED")
        logEvent("Live Control STOPPED")
        
        // Unregister event listeners
        (activity as? MainActivity)?.setKeyboardEventListener(null)
        (activity as? MainActivity)?.setMouseEventListener(null)
    }
    
    private fun sendKeyboardCommand(keyCode: Int, action: Int) {
        // Convert Android keyCode to key name (a-z, 0-9, enter, etc.)
        val keyName = keyCodeToKeyName(keyCode)
        if (keyName.isEmpty()) {
            logEvent("WARN: Unknown keyCode $keyCode")
            return
        }
        
        // Use standard legacy format: KEY:keyName_DOWN/UP
        val actionStr = if (action == 0) "DOWN" else "UP"
        val command = "KEY:${keyName}_$actionStr"
        
        try {
            bleManager?.sendCommandLowLatency(command)
        } catch (e: Exception) {
            logEvent("ERROR: ${e.message}")
        }
    }
    
    /**
     * Convert Android KeyCode to PWDongle-compatible key name
     */
    private fun keyCodeToKeyName(keyCode: Int): String {
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_0 -> "0"
            android.view.KeyEvent.KEYCODE_1 -> "1"
            android.view.KeyEvent.KEYCODE_2 -> "2"
            android.view.KeyEvent.KEYCODE_3 -> "3"
            android.view.KeyEvent.KEYCODE_4 -> "4"
            android.view.KeyEvent.KEYCODE_5 -> "5"
            android.view.KeyEvent.KEYCODE_6 -> "6"
            android.view.KeyEvent.KEYCODE_7 -> "7"
            android.view.KeyEvent.KEYCODE_8 -> "8"
            android.view.KeyEvent.KEYCODE_9 -> "9"
            android.view.KeyEvent.KEYCODE_A -> "a"
            android.view.KeyEvent.KEYCODE_B -> "b"
            android.view.KeyEvent.KEYCODE_C -> "c"
            android.view.KeyEvent.KEYCODE_D -> "d"
            android.view.KeyEvent.KEYCODE_E -> "e"
            android.view.KeyEvent.KEYCODE_F -> "f"
            android.view.KeyEvent.KEYCODE_G -> "g"
            android.view.KeyEvent.KEYCODE_H -> "h"
            android.view.KeyEvent.KEYCODE_I -> "i"
            android.view.KeyEvent.KEYCODE_J -> "j"
            android.view.KeyEvent.KEYCODE_K -> "k"
            android.view.KeyEvent.KEYCODE_L -> "l"
            android.view.KeyEvent.KEYCODE_M -> "m"
            android.view.KeyEvent.KEYCODE_N -> "n"
            android.view.KeyEvent.KEYCODE_O -> "o"
            android.view.KeyEvent.KEYCODE_P -> "p"
            android.view.KeyEvent.KEYCODE_Q -> "q"
            android.view.KeyEvent.KEYCODE_R -> "r"
            android.view.KeyEvent.KEYCODE_S -> "s"
            android.view.KeyEvent.KEYCODE_T -> "t"
            android.view.KeyEvent.KEYCODE_U -> "u"
            android.view.KeyEvent.KEYCODE_V -> "v"
            android.view.KeyEvent.KEYCODE_W -> "w"
            android.view.KeyEvent.KEYCODE_X -> "x"
            android.view.KeyEvent.KEYCODE_Y -> "y"
            android.view.KeyEvent.KEYCODE_Z -> "z"
            android.view.KeyEvent.KEYCODE_ENTER -> "enter"
            android.view.KeyEvent.KEYCODE_TAB -> "tab"
            android.view.KeyEvent.KEYCODE_SPACE -> "space"
            android.view.KeyEvent.KEYCODE_DEL -> "backspace"
            android.view.KeyEvent.KEYCODE_FORWARD_DEL -> "delete"
            android.view.KeyEvent.KEYCODE_ESCAPE -> "esc"
            android.view.KeyEvent.KEYCODE_HOME -> "home"
            260 -> "end"  // KEYCODE_END = 260
            android.view.KeyEvent.KEYCODE_PAGE_UP -> "pageup"
            android.view.KeyEvent.KEYCODE_PAGE_DOWN -> "pagedown"
            android.view.KeyEvent.KEYCODE_DPAD_UP -> "up"
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> "down"
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> "left"
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
            android.view.KeyEvent.KEYCODE_SHIFT_LEFT -> "shift"
            android.view.KeyEvent.KEYCODE_SHIFT_RIGHT -> "shift"
            android.view.KeyEvent.KEYCODE_CTRL_LEFT -> "ctrl"
            android.view.KeyEvent.KEYCODE_CTRL_RIGHT -> "ctrl"
            android.view.KeyEvent.KEYCODE_ALT_LEFT -> "alt"
            android.view.KeyEvent.KEYCODE_ALT_RIGHT -> "alt"
            else -> ""  // Unknown key
        }
    }
    
    private fun sendMouseCommand(x: Int, y: Int, action: Int) {
        // Use standard legacy format: MOUSE:x_y_ACTION
        val actionStr = when (action) {
            0 -> "MOVE"      // M=MOVE
            1 -> "LCLICK"    // L=LEFT_CLICK
            2 -> "RCLICK"    // R=RIGHT_CLICK
            else -> "MOVE"   // Default to MOVE
        }
        val command = "MOUSE:${x}_${y}_$actionStr"
        
        try {
            bleManager?.sendCommandLowLatency(command)
        } catch (e: Exception) {
            logEvent("ERROR: ${e.message}")
        }
    }
    
    private fun showKeyboardDialog() {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Material_Dialog)
        dialog.setContentView(R.layout.dialog_keyboard)
        dialog.show()
    }
    
    private fun showTouchpadDialog() {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Material_Dialog)
        dialog.setContentView(R.layout.dialog_touchpad)
        dialog.show()
    }
    
    private fun logEvent(message: String) {
        if (!logEventsEnabled) return  // Skip logging if disabled for performance
        
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        eventLog.add("[$timestamp] $message")
        if (eventLog.size > 50) eventLog.removeAt(0)
        
        eventLogText?.text = eventLog.joinToString("\n")
    }
    
    private fun updateStatus(message: String? = null) {
        val status = message ?: if (bleManager?.isConnected() == true) {
            "Status: Connected to PWDongle"
        } else {
            "Status: Not connected"
        }
        statusText?.text = status
    }
}
