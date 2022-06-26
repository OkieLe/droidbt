package io.github.boopited.droidbt.scanner

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper

class ClassicDeviceScanner(
    private val context: Context,
    private val callback: ResultCallback,
    private val deviceFilter: DeviceFilter = DeviceFilter.default()
) : Scanner {

    interface DeviceFilter {
        fun matches(device: BluetoothDevice, btClass: BluetoothClass?): Boolean

        companion object {
            fun default(): DeviceFilter {
                return object : DeviceFilter {
                    override fun matches(
                        device: BluetoothDevice,
                        btClass: BluetoothClass?
                    ): Boolean = true
                }
            }
        }
    }

    var logEnabled: Boolean = false

    private val bluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning: Boolean = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent
                        .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice?
                    val btClass = intent
                        .getParcelableExtra(BluetoothDevice.EXTRA_CLASS) as BluetoothClass?
                    device?.takeIf {
                        deviceFilter.matches(it, btClass)
                    }?.let {
                        callback.onDeviceFound(it, btClass)
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    isScanning = true
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    isScanning = false
                    ignoreEvents()
                }
                else -> {}
            }
        }
    }

    private fun listenEvents() {
        val intentFilter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_FOUND)
        }
        context.registerReceiver(receiver, intentFilter)
    }

    private fun ignoreEvents() {
        context.unregisterReceiver(receiver)
    }

    override fun getType(): Int {
        return BluetoothDevice.DEVICE_TYPE_CLASSIC
    }

    override fun isScanning() = isScanning

    override fun startScan() {
        if (isScanning) return
        listenEvents()
        bluetoothAdapter.startDiscovery()
        handler.postDelayed({
            stopScan()
            callback.onScanComplete(getType())
        }, SCAN_TIMEOUT)
    }

    override fun stopScan() {
        if (!isScanning) return
        bluetoothAdapter.cancelDiscovery()
    }

    companion object {
        private const val SCAN_TIMEOUT: Long = 15000
    }
}
