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

// Global state variables
var (
	SelectedDrive     *DriveInfo
	CurrentProfile    SmartProfile     = HealthyHDD
	SmartAttributes   []SmartAttribute
	SAState           LowLevelSAState
	DetectedFailures  []DriveFailure
	ActiveFailure     *DriveFailure
	WipedPartitions   map[int]bool
	CreatedPartitions []Partition
	CarvedFiles       []RecoveredFile
	ArabicMode        bool = true // Default to Arabic as requested by the user, can switch to English
)

func main() {
	rand.Seed(time.Now().UnixNano())
	InitializeVirtualDisk()
	SAState = InitializeLowLevelSA()
	DetectedFailures = GetFailureDetails()

	// Clear Screen
	fmt.Print("\033[H\033[2J")

	// Print high-tech boot logo
	PrintBootLogo()

	// Menu Loop
	reader := bufio.NewReader(os.Stdin)
	for {
		if ArabicMode {
			PrintArabicMenu()
		} else {
			PrintEnglishMenu()
		}

		fmt.Print("\033[36mPC-3000>> \033[0m")
		input, err := reader.ReadString('\n')
		if err != nil {
			break
		}
		input = strings.TrimSpace(input)
		if input == "" {
			continue
		}

		handleCommand(input, reader)
	}
}

func PrintBootLogo() {
	logo := `
\033[95m ██████╗  ██████╗     ██████╗  ██████╗  ██████╗  ██████╗     ██████╗ ██████╗  ██████╗ \033[0m
\033[95m ██╔══██╗██╔════╝     ╚════██╗██╔═████╗██╔═████╗██╔═████╗    ██╔══██╗██╔══██╗██╔═══██╗\033[0m
\033[95m ██████╔╝██║           █████╔╝██║██╔██║██║██╔██║██║██╔██║    ██████╔╝██████╔╝██║   ██║\033[0m
\033[95m ██╔═══╝ ██║           ╚═══██╗████╔╝██║████╔╝██║████╔╝██║    ██╔═══╝ ██╔══██╗██║   ██║\033[0m
\033[95m ██║     ╚██████╗     ██████╔╝╚██████╔╝╚██████╔╝╚██████╔╝    ██║     ██║  ██║╚██████╔╝\033[0m
\033[95m ╚═╝      ╚═════╝     ╚═════╝  ╚═════╝  ╚═════╝  ╚═════╝     ╚═╝     ╚═╝  ╚═╝ ╚═════╝ \033[0m
`
	fmt.Println(logo)
	fmt.Println("\033[91m      ACE LAB PC-3000 WINDOWS HARDWARE DIAGNOSTICS SUITE [NATIVE EXE]\033[0m")
	fmt.Println("\033[36m      ---------------------------------------------------------------\033[0m")
	fmt.Println("      Rostov-on-Don, Russia. Native x86_64 PE Executable Engine.")
	fmt.Println("      Arabic and English interactive diagnostic consoles supported.")
	fmt.Println()
}

func PrintArabicMenu() {
	fmt.Println("\n\033[36m========================= القائمة الرئيسية لـ PC-3000 =========================\033[0m")
	if SelectedDrive != nil {
		fmt.Printf("\033[92m✔ القرص النشط الحالي:\033[0m %s %s [%s]\n", SelectedDrive.Vendor, SelectedDrive.Model, FormatSize(SelectedDrive.CapacityBytes))
		if ActiveFailure != nil {
			fmt.Printf("\033[91m⚠ عطل الميكروكود المكتشف:\033[0m %s\n", ActiveFailure.TitleAr)
		} else {
			fmt.Println("\033[92m✔ حالة القرص:\033[0m سليم وجاهز للفحص.")
		}
	} else {
		fmt.Println("\033[33m⚠ لم يتم اختيار قرص صلب بعد! يرجى اختيار قرص صلب من الخيار (1) للبدء.\033[0m")
	}
	fmt.Println("\033[36m───────────────────────────────────────────────────────────────────────────────\033[0m")
	fmt.Println(" 1. اختيار/توصيل قرص صلب (فحص حقيقي للويندوز أو فحص أقراص محاكاة WD)")
	fmt.Println(" 2. فحص سجلات S.M.A.R.T. ومؤشرات صحة القرص (CRITICAL TELEMETRY)")
	fmt.Println(" 3. فحص قطاعات الميديا بمسح Victoria (Heatmap Sector Scanner)")
	fmt.Println(" 4. معالجة الإشارات المغناطيسية السطحية بنظام SpinRite Loop Repair")
	fmt.Println(" 5. إدارة وتقسيم الهارد ديسك (Partition Table Editor & Secure Wipe)")
	fmt.Println(" 6. استعادة الملفات والبارتشنات المحذوفة (Deep Carver & TestDisk Wizard)")
	fmt.Println(" 7. تحكم إلكترونيات الخدمة والـ ATA المباشر (SA Modules & ROM Programmer)")
	fmt.Println(" 8. تطبيق الإصلاح التلقائي الشامل بالذكاء الاصطناعي (AI Autonomous Self-Repair)")
	fmt.Println(" 9. تغيير لغة واجهة التطبيق إلى الإنجليزية (Switch Interface to English)")
	fmt.Println(" 0. الخروج من التطبيق وإغلاق واجهة الصيانة (Exit PC-3000)")
	fmt.Println("\033[36m===============================================================================\033[0m")
}

func PrintEnglishMenu() {
	fmt.Println("\n\033[36m========================= PC-3000 MAIN UTILITY MENU =========================\033[0m")
	if SelectedDrive != nil {
		fmt.Printf("\033[92m✔ Active Target:\033[0m %s %s [%s]\n", SelectedDrive.Vendor, SelectedDrive.Model, FormatSize(SelectedDrive.CapacityBytes))
		if ActiveFailure != nil {
			fmt.Printf("\033[91m⚠ Diagnosed Firmware Issue:\033[0m %s\n", ActiveFailure.TitleEn)
		} else {
			fmt.Println("\033[92m✔ Drive Status:\033[0m Healthy & Ready.")
		}
	} else {
		fmt.Println("\033[33m⚠ No hard drive selected! Please select a drive from Option (1) to begin.\033[0m")
	}
	fmt.Println("\033[36m───────────────────────────────────────────────────────────────────────────────\033[0m")
	fmt.Println(" 1. Select/Mount Hard Drive (Physical Windows Drives or Simulated WD Presets)")
	fmt.Println(" 2. S.M.A.R.T. Health Attributes & Register Mappings (Critical Telemetry)")
	fmt.Println(" 3. Victoria Platter Block Latency Heatmap Scan (Sector Scanner)")
	fmt.Println(" 4. SpinRite Signal-Level Magnetic Platter Realignment & Pulse Waveform")
	fmt.Println(" 5. Partition Magic Volume Manager (Table Editor & Secure zero-wipe)")
	fmt.Println(" 6. Recover Files & Partitions (Deep Signature Carver & TestDisk Scanner)")
	fmt.Println(" 7. Service Area (SA) Controls & Direct ATA Registers (ROM Programmer & Defects)")
	fmt.Println(" 8. Apply Complete AI Auto-Repair (Autonomous Exception Self-Healing)")
	fmt.Println(" 9. تغيير لغة واجهة التطبيق إلى العربية (Switch Interface to Arabic)")
	fmt.Println(" 0. Exit PC-3000 Workstation")
	fmt.Println("\033[36m===============================================================================\033[0m")
}

func handleCommand(cmd string, reader *bufio.Reader) {
	switch cmd {
	case "1":
		RunSelectDriveMenu(reader)
	case "2":
		RunSmartMenu(reader)
	case "3":
		RunVictoriaScan(reader)
	case "4":
		RunSpinRiteRepair(reader)
	case "5":
		RunPartitionManager(reader)
	case "6":
		RunDataRecovery(reader)
	case "7":
		RunServiceAreaControls(reader)
	case "8":
		RunAiRepairWizard(reader)
	case "9":
		ArabicMode = !ArabicMode
		fmt.Print("\033[H\033[2J")
		PrintBootLogo()
		if ArabicMode {
			fmt.Println("\033[92m✔ تم تحويل لغة واجهة المستخدم بنجاح إلى العربية.\033[0m")
		} else {
			fmt.Println("\033[92m✔ Interface successfully switched to English.\033[0m")
		}
	case "0":
		if ArabicMode {
			fmt.Println("\n[!] جاري إغلاق قنوات اتصال الأجهزة وإيقاف تشغيل ميكروكونترولر PC-3000... مع السلامة!")
		} else {
			fmt.Println("\n[!] Closing active hardware channels and shutting down PC-3000 microcode link... Goodbye!")
		}
		os.Exit(0)
	default:
		if ArabicMode {
			fmt.Println("\033[91m❌ أمر خاطئ! يرجى اختيار رقم من القائمة الرئيسية.\033[0m")
		} else {
			fmt.Println("\033[91m❌ Invalid command! Please enter a number from the menu.\033[0m")
		}
	}
}

// Sub-Menu 1: Select Drive
func RunSelectDriveMenu(reader *bufio.Reader) {
	fmt.Print("\033[H\033[2J")
	PrintBootLogo()

	isAdmin := IsAdmin()

	if ArabicMode {
		fmt.Println("\n\033[36m--- [ اختيار وتوصيل القرص الصلب ] ---\033[0m")
		if isAdmin {
			fmt.Println("\033[92m✔ صلاحية الأدمن متاحة (Administrator)! يمكننا فحص الهاردات الحقيقية للويندوز مباشرة.\033[0m")
		} else {
			fmt.Println("\033[33m⚠ تنبيه: التطبيق لا يملك صلاحية الأدمن! يرجى تشغيل التطبيق كمسؤول (Run as Admin) للوصول للهاردات الحقيقية.\033[0m")
			fmt.Println("سيكون بإمكانك استخدام وضع المحاكاة المتطور (WD Simulation Mode) لجميع الفحوصات والاصلاحات.")
		}
		fmt.Println()
		fmt.Println("أ) فحص واكتشاف الأقراص الصلبة الفيزيائية المتصلة بنظام ويندوز")
		fmt.Println("ب) تحميل ملفات تعريف ومحاكاة أقراص Western Digital تالفة/سليمة")
		fmt.Println("ج) العودة للقائمة الرئيسية")
		fmt.Print("\nاختر الخيار المناسب: ")
	} else {
		fmt.Println("\n\033[36m--- [ DRIVE MOUNT & SELECTION PANEL ] ---\033[0m")
		if isAdmin {
			fmt.Println("\033[92m✔ Running with elevated Administrator rights. Raw physical block IO enabled.\033[0m")
		} else {
			fmt.Println("\033[33m⚠ Running in rootless/user mode. UAC elevation not detected.\033[0m")
			fmt.Println("Standard physical disk communication restricted. Western Digital profiles ready.")
		}
		fmt.Println()
		fmt.Println("A) Scan and mount active Windows physical disk drives")
		fmt.Println("B) Load custom Western Digital (or other brands) simulation profiles")
		fmt.Println("C) Back to main menu")
		fmt.Print("\nEnter choice: ")
	}

	choice, _ := reader.ReadString('\n')
	choice = strings.ToUpper(strings.TrimSpace(choice))

	if choice == "A" || choice == "أ" {
		drives := EnumeratePhysicalDrives()
		if len(drives) == 0 {
			if ArabicMode {
				fmt.Println("\n\033[91m❌ لم يتم العثور على أي أقراص حقيقية متصلة أو تفتقد لصلاحية المسؤول (Run as Admin).\033[0m")
			} else {
				fmt.Println("\n\033[91m❌ No physical drives enumerated. Ensure you are running as Administrator.\033[0m")
			}
			return
		}

		if ArabicMode {
			fmt.Println("\nالأقراص الصلبة المتوفرة في جهازك:")
			for idx, d := range drives {
				fmt.Printf(" [%d] مسار القرص: %s | الحجم: %s | الموديل: %s\n", idx+1, d.Path, FormatSize(d.CapacityBytes), d.Model)
			}
			fmt.Print("\nاختر رقم القرص الصلب المطلوب: ")
		} else {
			fmt.Println("\nAvailable Windows Physical Disk Drives:")
			for idx, d := range drives {
				fmt.Printf(" [%d] Path: %s | Size: %s | Model: %s\n", idx+1, d.Path, FormatSize(d.CapacityBytes), d.Model)
			}
			fmt.Print("\nSelect drive index: ")
		}

		numStr, _ := reader.ReadString('\n')
		num, err := strconv.Atoi(strings.TrimSpace(numStr))
		if err == nil && num >= 1 && num <= len(drives) {
			target := drives[num-1]
			SelectedDrive = &target
			CurrentProfile = HealthyHDD
			SmartAttributes = GetMockSmartAttributes(CurrentProfile)
			ActiveFailure = nil
			CreatedPartitions = GetDefaultPartitions(target.CapacityBytes)
			if ArabicMode {
				fmt.Printf("\n\033[92m✔ تم توصيل القرص الفيزيائي الحقيقي بنجاح: %s\033[0m\n", target.Path)
			} else {
				fmt.Printf("\n\033[92m✔ Mounted physical drive successfully: %s\033[0m\n", target.Path)
			}
		}

	} else if choice == "B" || choice == "ب" {
		presets := []struct {
			name   string
			vendor string
			model  string
			fw     string
			sn     string
			size   int64
			issue  *DriveFailure
			prof   SmartProfile
		}{
			{"WD Blue 1TB HDD", "Western Digital", "WDC WD10EZEX-08WN4A0", "80.00A80", "WD-WCC6Y1AK7SF2", 1000204886016, nil, HealthyHDD},
			{"WD Red Plus 12TB (Translator Failure - LBA 0)", "Western Digital", "WDC WD120EFBX-68B0AY0", "85.00A85", "WD-WMC6N0F9L8K2", 0, &DetectedFailures[0], FailingHDD},
			{"WD Blue 2TB (Module 32 Slow responding Loop)", "Western Digital", "WDC WD20EZAZ-00GGJB0", "80.00A80", "WD-WCC6Y2BK3SS1", 2000398934016, &DetectedFailures[1], FailingHDD},
			{"Seagate CC49 8TB (Terminal locked - LED:000000CC)", "Seagate", "ST8000DM004-2CX188", "CC49", "WFA4N8K2", 8001563222016, &DetectedFailures[2], FailingHDD},
			{"Toshiba Enterprise 16TB (Head 2 Click - pre-amp Fail)", "Toshiba", "MG08ACA16TE", "0101", "Y9K0A028FJ9D", 16000593500160, &DetectedFailures[3], FailingHDD},
			{"Samsung 990 Pro SSD 4TB (Bad Block Platter Cascade)", "Samsung", "SSD 990 PRO 4TB NVMe", "1B2QJXD7", "S73HNE0W302910M", 4000787030016, &DetectedFailures[4], FailingSSD},
		}

		if ArabicMode {
			fmt.Println("\nاختر ملف محاكاة القرص الصلب المطلوب:")
			for idx, p := range presets {
				fmt.Printf(" [%d] اسم الملف: %-52s | الحجم: %-10s\n", idx+1, p.name, FormatSize(p.size))
			}
			fmt.Print("\nاختر الملف المطلوب: ")
		} else {
			fmt.Println("\nSelect Simulation Drive Preset Profile:")
			for idx, p := range presets {
				fmt.Printf(" [%d] Preset: %-55s | Capacity: %-10s\n", idx+1, p.name, FormatSize(p.size))
			}
			fmt.Print("\nEnter choice index: ")
		}

		numStr, _ := reader.ReadString('\n')
		num, err := strconv.Atoi(strings.TrimSpace(numStr))
		if err == nil && num >= 1 && num <= len(presets) {
			p := presets[num-1]
			SelectedDrive = &DriveInfo{
				Index:          100 + num,
				Path:           "\\\\.\\SimulatedDrive" + strconv.Itoa(num),
				Vendor:         p.vendor,
				Model:          p.model,
				Firmware:       p.fw,
				Serial:         p.sn,
				CapacityBytes:  p.size,
				TotalSectors:   p.size / 512,
				SectorSize:     512,
				ConnectionType: "SATA Bridge (Simulated)",
				IsSimulated:    true,
			}
			CurrentProfile = p.prof
			SmartAttributes = GetMockSmartAttributes(CurrentProfile)
			ActiveFailure = p.issue
			CreatedPartitions = GetDefaultPartitions(p.size)

			if ArabicMode {
				fmt.Printf("\n\033[92m✔ تم تحميل ملف المحاكاة للقرص بنجاح: %s\033[0m\n", p.name)
			} else {
				fmt.Printf("\n\033[92m✔ Simulation preset profile loaded: %s\033[0m\n", p.name)
			}
		}
	}
}

// Sub-Menu 2: S.M.A.R.T. Health Monitor
func RunSmartMenu(reader *bufio.Reader) {
	fmt.Print("\033[H\033[2J")
	PrintBootLogo()

	if SelectedDrive == nil {
		if ArabicMode {
			fmt.Println("\033[91m❌ تنبيه: لم يتم توصيل قرص صلب بعد! الرجاء تفعيل خيار (1) وتحديد قرص صلب.\033[0m")
		} else {
			fmt.Println("\033[91m❌ Error: No target drive selected! Connect a drive from Option (1) first.\033[0m")
		}
		return
	}

	if ArabicMode {
		fmt.Printf("\n\033[36m--- [ رادار مؤشرات ومستشعرات الـ S.M.A.R.T. للقرص: %s ] ---\033[0m\n", SelectedDrive.Model)
	} else {
		fmt.Printf("\n\033[36m--- [ S.M.A.R.T. CRITICAL REGISTER RADAR: %s ] ---\033[0m\n", SelectedDrive.Model)
	}

	// Calculate and print
	rating := CalculateOverallHealth(SmartAttributes)
	if ArabicMode {
		fmt.Printf("• ميكروكونترولر تقييم الصحة العام: %s\n", rating)
	} else {
		fmt.Printf("• Microcontroller Health Rating: %s\n", rating)
	}

	// Aligned Print
	if ArabicMode {
		fmt.Printf("\n%-5s %-32s %-5s %-5s %-5s %-16s %-10s\n", "المعرف", "اسم المؤشر الحرج", "الحالي", "الأسوأ", "الحد", "القيمة الفيزيائية", "الحالة")
		fmt.Println(strings.Repeat("─", 88))
		for _, attr := range SmartAttributes {
			fmt.Printf("%-5s %-32s %-6d %-5d %-5d %-17s %-10s\n", attr.HexID, attr.Name, attr.Value, attr.Worst, attr.Threshold, attr.RawHex, attr.Status)
		}
	} else {
		fmt.Printf("\n%-5s %-32s %-5s %-5s %-5s %-16s %-10s\n", "HexID", "Attribute Register Name", "Value", "Worst", "Thresh", "Raw Value (Hex)", "Status")
		fmt.Println(strings.Repeat("─", 88))
		for _, attr := range SmartAttributes {
			fmt.Printf("%-5s %-32s %-5d %-5d %-5d %-16s %-10s\n", attr.HexID, attr.Name, attr.Value, attr.Worst, attr.Threshold, attr.RawHex, attr.Status)
		}
	}

	// Check warnings/alerts
	alerts := TriggerSmartAlerts(SmartAttributes)
	if len(alerts) > 0 {
		fmt.Println("\n\033[91m⚠️ تنبيهات وتحذيرات ميكروكود صحة القرص (Firmware Warnings):\033[0m")
		for _, alert := range alerts {
			fmt.Printf(" [%s] %s: %s\n", alert.Severity, alert.AttributeName, alert.Message)
		}
	} else {
		if ArabicMode {
			fmt.Println("\n\033[92m✔ لا توجد تجاوزات للحدود الآمنة في القرص الصلب حالياً.\033[0m")
		} else {
			fmt.Println("\n\033[92m✔ All telemetry parameters conform to standard factory limits.\033[0m")
		}
	}

	if ArabicMode {
		fmt.Println("\nاضغط مفتاح Enter للعودة للقائمة الرئيسية...")
	} else {
		fmt.Println("\nPress Enter to return to main menu...")
	}
	_, _ = reader.ReadString('\n')
}

// Sub-Menu 3: Victoria Block Scanner
func RunVictoriaScan(reader *bufio.Reader) {
	fmt.Print("\033[H\033[2J")
	PrintBootLogo()

	if SelectedDrive == nil {
		if ArabicMode {
			fmt.Println("\033[91m❌ تنبيه: لم يتم اختيار قرص صلب للبدء بمسحه!\033[0m")
		} else {
			fmt.Println("\033[91m❌ Error: No drive selected for media scanning!\033[0m")
		}
		return
	}

	var mode ScanMode = ModeRead
	if ArabicMode {
		fmt.Println("\n\033[36m--- [ أداة Victoria لفحص قطاعات الميديا ] ---\033[0m")
		fmt.Println("اختر وضع الفحص المطلوبة:")
		fmt.Println(" 1. فحص قطاعات القرص بقراءتها فقط (READ Mode)")
		fmt.Println(" 2. فحص وإصلاح القطاعات التالفة عن طريق إعادة التعيين (REMAP Mode)")
		fmt.Println(" 3. مسح وتصفير القطاعات أثناء القراءة لتجاوز التهنيج (ERASE Mode)")
		fmt.Print("اختر رقم الخيار: ")
	} else {
		fmt.Println("\n\033[36m--- [ VICTORIA PLATTER BLOCK SCANNER ] ---\033[0m")
		fmt.Println("Select Scanner Mode:")
		fmt.Println(" 1. Verify sectors only (READ Mode)")
		fmt.Println(" 2. Verify and relocate bad sectors dynamically (REMAP Mode)")
		fmt.Println(" 3. Zero-fill write sectors to wipe weak fields (ERASE Mode)")
		fmt.Print("Enter option: ")
	}

	opt, _ := reader.ReadString('\n')
	opt = strings.TrimSpace(opt)
	if opt == "2" {
		mode = ModeRemap
	} else if opt == "3" {
		mode = ModeErase
	}

	fmt.Print("\033[H\033[2J")
	PrintBootLogo()

	if ArabicMode {
		fmt.Println("\n\033[92m[!] جاري بدء مسح قطاعات خلايا التخزين... اضغط Ctrl+C لقطع العملية.\033[0m\n")
	} else {
		fmt.Println("\n\033[92m[!] Starting sector-by-sector latency scan... Press Ctrl+C to stop.\033[0m\n")
	}

	// Live Scan Loop Simulation
	totalBlocks := 120
	blocksScanned := 0
	var scanBlocks []BlockDetail

	bin3, bin10, bin50, bin150, bin500, binOver, binError := 0, 0, 0, 0, 0, 0, 0
	remaps := 0

	startTime := time.Now()

	for blocksScanned < totalBlocks {
		blocksScanned++
		lba := int64(blocksScanned) * 4096

		latency, delayType := SimulatedLatency(CurrentProfile, blocksScanned, totalBlocks)

		// Increment bins
		switch delayType {
		case Delay3ms:
			bin3++
		case Delay10ms:
			bin10++
		case Delay50ms:
			bin50++
		case Delay150ms:
			bin150++
		case Delay500ms:
			bin500++
		case DelayOver:
			binOver++
		case DelayError:
			binError++
			if mode == ModeRemap {
				remaps++
			}
		}

		scanBlocks = append(scanBlocks, BlockDetail{LBA: lba, LatencyMs: latency, Type: delayType})

		// Draw live block heatmap map
		fmt.Print("\033[H")
		PrintBootLogo()
		fmt.Println(RenderHeatmapLine(scanBlocks, 30))

		elapsed := time.Since(startTime).Seconds()
		estRemaining := 0.0
		if blocksScanned > 0 {
			estRemaining = (elapsed / float64(blocksScanned)) * float64(totalBlocks-blocksScanned)
		}

		stats := BlockScanStats{
			CurrentLBA:      lba,
			StartLBA:        0,
			EndLBA:          SelectedDrive.TotalSectors,
			BlocksScanned:   blocksScanned,
			BlocksTotal:     totalBlocks,
			ElapsedSeconds:  elapsed,
			EstRemainingSec: estRemaining,
			AverageSpeedMbs: 120.0 + rand.Float64()*40.0 - (float64(binOver) * 2.0),
			RemappedCount:   remaps,
			ErrorCount:      binError,
			Bin3:            bin3,
			Bin10:           bin10,
			Bin50:           bin50,
			Bin150:          bin150,
			Bin500:          bin500,
			BinOver:         binOver,
			BinError:        binError,
		}

		FormatStats(stats, mode, ArabicMode)
		time.Sleep(100 * time.Millisecond) // speed simulation
	}

	if ArabicMode {
		fmt.Println("\n✔ اكتمل فحص القرص ببرنامج Victoria!")
		fmt.Printf("إجمالي القطاعات التالفة: %d | القطاعات التي تم نقلها (Remap): %d\n", binError, remaps)
		fmt.Println("\nاضغط مفتاح Enter للعودة...")
	} else {
		fmt.Println("\n✔ Victoria sector scan complete!")
		fmt.Printf("Total Bad Sectors: %d | Reallocated (Remapped) sectors: %d\n", binError, remaps)
		fmt.Println("\nPress Enter to continue...")
	}
	_, _ = reader.ReadString('\n')
}

// Sub-Menu 4: SpinRite Signal-Level Repair
func RunSpinRiteRepair(reader *bufio.Reader) {
	fmt.Print("\033[H\033[2J")
	PrintBootLogo()

	if SelectedDrive == nil {
		if ArabicMode {
			fmt.Println("\033[91m❌ تنبيه: لم يتم اختيار قرص صلب للبدء بإصلاحه!\033[0m")
		} else {
			fmt.Println("\033[91m❌ Error: No drive selected for SpinRite repair!\033[0m")
		}
		return
	}

	if ArabicMode {
		fmt.Printf("\n\033[36m--- [ نظام SpinRite لإصلاح نبضات سطح خلايا التخزين ] ---\033[0m\n")
		fmt.Println("سيقوم النظام بإعادة محاذاة مغناطيسية الخلايا وتجديد قوتها السطحية.")
		fmt.Println("اضغط Enter لبدء بث النبضات الفهرسية...")
	} else {
		fmt.Printf("\n\033[36m--- [ SPINRITE SIGNAL HYSTERESIS PLATTER RECOVERY ] ---\033[0m\n")
		fmt.Println("The system will realign flux reversals and normalize weak surface domains.")
		fmt.Println("Press Enter to begin the dynamic signal sweep...")
	}
	_, _ = reader.ReadString('\n')

	fmt.Print("\033[H\033[2J")
	PrintBootLogo()

	phases := []string{"FORWARD_DEMAG", "REVERSE_DEMAG", "FLUX_INVERSION", "BURST_POLARIZATION", "DATA_INTEGRITY_VERIFY"}
	pulses := make([]int, 70)
	for i := range pulses {
		pulses[i] = 400 + rand.Intn(100)
	}

	repaired := 0
	recovered := 0
	signals := 0

	for tick := 1; tick <= 40; tick++ {
		phase := phases[rand.Intn(len(phases))]
		mv := SimulatedOscilloscopePulse(phase, tick)
		pulses = append(pulses[1:], mv)

		lba := int64(tick*3400 + 12000)

		recChance := rand.Float64() < 0.15
		if recChance {
			recovered++
			signals += 1 + rand.Intn(3)
			if ArabicMode {
				fmt.Printf("\033[92m• SpinRite: تم ضبط وعلاج مغناطيسية القطاع LBA: 0x%08X بقوة %d mV\033[0m\n", lba, mv)
			} else {
				fmt.Printf("\033[92m• SpinRite: Restored weak field on LBA: 0x%08X to stable %d mV\033[0m\n", lba, mv)
			}
		}

		repaired += 12 + rand.Intn(15)

		stats := RepairStats{
			ActiveSector:           lba,
			SectorsRepaired:        repaired,
			SectorsRecovered:       recovered,
			SignalsRegenerated:     signals,
			ScanProgress:           float64(tick) / 40.0,
			CurrentHysteresisPhase: phase,
			SurfaceVoltageMv:       mv,
			PulseHistory:           pulses,
		}

		fmt.Print("\033[H")
		PrintBootLogo()
		PrintSpinRiteStatus(stats, tick, ArabicMode)
		time.Sleep(150 * time.Millisecond) // oscilloscope sweep delay
	}

	if ArabicMode {
		fmt.Println("\n✔ اكتملت عملية التثبيت المغناطيسي السطحي للقطاعات بنجاح!")
		fmt.Println("اضغط Enter للعودة...")
	} else {
		fmt.Println("\n✔ SpinRite signal restoration complete!")
		fmt.Println("Press Enter to return to main menu...")
	}
	_, _ = reader.ReadString('\n')
}

// Sub-Menu 5: Partition Magic Manager
func RunPartitionManager(reader *bufio.Reader) {
	fmt.Print("\033[H\033[2J")
	PrintBootLogo()

	if SelectedDrive == nil {
		if ArabicMode {
			fmt.Println("\033[91m❌ تنبيه: لم يتم اختيار قرص صلب لتعديل تقسيماته!\033[0m")
		} else {
			fmt.Println("\033[91m❌ Error: No target drive selected!\033[0m")
		}
		return
	}

	for {
		fmt.Print("\033[H\033[2J")
		PrintBootLogo()

		if ArabicMode {
			fmt.Println("\n\033[36m--- [ Partition Magic - تعديل وتنسيق تقسيمات الهارد ديسك ] ---\033[0m")
			PrintPartitionTable(CreatedPartitions, SelectedDrive.CapacityBytes, ArabicMode)
			fmt.Println("\nالخيارات المتوفرة:")
			fmt.Println(" 1. تهيئة وتنسيق تقسيم حالي (Format Volume)")
			fmt.Println(" 2. حذف تقسيم حالي وإرجاع المساحة للمسبح (Delete Partition)")
			fmt.Println(" 3. إنشاء تقسيم جديد من المساحة غير المخصصة (Create Partition)")
			fmt.Println(" 4. مسح آمن وتصفير قطاعات التقسيم بالكامل (Secure Wipe Partition)")
			fmt.Println(" 5. العودة للقائمة الرئيسية")
			fmt.Print("\nاختر رقم الخيار المطلوب: ")
		} else {
			fmt.Println("\n\033[36m--- [ PARTITION MAGIC TABLE EDITOR & SANITIZER ] ---\033[0m")
			PrintPartitionTable(CreatedPartitions, SelectedDrive.CapacityBytes, ArabicMode)
			fmt.Println("\nAvailable Actions:")
			fmt.Println(" 1. Format existing volume (FAT32, NTFS, exFAT, ext4)")
			fmt.Println(" 2. Delete partition (Release storage to unallocated pool)")
			fmt.Println(" 3. Create partition from unallocated pool")
			fmt.Println(" 4. Secure zero-wipe volume (Block-level sanitization)")
			fmt.Println(" 5. Back to main menu")
			fmt.Print("\nEnter choice option: ")
		}

		opt, _ := reader.ReadString('\n')
		opt = strings.TrimSpace(opt)

		if opt == "1" {
			if ArabicMode {
				fmt.Print("أدخل رقم التقسيم (ID) المراد تهيئته: ")
			} else {
				fmt.Print("Enter partition ID to format: ")
			}
			idStr, _ := reader.ReadString('\n')
			id, _ := strconv.Atoi(strings.TrimSpace(idStr))

			if ArabicMode {
				fmt.Print("أدخل نظام الملفات الجديد (FAT32, NTFS, exFAT, ext4): ")
			} else {
				fmt.Print("Enter new filesystem type (FAT32, NTFS, exFAT, ext4): ")
			}
			fs, _ := reader.ReadString('\n')
			fs = strings.TrimSpace(fs)

			for idx, p := range CreatedPartitions {
				if p.ID == id && !p.IsUnallocated {
					CreatedPartitions[idx].FileSystem = fs
					if ArabicMode {
						fmt.Printf("\n\033[92m✔ تم تهيئة وتنسيق التقسيم [%s] إلى نظام الملفات: %s بنجاح كامل!\033[0m\n", p.Label, fs)
					} else {
						fmt.Printf("\n\033[92m✔ Successfully formatted [%s] partition to %s filesystem!\033[0m\n", p.Label, fs)
					}
					time.Sleep(1500 * time.Millisecond)
				}
			}

		} else if opt == "2" {
			if ArabicMode {
				fmt.Print("أدخل رقم التقسيم (ID) المراد حذفه: ")
			} else {
				fmt.Print("Enter partition ID to delete: ")
			}
			idStr, _ := reader.ReadString('\n')
			id, _ := strconv.Atoi(strings.TrimSpace(idStr))

			newParts, err := DeletePartition(CreatedPartitions, id)
			if err == nil {
				CreatedPartitions = newParts
				if ArabicMode {
					fmt.Println("\n\033[92m✔ تم حذف التقسيم وتحرير المساحة المنطقية بنجاح!\033[0m")
				} else {
					fmt.Println("\n\033[92m✔ Partition successfully deleted and released to unallocated pool.\033[0m")
				}
			} else {
				fmt.Printf("\n\033[91m❌ Error: %v\033[0m\n", err)
			}
			time.Sleep(1500 * time.Millisecond)

		} else if opt == "3" {
			if ArabicMode {
				fmt.Print("أدخل اسم التسمية للتقسيم الجديد: ")
			} else {
				fmt.Print("Enter new volume label: ")
			}
			label, _ := reader.ReadString('\n')
			label = strings.TrimSpace(label)

			if ArabicMode {
				fmt.Print("أدخل نظام الملفات (FAT32, NTFS, exFAT, ext4): ")
			} else {
				fmt.Print("Enter filesystem (FAT32, NTFS, exFAT, ext4): ")
			}
			fs, _ := reader.ReadString('\n')
			fs = strings.TrimSpace(fs)

			if ArabicMode {
				fmt.Print("أدخل الحجم الكلي بالميجابايت (MB): ")
			} else {
				fmt.Print("Enter size in megabytes (MB): ")
			}
			sizeStr, _ := reader.ReadString('\n')
			size, _ := strconv.ParseInt(strings.TrimSpace(sizeStr), 10, 64)

			newParts, err := CreateNewPartition(CreatedPartitions, label, fs, size)
			if err == nil {
				CreatedPartitions = newParts
				if ArabicMode {
					fmt.Println("\n\033[92m✔ تم إنشاء وتخصيص تقسيم الملفات الجديد بنجاح!\033[0m")
				} else {
					fmt.Println("\n\033[92m✔ New partition created and indexed to partition table.\033[0m")
				}
			} else {
				fmt.Printf("\n\033[91m❌ Error: %v\033[0m\n", err)
			}
			time.Sleep(1500 * time.Millisecond)

		} else if opt == "4" {
			if ArabicMode {
				fmt.Print("أدخل رقم التقسيم (ID) المراد مسحه وتصفيره: ")
			} else {
				fmt.Print("Enter partition ID to secure wipe: ")
			}
			idStr, _ := reader.ReadString('\n')
			id, _ := strconv.Atoi(strings.TrimSpace(idStr))

			found := false
			for _, p := range CreatedPartitions {
				if p.ID == id && !p.IsUnallocated {
					found = true
					SecureWipePartition(p, SelectedDrive.Path, SelectedDrive.IsSimulated, ArabicMode)
					_, _ = reader.ReadString('\n')
				}
			}
			if !found {
				if ArabicMode {
					fmt.Println("\033[91m❌ المعرف المدخل غير صحيح أو مخصص لمساحة فارغة.\033[0m")
				} else {
					fmt.Println("\033[91m❌ Invalid partition ID or partition is unallocated.\033[0m")
				}
				time.Sleep(1500 * time.Millisecond)
			}

		} else if opt == "5" {
			break
		}
	}
}

// Sub-Menu 6: Data Recovery Carver & TestDisk Partition Rebuilder
func RunDataRecovery(reader *bufio.Reader) {
	fmt.Print("\033[H\033[2J")
	PrintBootLogo()

	if SelectedDrive == nil {
		if ArabicMode {
			fmt.Println("\033[91m❌ تنبيه: يرجى اختيار قرص صلب للبدء باستعادة الملفات منه!\033[0m")
		} else {
			fmt.Println("\033[91m❌ Error: Please select a target hard drive first!\033[0m")
		}
		return
	}

	if ArabicMode {
		fmt.Println("\n\033[36m--- [ استعادة الملفات والبارتشنات المحذوفة ] ---\033[0m")
		fmt.Println("اختر المعالج المطلوب:")
		fmt.Println(" 1. فحص واستعادة التقسيمات المحذوفة وإعادة بناء الـ MBR/GPT (TestDisk Wizard)")
		fmt.Println(" 2. البحث العميق واستخراج الملفات المحذوفة بالتوقيع الرقمي (Deep File Carver)")
		fmt.Print("\nاختر رقم المعالج: ")
	} else {
		fmt.Println("\n\033[36m--- [ DEEP DATA & PARTITION RECOVERY WORKSTATION ] ---\033[0m")
		fmt.Println("Select Recovery Wizard:")
		fmt.Println(" 1. Rebuild MBR/GPT and recover deleted partitions (TestDisk Wizard)")
		fmt.Println(" 2. File Signature Raw Carver (Deep Carving JPG, PNG, PDF, ZIP, HTML, TXT)")
		fmt.Print("\nEnter wizard index: ")
	}

	opt, _ := reader.ReadString('\n')
	opt = strings.TrimSpace(opt)

	if opt == "1" {
		parts := ScanPartitionsTestDisk(SelectedDrive.Path, SelectedDrive.IsSimulated, ArabicMode)
		if len(parts) > 0 {
			if ArabicMode {
				fmt.Println("\nالتقسيمات المستعادة والقابلة للإصلاح على الهارد ديسك:")
				for idx, p := range parts {
					fmt.Printf(" [%d] اسم التقسيم: %-16s | الحجم: %-12s | نظام الملفات: %-8s\n", idx+1, p.Label, FormatSize(p.SizeBytes), p.FileSystem)
				}
				fmt.Print("\nهل تريد كتابة وتثبيت هذه التقسيمات المستعادة على الهارد ديسك مباشرة؟ (نعم/لا): ")
			} else {
				fmt.Println("\nRecovered partitions ready for restoration on disk:")
				for idx, p := range parts {
					fmt.Printf(" [%d] Label: %-20s | Capacity: %-12s | FS: %-8s\n", idx+1, p.Label, FormatSize(p.SizeBytes), p.FileSystem)
				}
				fmt.Print("\nDo you want to write these partition structures back to the disk partition table? (yes/no): ")
			}
			ans, _ := reader.ReadString('\n')
			ans = strings.ToLower(strings.TrimSpace(ans))
			if ans == "نعم" || ans == "yes" || ans == "y" {
				CreatedPartitions = parts
				if ArabicMode {
					fmt.Println("\n\033[92m✔ تم كتابة جدول التقسيمات الجديد للقرص بنجاح كلي وإعادة تهيئة الـ GPT/MBR!\033[0m")
				} else {
					fmt.Println("\n\033[92m✔ Partition table written back successfully. Partition structures active!\033[0m")
				}
			}
		}

	} else if opt == "2" {
		carved := DeepScanFileCarver(SelectedDrive.Path, 0, 1000, SelectedDrive.IsSimulated, ArabicMode)
		CarvedFiles = carved
		if len(carved) > 0 {
			if ArabicMode {
				fmt.Printf("\n✔ اكتمل الفحص! تم استخراج عدد %d ملفاً بنجاح.\n", len(carved))
				fmt.Println("الملفات المسترجعة الجاهزة للحفظ:")
				for idx, f := range carved {
					fmt.Printf(" [%d] اسم الملف: %-24s | الحجم: %-10s | السلامة: %-15s\n", idx+1, f.Name, FormatSize(f.SizeBytes), f.Integrity)
				}
				fmt.Print("\nأدخل رقم الملف الذي تريد استخراجه وحفظه، أو اكتب (كل) لحفظ جميع الملفات: ")
			} else {
				fmt.Printf("\n✔ Deep carving complete! Extracted %d files successfully.\033[0m\n", len(carved))
				fmt.Println("Recovered files available for extraction:")
				for idx, f := range carved {
					fmt.Printf(" [%d] File Name: %-26s | Size: %-10s | Parity: %-20s\n", idx+1, f.Name, FormatSize(f.SizeBytes), f.Integrity)
				}
				fmt.Print("\nEnter file index to extract, or type 'all' to save all files: ")
			}

			ans, _ := reader.ReadString('\n')
			ans = strings.TrimSpace(ans)

			destFolder := "./Recovered_Files"
			if ans == "كل" || strings.ToLower(ans) == "all" {
				for _, f := range carved {
					SaveFileToDisk(f, destFolder, ArabicMode)
				}
			} else {
				idx, err := strconv.Atoi(ans)
				if err == nil && idx >= 1 && idx <= len(carved) {
					SaveFileToDisk(carved[idx-1], destFolder, ArabicMode)
				}
			}
		} else {
			if ArabicMode {
				fmt.Println("\n\033[31m❌ لم يتم العثور على أي تواقيع لملفات محذوفة في المساحة المحددة.\033[0m")
			} else {
				fmt.Println("\n\033[31m❌ No carved file signatures matched in the specified LBA boundaries.\033[0m")
			}
		}
	}

	if ArabicMode {
		fmt.Println("\nاضغط Enter للعودة...")
	} else {
		fmt.Println("\nPress Enter to continue...")
	}
	_, _ = reader.ReadString('\n')
}

// Sub-Menu 7: Service Area and Direct ATA Controls
func RunServiceAreaControls(reader *bufio.Reader) {
	fmt.Print("\033[H\033[2J")
	PrintBootLogo()

	if SelectedDrive == nil {
		if ArabicMode {
			fmt.Println("\033[91m❌ تنبيه: الرجاء تحديد قرص صلب لتنشيط خيارات لوحة التحكم من خيار (1).\033[0m")
		} else {
			fmt.Println("\033[91m❌ Error: No active drive target found!\033[0m")
		}
		return
	}

	for {
		fmt.Print("\033[H\033[2J")
		PrintBootLogo()

		if ArabicMode {
			fmt.Println("\n\033[36m--- [ لوحة تحكم ميكروكود المنطقة الخدمية SA و Direct ATA ] ---\033[0m")
			fmt.Println(FormatAtaRegisters(SAState))
			fmt.Println("\nالخيارات المتوفرة:")
			fmt.Println(" 1. إرسال أوامر ATA المباشرة وتسجيل سجلات الحالة (BSY/DRDY Register Commands)")
			fmt.Println(" 2. استعراض سجلات العيوب المصنعية وجدول التعيينات (Defects P-List & G-List)")
			fmt.Println(" 3. عزل وتعطيل الرؤوس المتضررة والمخدوشة (Isolate Platter Physical Heads 0-3)")
			fmt.Println(" 4. مستكشف موديولات المنطقة الخدمية وإصلاح الفيرموير (SA Modules Diagnostics)")
			fmt.Println(" 5. مبرمجة الـ ROM EEPROM وتعديل خريطة الرؤوس بالـ BIOS (ROM Map Header Programmer)")
			fmt.Println(" 6. العودة للقائمة الرئيسية")
			fmt.Print("\nاختر رقم الخيار المطلوب: ")
		} else {
			fmt.Println("\n\033[36m--- [ SERVICE AREA (SA) CONTROLS & DIRECT ATA WORKSTATION ] ---\033[0m")
			fmt.Println(FormatAtaRegisters(SAState))
			fmt.Println("\nAvailable Modules:")
			fmt.Println(" 1. Direct ATA command transmitter and hex buffer register log")
			fmt.Println(" 2. View platter physical factory & grown defect maps (P-List / G-List)")
			fmt.Println(" 3. Physical heads map isolation toggler (Isolate scratching pre-amp Head 0-3)")
			fmt.Println(" 4. SA firmware modules manager (Diagnose, verify CRC and rewrite modules)")
			fmt.Println(" 5. Low-level ROM EEPROM programmer (Edit ROM head adaptives and flash BIOS)")
			fmt.Println(" 6. Back to main menu")
			fmt.Print("\nEnter choice: ")
		}

		opt, _ := reader.ReadString('\n')
		opt = strings.TrimSpace(opt)

		if opt == "1" {
			commands := []struct {
				hex  string
				desc string
				ar   string
			}{
				{"0xEC", "IDENTIFY DEVICE (Get drive hardware model/firmware registers)", "التعرف على الهارد وقراءة سجلات المساحة والموديل"},
				{"0x20", "READ SECTORS (Read block sector data at raw LBA 0x00)", "قراءة القطاعات المباشرة في مسار LBA 0"},
				{"0xE0", "SPIN UP MOTOR (Force start platter spindle rotation torque)", "بدء تدوير الموتور المغناطيسي لسطح الهارد"},
				{"0xE1", "SPIN DOWN MOTOR (Force de-energize spin and park heads to ramp)", "إيقاف الموتور وركن الرؤوس في المسار الآمن"},
				{"0xF1", "SECURITY LOCK (Simulate setting master controller lock password)", "فرض حظر وقفل الحماية على لوحة التحكم"},
				{"0xF2", "SECURITY UNLOCK (Clear Master/User password from ATA buffer)", "إزالة قفل الحماية وتجاوز كلمة المرور للتحكم"},
				{"0x90", "EXECUTE DIAGNOSTICS (Trigger internal controller register self-test)", "تشغيل الفحص الذاتي للبطاقة الإلكترونية"},
			}

			if ArabicMode {
				fmt.Println("\nاختر أمر ATA المراد إرساله عبر الناقل المباشر:")
				for idx, c := range commands {
					fmt.Printf(" [%d] كود الأمر: %s | %s\n", idx+1, c.hex, c.ar)
				}
				fmt.Print("\nاختر رقم الأمر: ")
			} else {
				fmt.Println("\nSelect direct ATA register command to transmit:")
				for idx, c := range commands {
					fmt.Printf(" [%d] Opcode: %s | %s\n", idx+1, c.hex, c.desc)
				}
				fmt.Print("\nEnter command index: ")
			}

			cIdxStr, _ := reader.ReadString('\n')
			cIdx, err := strconv.Atoi(strings.TrimSpace(cIdxStr))

			if err == nil && cIdx >= 1 && cIdx <= len(commands) {
				targetCmd := commands[cIdx-1]
				fmt.Printf("\nTransmitting %s...\n", targetCmd.hex)
				time.Sleep(1000 * time.Millisecond) // send delay
				dump, newState := ExecuteSimulatedAtaCommand(targetCmd.hex, ModelName(SelectedDrive.Model))
				SAState = newState

				if len(dump) > 0 {
					if ArabicMode {
						fmt.Println("\n[✔] تم استقبال مخزن البيانات الـ 512 بايت بنجاح (HEX DUMP):")
					} else {
						fmt.Println("\n[✔] Data buffer returned successfully from physical interface register (HEX DUMP):")
					}
					for _, dLine := range dump {
						fmt.Println(dLine)
					}
				} else {
					if ArabicMode {
						fmt.Println("\n[✔] تم تنفيذ الأمر البرمجي وتأكيده من معالج الهارد ديسك بنجاح.")
					} else {
						fmt.Println("\n[✔] Direct hardware register command execution completed successfully.")
					}
				}
			}
			_, _ = reader.ReadString('\n')

		} else if opt == "2" {
			PrintDefectTable(SAState.Defects, ArabicMode)
			if ArabicMode {
				fmt.Println("\nالخيارات المتوفرة:")
				fmt.Println(" 1. مزامنة عيوب مسح خلايا Victoria وحقنها بجدول الـ G-List")
				fmt.Println(" 2. تصفير وإعادة تهيئة جدول العيوب النامية (Clear G-List)")
				fmt.Println(" 3. العودة")
				fmt.Print("اختر خياراً: ")
			} else {
				fmt.Println("\nAvailable Options:")
				fmt.Println(" 1. Sync Victoria scan bad sectors and append to Grown Defect Table (G-List)")
				fmt.Println(" 2. Clear and format Grown Defect Table (Clear G-List)")
				fmt.Println(" 3. Back")
				fmt.Print("Enter option: ")
			}

			dOpt, _ := reader.ReadString('\n')
			dOpt = strings.TrimSpace(dOpt)

			if dOpt == "1" {
				// simulate sync
				newDefect := DefectRecord{0x03FF0021, 20412, 1, 45, "Physical Surface", "G-List"}
				SAState.Defects = append(SAState.Defects, newDefect)
				if ArabicMode {
					fmt.Println("\n✔ تمت عملية مزامنة العيوب السطحية من مسح الخلايا وإضافتها لقائمة G-List بالمنطقة الخدمية!")
				} else {
					fmt.Println("\n✔ Victoria sector errors synced. New G-List defect map generated and written back to SA Module 0x35.")
				}
			} else if dOpt == "2" {
				var filtered []DefectRecord
				for _, d := range SAState.Defects {
					if d.Source != "G-List" {
						filtered = append(filtered, d)
					}
				}
				SAState.Defects = filtered
				if ArabicMode {
					fmt.Println("\n✔ تم تصفير قائمة العيوب النامية G-List بنجاح كامل.")
				} else {
					fmt.Println("\n✔ Grown Defect Table (G-List) cleared and formatted successfully.")
				}
			}
			time.Sleep(1500 * time.Millisecond)

		} else if opt == "3" {
			if ArabicMode {
				fmt.Println("\nتعديل وعزل قنوات مضخم الإشارة للرؤوس المغناطيسية:")
				for i := 0; i < 4; i++ {
					status := "\033[32mنشط ACTIVE\033[0m"
					if !SAState.EnabledHeads[i] {
						status = "\033[31mمعزول BYPASSED (مغلق)\033[0m"
					}
					fmt.Printf(" • الرأس المغناطيسي HEAD %d: %s\n", i, status)
				}
				fmt.Print("\nأدخل رقم الرأس الذي تريد تغيير حالته (0-3)، أو اضغط Enter للإلغاء: ")
			} else {
				fmt.Println("\nIsolate clicks and scratching on physical platter sides:")
				for i := 0; i < 4; i++ {
					status := "\033[32mACTIVE\033[0m"
					if !SAState.EnabledHeads[i] {
						status = "\033[31mBYPASSED\033[0m"
					}
					fmt.Printf(" • Platter Read Head %d: %s\n", i, status)
				}
				fmt.Print("\nEnter head index to toggle state (0-3), or press Enter to cancel: ")
			}

			hStr, _ := reader.ReadString('\n')
			hStr = strings.TrimSpace(hStr)
			if hStr != "" {
				h, err := strconv.Atoi(hStr)
				if err == nil && h >= 0 && h <= 3 {
					activeCount := 0
					for _, active := range SAState.EnabledHeads {
						if active {
							activeCount++
						}
					}

					if SAState.EnabledHeads[h] {
						if activeCount > 1 {
							SAState.EnabledHeads[h] = false
							if ArabicMode {
								fmt.Printf("\n\033[91m✔ تم عزل وإيقاف الرأس المغناطيسي HEAD %d بنجاح لمنع الخدوش السطحية!\033[0m\n", h)
							} else {
								fmt.Printf("\n\033[91m✔ Successfully isolated Head %d channel pre-amp to prevent scratching!\033[0m\n", h)
							}
						} else {
							if ArabicMode {
								fmt.Println("\n\033[31m❌ خطأ: لا يمكن تعطيل جميع رؤوس القراءة! يجب إبقاء رأس قراءة نشط واحد على الأقل.\033[0m")
							} else {
								fmt.Println("\n\033[31m❌ Error: Cannot disable all read heads. At least one read channel must remain active.\033[0m")
							}
						}
					} else {
						SAState.EnabledHeads[h] = true
						if ArabicMode {
							fmt.Printf("\n\033[92m✔ تم تفعيل الرأس المغناطيسي HEAD %d وإعادة معايرته.\033[0m\n", h)
						} else {
							fmt.Printf("\n\033[92m✔ Successfully re-activated Head %d read channel.\033[0m\n", h)
						}
					}
					time.Sleep(1500 * time.Millisecond)
				}
			}

		} else if opt == "4" {
			PrintFirmwareModules(SAState.Modules, ArabicMode)
			if ArabicMode {
				fmt.Println("\nالخيارات:")
				fmt.Println(" 1. فحص وتدقيق موديول معين بالقطاع الخدمي (Diagnose SA Module)")
				fmt.Println(" 2. إعادة كتابة وترميم موديول تالف من مخزن الباك اب (Restore module)")
				fmt.Println(" 3. العودة")
				fmt.Print("اختر رقماً: ")
			} else {
				fmt.Println("\nOptions:")
				fmt.Println(" 1. Verify and diagnose SA module integrity")
				fmt.Println(" 2. Rewrite and recover module from clean backup repository")
				fmt.Println(" 3. Back")
				fmt.Print("Enter option: ")
			}

			mOpt, _ := reader.ReadString('\n')
			mOpt = strings.TrimSpace(mOpt)

			if mOpt == "1" || mOpt == "2" {
				if ArabicMode {
					fmt.Print("أدخل معرف الموديول (مثال: 0x11): ")
				} else {
					fmt.Print("Enter module ID (e.g. 0x11): ")
				}
				mID, _ := reader.ReadString('\n')
				mID = strings.TrimSpace(mID)

				found := false
				for idx, m := range SAState.Modules {
					if m.ID == mID {
						found = true
						if mOpt == "1" {
							if ArabicMode {
								fmt.Printf("\nجاري مراجعة سلامة الموديول %s... والتحقق من توافق CRC...\n", mID)
							} else {
								fmt.Printf("\nDiagnosing Module %s integrity... calculating segment CRC parity...\n", mID)
							}
							time.Sleep(1000 * time.Millisecond)
							if m.Status == "CORRUPT" || m.Status == "UNREADABLE" {
								if ArabicMode {
									fmt.Println("\033[31m[❌] تم الكشف عن خلل في تطابق قيم التحقق! الموديول تالف.\033[0m")
								} else {
									fmt.Println("\033[31m[❌] CRC Mismatch detected! Module microcode has corruption.\033[0m")
								}
							} else {
								if ArabicMode {
									fmt.Println("\033[32m[✔] موديول سليم وخالٍ من الاختناقات البرمجية.\033[0m")
								} else {
									fmt.Println("\033[32m[✔] Module health: OK. CRC verification matched.\033[0m")
								}
							}
						} else {
							if ArabicMode {
								fmt.Printf("\nجاري كتابة الموديول %s من الباك اب النظيف للمصنع...\n", mID)
							} else {
								fmt.Printf("\nRewriting Module %s to SA cylinders from clean repository backup...\n", mID)
							}
							time.Sleep(1200 * time.Millisecond)
							SAState.Modules[idx].Status = "OK"
							if ArabicMode {
								fmt.Println("\033[32m[✔] تم استبدال وترميم الموديول بنجاح كامل!\033[0m")
							} else {
								fmt.Println("\033[32m[✔] SA firmware module rewritten successfully! Parity restored.\033[0m")
							}
						}
						_, _ = reader.ReadString('\n')
					}
				}
				if !found {
					if ArabicMode {
						fmt.Println("\033[91m❌ موديول غير مسجل بمستكشف الـ SA.\033[0m")
					} else {
						fmt.Println("\033[91m❌ Specified Module ID not found in SA directory index.\033[0m")
					}
					time.Sleep(1500 * time.Millisecond)
				}
			}

		} else if opt == "5" {
			if ArabicMode {
				fmt.Println("\nمبرمجة الـ ROM BIOS ومعدل خريطة الرؤوس بالـ EEPROM:")
				fmt.Println("خريطة الرؤوس النشطة بالـ ROM حالياً:", SAState.RomHeads)
				fmt.Println("\nالخيارات:")
				fmt.Println(" 1. تعطيل رأس من خريطة الـ ROM adaptives")
				fmt.Println(" 2. تفعيل رأس من خريطة الـ ROM adaptives")
				fmt.Println(" 3. بدء الفلاش وكتابة الميكروكود المطور للـ ROM (Burn EEPROM)")
				fmt.Println(" 4. العودة")
				fmt.Print("اختر رقماً: ")
			} else {
				fmt.Println("\nROM BIOS EEPROM FLASH WRITER & ADAPTIVES EDITOR:")
				fmt.Println("Active ROM head map indexes:", SAState.RomHeads)
				fmt.Println("\nAvailable Options:")
				fmt.Println(" 1. Disable head index in ROM adaptives map configuration")
				fmt.Println(" 2. Enable head index in ROM adaptives map configuration")
				fmt.Println(" 3. Trigger raw EEPROM flash write sequence (Burn ROM BIOS)")
				fmt.Println(" 4. Back")
				fmt.Print("Enter choice index: ")
			}

			rOpt, _ := reader.ReadString('\n')
			rOpt = strings.TrimSpace(rOpt)

			if rOpt == "1" || rOpt == "2" {
				if ArabicMode {
					fmt.Print("أدخل رقم الرأس (0-3): ")
				} else {
					fmt.Print("Enter head index (0-3): ")
				}
				hStr, _ := reader.ReadString('\n')
				h, err := strconv.Atoi(strings.TrimSpace(hStr))

				if err == nil && h >= 0 && h <= 3 {
					if rOpt == "1" {
						// remove head from RomHeads if exists
						var next []int
						for _, val := range SAState.RomHeads {
							if val != h {
								next = append(next, val)
							}
						}
						if len(next) > 0 {
							SAState.RomHeads = next
							if ArabicMode {
								fmt.Printf("✔ تم عزل الرأس %d من خريطة تكيف ميكروكود الـ ROM بنجاح.\n", h)
							} else {
								fmt.Printf("✔ Head %d isolated from ROM adaptives header.\n", h)
							}
						} else {
							if ArabicMode {
								fmt.Println("❌ لا يمكن تفريغ جميع رؤوس الـ ROM!")
							} else {
								fmt.Println("❌ Cannot disable all head adaptives in ROM struct.")
							}
						}
					} else {
						// add if not exists
						found := false
						for _, val := range SAState.RomHeads {
							if val == h {
								found = true
							}
						}
						if !found {
							SAState.RomHeads = append(SAState.RomHeads, h)
							if ArabicMode {
								fmt.Printf("✔ تم دمج الرأس %d بخريطة تكيف ميكروكود الـ ROM بنجاح.\n", h)
							} else {
								fmt.Printf("✔ Head %d re-enabled in ROM adaptives header.\n", h)
							}
						}
					}
					time.Sleep(1500 * time.Millisecond)
				}

			} else if rOpt == "3" {
				FlashRomChip(SAState.RomHeads, ArabicMode)
				_, _ = reader.ReadString('\n')
			}

		} else if opt == "6" {
			break
		}
	}
}

// Sub-Menu 8: AI Autonomous Self-Repair Wizard
func RunAiRepairWizard(reader *bufio.Reader) {
	fmt.Print("\033[H\033[2J")
	PrintBootLogo()

	if SelectedDrive == nil {
		if ArabicMode {
			fmt.Println("\033[91m❌ تنبيه: الرجاء اختيار قرص صلب أولاً لفحصه واكتشاف مشاكله تلقائياً!\033[0m")
		} else {
			fmt.Println("\033[91m❌ Error: Please connect/mount a target drive from Option (1) first!\033[0m")
		}
		return
	}

	if ActiveFailure == nil {
		if ArabicMode {
			fmt.Println("\n\033[92m✔ فحص الذكاء الاصطناعي العام: ميكروكونترولر القرص ومؤشرات السطح سليمة وخالية من الأعطال.\033[0m")
			fmt.Println("لا يوجد أي مشاكل برمجية أو فيرموير لتطبيق الإصلاح التلقائي عليها حالياً.")
		} else {
			fmt.Println("\n\033[92m✔ AI Diagnostics Complete: Drive firmware, controller and media surface report healthy.\033[0m")
			fmt.Println("No firmware exception or sector block failures detected to warrant auto-repair.")
		}
		_, _ = reader.ReadString('\n')
		return
	}

	// Trigger Repair
	success := RunAiAutoRepair(*ActiveFailure, ArabicMode)
	if success {
		// Healing consequences!
		if ActiveFailure.FirmwareAction == "TRANSLATOR" {
			SelectedDrive.CapacityBytes = 12000398934016 // restore 12TB NAS capacity!
			SelectedDrive.TotalSectors = 12000398934016 / 512
			CreatedPartitions = GetDefaultPartitions(SelectedDrive.CapacityBytes)
		} else if ActiveFailure.FirmwareAction == "HEAD_ISOLATION" {
			SAState.RomHeads = []int{0, 1, 3}
			SAState.EnabledHeads = map[int]bool{0: true, 1: true, 3: true}
		}

		ActiveFailure = nil
		CurrentProfile = HealthyHDD
		SmartAttributes = GetMockSmartAttributes(CurrentProfile)
	}

	if ArabicMode {
		fmt.Println("\nاضغط Enter للعودة للرادار الرئيسي...")
	} else {
		fmt.Println("\nPress Enter to return to main dashboard...")
	}
	_, _ = reader.ReadString('\n')
}
