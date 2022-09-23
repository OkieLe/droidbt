package io.github.boopited.droidbt.gatt

import android.bluetooth.*
import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
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

    private class WriteBuffer {
        private var characteristicData: MutableMap<UUID, MutableList<ByteArray>> = mutableMapOf()
        private var descriptorData: MutableMap<UUID, MutableList<ByteArray>> = mutableMapOf()

        fun addCharacteristic(uuid: UUID, data: ByteArray) {
            if (!characteristicData.containsKey(uuid))
                characteristicData[uuid] = mutableListOf()
            characteristicData[uuid]?.add(data)
        }

        fun addDescriptor(uuid: UUID, data: ByteArray) {
            if (!descriptorData.containsKey(uuid))
                descriptorData[uuid] = mutableListOf()
            descriptorData[uuid]?.add(data)
        }

        fun write(
            setCharacteristic: (uuid: UUID, value: ByteArray) -> Boolean,
            setDescriptor: (uuid: UUID, value: ByteArray) -> Boolean
        ): Boolean {
            val charOs = ByteArrayOutputStream()
            val charResult = characteristicData.map {
                it.value.forEach { charOs.write(it) }
                setCharacteristic(it.key, charOs.toByteArray())
            }
            charOs.close()
            characteristicData.clear()
            val desOs = ByteArrayOutputStream()
            val desResult = descriptorData.map {
                it.value.forEach { desOs.write(it) }
                setDescriptor(it.key, desOs.toByteArray())
            }
            desOs.close()
            descriptorData.clear()
            return charResult.all { it } && desResult.all { it }
        }
    }

    var logEnabled = false

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
                if (logEnabled) Log.i(TAG, "BluetoothDevice CONNECTED: $device")
                serverCallback.onDeviceConnected(device)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (logEnabled) Log.i(TAG, "BluetoothDevice DISCONNECTED: $device")
                serverCallback.onDeviceDisconnected(device)
                //Remove device from any active subscriptions
                registeredDevices.remove(device)
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            if (logEnabled) Log.d(TAG, "Service added ${service?.uuid}, status: $status")
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (logEnabled) Log.i(
                TAG,
                "${device.address} request to read characteristic ${characteristic.uuid}"
            )
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
            if (logEnabled) Log.i(
                TAG,
                "${device.address} request to write characteristic ${characteristic.uuid}"
            )
            val done = if (preparedWrite) {
                handleWriteFrame(device, characteristic.uuid, value)
                true
            } else {
                serverCallback.setCharacteristic(characteristic.uuid, value)
            }
            if (responseNeeded) {
                bluetoothGattServer?.sendResponse(
                    device,
                    requestId,
                    if (done) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE,
                    0, ByteArray(0)
                )
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            if (logEnabled) Log.i(
                TAG,
                "${device.address} request to read descriptor ${descriptor.uuid}"
            )
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
            if (logEnabled) Log.i(
                TAG,
                "${device.address} request to write descriptor ${descriptor.uuid}"
            )
            if (serverCallback.isNotification(descriptor.uuid)) {
                if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
                    if (logEnabled) Log.d(TAG, "Subscribe device to notifications: $device")
                    registeredDevices.add(device)
                } else if (Arrays.equals(
                        BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE,
                        value
                    )
                ) {
                    if (logEnabled) Log.d(TAG, "Unsubscribe device from notifications: $device")
                    registeredDevices.remove(device)
                }
            }

            val done = if (preparedWrite) {
                handleWriteFrame(device, descriptor.uuid, value)
                true
            } else {
                serverCallback.setDescriptor(descriptor.uuid, value)
            }
            if (responseNeeded) {
                bluetoothGattServer?.sendResponse(
                    device,
                    requestId,
                    if (done) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE,
                    0, ByteArray(0)
                )
            }
        }

        override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
            super.onExecuteWrite(device, requestId, execute)
            val done = if (execute) {
                writeBuffers[device.address]?.write(
                    serverCallback::setCharacteristic,
                    serverCallback::setDescriptor
                ) ?: false
            } else false
            bluetoothGattServer?.sendResponse(
                device,
                requestId,
                if (done) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE,
                0, ByteArray(0)
            )
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            if (logEnabled) Log.i(TAG, "Notification sent $status")
        }

        private var writeBuffers: MutableMap<String, WriteBuffer> = mutableMapOf()
        private fun handleWriteFrame(
            device: BluetoothDevice, uuid: UUID, data: ByteArray,
            isCharacteristic: Boolean = true
        ) {
            if (!writeBuffers.containsKey(device.address)) {
                writeBuffers[device.address] = WriteBuffer()
            }
            if (isCharacteristic) {
                writeBuffers[device.address]?.addCharacteristic(uuid, data)
            } else {
                writeBuffers[device.address]?.addDescriptor(uuid, data)
            }
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
            if (logEnabled) Log.i(TAG, "No subscribers registered")
            return
        }
        if (logEnabled) Log.i(TAG, "Sending update to ${registeredDevices.size} subscribers")
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
