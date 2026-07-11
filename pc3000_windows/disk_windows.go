//go:build windows
// +build windows

package main

import (
	"fmt"
	"syscall"
	"unsafe"
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

var (
	shell32           = syscall.NewLazyDLL("shell32.dll")
	procIsUserAnAdmin = shell32.NewProc("IsUserAnAdmin")
)

// IsAdmin checks if the current process has Administrator privileges on Windows using shell32.dll
func IsAdmin() bool {
	ret, _, _ := procIsUserAnAdmin.Call()
	return ret != 0
}

// EnumeratePhysicalDrives checks for active PhysicalDrive paths on Windows
func EnumeratePhysicalDrives() []DriveInfo {
	var drives []DriveInfo

	// Scan PhysicalDrive0 to PhysicalDrive15
	for i := 0; i < 16; i++ {
		path := fmt.Sprintf("\\\\.\\PhysicalDrive%d", i)

		// Attempt to open the drive with read-only access to verify existence
		utfPath, err := syscall.UTF16PtrFromString(path)
		if err != nil {
			continue
		}

		handle, err := syscall.CreateFile(
			utfPath,
			syscall.GENERIC_READ,
			syscall.FILE_SHARE_READ|syscall.FILE_SHARE_WRITE,
			nil,
			syscall.OPEN_EXISTING,
			0,
			0,
		)
		if err != nil {
			// Drive does not exist or access denied
			continue
		}
		defer syscall.CloseHandle(handle)

		// Get drive size using IOCTL_DISK_GET_DRIVE_GEOMETRY_EX
		var sizeBytes int64 = 500107862016 // fallback 500GB
		var geometry diskGeometryEx
		var bytesReturned uint32

		ioctlDiskGetDriveGeometryEx := uint32(0x000700A0) // IOCTL_DISK_GET_DRIVE_GEOMETRY_EX
		err = syscall.DeviceIoControl(
			handle,
			ioctlDiskGetDriveGeometryEx,
			nil,
			0,
			(*byte)(unsafe.Pointer(&geometry)),
			uint32(unsafe.Sizeof(geometry)),
			&bytesReturned,
			nil,
		)
		if err == nil {
			sizeBytes = geometry.DiskSize
		}

		drives = append(drives, DriveInfo{
			Index:          i,
			Path:           path,
			Vendor:         "Physical",
			Model:          fmt.Sprintf("Direct Physical Drive %d", i),
			Firmware:       "WIN-HW",
			Serial:         fmt.Sprintf("HW-DISK-%04d", i),
			CapacityBytes:  sizeBytes,
			TotalSectors:   sizeBytes / 512,
			SectorSize:     512,
			ConnectionType: "SATA/NVMe Direct",
			IsSimulated:    false,
		})
	}

	return drives
}

// Disk Geometry structures for IOCTL
type diskGeometry struct {
	Cylinders         int64
	MediaType         int32
	TracksPerCylinder uint32
	SectorsPerTrack   uint32
	BytesPerSector    uint32
}

type diskGeometryEx struct {
	Geometry diskGeometry
	DiskSize int64
	Data     [1]byte
}

// ReadPhysicalSector reads a 512-byte block from a real physical Windows drive
func ReadPhysicalSector(path string, lba int64) ([]byte, error) {
	utfPath, err := syscall.UTF16PtrFromString(path)
	if err != nil {
		return nil, err
	}

	handle, err := syscall.CreateFile(
		utfPath,
		syscall.GENERIC_READ,
		syscall.FILE_SHARE_READ|syscall.FILE_SHARE_WRITE,
		nil,
		syscall.OPEN_EXISTING,
		0,
		0,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to open physical disk (requires admin rights): %v", err)
	}
	defer syscall.CloseHandle(handle)

	// Seek to Sector LBA * 512
	offset := lba * 512
	var low int32 = int32(offset & 0xFFFFFFFF)
	var high int32 = int32((offset >> 32) & 0xFFFFFFFF)

	_, err = syscall.SetFilePointer(handle, low, &high, syscall.FILE_BEGIN)
	if err != nil {
		return nil, fmt.Errorf("seek error on LBA %d: %v", lba, err)
	}

	buffer := make([]byte, 512)
	var bytesRead uint32
	err = syscall.ReadFile(handle, buffer, &bytesRead, nil)
	if err != nil {
		return nil, fmt.Errorf("read error on LBA %d: %v", lba, err)
	}

	return buffer, nil
}

// WritePhysicalSector writes a 512-byte block to a real physical Windows drive
func WritePhysicalSector(path string, lba int64, data []byte) error {
	if len(data) != 512 {
		return fmt.Errorf("data block must be exactly 512 bytes")
	}

	utfPath, err := syscall.UTF16PtrFromString(path)
	if err != nil {
		return err
	}

	handle, err := syscall.CreateFile(
		utfPath,
		syscall.GENERIC_WRITE,
		syscall.FILE_SHARE_READ|syscall.FILE_SHARE_WRITE,
		nil,
		syscall.OPEN_EXISTING,
		0,
		0,
	)
	if err != nil {
		return fmt.Errorf("failed to open physical disk with write permissions (requires admin rights): %v", err)
	}
	defer syscall.CloseHandle(handle)

	// Seek to Sector LBA * 512
	offset := lba * 512
	var low int32 = int32(offset & 0xFFFFFFFF)
	var high int32 = int32((offset >> 32) & 0xFFFFFFFF)

	_, err = syscall.SetFilePointer(handle, low, &high, syscall.FILE_BEGIN)
	if err != nil {
		return fmt.Errorf("seek error on LBA %d: %v", lba, err)
	}

	var bytesWritten uint32
	err = syscall.WriteFile(handle, data, &bytesWritten, nil)
	if err != nil {
		return fmt.Errorf("write error on LBA %d: %v", lba, err)
	}

	return nil
}
