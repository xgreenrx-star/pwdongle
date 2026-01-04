package com.pwdongle.recorder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.launch

/**
 * Input devices fragment - manage hardware and on-screen input
 */
class InputFragment : Fragment() {
    
    private lateinit var statusText: TextView
    private lateinit var hardwareButton: Button
    private lateinit var keyboardButton: Button
    private lateinit var touchpadButton: Button
    private lateinit var exitButton: Button
    private lateinit var scanButton: Button
    private lateinit var devicesList: ListView
    
    private var bleManager: BLEManager? = null
    private var scannedDevices = mutableListOf<String>()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_input, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        statusText = view.findViewById(R.id.statusText)
        hardwareButton = view.findViewById(R.id.hardwareButton)
        keyboardButton = view.findViewById(R.id.keyboardButton)
        touchpadButton = view.findViewById(R.id.touchpadButton)
        exitButton = view.findViewById(R.id.exitButton)
        scanButton = view.findViewById(R.id.scanButton)
        devicesList = view.findViewById(R.id.devicesList)
        
        try {
            bleManager = BLEManager.getInstance(requireContext()) { status ->
                try {
                    requireActivity().runOnUiThread {
                        statusText.text = status
                    }
                } catch (e: Exception) { /* ignore */ }
            }
        } catch (e: Exception) {
            statusText.text = "BLE unavailable"
        }

        if (bleManager?.isConnected() == true) {
            statusText.text = "Connected to PWDongle"
            scanButton.isEnabled = true
        }
        
        setupListeners()
    }
    
    private fun setupListeners() {
        // Scan for BLE devices
        scanButton.setOnClickListener {
            if (bleManager?.isConnected() == true) {
                statusText.text = "Connected to PWDongle"
                return@setOnClickListener
            }
            android.util.Log.d("InputFragment", "Scan button clicked")
            scannedDevices.clear()
            devicesList.adapter = null
            statusText.text = "Scanning... (Make sure Bluetooth is ON)"
            scanButton.isEnabled = false
            
            try {
                android.util.Log.d("InputFragment", "Starting BLE scan")
                bleManager?.scanForDevices { devices ->
                    android.util.Log.d("InputFragment", "Scan completed. Found ${devices.size} devices")
                    try {
                        if (isAdded) {
                            requireActivity().runOnUiThread {
                                scannedDevices.clear()
                                scannedDevices.addAll(devices)
                                
                                if (devices.isEmpty()) {
                                    statusText.text = "No devices found. Ensure Bluetooth is ON and devices are in range."
                                } else {
                                    statusText.text = "Found ${devices.size} device(s)"
                                    
                                    val adapter = ArrayAdapter(
                                        requireContext(),
                                        android.R.layout.simple_list_item_1,
                                        scannedDevices
                                    )
                                    devicesList.adapter = adapter
                                    
                                    // Set click listener for device selection
                                    if (devicesList.onItemClickListener == null) {
                                        devicesList.setOnItemClickListener { _, _, position, _ ->
                                            val selectedDevice = scannedDevices[position]
                                            statusText.text = "Connecting to: $selectedDevice"
                                            bleManager?.connectToDevice(selectedDevice) { result ->
                                                try {
                                                    if (isAdded) {
                                                        requireActivity().runOnUiThread {
                                                            statusText.text = result
                                                        }
                                                    }
                                                } catch (e: Exception) { /* ignore */ }
                                            }
                                        }
                                    }
                                }
                                
                                scanButton.isEnabled = true
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("InputFragment", "Scan callback failed", e)
                        if (isAdded) {
                            statusText.text = "Error: ${e.message}"
                            scanButton.isEnabled = true
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("InputFragment", "Scan failed", e)
                statusText.text = "Scan failed: ${e.message}"
                scanButton.isEnabled = true
            }
        }
        
        hardwareButton.setOnClickListener {
            statusText.text = "Listening for USB OTG keyboard/mouse..."
            Toast.makeText(context, "Connect USB OTG devices to smartphone", Toast.LENGTH_LONG).show()
        }
        
        keyboardButton.setOnClickListener {
            showOnScreenKeyboard()
        }
        
        touchpadButton.setOnClickListener {
            showOnScreenTouchpad()
        }

        exitButton.setOnClickListener {
            showExitConfirmation()
        }
    }

    private fun showExitConfirmation() {
        val act = activity ?: return
        AlertDialog.Builder(act)
            .setTitle("Exit")
            .setMessage("Are you sure you want to exit?")
            .setPositiveButton("Exit") { _, _ ->
                Toast.makeText(context, "Exiting...", Toast.LENGTH_SHORT).show()
                try {
                    act.finishAffinity()
                } catch (_: Exception) {
                    act.finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showOnScreenKeyboard() {
        if (bleManager == null) {
            Toast.makeText(context, "BLE not available", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_keyboard, null)
        val keyboardView = dialogView.findViewById<KeyboardView>(R.id.keyboardView)

        keyboardView.setOnTextInputListener { text ->
            bleManager?.sendCommand("TYPE:$text")
        }

        keyboardView.setOnKeyPressListener { keyName ->
            val mapped = when (keyName.lowercase()) {
                "backspace" -> "KEY:backspace"
                "enter" -> "KEY:enter"
                "up" -> "KEY:up"
                "down" -> "KEY:down"
                "left" -> "KEY:left"
                "right" -> "KEY:right"
                else -> "KEY:$keyName"
            }
            bleManager?.sendCommand(mapped)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("On-Screen Keyboard")
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }
    
    private fun showOnScreenTouchpad() {
        if (bleManager == null) {
            Toast.makeText(context, "BLE not available", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_touchpad, null)
        val touchpadView = dialogView.findViewById<TouchpadView>(R.id.touchpadView)

        touchpadView.setOnMouseMoveListener { dx, dy ->
            bleManager?.sendCommand("MOUSE:move:${dx},${dy}")
        }

        touchpadView.setOnMouseClickListener { button ->
            val mapped = when (button.lowercase()) {
                "left" -> "MOUSE:leftclick"
                "right" -> "MOUSE:rightclick"
                "middle" -> "MOUSE:middleclick"
                else -> "MOUSE:$button"
            }
            bleManager?.sendCommand(mapped)
        }

        touchpadView.setOnMouseScrollListener { amount ->
            bleManager?.sendCommand("MOUSE:scroll:$amount")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("On-Screen Touchpad")
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }
}
