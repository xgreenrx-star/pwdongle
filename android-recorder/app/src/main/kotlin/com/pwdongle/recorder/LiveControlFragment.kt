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
        val actionStr = if (action == 0) "DOWN" else "UP"
        val command = "KEY:${keyCode}_${actionStr}"
        
        try {
            // Direct send without coroutine overhead for low latency
            bleManager?.sendCommandLowLatency(command)
        } catch (e: Exception) {
            logEvent("ERROR: ${e.message}")
        }
    }
    
    private fun sendMouseCommand(x: Int, y: Int, action: Int) {
        val actionStr = when (action) {
            0 -> "MOVE"
            1 -> "LCLICK"
            2 -> "RCLICK"
            else -> "ACTION_$action"
        }
        val command = "MOUSE:${x}_${y}_${actionStr}"
        
        try {
            // Direct send without coroutine overhead for low latency
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
