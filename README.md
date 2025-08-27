# uhf_rfid

Flutter plugin for UHF RFID readers (Android) using a native library. Provides initialization, power control, inventory streaming, TID reads, power/work area configuration, and stream mode toggling (EPC or TID).

## Installation

Add to your `pubspec.yaml`:

```yaml
dependencies:
  uhf_rfid:
    path: .
```

Android only. Ensure the device has the supported UHF module and serial port.

## Quick start

```dart
import 'package:uhf_rfid/uhf_rfid.dart';

// 1) Initialize (optionally set correct serial port)
final ok = await UhfRfid.initialize(port: '/dev/ttyHS2');

// 2) Power on and start inventory
await UhfRfid.powerOn();
await UhfRfid.setStreamMode(tid: false); // false = EPC stream, true = TID stream
await UhfRfid.startInventory();

// 3) Listen for tags
final sub = UhfRfid.inventoryStream.listen((tag) {
  print('Tag: ${tag.epc} rssi=${tag.rssi}');
});

// 4) Stop and cleanup
await UhfRfid.stopInventory();
await UhfRfid.powerOff();
await UhfRfid.close();
await sub.cancel();
```

## API

All methods are static on `UhfRfid`.

- `Future<bool> initialize({String port = '/dev/ttyHS2'})`
  - Initializes the reader for the given serial `port`.
  - Returns `true` if the native reader was obtained.

- `Future<void> powerOn()` / `Future<void> powerOff()`
  - Controls UHF module power.

- `Future<void> startInventory()` / `Future<void> stopInventory()`
  - Starts/stops background inventory loop. Emits items on `inventoryStream`.

- `Stream<UhfRfidTag> get inventoryStream`
  - Stream of read results.
  - In EPC mode: `UhfRfidTag.epc` holds EPC hex, `rssi` is provided.
  - In TID mode: `UhfRfidTag.epc` contains the TID hex and `rssi` is 0.

- `Future<void> setStreamMode({required bool tid})`
  - Selects stream content: `tid: false` → EPC; `tid: true` → TID.

- `Future<String?> readTid({int startWord = 0, int wordCount = 6, String accessPasswordHex = '00000000'})`
  - One-shot TID read from the TID memory bank (bank 2).
  - `startWord`: word offset within TID bank.
  - `wordCount`: number of 16-bit words to read.
  - `accessPasswordHex`: 4-byte access password in hex.

- `Future<bool> setPower(int powerDb)` / `Future<int?> getPower()`
  - Set/get RF output power in dBm (0–33 typical). Note: getter/setter may be device/SDK dependent; on some devices only setting is effective.

- `Future<bool> setWorkArea(int area)` / `Future<int?> getWorkArea()`
  - Set/get regional work area (band). Values are device-specific (commonly 0–3). Consult device docs.

- `Future<Map<String, dynamic>> checkDeviceSupport({String port = '/dev/ttyHS2'})`
  - Checks if the device supports UHF RFID reading.
  - Returns a map with:
    - `supported`: boolean indicating if UHF reader is available
    - `reason`: string explaining the result
    - `port`: the port that was tested
  - Use this before calling `initialize()` to check hardware support.

Example:
```dart
final support = await UhfRfid.checkDeviceSupport();
if (support['supported']) {
  print('UHF reader available: ${support['reason']}');
  await UhfRfid.initialize();
} else {
  print('UHF reader not available: ${support['reason']}');
}
```

## Example app features

The `example` app demonstrates:

- Port selection and initialization
- Start/Stop and automatic power on/off
- EPC/TID stream toggle
- One-shot TID read button
- Power (dB) and Work Area controls
- Debounce window (ms) to suppress duplicate IDs
- Unique and total counts with simple list of observed IDs

## Debouncing duplicate tags (suppress repeats)

Inventory streams often return the same tag repeatedly while it remains in the antenna field. You can suppress repeats within a time window (debounce) on the Dart side:

```dart
int debounceMs = 300; // adjust as needed
final Map<String, int> lastSeenMs = {};

final sub = UhfRfid.inventoryStream.listen((tag) {
  final now = DateTime.now().millisecondsSinceEpoch;
  final last = lastSeenMs[tag.epc];
  if (last != null && now - last < debounceMs) {
    // Same ID seen too soon; skip
    return;
  }
  lastSeenMs[tag.epc] = now;

  // Handle unique-or-debounced tag here
  // e.g., update counts or UI
});

// When clearing state
lastSeenMs.clear();
```

Notes:
- This runs entirely on the Flutter side and works for both EPC and TID stream modes.
- The example app includes a Debounce (ms) slider and maintains `Unique` and `Total` counts using this pattern.

## Notes

- If you see `D/uhf_log: timeout 1`, it indicates no tags were read in that poll; this is not fatal.
- GPU/gralloc errors on some devices can be worked around using `--enable-software-rendering`.
- Serial ports vary by device: try `/dev/ttyHS2`, `/dev/ttyHS1`, `/dev/ttyS4`, etc.

## Work area codes (original project)

Use these values with `setWorkArea(area)` to select the regulatory band:

- 0: China (920.5–924.5 MHz)
- 1: North America (902–928 MHz)
- 2: Europe (865–868 MHz)
- 3: Japan (916–921 MHz)

Note: Some firmware variants may map these codes differently. If you observe poor reads, try another area value consistent with your region and verify with your device manual.

## License

See `LICENSE`.

