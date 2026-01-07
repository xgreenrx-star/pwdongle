# Live Control Latency Optimization Analysis

## Current Status
- Implemented app-side optimizations: write-without-response, MTU negotiation, disabled logging
- **Remaining bottleneck: Command format size over BLE**

## Latency Breakdown

### Current Format
- `KEY:41_DOWN\n` = 11 bytes
- BLE transmission @ ~1-2 Mbps = ~44-88 µs to send
- **Actual latency dominated by**: App processing, BLE stack overhead, PWDongle parsing

### Optimized Format Proposal
- Short format: `K:41:D\n` = 6 bytes
- Reduction: **45% smaller payload**
- Format: `K:keyCode:D/U` for keyboard, `M:x:y:L/R/M` for mouse

## Proposed Command Set

### Keyboard
```
K:keyCode:D     = KEY DOWN
K:keyCode:U     = KEY UP
```

### Mouse
```
M:x:y:M         = MOUSE MOVE
M:x:y:L         = LEFT CLICK
M:x:y:R         = RIGHT CLICK
M:x:y:W:+/-     = SCROLL (wheel)
```

### Existing Format (Fallback)
```
KEY:keyCode_DOWN
MOUSE:x_y_MOVE
etc.
```

## Implementation Plan

### Phase 1: PWDongle Firmware Update
1. Add parser for short command format in `usb.cpp::processBLELine()`
2. Detect format by first character: `K` or `M` = short, `K` in `KEY` = long
3. Convert short format to existing processing code
4. Maintain backward compatibility

### Phase 2: Android App Update
1. Use short format by default for live control
2. Fallback to long format if device doesn't support
3. Track device capability flag after connection

## Expected Latency Improvement
- App-side: ~45% reduction in BLE transmission time
- Total: Another **15-25ms reduction** on top of current optimizations
- Final target: **10-20ms per event** (vs current 45-60ms)

## Code Location for PWDongle Changes
File: `src/usb.cpp` function `processBLELine()`

Example parsing:
```cpp
if (line.startsWith("K:")) {
    // K:keyCode:D/U format
    // Parse and route to sendKeyboardCommand()
} else if (line.startsWith("M:")) {
    // M:x:y:L/R/M format
    // Parse and route to sendMouseCommand()
} else if (line.startsWith("KEY:")) {
    // Legacy format - existing code
}
```

## Testing Strategy
1. Build firmware with new format support
2. Update app to send short format
3. Monitor BLE packet sizes in logs
4. Measure end-to-end latency (keyboard press → PC keystroke)
