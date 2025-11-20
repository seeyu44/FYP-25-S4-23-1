package com.example.fyp_25_s4_23.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs

data class CompatibilityResult(
    val isCompatible: Boolean,
    val issues: List<String>,
    val warnings: List<String>
)

data class DeviceSpecs(
    val androidVersion: String,
    val sdkVersion: Int,
    val availableRamMB: Long,
    val totalRamMB: Long,
    val availableStorageGB: Long,
    val cpuCores: Int,
    val deviceModel: String
)

class DeviceCompatibilityChecker(private val context: Context) {

    companion object {
        private const val MIN_SDK_VERSION = 26 // Android 8.0 (Oreo)
        private const val RECOMMENDED_SDK_VERSION = 29 // Android 10
        private const val MIN_RAM_MB = 2048 // 2GB
        private const val RECOMMENDED_RAM_MB = 4096 // 4GB
        private const val MIN_STORAGE_GB = 1 // 1GB free space
        private const val MIN_CPU_CORES = 4
    }

    fun checkCompatibility(): CompatibilityResult {
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val specs = getDeviceSpecs()

        // Check Android version
        if (specs.sdkVersion < MIN_SDK_VERSION) {
            issues.add("Android version too old. Requires Android 8.0 or higher (Current: ${specs.androidVersion})")
        } else if (specs.sdkVersion < RECOMMENDED_SDK_VERSION) {
            warnings.add("Android ${specs.androidVersion} detected. Android 10+ recommended for best performance")
        }

        // Check RAM
        if (specs.totalRamMB < MIN_RAM_MB) {
            issues.add("Insufficient RAM. Requires at least 2GB (Found: ${specs.totalRamMB}MB)")
        } else if (specs.totalRamMB < RECOMMENDED_RAM_MB) {
            warnings.add("Low RAM detected (${specs.totalRamMB}MB). 4GB+ recommended for optimal performance")
        }

        // Check available storage
        if (specs.availableStorageGB < MIN_STORAGE_GB) {
            issues.add("Insufficient storage space. Need at least ${MIN_STORAGE_GB}GB free (Available: ${specs.availableStorageGB}GB)")
        }

        // Check CPU cores
        if (specs.cpuCores < MIN_CPU_CORES) {
            warnings.add("Low CPU core count (${specs.cpuCores} cores). May experience slower performance")
        }

        return CompatibilityResult(
            isCompatible = issues.isEmpty(),
            issues = issues,
            warnings = warnings
        )
    }

    fun getDeviceSpecs(): DeviceSpecs {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        return DeviceSpecs(
            androidVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            availableRamMB = memoryInfo.availMem / (1024 * 1024),
            totalRamMB = memoryInfo.totalMem / (1024 * 1024),
            availableStorageGB = getAvailableStorageGB(),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
    }

    private fun getAvailableStorageGB(): Long {
        val stat = StatFs(Environment.getDataDirectory().path)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        return availableBytes / (1024 * 1024 * 1024)
    }
}