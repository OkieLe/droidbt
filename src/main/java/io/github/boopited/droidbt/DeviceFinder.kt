package io.github.boopited.droidbt

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanRecord
import android.content.Context
import android.util.Log
import io.github.boopited.droidbt.common.BaseManager
import io.github.boopited.droidbt.scanner.ClassicDeviceScanner
import io.github.boopited.droidbt.scanner.LeDeviceScanner
import io.github.boopited.droidbt.scanner.ResultCallback
import java.util.*

class DeviceFinder(
    context: Context,
    private var deviceCallback: ResultCallback? = null,
    private val serviceUUID: UUID? = null,
    private val leOnly: Boolean = true
) : BaseManager(context), ResultCallback {

    private var btScanner: ClassicDeviceScanner = ClassicDeviceScanner(
        context,
        this
    )

    private var leScanner: LeDeviceScanner = LeDeviceScanner(
        context, bluetoothAdapter.bluetoothLeScanner,
        this,
        if (serviceUUID != null) LeDeviceScanner.DeviceFilter.forService(serviceUUID)
        else LeDeviceScanner.DeviceFilter.default()
    )

    private val classicDevices: MutableSet<BluetoothDevice> = mutableSetOf()
    private val leDevices: MutableSet<BluetoothDevice> = mutableSetOf()

    override fun onLogEnabled(enable: Boolean) {
        super.onLogEnabled(enable)
        btScanner.logEnabled = enable
        leScanner.logEnabled = enable
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
            deviceCallback?.onLeDeviceFound(device, data)
            if (logEnabled) Log.i(TAG, "${device.name}: (${device.address})@${device.type}")
            data?.serviceUuids?.forEach {
                Log.i(TAG, it.uuid.toString())
            }
        }
    }

    override fun onDeviceFound(device: BluetoothDevice, btClass: BluetoothClass?) {
        if (classicDevices.add(device)) {
            deviceCallback?.onDeviceFound(device, btClass)
            if (logEnabled) Log.i(TAG, "${device.name}: (${device.address})@${device.type}")
        }
    }

    override fun onScanComplete(type: Int) {
        if (logEnabled) Log.i(TAG, "Scan complete $type")
        if (leScanner.isScanning() || btScanner.isScanning()) return
        deviceCallback?.onScanComplete(type)
    }

    override fun onScanFailed(type: Int, code: Int) {
        Log.e(TAG, "Error: $type: $code")
        deviceCallback?.onScanFailed(type, code)
    }

    fun isScanning(): Boolean {
        return leScanner.isScanning() || (!leOnly && btScanner.isScanning())
    }

    companion object {
        private const val TAG = "DeviceFinder"
    }
}
