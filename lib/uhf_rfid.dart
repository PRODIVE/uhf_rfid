import 'dart:async';
import 'package:flutter/services.dart';

class UhfRfidTag {
  final String epc;
  final int rssi;
  UhfRfidTag(this.epc, this.rssi);
}

class UhfRfid {
  static const MethodChannel _methods = MethodChannel('uhf_rfid/methods');
  static const EventChannel _epcStream = EventChannel('uhf_rfid/epc_stream');

  static Future<bool> initialize({String port = '/dev/ttyHS2'}) async {
    final ok = await _methods.invokeMethod<bool>('initialize', {'port': port});
    return ok ?? false;
  }

  static Future<void> powerOn() => _methods.invokeMethod('powerOn');
  static Future<void> powerOff() => _methods.invokeMethod('powerOff');

  static Future<void> startInventory() => _methods.invokeMethod('startInventory');
  static Future<void> stopInventory() => _methods.invokeMethod('stopInventory');
  static Future<void> close() => _methods.invokeMethod('close');

  static Stream<UhfRfidTag> get inventoryStream {
    return _epcStream.receiveBroadcastStream().map((dynamic e) {
      final map = Map<String, dynamic>.from(e as Map);
      return UhfRfidTag(map['epc'] as String, (map['rssi'] as num?)?.toInt() ?? 0);
    });
  }
}
