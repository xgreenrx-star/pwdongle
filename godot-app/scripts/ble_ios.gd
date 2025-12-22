extends Node

## iOS BLE implementation for PWDongle app
## Uses GDExtension/NativeScript to call iOS CoreBluetooth APIs

signal device_found(device_name: String, device_address: String)
signal connection_changed(connected: bool)
signal data_received(data: String)
signal error_occurred(error_message: String)

var native_instance = null
var is_connected = false

func _ready():
	# Load native iOS CoreBluetooth wrapper (GDExtension)
	if ClassDB.class_exists("IOSBluetooth"):
		native_instance = ClassDB.instantiate("IOSBluetooth")
		
		# Connect native signals to our signals
		native_instance.connect("on_device_found", _on_device_found_callback)
		native_instance.connect("on_connection_changed", _on_connection_state_changed_callback)
		native_instance.connect("on_characteristic_changed", _on_characteristic_changed_callback)
		native_instance.connect("on_error", _on_ble_error_callback)
		
		print("iOS BLE: Native extension loaded")
	else:
		push_error("iOS BLE: IOSBluetooth native class not found - requires custom GDExtension")

## Platform-specific BLE methods

func start_scan():
	"""Start scanning for BLE devices"""
	if native_instance:
		native_instance.call("start_scan")
		print("iOS BLE: Started scanning")
	else:
		error_occurred.emit("Native BLE extension not available")

func stop_scan():
	"""Stop scanning for BLE devices"""
	if native_instance:
		native_instance.call("stop_scan")
		print("iOS BLE: Stopped scanning")

func connect_to_device(device_address: String):
	"""Connect to a BLE device by UUID (iOS uses UUIDs instead of MAC addresses)"""
	if native_instance:
		native_instance.call("connect_to_device", device_address)
		print("iOS BLE: Connecting to " + device_address)
	else:
		error_occurred.emit("Native BLE extension not available")

func disconnect():
	"""Disconnect from current BLE device"""
	if native_instance:
		native_instance.call("disconnect")
		print("iOS BLE: Disconnecting")
		is_connected = false
		connection_changed.emit(false)

func write_characteristic(service_uuid: String, char_uuid: String, data: PackedByteArray):
	"""Write data to a BLE characteristic"""
	if native_instance and is_connected:
		native_instance.call("write_characteristic", service_uuid, char_uuid, data)
		return true
	else:
		if not is_connected:
			error_occurred.emit("Not connected to device")
		return false

func subscribe_to_characteristic(service_uuid: String, char_uuid: String):
	"""Subscribe to notifications from a BLE characteristic"""
	if native_instance and is_connected:
		native_instance.call("subscribe_to_characteristic", service_uuid, char_uuid)
		print("iOS BLE: Subscribed to " + char_uuid)
		return true
	else:
		return false

## Callbacks from iOS CoreBluetooth

func _on_device_found_callback(device_name: String, device_uuid: String):
	"""Called when a BLE peripheral is discovered"""
	device_found.emit(device_name, device_uuid)

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
