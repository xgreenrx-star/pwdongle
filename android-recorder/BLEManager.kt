package com.pwdongle.recorder

import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
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
    private val onStatusChange: (String) -> Unit
) {
    
    companion object {
        private const val TAG = "BLEManager"
        
        // Nordic UART Service UUIDs
        private val NUS_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        private val NUS_RX_CHAR_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // Write
        private val NUS_TX_CHAR_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // Notify
        
        private const val MTU_SIZE = 20 // BLE MTU for chunking
    }
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    
    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private val foundDevices = mutableMapOf<String, BluetoothDevice>()
    
    private var isScanning = false
    private var isConnected = false
    
    // Scan callback
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: "Unknown"
            
            // Filter for PWDongle devices
            if (deviceName.contains("PWDongle", ignoreCase = true)) {
                Log.d(TAG, "Found PWDongle: $deviceName (${device.address})")
                foundDevices[deviceName] = device
                onDeviceFound?.invoke(deviceName)
            }
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
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server")
                    isConnected = true
                    onStatusChange("Connected, discovering services...")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server")
                    isConnected = false
                    onStatusChange("Disconnected")
                    cleanup()
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered")
                
                val nusService = gatt.getService(NUS_SERVICE_UUID)
                if (nusService != null) {
                    rxCharacteristic = nusService.getCharacteristic(NUS_RX_CHAR_UUID)
                    txCharacteristic = nusService.getCharacteristic(NUS_TX_CHAR_UUID)
                    
                    // Enable notifications on TX characteristic
                    txCharacteristic?.let {
                        gatt.setCharacteristicNotification(it, true)
                        val descriptor = it.getDescriptor(
                            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                        )
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }
                    
                    onStatusChange("Connected to PWDongle NUS")
                } else {
                    Log.e(TAG, "Nordic UART Service not found")
                    onStatusChange("Error: NUS not found")
                    gatt.disconnect()
                }
            }
        }
        
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == NUS_TX_CHAR_UUID) {
                val data = characteristic.value
                val response = String(data, Charsets.UTF_8)
                Log.d(TAG, "Received: $response")
                onStatusChange("Response: $response")
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
    }
    
    fun startScan(onDeviceFound: (String) -> Unit) {
        this.onDeviceFound = onDeviceFound
        foundDevices.clear()
        
        if (isScanning) {
            stopScan()
        }
        
        bluetoothLeScanner?.startScan(scanCallback)
        isScanning = true
        onStatusChange("Scanning for devices...")
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
}
