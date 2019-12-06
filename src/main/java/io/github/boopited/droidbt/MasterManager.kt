package io.github.boopited.droidbt

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanRecord
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import io.github.boopited.droidbt.common.BaseManager
import io.github.boopited.droidbt.scanner.ClassicDeviceScanner
import io.github.boopited.droidbt.scanner.LeDeviceScanner
import io.github.boopited.droidbt.scanner.ResultCallback
import java.util.*

class MasterManager(
    context: Context,
    private var deviceCallback: DeviceCallback? = null,
    private val filterUUID: UUID? = null,
    private val leOnly: Boolean = true
) : BaseManager(context), ResultCallback {

    interface DeviceCallback {
        fun onDeviceFound(device: BluetoothDevice)
        fun onComplete() {}
        fun onFailed(error: Int)
    }

    private var btScanner: ClassicDeviceScanner = ClassicDeviceScanner(
        context,
        this
    )

    private var leScanner: LeDeviceScanner = LeDeviceScanner(
        bluetoothAdapter.bluetoothLeScanner,
        this,
        filterUUID
    )

    private val classicDevices: MutableSet<BluetoothDevice> = mutableSetOf()
    private val leDevices: MutableSet<BluetoothDevice> = mutableSetOf()

    override fun onBluetoothEnabled(enable: Boolean) {
    }

    override fun start() {
        super.start()
        if (!leOnly) btScanner.startScan()
        leScanner.startScan()
    }

    override fun stop() {
        if (!leOnly) btScanner.stopScan()
        leScanner.stopScan()
        super.stop()
    }

    override fun onLeDeviceFound(device: BluetoothDevice, data: ScanRecord?) {
        if (leDevices.add(device)) {
            deviceCallback?.onDeviceFound(device)
            Log.i(TAG, "${device.name}: (${device.address})@${device.type}")
            data?.serviceUuids?.forEach {
                Log.i(TAG, it.uuid.toString())
            }
        }
    }

    override fun onDeviceFound(device: BluetoothDevice, btClass: BluetoothClass) {
        if (classicDevices.add(device)) {
            deviceCallback?.onDeviceFound(device)
            Log.i(TAG, "${device.name}: (${device.address})@${device.type}")
        }
    }

    override fun onScanComplete(type: Int) {
        Log.i(TAG, "Scan complete $type")
        if (leScanner.isScanning() || btScanner.isScanning()) return
        deviceCallback?.onComplete()
    }

    override fun onScanFailed(type: Int, code: Int) {
        Log.e(TAG, "Error: $type: $code")
        deviceCallback?.onFailed(code)
    }

    companion object {
        private const val TAG = "MasterManager"
    }
}