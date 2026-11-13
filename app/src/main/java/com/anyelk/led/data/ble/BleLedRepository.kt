package com.anyelk.led.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar

data class ScannedDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val isLikelyLed: Boolean
)

sealed class LedConnectionState {
    data object Idle : LedConnectionState()
    data object Scanning : LedConnectionState()
    data class Connecting(val address: String, val name: String?) : LedConnectionState()
    data class Connected(val address: String, val name: String?) : LedConnectionState()
    data class Disconnected(val address: String?, val reason: String? = null) : LedConnectionState()
    data class Error(val message: String) : LedConnectionState()
}

/**
 * Scans, connects and talks to an ELK-BLEDOB style controller.
 *
 * Commands are split into two lanes:
 *  - control lane (power, brightness, pixel order): buffered, sent promptly;
 *  - color lane (RGB): conflated (only the newest value survives) and
 *    throttled to ~30 FPS so a music visualizer cannot flood the GATT queue.
 */
class BleLedRepository(
    context: Context,
    private val settings: com.anyelk.led.data.settings.SettingsRepository
) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Strip channel order; synced to the controller as pin-seq on connect. */
    @Volatile
    private var pixelOrder: PixelOrder = PixelOrder.GRB

    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter

    private val _devices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val devices: StateFlow<List<ScannedDevice>> = _devices.asStateFlow()

    private val _connection = MutableStateFlow<LedConnectionState>(LedConnectionState.Idle)
    val connection: StateFlow<LedConnectionState> = _connection.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val controlChannel = Channel<ByteArray>(Channel.BUFFERED)
    private val colorChannel = Channel<ByteArray>(Channel.CONFLATED)

    private val writeMutex = Mutex()
    private var gatt: BluetoothGatt? = null
    private var targetCharacteristic: BluetoothGattCharacteristic? = null
    private var connectedAddress: String? = null

    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null

    private var senderJob: kotlinx.coroutines.Job? = null
    private var keepAliveJob: kotlinx.coroutines.Job? = null

    private val writeSignal = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            settings.settings.collect { pixelOrder = it.pixelOrder }
        }
    }

    // ------------------------------------------------------------------ scan

    @SuppressLint("MissingPermission")
    fun startScan(durationMs: Long = 10_000L) {
        val sc = adapter?.bluetoothLeScanner ?: run {
            _connection.value = LedConnectionState.Error("Bluetooth is unavailable")
            return
        }
        if (!hasScanPermission()) {
            _connection.value = LedConnectionState.Error("Scan permission not granted")
            return
        }

        stopScan()
        _devices.value = emptyList()
        _connection.value = LedConnectionState.Scanning

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val name = result.device.name ?: result.scanRecord?.deviceName
                if (name.isNullOrBlank()) return
                val entry = ScannedDevice(
                    address = device.address,
                    name = name,
                    rssi = result.rssi,
                    isLikelyLed = BleProtocol.isKnownDeviceName(name)
                )
                val current = _devices.value
                val index = current.indexOfFirst { it.address == entry.address }
                val next = if (index >= 0) {
                    current.toMutableList().also {
                        // Keep the strongest reading, prefer LED-like names.
                        val old = it[index]
                        if (entry.rssi >= old.rssi || entry.isLikelyLed) it[index] = entry
                    }
                } else {
                    (current + entry).sortedWith(
                        compareByDescending<ScannedDevice> { it.isLikelyLed }
                            .thenByDescending { it.rssi }
                    )
                }
                _devices.value = next
            }

            override fun onScanFailed(errorCode: Int) {
                _connection.value = LedConnectionState.Error("Scan failed: $errorCode")
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            sc.startScan(null, settings, callback)
            scanner = sc
            scanCallback = callback
            scope.launch {
                delay(durationMs)
                stopScan()
            }
        } catch (e: SecurityException) {
            _connection.value = LedConnectionState.Error(e.message ?: "Scan failed")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val sc = scanner ?: return
        val cb = scanCallback
        try {
            if (cb != null) sc.stopScan(cb)
        } catch (_: Exception) {
            // Scanner may already be stopped.
        }
        scanner = null
        scanCallback = null
        if (_connection.value == LedConnectionState.Scanning) {
            _connection.value = LedConnectionState.Idle
        }
    }

    // --------------------------------------------------------------- connect

    @SuppressLint("MissingPermission")
    fun connect(address: String, name: String? = null) {
        if (!hasConnectPermission()) {
            _connection.value = LedConnectionState.Error("Connect permission not granted")
            return
        }
        val dev = adapter?.getRemoteDevice(address) ?: run {
            _connection.value = LedConnectionState.Error("Device not found")
            return
        }

        stopScan()
        // Drop any previous session before starting a fresh one.
        disconnect()
        _connection.value = LedConnectionState.Connecting(address, name)

        try {
            val newGatt = dev.connectGatt(
                appContext,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
            gatt = newGatt
            connectedAddress = address
        } catch (e: SecurityException) {
            _connection.value = LedConnectionState.Error(e.message ?: "Connect failed")
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        keepAliveJob?.cancel()
        senderJob?.cancel()
        senderJob = null
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
        targetCharacteristic = null
        if (connectedAddress != null) {
            _connection.value = LedConnectionState.Disconnected(connectedAddress)
        } else {
            _connection.value = LedConnectionState.Idle
        }
        connectedAddress = null
    }

    /** Restores a session to the last known device (auto-connect). */
    fun connectLast(address: String?, name: String?) {
        if (address.isNullOrBlank()) return
        val state = _connection.value
        if (state is LedConnectionState.Connected && state.address == address) return
        connect(address, name)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    _connection.value = LedConnectionState.Connecting(
                        gatt.device.address,
                        gatt.device.name
                    )
                    gatt.discoverServices()
                }
                BluetoothGatt.STATE_DISCONNECTED -> {
                    keepAliveJob?.cancel()
                    senderJob?.cancel()
                    senderJob = null
                    targetCharacteristic = null
                    val address = gatt.device.address
                    try {
                        gatt.close()
                    } catch (_: Exception) {
                    }
                    if (this@BleLedRepository.gatt === gatt) {
                        this@BleLedRepository.gatt = null
                    }
                    _connection.value = LedConnectionState.Disconnected(
                        address,
                        if (status != BluetoothGatt.GATT_SUCCESS) "Link lost ($status)" else null
                    )
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connection.value = LedConnectionState.Error("Service discovery failed: $status")
                return
            }
            val service: BluetoothGattService? =
                gatt.getService(BleProtocol.SERVICE_UUID)
            val characteristic = service?.getCharacteristic(BleProtocol.CHARACTERISTIC_UUID)
            if (characteristic == null) {
                _connection.value =
                    LedConnectionState.Error("ELK control characteristic (FFF3) not found")
                return
            }

            targetCharacteristic = characteristic
            _connection.value = LedConnectionState.Connected(
                gatt.device.address,
                gatt.device.name ?: gatt.device.address
            )

            try {
                gatt.requestMtu(247)
            } catch (_: Exception) {
            }

            startSender()
            syncTime(gatt)
            // Push the pin sequence [pinR, pinG, pinB] so the controller routes
            // the logical RGB packet onto the strip's physical channels. Sent on
            // every connect for a deterministic baseline (undoes any drift).
            sendControl(BleProtocol.pixelOrder(pixelOrder))
            startKeepAlive()
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            writeSignal.trySend(Unit)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            // MTU is informational only.
        }
    }

    // -------------------------------------------------------------- sending

    private fun startSender() {
        if (senderJob?.isActive == true) return
        senderJob = scope.launch {
            var lastColorAt = 0L
            while (isActive) {
                // Control lane has priority.
                val control = controlChannel.tryReceive().getOrNull()
                if (control != null) {
                    writeBytes(control)
                    continue
                }

                var pendingColor: ByteArray? = null
                select<Unit> {
                    controlChannel.onReceive { pkt ->
                        writeBytes(pkt)
                    }
                    colorChannel.onReceive { pkt ->
                        pendingColor = pkt
                    }
                }

                val color = pendingColor
                if (color != null) {
                    val elapsed = SystemClock.elapsedRealtime() - lastColorAt
                    if (elapsed < MIN_COLOR_INTERVAL_MS) {
                        delay(MIN_COLOR_INTERVAL_MS - elapsed)
                    }
                    // The color lane is conflated: prefer whatever is newest.
                    val latest = colorChannel.tryReceive().getOrNull() ?: color
                    writeBytes(latest)
                    lastColorAt = SystemClock.elapsedRealtime()
                }
            }
        }
    }

    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (isActive) {
                delay(KEEP_ALIVE_INTERVAL_MS)
                syncTime(gatt)
            }
        }
    }

    private fun syncTime(target: BluetoothGatt?) {
        val cal = Calendar.getInstance()
        sendControl(
            BleProtocol.timeSync(
                hour = cal.get(Calendar.HOUR_OF_DAY),
                minute = cal.get(Calendar.MINUTE),
                second = cal.get(Calendar.SECOND),
                dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // Sun=1..Sat=7
            )
        )
    }

    /** Buffered, prompt commands (power, brightness, pixel order, effects). */
    fun sendControl(packet: ByteArray) {
        controlChannel.trySend(packet)
        startSenderIfConnected()
    }

    /** High-rate RGB updates: conflated + throttled inside the sender loop. */
    fun sendColor(red: Int, green: Int, blue: Int) {
        // Logical [R, G, B] always — channel routing is the pin-seq's job.
        colorChannel.trySend(BleProtocol.color(red, green, blue))
        startSenderIfConnected()
    }

    fun sendPixelOrder(order: PixelOrder) {
        sendControl(BleProtocol.pixelOrder(order))
    }

    private fun startSenderIfConnected() {
        if (_connection.value is LedConnectionState.Connected && senderJob?.isActive != true) {
            startSender()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeBytes(data: ByteArray) {
        val g = gatt ?: return
        val ch = targetCharacteristic ?: return
        if (!hasConnectPermission()) return

        writeMutex.withLock {
            // Drop stale completion signals from previous writes.
            while (writeSignal.tryReceive().isSuccess) {
                // drain
            }
            val result = withTimeoutOrNull(WRITE_TIMEOUT_MS) {
                val submitted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeCharacteristic(
                        ch, data, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    )
                } else {
                    @Suppress("DEPRECATION")
                    run {
                        ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        ch.value = data
                        g.writeCharacteristic(ch)
                    }
                }
                val accepted = when (submitted) {
                    is Boolean -> submitted
                    is Int -> submitted == BluetoothStatusCodes.SUCCESS
                    else -> true
                }
                if (!accepted) return@withTimeoutOrNull false
                // Wait until the stack reports the write; on timeout proceed so a
                // controller that never acks WRITE_TYPE_NO_RESPONSE cannot stall us.
                writeSignal.receive()
                true
            }
            if (result == false) {
                // Transient failure — the next scheduled update retries.
            }
        }
    }

    // ------------------------------------------------------------ permission

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        /** Minimum gap between RGB packets (~30 FPS ceiling on the air). */
        const val MIN_COLOR_INTERVAL_MS = 33L
        private const val WRITE_TIMEOUT_MS = 400L
        private const val KEEP_ALIVE_INTERVAL_MS = 20_000L
    }
}
