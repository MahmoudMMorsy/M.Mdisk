package main

import (
	"fmt"
)

// HealthStatus represents S.M.A.R.T. threshold limits
type HealthStatus string

const (
	StatusOK       HealthStatus = "OK"
	StatusWarning  HealthStatus = "WARNING"
	StatusCritical HealthStatus = "CRITICAL"
)

// SmartAttribute represents a single S.M.A.R.T. register value
type SmartAttribute struct {
	ID          int
	HexID       string
	Name        string
	Value       int
	Worst       int
	Threshold   int
	RawValue    int64
	RawHex      string
	Status      HealthStatus
	IsCritical  bool
	Description string
}

// SmartAlert represents a triggered alert
type SmartAlert struct {
	ID            int
	AttributeName string
	CurrentValue  int64
	Threshold     int64
	Severity      string // "WARNING", "CRITICAL"
	Message       string
}

// SmartProfile represents preset drive states
type SmartProfile string

const (
	HealthyHDD SmartProfile = "HEALTHY_HDD"
	FailingHDD SmartProfile = "FAILING_HDD"
	HealthySSD SmartProfile = "HEALTHY_SSD"
	FailingSSD SmartProfile = "FAILING_SSD"
)

// GetMockSmartAttributes generates low-level S.M.A.R.T. values matching the specified health profile
func GetMockSmartAttributes(profile SmartProfile) []SmartAttribute {
	var attrs []SmartAttribute

	switch profile {
	case HealthyHDD:
		attrs = []SmartAttribute{
			{0x01, "0x01", "Raw Read Error Rate", 100, 100, 51, 0, "0x000000000000", StatusOK, true, "Frequency of raw read errors from the platter surface."},
			{0x03, "0x03", "Spin-Up Time", 92, 92, 21, 3810, "0x000000000EE2", StatusOK, false, "Average time in milliseconds to stabilize spindle rotation."},
			{0x04, "0x04", "Start/Stop Count", 98, 98, 0, 1420, "0x00000000058C", StatusOK, false, "Spindle motor start/stop cycle log count."},
			{0x05, "0x05", "Reallocated Sectors Count", 200, 200, 140, 0, "0x000000000000", StatusOK, true, "Count of remapped physical sectors relocated to reserve cylinders."},
			{0x07, "0x07", "Seek Error Rate", 100, 100, 51, 0, "0x000000000000", StatusOK, false, "Frequency of physical arm positioning positioning errors."},
			{0x09, "0x09", "Power-On Hours", 89, 89, 0, 9420, "0x0000000024CC", StatusOK, false, "Total cumulative operating hours since manufacture."},
			{0x0C, "0x0C", "Power Cycle Count", 99, 99, 0, 312, "0x000000000138", StatusOK, false, "Log count of full electrical power boot cycles."},
			{0xC2, "0xC2", "Temperature", 38, 38, 0, 38, "0x000000000026", StatusOK, false, "Current internal sensor temperature in Celsius."},
			{0xC5, "0xC5", "Current Pending Sector Count", 200, 200, 0, 0, "0x000000000000", StatusOK, true, "Count of unstable, unreadable sectors awaiting hardware reallocation."},
			{0xC6, "0xC6", "Offline Uncorrectable Sector Count", 200, 200, 0, 0, "0x000000000000", StatusOK, true, "Permanent, uncorrectable read/write failures on the magnetic surface."},
		}
	case FailingHDD:
		attrs = []SmartAttribute{
			{0x01, "0x01", "Raw Read Error Rate", 42, 38, 51, 8824, "0x000000002278", StatusCritical, true, "Frequency of raw read errors from the platter surface."},
			{0x03, "0x03", "Spin-Up Time", 84, 84, 21, 5910, "0x000000001716", StatusOK, false, "Average time in milliseconds to stabilize spindle rotation."},
			{0x04, "0x04", "Start/Stop Count", 91, 91, 0, 4810, "0x0000000012CA", StatusOK, false, "Spindle motor start/stop cycle log count."},
			{0x05, "0x05", "Reallocated Sectors Count", 112, 112, 140, 184, "0x0000000000B8", StatusCritical, true, "Count of remapped physical sectors relocated to reserve cylinders."},
			{0x07, "0x07", "Seek Error Rate", 52, 52, 51, 412, "0x00000000019C", StatusWarning, false, "Frequency of physical arm positioning positioning errors."},
			{0x09, "0x09", "Power-On Hours", 42, 42, 0, 48210, "0x00000000BC52", StatusOK, false, "Total cumulative operating hours since manufacture."},
			{0x0C, "0x0C", "Power Cycle Count", 94, 94, 0, 2104, "0x000000000838", StatusOK, false, "Log count of full electrical power boot cycles."},
			{0xC2, "0xC2", "Temperature", 54, 58, 0, 54, "0x000000000036", StatusWarning, false, "Current internal sensor temperature in Celsius."},
			{0xC5, "0xC5", "Current Pending Sector Count", 130, 130, 0, 85, "0x000000000055", StatusCritical, true, "Count of unstable, unreadable sectors awaiting hardware reallocation."},
			{0xC6, "0xC6", "Offline Uncorrectable Sector Count", 120, 120, 0, 42, "0x00000000002A", StatusCritical, true, "Permanent, uncorrectable read/write failures on the magnetic surface."},
		}
	case HealthySSD:
		attrs = []SmartAttribute{
			{0x05, "0x05", "Reallocated Flash Blocks", 100, 100, 10, 0, "0x000000000000", StatusOK, true, "Count of reallocated flash memory blocks."},
			{0x09, "0x09", "Power-On Hours", 98, 98, 0, 1240, "0x0000000004D8", StatusOK, false, "Total cumulative operating hours since manufacture."},
			{0x0C, "0x0C", "Power Cycle Count", 99, 99, 0, 451, "0x0000000001C3", StatusOK, false, "Log count of full electrical power boot cycles."},
			{0xE7, "0xE7", "SSD Life Left (Wear Leveling)", 100, 100, 10, 100, "0x000000000064", StatusOK, true, "Remaining life indicator based on NAND write endurance cycle consumption."},
			{0xF1, "0xF1", "Total LBAs Written", 99, 99, 0, 48210432, "0x000002DF8800", StatusOK, false, "Total cumulative bytes written to the flash controller (in sectors)."},
			{0xF2, "0xF2", "Total LBAs Read", 99, 99, 0, 89421002, "0x000005549000", StatusOK, false, "Total cumulative bytes read from the flash controller (in sectors)."},
		}
	case FailingSSD:
		attrs = []SmartAttribute{
			{0x05, "0x05", "Reallocated Flash Blocks", 42, 42, 10, 582, "0x000000000246", StatusCritical, true, "Count of reallocated flash memory blocks."},
			{0x09, "0x09", "Power-On Hours", 85, 85, 0, 24820, "0x0000000060F4", StatusOK, false, "Total cumulative operating hours since manufacture."},
			{0x0C, "0x0C", "Power Cycle Count", 91, 91, 0, 1840, "0x000000000730", StatusOK, false, "Log count of full electrical power boot cycles."},
			{0xE7, "0xE7", "SSD Life Left (Wear Leveling)", 8, 8, 10, 8, "0x000000000008", StatusCritical, true, "Remaining life indicator based on NAND write endurance cycle consumption."},
			{0xF1, "0xF1", "Total LBAs Written", 40, 40, 0, 4821043200, "0x00011F2E4000", StatusOK, false, "Total cumulative bytes written to the flash controller (in sectors)."},
			{0xF2, "0xF2", "Total LBAs Read", 50, 50, 0, 9841203901, "0x00024A9A2200", StatusOK, false, "Total cumulative bytes read from the flash controller (in sectors)."},
		}
	}

	return attrs
}

// CalculateOverallHealth checks the critical S.M.A.R.T attributes and returns pass/fail
func CalculateOverallHealth(attributes []SmartAttribute) string {
	if len(attributes) == 0 {
		return "UNKNOWN"
	}
	warningCount := 0
	criticalCount := 0
	for _, attr := range attributes {
		if attr.Status == StatusCritical && attr.IsCritical {
			criticalCount++
		}
		if attr.Status == StatusWarning {
			warningCount++
		}
	}
	if criticalCount > 0 {
		return "FAILING (CRITICAL)"
	} else if warningCount > 1 {
		return "WARNING (DEGRADED)"
	}
	return "PASS (HEALTHY)"
}

// TriggerSmartAlerts parses S.M.A.R.T. registers and fires list of warnings
func TriggerSmartAlerts(attributes []SmartAttribute) []SmartAlert {
	var alerts []SmartAlert
	for _, attr := range attributes {
		if attr.ID == 0x05 && attr.RawValue > 0 {
			severity := "WARNING"
			if attr.RawValue > 20 {
				severity = "CRITICAL"
			}
			alerts = append(alerts, SmartAlert{
				ID:            0x05,
				AttributeName: "Reallocated Sectors Count",
				CurrentValue:  attr.RawValue,
				Threshold:     int64(attr.Threshold),
				Severity:      severity,
				Message:       fmt.Sprintf("Drive has reallocated %d physically damaged sectors to the reserve pool. Platter degradation detected.", attr.RawValue),
			})
		}
		if attr.ID == 0xC5 && attr.RawValue > 0 {
			severity := "WARNING"
			if attr.RawValue > 5 {
				severity = "CRITICAL"
			}
			alerts = append(alerts, SmartAlert{
				ID:            0xC5,
				AttributeName: "Current Pending Sector Count",
				CurrentValue:  attr.RawValue,
				Threshold:     int64(attr.Threshold),
				Severity:      severity,
				Message:       fmt.Sprintf("Drive has %d unstable sectors waiting for write-cycle reallocation. Danger of crash.", attr.RawValue),
			})
		}
		if attr.ID == 0xC6 && attr.RawValue > 0 {
			alerts = append(alerts, SmartAlert{
				ID:            0xC6,
				AttributeName: "Offline Uncorrectable Sector Count",
				CurrentValue:  attr.RawValue,
				Threshold:     int64(attr.Threshold),
				Severity:      "CRITICAL",
				Message:       fmt.Sprintf("Permanent uncorrectable read/write failures: %d sectors cannot be recovered by physical ECC hardware.", attr.RawValue),
			})
		}
		if attr.ID == 0xE7 && attr.RawValue < 15 {
			alerts = append(alerts, SmartAlert{
				ID:            0xE7,
				AttributeName: "Wear Leveling Indicator (SSD life)",
				CurrentValue:  attr.RawValue,
				Threshold:     int64(attr.Threshold),
				Severity:      "CRITICAL",
				Message:       fmt.Sprintf("NAND Flash memory wear limit reached! wear rating: %d%%. Drive is locking down to read-only mode.", attr.RawValue),
			})
		}
	}
	return alerts
}
