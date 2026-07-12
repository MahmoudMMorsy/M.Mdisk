package main

import (
	"bufio"
	"fmt"
	"math/rand"
	"os"
	"strconv"
	"strings"
	"time"
)

// RunCLIMode starts the interactive CLI console mode
func RunCLIMode() {
	reader := bufio.NewReader(os.Stdin)
	InitializeVirtualDisk()
	DrivesList = make([]DriveInfo, 0)

	// Pre-populate drives list
	DrivesList = append(DrivesList, DriveInfo{
		Index:          0,
		Path:           "\\\\.\\PhysicalDrive0",
		Vendor:         "Western Digital",
		Model:          "WDC WD10EZEX-08WN4A0",
		Firmware:       "80.00A80",
		Serial:         "WD-WCC6Y1AK7SF2",
		CapacityBytes:  1000204886016,
		TotalSectors:   1000204886016 / 512,
		SectorSize:     512,
		ConnectionType: "SATA Direct",
		IsSimulated:    true,
	})

	DrivesList = append(DrivesList, DriveInfo{
		Index:          1,
		Path:           "\\\\.\\PhysicalDrive1",
		Vendor:         "Western Digital",
		Model:          "WDC WD120EFBX-68B0AY0",
		Firmware:       "85.00A85",
		Serial:         "WD-WMC6N0F9L8K2",
		CapacityBytes:  0,
		TotalSectors:   0,
		SectorSize:     512,
		ConnectionType: "SATA Bridge (Simulated)",
		IsSimulated:    true,
		ActiveIssue:    "TRANSLATOR_FAILURE",
	})

	SelectedDrive = &DrivesList[0]
	ActiveFailure = nil
	CurrentProfile = HealthyHDD
	SmartAttrs = GetMockSmartAttributes(HealthyHDD)
	Partitions = GetDefaultPartitions(SelectedDrive.CapacityBytes)

	for {
		fmt.Println("\n\033[95m====================================================================\033[0m")
		fmt.Println("\033[96m       NOOR OS / PC-3000 ADVANCED STORAGE UTILITY - CLI MODE        \033[0m")
		fmt.Println("\033[95m====================================================================\033[0m")
		if SelectedDrive != nil {
			fmt.Printf("\033[92m[+] Selected Drive: %s %s (%s) [SN: %s]\033[0m\n", SelectedDrive.Vendor, SelectedDrive.Model, FormatSize(SelectedDrive.CapacityBytes), SelectedDrive.Serial)
		} else {
			fmt.Println("\033[31m[-] No Drive Selected!\033[0m")
		}
		fmt.Println("--------------------------------------------------------------------")
		fmt.Println("1. Select Target Physical Disk / اختيار القرص الصلب")
		fmt.Println("2. S.M.A.R.T. Health Telemetry & Diagnostics / فحص المستشعرات")
		fmt.Println("3. Victoria Platter Latency Scan / فحص قطاعات القرص فيكتوريا")
		fmt.Println("4. SpinRite Magnetic Pulse Surface Recovery / بث نبضات المغنطة")
		fmt.Println("5. Partition Management & MBR/GPT Recovery / إدارة الأقسام")
		fmt.Println("6. Deep File Carver Signature Recovery / استعادة الملفات")
		fmt.Println("7. Advanced Imager Core (Skip/Retry/Reverse) / الاستنساخ المتقدم")
		fmt.Println("8. Firmware SA / Boot Maintenance / صيانة الفيرموير ومناطق الخدمة")
		fmt.Println("9. Exit / خروج")
		fmt.Println("--------------------------------------------------------------------")
		fmt.Print("\033[93mEnter choice [1-9]: \033[0m")

		choiceStr, _ := reader.ReadString('\n')
		choiceStr = strings.TrimSpace(choiceStr)
		if choiceStr == "" {
			continue
		}

		choice, err := strconv.Atoi(choiceStr)
		if err != nil {
			fmt.Println("Invalid input! Please enter a number between 1 and 9.")
			continue
		}

		switch choice {
		case 1:
			fmt.Println("\n[ Available Drives / الأقراص المتاحة ]:")
			for idx, d := range DrivesList {
				fmt.Printf("%d. %s %s [%s] [SN: %s]\n", idx+1, d.Vendor, d.Model, FormatSize(d.CapacityBytes), d.Serial)
			}
			fmt.Print("Choose Drive Index: ")
			idxStr, _ := reader.ReadString('\n')
			idx, err := strconv.Atoi(strings.TrimSpace(idxStr))
			if err == nil && idx >= 1 && idx <= len(DrivesList) {
				SelectedDrive = &DrivesList[idx-1]
				ActiveFailure = nil
				profile := HealthyHDD
				for _, fail := range DetectedFailures {
					if fail.ID == SelectedDrive.ActiveIssue {
						ActiveFailure = &fail
						profile = FailingHDD
						break
					}
				}
				CurrentProfile = profile
				SmartAttrs = GetMockSmartAttributes(profile)
				Partitions = GetDefaultPartitions(SelectedDrive.CapacityBytes)
				fmt.Println("\033[92m✔ Drive selected successfully!\033[0m")
			} else {
				fmt.Println("\033[31m[-] Invalid selection.\033[0m")
			}

		case 2:
			if SelectedDrive == nil {
				fmt.Println("[-] Please select a drive first.")
				continue
			}
			fmt.Println("\n[ S.M.A.R.T. Health Telemetry Mappings ]:")
			fmt.Printf("%-5s %-32s %-5s %-5s %-6s %-14s %s\n", "HexID", "Attribute Name", "Value", "Worst", "Thresh", "Raw Hex", "Status")
			fmt.Println(strings.Repeat("─", 84))
			for _, attr := range SmartAttrs {
				fmt.Printf("%-5s %-32s %-5d %-5d %-6d %-14s %s\n",
					attr.HexID, attr.Name, attr.Value, attr.Worst, attr.Threshold, attr.RawHex, attr.Status)
			}
			overall := CalculateOverallHealth(SmartAttrs)
			fmt.Printf("\nOverall Evaluation Rating: %s\n", overall)

		case 3:
			if SelectedDrive == nil {
				fmt.Println("[-] Please select a drive first.")
				continue
			}
			fmt.Println("\nStarting Victoria Latency Heatmap Platter Block Scan...")
			stats := BlockScanStats{
				CurrentLBA:    0,
				BlocksTotal:   100,
				BlocksScanned: 0,
			}
			for i := 1; i <= 100; i++ {
				time.Sleep(30 * time.Millisecond)
				stats.BlocksScanned = i
				stats.CurrentLBA = int64(i) * 16384
				_, delayType := SimulatedLatency(CurrentProfile, i, 100)

				switch delayType {
				case Delay3ms:
					stats.Bin3++
				case Delay10ms:
					stats.Bin10++
				case Delay50ms:
					stats.Bin50++
				case Delay150ms:
					stats.Bin150++
				case Delay500ms:
					stats.Bin500++
				case DelayOver:
					stats.BinOver++
				case DelayError:
					stats.BinError++
					stats.ErrorCount++
				}

				stats.AverageSpeedMbs = 180.0 + rand.Float64()*40.0
				blockColorStr := PrintAnsiHeatmapBlock(delayType)
				fmt.Printf("%s", blockColorStr)
				if i%20 == 0 {
					fmt.Println()
				}
			}
			FormatStats(stats, ModeRead, true)

		case 4:
			if SelectedDrive == nil {
				fmt.Println("[-] Please select a drive first.")
				continue
			}
			fmt.Println("\nStarting SpinRite Magnetic Hysteresis Surface Realignment...")
			stats := RepairStats{
				ActiveSector:           8500,
				SectorsRepaired:        12000,
				SectorsRecovered:       8400,
				SignalsRegenerated:     342,
				ScanProgress:           1.0,
				CurrentHysteresisPhase: "BURST_POLARIZATION",
				SurfaceVoltageMv:       782,
				PulseHistory:           make([]int, 70),
			}
			for i := 0; i < 70; i++ {
				stats.PulseHistory[i] = SimulatedOscilloscopePulse("BURST_POLARIZATION", i)
			}
			PrintSpinRiteStatus(stats, 70, true)

		case 5:
			if SelectedDrive == nil {
				fmt.Println("[-] Please select a drive first.")
				continue
			}
			fmt.Println("\n[ Partition Volume Management & Layout ]")
			PrintPartitionTable(Partitions, SelectedDrive.CapacityBytes, true)
			fmt.Println("\nOptions:")
			fmt.Println("1. Create Partition / إنشاء قسم")
			fmt.Println("2. Delete Partition / حذف قسم")
			fmt.Println("3. Rebuild Partition Table MBR/GPT / إعادة بناء جدول الأقسام التالف")
			fmt.Println("4. Secure Zero-Wipe Volume / مسح وتصفير كامل")
			fmt.Print("Choose option: ")
			optStr, _ := reader.ReadString('\n')
			opt, _ := strconv.Atoi(strings.TrimSpace(optStr))
			if opt == 1 {
				fmt.Print("Enter volume label: ")
				label, _ := reader.ReadString('\n')
				label = strings.TrimSpace(label)
				fmt.Print("Enter size in MB: ")
				szStr, _ := reader.ReadString('\n')
				sz, _ := strconv.ParseInt(strings.TrimSpace(szStr), 10, 64)
				var err error
				Partitions, err = CreateNewPartition(Partitions, label, "NTFS", sz)
				if err == nil {
					fmt.Println("\033[92m✔ Partition created successfully!\033[0m")
				} else {
					fmt.Printf("\033[31m[-] Error: %s\033[0m\n", err.Error())
				}
			} else if opt == 2 {
				fmt.Print("Enter Partition ID to delete: ")
				idStr, _ := reader.ReadString('\n')
				id, _ := strconv.Atoi(strings.TrimSpace(idStr))
				var err error
				Partitions, err = DeletePartition(Partitions, id)
				if err == nil {
					fmt.Println("\033[92m✔ Partition deleted successfully!\033[0m")
				} else {
					fmt.Printf("\033[31m[-] Error: %s\033[0m\n", err.Error())
				}
			} else if opt == 3 {
				fmt.Println("\nScanning and reconstructing lost partition maps (TestDisk Algorithm)...")
				Partitions = ScanPartitionsTestDisk(SelectedDrive.Path, SelectedDrive.IsSimulated, true)
				fmt.Println("\033[92m✔ Partition table successfully reconstructed!\033[0m")
			} else if opt == 4 {
				fmt.Print("Enter Partition Index to zero-wipe: ")
				idStr, _ := reader.ReadString('\n')
				idx, _ := strconv.Atoi(strings.TrimSpace(idStr))
				if idx >= 0 && idx < len(Partitions) {
					SecureWipePartition(Partitions[idx], SelectedDrive.Path, SelectedDrive.IsSimulated, true)
				} else {
					fmt.Println("\033[31m[-] Invalid partition index.\033[0m")
				}
			}

		case 6:
			if SelectedDrive == nil {
				fmt.Println("[-] Please select a drive first.")
				continue
			}
			fmt.Println("\nInitiating Deep Signature Carver Scanner on magnetic platter layers...")
			files := DeepScanFileCarver(SelectedDrive.Path, 0, 1000, SelectedDrive.IsSimulated, true)
			fmt.Printf("\nFound %d recoverable files!\n", len(files))
			for idx, f := range files {
				fmt.Printf("%d. %s [%s] [LBA: %d] [Integrity: %s]\n", idx+1, f.Name, FormatSize(f.SizeBytes), f.SectorLBA, f.Integrity)
			}
			if len(files) > 0 {
				fmt.Print("\nEnter file index to recover and save on host: ")
				idStr, _ := reader.ReadString('\n')
				id, _ := strconv.Atoi(strings.TrimSpace(idStr))
				if id >= 1 && id <= len(files) {
					SaveFileToDisk(files[id-1], "./Recovered_Files", true)
				}
			}

		case 7:
			if SelectedDrive == nil {
				fmt.Println("[-] Please select a drive first.")
				continue
			}
			fmt.Println("\n[ Advanced Imager Core (Skip / Retry / Reverse) ]")
			fmt.Println("Configuring default non-blocking read strategies:")
			fmt.Println("• Timeout Limit: 2500ms")
			fmt.Println("• Read Direction: Reverse (Backwards to avoid head stiction lock)")
			fmt.Println("• Max Retry Attempts: 3")
			fmt.Println("• Bad Sector Skip: True")
			fmt.Println("\nStarting imaging sequence...")
			for lba := int64(100); lba <= 105; lba++ {
				time.Sleep(150 * time.Millisecond)
				fmt.Printf("✔ Copying Sector LBA: 0x%08X | Strategy: Reverse Copy | Status: Success\n", lba)
			}
			fmt.Println("\033[92m✔ Image task completed successfully. Image saved to: ./DiskImage_NoorOS.img\033[0m")

		case 8:
			if SelectedDrive == nil {
				fmt.Println("[-] Please select a drive first.")
				continue
			}
			fmt.Println("\n[ Firmware Service Area (SA) & Boot Maintenance ]")
			state := InitializeLowLevelSA()
			PrintFirmwareModules(state.Modules, true)
			fmt.Println("\nOptions:")
			fmt.Println("1. Reset Busy / LED:000000CC State / تصفير حالة التجميد")
			fmt.Println("2. Run ATA / SCSI Security Erase / مسح آمن للقرص")
			fmt.Println("3. Patch ROM EEPROM Bios (Bypass faulty Head Map) / تعديل جدول الرؤوس بالروم")
			fmt.Println("4. Reconstruct Boot Records (Bootstrap Code) / إصلاح ملفات إقلاع النظام")
			fmt.Print("Choose option: ")
			optStr, _ := reader.ReadString('\n')
			opt, _ := strconv.Atoi(strings.TrimSpace(optStr))
			if opt == 1 {
				fmt.Println("\nSending raw terminal unlock instructions to microcode...")
				time.Sleep(500 * time.Millisecond)
				fmt.Println("\033[92m✔ Busy State and LED:000000CC serial locked exception successfully cleared!\033[0m")
			} else if opt == 2 {
				fmt.Println("\nExecuting firmware-level command ATA SECURITY ERASE (0xF4)...")
				time.Sleep(600 * time.Millisecond)
				fmt.Println("\033[92m✔ Security erase transaction completed. All platter sectors flushed to low physical charge state!\033[0m")
			} else if opt == 3 {
				FlashRomChip([]int{0, 1, 3}, true) // Bypass head 2
			} else if opt == 4 {
				fmt.Println("\nWriting safe boot records (VBR / MBR Bootstrap program structure)...")
				time.Sleep(500 * time.Millisecond)
				fmt.Println("\033[92m✔ Boot records repaired. Noor OS System partition marked active and bootable!\033[0m")
			}

		case 9:
			fmt.Println("\nThank you for using Noor OS PC-3000 Tool. Exiting...")
			return
		}
	}
}
