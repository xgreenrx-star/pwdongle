package com.pwdongle.recorder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Main recorder fragment - handles recording setup and controls
 * 
 * Features:
 * - Auto-connect to PWDongle on startup (if enabled)
 * - Manual device selection if auto-connect disabled
 * - Macro recording with keyboard/mouse capture
 */
class RecorderFragment : Fragment() {
    
    private lateinit var statusText: TextView
    private lateinit var filenameInput: EditText
    private lateinit var recordButton: Button
    private lateinit var inputMethodSpinner: Spinner
    private lateinit var mousePositionText: TextView
    private lateinit var eventCountText: TextView
    private lateinit var deviceSelector: LinearLayout
    private lateinit var deviceSpinner: Spinner
    private lateinit var connectButton: Button
    private lateinit var filesButton: Button
    private lateinit var settingsButton: Button
    
    private var bleManager: BLEManager? = null
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var macroRecorder: MacroRecorder
    private lateinit var fileManager: MacroFileManager
    private lateinit var mouseTracker: MouseTracker
    
    private var isRecording = false
    private var isConnected = false
    private var autoConnectEnabled = false
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val deviceList = mutableListOf<String>()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_recorder, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeUI(view)
        initializeManagers()
        setupListeners()
        
        // Start auto-connect on fragment creation if enabled
        viewLifecycleOwner.lifecycleScope.launch {
            preferencesManager.autoConnectEnabledFlow.collect { autoConnectEnabled ->
                this@RecorderFragment.autoConnectEnabled = autoConnectEnabled
                if (autoConnectEnabled) {
                    deviceSelector.visibility = View.GONE
                    statusText.text = "Auto-connect ENABLED - Searching for PWDongle..."
                    autoConnectToPWDongle()
                } else {
                    deviceSelector.visibility = View.VISIBLE
                    statusText.text = "Auto-connect DISABLED - Please select device"
                }
            }
        }
    }
    
    private fun initializeUI(view: View) {
        statusText = view.findViewById(R.id.statusText)
        filenameInput = view.findViewById(R.id.filenameInput)
        recordButton = view.findViewById(R.id.recordButton)
        inputMethodSpinner = view.findViewById(R.id.inputMethodSpinner)
        mousePositionText = view.findViewById(R.id.mousePositionText)
        eventCountText = view.findViewById(R.id.eventCountText)
        deviceSelector = view.findViewById(R.id.deviceSelector)
        deviceSpinner = view.findViewById(R.id.deviceSpinner)
        connectButton = view.findViewById(R.id.connectButton)
        filesButton = view.findViewById(R.id.filesButton)
        settingsButton = view.findViewById(R.id.settingsButton)
        
        // Setup input method spinner
        val inputMethods = arrayOf("Hardware (USB OTG)", "On-Screen Keyboard", "Touchpad Only", "Mixed Mode")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, inputMethods)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        inputMethodSpinner.adapter = adapter
    }
    
    private fun initializeManagers() {
        preferencesManager = PreferencesManager(requireContext())
        
        try {
            bleManager = BLEManager.getInstance(requireContext()) { status ->
                try {
                    requireActivity().runOnUiThread {
                        statusText.text = status
                        if (status.contains("Connected", ignoreCase = true)) {
                            isConnected = true
                            reconnectAttempts = 0
                            updateUIState()
                        } else if (
                            status.contains("Disconnected", ignoreCase = true) ||
                            status.contains("timeout", ignoreCase = true) ||
                            status.contains("terminated", ignoreCase = true) ||
                            status.contains("error", ignoreCase = true)
                        ) {
                            isConnected = false
                            if (isRecording) stopRecording()
                            updateUIState()
                            maybeScheduleReconnect(status)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RecorderFragment", "BLE status update failed", e)
                }
            }
            if (bleManager?.isConnected() == true) {
                isConnected = true
                statusText.text = "Connected to PWDongle"
                updateUIState()
            }
        } catch (e: Exception) {
            android.util.Log.e("RecorderFragment", "BLE initialization failed", e)
            statusText.text = "Bluetooth unavailable: ${e.message}"
        }
        
        macroRecorder = MacroRecorder()
        fileManager = MacroFileManager(requireContext())
        mouseTracker = MouseTracker()
    }
    
    private fun setupListeners() {
        recordButton.setOnClickListener {
            if (!isRecording) {
                startRecording()
            } else {
                stopRecording()
            }
        }
        
        connectButton.setOnClickListener {
            var selectedDevice = deviceSpinner.selectedItem?.toString()
            
            // Handle manual MAC address entry
            if (selectedDevice == "Enter MAC address manually...") {
                showMACEntryDialog()
                return@setOnClickListener
            }
            
            if (selectedDevice != null) {
                statusText.text = "Connecting to $selectedDevice..."
                
                viewLifecycleOwner.lifecycleScope.launch {
                    preferencesManager.setLastConnectedDevice(selectedDevice)
                    preferencesManager.addSavedDevice(selectedDevice)
                }
                
                bleManager?.connectToDevice(selectedDevice) { result ->
                    requireActivity().runOnUiThread {
                        statusText.text = result
                    }
                }
            } else {
                Toast.makeText(context, "Please select a device", Toast.LENGTH_SHORT).show()
            }
        }

        filesButton.setOnClickListener {
            findNavController().navigate(R.id.nav_file_manager)
        }

        settingsButton.setOnClickListener {
            findNavController().navigate(R.id.nav_settings)
        }
    }

    /**
     * Schedule a limited auto-reconnect when auto-connect is enabled.
     */
    private fun maybeScheduleReconnect(reason: String) {
        if (!autoConnectEnabled) return
        if (isConnected) return
        if (reconnectAttempts >= maxReconnectAttempts) {
            if (isAdded) {
                statusText.text = "Connection lost: $reason. Manual reconnect required."
            }
            return
        }
        reconnectAttempts += 1
        if (isAdded) {
            statusText.text = "Reconnecting... (attempt $reconnectAttempts/$maxReconnectAttempts)"
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (!isConnected && isAdded) {
                    autoConnectToPWDongle()
                }
            }, 2000)
        }
    }
    
    /**
     * Automatically search for and connect to PWDongle
     */
    private fun autoConnectToPWDongle(remainingRetries: Int = 2) {
        if (bleManager?.isConnected() == true) {
            statusText.text = "Connected to PWDongle"
            isConnected = true
            updateUIState()
            return
        }
        bleManager?.scanForDevices { devices ->
            requireActivity().runOnUiThread {
                android.util.Log.d("RecorderFragment", "Auto-scan found ${devices.size} devices: $devices")

                // Prefer exact name first
                var targetDevice = devices.find { it.equals("PWDongle", ignoreCase = true) }

                // Otherwise partial matches / common names
                if (targetDevice == null) targetDevice = devices.find { it.contains("PWDongle", ignoreCase = true) }
                if (targetDevice == null) targetDevice = devices.find { it.contains("ESP32", ignoreCase = true) }
                if (targetDevice == null) targetDevice = devices.find { it.contains("Recorder", ignoreCase = true) }

                // If there is exactly one device, assume it is the target
                if (targetDevice == null && devices.size == 1) {
                    targetDevice = devices.first()
                    android.util.Log.d("RecorderFragment", "Only one device found; selecting: $targetDevice")
                }

                if (targetDevice != null) {
                    // Update UI safely
                    if (isAdded) {
                        statusText.text = "Device found! Connecting to $targetDevice..."
                    }
                    android.util.Log.d("RecorderFragment", "Auto-connecting to $targetDevice")

                    if (isAdded) {
                        // Save prefs in background - don't wait for lifecycle
                        kotlin.concurrent.thread {
                            kotlinx.coroutines.runBlocking {
                                preferencesManager.setLastConnectedDevice(targetDevice)
                                preferencesManager.addSavedDevice(targetDevice)
                            }
                        }
                    }

                    bleManager?.connectToDevice(targetDevice) { result ->
                        if (isAdded) {
                            requireActivity().runOnUiThread {
                                statusText.text = result
                                android.util.Log.d("RecorderFragment", "Connection result: $result")
                            }
                        }
                    }
                } else {
                    if (isAdded) {
                        statusText.text = "PWDongle not found (${devices.size} device(s))."
                    }
                    android.util.Log.d("RecorderFragment", "No matching device found. Available: $devices")

                    if (remainingRetries > 0) {
                        val nextTry = 3 - remainingRetries + 1
                        if (isAdded) {
                            statusText.text = "PWDongle not found. Retrying scan ($nextTry/3)..."
                            // Use handler instead of viewLifecycleOwner to avoid lifecycle issues
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                if (isAdded) {
                                    autoConnectToPWDongle(remainingRetries - 1)
                                }
                            }, 2000)
                        }
                    } else {
                        if (isAdded) {
                            statusText.text = "PWDongle not auto-detected. Select device below:"
                            showDeviceSelection(devices)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Show device selection UI with available devices + manual MAC entry
     */
    private fun showDeviceSelection(devices: List<String>) {
        deviceList.clear()
        deviceList.addAll(devices)
        // Add manual MAC address option
        deviceList.add("Enter MAC address manually...")
        
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            deviceList
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        deviceSpinner.adapter = adapter
        deviceSelector.visibility = View.VISIBLE
        
        statusText.text = "PWDongle not auto-detected. Select from available devices:"
    }
    
    private fun startRecording() {
        val filename = filenameInput.text.toString().trim()
        if (filename.isEmpty()) {
            Toast.makeText(context, "Please enter a filename", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!isConnected) {
            Toast.makeText(context, "Please connect to PWDongle first", Toast.LENGTH_SHORT).show()
            return
        }
        
        isRecording = true
        macroRecorder.startRecording(filename)
        recordButton.text = "Stop Recording"
        recordButton.setBackgroundColor(android.graphics.Color.RED)
        statusText.text = "Recording: $filename"
        
        viewLifecycleOwner.lifecycleScope.launch {
            bleManager?.sendCommand("RECORD:$filename") ?: run {
                statusText.text = "Error: BLE not available"
            }
        }
    }
    
    private fun stopRecording() {
        if (!isRecording) return
        
        isRecording = false
        val content = macroRecorder.getMacroAsString()
        
        recordButton.text = "Start Recording"
        recordButton.setBackgroundColor(android.graphics.Color.GREEN)
        
        // Save to file
        viewLifecycleOwner.lifecycleScope.launch {
            val filename = filenameInput.text.toString().trim()
            fileManager.saveMacro(filename, content).onSuccess {
                statusText.text = "Macro saved: $filename"
                viewLifecycleOwner.lifecycleScope.launch {
                    bleManager?.sendCommand("STOPRECORD")
                }
            }.onFailure { error ->
                statusText.text = "Error saving macro: ${error.message}"
            }
        }
    }
    
    /**
     * Show dialog for manual MAC address entry (for devices not in bonded list)
     */
    private fun showMACEntryDialog() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        dialog.setTitle("Connect by MAC Address")
        dialog.setMessage("PWDongle MAC address (e.g., aa:bb:cc:dd:ee:ff):")
        
        val input = EditText(requireContext())
        input.hint = "xx:xx:xx:xx:f2:e9"
        input.setText("xx:xx:xx:xx:f2:e9") // Pre-fill with known PWDongle address if possible
        dialog.setView(input)
        
        dialog.setPositiveButton("Connect") { _, _ ->
            val macAddress = input.text.toString().trim().uppercase()
            if (macAddress.matches(Regex("[0-9A-F]{2}(:[0-9A-F]{2}){5}"))) {
                statusText.text = "Connecting to $macAddress..."
                bleManager?.connectToDeviceByMAC(macAddress) { result ->
                    requireActivity().runOnUiThread {
                        statusText.text = result
                    }
                }
            } else {
                Toast.makeText(context, "Invalid MAC address format", Toast.LENGTH_SHORT).show()
            }
        }
        
        dialog.setNegativeButton("Cancel", null)
        dialog.show()
    }
    
    private fun updateUIState() {
        recordButton.isEnabled = isConnected && !isRecording
    }
}
