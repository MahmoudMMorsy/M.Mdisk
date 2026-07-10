package com.example

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parses and maps raw S.M.A.R.T. attribute values from USB/SATA drives
 * converting hexadecimal responses to standardized performance metrics.
 */
object SmartAttributeParser {

    /**
     * Data class representing a parsed S.M.A.R.T. Attribute.
     */
    data class SmartAttribute(
        val id: Int,
        val hexId: String,
        val name: String,
        val description: String,
        val value: Int,         // Normalized current value (0 - 253)
        val worst: Int,         // Normalized worst recorded value
        val threshold: Int,     // Threshold value (0 - 255)
        val rawValue: Long,     // Raw attribute value (6 bytes decoded)
        val rawHex: String,     // Hexadecimal string of raw value
        val status: HealthStatus,
        val isCritical: Boolean
    )

    enum class HealthStatus {
        OK,
        WARNING,
        CRITICAL
    }

    /**
     * Map of Standard S.M.A.R.T. Attribute ID to Name, Description, and whether it's critical.
     */
    private val ATTRIBUTE_DEFS = mapOf(
        0x01 to AttributeDefinition("Raw Read Error Rate", "Frequency of raw read errors. Indicates media surface health.", true),
        0x02 to AttributeDefinition("Throughput Performance", "Overall throughput performance of the hard drive.", false),
        0x03 to AttributeDefinition("Spin-Up Time", "Average time (in milliseconds) for spindle to spin up from cold start.", false),
        0x04 to AttributeDefinition("Start/Stop Count", "Total count of spindle start/stop cycles.", false),
        0x05 to AttributeDefinition("Reallocated Sectors Count", "Number of damaged sectors removed from service and remapped. Critical indicator of drive failure.", true),
        0x07 to AttributeDefinition("Seek Error Rate", "Frequency of seek errors of magnetic heads. High rates indicate head or slider wear.", false),
        0x08 to AttributeDefinition("Seek Time Performance", "Average performance of seek operations.", false),
        0x09 to AttributeDefinition("Power-On Hours (POH)", "Total duration of time the drive has been in active power-on state.", false),
        0x0A to AttributeDefinition("Spin Retry Count", "Count of physical retries needed to spin up to rated speed.", true),
        0x0C to AttributeDefinition("Power Cycle Count", "Number of times the drive has been turned on and off.", false),
        0xB8 to AttributeDefinition("End-to-End Error", "Count of transfer parity/checksum errors inside the drive controller cache.", true),
        0xBB to AttributeDefinition("Reported Uncorrectable Errors", "The number of errors that could not be recovered using hardware ECC.", true),
        0xBC to AttributeDefinition("Command Timeout", "Number of aborted operations due to PSU or interface communication timeouts.", false),
        0xBD to AttributeDefinition("High Fly Writes", "Number of times write head detected incorrect flying height above the media.", false),
        0xBE to AttributeDefinition("Airflow Temperature", "Air temperature inside the drive enclosure (often identical to 0xC2).", false),
        0xC0 to AttributeDefinition("Power-Off Retract Count", "Number of times heads were retracted from disk surface safely.", false),
        0xC1 to AttributeDefinition("Load/Unload Cycle Count", "Count of head load/unload cycles to the landing zone.", false),
        0xC2 to AttributeDefinition("Device Temperature", "Current internal temperature of the drive (in Celsius).", false),
        0xC3 to AttributeDefinition("Hardware ECC Recovered", "Time-weighted count of error correction events applied to read data.", false),
        0xC4 to AttributeDefinition("Reallocation Event Count", "Total count of remapping operations (successful and unsuccessful).", true),
        0xC5 to AttributeDefinition("Current Pending Sector Count", "Sectors waiting to be remapped due to unstable read/write. Critical warn.", true),
        0xC6 to AttributeDefinition("Offline Uncorrectable Sector Count", "Sectors with uncorrectable read/write errors. Highly critical.", true),
        0xC7 to AttributeDefinition("UltraDMA CRC Error Count", "Number of errors in data transfers via interface cable (e.g. bad SATA cable).", false),
        0xEA to AttributeDefinition("Lifetime Writes (NAND)", "Total Terabytes Written (TBW) to Flash media (for SSDs).", false),
        0xE7 to AttributeDefinition("SSD Life Left / Wearout", "Percentage of calculated remaining flash memory endurance.", true),
        0xF0 to AttributeDefinition("Head Flying Hours", "Time spent by the positioning mechanism while actively searching.", false)
    )

    private data class AttributeDefinition(
        val name: String,
        val description: String,
        val isCritical: Boolean
    )

    /**
     * Parses a raw 512-byte response from an ATA SMART READ DATA command.
     * Extracts all standard attributes and returns a formatted list.
     * If thresholds mapping is provided (typically from separate command), it is integrated.
     */
    fun parseSmartData(smartSector: ByteArray, thresholdsSector: ByteArray? = null): List<SmartAttribute> {
        val attributes = mutableListOf<SmartAttribute>()
        if (smartSector.size < 512) {
            return attributes
        }

        // S.M.A.R.T. revision is at byte 0-1
        // Attributes start at byte 2. There can be up to 30 attributes (each is 12 bytes)
        // 2 + 30 * 12 = 362.
        for (i in 0 until 30) {
            val offset = 2 + (i * 12)
            if (offset + 12 > 512) break
            
            val id = smartSector[offset].toInt() and 0xFF
            if (id == 0) continue // Attribute slot is empty

            val flags = ((smartSector[offset + 2].toInt() and 0xFF) shl 8) or (smartSector[offset + 1].toInt() and 0xFF)
            val value = smartSector[offset + 3].toInt() and 0xFF
            val worst = smartSector[offset + 4].toInt() and 0xFF
            
            // Raw value is 6 bytes at offset 5 to 10
            var rawValue = 0L
            for (r in 0 until 6) {
                rawValue = rawValue or ((smartSector[offset + 5 + r].toLong() and 0xFF) shl (r * 8))
            }
            
            // Extract threshold if thresholdsSector is provided
            var threshold = 0
            if (thresholdsSector != null && thresholdsSector.size >= 512) {
                // Same mapping but starts at byte 2
                for (t in 0 until 30) {
                    val tOffset = 2 + (t * 12)
                    val tId = thresholdsSector[tOffset].toInt() and 0xFF
                    if (tId == id) {
                        threshold = thresholdsSector[tOffset + 1].toInt() and 0xFF
                        break
                    }
                }
            } else {
                // Provide realistic default thresholds based on industry average
                threshold = when (id) {
                    0x01 -> 51  // Read error rate
                    0x05 -> 10  // Reallocated sectors
                    0x0A -> 30  // Spin retry
                    0xC5 -> 0   // Pending sectors
                    0xC6 -> 0   // Offline uncorrectable
                    else -> 0
                }
            }

            val hexId = "0x" + String.format("%02X", id)
            val def = ATTRIBUTE_DEFS[id] ?: AttributeDefinition(
                name = "Unknown Attribute ($hexId)",
                description = "Vendor-specific attribute.",
                isCritical = false
            )

            // Determine status based on critical levels and thresholds
            val status = determineStatus(id, value, threshold, rawValue)

            // Format raw hex string
            val rawHex = "0x" + String.format("%012X", rawValue)

            attributes.add(
                SmartAttribute(
                    id = id,
                    hexId = hexId,
                    name = def.name,
                    description = def.description,
                    value = value,
                    worst = worst,
                    threshold = threshold,
                    rawValue = formatRawValueSpecial(id, rawValue),
                    rawHex = rawHex,
                    status = status,
                    isCritical = def.isCritical
                )
            )
        }

        // Sort by ID to keep it clean
        return attributes.sortedBy { it.id }
    }

    /**
     * Determines the health status of an attribute based on its values.
     */
    private fun determineStatus(id: Int, value: Int, threshold: Int, rawValue: Long): HealthStatus {
        // Check for direct failures (Value drops below threshold)
        if (value <= threshold && threshold > 0) {
            return HealthStatus.CRITICAL
        }

        // Check for critical raw thresholds
        when (id) {
            0x05 -> { // Reallocated Sector Count
                if (rawValue > 50) return HealthStatus.CRITICAL
                if (rawValue > 0) return HealthStatus.WARNING
            }
            0xC5 -> { // Current Pending Sector Count
                if (rawValue > 10) return HealthStatus.CRITICAL
                if (rawValue > 0) return HealthStatus.WARNING
            }
            0xC6 -> { // Offline Uncorrectable Sector Count
                if (rawValue > 5) return HealthStatus.CRITICAL
                if (rawValue > 0) return HealthStatus.WARNING
            }
            0xC2 -> { // Temperature
                // Temp is usually represented as degrees Celsius in the lower byte of rawValue
                val temp = rawValue and 0xFF
                if (temp >= 55) return HealthStatus.CRITICAL
                if (temp >= 45) return HealthStatus.WARNING
            }
            0xE7 -> { // SSD Wearout Indicator (Life Left)
                if (value < 10) return HealthStatus.CRITICAL
                if (value < 25) return HealthStatus.WARNING
            }
        }

        return HealthStatus.OK
    }

    /**
     * Some raw values have special encodings. E.g., temperature contains multiple sensors or min/max.
     * Here we format or normalize them so they are readable.
     */
    private fun formatRawValueSpecial(id: Int, raw: Long): Long {
        return when (id) {
            0xC2 -> raw and 0xFF // Return just the lower byte representing Celsius
            else -> raw
        }
    }

    /**
     * Generates a fully populated mock S.M.A.R.T. 512-byte buffer for simulation or testing.
     * Allows selecting different drive "health profiles".
     */
    fun generateMockSmartBuffer(profile: DriveProfile): ByteArray {
        val buffer = ByteArray(512)
        buffer[0] = 0x10 // Rev major
        buffer[1] = 0x00 // Rev minor

        val attributes = when (profile) {
            DriveProfile.HEALTHY_SATA -> listOf(
                MockAttr(1, 100, 100, 0L),       // Raw Read Error Rate
                MockAttr(3, 95, 95, 4200L),      // Spin Up Time
                MockAttr(4, 100, 100, 250L),     // Start/Stop Count
                MockAttr(5, 100, 100, 0L),       // Reallocated Sectors (0)
                MockAttr(9, 98, 98, 1420L),      // Power-On Hours
                MockAttr(12, 100, 100, 120L),    // Power Cycles
                MockAttr(194, 72, 72, 28L),      // Temp (28C)
                MockAttr(197, 100, 100, 0L),     // Current Pending
                MockAttr(198, 100, 100, 0L)      // Offline Uncorrectable
            )
            DriveProfile.FAILING_SATA -> listOf(
                MockAttr(1, 74, 62, 14502L),     // High Raw Read Error Rate
                MockAttr(3, 90, 80, 5100L),      // High Spin Up
                MockAttr(4, 88, 88, 4820L),
                MockAttr(5, 61, 61, 412L),       // 412 Reallocated Sectors! (Failure)
                MockAttr(9, 45, 45, 24800L),     // 24.8k Hours
                MockAttr(12, 85, 85, 1850L),
                MockAttr(194, 52, 41, 48L),      // Temp (48C - Warning)
                MockAttr(197, 85, 85, 14L),      // 14 Pending Sectors! (Warning/Critical)
                MockAttr(198, 89, 89, 6L)        // 6 Uncorrectable!
            )
            DriveProfile.HEALTHY_SSD -> listOf(
                MockAttr(1, 100, 100, 0L),
                MockAttr(5, 100, 100, 0L),
                MockAttr(9, 99, 99, 410L),
                MockAttr(12, 100, 100, 85L),
                MockAttr(194, 70, 70, 30L),      // Temp (30C)
                MockAttr(230, 98, 98, 98L),      // Life left (98% - Healthy)
                MockAttr(234, 100, 100, 8420L)   // Terabytes Written (8.4 TBW)
            )
            DriveProfile.FAILING_SSD -> listOf(
                MockAttr(1, 100, 100, 12L),
                MockAttr(5, 90, 90, 14L),        // Remapped Flash Blocks
                MockAttr(9, 60, 60, 18450L),
                MockAttr(12, 85, 85, 1200L),
                MockAttr(194, 55, 40, 45L),
                MockAttr(230, 8, 8, 8L),         // SSD Life left (8% - Critical Wearout!)
                MockAttr(234, 100, 100, 114250L) // 114 TBW (Very High write wear)
            )
        }

        attributes.forEachIndexed { i, attr ->
            val offset = 2 + (i * 12)
            if (offset + 12 <= 512) {
                buffer[offset] = (attr.id and 0xFF).toByte()
                buffer[offset + 1] = 0x00 // status flag LSB
                buffer[offset + 2] = 0x00 // status flag MSB
                buffer[offset + 3] = (attr.valNorm and 0xFF).toByte()
                buffer[offset + 4] = (attr.worstNorm and 0xFF).toByte()
                
                // Write 6-byte raw value in little-endian
                var tempRaw = attr.rawVal
                for (r in 0 until 6) {
                    buffer[offset + 5 + r] = (tempRaw and 0xFFL).toByte()
                    tempRaw = tempRaw shr 8
                }
                buffer[offset + 11] = 0x00 // Reserved
            }
        }

        return buffer
    }

    private data class MockAttr(val id: Int, val valNorm: Int, val worstNorm: Int, val rawVal: Long)

    enum class DriveProfile {
        HEALTHY_SATA,
        FAILING_SATA,
        HEALTHY_SSD,
        FAILING_SSD
    }
}
