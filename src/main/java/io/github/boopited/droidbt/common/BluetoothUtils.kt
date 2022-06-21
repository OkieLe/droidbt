package io.github.boopited.droidbt.common

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.RequiresApi

object BluetoothUtils {
    const val REQUEST_PERMISSION = 1001
    const val REQUEST_ENABLE_BT = 1002

    private val basicRuntimePermissions = listOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    @RequiresApi(Build.VERSION_CODES.S)
    private val newRuntimePermissionsApi31 = listOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE
    )

    private val runtimePermissions
    @SuppressLint("NewApi") get() = run {
        val currentApi = Build.VERSION.SDK_INT
        val permissions = mutableListOf<String>()
        permissions.addAll(basicRuntimePermissions)
        if (currentApi >= Build.VERSION_CODES.S) {
            permissions.addAll(newRuntimePermissionsApi31)
        }
        permissions
    }

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

    fun hasPermission(context: Context, permission: String): Boolean {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
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

    fun isBluetoothEnabled(context: Context): Boolean {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager.adapter.isEnabled
    }

    fun openBluetooth(activity: ComponentActivity, callback: (Boolean) -> Unit) {
        activity.registerForActivityResult(OpenBluetoothContract(), callback)
    }
}

private class OpenBluetoothContract: ActivityResultContract<Unit, Boolean>() {
    override fun createIntent(context: Context, input: Unit?): Intent {
        return Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
        return resultCode == Activity.RESULT_OK
    }
}
