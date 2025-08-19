import 'package:flutter_test/flutter_test.dart';
import 'package:uhf_rfid/uhf_rfid.dart';
import 'package:uhf_rfid/uhf_rfid_platform_interface.dart';
import 'package:uhf_rfid/uhf_rfid_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockUhfRfidPlatform
    with MockPlatformInterfaceMixin
    implements UhfRfidPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final UhfRfidPlatform initialPlatform = UhfRfidPlatform.instance;

  test('$MethodChannelUhfRfid is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelUhfRfid>());
  });

  test('getPlatformVersion', () async {
    UhfRfid uhfRfidPlugin = UhfRfid();
    MockUhfRfidPlatform fakePlatform = MockUhfRfidPlatform();
    UhfRfidPlatform.instance = fakePlatform;

    expect(await uhfRfidPlugin.getPlatformVersion(), '42');
  });
}
