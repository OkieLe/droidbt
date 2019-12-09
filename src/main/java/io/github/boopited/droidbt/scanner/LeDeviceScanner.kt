package io.github.boopited.droidbt.scanner

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.os.Handler
import android.os.ParcelUuid
import java.util.*

class LeDeviceScanner(
    private val scanner: BluetoothLeScanner,
    private val resultCallback: ResultCallback,
    private val filterUUID: UUID? = null,
    private val nameFilter: String? = null
): Scanner {

    private val handler = Handler()
    private var isScanning: Boolean = false

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
        .apply {
            if (BluetoothAdapter.getDefaultAdapter().isOffloadedScanBatchingSupported)
                setReportDelay(0L)
        }.build()
    private val scannerCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.device?.let {
                if (filterUUID == null || result.scanRecord?.serviceUuids?.any { service ->
                        service.uuid.toString() == filterUUID.toString() } == true
                    || (nameFilter != null && it.name?.startsWith(nameFilter) == true))
                    resultCallback.onLeDeviceFound(it, result.scanRecord)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            results?.map {
                if (filterUUID == null || it.scanRecord?.serviceUuids?.
                        contains(ParcelUuid(filterUUID)) == true)
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
        private const val TIMEOUT_FOR_STOP = 12000L
    }
}