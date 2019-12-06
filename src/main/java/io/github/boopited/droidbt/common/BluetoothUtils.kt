package io.github.boopited.droidbt.common

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

object BluetoothUtils {
    const val REQUEST_PERMISSION = 1001
    const val REQUEST_ENABLE_BT = 1002

    private val runtimePermissions = listOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    /**
     * Verify the level of Bluetooth support provided by the hardware.
     * @param bluetoothAdapter System [BluetoothAdapter].
     * @return true if Bluetooth is properly supported, false otherwise.
     */
    fun checkBluetoothSupport(context: Context, bluetoothAdapter: BluetoothAdapter?): Boolean {
        if (bluetoothAdapter == null) {
            Log.w("BluetoothUtils", "Bluetooth is not supported")
            return false
        }

        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w("BluetoothUtils", "Bluetooth LE is not supported")
            return false
        }

        return true
    }

    fun hasPermissions(context: Context): Boolean {
        return !runtimePermissions.any {
            context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
    }

    fun permissionsToAsk(context: Context): Array<String> {
        return runtimePermissions.filter {
            context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }

    fun isBluetoothEnabled(): Boolean {
        return BluetoothAdapter.getDefaultAdapter().isEnabled
    }

    fun openBluetooth(context: Activity, code: Int) {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        context.startActivityForResult(enableBtIntent, code)
    }
}