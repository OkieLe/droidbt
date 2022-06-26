package io.github.boopited.droidbt

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanRecord
import android.content.Context
import android.util.Log
import io.github.boopited.droidbt.common.BaseManager
import io.github.boopited.droidbt.scanner.ResultCallback
import java.util.*

class CentralManager(
    context: Context,
    private val serviceUUID: UUID,
    private var deviceCallback: DeviceCallback? = null
) : BaseManager(context), ResultCallback {

    interface DeviceCallback {
        fun onDeviceFound(device: BluetoothDevice)
        fun onComplete() {}
        fun onFailed(error: Int)
    }

    private var deviceFinder: DeviceFinder = DeviceFinder(context, this, serviceUUID)

    private val leDevices: MutableSet<BluetoothDevice> = mutableSetOf()

    override fun onLogEnabled(enable: Boolean) {
        super.onLogEnabled(enable)
        deviceFinder.logEnabled = enable
    }

    override fun start() {
        super.start()
        deviceFinder.start()
    }

    override fun stop() {
        deviceFinder.stop()
        super.stop()
    }

    override fun onLeDeviceFound(device: BluetoothDevice, data: ScanRecord?) {
        if (leDevices.add(device)) {
            deviceCallback?.onDeviceFound(device)
            if (logEnabled) Log.i(TAG, "${device.name}: (${device.address})@${device.type}")
            data?.serviceUuids?.forEach {
                Log.i(TAG, it.uuid.toString())
            }
        }
    }

    override fun onScanComplete(type: Int) {
        if (logEnabled) Log.i(TAG, "Scan complete $type")
        if (deviceFinder.isScanning()) return
        deviceCallback?.onComplete()
    }

    override fun onScanFailed(type: Int, code: Int) {
        Log.e(TAG, "Error: $type: $code")
        deviceCallback?.onFailed(code)
    }

    companion object {
        private const val TAG = "CentralManager"
    }
}
