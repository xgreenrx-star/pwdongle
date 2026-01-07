package com.pwdongle.recorder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch

/**
 * Settings fragment - app preferences and configuration
 */
class SettingsFragment : Fragment() {
    
    private lateinit var scanButton: Button
    private lateinit var liveControlButton: Button
    private lateinit var devicesList: ListView
    private lateinit var statusText: TextView
    private lateinit var delayThresholdSlider: SeekBar
    private lateinit var delayThresholdText: TextView
    private lateinit var autoConnectSwitch: Switch
    private lateinit var themeRadioGroup: RadioGroup
    private lateinit var themeSystemRadio: RadioButton
    private lateinit var themeLightRadio: RadioButton
    private lateinit var themeDarkRadio: RadioButton
    
    private var bleManager: BLEManager? = null
    private lateinit var preferencesManager: PreferencesManager
    private var hasInitializedTheme: Boolean = false
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        scanButton = view.findViewById(R.id.scanButton)
        liveControlButton = view.findViewById(R.id.liveControlButton)
        devicesList = view.findViewById(R.id.devicesList)
        statusText = view.findViewById(R.id.statusText)
        delayThresholdSlider = view.findViewById(R.id.delayThresholdSlider)
        delayThresholdText = view.findViewById(R.id.delayThresholdText)
        autoConnectSwitch = view.findViewById(R.id.autoConnectSwitch)
        themeRadioGroup = view.findViewById(R.id.themeRadioGroup)
        themeSystemRadio = view.findViewById(R.id.themeSystemRadio)
        themeLightRadio = view.findViewById(R.id.themeLightRadio)
        themeDarkRadio = view.findViewById(R.id.themeDarkRadio)
        
        preferencesManager = PreferencesManager(requireContext())
        
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
        
        // Load auto-connect preference
        viewLifecycleOwner.lifecycleScope.launch {
            preferencesManager.autoConnectEnabledFlow.collect { enabled ->
                autoConnectSwitch.isChecked = enabled
            }
        }

        // Load theme mode preference
        viewLifecycleOwner.lifecycleScope.launch {
            preferencesManager.themeModeFlow.collect { mode ->
                when (mode) {
                    "light" -> themeLightRadio.isChecked = true
                    "dark" -> themeDarkRadio.isChecked = true
                    else -> themeSystemRadio.isChecked = true
                }
                // Apply theme on first load without recreating the activity
                applyTheme(mode, triggerRecreate = false)
                hasInitializedTheme = true
            }
        }
        
        setupListeners()
    }
    
    private fun setupListeners() {
        // Auto-connect toggle listener
        autoConnectSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewLifecycleOwner.lifecycleScope.launch {
                preferencesManager.setAutoConnectEnabled(isChecked)
                statusText.text = if (isChecked) {
                    "Auto-connect ENABLED - PWDongle will be auto-detected on startup"
                } else {
                    "Auto-connect DISABLED - Manual device selection required"
                }
            }
        }
        
        // Live Control button - navigate to live control fragment
        liveControlButton.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_settings_to_live_control)
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Navigation failed", e)
                statusText.text = "Error: Could not open Live Control"
            }
        }

        scanButton.setOnClickListener {
            if (bleManager?.isConnected() == true) {
                statusText.text = "Connected to PWDongle"
                return@setOnClickListener
            }
            android.util.Log.d("SettingsFragment", "Scan button clicked")
            statusText.text = "Scanning... (Make sure Bluetooth is ON)"
            scanButton.isEnabled = false
            
            try {
                android.util.Log.d("SettingsFragment", "Starting BLE scan")
                bleManager?.scanForDevices { devices ->
                    android.util.Log.d("SettingsFragment", "Scan completed. Found ${devices.size} devices")
                    try {
                        if (isAdded) {
                            requireActivity().runOnUiThread {
                                val deviceList = devices.toMutableList()
                                
                                if (devices.isEmpty()) {
                                    statusText.text = "No devices found. Ensure Bluetooth is ON and devices are in range."
                                } else {
                                    statusText.text = "Found ${devices.size} device(s)"
                                    
                                    val adapter = ArrayAdapter(
                                        requireContext(),
                                        android.R.layout.simple_list_item_1,
                                        deviceList
                                    )
                                    devicesList.adapter = adapter
                                    
                                    // Set click listener for device selection
                                    if (devicesList.onItemClickListener == null) {
                                        devicesList.setOnItemClickListener { _, _, position, _ ->
                                            val selectedDevice = deviceList[position]
                                            statusText.text = "Connecting to: $selectedDevice"
                                            
                                            // Save selected device as last connected
                                            viewLifecycleOwner.lifecycleScope.launch {
                                                preferencesManager.setLastConnectedDevice(selectedDevice)
                                                preferencesManager.addSavedDevice(selectedDevice)
                                            }
                                            
                                            bleManager?.setAutoReconnect(true, 5)  // Enable auto-reconnect
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
                        android.util.Log.e("SettingsFragment", "Scan callback failed", e)
                        if (isAdded) {
                            statusText.text = "Error: ${e.message}"
                            scanButton.isEnabled = true
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Scan failed", e)
                statusText.text = "Scan failed: ${e.message}"
                scanButton.isEnabled = true
            }
        }
        
        delayThresholdSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                delayThresholdText.text = "Delay Threshold: ${progress}ms"
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        // Theme selection listener
        themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.themeLightRadio -> "light"
                R.id.themeDarkRadio -> "dark"
                else -> "system"
            }
            viewLifecycleOwner.lifecycleScope.launch {
                preferencesManager.setThemeMode(mode)
                // Only recreate when user actively changes selection
                applyTheme(mode, triggerRecreate = hasInitializedTheme)
            }
        }
    }

    private fun applyTheme(mode: String, triggerRecreate: Boolean) {
        when (mode) {
            "light" -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            )
            "dark" -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            )
            else -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            )
        }

        if (triggerRecreate && isAdded) {
            // Defer recreate slightly to avoid racing fragment lifecycle updates
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    requireActivity().recreate()
                } catch (_: Exception) { /* ignore */ }
            }
        }
    }
}
