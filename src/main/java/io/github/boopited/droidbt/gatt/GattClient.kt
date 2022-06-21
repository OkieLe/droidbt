package io.github.boopited.droidbt.gatt

import android.bluetooth.*
import android.content.Context
import android.util.Log
import java.util.*

class GattClient(
    private val context: Context,
    private var clientCallback: GattClientCallback? = null
) {

    interface GattClientCallback {
        fun onGattConnected(gatt: BluetoothGatt) {}
        fun onGattDisconnected(gatt: BluetoothGatt) {}
        fun onServiceDiscovered(gatt: BluetoothGatt)
        fun onDataAvailable(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic)
    }

    var logEnabled = false

    private val bluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var deviceAddress: String? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectionState = STATE_DISCONNECTED

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectionState = STATE_CONNECTED
                if (logEnabled) Log.i(TAG, "Connected to GATT server.")
                clientCallback?.onGattConnected(gatt)
                // Attempts to discover services after successful connection.
                if (logEnabled) Log.i(TAG, "Attempting to start service discovery:")
                bluetoothGatt?.discoverServices()

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectionState = STATE_DISCONNECTED
                if (logEnabled) Log.i(TAG, "Disconnected from GATT server.")
                clientCallback?.onGattDisconnected(gatt)
                close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (logEnabled) Log.e(TAG, "onServicesDiscovered success: ${gatt.services}")
                clientCallback?.onServiceDiscovered(gatt)
            } else {
                if (logEnabled) Log.e(TAG, "onServicesDiscovered received: $status")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                clientCallback?.onDataAvailable(gatt, characteristic)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            clientCallback?.onDataAvailable(gatt, characteristic)
        }
    }

    fun connect(address: String): Boolean {
        // Previously connected device. Try to reconnect.
        if (deviceAddress != null && address == deviceAddress && bluetoothGatt != null) {
            if (logEnabled) Log.d(TAG, "Trying to use an existing bluetoothGatt for connection.")
            if (connectionState == STATE_CONNECTED) {
                return true
            } else if (bluetoothGatt?.connect() == true) {
                connectionState = STATE_CONNECTING
                return true
            } else {
                return false
            }
        }

        val device = bluetoothAdapter?.getRemoteDevice(address)
        if (device == null) {
            Log.w(TAG, "Device not found. Unable to connect.")
            return false
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
        if (logEnabled) Log.d(TAG, "Trying to create a new connection.")
        deviceAddress = address
        connectionState = STATE_CONNECTING
        return true
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * `BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)`
     * callback.
     */
    fun disconnect() {
        if (bluetoothGatt == null) {
            if (logEnabled) Log.w(TAG, "BluetoothAdapter not initialized")
            return
        }
        if (logEnabled) Log.d(TAG, "Disconnecting the GATT.")
        bluetoothGatt?.disconnect()
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    fun close() {
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    fun sameAs(other: BluetoothGatt): Boolean {
        return deviceAddress == other.device.address
    }

    /**
     * Request a read on a given `BluetoothGattCharacteristic`. The read result is reported
     * asynchronously through the `BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)`
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    fun readCharacteristic(characteristic: BluetoothGattCharacteristic) {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            if (logEnabled) Log.w(TAG, "BluetoothAdapter not initialized")
            return
        }
        bluetoothGatt?.readCharacteristic(characteristic)
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    fun setCharacteristicNotification(
        characteristic: BluetoothGattCharacteristic,
        descriptorId: UUID,
        enabled: Boolean
    ) {
        if (bluetoothGatt == null) {
            if (logEnabled) Log.w(TAG, "BluetoothAdapter not initialized")
            return
        }
        bluetoothGatt?.setCharacteristicNotification(characteristic, enabled)

        val descriptor = characteristic.getDescriptor(descriptorId)
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        bluetoothGatt?.writeDescriptor(descriptor)
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after `BluetoothGatt#discoverServices()` completes successfully.
     *
     * @return A `List` of supported services.
     */
    fun getSupportedGattServices(): List<BluetoothGattService> {
        return bluetoothGatt?.services.orEmpty()
    }

    companion object {
        private const val TAG = "GattClient"

        private const val STATE_DISCONNECTED = 0
        private const val STATE_CONNECTING = 1
        private const val STATE_CONNECTED = 2
    }
}
