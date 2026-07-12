//go:build !windows
// +build !windows

package main

import (
	"fmt"
)

// DriveInfo represents either a physical or simulated drive
type DriveInfo struct {
	Index          int
	Path           string
	Vendor         string
	Model          string
	Firmware       string
	Serial         string
	CapacityBytes  int64
	TotalSectors   int64
	SectorSize     int
	ConnectionType string
	IsSimulated    bool
	ActiveIssue    string // Failure mode if any
}

// IsAdmin checks if the current process has Admin privileges (root on non-Windows)
func IsAdmin() bool {
	// Simulated admin access for cross-platform compliance
	return true
}

// EnumeratePhysicalDrives returns mock physical drives on non-Windows
func EnumeratePhysicalDrives() []DriveInfo {
	return []DriveInfo{
		{
			Index:          0,
			Path:           "/dev/sda",
			Vendor:         "Direct Physical",
			Model:          "Simulated Linux Device Node (Host Mock)",
			Firmware:       "LNX-HW",
			Serial:         "HW-DISK-LNX0",
			CapacityBytes:  500107862016,
			TotalSectors:   500107862016 / 512,
			SectorSize:     512,
			ConnectionType: "SATA Direct",
			IsSimulated:    false,
		},
	}
}

// QueryPhysicalSmartAttributes mock stub for Linux compiles
func QueryPhysicalSmartAttributes(path string) ([]SmartAttribute, error) {
	return GetMockSmartAttributes(HealthyHDD), nil
}

// GetSmartAttributeName mock stub for Linux compiles
func GetSmartAttributeName(id int) string {
	return "Mock Parameter"
}

// ReadPhysicalSector reads a 512-byte block from virtual sector maps on Linux
func ReadPhysicalSector(path string, lba int64) ([]byte, error) {
	// Read directly from virtual disk sector map
	if lba >= 0 && lba < 1000 {
		buf := make([]byte, 512)
		copy(buf, VirtualDisk[lba*512:(lba+1)*512])
		return buf, nil
	}
	return make([]byte, 512), nil
}

// WritePhysicalSector writes a 512-byte block to virtual sector maps on Linux
func WritePhysicalSector(path string, lba int64, data []byte) error {
	if len(data) != 512 {
		return fmt.Errorf("data block must be exactly 512 bytes")
	}
	if lba >= 0 && lba < 1000 {
		copy(VirtualDisk[lba*512:(lba+1)*512], data)
	}
	return nil
}
