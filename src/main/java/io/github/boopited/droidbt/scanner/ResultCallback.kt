package io.github.boopited.droidbt.scanner

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanRecord

interface ResultCallback {
    fun onScanFailed(type: Int, code: Int)
    fun onLeDeviceFound(
        device: BluetoothDevice,
        data: ScanRecord?
    ) {}
    fun onDeviceFound(
        device: BluetoothDevice,
        btClass: BluetoothClass
    ) {}
    fun onScanComplete(type: Int)
}