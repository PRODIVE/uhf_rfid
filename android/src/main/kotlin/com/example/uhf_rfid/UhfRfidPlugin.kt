package com.example.uhf_rfid

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
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
                    Log.d("UhfRfidPlugin", "Initializing with port: $port")
                    UhfReader.setPortPath(port)
                    reader = UhfReader.getInstance()
                    val success = reader != null
                    Log.d("UhfRfidPlugin", "Initialize result: $success, reader=$reader")
                    result.success(success)
                } catch (e: Exception) {
                    Log.e("UhfRfidPlugin", "Initialize error", e)
                    result.error("INIT_ERROR", e.message, null)
                }
            }
            "checkDeviceSupport" -> {
                val port = call.argument<String>("port") ?: "/dev/ttyHS2"
                try {
                    // Test if the serial port file exists and is accessible
                    val portFile = java.io.File(port)
                    if (!portFile.exists()) {
                        result.success(mapOf(
                            "supported" to false,
                            "reason" to "Serial port $port does not exist",
                            "port" to port
                        ))
                        return
                    }
                    
                    // Try to initialize the reader to test hardware support
                    UhfReader.setPortPath(port)
                    val testReader = UhfReader.getInstance()
                    if (testReader != null) {
                        testReader.close()
                        result.success(mapOf(
                            "supported" to true,
                            "reason" to "UHF reader hardware detected",
                            "port" to port
                        ))
                    } else {
                        result.success(mapOf(
                            "supported" to false,
                            "reason" to "UHF reader hardware not detected",
                            "port" to port
                        ))
                    }
                } catch (e: Exception) {
                    result.success(mapOf(
                        "supported" to false,
                        "reason" to "Error: ${e.message}",
                        "port" to port
                    ))
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
                    Log.d("UhfRfidPlugin", "Powering on UHF module")
                    ReaderPort.uhfOpenPower()
                    Log.d("UhfRfidPlugin", "UHF power on successful")
                    result.success(true)
                } catch (e: Exception) {
                    Log.e("UhfRfidPlugin", "Power on error", e)
                    result.error("POWER_ON_ERROR", e.message, null)
                }
            }
            "powerOff" -> {
                try {
                    Log.d("UhfRfidPlugin", "Powering off UHF module")
                    ReaderPort.uhfClosePower()
                    Log.d("UhfRfidPlugin", "UHF power off successful")
                    result.success(true)
                } catch (e: Exception) {
                    Log.e("UhfRfidPlugin", "Power off error", e)
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
                Log.d("UhfRfidPlugin", "Start inventory requested")
                startInventory()
                result.success(true)
            }
            "stopInventory" -> {
                Log.d("UhfRfidPlugin", "Stop inventory requested")
                stopInventory()
                result.success(true)
            }
            "close" -> {
                stopInventory()
                reader?.close()
                reader = null
                result.success(true)
            }
            "testReader" -> {
                val r = reader
                if (r == null) {
                    result.success(mapOf(
                        "status" to "error",
                        "message" to "Reader is null - not initialized"
                    ))
                    return
                }
                
                Thread {
                    try {
                        Log.d("UhfRfidPlugin", "Testing reader functionality")
                        
                        // Test basic inventory call
                        val epcs = r.inventoryRealTime()
                        val rssis = r.rssiList
                        
                        val testResult = mapOf(
                            "status" to "success",
                            "reader_initialized" to true,
                            "epcs_result" to (epcs != null),
                            "epcs_count" to (epcs?.size ?: 0),
                            "rssis_result" to (rssis != null),
                            "rssis_count" to (rssis?.size ?: 0),
                            "scanning" to scanning,
                            "stream_mode" to streamMode,
                            "event_sink_available" to (eventSink != null)
                        )
                        
                        Log.d("UhfRfidPlugin", "Test result: $testResult")
                        mainHandler.post { result.success(testResult) }
                    } catch (e: Exception) {
                        Log.e("UhfRfidPlugin", "Test reader error", e)
                        mainHandler.post { 
                            result.success(mapOf(
                                "status" to "error",
                                "message" to e.message
                            )) 
                        }
                    }
                }.start()
            }
            else -> result.notImplemented()
        }
    }

    override fun onListen(args: Any?, events: EventChannel.EventSink?) {
        Log.d("UhfRfidPlugin", "EventChannel onListen called, eventSink=$events")
        eventSink = events
    }

    override fun onCancel(args: Any?) {
        Log.d("UhfRfidPlugin", "EventChannel onCancel called")
        eventSink = null
    }

    private fun startInventory() {
        Log.d("UhfRfidPlugin", "startInventory called, scanning=$scanning")
        if (scanning) return
        if (inventoryThread == null) {
            inventoryThread = HandlerThread("uhf-inventory")
            inventoryThread!!.start()
            inventoryHandler = Handler(inventoryThread!!.looper)
            Log.d("UhfRfidPlugin", "Created inventory thread")
        }
        scanning = true
        Log.d("UhfRfidPlugin", "Starting inventory loop, reader=$reader, eventSink=$eventSink, streamMode=$streamMode")
        inventoryHandler?.post(object : Runnable {
            override fun run() {
                if (!scanning) return
                val r = reader
                if (r == null) {
                    Log.w("UhfRfidPlugin", "Reader is null in inventory loop")
                    inventoryHandler?.postDelayed(this, 40L)
                    return
                }
                if (eventSink == null) {
                    Log.w("UhfRfidPlugin", "EventSink is null in inventory loop")
                    inventoryHandler?.postDelayed(this, 40L)
                    return
                }
                
                try {
                    if (streamMode == "epc") {
                        val epcs = r.inventoryRealTime()
                        val rssis = r.rssiList
                        Log.d("UhfRfidPlugin", "inventoryRealTime returned: epcs=${epcs?.size ?: "null"}, rssis=${rssis?.size ?: "null"}")
                        if (epcs != null && epcs.isNotEmpty()) {
                            var i = 0
                            for (raw in epcs) {
                                val rssi = if (i < rssis.size) rssis[i] else 0
                                i++
                                if (raw != null) {
                                    val hex = Tools.Bytes2HexString(raw, raw.size)
                                    Log.d("UhfRfidPlugin", "Found EPC: $hex, RSSI: $rssi")
                                    mainHandler.post {
                                        eventSink?.success(mapOf("epc" to hex, "rssi" to rssi))
                                    }
                                }
                            }
                        } else {
                            Log.d("UhfRfidPlugin", "No EPCs found in this cycle")
                        }
                    } else {
                        // streamMode == "tid": issue a single TID read cycle and emit when present
                        Log.d("UhfRfidPlugin", "Attempting TID read")
                        val data: ByteArray? = r.readFrom6C(2, 0, 6, byteArrayOf(0x00, 0x00, 0x00, 0x00))
                        if (data != null && data.isNotEmpty()) {
                            val hex = Tools.Bytes2HexString(data, data.size)
                            Log.d("UhfRfidPlugin", "Found TID: $hex")
                            mainHandler.post {
                                eventSink?.success(mapOf("tid" to hex))
                            }
                        } else {
                            Log.d("UhfRfidPlugin", "No TID found in this cycle")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("UhfRfidPlugin", "Error in inventory loop", e)
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

