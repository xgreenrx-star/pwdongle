#!/usr/bin/env python3
"""
PWDongle Macro Recorder - Python/Termux Proof of Concept

This script demonstrates recording keyboard and mouse input
and transmitting commands to PWDongle via Bluetooth LE.

Requirements:
- Python 3.7+
- bleak (Bluetooth LE library)
- pynput (keyboard/mouse capture)

Installation (Termux):
    pkg install python
    pip install bleak pynput

Installation (Desktop Linux):
    pip3 install bleak pynput

Usage:
    python3 pwdongle_recorder.py
"""

import asyncio
import time
from datetime import datetime
from typing import Optional
import sys

try:
    from bleak import BleakClient, BleakScanner
    BLEAK_AVAILABLE = True
except ImportError:
    print("Error: bleak library required for Bluetooth")
    print("Install with: pip install bleak")
    sys.exit(1)

# pynput is optional (not available on Android/Termux)
try:
    from pynput import keyboard, mouse
    PYNPUT_AVAILABLE = True
except ImportError:
    PYNPUT_AVAILABLE = False
    print("Note: pynput not available (normal on Android)")
    print("Using manual command mode instead\n")

# Nordic UART Service UUIDs
NUS_SERVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
NUS_RX_CHAR_UUID = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"  # Write
NUS_TX_CHAR_UUID = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"  # Notify

DELAY_THRESHOLD_MS = 50  # Minimum delay to record


class PWDongleRecorder:
    """Records keyboard/mouse input and sends to PWDongle via BLE"""
    
    def __init__(self):
        self.client: Optional[BleakClient] = None
        self.is_recording = False
        self.filename = ""
        self.events = []
        self.start_time = 0
        self.last_event_time = 0
        self.mouse_x = 0
        self.mouse_y = 0
        
        # Input listeners
        self.keyboard_listener = None
        self.mouse_listener = None
    
    async def scan_devices(self):
        """Scan for PWDongle BLE devices"""
        print("Scanning for PWDongle devices...")
        devices = await BleakScanner.discover(timeout=10.0)
        
        pwdongle_devices = [d for d in devices if d.name and "PWDongle" in d.name]
        
        if not pwdongle_devices:
            print("No PWDongle devices found")
            return None
        
        print(f"\nFound {len(pwdongle_devices)} PWDongle device(s):")
        for i, device in enumerate(pwdongle_devices):
            print(f"  {i+1}. {device.name} ({device.address})")
        
        return pwdongle_devices
    
    async def connect(self, address: str):
        """Connect to PWDongle device"""
        print(f"Connecting to {address}...")
        self.client = BleakClient(address)
        await self.client.connect()
        
        # Enable notifications on TX characteristic
        await self.client.start_notify(NUS_TX_CHAR_UUID, self._notification_handler)
        
        print("Connected to PWDongle!")
        return True
    
    async def disconnect(self):
        """Disconnect from PWDongle"""
        if self.client and self.client.is_connected:
            await self.client.disconnect()
            print("Disconnected")
    
    def _notification_handler(self, sender, data):
        """Handle notifications from PWDongle"""
        message = data.decode('utf-8', errors='ignore')
        print(f"PWDongle: {message.strip()}")
    
    async def send_command(self, command: str):
        """Send command to PWDongle"""
        if not self.client or not self.client.is_connected:
            print("Error: Not connected to PWDongle")
            return False
        
        data = (command + "\n").encode('utf-8')
        await self.client.write_gatt_char(NUS_RX_CHAR_UUID, data)
        print(f"Sent: {command}")
        return True
    
    def start_recording(self, filename: str):
        """Start macro recording"""
        self.filename = filename
        self.is_recording = True
        self.events = []
        self.start_time = time.time()
        self.last_event_time = self.start_time
        
        print(f"\n=== RECORDING STARTED: {filename} ===")
        
        if PYNPUT_AVAILABLE:
            print("Position mouse at top-left corner and perform actions...")
            print("Press ESC to stop recording\n")
            # Start input listeners
            self._start_listeners()
        else:
            print("Manual command mode - Enter commands directly:")
            print("  key:<name>        - Record key (e.g., key:enter)")
            print("  mouse:x,y         - Record mouse move (e.g., mouse:100,200)")
            print("  click:<button>    - Record click (e.g., click:left)")
            print("  scroll:<amount>   - Record scroll (e.g., scroll:5)")
            print("  stop              - Stop recording\n")
        
        # Send RECORD command to PWDongle
        asyncio.create_task(self.send_command(f"RECORD:{filename}"))
    
    def stop_recording(self):
        """Stop macro recording"""
        if not self.is_recording:
            return
        
        self.is_recording = False
        duration = time.time() - self.start_time
        
        # Stop listeners if available
        if PYNPUT_AVAILABLE:
            self._stop_listeners()
        
        print(f"\n=== RECORDING STOPPED ===")
        print(f"Duration: {duration:.2f}s")
        print(f"Events captured: {len(self.events)}")
        
        # Send STOPRECORD command to PWDongle
        asyncio.create_task(self.send_command("STOPRECORD"))
        
        # Generate and save macro
        self._generate_macro()
    
    def process_manual_command(self, cmd: str):
        """Process manual command input (for Android/Termux)"""
        if not self.is_recording:
            return
        
        cmd = cmd.strip().lower()
        
        if cmd == "stop":
            self.stop_recording()
            return
        
        self._add_delay_if_needed()
        
        if cmd.startswith("key:"):
            key_name = cmd[4:].strip()
            self.events.append(("KEY", key_name))
            asyncio.create_task(self.send_command(f"KEY:{key_name}"))
            print(f"  Recorded: KEY {key_name}")
            
        elif cmd.startswith("mouse:"):
            coords = cmd[6:].strip()
            parts = coords.split(',')
            if len(parts) == 2:
                x, y = int(parts[0]), int(parts[1])
                self.events.append(("MOUSE_MOVE", x, y))
                asyncio.create_task(self.send_command(f"MOUSE:MOVE:{x},{y}"))
                print(f"  Recorded: MOUSE MOVE {x},{y}")
                
        elif cmd.startswith("click:"):
            button = cmd[6:].strip()
            self.events.append(("MOUSE_BUTTON", button, True))
            asyncio.create_task(self.send_command(f"MOUSE:CLICK:{button}"))
            print(f"  Recorded: MOUSE CLICK {button}")
            
        elif cmd.startswith("scroll:"):
            amount = int(cmd[7:].strip())
            self.events.append(("MOUSE_SCROLL", amount))
            asyncio.create_task(self.send_command(f"MOUSE:SCROLL:{amount}"))
            print(f"  Recorded: MOUSE SCROLL {amount}")
        
        self.last_event_time = time.time()
    
    def _start_listeners(self):
        """Start keyboard and mouse listeners"""
        if not PYNPUT_AVAILABLE:
            return
            
        self.keyboard_listener = keyboard.Listener(
            on_press=self._on_key_press,
            on_release=self._on_key_release
        )
        self.mouse_listener = mouse.Listener(
            on_move=self._on_mouse_move,
            on_click=self._on_mouse_click,
            on_scroll=self._on_mouse_scroll
        )
        
        self.keyboard_listener.start()
        self.mouse_listener.start()
    
    def _stop_listeners(self):
        """Stop input listeners"""
        if not PYNPUT_AVAILABLE:
            return
            
        if self.keyboard_listener:
            self.keyboard_listener.stop()
        if self.mouse_listener:
            self.mouse_listener.stop()
    
    def _add_delay_if_needed(self):
        """Add delay event if threshold exceeded"""
        now = time.time()
        delay_ms = int((now - self.last_event_time) * 1000)
        
        if delay_ms >= DELAY_THRESHOLD_MS:
            self.events.append(("DELAY", delay_ms))
        
        self.last_event_time = now
    
    def _on_key_press(self, key):
        """Handle key press events"""
        if not self.is_recording:
            return
        
        # Stop recording on ESC
        if key == keyboard.Key.esc:
            self.stop_recording()
            return False
        
        self._add_delay_if_needed()
        
        # Convert key to PWDongle format
        key_name = self._get_key_name(key)
        self.events.append(("KEY", key_name))
        
        # Send to PWDongle
        asyncio.create_task(self.send_command(f"KEY:{key_name}"))
    
    def _on_key_release(self, key):
        """Handle key release events (not recorded for PWDongle)"""
        pass
    
    def _on_mouse_move(self, x, y):
        """Handle mouse move events"""
        if not self.is_recording:
            self.mouse_x = x
            self.mouse_y = y
            return
        
        if x == self.mouse_x and y == self.mouse_y:
            return
        
        self._add_delay_if_needed()
        
        self.mouse_x = x
        self.mouse_y = y
        self.events.append(("MOUSE_MOVE", x, y))
        
        # Send to PWDongle
        asyncio.create_task(self.send_command(f"MOUSE:MOVE:{x},{y}"))
    
    def _on_mouse_click(self, x, y, button, pressed):
        """Handle mouse click events"""
        if not self.is_recording:
            return
        
        self._add_delay_if_needed()
        
        button_name = str(button).split('.')[-1].lower()
        action = "DOWN" if pressed else "UP"
        
        self.events.append(("MOUSE_BUTTON", button_name, pressed))
        
        # Send to PWDongle
        asyncio.create_task(self.send_command(f"MOUSE:{action}:{button_name}"))
    
    def _on_mouse_scroll(self, x, y, dx, dy):
        """Handle mouse scroll events"""
        if not self.is_recording:
            return
        
        self._add_delay_if_needed()
        
        scroll_amount = int(dy)
        self.events.append(("MOUSE_SCROLL", scroll_amount))
        
        # Send to PWDongle
        asyncio.create_task(self.send_command(f"MOUSE:SCROLL:{scroll_amount}"))
    
    def _get_key_name(self, key) -> str:
        """Convert pynput key to PWDongle key name"""
        # Special keys
        if hasattr(key, 'name'):
            return key.name.lower()
        
        # Printable characters
        if hasattr(key, 'char') and key.char:
            return key.char.lower()
        
        return str(key)
    
    def _generate_macro(self):
        """Generate macro file from recorded events"""
        filename = f"{self.filename}.txt"
        
        with open(filename, 'w') as f:
            # Header
            f.write(f"// Recorded macro: {self.filename}\n")
            f.write(f"// Date: {datetime.now()}\n")
            f.write(f"// Duration: {time.time() - self.start_time:.2f}s\n")
            f.write(f"// Events: {len(self.events)}\n")
            f.write("\n")
            
            # Initial RESET
            f.write("{{MOUSE:RESET}}\n")
            f.write("{{DELAY:100}}\n")
            f.write("\n")
            
            # Events
            for event in self.events:
                if event[0] == "DELAY":
                    f.write(f"{{{{DELAY:{event[1]}}}}}\n")
                elif event[0] == "KEY":
                    f.write(f"{{{{KEY:{event[1]}}}}}\n")
                elif event[0] == "MOUSE_MOVE":
                    f.write(f"{{{{MOUSE:MOVE:{event[1]},{event[2]}}}}}\n")
                elif event[0] == "MOUSE_BUTTON":
                    action = "DOWN" if event[2] else "UP"
                    f.write(f"{{{{MOUSE:{action}:{event[1]}}}}}\n")
                elif event[0] == "MOUSE_SCROLL":
                    f.write(f"{{{{MOUSE:SCROLL:{event[1]}}}}}\n")
        
        print(f"\nMacro saved to: {filename}")


async def main():
    """Main entry point"""
    print("=== PWDongle Macro Recorder (Python) ===\n")
    
    recorder = PWDongleRecorder()
    
    try:
        # Scan for devices
        devices = await recorder.scan_devices()
        if not devices:
            return
        
        # Select device
        if len(devices) == 1:
            selected = devices[0]
        else:
            choice = int(input("\nSelect device (1-{}): ".format(len(devices))))
            selected = devices[choice - 1]
        
        # Connect
        await recorder.connect(selected.address)
        
        # Get filename
        filename = input("\nEnter macro filename (without .txt): ").strip()
        if not filename:
            filename = "recorded_macro"
        
        # Start recording
        recorder.start_recording(filename)
        
        if PYNPUT_AVAILABLE:
            # Instructions for desktop with pynput
            print("\n=== INSTRUCTIONS ===")
            print("1. Position your mouse at the top-left corner")
            print("2. Press ENTER to start recording")
            print("3. Perform your actions (keyboard/mouse)")
            print("4. Press ESC to stop recording")
            input("\nPress ENTER when ready...")
            
            # Wait for recording to complete (ESC pressed)
            while recorder.is_recording:
                await asyncio.sleep(0.1)
        else:
            # Manual mode for Android/Termux
            print("\n=== MANUAL MODE (Android/Termux) ===")
            print("Enter commands to send to PWDongle:")
            
            while recorder.is_recording:
                try:
                    cmd = await asyncio.get_event_loop().run_in_executor(
                        None, input, "> "
                    )
                    recorder.process_manual_command(cmd)
                except EOFError:
                    break
                except KeyboardInterrupt:
                    recorder.stop_recording()
                    break
        
        # Disconnect
        await asyncio.sleep(1)
        await recorder.disconnect()
        
        print("\nRecording complete!")
        
    except KeyboardInterrupt:
        print("\n\nInterrupted by user")
        if recorder.is_recording:
            recorder.stop_recording()
        await recorder.disconnect()
    except Exception as e:
        print(f"\nError: {e}")
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    asyncio.run(main())
