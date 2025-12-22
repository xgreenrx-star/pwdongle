extends Node

## Android BLE implementation for PWDongle app
## Uses JNI to call Android BluetoothLE APIs

signal device_found(device_name: String, device_address: String)
signal connection_changed(connected: bool)
signal data_received(data: String)
signal error_occurred(error_message: String)

var jni_class = null
var is_connected = false

func _ready():
	if Engine.has_singleton("JNISingleton"):
		jni_class = Engine.get_singleton("JNISingleton")
		print("Android BLE: JNI singleton loaded")
	else:
		push_error("Android BLE: JNI singleton not found - requires custom Android plugin")

## Platform-specific BLE methods

func start_scan():
	"""Start scanning for BLE devices"""
	if jni_class:
		jni_class.call("startBleScan")
		print("Android BLE: Started scanning")
	else:
		error_occurred.emit("JNI singleton not available")

func stop_scan():
	"""Stop scanning for BLE devices"""
	if jni_class:
		jni_class.call("stopBleScan")
		print("Android BLE: Stopped scanning")

func connect_to_device(device_address: String):
	"""Connect to a BLE device by address"""
	if jni_class:
		jni_class.call("connectToDevice", device_address)
		print("Android BLE: Connecting to " + device_address)
	else:
		error_occurred.emit("JNI singleton not available")

func disconnect():
	"""Disconnect from current BLE device"""
	if jni_class:
		jni_class.call("disconnect")
		print("Android BLE: Disconnecting")
		is_connected = false
		connection_changed.emit(false)

func write_characteristic(service_uuid: String, char_uuid: String, data: PackedByteArray):
	"""Write data to a BLE characteristic"""
	if jni_class and is_connected:
		# Convert PackedByteArray to Java byte array
		var byte_array = []
		for byte in data:
			byte_array.append(byte)
		
		jni_class.call("writeCharacteristic", service_uuid, char_uuid, byte_array)
		return true
	else:
		if not is_connected:
			error_occurred.emit("Not connected to device")
		return false

func subscribe_to_characteristic(service_uuid: String, char_uuid: String):
	"""Subscribe to notifications from a BLE characteristic"""
	if jni_class and is_connected:
		jni_class.call("subscribeToCharacteristic", service_uuid, char_uuid)
		print("Android BLE: Subscribed to " + char_uuid)
		return true
	else:
		return false

## Callbacks from Android (called via JNI)

func _on_device_found_callback(device_name: String, device_address: String):
	"""Called when a BLE device is discovered"""
	device_found.emit(device_name, device_address)

func _on_connection_state_changed_callback(connected: bool):
	"""Called when connection state changes"""
	is_connected = connected
	connection_changed.emit(connected)

func _on_characteristic_changed_callback(char_uuid: String, data: PackedByteArray):
	"""Called when a subscribed characteristic sends data"""
	var text = data.get_string_from_utf8()
	data_received.emit(text)

func _on_ble_error_callback(error_message: String):
	"""Called when a BLE error occurs"""
	error_occurred.emit(error_message)
