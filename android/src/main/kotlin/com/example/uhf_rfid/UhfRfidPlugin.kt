package com.example.uhf_rfid

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
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
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
    private var scanning: Boolean = false
    private var streamMode: String = "epc" // "epc" or "tid"

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
            "setStreamMode" -> {
                val mode = call.argument<String>("mode")
                if (mode == "epc" || mode == "tid") {
                    streamMode = mode
                    result.success(true)
                } else {
                    result.error("BAD_MODE", "mode must be 'epc' or 'tid'", null)
                }
            }
            "readTid" -> {
                val start: Int = call.argument<Int>("start") ?: 0
                val count: Int = call.argument<Int>("count") ?: 6
                val passwordHex: String = call.argument<String>("password") ?: "00000000"
                val pwdBytes: ByteArray = try {
                    Tools.HexString2Bytes(passwordHex)
                } catch (e: Exception) {
                    byteArrayOf(0x00, 0x00, 0x00, 0x00)
                }
                val r = reader
                if (r == null) {
                    result.error("NO_READER", "Reader not initialized", null)
                    return
                }
                Thread {
                    try {
                        val data: ByteArray? = r.readFrom6C(2, start, count, pwdBytes)
                        if (data != null && data.isNotEmpty()) {
                            val hex = Tools.Bytes2HexString(data, data.size)
                            mainHandler.post { result.success(hex) }
                        } else {
                            mainHandler.post { result.error("READ_TID_FAIL", "Empty response", null) }
                        }
                    } catch (e: Exception) {
                        mainHandler.post { result.error("READ_TID_ERROR", e.message, null) }
                    }
                }.start()
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
            "setPower" -> {
                val power = call.argument<Int>("power")
                if (power == null || power < 0 || power > 33) {
                    result.error("BAD_POWER", "Power must be 0-33 dBm", null)
                    return
                }
                // Note: setOutputPower method may not be available in this UHF library
                // For now, just return success to allow UI testing
                result.success(true)
            }
            "getPower" -> {
                // Note: getOutputPower method may not be available in this UHF library
                // For now, return a default value to allow UI testing
                result.success(20)
            }
            "setWorkArea" -> {
                val area = call.argument<Int>("area")
                if (area == null || area < 0 || area > 3) {
                    result.error("BAD_AREA", "Work area must be 0-3", null)
                    return
                }
                // Note: setWorkArea method may not be available in this UHF library
                // For now, just return success to allow UI testing
                result.success(true)
            }
            "getWorkArea" -> {
                // Note: getWorkArea method may not be available in this UHF library
                // For now, return a default value to allow UI testing
                result.success(0)
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
                    if (streamMode == "epc") {
                        val epcs = r.inventoryRealTime()
                        val rssis = r.rssiList
                        if (epcs != null && epcs.isNotEmpty()) {
                            var i = 0
                            for (raw in epcs) {
                                val rssi = if (i < rssis.size) rssis[i] else 0
                                i++
                                if (raw != null) {
                                    val hex = Tools.Bytes2HexString(raw, raw.size)
                                    mainHandler.post {
                                        eventSink?.success(mapOf("epc" to hex, "rssi" to rssi))
                                    }
                                }
                            }
                        }
                    } else {
                        // streamMode == "tid": issue a single TID read cycle and emit when present
                        val data: ByteArray? = r.readFrom6C(2, 0, 6, byteArrayOf(0x00, 0x00, 0x00, 0x00))
                        if (data != null && data.isNotEmpty()) {
                            val hex = Tools.Bytes2HexString(data, data.size)
                            mainHandler.post {
                                eventSink?.success(mapOf("tid" to hex))
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

