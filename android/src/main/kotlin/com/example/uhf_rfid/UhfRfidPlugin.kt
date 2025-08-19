package com.example.uhf_rfid

import android.os.Handler
import android.os.HandlerThread
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import com.magicrf.uhfreaderlib.reader.UhfReader
import com.magicrf.uhfreaderlib.reader.Tools
import com.magicrf.uhfreaderlib.reader.ReaderPort

class UhfRfidPlugin : FlutterPlugin, MethodChannel.MethodCallHandler, EventChannel.StreamHandler {
    private lateinit var methodChannel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private var reader: UhfReader? = null
    private var eventSink: EventChannel.EventSink? = null
    private var inventoryThread: HandlerThread? = null
    private var inventoryHandler: Handler? = null
    private var scanning: Boolean = false

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel = MethodChannel(binding.binaryMessenger, "uhf_rfid/methods")
        eventChannel = EventChannel(binding.binaryMessenger, "uhf_rfid/epc_stream")
        methodChannel.setMethodCallHandler(this)
        eventChannel.setStreamHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        stopInventory()
        reader?.close()
        reader = null
        methodChannel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "initialize" -> {
                val port = call.argument<String>("port") ?: "/dev/ttyHS2"
                try {
                    UhfReader.setPortPath(port)
                    reader = UhfReader.getInstance()
                    result.success(reader != null)
                } catch (e: Exception) {
                    result.error("INIT_ERROR", e.message, null)
                }
            }
            "powerOn" -> {
                try {
                    ReaderPort.uhfOpenPower()
                    result.success(true)
                } catch (e: Exception) {
                    result.error("POWER_ON_ERROR", e.message, null)
                }
            }
            "powerOff" -> {
                try {
                    ReaderPort.uhfClosePower()
                    result.success(true)
                } catch (e: Exception) {
                    result.error("POWER_OFF_ERROR", e.message, null)
                }
            }
            "startInventory" -> {
                startInventory()
                result.success(true)
            }
            "stopInventory" -> {
                stopInventory()
                result.success(true)
            }
            "close" -> {
                stopInventory()
                reader?.close()
                reader = null
                result.success(true)
            }
            else -> result.notImplemented()
        }
    }

    override fun onListen(args: Any?, events: EventChannel.EventSink?) {
        eventSink = events
    }

    override fun onCancel(args: Any?) {
        eventSink = null
    }

    private fun startInventory() {
        if (scanning) return
        if (inventoryThread == null) {
            inventoryThread = HandlerThread("uhf-inventory")
            inventoryThread!!.start()
            inventoryHandler = Handler(inventoryThread!!.looper)
        }
        scanning = true
        inventoryHandler?.post(object : Runnable {
            override fun run() {
                if (!scanning) return
                val r = reader
                if (r != null) {
                    val epcs = r.inventoryRealTime()
                    val rssis = r.rssiList
                    if (epcs != null && epcs.isNotEmpty()) {
                        var i = 0
                        for (raw in epcs) {
                            val rssi = if (i < rssis.size) rssis[i] else 0
                            i++
                            if (raw != null) {
                                val hex = Tools.Bytes2HexString(raw, raw.size)
                                eventSink?.success(mapOf("epc" to hex, "rssi" to rssi))
                            }
                        }
                    }
                }
                inventoryHandler?.postDelayed(this, 40L)
            }
        })
    }

    private fun stopInventory() {
        scanning = false
        inventoryHandler?.removeCallbacksAndMessages(null)
        inventoryThread?.quitSafely()
        inventoryThread = null
        inventoryHandler = null
    }
}

package com.example.uhf_rfid

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

/** UhfRfidPlugin */
class UhfRfidPlugin: FlutterPlugin, MethodCallHandler {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "uhf_rfid")
    channel.setMethodCallHandler(this)
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    if (call.method == "getPlatformVersion") {
      result.success("Android ${android.os.Build.VERSION.RELEASE}")
    } else {
      result.notImplemented()
    }
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }
}
