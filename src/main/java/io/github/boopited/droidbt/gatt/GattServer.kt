package io.github.boopited.droidbt.gatt

import android.bluetooth.*
import android.content.Context
import android.util.Log
import java.util.*

class GattServer(
    private val context: Context,
    private val serverCallback: GattServerCallback
) {

    interface GattServerCallback {
        fun onDeviceConnected(device: BluetoothDevice)
        fun onDeviceDisconnected(device: BluetoothDevice)
        fun isNotification(uuid: UUID): Boolean
        fun getCharacteristic(uuid: UUID): ByteArray?
        fun setCharacteristic(uuid: UUID, value: ByteArray): Boolean
        fun getDescriptor(uuid: UUID): ByteArray?
        fun setDescriptor(uuid: UUID, value: ByteArray): Boolean
    }

    /* Bluetooth API */
    private var bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var bluetoothGattServer: BluetoothGattServer? = null

    /* Collection of notification subscribers */
    private val registeredDevices = mutableSetOf<BluetoothDevice>()

    /**
     * Callback to handle incoming requests to the GATT server.
     * All read/write requests for characteristics and descriptors are handled here.
     */
    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "BluetoothDevice CONNECTED: $device")
                serverCallback.onDeviceConnected(device)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "BluetoothDevice DISCONNECTED: $device")
                serverCallback.onDeviceDisconnected(device)
                //Remove device from any active subscriptions
                registeredDevices.remove(device)
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = serverCallback.getCharacteristic(characteristic.uuid)
            bluetoothGattServer?.sendResponse(device,
                requestId,
                data?.let { BluetoothGatt.GATT_SUCCESS } ?: BluetoothGatt.GATT_FAILURE,
                0,
                data)
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {

            val done = serverCallback.setCharacteristic(characteristic.uuid, value)
            if (responseNeeded) {
                bluetoothGattServer?.sendResponse(device,
                    requestId,
                    if (done) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE,
                    0, null)
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            val data = if (serverCallback.isNotification(descriptor.uuid)) {
                if (registeredDevices.contains(device)) {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                }
            } else serverCallback.getDescriptor(descriptor.uuid)
            bluetoothGattServer?.sendResponse(device,
                requestId,
                data?.let { BluetoothGatt.GATT_SUCCESS } ?: BluetoothGatt.GATT_FAILURE,
                0,
                data)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (serverCallback.isNotification(descriptor.uuid)) {
                if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(TAG, "Subscribe device to notifications: $device")
                    registeredDevices.add(device)
                } else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(TAG, "Unsubscribe device from notifications: $device")
                    registeredDevices.remove(device)
                }
            }

            val done = serverCallback.setDescriptor(descriptor.uuid, value)
            if (responseNeeded) {
                bluetoothGattServer?.sendResponse(device,
                    requestId,
                    if (done) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE,
                    0, null)
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            Log.i(TAG, "Notification sent $status")
        }
    }

    init {
        bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback)
    }

    fun startService(service: BluetoothGattService) {
        bluetoothGattServer?.addService(service)
            ?: Log.w(TAG, "Unable to create GATT server")
    }

    fun stopService(uuid: UUID) {
        bluetoothGattServer?.getService(uuid)?.let {
            bluetoothGattServer?.removeService(it)
        }
    }

    fun shutdown() {
        bluetoothGattServer?.clearServices()
        bluetoothGattServer?.close()
    }

    fun notifyDevices(service: UUID, characteristic: UUID, value: ByteArray) {
        if (registeredDevices.isEmpty()) {
            Log.i(TAG, "No subscribers registered")
            return
        }
        Log.i(TAG, "Sending update to ${registeredDevices.size} subscribers")
        for (device in registeredDevices) {
            val data = bluetoothGattServer
                ?.getService(service)
                ?.getCharacteristic(characteristic)
            data?.value = value
            bluetoothGattServer?.notifyCharacteristicChanged(device, data, false)
        }
    }

    companion object {
        private const val TAG = "GattServer"
    }
}