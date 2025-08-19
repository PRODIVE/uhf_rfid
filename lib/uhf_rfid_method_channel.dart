import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'uhf_rfid_platform_interface.dart';

/// An implementation of [UhfRfidPlatform] that uses method channels.
class MethodChannelUhfRfid extends UhfRfidPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('uhf_rfid');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
