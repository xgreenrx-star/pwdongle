#!/usr/bin/env python3
"""
PWDongle Macro Recorder - Termux/Android Version

Simple version using bluetooth serial socket (no complex dependencies)

Requirements:
- Python 3
- pybluez (optional, falls back to manual connection)

Installation (Termux):
    pkg install python bluetooth
    pip install pybluez

Usage:
    python3 pwdongle_termux.py
"""

import time
import sys
import subprocess
from datetime import datetime

# Try to import bluetooth, but make it optional
try:
    import bluetooth
    BLUETOOTH_AVAILABLE = True
except ImportError:
    BLUETOOTH_AVAILABLE = False
    print("Note: pybluez not available")
    print("You can still use manual pairing with Serial Bluetooth Terminal app\n")


class PWDongleRecorder:
    """Simplified recorder for Termux/Android"""
    
    def __init__(self):
        self.is_recording = False
        self.filename = ""
        self.events = []
        self.start_time = 0
        self.last_event_time = 0
        self.sock = None
    
    def scan_devices(self):
        """Scan for Bluetooth devices using bluetoothctl"""
        print("Scanning for Bluetooth devices...")
        print("(Make sure Bluetooth is enabled and PWDongle is advertising)\n")
        
        try:
            # Use bluetoothctl to scan
            subprocess.run(["bluetoothctl", "scan", "on"], timeout=1, 
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            time.sleep(5)
            
            # Get devices
            result = subprocess.run(["bluetoothctl", "devices"], 
                                  capture_output=True, text=True)
            
            devices = []
            for line in result.stdout.split('\n'):
                if 'Device' in line and ('PWDongle' in line or 'ESP32' in line):
                    parts = line.split()
                    if len(parts) >= 3:
                        addr = parts[1]
                        name = ' '.join(parts[2:])
                        devices.append((addr, name))
                        print(f"Found: {name} ({addr})")
            
            subprocess.run(["bluetoothctl", "scan", "off"], 
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            
            return devices
        except Exception as e:
            print(f"Error scanning: {e}")
            return []
    
    def connect_pybluez(self, address):
        """Connect using pybluez (if available)"""
        if not BLUETOOTH_AVAILABLE:
            return False
        
        print(f"Connecting to {address}...")
        
        try:
            # Find RFCOMM channel
            services = bluetooth.find_service(address=address)
            if not services:
                print("No services found. Make sure device is paired.")
                return False
            
            # Use Serial Port Profile
            port = None
            for svc in services:
                if "Serial" in svc["name"] or "SPP" in svc["name"]:
                    port = svc["port"]
                    break
            
            if port is None:
                port = 1  # Default RFCOMM channel
            
            # Connect
            self.sock = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
            self.sock.connect((address, port))
            
            print("Connected!")
            return True
            
        except Exception as e:
            print(f"Connection error: {e}")
            print("\nTry pairing first:")
            print(f"  bluetoothctl pair {address}")
            print(f"  bluetoothctl trust {address}")
            return False
    
    def connect_manual(self, address):
        """Manual connection instructions"""
        print("\n=== MANUAL CONNECTION ===")
        print("Since pybluez isn't available, use this method:")
        print("")
        print("1. Install 'Serial Bluetooth Terminal' app from Play Store")
        print("2. Open the app")
        print("3. Tap menu (≡) → Devices")
        print(f"4. Select: PWDongle or {address}")
        print("5. Once connected, type commands in the app")
        print("")
        print("Commands to send:")
        print("  RECORD:filename")
        print("  KEY:enter")
        print("  MOUSE:MOVE:100,200")
        print("  MOUSE:CLICK:left")
        print("  STOPRECORD")
        print("")
        input("Press ENTER when done...")
        return False
    
    def send_command(self, command):
        """Send command via Bluetooth"""
        if not self.sock:
            print(f"Would send: {command}")
            return False
        
        try:
            data = (command + "\n").encode('utf-8')
            self.sock.send(data)
            print(f"Sent: {command}")
            
            # Wait for response (optional)
            time.sleep(0.1)
            
            return True
        except Exception as e:
            print(f"Send error: {e}")
            return False
    
    def start_recording(self, filename):
        """Start recording"""
        self.filename = filename
        self.is_recording = True
        self.events = []
        self.start_time = time.time()
        self.last_event_time = self.start_time
        
        print(f"\n=== RECORDING: {filename} ===")
        print("Enter commands (type 'help' for list, 'stop' to finish):\n")
        
        self.send_command(f"RECORD:{filename}")
    
    def stop_recording(self):
        """Stop recording"""
        if not self.is_recording:
            return
        
        self.is_recording = False
        duration = time.time() - self.start_time
        
        self.send_command("STOPRECORD")
        
        print(f"\n=== RECORDING STOPPED ===")
        print(f"Duration: {duration:.2f}s")
        print(f"Events: {len(self.events)}")
        
        self.generate_macro()
    
    def process_command(self, cmd):
        """Process user command"""
        cmd = cmd.strip().lower()
        
        if cmd == "stop":
            self.stop_recording()
            return False
        
        if cmd == "help":
            print("\nCommands:")
            print("  key:<name>       - Send key (e.g., key:enter, key:tab)")
            print("  mouse:<x>,<y>    - Move mouse (e.g., mouse:500,300)")
            print("  click:<button>   - Click (e.g., click:left, click:right)")
            print("  scroll:<amount>  - Scroll (e.g., scroll:5, scroll:-3)")
            print("  type:<text>      - Type text (e.g., type:hello)")
            print("  delay:<ms>       - Add delay (e.g., delay:1000)")
            print("  stop             - Stop recording\n")
            return True
        
        # Add delay since last event
        self.add_delay_if_needed()
        
        if cmd.startswith("key:"):
            key_name = cmd[4:].strip()
            self.events.append(("KEY", key_name))
            self.send_command(f"KEY:{key_name}")
            
        elif cmd.startswith("mouse:"):
            coords = cmd[6:].strip()
            parts = coords.split(',')
            if len(parts) == 2:
                x, y = int(parts[0]), int(parts[1])
                self.events.append(("MOUSE_MOVE", x, y))
                self.send_command(f"MOUSE:MOVE:{x},{y}")
        
        elif cmd.startswith("click:"):
            button = cmd[6:].strip()
            self.events.append(("MOUSE_CLICK", button))
            self.send_command(f"MOUSE:CLICK:{button}")
        
        elif cmd.startswith("scroll:"):
            amount = int(cmd[7:].strip())
            self.events.append(("MOUSE_SCROLL", amount))
            self.send_command(f"MOUSE:SCROLL:{amount}")
        
        elif cmd.startswith("type:"):
            text = cmd[5:].strip()
            self.events.append(("TYPE", text))
            self.send_command(f"TYPE:{text}")
        
        elif cmd.startswith("delay:"):
            ms = int(cmd[6:].strip())
            self.events.append(("DELAY", ms))
        
        self.last_event_time = time.time()
        return True
    
    def add_delay_if_needed(self):
        """Add delay if threshold exceeded"""
        now = time.time()
        delay_ms = int((now - self.last_event_time) * 1000)
        
        if delay_ms >= 50:
            self.events.append(("DELAY", delay_ms))
    
    def generate_macro(self):
        """Generate macro file"""
        filename = f"{self.filename}.txt"
        
        with open(filename, 'w') as f:
            f.write(f"// Recorded: {self.filename}\n")
            f.write(f"// Date: {datetime.now()}\n")
            f.write(f"// Duration: {time.time() - self.start_time:.2f}s\n\n")
            
            f.write("{{MOUSE:RESET}}\n")
            f.write("{{DELAY:100}}\n\n")
            
            for event in self.events:
                if event[0] == "DELAY":
                    f.write(f"{{{{DELAY:{event[1]}}}}}\n")
                elif event[0] == "KEY":
                    f.write(f"{{{{KEY:{event[1]}}}}}\n")
                elif event[0] == "MOUSE_MOVE":
                    f.write(f"{{{{MOUSE:MOVE:{event[1]},{event[2]}}}}}\n")
                elif event[0] == "MOUSE_CLICK":
                    f.write(f"{{{{MOUSE:CLICK:{event[1]}}}}}\n")
                elif event[0] == "MOUSE_SCROLL":
                    f.write(f"{{{{MOUSE:SCROLL:{event[1]}}}}}\n")
                elif event[0] == "TYPE":
                    f.write(f"{{{{TEXT:{event[1]}}}}}\n")
        
        print(f"\nMacro saved: {filename}")
        print(f"Location: {subprocess.run(['pwd'], capture_output=True, text=True).stdout.strip()}/{filename}")
    
    def disconnect(self):
        """Disconnect"""
        if self.sock:
            try:
                self.sock.close()
            except:
                pass
        print("Disconnected")


def main():
    """Main entry point"""
    print("=== PWDongle Recorder (Termux) ===\n")
    
    recorder = PWDongleRecorder()
    
    try:
        # Scan option
        print("1. Scan for PWDongle")
        print("2. Enter MAC address manually")
        print("3. Use Serial Bluetooth Terminal app (no Python connection)")
        choice = input("\nSelect option (1-3): ").strip()
        
        if choice == "1":
            devices = recorder.scan_devices()
            if not devices:
                print("\nNo devices found. Make sure PWDongle is on and advertising.")
                return
            
            if len(devices) == 1:
                addr, name = devices[0]
            else:
                for i, (addr, name) in enumerate(devices):
                    print(f"{i+1}. {name} ({addr})")
                idx = int(input("Select device: ")) - 1
                addr, name = devices[idx]
            
            if BLUETOOTH_AVAILABLE:
                if not recorder.connect_pybluez(addr):
                    recorder.connect_manual(addr)
                    return
            else:
                recorder.connect_manual(addr)
                return
        
        elif choice == "2":
            addr = input("Enter MAC address (e.g., AA:BB:CC:DD:EE:FF): ").strip()
            if BLUETOOTH_AVAILABLE:
                if not recorder.connect_pybluez(addr):
                    recorder.connect_manual(addr)
                    return
            else:
                recorder.connect_manual(addr)
                return
        
        else:
            recorder.connect_manual("device")
            return
        
        # Get filename
        filename = input("\nEnter macro filename (without .txt): ").strip()
        if not filename:
            filename = "termux_macro"
        
        # Start recording
        recorder.start_recording(filename)
        
        # Process commands
        while recorder.is_recording:
            try:
                cmd = input("> ")
                if not recorder.process_command(cmd):
                    break
            except EOFError:
                break
        
        recorder.disconnect()
        print("\nDone!")
        
    except KeyboardInterrupt:
        print("\n\nInterrupted")
        if recorder.is_recording:
            recorder.stop_recording()
        recorder.disconnect()
    except Exception as e:
        print(f"\nError: {e}")
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    main()
