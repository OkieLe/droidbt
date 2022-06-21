package io.github.boopited.droidbt.common

import android.bluetooth.BluetoothDevice

interface StateCallback {
    fun onBluetoothEnabled(enable: Boolean)
    fun onDeviceConnected(device: BluetoothDevice)
    fun onDeviceDisconnected(device: BluetoothDevice)
}
