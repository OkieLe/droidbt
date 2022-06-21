package io.github.boopited.droidbt.common

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.CallSuper

abstract class BaseManager(protected val context: Context) {

    protected val bluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val stateCallbacks = mutableListOf<StateCallback>()

    init {
        // We can't continue without proper Bluetooth support
        check(BluetoothUtils.checkBluetoothSupport(context, bluetoothAdapter))

        check(bluetoothAdapter.isEnabled)
    }

    private fun addStateCallback(stateCallback: StateCallback) {
        stateCallbacks.add(stateCallback)
    }

    private fun removeStateCallback(stateCallback: StateCallback) {
        stateCallbacks.remove(stateCallback)
    }

    @CallSuper
    open fun start() {
        // Register for system Bluetooth events
        val stateFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(switchStateReceiver, stateFilter)
        val connectionFilter = IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        context.registerReceiver(connectionStateReceiver, connectionFilter)
    }

    @CallSuper
    open fun stop() {
        context.unregisterReceiver(switchStateReceiver)
        context.unregisterReceiver(connectionStateReceiver)
    }

    @CallSuper
    protected open fun onBluetoothEnabled(enable: Boolean) {
        stateCallbacks.forEach { it.onBluetoothEnabled(enable) }
    }

    /**
     * Listens for Bluetooth adapter events to enable/disable
     * advertising and server functionality.
     */
    private val switchStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)) {
                BluetoothAdapter.STATE_ON -> {
                    onBluetoothEnabled(true)
                }
                BluetoothAdapter.STATE_TURNING_OFF,
                BluetoothAdapter.STATE_TURNING_ON,
                BluetoothAdapter.STATE_OFF -> {
                    onBluetoothEnabled(false)
                }
            }
        }
    }

    @CallSuper
    protected open fun onBluetoothDisconnected(device: BluetoothDevice?) {
        device?.let {
            stateCallbacks.forEach { it.onDeviceConnected(device) }
        }
    }

    @CallSuper
    protected open fun onBluetoothConnected(device: BluetoothDevice?) {
        device?.let {
            stateCallbacks.forEach { it.onDeviceDisconnected(device) }
        }
    }

    private val connectionStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.STATE_DISCONNECTED)) {
                BluetoothAdapter.STATE_CONNECTED -> {
                    onBluetoothConnected(device)
                }
                BluetoothAdapter.STATE_DISCONNECTING -> {
                    // ignore
                }
                BluetoothAdapter.STATE_DISCONNECTED,
                BluetoothAdapter.STATE_CONNECTING -> {
                    onBluetoothDisconnected(device)
                }
            }
        }
    }
}
