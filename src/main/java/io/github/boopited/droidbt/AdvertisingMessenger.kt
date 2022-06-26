package io.github.boopited.droidbt

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanRecord
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import io.github.boopited.droidbt.common.BaseManager
import io.github.boopited.droidbt.common.BluetoothUtils
import io.github.boopited.droidbt.scanner.LeDeviceScanner
import io.github.boopited.droidbt.scanner.ResultCallback
import java.util.UUID

class AdvertisingMessenger(
    context: Context,
    serviceUuid: UUID,
    private val callback: MessageCallback
) : BaseManager(context), ResultCallback {
    interface MessageCallback {
        fun onMessageSent()
        fun onMessageSentFailed(errorCode: Int)
        fun onMessageReceived(data: String)
    }

    private val messageUuid = ParcelUuid(serviceUuid)
    private val leAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
    private val leScanner = LeDeviceScanner(
        context, this,
        LeDeviceScanner.DeviceFilter.forService(serviceUuid),
        monitorMode = true
    )

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            if (logEnabled) Log.i(TAG, "LE Advertise Started.")
            callback.onMessageSent()
        }

        override fun onStartFailure(errorCode: Int) {
            callback.onMessageSentFailed(errorCode)
            if (logEnabled) Log.i(TAG, "LE Advertise Failed: $errorCode")
        }
    }

    override fun onLogEnabled(enable: Boolean) {
        super.onLogEnabled(enable)
        leScanner.logEnabled = enable
    }

    fun sendMessage(message: String) {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(false)
            .setTimeout(ADVERTISE_TIME_OUT)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(messageUuid)
            .addServiceData(messageUuid, message.toByteArray())
            .build()

        if (BluetoothUtils.hasPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE)) {
            leAdvertiser.startAdvertising(settings, data, advertiseCallback)
        }
    }

    override fun start() {
        super.start()
        leScanner.startScan()
    }

    override fun stop() {
        leAdvertiser.stopAdvertising(advertiseCallback)
        leScanner.stopScan()
        super.stop()
    }

    override fun onLeDeviceFound(device: BluetoothDevice, data: ScanRecord?) {
        super.onLeDeviceFound(device, data)
        data?.getServiceData(messageUuid)?.takeIf { it.isNotEmpty() }?.let {
            callback.onMessageReceived(String(it))
        }
    }

    override fun onScanFailed(type: Int, code: Int) {
        if (logEnabled) Log.i(TAG, "Scan failed $code")
    }

    override fun onScanComplete(type: Int) {
        if (logEnabled) Log.i(TAG, "Scan complete")
    }

    companion object {
        private const val TAG = "BleP2pMessenger"
        private const val ADVERTISE_TIME_OUT = 10000
    }
}
