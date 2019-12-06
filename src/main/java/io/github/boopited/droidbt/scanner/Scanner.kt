package io.github.boopited.droidbt.scanner

interface Scanner {
    fun getType(): Int
    fun isScanning(): Boolean
    fun startScan()
    fun stopScan()
}