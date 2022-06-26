package io.github.boopited.droidbt.scanner

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.*

class LeDeviceScanner(
    context: Context,
    private val scanner: BluetoothLeScanner,
    private val resultCallback: ResultCallback,
    private val deviceFilter: DeviceFilter = DeviceFilter.default()
): Scanner {

    interface DeviceFilter {
        fun matches(scanResult: ScanResult): Boolean
        companion object {
            fun default(): DeviceFilter {
                return object : DeviceFilter {
                    override fun matches(scanResult: ScanResult): Boolean = true
                }
            }
            fun forService(serviceUUID: UUID): DeviceFilter {
                return object : DeviceFilter {
                    override fun matches(scanResult: ScanResult): Boolean {
                        return scanResult.scanRecord?.serviceUuids?.any { service ->
                            service.uuid.toString() == serviceUUID.toString()
                        } == true
                    }
                }
            }
        }
    }

    var logEnabled = false

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning: Boolean = false

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
        .apply {
            if (bluetoothManager.adapter.isOffloadedScanBatchingSupported)
                setReportDelay(0L)
        }.build()
    private val scannerCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.device?.let { device ->
                if (deviceFilter.matches(result))
                    resultCallback.onLeDeviceFound(device, result.scanRecord)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            results?.map {
                if (deviceFilter.matches(it))
                    resultCallback.onLeDeviceFound(it.device, it.scanRecord)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            isScanning = false
            resultCallback.onScanFailed(getType(), errorCode)
        }
    }

    override fun getType(): Int {
        return BluetoothDevice.DEVICE_TYPE_LE
    }

    override fun isScanning() = isScanning

    override fun startScan() {
        if (isScanning) return
        isScanning = true
        scanner.startScan(
            emptyList(),
            scanSettings,
            scannerCallback
        )
        handler.postDelayed({
            stopScan()
            resultCallback.onScanComplete(getType())
        }, TIMEOUT_FOR_STOP)
    }

    override fun stopScan() {
        if (!isScanning) return
        scanner.flushPendingScanResults(scannerCallback)
        scanner.stopScan(scannerCallback)
        isScanning = false
    }

    companion object {
        private const val TIMEOUT_FOR_STOP = 20000L
    }
}
