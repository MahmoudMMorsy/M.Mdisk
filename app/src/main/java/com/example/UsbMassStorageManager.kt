package com.example

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Manages rootless USB-OTG Host connections to SATA/USB bridges and mass storage drives.
 * Implements SCSI Command Block Wrapper (CBW) and Command Status Wrapper (CSW) over Bulk-Only Transport (BOT).
 */
class UsbMassStorageManager(private val context: Context, private val onStatusUpdated: (String) -> Unit) {
    private val TAG = "UsbMassStorageManager"
    private val ACTION_USB_PERMISSION = "com.example.USB_PERMISSION"

    private val usbManager: UsbManager? = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    private var usbDevice: UsbDevice? = null
    private var usbInterface: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null
    private var connection: UsbDeviceConnection? = null

    // For command tagging
    private var tagSequence = 1

    /**
     * Broadcaster for USB permissions
     */
    val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let {
                            onStatusUpdated("Permission GRANTED for device: ${it.deviceName}")
                            connectToDevice(it)
                        }
                    } else {
                        onStatusUpdated("Permission DENIED for device: ${device?.deviceName}")
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(
            context,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    fun unregisterReceiver() {
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Unregister receiver error: ${e.message}")
        }
    }

    /**
     * Enumerates connected USB Mass Storage / SCSI Bridge devices.
     */
    fun scanDevices(): List<UsbDevice> {
        val list = mutableListOf<UsbDevice>()
        val deviceList = usbManager?.deviceList ?: emptyMap()
        for (device in deviceList.values) {
            // Exclude common non-storage classes (e.g. HID=3, Hub=9)
            if (device.deviceClass == 3 || device.deviceClass == 9) {
                continue
            }
            // Check interfaces: if interface class is HID=3, exclude
            var hasHidInterface = false
            for (i in 0 until device.interfaceCount) {
                val inter = device.getInterface(i)
                if (inter.interfaceClass == 3) {
                    hasHidInterface = true
                    break
                }
            }
            if (hasHidInterface) {
                continue
            }
            list.add(device)
        }
        return list
    }

    /**
     * Requests permission to open the USB Device
     */
    fun requestPermissionForDevice(device: UsbDevice) {
        onStatusUpdated("Requesting permission for ${device.productName ?: "USB OTG Device"}...")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
        usbManager?.requestPermission(device, permissionIntent)
    }

    /**
     * Connects to a device and opens Bulk In/Out endpoints.
     */
    fun connectToDevice(device: UsbDevice): Boolean {
        onStatusUpdated("Connecting to ${device.productName ?: "USB Device"}...")
        usbDevice = device

        // Find mass storage interface and endpoints
        var foundInterface: UsbInterface? = null
        for (i in 0 until device.interfaceCount) {
            val inter = device.getInterface(i)
            if (inter.interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) {
                foundInterface = inter
                break
            }
        }

        // Fallback: search for any interface with bulk endpoints
        if (foundInterface == null) {
            for (i in 0 until device.interfaceCount) {
                val inter = device.getInterface(i)
                var hasIn = false
                var hasOut = false
                for (j in 0 until inter.endpointCount) {
                    val ep = inter.getEndpoint(j)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.direction == UsbConstants.USB_DIR_IN) hasIn = true
                        else if (ep.direction == UsbConstants.USB_DIR_OUT) hasOut = true
                    }
                }
                if (hasIn && hasOut) {
                    foundInterface = inter
                    onStatusUpdated("Non-standard interface selected as fallback.")
                    break
                }
            }
        }

        if (foundInterface == null) {
            onStatusUpdated("Error: Mass Storage interface not found.")
            return false
        }

        usbInterface = foundInterface
        var epIn: UsbEndpoint? = null
        var epOut: UsbEndpoint? = null

        for (j in 0 until foundInterface.endpointCount) {
            val ep = foundInterface.getEndpoint(j)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_IN) {
                    epIn = ep
                } else {
                    epOut = ep
                }
            }
        }

        if (epIn == null || epOut == null) {
            onStatusUpdated("Error: Direct bulk endpoints not found.")
            return false
        }

        endpointIn = epIn
        endpointOut = epOut

        try {
            connection = usbManager?.openDevice(device)
            if (connection == null) {
                onStatusUpdated("Error: Unable to open USB connection. Permission issue or device busy.")
                return false
            }

            val claimed = connection?.claimInterface(usbInterface, true) ?: false
            if (!claimed) {
                onStatusUpdated("Error: Failed to claim Mass Storage interface.")
                connection?.close()
                connection = null
                return false
            }

            onStatusUpdated("Successfully connected! Initialized SCSI Bulk-Only Transport.")
            return true
        } catch (e: Exception) {
            onStatusUpdated("Connection Exception: ${e.message}")
            return false
        }
    }

    /**
     * Closes current USB device connection
     */
    fun disconnect() {
        connection?.let {
            it.releaseInterface(usbInterface)
            it.close()
        }
        connection = null
        usbDevice = null
        usbInterface = null
        endpointIn = null
        endpointOut = null
        onStatusUpdated("Disconnected from USB device.")
    }

    /**
     * Sends a SCSI / SAT Command via Command Block Wrapper (CBW)
     * and reads response, followed by Command Status Wrapper (CSW).
     */
    fun executeScsiCommand(
        cdb: ByteArray,
        directionIn: Boolean,
        dataBuffer: ByteArray?,
        timeoutMs: Int = 3000
    ): Boolean {
        val conn = connection ?: return false
        val epOut = endpointOut ?: return false
        val epIn = endpointIn ?: return false

        val dataLength = dataBuffer?.size ?: 0
        val tag = tagSequence++

        // 1. Construct 31-byte CBW
        val cbwBuffer = ByteBuffer.allocate(31).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            putInt(0x43425355) // dCBWSignature = "USBC"
            putInt(tag)        // dCBWTag
            putInt(dataLength) // dCBWDataTransferLength
            put((if (directionIn) 0x80 else 0x00).toByte()) // bmCBWFlags (Bit 7 is direction)
            put(0.toByte())    // bCBWLUN (Logical Unit Number)
            put(cdb.size.toByte()) // bCBWCDBLength
            put(cdb)           // CBW Command Descriptor Block
        }

        // Send CBW
        val sentCbw = conn.bulkTransfer(epOut, cbwBuffer.array(), cbwBuffer.capacity(), timeoutMs)
        if (sentCbw != cbwBuffer.capacity()) {
            onStatusUpdated("SCSI Error: CBW transfer failed ($sentCbw/31 bytes sent)")
            return false
        }

        // 2. Transfer Data (In or Out)
        if (dataBuffer != null && dataLength > 0) {
            if (directionIn) {
                val received = conn.bulkTransfer(epIn, dataBuffer, dataLength, timeoutMs)
                if (received < 0) {
                    onStatusUpdated("SCSI Error: Bulk data IN transfer failed ($received)")
                    return false
                }
            } else {
                val sent = conn.bulkTransfer(epOut, dataBuffer, dataLength, timeoutMs)
                if (sent != dataLength) {
                    onStatusUpdated("SCSI Error: Bulk data OUT transfer failed ($sent/$dataLength)")
                    return false
                }
            }
        }

        // 3. Read 13-byte CSW
        val cswBytes = ByteArray(13)
        val readCsw = conn.bulkTransfer(epIn, cswBytes, 13, timeoutMs)
        if (readCsw != 13) {
            onStatusUpdated("SCSI Error: CSW transfer failed ($readCsw/13 bytes read)")
            return false
        }

        val cswBuffer = ByteBuffer.wrap(cswBytes).apply {
            order(ByteOrder.LITTLE_ENDIAN)
        }

        val cswSignature = cswBuffer.int
        val cswTag = cswBuffer.int
        val cswResidue = cswBuffer.int
        val cswStatus = cswBuffer.get().toInt() and 0xFF

        if (cswSignature != 0x53565355) { // "USBS"
            onStatusUpdated("SCSI Error: Invalid CSW signature: 0x${Integer.toHexString(cswSignature)}")
            return false
        }

        if (cswTag != tag) {
            onStatusUpdated("SCSI Warning: CSW tag mismatch ($cswTag expected $tag)")
        }

        if (cswStatus != 0) {
            onStatusUpdated("SCSI Command Failed! CSW status code: $cswStatus (Residue: $cswResidue)")
            return false
        }

        return true
    }

    /**
     * High-level helper: Executes standard SCSI Inquiry.
     * Returns a map of Vendor, Product, and Revision.
     */
    fun sendScsiInquiry(): Map<String, String>? {
        // CDB for SCSI Inquiry: 12 00 00 00 24 00
        val cdb = byteArrayOf(0x12, 0, 0, 0, 0x24, 0)
        val response = ByteArray(36)
        
        val success = executeScsiCommand(cdb, directionIn = true, dataBuffer = response)
        if (!success) return null

        val vendor = String(response, 8, 8).trim()
        val product = String(response, 16, 16).trim()
        val revision = String(response, 32, 4).trim()

        return mapOf("Vendor" to vendor, "Product" to product, "Revision" to revision)
    }

    /**
     * High-level helper: Executes SCSI Read Capacity (10).
     * Returns total sectors and sector size in bytes.
     */
    fun sendReadCapacity10(): Pair<Long, Int>? {
        // CDB for Read Capacity: 25 00 00 00 00 00 00 00 00 00
        val cdb = byteArrayOf(0x25, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val response = ByteArray(8)

        val success = executeScsiCommand(cdb, directionIn = true, dataBuffer = response)
        if (!success) return null

        val buffer = ByteBuffer.wrap(response).apply { order(ByteOrder.BIG_ENDIAN) }
        val maxLba = buffer.int.toLong() and 0xFFFFFFFFL
        val sectorSize = buffer.int

        // Total sectors = max LBA + 1
        return Pair(maxLba + 1, sectorSize)
    }

    /**
     * High-level helper: Executes SCSI to ATA Translation (SAT) ATA PASS-THROUGH (16)
     * to send standard ATA IDENTIFY DEVICE command.
     */
    fun sendIdentifyDevice(): ByteArray? {
        // ATA PASS-THROUGH (16) Command Descriptor Block:
        // Byte 0: 0x85 (Opcode)
        // Byte 1: 0x08 (Multiple_count = 0, Protocol = PIO Data-In, Extend = 0)
        // Byte 2: 0x0e (OFFLINE=0, CK_COND=0, T_DIR=1 (In), T_LEN=2 (sector count))
        // Byte 3-13: Standard ATA register outputs/inputs
        // Byte 14: 0xEC (ATA Command: IDENTIFY DEVICE)
        // Byte 15: 0x00
        val cdb = ByteArray(16)
        cdb[0] = 0x85.toByte() // ATA PASS-THROUGH (16)
        cdb[1] = 0x08.toByte() // PIO Data-In protocol
        cdb[2] = 0x0E.toByte() // Transfer from device, sector count unit, T_LEN = 2 (Sector Count register)
        cdb[14] = 0xEC.toByte() // ATA Command: IDENTIFY DEVICE

        val identifySector = ByteArray(512)
        val success = executeScsiCommand(cdb, directionIn = true, dataBuffer = identifySector)
        return if (success) identifySector else null
    }

    /**
     * High-level helper: Executes SAT ATA PASS-THROUGH (16)
     * to send S.M.A.R.T. READ DATA command.
     */
    fun sendSmartReadData(): ByteArray? {
        // ATA PASS-THROUGH (16) for SMART READ DATA:
        // Byte 3: S.M.A.R.T. Feature (0xD0)
        // Byte 4: Sector Count (0x01)
        // Byte 6: LBA Low (0x00)
        // Byte 8: LBA Mid (0x4F)
        // Byte 10: LBA High (0xC2)
        // Byte 12: Device (0x00 or 0x40 for Master)
        // Byte 14: S.M.A.R.T. Command (0xB0)
        val cdb = ByteArray(16)
        cdb[0] = 0x85.toByte() // ATA PASS-THROUGH (16)
        cdb[1] = 0x08.toByte() // PIO Data-In
        cdb[2] = 0x0E.toByte() // T_DIR = 1, T_LEN = 2
        cdb[3] = 0xD0.toByte() // SMART Feature: Read Data
        cdb[4] = 0x01.toByte() // Sector Count = 1
        cdb[8] = 0x4F.toByte() // SMART LBA Mid register boundary
        cdb[10] = 0xC2.toByte() // SMART LBA High register boundary
        cdb[14] = 0xB0.toByte() // SMART Command

        val smartSector = ByteArray(512)
        val success = executeScsiCommand(cdb, directionIn = true, dataBuffer = smartSector)
        return if (success) smartSector else null
    }

    /**
     * High-level helper: Writes block data to a specific Logical Block Address (LBA)
     * using SCSI WRITE (10).
     */
    fun writeSectors(lba: Long, sectorCount: Int, data: ByteArray): Boolean {
        val cdb = ByteArray(10)
        cdb[0] = 0x2A.toByte() // WRITE 10
        cdb[1] = 0x00.toByte()
        
        // LBA 32-bit (Big Endian)
        cdb[2] = ((lba shr 24) and 0xFF).toByte()
        cdb[3] = ((lba shr 16) and 0xFF).toByte()
        cdb[4] = ((lba shr 8) and 0xFF).toByte()
        cdb[5] = (lba and 0xFF).toByte()
        
        cdb[6] = 0x00.toByte()
        
        // Transfer Length (number of sectors)
        cdb[7] = ((sectorCount shr 8) and 0xFF).toByte()
        cdb[8] = (sectorCount and 0xFF).toByte()
        
        cdb[9] = 0x00.toByte()
        
        return executeScsiCommand(cdb, directionIn = false, dataBuffer = data)
    }

    /**
     * High-level helper: Reads block data from a specific Logical Block Address (LBA)
     * using SCSI READ (10).
     */
    fun readSectors(lba: Long, sectorCount: Int, sectorSize: Int = 512): ByteArray? {
        val cdb = ByteArray(10)
        cdb[0] = 0x28.toByte() // READ 10
        cdb[1] = 0x00.toByte()
        
        // LBA 32-bit
        cdb[2] = ((lba shr 24) and 0xFF).toByte()
        cdb[3] = ((lba shr 16) and 0xFF).toByte()
        cdb[4] = ((lba shr 8) and 0xFF).toByte()
        cdb[5] = (lba and 0xFF).toByte()
        
        cdb[6] = 0x00.toByte()
        
        // Transfer Length
        cdb[7] = ((sectorCount shr 8) and 0xFF).toByte()
        cdb[8] = (sectorCount and 0xFF).toByte()
        
        cdb[9] = 0x00.toByte()
        
        val buffer = ByteArray(sectorCount * sectorSize)
        val success = executeScsiCommand(cdb, directionIn = true, dataBuffer = buffer)
        return if (success) buffer else null
    }
}
