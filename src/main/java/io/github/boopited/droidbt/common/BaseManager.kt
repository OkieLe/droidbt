package io.github.boopited.droidbt.common

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.CallSuper

abstract class BaseManager(protected val context: Context) {

    protected val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

    init {
        // We can't continue without proper Bluetooth support
        check(BluetoothUtils.checkBluetoothSupport(context, bluetoothAdapter))

        check(bluetoothAdapter.isEnabled)
    }

    @CallSuper
    open fun start() {
        // Register for system Bluetooth events
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(bluetoothReceiver, filter)
    }

    @CallSuper
    open fun stop() {
        context.unregisterReceiver(bluetoothReceiver)
    }

    open fun onBluetoothEnabled(enable: Boolean) {}

    /**
     * Listens for Bluetooth adapter events to enable/disable
     * advertising and server functionality.
     */
    private val bluetoothReceiver = object : BroadcastReceiver() {
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
}