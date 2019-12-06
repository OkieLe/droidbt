package io.github.boopited.droidbt

import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import io.github.boopited.droidbt.common.BaseManager
import java.util.*

class SlaveManager(
    context: Context,
    private val advertiseUuid: UUID
): BaseManager(context) {

    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var isAdvertising = false

    fun isAdvertising(): Boolean {
        return isAdvertising
    }

    override fun start() {
        super.start()
        if (bluetoothAdapter.isEnabled) {
            startAdvertising()
        }
    }

    override fun stop() {
        if (bluetoothAdapter.isEnabled) {
            stopAdvertising()
        }
        super.stop()
    }

    override fun onBluetoothEnabled(enable: Boolean) {
        if (!enable) {
            stopAdvertising()
            isAdvertising = false
        }
    }

    /**
     * Callback to receive information about the advertisement process.
     */
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i(TAG, "LE Advertise Started.")
            isAdvertising = true
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            Log.w(TAG, "LE Advertise Failed: $errorCode")
        }
    }

    /**
     * Begin advertising over Bluetooth that this device is connectable
     * and supports the Chat Service.
     */
    private fun startAdvertising() {
        if (bluetoothLeAdvertiser == null)
            bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser

        bluetoothLeAdvertiser?.let {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(30000)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(ParcelUuid(advertiseUuid))
                .build()

            it.startAdvertising(settings, data, advertiseCallback)
        } ?: Log.w(TAG, "Failed to create advertiser")
    }

    /**
     * Stop Bluetooth advertisements.
     */
    private fun stopAdvertising() {
        val bluetoothLeAdvertiser: BluetoothLeAdvertiser? =
            bluetoothAdapter.bluetoothLeAdvertiser
        bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            ?: Log.w(TAG, "Failed to create advertiser")
    }

    companion object {
        private const val TAG = "SlaveManager"
    }
}