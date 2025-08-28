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

  static Future<Map<String, dynamic>> checkDeviceSupport({String port = '/dev/ttyHS2'}) async {
    final result = await _methods.invokeMethod<Map<dynamic, dynamic>>('checkDeviceSupport', {'port': port});
    return Map<String, dynamic>.from(result ?? {});
  }

  static Future<void> powerOn() => _methods.invokeMethod('powerOn');
  static Future<void> powerOff() => _methods.invokeMethod('powerOff');

  static Future<void> startInventory() => _methods.invokeMethod('startInventory');
  static Future<void> stopInventory() => _methods.invokeMethod('stopInventory');
  static Future<void> close() => _methods.invokeMethod('close');

  static Stream<UhfRfidTag> get inventoryStream {
    return _epcStream.receiveBroadcastStream().map((dynamic e) {
      final map = Map<String, dynamic>.from(e as Map);
      if (map.containsKey('epc')) {
        return UhfRfidTag(map['epc'] as String, (map['rssi'] as num?)?.toInt() ?? 0);
      } else if (map.containsKey('tid')) {
        return UhfRfidTag(map['tid'] as String, 0);
      }
      throw StateError('Unknown stream payload: $map');
    });
  }

  static Future<void> setStreamMode({required bool tid}) async {
    await _methods.invokeMethod('setStreamMode', {'mode': tid ? 'tid' : 'epc'});
  }

  static Future<String?> readTid({int startWord = 0, int wordCount = 6, String accessPasswordHex = '00000000'}) async {
    final tid = await _methods.invokeMethod<String>('readTid', {
      'start': startWord,
      'count': wordCount,
      'password': accessPasswordHex,
    });
    return tid;
  }

  static Future<bool> setPower(int powerDb) async {
    final success = await _methods.invokeMethod<bool>('setPower', {'power': powerDb});
    return success ?? false;
  }

  static Future<int?> getPower() async {
    final power = await _methods.invokeMethod<int>('getPower');
    return power;
  }

  static Future<bool> setWorkArea(int area) async {
    final success = await _methods.invokeMethod<bool>('setWorkArea', {'area': area});
    return success ?? false;
  }

  static Future<int?> getWorkArea() async {
    final area = await _methods.invokeMethod<int>('getWorkArea');
    return area;
  }

  static Future<Map<String, dynamic>> testReader() async {
    final result = await _methods.invokeMethod<Map<dynamic, dynamic>>('testReader');
    return Map<String, dynamic>.from(result ?? {});
  }
}
