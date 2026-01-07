package com.pwdongle.recorder

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.*

/**
 * BLE Manager for PWDongle Communication
 * 
 * Handles:
 * - BLE device scanning
 * - Connection to Nordic UART Service (NUS)
 * - Command transmission with MTU chunking
 * - Response reception
 */
class BLEManager(
    private val context: Context,
    private var onStatusChange: (String) -> Unit
) {
    
    companion object {
        private const val TAG = "BLEManager"
        
        // Nordic UART Service UUIDs
        private val NUS_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        private val NUS_RX_CHAR_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // Write
        private val NUS_TX_CHAR_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // Notify
        
        private const val MTU_SIZE = 20 // Default BLE MTU
        private const val MAX_MTU_REQUEST = 247 // Max BLE MTU size to request

        @Volatile private var shared: BLEManager? = null

        fun getInstance(context: Context, onStatusChange: (String) -> Unit): BLEManager {
            val existing = shared
            return if (existing != null) {
                existing.setStatusListener(onStatusChange)
                existing
            } else {
                val created = BLEManager(context.applicationContext, onStatusChange)
                shared = created
                created
            }
        }
    }
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    
    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    
    private var negotiatedMTU = MTU_SIZE  // Track actual MTU after negotiation
    
    private val handler = Handler(Looper.getMainLooper())
    private val foundDevices = mutableMapOf<String, BluetoothDevice>()
    
    private var isScanning = false
    private var isConnected = false
    
    // Command response callback
    private var currentResponseCallback: ((String) -> Unit)? = null
    private val currentResponseBuffer = StringBuilder()
    private var responseTimeout: Handler? = null

    // Auto-reconnection state
    private var reconnectEnabled = false
    private var reconnectAttempts = 0
    private var maxReconnectAttempts = 5
    private var lastConnectedDevice: BluetoothDevice? = null
    private var lastConnectedDeviceName: String? = null
    private var reconnectRunnable: Runnable? = null

    // Warm cache of last successful PIN and passwords
    var cachedPin: String? = null
        private set
    var cachedPasswords: String? = null
        private set
    fun updateCachedPin(pin: String) { cachedPin = pin }
    fun updateCachedPasswords(raw: String) { cachedPasswords = raw }
    fun clearCache() { cachedPin = null; cachedPasswords = null }
    fun hasCachedPasswords(): Boolean = cachedPin != null && cachedPasswords != null
    
    // Scan callback
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: "Unknown_${device.address.takeLast(4)}"
            
            Log.d(TAG, "Found device: $deviceName (${device.address})")
            
            // Add all devices, user can filter if needed
            foundDevices[deviceName] = device
            onDeviceFound?.invoke(deviceName)
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error: $errorCode")
            onStatusChange("Scan failed: Error $errorCode")
        }
    }
    
    private var onDeviceFound: ((String) -> Unit)? = null
    
    // GATT callback
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState")
            
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server, status=$status")
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        isConnected = true
                        onStatusChange("Connected, discovering services...")
                        
                        try {
                            // Small delay before service discovery to let connection stabilize
                            handler.postDelayed({
                                try {
                                    Log.d(TAG, "Starting service discovery...")
                                    gatt.discoverServices()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Service discovery failed: ${e.message}")
                                    onStatusChange("Service discovery failed")
                                }
                            }, 600)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error scheduling service discovery: ${e.message}")
                            onStatusChange("Connection error")
                        }
                    } else {
                        Log.e(TAG, "Connection established but status indicates error: $status")
                        onStatusChange("Connection error (status: $status)")
                        gatt.disconnect()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server, status=$status")
                    isConnected = false
                    
                    val reason = when (status) {
                        0 -> "Connection closed normally"
                        8 -> "Connection timeout"
                        19 -> "Connection terminated by peer"
                        22 -> "Connection terminated locally"
                        133 -> "GATT error"
                        else -> "Disconnected (code: $status)"
                    }
                    
                    Log.d(TAG, "Disconnect reason: $reason")
                    onStatusChange(reason)
                    cleanup()
                    
                    // Attempt auto-reconnection if enabled and not manually disconnected
                    if (reconnectEnabled && status != 0 && lastConnectedDevice != null) {
                        scheduleReconnect()
                    }
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "onServicesDiscovered: status=$status")
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered successfully")
                
                // Request larger MTU for lower latency (reduces fragmentation)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        val mtuRequested = gatt.requestMtu(247)  // Max BLE MTU
                        Log.d(TAG, "MTU request initiated: $mtuRequested")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "MTU request failed (non-critical): ${e.message}")
                }
                
                // List all services for debugging
                gatt.services.forEach { service ->
                    Log.d(TAG, "Service found: ${service.uuid}")
                }
                
                val nusService = gatt.getService(NUS_SERVICE_UUID)
                if (nusService != null) {
                    Log.d(TAG, "Nordic UART Service found")
                    rxCharacteristic = nusService.getCharacteristic(NUS_RX_CHAR_UUID)
                    txCharacteristic = nusService.getCharacteristic(NUS_TX_CHAR_UUID)
                    
                    Log.d(TAG, "RX Characteristic: ${if (rxCharacteristic != null) "found" else "NOT FOUND"}")
                    Log.d(TAG, "TX Characteristic: ${if (txCharacteristic != null) "found" else "NOT FOUND"}")
                    
                    // Enable notifications on TX characteristic
                    txCharacteristic?.let {
                        try {
                            gatt.setCharacteristicNotification(it, true)
                            val descriptor = it.getDescriptor(
                                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                            )
                            if (descriptor != null) {
                                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                val result = gatt.writeDescriptor(descriptor)
                                Log.d(TAG, "Descriptor write initiated: $result")
                            } else {
                                Log.e(TAG, "CCCD descriptor not found")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error enabling notifications: ${e.message}")
                        }
                    }
                    
                    onStatusChange("Connected to PWDongle")
                    reconnectAttempts = 0  // Reset reconnect counter on successful connection
                } else {
                    Log.e(TAG, "Nordic UART Service not found")
                    onStatusChange("Error: NUS not found")
                    gatt.disconnect()
                }
            } else {
                Log.e(TAG, "Service discovery failed with status: $status")
                onStatusChange("Service discovery failed")
                gatt.disconnect()
            }
        }
        
        private var lastReceivedChunk = ""
        private var lastReceivedTime = 0L
        
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == NUS_TX_CHAR_UUID) {
                val data = characteristic.value
                val response = String(data, Charsets.UTF_8)
                
                // Deduplicate: Android sometimes calls this twice for the same notification
                val now = System.currentTimeMillis()
                if (response == lastReceivedChunk && (now - lastReceivedTime) < 50) {
                    Log.d(TAG, "Duplicate chunk ignored: $response")
                    return
                }
                lastReceivedChunk = response
                lastReceivedTime = now
                
                Log.d(TAG, "Received: $response")
                
                // Route to callback if one is active
                if (currentResponseCallback != null) {
                    currentResponseBuffer.append(response)
                    
                    // Always use timeout approach to collect all chunks
                    // Reset the timeout each time we receive data
                    responseTimeout?.removeCallbacksAndMessages(null)
                    responseTimeout = Handler(Looper.getMainLooper()).apply {
                        postDelayed({
                            // No more data received for 1500ms, assume complete
                            val finalResponse = currentResponseBuffer.toString()
                            Log.d(TAG, "Response finalized, invoking callback")
                            currentResponseCallback?.invoke(finalResponse)
                            currentResponseCallback = null
                            currentResponseBuffer.clear()
                            responseTimeout = null
                        }, 1500) // 1500ms idle timeout - resets with each chunk
                    }
                } else {
                    onStatusChange("Response: $response")
                }
            }
        }
        
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic write successful")
            } else {
                Log.e(TAG, "Characteristic write failed: $status")
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                negotiatedMTU = mtu
                Log.d(TAG, "MTU changed to: $mtu")
                onStatusChange("Connected (MTU: $mtu)")
            } else {
                Log.d(TAG, "MTU change failed: $status (using default 20)")
            }
        }
    }
    
    fun scanForDevices(onDevicesFound: (List<String>) -> Unit) {
        if (isConnected) {
            onStatusChange("Already connected")
            onDevicesFound(foundDevices.keys.toList())
            return
        }
        // Check if Bluetooth is enabled
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth adapter is null")
            onStatusChange("Bluetooth not available on this device")
            onDevicesFound(emptyList())
            return
        }
        
        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is disabled")
            onStatusChange("Bluetooth is disabled. Enable it in Settings.")
            onDevicesFound(emptyList())
            return
        }
        
        // Check for required permissions
        val hasBluetoothScan = ContextCompat.checkSelfPermission(
            context,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Manifest.permission.BLUETOOTH_SCAN
            } else {
                Manifest.permission.BLUETOOTH
            }
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not needed on older Android
        }
        
        Log.d(TAG, "Permission check - Bluetooth: $hasBluetoothScan, Location: $hasLocation")
        
        if (!hasBluetoothScan) {
            Log.e(TAG, "BLUETOOTH_SCAN permission not granted")
            onStatusChange("Missing Bluetooth permission. Grant it in Settings.")
            onDevicesFound(emptyList())
            return
        }
        
        if (!hasLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.e(TAG, "ACCESS_FINE_LOCATION permission not granted")
            onStatusChange("Missing location permission. Grant it in Settings.")
            onDevicesFound(emptyList())
            return
        }
        
        // Verify scanner is available
        if (bluetoothLeScanner == null) {
            // Try to reinitialize
            bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
            if (bluetoothLeScanner == null) {
                Log.e(TAG, "BLE scanner is null")
                onStatusChange("BLE scanner not available on this device")
                onDevicesFound(emptyList())
                return
            }
        }
        
        foundDevices.clear()
        
        // First, add bonded devices (they may not appear in active BLE scans)
        try {
            val bondedDevices = bluetoothAdapter?.bondedDevices
            if (bondedDevices != null) {
                Log.d(TAG, "Found ${bondedDevices.size} bonded devices")
                for (device in bondedDevices) {
                    // Add all bonded devices (they could be BLE, BR/EDR, or DUAL)
                    val deviceName = device.name ?: "Unknown_${device.address.takeLast(4)}"
                    Log.d(TAG, "Added bonded device: $deviceName (type=${device.type}, address=${device.address})")
                    
                    // Check if this is PWDongle
                    if (device.address.endsWith("f2:e9") || device.name?.contains("PWDongle") == true) {
                        Log.i(TAG, "*** FOUND PWDONGLE IN BONDED DEVICES! ***")
                    }
                    
                    foundDevices[deviceName] = device
                }
                Log.d(TAG, "Initial bonded devices added: ${foundDevices.size}")
                
                // Log all addresses for debugging
                Log.d(TAG, "Bonded devices addresses: ${bondedDevices.map { it.address }.joinToString(", ")}")
            } else {
                Log.w(TAG, "bondedDevices is NULL!")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot access bonded devices: ${e.message}")
        }
        
        // Explicitly check for PWDongle in dumpsys output for debugging
        Log.d(TAG, "Checking for PWDongle in system...")
        
        var lastUpdate = System.currentTimeMillis()
        var deviceCount = 0
        
        onDeviceFound = { deviceName ->
            deviceCount++
            Log.d(TAG, "Device found #$deviceCount: $deviceName")
            // Update UI every 500ms instead of on every device found
            val now = System.currentTimeMillis()
            if (now - lastUpdate > 500) {
                Log.d(TAG, "UI Update: Found ${foundDevices.size} devices")
                onDevicesFound(foundDevices.keys.toList())
                lastUpdate = now
            }
        }
        
        // Immediately update UI if we found bonded devices
        if (foundDevices.isNotEmpty()) {
            Log.d(TAG, "Immediately reporting ${foundDevices.size} bonded devices to UI")
            onDevicesFound(foundDevices.keys.toList())
        }
        
        // Clear previous scan
        if (isScanning) {
            stopScan()
        }
        
        try {
            Log.d(TAG, "Starting BLE scan with LOW_LATENCY settings...")
            
            // Create scan settings for more aggressive scanning
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            
            bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
            isScanning = true
            Log.d(TAG, "BLE scan started successfully")
            onStatusChange("Scanning for devices (10 sec)...")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for BLE scan: ${e.message}")
            onStatusChange("Permission denied: Grant location permission in Settings")
            onDevicesFound(emptyList())
            return
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start scan: ${e.message}", e)
            onStatusChange("Scan failed: ${e.message}")
            onDevicesFound(emptyList())
            return
        }
        
        // Also add bonded devices (they may not appear in active scans)
        try {
            val bondedDevices = bluetoothAdapter?.bondedDevices
            if (bondedDevices != null) {
                for (device in bondedDevices) {
                    // Only add BLE devices (type DEVICE_TYPE_LE or DEVICE_TYPE_DUAL)
                    if (device.type == BluetoothDevice.DEVICE_TYPE_LE || device.type == BluetoothDevice.DEVICE_TYPE_DUAL) {
                        val deviceName = device.name ?: "Unknown_${device.address.takeLast(4)}"
                        if (!foundDevices.containsKey(deviceName)) {
                            Log.d(TAG, "Added bonded device: $deviceName (${device.address})")
                            foundDevices[deviceName] = device
                        }
                    }
                }
                Log.d(TAG, "Added ${bondedDevices.size} bonded devices. Total now: ${foundDevices.size}")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot access bonded devices: ${e.message}")
        }
        
        // Auto-stop scan after 10 seconds
        handler.postDelayed({
            if (isScanning) {
                stopScan()
                Log.d(TAG, "BLE scan stopped after 10 seconds. Total devices found: ${foundDevices.size}")
            }
            // Final check for bonded devices again (in case they weren't found during scan)
            try {
                val bondedDevices = bluetoothAdapter?.bondedDevices
                if (bondedDevices != null) {
                    for (device in bondedDevices) {
                        val deviceName = device.name ?: "Unknown_${device.address.takeLast(4)}"
                        if (!foundDevices.containsKey(deviceName)) {
                            Log.d(TAG, "Final: Added bonded device: $deviceName")
                            foundDevices[deviceName] = device
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot access bonded devices in final check: ${e.message}")
            }
            onDevicesFound(foundDevices.keys.toList())
        }, 10000)
    }
    
    fun connectToDevice(deviceName: String, onResult: (String) -> Unit) {
        val device = foundDevices[deviceName]
        if (device == null) {
            Log.e(TAG, "Device not found in foundDevices map: $deviceName")
            onResult("Device not found: $deviceName")
            return
        }
        
        // Check for BLUETOOTH_CONNECT permission (Android 12+)
        val hasBluetoothConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not needed on older Android
        }
        
        if (!hasBluetoothConnect) {
            Log.e(TAG, "BLUETOOTH_CONNECT permission not granted")
            onResult("Missing Bluetooth Connect permission. Grant it in Settings.")
            return
        }
        
        // Stop scanning before connecting
        if (isScanning) {
            stopScan()
        }
        
        try {
            Log.d(TAG, "Attempting to connect to device: $deviceName (${device.address})")
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            lastConnectedDevice = device
            lastConnectedDeviceName = deviceName
            onResult("Connecting to $deviceName...")
            Log.d(TAG, "connectGatt() called successfully")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception during connection: ${e.message}")
            onResult("Permission denied: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed: ${e.message}", e)
            onResult("Connection failed: ${e.message}")
        }
    }
    
    /**
     * Connect to a device by its MAC address directly (for bonded devices not in scan results)
     */
    fun connectToDeviceByMAC(macAddress: String, onResult: (String) -> Unit) {
        // Check for BLUETOOTH_CONNECT permission (Android 12+)
        val hasBluetoothConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not needed on older Android
        }
        
        if (!hasBluetoothConnect) {
            Log.e(TAG, "BLUETOOTH_CONNECT permission not granted")
            onResult("Missing Bluetooth Connect permission. Grant it in Settings.")
            return
        }
        
        // Stop scanning before connecting
        if (isScanning) {
            stopScan()
        }
        
        try {
            // Try to get the device from BluetoothAdapter using MAC
            val device = bluetoothAdapter?.getRemoteDevice(macAddress)
            if (device == null) {
                Log.e(TAG, "Could not get remote device for MAC: $macAddress")
                onResult("Device not found at MAC: $macAddress")
                return
            }
            
            Log.d(TAG, "Attempting to connect to MAC address: $macAddress (${device.name})")
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            lastConnectedDevice = device
            lastConnectedDeviceName = device.name ?: macAddress
            onResult("Connecting to ${device.name ?: macAddress}...")
            Log.d(TAG, "connectGatt() called successfully for MAC address")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception during MAC connection: ${e.message}")
            onResult("Permission denied: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "MAC connection failed: ${e.message}", e)
            onResult("Connection failed: ${e.message}")
        }
    }
    
    fun stopScan() {
        if (isScanning) {
            bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
        }
    }
    
    fun connect(deviceName: String) {
        val device = foundDevices[deviceName]
        if (device == null) {
            onStatusChange("Device not found: $deviceName")
            return
        }
        
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }
    
    fun disconnect() {
        reconnectEnabled = false  // Disable reconnection for manual disconnect
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        bluetoothGatt?.disconnect()
        cleanup()
    }
    
    private fun cleanup() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        rxCharacteristic = null
        txCharacteristic = null
        isConnected = false
    }
    
    /**
     * Send command to PWDongle and receive response via callback
     * @param command The command to send
     * @param onResponse Callback invoked when response is received
     */
    fun sendCommandWithResponse(command: String, onResponse: ((String) -> Unit)) {
        responseTimeout?.removeCallbacksAndMessages(null)
        currentResponseCallback = onResponse
        currentResponseBuffer.clear()
        sendCommand(command)  // Use the existing send method
    }

    /**
     * Warm path: if a cached response exists for the given PIN, return it immediately
     * and optionally continue with a live fetch to refresh callers.
     */
    fun warmFetchPasswords(
        pin: String?,
        onCache: ((String) -> Unit)?,
        onLive: ((String) -> Unit)?,
        onError: ((String) -> Unit)? = null
    ) {
        if (pin == null || pin.length != 4) {
            onError?.invoke("PIN missing")
            return
        }

        val cachedPinLocal = cachedPin
        val cachedPasswordsLocal = cachedPasswords

        if (cachedPinLocal == pin && cachedPasswordsLocal != null) {
            onCache?.invoke(cachedPasswordsLocal)
        }

        if (!isConnected || rxCharacteristic == null) {
            onError?.invoke("Not connected")
            return
        }

        sendCommandWithResponse("RETRIEVEPW") { firstResponse ->
            if (!firstResponse.contains("OK", ignoreCase = true)) {
                onError?.invoke(firstResponse)
                return@sendCommandWithResponse
            }

            handler.postDelayed({
                sendCommandWithResponse(pin) { authResponse ->
                    onLive?.invoke(authResponse)
                }
            }, 10)
        }
    }
    
    /**
     * Send command to PWDongle via BLE
     * Automatically chunks data if > MTU size
     */
    fun sendCommand(command: String) {
        if (!isConnected || rxCharacteristic == null) {
            Log.e(TAG, "Not connected or RX characteristic not available")
            return
        }
        
        val commandWithNewline = command + "\n"
        val data = commandWithNewline.toByteArray(Charsets.UTF_8)
        
        // If data fits in one packet, send it
        if (data.size <= MTU_SIZE) {
            rxCharacteristic?.value = data
            bluetoothGatt?.writeCharacteristic(rxCharacteristic)
            Log.d(TAG, "Sent: $command")
        } else {
            // Chunk the data
            var offset = 0
            while (offset < data.size) {
                val chunkSize = minOf(MTU_SIZE, data.size - offset)
                val chunk = data.copyOfRange(offset, offset + chunkSize)
                
                rxCharacteristic?.value = chunk
                bluetoothGatt?.writeCharacteristic(rxCharacteristic)
                
                offset += chunkSize
                
                // Small delay between chunks
                Thread.sleep(10)
            }
            Log.d(TAG, "Sent (chunked): $command")
        }
    }

    /**
     * Low-latency send optimized for live control
     * Uses write-without-response for minimal delay
     */
    fun sendCommandLowLatency(command: String) {
        if (!isConnected || rxCharacteristic == null) {
            return
        }
        
        try {
            val commandWithNewline = command + "\n"
            val data = commandWithNewline.toByteArray(Charsets.UTF_8)
            
            // Use negotiated MTU instead of default
            if (data.size <= negotiatedMTU) {
                rxCharacteristic?.value = data
                rxCharacteristic?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                bluetoothGatt?.writeCharacteristic(rxCharacteristic)
                Log.d(TAG, "Low-latency send (no-resp): ${data.size}B - $command")
            } else {
                // If command exceeds MTU, fall back to standard send
                Log.w(TAG, "Command exceeds MTU ($negotiatedMTU), using standard send")
                sendCommand(command)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Low-latency send failed: ${e.message}")
        }
    }

    fun setStatusListener(listener: (String) -> Unit) {
        onStatusChange = listener
    }

    /**
     * Enable automatic reconnection on connection loss
     * @param enabled Whether to enable auto-reconnect
     * @param maxAttempts Maximum number of reconnection attempts (default 5)
     */
    fun setAutoReconnect(enabled: Boolean, maxAttempts: Int = 5) {
        reconnectEnabled = enabled
        maxReconnectAttempts = maxAttempts
        if (!enabled) {
            // Cancel any pending reconnect attempts
            reconnectRunnable?.let { handler.removeCallbacks(it) }
            reconnectAttempts = 0
        }
        Log.d(TAG, "Auto-reconnect ${if (enabled) "enabled" else "disabled"} (max attempts: $maxAttempts)")
    }

    /**
     * Schedule a reconnection attempt with exponential backoff
     */
    private fun scheduleReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            Log.w(TAG, "Max reconnection attempts ($maxReconnectAttempts) reached")
            onStatusChange("Reconnection failed after $maxReconnectAttempts attempts")
            return
        }

        // Cancel any existing reconnect task
        reconnectRunnable?.let { handler.removeCallbacks(it) }

        reconnectAttempts++
        // Exponential backoff: 1s, 2s, 4s, 8s, 16s
        val delayMs = (1000L * (1 shl (reconnectAttempts - 1))).coerceAtMost(16000L)
        
        Log.d(TAG, "Scheduling reconnect attempt $reconnectAttempts/$maxReconnectAttempts in ${delayMs}ms")
        onStatusChange("Reconnecting in ${delayMs/1000}s (attempt $reconnectAttempts/$maxReconnectAttempts)")

        reconnectRunnable = Runnable {
            attemptReconnect()
        }
        
        handler.postDelayed(reconnectRunnable!!, delayMs)
    }

    /**
     * Attempt to reconnect to the last connected device
     */
    private fun attemptReconnect() {
        val device = lastConnectedDevice
        val deviceName = lastConnectedDeviceName
        
        if (device == null) {
            Log.e(TAG, "Cannot reconnect: no last device stored")
            onStatusChange("Reconnection failed: no device")
            return
        }

        Log.d(TAG, "Attempting reconnection to $deviceName...")
        onStatusChange("Reconnecting to $deviceName...")

        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            Log.d(TAG, "Reconnection attempt initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Reconnection attempt failed: ${e.message}", e)
            onStatusChange("Reconnection failed: ${e.message}")
            
            // Schedule another attempt if under max
            if (reconnectAttempts < maxReconnectAttempts) {
                scheduleReconnect()
            }
        }
    }

    fun isConnected(): Boolean = isConnected
}
