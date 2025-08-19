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

  Future<void> _initialize() async {
    final ok = await UhfRfid.initialize(port: _portController.text.trim());
    setState(() => _initialized = ok);
  }

  Future<void> _start() async {
    if (!_initialized) return;
    await UhfRfid.startInventory();
    _sub ??= UhfRfid.inventoryStream.listen((tag) {
      setState(() {
        _epcCounts.update(tag.epc, (v) => v + 1, ifAbsent: () => 1);
      });
    });
    setState(() => _scanning = true);
  }

  Future<void> _stop() async {
    await UhfRfid.stopInventory();
    await _sub?.cancel();
    _sub = null;
    setState(() => _scanning = false);
  }

  Future<void> _close() async {
    await _stop();
    await UhfRfid.close();
    setState(() => _initialized = false);
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
                onPressed: _initialize,
                child: Text(_initialized ? 'Re-Init' : 'Init'),
              ),
            ]),
            const SizedBox(height: 12),
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
                onPressed: () => setState(_epcCounts.clear),
                child: const Text('Clear'),
              ),
            ]),
            const SizedBox(height: 12),
            Text('Initialized: $_initialized   Scanning: $_scanning'),
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
