package com.pwdongle.recorder

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Main Activity for PWDongle Macro Recorder
 * 
 * Features:
 * - BLE device scanning and connection
 * - USB OTG input capture
 * - Recording control UI
 * - Permission management
 */
class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var statusText: TextView
    private lateinit var mousePositionText: TextView
    private lateinit var scanButton: Button
    private lateinit var connectButton: Button
    private lateinit var recordButton: Button
    private lateinit var deviceSpinner: Spinner
    private lateinit var filenameInput: EditText
    
    // Managers
    private lateinit var bleManager: BLEManager
    private lateinit var macroRecorder: MacroRecorder
    private lateinit var inputCapture: InputCaptureService
    private lateinit var mouseTracker: MouseTracker
    
    // State
    private var isRecording = false
    private var isConnected = false
    private val deviceList = mutableListOf<String>()
    
    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1
        private const val REQUEST_USB_PERMISSION = 2
        private const val REQUEST_ENABLE_BT = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initializeUI()
        checkPermissions()
        initializeManagers()
    }
    
    private fun initializeUI() {
        statusText = findViewById(R.id.statusText)
        mousePositionText = findViewById(R.id.mousePositionText)
        scanButton = findViewById(R.id.scanButton)
        connectButton = findViewById(R.id.connectButton)
        recordButton = findViewById(R.id.recordButton)
        deviceSpinner = findViewById(R.id.deviceSpinner)
        filenameInput = findViewById(R.id.filenameInput)
        
        scanButton.setOnClickListener { startBLEScan() }
        connectButton.setOnClickListener { connectToDevice() }
        recordButton.setOnClickListener { toggleRecording() }
        
        updateUIState()
    }
    
    private fun initializeManagers() {
        bleManager = BLEManager(this) { status ->
            runOnUiThread {
                statusText.text = status
                if (status.contains("Connected")) {
                    isConnected = true
                    updateUIState()
                } else if (status.contains("Disconnected")) {
                    isConnected = false
                    isRecording = false
                    updateUIState()
                }
            }
        }
        
        macroRecorder = MacroRecorder()
        mouseTracker = MouseTracker()
        
        inputCapture = InputCaptureService(this,
            onKeyEvent = { keyCode, action ->
                if (isRecording) {
                    val keyName = getKeyName(keyCode)
                    macroRecorder.recordKey(keyName, action)
                    bleManager.sendCommand("KEY:$keyName")
                }
            },
            onMouseMove = { x, y ->
                mouseTracker.updatePosition(x, y)
                runOnUiThread {
                    mousePositionText.text = "Mouse: ($x, $y)"
                }
                if (isRecording) {
                    macroRecorder.recordMouseMove(x, y)
                    bleManager.sendCommand("MOUSE:MOVE:$x,$y")
                }
            },
            onMouseButton = { button, isDown ->
                if (isRecording) {
                    val buttonName = when(button) {
                        0 -> "left"
                        1 -> "right"
                        2 -> "middle"
                        else -> "unknown"
                    }
                    macroRecorder.recordMouseButton(buttonName, isDown)
                    val action = if (isDown) "DOWN" else "UP"
                    bleManager.sendCommand("MOUSE:$action:$buttonName")
                }
            },
            onMouseScroll = { amount ->
                if (isRecording) {
                    macroRecorder.recordMouseScroll(amount)
                    bleManager.sendCommand("MOUSE:SCROLL:$amount")
                }
            }
        )
    }
    
    private fun checkPermissions() {
        val permissionsNeeded = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH)
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        
        permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        
        val notGranted = permissionsNeeded.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                notGranted.toTypedArray(),
                REQUEST_BLUETOOTH_PERMISSIONS
            )
        } else {
            enableBluetooth()
        }
    }
    
    private fun enableBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
    }
    
    private fun startBLEScan() {
        statusText.text = "Scanning for PWDongle..."
        deviceList.clear()
        
        bleManager.startScan { device ->
            if (!deviceList.contains(device)) {
                deviceList.add(device)
                runOnUiThread {
                    val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, deviceList)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    deviceSpinner.adapter = adapter
                }
            }
        }
        
        // Stop scan after 10 seconds
        deviceSpinner.postDelayed({
            bleManager.stopScan()
            statusText.text = "Scan complete. Found ${deviceList.size} device(s)"
        }, 10000)
    }
    
    private fun connectToDevice() {
        val selectedDevice = deviceSpinner.selectedItem?.toString()
        if (selectedDevice != null) {
            statusText.text = "Connecting to $selectedDevice..."
            bleManager.connect(selectedDevice)
        } else {
            Toast.makeText(this, "Please select a device", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun toggleRecording() {
        if (!isConnected) {
            Toast.makeText(this, "Please connect to PWDongle first", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!isRecording) {
            // Start recording
            val filename = filenameInput.text.toString().trim()
            if (filename.isEmpty()) {
                Toast.makeText(this, "Please enter a filename", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Verify mouse is at top-left
            if (mouseTracker.x > 50 || mouseTracker.y > 50) {
                Toast.makeText(
                    this,
                    "Please position mouse at top-left corner before recording",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            
            macroRecorder.startRecording(filename)
            bleManager.sendCommand("RECORD:$filename")
            isRecording = true
            statusText.text = "Recording: $filename"
            recordButton.text = "Stop Recording"
            recordButton.setBackgroundColor(getColor(android.R.color.holo_red_light))
            
            inputCapture.start()
            
        } else {
            // Stop recording
            inputCapture.stop()
            macroRecorder.stopRecording()
            bleManager.sendCommand("STOPRECORD")
            isRecording = false
            statusText.text = "Recording stopped"
            recordButton.text = "Start Recording"
            recordButton.setBackgroundColor(getColor(android.R.color.holo_green_light))
        }
        
        updateUIState()
    }
    
    private fun updateUIState() {
        scanButton.isEnabled = !isConnected && !isRecording
        connectButton.isEnabled = !isConnected && !isRecording
        recordButton.isEnabled = isConnected
        filenameInput.isEnabled = !isRecording
    }
    
    private fun getKeyName(keyCode: Int): String {
        // Map Android key codes to PWDongle key names
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_ENTER -> "enter"
            android.view.KeyEvent.KEYCODE_TAB -> "tab"
            android.view.KeyEvent.KEYCODE_SPACE -> "space"
            android.view.KeyEvent.KEYCODE_DEL -> "backspace"
            android.view.KeyEvent.KEYCODE_FORWARD_DEL -> "delete"
            android.view.KeyEvent.KEYCODE_ESCAPE -> "esc"
            android.view.KeyEvent.KEYCODE_SHIFT_LEFT -> "shift"
            android.view.KeyEvent.KEYCODE_CTRL_LEFT -> "ctrl"
            android.view.KeyEvent.KEYCODE_ALT_LEFT -> "alt"
            android.view.KeyEvent.KEYCODE_DPAD_UP -> "up"
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> "down"
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> "left"
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
            android.view.KeyEvent.KEYCODE_F1 -> "f1"
            android.view.KeyEvent.KEYCODE_F2 -> "f2"
            android.view.KeyEvent.KEYCODE_F3 -> "f3"
            android.view.KeyEvent.KEYCODE_F4 -> "f4"
            android.view.KeyEvent.KEYCODE_F5 -> "f5"
            android.view.KeyEvent.KEYCODE_F6 -> "f6"
            android.view.KeyEvent.KEYCODE_F7 -> "f7"
            android.view.KeyEvent.KEYCODE_F8 -> "f8"
            android.view.KeyEvent.KEYCODE_F9 -> "f9"
            android.view.KeyEvent.KEYCODE_F10 -> "f10"
            android.view.KeyEvent.KEYCODE_F11 -> "f11"
            android.view.KeyEvent.KEYCODE_F12 -> "f12"
            else -> {
                // For printable characters, get the character
                val event = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
                val char = event.unicodeChar.toChar()
                if (char.isLetterOrDigit() || char.isWhitespace()) {
                    char.lowercase()
                } else {
                    "key$keyCode"
                }
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                enableBluetooth()
            } else {
                Toast.makeText(
                    this,
                    "Bluetooth permissions required for this app",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            toggleRecording()
        }
        inputCapture.stop()
        bleManager.disconnect()
    }
}
