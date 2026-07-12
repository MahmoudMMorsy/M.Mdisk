//go:build windows
// +build windows

package main

import (
	"bytes"
	"fmt"
	"strings"
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

// Win32 DLL loaders
var (
	shell32           = syscall.NewLazyDLL("shell32.dll")
	procIsUserAnAdmin = shell32.NewProc("IsUserAnAdmin")

	procGetLogicalDriveStringsW = kernel32.NewProc("GetLogicalDriveStringsW")
	procGetDriveTypeW        = kernel32.NewProc("GetDriveTypeW")
	procGetDiskFreeSpaceExW  = kernel32.NewProc("GetDiskFreeSpaceExW")
)

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

// IsAdmin checks if the current process has Administrator privileges on Windows using shell32.dll
func IsAdmin() bool {
	ret, _, _ := procIsUserAnAdmin.Call()
	return ret != 0
}

// IOCTL control codes for storage
const (
	IOCTL_STORAGE_QUERY_PROPERTY = 0x002d1400 // Storage query property
	IOCTL_DISK_GET_DRIVE_GEOMETRY_EX = 0x000700A0
	IOCTL_DISK_GET_DRIVE_LAYOUT_EX   = 0x00070050
	SMART_GET_VERSION                = 0x00074080
	SMART_RCV_DRIVE_DATA             = 0x0007c088
)

// Storage query property structures
type STORAGE_PROPERTY_QUERY struct {
	PropertyId int32
	QueryType  int32
	AdditionalParameters [1]byte
}

type STORAGE_DEVICE_DESCRIPTOR struct {
	Version               uint32
	Size                  uint32
	DeviceType            byte
	DeviceTypeModifier    byte
	RemovableMedia        bool
	CommandQueueing       bool
	VendorIdOffset        uint32
	ProductIdOffset       uint32
	ProductRevisionOffset uint32
	SerialNumberOffset    uint32
	BusType               uint32
	RawPropertiesLength   uint32
	RawDeviceProperties   [512]byte
}

// SMART structures
type GETVERSIONOUTPARAMS struct {
	Version      byte
	Revision     byte
	Reserved     byte
	Map          byte
	IDEDevMap    uint32
	Capabilities uint32
	Reserved2    [4]uint32
}

type SENDCMDINPARAMS struct {
	BufferSize  uint32
	Features    byte
	SectorCount byte
	SectorNumber byte
	CylLow       byte
	CylHigh      byte
	DriveHead    byte
	Command      byte
	Reserved     [3]byte
	Reserved2    [4]uint32
	Buffer       [1]byte
}

type SENDCMDOUTPARAMS struct {
	BufferSize  uint32
	DriverStatus [4]byte
	Buffer       [512]byte
}

// EnumerateLogicalDrives retrieves all active drive letters (C:\, D:\, etc.) with actual capacities and drive types
func EnumerateLogicalDrives() []DriveInfo {
	var drives []DriveInfo

	buffer := make([]uint16, 256)
	ret, _, _ := procGetLogicalDriveStringsW.Call(uintptr(len(buffer)), uintptr(unsafe.Pointer(&buffer[0])))
	if ret == 0 {
		return drives
	}

	// Parse null-terminated strings
	var driveLetters []string
	curr := 0
	for i := 0; i < int(ret); i++ {
		if buffer[i] == 0 {
			if i > curr {
				driveLetters = append(driveLetters, string(utf16ToRunes(buffer[curr:i])))
			}
			curr = i + 1
		}
	}

	for idx, letter := range driveLetters {
		uLetter, err := syscall.UTF16PtrFromString(letter)
		if err != nil {
			continue
		}

		// Get drive type (e.g. DRIVE_FIXED = 3, DRIVE_REMOVABLE = 2, DRIVE_CDROM = 5)
		driveType, _, _ := procGetDriveTypeW.Call(uintptr(unsafe.Pointer(uLetter)))

		typeStr := "Fixed HDD/SSD"
		if driveType == 2 {
			typeStr = "Removable USB Flash"
		} else if driveType == 5 {
			typeStr = "CD-ROM Drive"
		} else if driveType == 4 {
			typeStr = "Network Drive"
		}

		// Get drive capacity
		var freeBytesAvailable, totalNumberOfBytes, totalNumberOfFreeBytes int64
		_, _, _ = procGetDiskFreeSpaceExW.Call(
			uintptr(unsafe.Pointer(uLetter)),
			uintptr(unsafe.Pointer(&freeBytesAvailable)),
			uintptr(unsafe.Pointer(&totalNumberOfBytes)),
			uintptr(unsafe.Pointer(&totalNumberOfFreeBytes)),
		)

		if totalNumberOfBytes <= 0 {
			continue // skip empty drives / card readers without media
		}

		path := fmt.Sprintf("\\\\.\\%s", strings.TrimSuffix(letter, "\\"))

		drives = append(drives, DriveInfo{
			Index:          idx + 1000,
			Path:           path,
			Vendor:         "Logical Volume",
			Model:          fmt.Sprintf("Partition Drive (%s)", strings.TrimSuffix(letter, "\\")),
			Firmware:       "WIN-LOG",
			Serial:         fmt.Sprintf("VOL-SERIAL-%s", strings.TrimSuffix(letter, ":\\")),
			CapacityBytes:  totalNumberOfBytes,
			TotalSectors:   totalNumberOfBytes / 512,
			SectorSize:     512,
			ConnectionType: typeStr,
			IsSimulated:    false,
		})
	}

	return drives
}

func utf16ToRunes(s []uint16) []rune {
	r := make([]rune, len(s))
	for i, v := range s {
		r[i] = rune(v)
	}
	return r
}

// EnumeratePhysicalDrives checks for active PhysicalDrive paths and queries their properties natively
func EnumeratePhysicalDrives() []DriveInfo {
	var drives []DriveInfo

	// Scan PhysicalDrive0 to PhysicalDrive15
	for i := 0; i < 16; i++ {
		path := fmt.Sprintf("\\\\.\\PhysicalDrive%d", i)

		utfPath, err := syscall.UTF16PtrFromString(path)
		if err != nil {
			continue
		}

		handle, err := syscall.CreateFile(
			utfPath,
			syscall.GENERIC_READ|syscall.GENERIC_WRITE,
			syscall.FILE_SHARE_READ|syscall.FILE_SHARE_WRITE,
			nil,
			syscall.OPEN_EXISTING,
			0,
			0,
		)
		if err != nil {
			// Try read-only if read-write fails
			handle, err = syscall.CreateFile(
				utfPath,
				syscall.GENERIC_READ,
				syscall.FILE_SHARE_READ|syscall.FILE_SHARE_WRITE,
				nil,
				syscall.OPEN_EXISTING,
				0,
				0,
			)
			if err != nil {
				continue
			}
		}
		defer syscall.CloseHandle(handle)

		// 1. Get drive size using IOCTL_DISK_GET_DRIVE_GEOMETRY_EX
		var sizeBytes int64 = 500107862016 // fallback 500GB
		var geometry diskGeometryEx
		var bytesReturned uint32

		err = syscall.DeviceIoControl(
			handle,
			IOCTL_DISK_GET_DRIVE_GEOMETRY_EX,
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

		// 2. Query hardware details using IOCTL_STORAGE_QUERY_PROPERTY
		var query STORAGE_PROPERTY_QUERY
		query.PropertyId = 0 // StorageDeviceProperty
		query.QueryType = 0  // PropertyStandardQuery

		var desc STORAGE_DEVICE_DESCRIPTOR
		err = syscall.DeviceIoControl(
			handle,
			IOCTL_STORAGE_QUERY_PROPERTY,
			(*byte)(unsafe.Pointer(&query)),
			uint32(unsafe.Sizeof(query)),
			(*byte)(unsafe.Pointer(&desc)),
			uint32(unsafe.Sizeof(desc)),
			&bytesReturned,
			nil,
		)

		vendor := "Generic"
		model := fmt.Sprintf("Physical Drive %d", i)
		serial := fmt.Sprintf("HW-DISK-%04d", i)
		firmware := "HW-REV"

		if err == nil {
			// Cast strictly to the bounds of the actual descriptor structure to prevent buffer over-read
			raw := (*[544]byte)(unsafe.Pointer(&desc))
			if desc.VendorIdOffset > 0 && desc.VendorIdOffset < desc.Size {
				vendor = getStringAtOffset(raw[:], desc.VendorIdOffset)
			}
			if desc.ProductIdOffset > 0 && desc.ProductIdOffset < desc.Size {
				model = getStringAtOffset(raw[:], desc.ProductIdOffset)
			}
			if desc.SerialNumberOffset > 0 && desc.SerialNumberOffset < desc.Size {
				serial = getStringAtOffset(raw[:], desc.SerialNumberOffset)
			}
			if desc.ProductRevisionOffset > 0 && desc.ProductRevisionOffset < desc.Size {
				firmware = getStringAtOffset(raw[:], desc.ProductRevisionOffset)
			}
		}

		busTypeStr := "SATA"
		if desc.BusType == 11 { // BusTypeNvme
			busTypeStr = "NVMe"
		} else if desc.BusType == 7 { // BusTypeUsb
			busTypeStr = "USB"
		}

		drives = append(drives, DriveInfo{
			Index:          i,
			Path:           path,
			Vendor:         vendor,
			Model:          model,
			Firmware:       firmware,
			Serial:         serial,
			CapacityBytes:  sizeBytes,
			TotalSectors:   sizeBytes / 512,
			SectorSize:     512,
			ConnectionType: fmt.Sprintf("%s Direct HW Link", busTypeStr),
			IsSimulated:    false,
		})
	}

	return drives
}

func getStringAtOffset(raw []byte, offset uint32) string {
	if int(offset) >= len(raw) {
		return ""
	}
	end := bytes.IndexByte(raw[offset:], 0)
	if end == -1 {
		return strings.TrimSpace(string(raw[offset:]))
	}
	return strings.TrimSpace(string(raw[offset : int(offset)+end]))
}

// QueryPhysicalSmartAttributes queries actual S.M.A.R.T. registers of a physical drive via Win32 IOCTL
func QueryPhysicalSmartAttributes(path string) ([]SmartAttribute, error) {
	var attrs []SmartAttribute

	utfPath, err := syscall.UTF16PtrFromString(path)
	if err != nil {
		return nil, err
	}

	handle, err := syscall.CreateFile(
		utfPath,
		syscall.GENERIC_READ|syscall.GENERIC_WRITE,
		syscall.FILE_SHARE_READ|syscall.FILE_SHARE_WRITE,
		nil,
		syscall.OPEN_EXISTING,
		0,
		0,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to open disk for S.M.A.R.T query: %v", err)
	}
	defer syscall.CloseHandle(handle)

	// 1. Get S.M.A.R.T version and capabilities
	var ver GETVERSIONOUTPARAMS
	var bytesReturned uint32
	err = syscall.DeviceIoControl(
		handle,
		SMART_GET_VERSION,
		nil,
		0,
		(*byte)(unsafe.Pointer(&ver)),
		uint32(unsafe.Sizeof(ver)),
		&bytesReturned,
		nil,
	)
	if err != nil {
		return nil, fmt.Errorf("S.M.A.R.T is disabled or unsupported by controller: %v", err)
	}

	// 2. Build input command parameters to retrieve S.M.A.R.T. attributes
	var in SENDCMDINPARAMS
	in.BufferSize = 512
	in.Command = 0x05 // SMART_CMD
	in.Features = 0xD0 // SMART_READ_VALUES
	in.SectorCount = 1
	in.SectorNumber = 1
	in.CylLow = 0x4F
	in.CylHigh = 0xC2

	var out SENDCMDOUTPARAMS
	err = syscall.DeviceIoControl(
		handle,
		SMART_RCV_DRIVE_DATA,
		(*byte)(unsafe.Pointer(&in)),
		uint32(unsafe.Sizeof(in)),
		(*byte)(unsafe.Pointer(&out)),
		uint32(unsafe.Sizeof(out)),
		&bytesReturned,
		nil,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to retrieve S.M.A.R.T registers: %v", err)
	}

	// Parse the 512-byte raw data payload
	// Offset 361 starts the 361-512 attributes array (12 bytes per attribute)
	raw := out.Buffer[:]
	for i := 2; i < 362; i += 12 {
		id := int(raw[i])
		if id == 0 {
			continue
		}

		status := raw[i+1] // flags
		val := int(raw[i+3])
		worst := int(raw[i+4])

		// Parse raw value
		rawValue := int64(raw[i+5]) | (int64(raw[i+6]) << 8) | (int64(raw[i+7]) << 16) | (int64(raw[i+8]) << 24) | (int64(raw[i+9]) << 32) | (int64(raw[i+10]) << 40)

		name := GetSmartAttributeName(id)
		hexID := fmt.Sprintf("0x%02X", id)
		rawHex := fmt.Sprintf("0x%012X", rawValue)

		health := StatusOK
		if val < 20 || (id == 0x05 && rawValue > 10) || (id == 0xC5 && rawValue > 0) {
			health = StatusCritical
		} else if val < 50 {
			health = StatusWarning
		}

		attrs = append(attrs, SmartAttribute{
			ID:          id,
			HexID:       hexID,
			Name:        name,
			Value:       val,
			Worst:       worst,
			Threshold:   10, // generic threshold fallback
			RawValue:    rawValue,
			RawHex:      rawHex,
			Status:      health,
			IsCritical:  id == 0x05 || id == 0xC5 || id == 0xC6,
			Description: fmt.Sprintf("S.M.A.R.T parameter index %s. Status Flags: 0x%02X", hexID, status),
		})
	}

	return attrs, nil
}

// GetSmartAttributeName decodes Win32 S.M.A.R.T parameter names
func GetSmartAttributeName(id int) string {
	switch id {
	case 0x01:
		return "Raw Read Error Rate"
	case 0x02:
		return "Throughput Performance"
	case 0x03:
		return "Spin-Up Time"
	case 0x04:
		return "Start/Stop Count"
	case 0x05:
		return "Reallocated Sectors Count"
	case 0x07:
		return "Seek Error Rate"
	case 0x08:
		return "Seek Time Performance"
	case 0x09:
		return "Power-On Hours"
	case 0x0A:
		return "Spin Retry Count"
	case 0x0C:
		return "Power Cycle Count"
	case 0xC2:
		return "Temperature"
	case 0xC4:
		return "Reallocation Event Count"
	case 0xC5:
		return "Current Pending Sector Count"
	case 0xC6:
		return "Offline Uncorrectable Sector Count"
	case 0xC7:
		return "UltraDMA CRC Error Count"
	default:
		return fmt.Sprintf("Vendor Specific Parameter (0x%02X)", id)
	}
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
		return nil, fmt.Errorf("failed to open physical disk: %v", err)
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

// CloneDiskReal performs a high-performance raw disk-to-disk or disk-to-file clone by opening handles once and using buffered block I/O
func CloneDiskReal(srcPath, destPath string, totalSectors int64, progressCallback func(float64)) error {
	utfSrc, err := syscall.UTF16PtrFromString(srcPath)
	if err != nil {
		return err
	}
	utfDest, err := syscall.UTF16PtrFromString(destPath)
	if err != nil {
		return err
	}

	// Open source handle once
	hSrc, err := syscall.CreateFile(
		utfSrc,
		syscall.GENERIC_READ,
		syscall.FILE_SHARE_READ|syscall.FILE_SHARE_WRITE,
		nil,
		syscall.OPEN_EXISTING,
		0,
		0,
	)
	if err != nil {
		return fmt.Errorf("failed to open source drive: %v", err)
	}
	defer syscall.CloseHandle(hSrc)

	// Open destination handle once. Use CREATE_ALWAYS to support creating files if they do not exist
	creationDisposition := uint32(syscall.OPEN_EXISTING)
	if !strings.HasPrefix(destPath, "\\\\.\\") {
		creationDisposition = syscall.CREATE_ALWAYS
	}
	hDest, err := syscall.CreateFile(
		utfDest,
		syscall.GENERIC_WRITE,
		syscall.FILE_SHARE_READ|syscall.FILE_SHARE_WRITE,
		nil,
		creationDisposition,
		0,
		0,
	)
	if err != nil {
		return fmt.Errorf("failed to open destination: %v", err)
	}
	defer syscall.CloseHandle(hDest)

	// Block size: 128 sectors (64 KB)
	sectorsPerBlock := int64(128)
	bufferSize := sectorsPerBlock * 512
	buffer := make([]byte, bufferSize)

	for lba := int64(0); lba < totalSectors; lba += sectorsPerBlock {
		if lba+sectorsPerBlock > totalSectors {
			sectorsPerBlock = totalSectors - lba
			bufferSize = sectorsPerBlock * 512
			buffer = buffer[:bufferSize]
		}

		// Read block
		var bytesRead uint32
		err = syscall.ReadFile(hSrc, buffer, &bytesRead, nil)
		if err != nil {
			break
		}
		if bytesRead == 0 {
			break
		}

		// Write block
		var bytesWritten uint32
		err = syscall.WriteFile(hDest, buffer[:bytesRead], &bytesWritten, nil)
		if err != nil {
			break
		}

		progress := (float64(lba+sectorsPerBlock) / float64(totalSectors)) * 100.0
		if progress > 100.0 {
			progress = 100.0
		}
		progressCallback(progress)
	}

	progressCallback(100.0)
	return nil
}

// BackupDiskImageReal performs a high-performance single-handle buffered sector backup to a .bin file
func BackupDiskImageReal(srcPath, imageFilePath string, totalSectors int64, progressCallback func(float64)) error {
	utfSrc, err := syscall.UTF16PtrFromString(srcPath)
	if err != nil {
		return err
	}
	utfDest, err := syscall.UTF16PtrFromString(imageFilePath)
	if err != nil {
		return err
	}

	hSrc, err := syscall.CreateFile(
		utfSrc,
		syscall.GENERIC_READ,
		syscall.FILE_SHARE_READ|syscall.FILE_SHARE_WRITE,
		nil,
		syscall.OPEN_EXISTING,
		0,
		0,
	)
	if err != nil {
		return fmt.Errorf("failed to open source drive: %v", err)
	}
	defer syscall.CloseHandle(hSrc)

	hDest, err := syscall.CreateFile(
		utfDest,
		syscall.GENERIC_WRITE,
		syscall.FILE_SHARE_READ|syscall.FILE_SHARE_WRITE,
		nil,
		syscall.CREATE_ALWAYS, // Ensure file creation/overwrite
		0,
		0,
	)
	if err != nil {
		return fmt.Errorf("failed to create backup file: %v", err)
	}
	defer syscall.CloseHandle(hDest)

	sectorsPerBlock := int64(128)
	bufferSize := sectorsPerBlock * 512
	buffer := make([]byte, bufferSize)

	for lba := int64(0); lba < totalSectors; lba += sectorsPerBlock {
		if lba+sectorsPerBlock > totalSectors {
			sectorsPerBlock = totalSectors - lba
			bufferSize = sectorsPerBlock * 512
			buffer = buffer[:bufferSize]
		}

		var bytesRead uint32
		err = syscall.ReadFile(hSrc, buffer, &bytesRead, nil)
		if err != nil {
			break
		}
		if bytesRead == 0 {
			break
		}

		var bytesWritten uint32
		err = syscall.WriteFile(hDest, buffer[:bytesRead], &bytesWritten, nil)
		if err != nil {
			break
		}

		progress := (float64(lba+sectorsPerBlock) / float64(totalSectors)) * 100.0
		if progress > 100.0 {
			progress = 100.0
		}
		progressCallback(progress)
	}

	progressCallback(100.0)
	return nil
}
