package main

import (
	"fmt"
	"time"
)

// DefectRecord represents a bad sector mapped in drive microcode tables
type DefectRecord struct {
	LBA      int64
	Cylinder int
	Head     int
	Sector   int
	Type     string // Physical Surface, Magnetic Weakness, Servo Disturbance
	Source   string // P-List, G-List
}

// FirmwareModule represents a microcode file on SA cylinders
type FirmwareModule struct {
	ID          string
	Name        string
	SizeSectors int
	Status      string // OK, CORRUPT, UNREADABLE
	Cylinder    string
}

// LowLevelSAState manages low-level structures
type LowLevelSAState struct {
	Bsy, Drdy, Df, Drq, Err bool
	EnabledHeads            map[int]bool
	RomHeads                []int
	Defects                 []DefectRecord
	Modules                 []FirmwareModule
}

// InitializeLowLevelSA initializes SA modules and defects
func InitializeLowLevelSA() LowLevelSAState {
	return LowLevelSAState{
		Bsy:          false,
		Drdy:         true,
		Df:           false,
		Drq:          false,
		Err:          false,
		EnabledHeads: map[int]bool{0: true, 1: true, 2: true, 3: true},
		RomHeads:     []int{0, 1, 2, 3},
		Defects: []DefectRecord{
			{204848, 122, 0, 14, "Physical Surface", "P-List"},
			{10543900, 1140, 2, 89, "Magnetic Weakness", "P-List"},
			{88439200, 8740, 1, 102, "Servo Disturbance", "P-List"},
		},
		Modules: []FirmwareModule{
			{"0x01", "Directory Index (DIR)", 16, "OK", "SA Cyl -2"},
			{"0x02", "Configuration Overlay (CONFIG)", 32, "OK", "SA Cyl -2"},
			{"0x03", "Zone Translation Table (ZONES)", 128, "OK", "SA Cyl -2"},
			{"0x11", "S.M.A.R.T. Log Database (SMART)", 64, "CORRUPT", "SA Cyl -1"},
			{"0x28", "Primary Defect Table (P-List)", 512, "OK", "SA Cyl -1"},
			{"0x35", "Grown Defect Table (G-List)", 128, "UNREADABLE", "SA Cyl -1"},
			{"0x4A", "ROM Head Adaptive Profile", 8, "OK", "SA Cyl -1"},
		},
	}
}

// FormatAtaRegisters outputs a high-tech LED register light display
func FormatAtaRegisters(state LowLevelSAState) string {
	led := func(name string, active bool, activeColor string) string {
		if active {
			return fmt.Sprintf("[%s%s\033[0m]", activeColor, name)
		}
		return fmt.Sprintf("[\033[90m%s\033[0m]", name)
	}

	return fmt.Sprintf("IDE/SATA Bus Registers: %s %s %s %s %s",
		led("BSY", state.Bsy, "\033[33m"),  // Amber
		led("DRDY", state.Drdy, "\033[32m"), // Green
		led("DF", state.Df, "\033[31m"),    // Red
		led("DRQ", state.Drq, "\033[36m"),   // Cyan
		led("ERR", state.Err, "\033[31m"),   // Red
	)
}

// ExecuteSimulatedAtaCommand transmits raw command and generates Hex buffer output
func ExecuteSimulatedAtaCommand(cmdHex string, drive ModelName) ([]string, LowLevelSAState) {
	state := InitializeLowLevelSA()
	var dump []string

	switch cmdHex {
	case "0xEC": // IDENTIFY DEVICE
		state.Drq = true
		dump = []string{
			"0000  5A 0A 00 40 10 00 00 00  00 00 00 00 00 3F 00 00  |Z..@.........?..|",
			"0010  57 44 2D 57 43 43 36 59  31 41 4B 37 53 46 32 20  |WD-WCC6Y1AK7SF2 |",
			"0020  20 20 20 20 20 20 20 20  20 20 00 03 00 02 00 04  |          ......|",
			"0030  33 30 30 33 38 30 2E 30  30 41 38 30 20 20 20 20  |300380.00A80    |",
			fmt.Sprintf("0040  %-56s |%s|", "57 44 43 20 57 44 31 30  45 5A 45 58 2D 30 38 57", "WDC WD10EZEX-08W"),
			"0050  20 20 20 20 20 20 20 20  20 20 20 20 20 20 20 20  |                |",
			"0060  00 10 00 00 00 00 00 00  00 07 00 03 00 7F 00 00  |................|",
			"0100  00 01 00 00 00 00 00 00  00 00 00 00 00 00 00 00  |................|",
		}
	case "0x20": // READ SECTORS
		state.Drq = true
		dump = []string{
			"0000  EB 58 90 4D 53 44 4F 53  35 2E 30 00 02 08 26 00  |.X.MSDOS5.0...&.|",
			"0010  02 00 00 00 00 F8 00 00  3F 00 FF 00 3F 00 00 00  |........?...?...|",
			"0020  C1 3E 4C 3A 80 00 29 C3  67 9A 4E 4F 20 4E 41 4D  |.>L:..).g.NO NAM|",
			"0030  45 20 20 20 46 41 54 33  32 20 20 20 33 C9 8E D1  |E   FAT32   3...|",
			"0040  BC F4 7B 8E C1 8E D9 BD  00 7C 88 4E 02 8A 56 40  |..{......|.N..V@|",
			"0050  B4 41 BB AA 55 CD 13 72  10 81 FB 55 AA 75 0A F6  |.A..U..r...U.u..|",
			"0100  55 AA 00 00 00 00 00 00  00 00 00 00 00 00 00 00  |U...............|",
		}
	case "0xF1": // SECURITY LOCK
		state.Df = true
		state.Err = true
	}

	return dump, state
}

type ModelName string

// PrintDefectTable outputs the P-List and G-List defects
func PrintDefectTable(defects []DefectRecord, arabic bool) {
	if arabic {
		fmt.Printf("\n%-12s %-12s %-12s %-10s %-10s\n", "رقم القطاع LBA", "الأسطوانة CYL", "الرأس HD", "القطاع SEC", "نوع العيب")
		fmt.Println("──────────────────────────────────────────────────────────────────")
		for _, d := range defects {
			fmt.Printf("%-14d %-12d %-12d %-10d %-10s (%s)\n", d.LBA, d.Cylinder, d.Head, d.Sector, d.Type, d.Source)
		}
	} else {
		fmt.Printf("\n%-14s %-12s %-12s %-10s %-10s\n", "LBA Sector", "Cylinder Cyl", "Head HD", "Sector SEC", "Defect Class")
		fmt.Println("──────────────────────────────────────────────────────────────────")
		for _, d := range defects {
			fmt.Printf("%-14d %-12d %-12d %-10d %-10s (%s)\n", d.LBA, d.Cylinder, d.Head, d.Sector, d.Type, d.Source)
		}
	}
}

// PrintFirmwareModules outputs SA Modules directory
func PrintFirmwareModules(modules []FirmwareModule, arabic bool) {
	if arabic {
		fmt.Printf("\n%-8s %-32s %-12s %-10s\n", "المعرف", "اسم وحدة الفيرموير Module", "الحجم بالقطاع", "حالة الوحدة")
		fmt.Println("──────────────────────────────────────────────────────────────────")
		for _, m := range modules {
			status := "\033[32mسليمة OK\033[0m"
			if m.Status == "CORRUPT" {
				status = "\033[33mتالفة CORRUPT\033[0m"
			} else if m.Status == "UNREADABLE" {
				status = "\033[31mغير مقروءة\033[0m"
			}
			fmt.Printf("%-8s %-32s %-15d %-10s\n", m.ID, m.Name, m.SizeSectors, status)
		}
	} else {
		fmt.Printf("\n%-8s %-32s %-12s %-10s\n", "Mod ID", "Firmware Module Name", "Size (Sect)", "SA Status")
		fmt.Println("──────────────────────────────────────────────────────────────────")
		for _, m := range modules {
			status := "\033[32mOK\033[0m"
			if m.Status == "CORRUPT" {
				status = "\033[33mCORRUPT\033[0m"
			} else if m.Status == "UNREADABLE" {
				status = "\033[31mUNREADABLE\033[0m"
			}
			fmt.Printf("%-8s %-32s %-12d %-10s\n", m.ID, m.Name, m.SizeSectors, status)
		}
	}
}

// FlashRomChip EEPROM programmer sequence
func FlashRomChip(headMap []int, arabic bool) {
	if arabic {
		fmt.Println("\n[!] بدء عملية برمجة وتصفير شريحة الـ ROM BIOS بسعة 512 كيلوبايت...")
		fmt.Println("💡 جاري إرسال إشارات التهيئة عبر واجهة الميكروكنترولر...")
	} else {
		fmt.Println("\n[!] Initializing 512KB EEPROM ROM flash programmer sequence...")
		fmt.Println("💡 Sending direct hardware assertion pin signals over terminal...")
	}

	steps := 4
	for i := 1; i <= steps; i++ {
		time.Sleep(400 * time.Millisecond)
		progress := float64(i) / float64(steps) * 100.0
		if arabic {
			fmt.Printf("• كتابة كتل الميكروكود للـ ROM... %.1f%%\n", progress)
		} else {
			fmt.Printf("• Writing ROM microcode blocks... %.1f%%\n", progress)
		}
	}

	time.Sleep(300 * time.Millisecond)
	headMapStr := fmt.Sprintf("%v", headMap)
	if arabic {
		fmt.Printf("✔ تم احتساب قيمة تدقيق الـ ROM (Checksum): 0x8C4B. تطابق ممتاز!\n")
		fmt.Printf("✔ تم حفظ خريطة الرؤوس النشطة بالـ ROM بنجاح: الرؤوس %s\n", headMapStr)
		fmt.Println("✔ تمت إعادة تهيئة شريحة الـ BIOS والتحقق من سلامتها كلياً!")
	} else {
		fmt.Printf("✔ ROM Fletcher Checksum calculated: 0x8C4B. MATCH.\n")
		fmt.Printf("✔ Patched active head adaptives written to ROM successfully: Heads %s\n", headMapStr)
		fmt.Println("✔ EEPROM ROM flash completed and verified. Drive microcontroller rebooted.")
	}
}
