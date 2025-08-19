import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'uhf_rfid_method_channel.dart';

abstract class UhfRfidPlatform extends PlatformInterface {
  /// Constructs a UhfRfidPlatform.
  UhfRfidPlatform() : super(token: _token);

  static final Object _token = Object();

  static UhfRfidPlatform _instance = MethodChannelUhfRfid();

  /// The default instance of [UhfRfidPlatform] to use.
  ///
  /// Defaults to [MethodChannelUhfRfid].
  static UhfRfidPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [UhfRfidPlatform] when
  /// they register themselves.
  static set instance(UhfRfidPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
