# VB2Arduino (VB6-like DSL → Arduino C++)

A minimal VB6-style language that transpiles to Arduino C++ and builds with PlatformIO/Arduino CLI. Intended as a starting point for a VB-flavored workflow on microcontrollers (e.g., ESP32-S3).

## Status
- Prototype: handles a small, Arduino-friendly VB subset
- Generates `generated/main.cpp`
- Optional PlatformIO build/upload via CLI flags

## Supported VB Subset (MVP)
- Entry points: `Sub Setup()`, `Sub Loop()`
- Declarations: `Const NAME = value`, `Dim x As Integer`, `Dim x`
- Control: `If/ElseIf/Else/End If`, `For i = a To b ... Next`, `While ... Wend`, `Do ... Loop` (while-only)
- I/O helpers: `PinMode pin, MODE`, `DigitalWrite pin, HIGH/LOW`, `DigitalRead pin`, `AnalogRead pin`, `AnalogWrite pin, value`, `Delay ms`, `SerialBegin baud`, `SerialPrint value`, `SerialPrintLine value`, `SerialAvailable()`, `SerialRead()`
- Expressions: `And/Or/Not`, `=` `<>` `<` `>` `<=` `>=`

## Quick Start
```bash
# Transpile only
python -m vb2arduino.cli examples/blink.vb --out generated

# Transpile + build with PlatformIO (board: esp32-s3-devkitm-1)
python -m vb2arduino.cli examples/blink.vb --out generated \
  --board esp32-s3-devkitm-1 --build

# Transpile + build + upload (set your port)
python -m vb2arduino.cli examples/blink.vb --out generated \
  --board esp32-s3-devkitm-1 --build --upload --port /dev/ttyUSB0
```

## How It Works
1. Parse a simplified VB-like syntax into an AST-lite structure.
2. Emit Arduino C++ (`generated/main.cpp`) with `setup()` and `loop()` bodies.
3. Optionally call `pio run` / `pio run --target upload` if `--build/--upload` provided.

## Notes & Limits
- Strings map to `String` (Arduino) for simplicity; adjust if heap is tight.
- Arrays and classes are not implemented in this prototype.
- Error messages are basic; the goal is to show the pipeline end-to-end.

## Examples
- `examples/blink.vb` — LED blink
- `examples/button_led.vb` — Button reads input, drives LED
- `examples/serial_echo.vb` — Serial echo with newline

## Requirements
- Python 3.10+
- PlatformIO CLI (`pio`) if using `--build/--upload`

## License
GPL-3.0-or-later (inherits project license).
