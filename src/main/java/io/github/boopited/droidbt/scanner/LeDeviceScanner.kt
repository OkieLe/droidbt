package io.github.boopited.droidbt.scanner

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.os.Handler

class LeDeviceScanner(
    private val scanner: BluetoothLeScanner,
    private val resultCallback: ResultCallback,
    private val scanFilters: List<ScanFilter> = emptyList()
): Scanner {

    private val handler = Handler()
    private var isScanning: Boolean = false

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        .setMatchMode(ScanSettings.MATCH_MODE_STICKY)
        .apply {
            if (BluetoothAdapter.getDefaultAdapter().isOffloadedScanBatchingSupported)
                setReportDelay(0L)
        }.build()
    private val scannerCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.device?.let { resultCallback.onLeDeviceFound(it, result.scanRecord) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            results?.map {
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
        return 2
    }

    override fun isScanning() = isScanning

    override fun startScan() {
        if (isScanning) return
        isScanning = true
        scanner.startScan(
            scanFilters,
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