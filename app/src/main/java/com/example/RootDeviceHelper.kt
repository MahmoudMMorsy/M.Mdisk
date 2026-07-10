package com.example

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

/**
 * Helper class for Android rooted environments to identify and open direct block/character
 * device files under /dev/block/ for raw disk I/O.
 */
object RootDeviceHelper {
    private const val TAG = "RootDeviceHelper"

    /**
     * Data class representing a block device found in the system.
     */
    data class BlockDevice(
        val path: String,
        val name: String,
        val sizeBytes: Long,
        val type: String, // e.g., "SATA/USB (sd)", "NVMe", "eMMC (mmcblk)"
        val isReadOnly: Boolean
    )

    /**
     * Checks if root access is available.
     */
    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("exit\n")
            os.flush()
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            Log.e(TAG, "Root check failed: ${e.message}")
            false
        }
    }

    /**
     * Scans /dev/block/ and /sys/block/ to identify all raw disk nodes available for low-level I/O.
     */
    fun enumerateBlockDevices(): List<BlockDevice> {
        val devices = mutableListOf<BlockDevice>()
        
        // Method 1: Scan /sys/block/ which lists all canonical disk devices
        val sysBlockDir = File("/sys/block")
        if (sysBlockDir.exists() && sysBlockDir.isDirectory) {
            sysBlockDir.listFiles()?.forEach { file ->
                val name = file.name
                // Filter out loopback, ram, and other virtual block devices unless requested
                if (name.startsWith("sd") || name.startsWith("nvme") || name.startsWith("mmcblk")) {
                    val devPath = if (name.startsWith("mmcblk")) {
                        "/dev/block/$name"
                    } else {
                        "/dev/block/$name"
                    }
                    
                    val sizeBytes = getDeviceSize(name)
                    val isRo = getDeviceReadOnlyStatus(name)
                    val type = when {
                        name.startsWith("sd") -> "SATA/SCSI (USB-OTG)"
                        name.startsWith("nvme") -> "PCIe NVMe SSD"
                        name.startsWith("mmcblk") -> "Internal eMMC/SD"
                        else -> "Unknown Block Device"
                    }
                    
                    devices.add(BlockDevice(devPath, name, sizeBytes, type, isRo))
                }
            }
        }

        // Method 2: Fallback to listing /dev/block/ if sysfs is restricted
        if (devices.isEmpty()) {
            val devBlockDir = File("/dev/block")
            if (devBlockDir.exists() && devBlockDir.isDirectory) {
                devBlockDir.listFiles()?.forEach { file ->
                    val name = file.name
                    if (name.startsWith("sd") || name.startsWith("nvme")) {
                        devices.add(
                            BlockDevice(
                                path = file.absolutePath,
                                name = name,
                                sizeBytes = -1L, // Unknown size
                                type = "SATA/SCSI Drive",
                                isReadOnly = false
                            )
                        )
                    }
                }
            }
        }
        
        return devices
    }

    /**
     * Reads the size of a block device in bytes from /sys/block/[devName]/size (size is in 512-byte sectors).
     */
    private fun getDeviceSize(devName: String): Long {
        return try {
            val sizeFile = File("/sys/block/$devName/size")
            if (sizeFile.exists()) {
                val sectors = sizeFile.readText().trim().toLong()
                sectors * 512L
            } else {
                -1L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading device size for $devName: ${e.message}")
            -1L
        }
    }

    /**
     * Checks if device is marked read-only in /sys/block/[devName]/ro
     */
    private fun getDeviceReadOnlyStatus(devName: String): Boolean {
        return try {
            val roFile = File("/sys/block/$devName/ro")
            if (roFile.exists()) {
                roFile.readText().trim() == "1"
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Issues low-level ATA/SCSI commands to a drive via root-level pass-through utilities
     * like sg_raw or hdparm, if available on the rooted system.
     * Returns the hex output or standard error logs.
     */
    fun executeRawAtaCommand(devicePath: String, cdbHex: String): String {
        if (!isRootAvailable()) return "ERROR: Root access is not available."
        
        return try {
            // e.g., using 'sg_raw' utility which is common on rooted Linux/Android for raw SCSI pass-through.
            // sg_raw /dev/block/sda 85 08 0e 00 d0 00 01 00 00 00 4f 00 c2 00 b0 00
            val command = "sg_raw -r 512 $devicePath $cdbHex"
            executeRootCommand(command)
        } catch (e: Exception) {
            "Exception executing command: ${e.message}"
        }
    }

    /**
     * Runs a command with root privileges and returns the output.
     */
    fun executeRootCommand(command: String): String {
        val output = StringBuilder()
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            val isReader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))

            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()

            var line: String?
            while (isReader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            while (errReader.readLine().also { line = it } != null) {
                output.append("ERR: ").append(line).append("\n")
            }
            
            process.waitFor()
        } catch (e: Exception) {
            output.append("Execution Exception: ").append(e.message)
        } finally {
            process?.destroy()
        }
        return output.toString()
    }
}
