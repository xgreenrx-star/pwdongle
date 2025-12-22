extends Node
class_name BLEManager

## Bluetooth Low Energy Manager for PWDongle
## Handles scanning, connecting, and communication with ESP32-S3 via Nordic UART Service

signal device_found(device_name: String, device_address: String)
signal connection_changed(is_connected: bool)
signal data_received(data: String)
signal error_occurred(error_message: String)

# Nordic UART Service UUIDs (must match ESP32 firmware)
const SERVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
const RX_CHAR_UUID = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"  # Write to device
const TX_CHAR_UUID = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"  # Read from device

var ble_plugin = null
var is_connected = false
var current_device_address = ""
var rx_buffer = ""

func _ready():
	# Initialize BLE plugin based on platform
	if OS.has_feature("android"):
		ble_plugin = load("res://addons/ble_plugin/ble_android.gd").new()
	elif OS.has_feature("ios"):
		ble_plugin = load("res://addons/ble_plugin/ble_ios.gd").new()
	else:
		push_error("BLE not supported on this platform")
		return
	
	if ble_plugin:
		add_child(ble_plugin)
		ble_plugin.connect("device_discovered", _on_device_discovered)
		ble_plugin.connect("connection_state_changed", _on_connection_state_changed)
		ble_plugin.connect("characteristic_read", _on_characteristic_read)
		ble_plugin.connect("characteristic_written", _on_characteristic_written)

## Start scanning for BLE devices
func start_scan():
	if ble_plugin:
		print("Starting BLE scan...")
		ble_plugin.start_scan()
	else:
		emit_signal("error_occurred", "BLE plugin not initialized")

## Stop scanning
func stop_scan():
	if ble_plugin:
		ble_plugin.stop_scan()

## Connect to PWDongle device by address
func connect_to_device(address: String):
	if ble_plugin:
		print("Connecting to device: ", address)
		current_device_address = address
		ble_plugin.connect_to_device(address)
	else:
		emit_signal("error_occurred", "BLE plugin not initialized")

## Disconnect from current device
func disconnect():
	if ble_plugin and is_connected:
		ble_plugin.disconnect()
		is_connected = false
		emit_signal("connection_changed", false)

## Send command to PWDongle (automatically adds newline)
func send_command(command: String):
	if not is_connected:
		emit_signal("error_occurred", "Not connected to device")
		return false
	
	var data = (command + "\n").to_utf8_buffer()
	
	# BLE has ~20 byte MTU limit, chunk if needed
	var max_chunk = 20
	var offset = 0
	
	while offset < data.size():
		var chunk_size = min(max_chunk, data.size() - offset)
		var chunk = data.slice(offset, offset + chunk_size)
		
		ble_plugin.write_characteristic(SERVICE_UUID, RX_CHAR_UUID, chunk)
		offset += chunk_size
		
		# Small delay between chunks
		if offset < data.size():
			await get_tree().create_timer(0.02).timeout
	
	print("Sent command: ", command)
	return true

## Convenience methods for specific PWDongle commands

func start_recording(filename: String):
	return send_command("RECORD:" + filename)

func stop_recording():
	return send_command("STOPRECORD")

func send_key(key_name: String):
	return send_command("KEY:" + key_name)

func send_mouse(action: String):
	return send_command("MOUSE:" + action)

func send_type(text: String):
	return send_command("TYPE:" + text)

func send_gamepad(action: String):
	return send_command("GAMEPAD:" + action)

func request_help():
	return send_command("HELP")

func request_about():
	return send_command("ABOUT")

## Signal handlers

func _on_device_discovered(name: String, address: String, rssi: int):
	print("Device found: ", name, " (", address, ") RSSI: ", rssi)
	
	# Only emit PWDongle devices
	if name == "PWDongle":
		emit_signal("device_found", name, address)

func _on_connection_state_changed(connected: bool):
	is_connected = connected
	print("Connection state changed: ", "Connected" if connected else "Disconnected")
	emit_signal("connection_changed", connected)
	
	if connected:
		# Subscribe to TX characteristic notifications
		ble_plugin.subscribe_characteristic(SERVICE_UUID, TX_CHAR_UUID)

func _on_characteristic_read(uuid: String, data: PackedByteArray):
	var text = data.get_string_from_utf8()
	_process_received_data(text)

func _on_characteristic_written(uuid: String, success: bool):
	if not success:
		emit_signal("error_occurred", "Failed to write characteristic")

## Process incoming data from PWDongle
func _process_received_data(data: String):
	rx_buffer += data
	
	# Check for complete lines (ended with \n)
	while "\n" in rx_buffer:
		var newline_pos = rx_buffer.find("\n")
		var line = rx_buffer.substr(0, newline_pos)
		rx_buffer = rx_buffer.substr(newline_pos + 1)
		
		if line.length() > 0:
			print("Received: ", line)
			emit_signal("data_received", line)
