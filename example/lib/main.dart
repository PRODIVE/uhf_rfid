import 'dart:async';
import 'package:flutter/material.dart';
import 'package:uhf_rfid/uhf_rfid.dart';

void main() => runApp(const UhfDemoApp());

class UhfDemoApp extends StatelessWidget {
  const UhfDemoApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      home: UhfHomePage(),
    );
  }
}

class UhfHomePage extends StatefulWidget {
  const UhfHomePage({super.key});

  @override
  State<UhfHomePage> createState() => _UhfHomePageState();
}

class _UhfHomePageState extends State<UhfHomePage> {
  final TextEditingController _portController =
      TextEditingController(text: '/dev/ttyHS2');
  bool _initialized = false;
  bool _scanning = false;
  StreamSubscription<UhfRfidTag>? _sub;
  final Map<String, int> _epcCounts = {};
  String? _lastTid;
  bool _tidMode = false;
  int _currentPower = 20;
  int _currentWorkArea = 0;
  int _debounceMs = 300;
  final Map<String, int> _lastSeenMs = {};
  String? _deviceSupportStatus;
  String? _testResult;

  Future<void> _checkDeviceSupport() async {
    try {
      final support = await UhfRfid.checkDeviceSupport(port: _portController.text.trim());
      setState(() {
        _deviceSupportStatus = '${support['supported'] ? '✓' : '✗'} ${support['reason']}';
      });
    } catch (e) {
      setState(() {
        _deviceSupportStatus = 'Error: $e';
      });
    }
  }

  Future<void> _initialize() async {
    final ok = await UhfRfid.initialize(port: _portController.text.trim());
    if (ok) {
      final power = await UhfRfid.getPower();
      final area = await UhfRfid.getWorkArea();
      setState(() {
        _initialized = ok;
        if (power != null) _currentPower = power;
        if (area != null) _currentWorkArea = area;
      });
    } else {
      setState(() => _initialized = ok);
    }
  }

  Future<void> _start() async {
    if (!_initialized) return;
    await UhfRfid.powerOn();
    await UhfRfid.setStreamMode(tid: _tidMode);
    await UhfRfid.startInventory();
    _sub ??= UhfRfid.inventoryStream.listen(
      (tag) {
        final now = DateTime.now().millisecondsSinceEpoch;
        final last = _lastSeenMs[tag.epc];
        if (last != null && now - last < _debounceMs) {
          return;
        }
        _lastSeenMs[tag.epc] = now;
        setState(() {
          _epcCounts.update(tag.epc, (v) => v + 1, ifAbsent: () => 1);
        });
      },
      onError: (error) {
        print('Stream error: $error');
        setState(() {
          _testResult = 'Stream error: $error';
        });
      },
      onDone: () {
        print('Stream closed');
        setState(() {
          _testResult = 'Stream closed unexpectedly';
        });
      }
    );
    setState(() => _scanning = true);
  }

  Future<void> _stop() async {
    await UhfRfid.stopInventory();
    await UhfRfid.powerOff();
    await _sub?.cancel();
    _sub = null;
    setState(() => _scanning = false);
  }

  Future<void> _close() async {
    await _stop();
    await UhfRfid.close();
    setState(() => _initialized = false);
  }

  Future<void> _testReader() async {
    try {
      final result = await UhfRfid.testReader();
      setState(() {
        _testResult = result.toString();
      });
    } catch (e) {
      setState(() {
        _testResult = 'Test error: $e';
      });
    }
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('UHF RFID Demo')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(children: [
              Expanded(
                child: TextField(
                  controller: _portController,
                  decoration: const InputDecoration(
                    labelText: 'Serial Port',
                    hintText: '/dev/ttyHS2',
                  ),
                ),
              ),
              const SizedBox(width: 8),
              ElevatedButton(
                onPressed: _checkDeviceSupport,
                child: const Text('Check Support'),
              ),
              const SizedBox(width: 8),
              ElevatedButton(
                onPressed: _initialize,
                child: Text(_initialized ? 'Re-Init' : 'Init'),
              ),
            ]),
            const SizedBox(height: 12),
            if (_deviceSupportStatus != null) ...[
              Text('Device Support: $_deviceSupportStatus'),
              const SizedBox(height: 8),
            ],
            Row(children: [
              Switch(
                value: _tidMode,
                onChanged: _initialized && !_scanning
                    ? (v) async {
                        setState(() => _tidMode = v);
                        await UhfRfid.setStreamMode(tid: v);
                      }
                    : null,
              ),
              const SizedBox(width: 8),
              Text(_tidMode ? 'Mode: TID' : 'Mode: EPC'),
            ]),
            const SizedBox(height: 8),
            Wrap(spacing: 8, children: [
              ElevatedButton(
                onPressed: _initialized && !_scanning ? _start : null,
                child: const Text('Start'),
              ),
              ElevatedButton(
                onPressed: _scanning ? _stop : null,
                child: const Text('Stop'),
              ),
              ElevatedButton(
                onPressed: _initialized ? _close : null,
                child: const Text('Close'),
              ),
              ElevatedButton(
                onPressed: _initialized ? () async {
                  final tid = await UhfRfid.readTid(startWord: 0, wordCount: 6);
                  setState(() { _lastTid = tid; });
                } : null,
                child: const Text('Read TID'),
              ),
              ElevatedButton(
                onPressed: () => setState(() {
                  _epcCounts.clear();
                  _lastSeenMs.clear();
                }),
                child: const Text('Clear'),
              ),
              ElevatedButton(
                onPressed: _initialized ? _testReader : null,
                child: const Text('Test Reader'),
              ),
            ]),
            const SizedBox(height: 12),
            Text('Unique: ${_epcCounts.length}   Total: ${_epcCounts.values.fold<int>(0, (a, b) => a + b)}'),
            Text('Initialized: $_initialized   Scanning: $_scanning'),
            if (_lastTid != null) ...[
              const SizedBox(height: 6),
              Text('TID: $_lastTid'),
            ],
            if (_testResult != null) ...[
              const SizedBox(height: 6),
              Text('Test Result: $_testResult'),
            ],
            const SizedBox(height: 12),
            if (_initialized) ...[
              Row(children: [
                const Text('Power: '),
                Expanded(
                  child: Slider(
                    value: _currentPower.toDouble(),
                    min: 0,
                    max: 33,
                    divisions: 33,
                    label: '${_currentPower}dB',
                    onChanged: (value) {
                      setState(() => _currentPower = value.round());
                    },
                    onChangeEnd: (value) async {
                      await UhfRfid.setPower(value.round());
                    },
                  ),
                ),
                SizedBox(
                  width: 50,
                  child: Text('${_currentPower}dB'),
                ),
              ]),
              Row(children: [
                const Text('Work Area: '),
                Expanded(
                  child: Slider(
                    value: _currentWorkArea.toDouble(),
                    min: 0,
                    max: 3,
                    divisions: 3,
                    label: 'Area $_currentWorkArea',
                    onChanged: (value) {
                      setState(() => _currentWorkArea = value.round());
                    },
                    onChangeEnd: (value) async {
                      await UhfRfid.setWorkArea(value.round());
                    },
                  ),
                ),
                SizedBox(
                  width: 50,
                  child: Text('Area $_currentWorkArea'),
                ),
              ]),
              const SizedBox(height: 8),
              Row(children: [
                const Text('Debounce (ms): '),
                Expanded(
                  child: Slider(
                    value: _debounceMs.toDouble(),
                    min: 0,
                    max: 2000,
                    divisions: 40,
                    label: '${_debounceMs}ms',
                    onChanged: (value) {
                      setState(() => _debounceMs = value.round());
                    },
                  ),
                ),
                SizedBox(
                  width: 70,
                  child: Text('${_debounceMs}ms'),
                ),
              ]),
            ],
            const Divider(height: 24),
            const Text('Tags:'),
            const SizedBox(height: 8),
            Expanded(
              child: ListView(
                children: _epcCounts.entries
                    .map((e) => ListTile(
                          dense: true,
                          title: Text(e.key),
                          trailing: Text('x${e.value}'),
                        ))
                    .toList(),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
