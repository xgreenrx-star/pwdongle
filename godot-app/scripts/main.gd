extends Control

## Main UI Controller for PWDongle app

@onready var ble_manager = $BLEManager
@onready var status_label = $MarginContainer/VBoxContainer/StatusPanel/StatusLabel
@onready var scan_button = $MarginContainer/VBoxContainer/ConnectionPanel/ScanButton
@onready var connect_button = $MarginContainer/VBoxContainer/ConnectionPanel/ConnectButton
@onready var disconnect_button = $MarginContainer/VBoxContainer/ConnectionPanel/DisconnectButton
@onready var device_list = $MarginContainer/VBoxContainer/ConnectionPanel/DeviceList

# Recording controls
@onready var filename_input = $MarginContainer/VBoxContainer/RecordingPanel/FilenameInput
@onready var record_button = $MarginContainer/VBoxContainer/RecordingPanel/RecordButton
@onready var stop_record_button = $MarginContainer/VBoxContainer/RecordingPanel/StopRecordButton
@onready var recording_status = $MarginContainer/VBoxContainer/RecordingPanel/RecordingStatus

# Quick actions
@onready var quick_actions = $MarginContainer/VBoxContainer/QuickActionsPanel/GridContainer
@onready var text_input = $MarginContainer/VBoxContainer/TypePanel/TextInput
@onready var send_text_button = $MarginContainer/VBoxContainer/TypePanel/SendButton

# Console
@onready var console_output = $MarginContainer/VBoxContainer/ConsolePanel/ConsoleOutput

var discovered_devices = {}
var is_recording = false

func _ready():
	# Connect BLE manager signals
	ble_manager.connect("device_found", _on_device_found)
	ble_manager.connect("connection_changed", _on_connection_changed)
	ble_manager.connect("data_received", _on_data_received)
	ble_manager.connect("error_occurred", _on_error_occurred)
	
	# Initial UI state
	_update_ui_state(false)
	disconnect_button.visible = false
	stop_record_button.visible = false
	
	# Setup quick action buttons
	_setup_quick_actions()

func _setup_quick_actions():
	# Add common key buttons
	var keys = ["enter", "tab", "backspace", "escape", "up", "down", "left", "right",
				"ctrl+c", "ctrl+v", "ctrl+z", "alt+tab", "win+r", "f5"]
	
	for key in keys:
		var button = Button.new()
		button.text = key.to_upper()
		button.pressed.connect(_on_quick_key_pressed.bind(key))
		quick_actions.add_child(button)

## Button handlers

func _on_scan_button_pressed():
	discovered_devices.clear()
	device_list.clear()
	_log_console("Scanning for PWDongle devices...")
	ble_manager.start_scan()
	
	# Auto-stop scan after 10 seconds
	await get_tree().create_timer(10.0).timeout
	ble_manager.stop_scan()
	_log_console("Scan complete")

func _on_connect_button_pressed():
	var selected = device_list.get_selected_items()
	if selected.size() > 0:
		var device_name = device_list.get_item_text(selected[0])
		var address = discovered_devices.get(device_name, "")
		if address:
			_log_console("Connecting to " + device_name + "...")
			ble_manager.connect_to_device(address)

func _on_disconnect_button_pressed():
	ble_manager.disconnect()

func _on_record_button_pressed():
	var filename = filename_input.text.strip_edges()
	if filename.is_empty():
		_log_console("ERROR: Filename required")
		return
	
	_log_console("Starting recording: " + filename)
	ble_manager.start_recording(filename)
	is_recording = true
	record_button.visible = false
	stop_record_button.visible = true
	recording_status.text = "RECORDING: " + filename
	recording_status.add_theme_color_override("font_color", Color.RED)

func _on_stop_record_button_pressed():
	_log_console("Stopping recording...")
	ble_manager.stop_recording()
	is_recording = false
	record_button.visible = true
	stop_record_button.visible = false
	recording_status.text = ""

func _on_send_text_button_pressed():
	var text = text_input.text
	if text.length() > 0:
		_log_console("Sending text: " + text)
		ble_manager.send_type(text)
		text_input.clear()

func _on_quick_key_pressed(key_name: String):
	_log_console("Sending key: " + key_name)
	ble_manager.send_key(key_name)

func _on_help_button_pressed():
	ble_manager.request_help()

func _on_about_button_pressed():
	ble_manager.request_about()

## BLE signal handlers

func _on_device_found(device_name: String, device_address: String):
	if not discovered_devices.has(device_name):
		discovered_devices[device_name] = device_address
		device_list.add_item(device_name + " [" + device_address + "]")
		_log_console("Found: " + device_name)

func _on_connection_changed(connected: bool):
	_update_ui_state(connected)
	
	if connected:
		status_label.text = "Connected to PWDongle"
		status_label.add_theme_color_override("font_color", Color.GREEN)
		_log_console("Connected successfully")
	else:
		status_label.text = "Disconnected"
		status_label.add_theme_color_override("font_color", Color.RED)
		_log_console("Disconnected")
		
		# Reset recording state
		if is_recording:
			_on_stop_record_button_pressed()

func _on_data_received(data: String):
	_log_console("← " + data)

func _on_error_occurred(error_message: String):
	_log_console("ERROR: " + error_message)

## UI helpers

func _update_ui_state(connected: bool):
	scan_button.disabled = connected
	connect_button.disabled = connected
	disconnect_button.visible = connected
	
	# Recording panel
	record_button.disabled = not connected
	filename_input.editable = not is_recording
	
	# Quick actions
	for button in quick_actions.get_children():
		button.disabled = not connected
	
	# Text input
	text_input.editable = connected
	send_text_button.disabled = not connected

func _log_console(message: String):
	var timestamp = Time.get_time_string_from_system()
	console_output.text += "[" + timestamp + "] " + message + "\n"
	
	# Auto-scroll to bottom
	await get_tree().process_frame
	console_output.scroll_vertical = console_output.get_line_count()
