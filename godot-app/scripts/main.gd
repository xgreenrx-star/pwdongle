extends Control

## Main UI Controller for PWDongle app

@onready var ble_manager = $BLEManager
@onready var status_label = $MarginContainer/ScrollContainer/VBoxContainer/StatusPanel/StatusLabel
@onready var scan_button = $MarginContainer/ScrollContainer/VBoxContainer/ConnectionPanel/ScanButton
@onready var connect_button = $MarginContainer/ScrollContainer/VBoxContainer/ConnectionPanel/ConnectButton
@onready var disconnect_button = $MarginContainer/ScrollContainer/VBoxContainer/ConnectionPanel/DisconnectButton
@onready var device_list = $MarginContainer/ScrollContainer/VBoxContainer/ConnectionPanel/DeviceList

# Recording controls
@onready var filename_input = $MarginContainer/ScrollContainer/VBoxContainer/RecordingPanel/FilenameInput
@onready var record_button = $MarginContainer/ScrollContainer/VBoxContainer/RecordingPanel/RecordButton
@onready var stop_record_button = $MarginContainer/ScrollContainer/VBoxContainer/RecordingPanel/StopRecordButton
@onready var recording_status = $MarginContainer/ScrollContainer/VBoxContainer/RecordingPanel/RecordingStatus

# Quick actions
@onready var quick_actions = $MarginContainer/ScrollContainer/VBoxContainer/QuickActionsPanel/GridContainer
@onready var text_input = $MarginContainer/ScrollContainer/VBoxContainer/TypePanel/TextInput
@onready var send_text_button = $MarginContainer/ScrollContainer/VBoxContainer/TypePanel/SendButton

# On-screen keyboard
@onready var keyboard_grid = $MarginContainer/ScrollContainer/VBoxContainer/KeyboardPanel/KeyboardGrid

# Touchpad
@onready var touchpad_area = $MarginContainer/ScrollContainer/VBoxContainer/TouchpadPanel/TouchpadArea
@onready var left_click_button = $MarginContainer/ScrollContainer/VBoxContainer/TouchpadPanel/ButtonContainer/LeftClickButton
@onready var right_click_button = $MarginContainer/ScrollContainer/VBoxContainer/TouchpadPanel/ButtonContainer/RightClickButton

# Console
@onready var console_output = $MarginContainer/ScrollContainer/VBoxContainer/ConsolePanel/ConsoleOutput

var discovered_devices = {}
var is_recording = false
var is_connected = false
var last_touch_pos: Vector2 = Vector2.ZERO

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
	_setup_keyboard()

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
	is_connected = connected
	
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
	
	# On-screen keyboard
	for button in keyboard_grid.get_children():
		if button is Button:
			button.disabled = not connected

	# Touchpad click buttons
	left_click_button.disabled = not connected
	right_click_button.disabled = not connected

	# Touchpad area blocking
	touchpad_area.mouse_filter = Control.MOUSE_FILTER_STOP if connected else Control.MOUSE_FILTER_IGNORE
	
	# Text input
	text_input.editable = connected
	send_text_button.disabled = not connected

func _log_console(message: String):
	var timestamp = Time.get_time_string_from_system()
	console_output.text += "[" + timestamp + "] " + message + "\n"
	
	# Auto-scroll to bottom
	await get_tree().process_frame
	console_output.scroll_vertical = console_output.get_line_count()

func _setup_keyboard():
	# QWERTY + utility keys
	var rows = [
		["q","w","e","r","t","y","u","i","o","p"],
		["a","s","d","f","g","h","j","k","l"],
		["z","x","c","v","b","n","m"],
		["SPACE","BACKSPACE","ENTER","ESC"]
	]
	for row in rows:
		for key in row:
			var button = Button.new()
			button.text = key.to_upper()
			button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			button.pressed.connect(_on_keyboard_key_pressed.bind(key))
			keyboard_grid.add_child(button)

func _on_keyboard_key_pressed(key: String):
	if not is_connected:
		return
	match key:
		"SPACE":
			ble_manager.send_type(" ")
		"BACKSPACE":
			ble_manager.send_key("backspace")
		"ENTER":
			ble_manager.send_key("enter")
		"ESC":
			ble_manager.send_key("escape")
		_:
			if key.length() == 1:
				ble_manager.send_type(key)
			else:
				ble_manager.send_key(key)

func _on_touchpad_gui_input(event):
	if not is_connected:
		return
	if event is InputEventScreenTouch:
		if event.pressed:
			last_touch_pos = event.position
		else:
			last_touch_pos = Vector2.ZERO
	elif event is InputEventScreenDrag:
		if last_touch_pos == Vector2.ZERO:
			last_touch_pos = event.position
			return
		var delta = event.position - last_touch_pos
		last_touch_pos = event.position
		if delta.length() < 1:
			return
		var dx = int(delta.x)
		var dy = int(delta.y)
		ble_manager.send_mouse("move:" + str(dx) + "," + str(dy))
	elif event is InputEventMouseMotion:
		var delta_mouse = event.relative
		if delta_mouse.length() < 1:
			return
		var dxm = int(delta_mouse.x)
		var dym = int(delta_mouse.y)
		ble_manager.send_mouse("move:" + str(dxm) + "," + str(dym))

func _on_left_click_button_pressed():
	if is_connected:
		ble_manager.send_mouse("leftclick")

func _on_right_click_button_pressed():
	if is_connected:
		ble_manager.send_mouse("rightclick")
