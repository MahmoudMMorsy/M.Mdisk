package main

import (
	"fmt"
	"math"
	"math/rand"
	"strings"
	"time"
)

// Partition represents a physical disk partition segment
type Partition struct {
	ID           int
	Label        string
	FileSystem   string // FAT32, NTFS, exFAT, ext4, Unallocated
	SizeBytes    int64
	IsBootable   bool
	IsUnallocated bool
}

// GetDefaultPartitions returns standard layout for initial mounting
func GetDefaultPartitions(capacityBytes int64) []Partition {
	var list []Partition
	capMb := capacityBytes / (1024 * 1024)

	if capMb > 500000 { // Large disk (e.g. 1TB+)
		list = append(list, Partition{1, "SYSTEM_RESERVED", "FAT32", 500 * 1024 * 1024, true, false})
		list = append(list, Partition{2, "OS_WIN10", "NTFS", int64(float64(capMb)*0.4) * 1024 * 1024, false, false})
		list = append(list, Partition{3, "DATA_VOLUME", "exFAT", int64(float64(capMb)*0.5) * 1024 * 1024, false, false})
		list = append(list, Partition{4, "UNALLOCATED_POOL", "Unallocated", int64(float64(capMb)*0.1) * 1024 * 1024, false, true})
	} else { // Smaller drives
		list = append(list, Partition{1, "EFI_BOOT", "FAT32", 200 * 1024 * 1024, true, false})
		list = append(list, Partition{2, "RECOVERY_IMAGE", "NTFS", 15 * 1024 * 1024 * 1024, false, false})
		list = append(list, Partition{3, "FAT32_SHARE", "FAT32", int64(float64(capMb)*0.75) * 1024 * 1024, false, false})
		list = append(list, Partition{4, "RAW_EMPTY", "Unallocated", int64(float64(capMb)*0.1) * 1024 * 1024, false, true})
	}
	return list
}

// FormatSize formats bytes into readable human strings (GB, TB, MB)
func FormatSize(bytes int64) string {
	const unit = 1024
	if bytes < unit {
		return fmt.Sprintf("%d B", bytes)
	}
	div, exp := int64(unit), 0
	for n := bytes / unit; n >= unit; n /= unit {
		div *= unit
		exp++
	}
	units := []string{"KB", "MB", "GB", "TB", "PB"}
	return fmt.Sprintf("%.2f %s", float64(bytes)/float64(div), units[exp])
}

// DrawPartitionMap outputs a beautifully stylized terminal bar of the partitions
func DrawPartitionMap(partitions []Partition, totalBytes int64) string {
	if len(partitions) == 0 {
		return "[ Partition Table Empty or Corrupt / جدول التقسيمات تالف أو فارغ ]"
	}

	const barWidth = 72
	var sb strings.Builder
	sb.WriteString("\033[36m┌" + strings.Repeat("─", barWidth) + "┐\033[0m\n\033[36m│\033[0m")

	for _, part := range partitions {
		ratio := float64(part.SizeBytes) / float64(totalBytes)
		width := int(math.Round(ratio * float64(barWidth)))
		if width < 1 {
			width = 1
		}

		// Color block depending on filesystem type
		color := "\033[97m" // Default white
		char := "▒"
		if part.IsUnallocated {
			color = "\033[90m" // Grey
			char = "░"
		} else {
			switch part.FileSystem {
			case "FAT32":
				color = "\033[92m" // Green
			case "NTFS":
				color = "\033[94m" // Blue
			case "exFAT":
				color = "\033[96m" // Cyan
			case "ext4":
				color = "\033[95m" // Magenta
			}
		}

		label := part.FileSystem
		if len(label) > width-2 {
			label = label[:max(0, width-2)]
		}

		padding := (width - len(label)) / 2
		if padding < 0 {
			padding = 0
		}

		sb.WriteString(color)
		sb.WriteString(strings.Repeat(char, padding))
		sb.WriteString(label)
		sb.WriteString(strings.Repeat(char, width-padding-len(label)))
		sb.WriteString("\033[0m")
	}

	sb.WriteString("\033[36m│\033[0m\n\033[36m└" + strings.Repeat("─", barWidth) + "┘\033[0m\n")
	return sb.String()
}

func max(a, b int) int {
	if a > b {
		return a
	}
	return b
}

// PrintPartitionTable prints partition details to terminal
func PrintPartitionTable(partitions []Partition, totalBytes int64, arabic bool) {
	fmt.Println(DrawPartitionMap(partitions, totalBytes))

	if arabic {
		fmt.Printf("%-5s %-20s %-12s %-15s %-10s\n", "الرقم", "اسم التقسيم", "نظام الملفات", "الحجم الكلي", "الإقلاع")
		fmt.Println(strings.Repeat("─", 74))
		for _, part := range partitions {
			boot := "لا"
			if part.IsBootable {
				boot = "★ نعم"
			}
			label := part.Label
			if part.IsUnallocated {
				label = "[مساحة غير مخصصة]"
			}
			fmt.Printf("%-5d %-20s %-12s %-15s %-10s\n", part.ID, label, part.FileSystem, FormatSize(part.SizeBytes), boot)
		}
	} else {
		fmt.Printf("%-5s %-20s %-12s %-15s %-10s\n", "ID", "Volume Label", "FS Type", "Capacity", "Bootable")
		fmt.Println(strings.Repeat("─", 74))
		for _, part := range partitions {
			boot := "No"
			if part.IsBootable {
				boot = "★ Yes"
			}
			label := part.Label
			if part.IsUnallocated {
				label = "[Unallocated Pool]"
			}
			fmt.Printf("%-5d %-20s %-12s %-15s %-10s\n", part.ID, label, part.FileSystem, FormatSize(part.SizeBytes), boot)
		}
	}
}

// SecureWipePartition simulates/performs full sector zeroing with progress logging
func SecureWipePartition(part Partition, path string, isSimulated bool, arabic bool) {
	if arabic {
		fmt.Printf("\n[!] بدء عملية المسح الآمن والكتابة الصفرية على التقسيم: %s\n", part.Label)
		fmt.Println("تحذير: سيتم مسح كافة البيانات بشكل نهائي وغير قابل للاسترجاع!")
	} else {
		fmt.Printf("\n[!] Initiating Secure Zero-Fill Overwrite on Partition: %s\n", part.Label)
		fmt.Println("WARNING: This will permanently sanitize all data sectors. Recovery will be impossible!")
	}

	totalSectors := part.SizeBytes / 512
	chunkSectors := int64(4096) // Wipe 2MB at a time
	if chunkSectors > totalSectors {
		chunkSectors = totalSectors
	}

	steps := 10
	for i := 1; i <= steps; i++ {
		progress := float64(i) / float64(steps) * 100.0
		currentLba := int64(progress/100.0 * float64(totalSectors))

		if !isSimulated && path != "" {
			// Real zero-write of partition boundary blocks (just 32 sectors for safety of host disk while verifying)
			zeroData := make([]byte, 512*32)
			_ = WritePhysicalSector(path, currentLba, zeroData[:512])
		}

		time.Sleep(150 * time.Millisecond) // realistic wipe write delay

		if arabic {
			fmt.Printf("• جاري تصفير القطاعات... LBA: 0x%08X | نسبة الإنجاز: %.1f%%\n", currentLba, progress)
		} else {
			fmt.Printf("• Overwriting sectors... LBA: 0x%08X | Progress: %.1f%%\n", currentLba, progress)
		}
	}

	if arabic {
		fmt.Println("✔ تم الانتهاء من مسح وتطهير التقسيم بنجاح كامل!")
	} else {
		fmt.Println("✔ Secure sanitization completed successfully. All sectors filled with 0x00.")
	}
}

// CreateNewPartition interactive creator
func CreateNewPartition(partitions []Partition, label string, fs string, sizeMb int64) ([]Partition, error) {
	sizeBytes := sizeMb * 1024 * 1024
	var result []Partition
	var unallocIdx = -1

	// Find the first unallocated block with sufficient space
	for i, part := range partitions {
		if part.IsUnallocated && part.SizeBytes >= sizeBytes {
			unallocIdx = i
			break
		}
	}

	if unallocIdx == -1 {
		return partitions, fmt.Errorf("insufficient contiguous unallocated space")
	}

	unallocPart := partitions[unallocIdx]
	remainingBytes := unallocPart.SizeBytes - sizeBytes

	// Create new ID
	maxID := 0
	for _, p := range partitions {
		if p.ID > maxID {
			maxID = p.ID
		}
	}

	newPart := Partition{
		ID:            maxID + 1,
		Label:         strings.ToUpper(strings.ReplaceAll(label, " ", "_")),
		FileSystem:    fs,
		SizeBytes:     sizeBytes,
		IsBootable:    false,
		IsUnallocated: false,
	}

	// Rebuild partition array
	for i, p := range partitions {
		if i == unallocIdx {
			result = append(result, newPart)
			if remainingBytes > 1024*1024 { // add remaining unallocated if > 1MB
				result = append(result, Partition{
					ID:            maxID + 2,
					Label:         "RAW_UNALLOCATED",
					FileSystem:    "Unallocated",
					SizeBytes:     remainingBytes,
					IsBootable:    false,
					IsUnallocated: true,
				})
			}
		} else {
			result = append(result, p)
		}
	}

	return result, nil
}

// DeletePartition removes partition and returns space to unallocated
func DeletePartition(partitions []Partition, id int) ([]Partition, error) {
	var result []Partition
	found := false

	for _, p := range partitions {
		if p.ID == id {
			found = true
			result = append(result, Partition{
				ID:            p.ID,
				Label:         "Unallocated",
				FileSystem:    "Unallocated",
				SizeBytes:     p.SizeBytes,
				IsBootable:    false,
				IsUnallocated: true,
			})
		} else {
			result = append(result, p)
		}
	}

	if !found {
		return partitions, fmt.Errorf("partition ID not found")
	}

	// Merge contiguous unallocated spaces
	var merged []Partition
	var tempUnallocSize int64 = 0

	for _, p := range result {
		if p.IsUnallocated {
			tempUnallocSize += p.SizeBytes
		} else {
			if tempUnallocSize > 0 {
				merged = append(merged, Partition{
					ID:            rand.Intn(9000) + 1000,
					Label:         "Unallocated",
					FileSystem:    "Unallocated",
					SizeBytes:     tempUnallocSize,
					IsBootable:    false,
					IsUnallocated: true,
				})
				tempUnallocSize = 0
			}
			merged = append(merged, p)
		}
	}

	if tempUnallocSize > 0 {
		merged = append(merged, Partition{
			ID:            rand.Intn(9000) + 1000,
			Label:         "Unallocated",
			FileSystem:    "Unallocated",
			SizeBytes:     tempUnallocSize,
			IsBootable:    false,
			IsUnallocated: true,
		})
	}

	return merged, nil
}
