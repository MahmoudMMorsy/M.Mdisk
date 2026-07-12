//go:build windows
// +build windows

package main

import (
	"fmt"
	"math/rand"
	"strconv"
	"syscall"
	"time"
	"unsafe"
)

// Win32 API DLL imports
var (
	user32           = syscall.NewLazyDLL("user32.dll")
	gdi32            = syscall.NewLazyDLL("gdi32.dll")
	kernel32         = syscall.NewLazyDLL("kernel32.dll")
	comctl32         = syscall.NewLazyDLL("comctl32.dll")

	// user32 functions
	procRegisterClassExW = user32.NewProc("RegisterClassExW")
	procCreateWindowExW  = user32.NewProc("CreateWindowExW")
	procShowWindow       = user32.NewProc("ShowWindow")
	procUpdateWindow     = user32.NewProc("UpdateWindow")
	procGetMessageW      = user32.NewProc("GetMessageW")
	procTranslateMessage = user32.NewProc("TranslateMessage")
	procDispatchMessageW = user32.NewProc("DispatchMessageW")
	procDefWindowProcW   = user32.NewProc("DefWindowProcW")
	procPostQuitMessage  = user32.NewProc("PostQuitMessage")
	procSendMessageW     = user32.NewProc("SendMessageW")
	procPostMessageW     = user32.NewProc("PostMessageW")
	procSetWindowTextW   = user32.NewProc("SetWindowTextW")
	procGetWindowTextW   = user32.NewProc("GetWindowTextW")
	procDestroyWindow    = user32.NewProc("DestroyWindow")
	procGetClientRect    = user32.NewProc("GetClientRect")
	procGetDC            = user32.NewProc("GetDC")
	procReleaseDC        = user32.NewProc("ReleaseDC")
	procInvalidateRect   = user32.NewProc("InvalidateRect")
	procSetTimer         = user32.NewProc("SetTimer")
	procKillTimer        = user32.NewProc("KillTimer")
	procBeginPaint       = user32.NewProc("BeginPaint")
	procEndPaint         = user32.NewProc("EndPaint")

	// gdi32 functions
	procCreateSolidBrush = gdi32.NewProc("CreateSolidBrush")
	procDeleteObject     = gdi32.NewProc("DeleteObject")
	procSelectObject     = gdi32.NewProc("SelectObject")
	procMoveToEx         = gdi32.NewProc("MoveToEx")
	procLineTo           = gdi32.NewProc("LineTo")
	procTextOutW         = gdi32.NewProc("TextOutW")
	procSetTextColor     = gdi32.NewProc("SetTextColor")
	procSetBkColor       = gdi32.NewProc("SetBkColor")
	procCreateFontW      = gdi32.NewProc("CreateFontW")
	procRectangle        = gdi32.NewProc("Rectangle")

	// comctl32 functions
	procInitCommonControlsEx = comctl32.NewProc("InitCommonControlsEx")
)

// Win32 constants
const (
	WS_OVERLAPPEDWINDOW = 0x00CF0000
	WS_VISIBLE          = 0x10000000
	WS_CHILD            = 0x40000000
	WS_BORDER           = 0x00800000
	WS_VSCROLL          = 0x00200000

	WM_DESTROY = 0x0002
	WM_PAINT   = 0x000F
	WM_COMMAND = 0x0111
	WM_TIMER   = 0x0113

	CBS_DROPDOWNLIST = 0x0003
	CBS_HASSTRINGS   = 0x0200
	LBS_NOTIFY       = 0x0001

	TCN_SELCHANGE = 0xFFFFFFF7
	WM_NOTIFY     = 0x004E

	PBM_SETRANGE = 0x0401
	PBM_SETPOS   = 0x0402

	// Custom User Message for Thread-Safe UI updates
	WM_USER_LOG = 0x0400 + 1

	// Win32 Color Messages
	WM_CTLCOLORSTATIC  = 0x0138
	WM_CTLCOLORLISTBOX = 0x0134
	WM_CTLCOLOREDIT    = 0x0133
)

// RGB helper function
func RGB(r, g, b byte) uint32 {
	return uint32(r) | (uint32(g) << 8) | (uint32(b) << 16)
}

// Custom GDI Brushes & Colors matching the high-tech Compose app
var (
	ColorSlateDark = RGB(12, 16, 23)     // Deep background
	ColorSlateCard = RGB(19, 26, 38)     // Card background
	ColorPurple80  = RGB(216, 180, 254)  // Glowing purple
	ColorNeonGreen = RGB(57, 255, 20)    // Glowing green
	ColorNeonAmber = RGB(245, 158, 11)   // Orange warning
	ColorNeonBlue  = RGB(59, 130, 246)   // Blue active
	ColorNeonRed   = RGB(239, 68, 68)    // Red failure
	ColorWhite     = RGB(255, 255, 255)
	ColorTerminal  = RGB(57, 255, 20)    // Green text

	BrushSlateDark syscall.Handle
	BrushSlateCard syscall.Handle
)

// Global state variables
var (
	MainHWND       syscall.Handle
	TabHWND        syscall.Handle
	ActivePanelID  int = 0
	SelectedDrive  *DriveInfo
	ActiveFailure  *DriveFailure
	Partitions     []Partition
	SmartAttrs     []SmartAttribute
	PulseHistory   []int
	WipeProgress   int = 0
	ScanProgress   int = 0
	ScanTimer      uintptr
	WipeTimer      uintptr
	RepairTimer    uintptr
	OscilloscopeX  int = 0

	// Track control handles for each panel to dynamically toggle show/hide and avoid overlaps
	PanelControls [7][]syscall.Handle

	// Real and Simulated Drives Lists
	DrivesList []DriveInfo

	// Dynamic Drive Profile state
	CurrentProfile SmartProfile = HealthyHDD
	CarvedFiles    []RecoveredFile

	// Scanned blocks for Victoria Heatmap Grid
	ScannedBlocks []BlockDetail

	// Failure definitions initialized globally
	DetectedFailures []DriveFailure = GetFailureDetails()

	// UI controls
	DriveComboHWND    syscall.Handle
	StatusLabelHWND   syscall.Handle
	InfoLabelHWND     syscall.Handle
	AiRepairBtnHWND   syscall.Handle
	InfoBoxHWND       syscall.Handle
	SmartListHWND     syscall.Handle
	VictoriaBtnHWND   syscall.Handle
	VictoriaProgHWND  syscall.Handle
	VictoriaHistHWND  syscall.Handle
	VictoriaGridHWND  syscall.Handle // Custom drawing area for Victoria blocks
	SpinRiteBtnHWND   syscall.Handle
	SpinRiteWaveHWND  syscall.Handle
	SpinRiteStatsHWND syscall.Handle
	PartListHWND      syscall.Handle
	PartFormatBtnHWND syscall.Handle
	PartWipeBtnHWND   syscall.Handle
	PartWipeProgHWND  syscall.Handle
	CarveBtnHWND      syscall.Handle
	CarveListHWND     syscall.Handle
	SaveCarvedBtnHWND syscall.Handle

	// Arabic / English Toggle
	ArabicMode bool = true
)

// Win32 structures
type wndClassEx struct {
	Size          uint32
	Style         uint32
	WndProc       uintptr
	ClsExtra      int32
	WndExtra      int32
	Instance      syscall.Handle
	Icon          syscall.Handle
	Cursor        syscall.Handle
	Background    syscall.Handle
	MenuName      *uint16
	ClassName     *uint16
	IconSm        syscall.Handle
}

type rect struct {
	Left, Top, Right, Bottom int32
}

type paintStruct struct {
	Hdc                 syscall.Handle
	Erase               int32
	RcPaint             rect
	Restore             int32
	IncUpdate           int32
	Row                 [32]byte
}

type nmhdr struct {
	HwndFrom syscall.Handle
	IDFrom   uintptr
	Code     uint32
}

type initCommonControlsEx struct {
	Size uint32
	ICC  uint32
}

func main() {
	rand.Seed(time.Now().UnixNano())
	InitializeVirtualDisk()

	// Initialize background brushes
	hBrushDark, _, _ := procCreateSolidBrush.Call(uintptr(ColorSlateDark))
	BrushSlateDark = syscall.Handle(hBrushDark)
	hBrushCard, _, _ := procCreateSolidBrush.Call(uintptr(ColorSlateCard))
	BrushSlateCard = syscall.Handle(hBrushCard)

	// Initialize Common Controls (Tab control, Progress Bar)
	var icex initCommonControlsEx
	icex.Size = uint32(unsafe.Sizeof(icex))
	icex.ICC = 0x00000008 | 0x00004000 // ICC_TAB_CLASSES | ICC_PROGRESS_CLASS
	_, _, _ = procInitCommonControlsEx.Call(uintptr(unsafe.Pointer(&icex)))

	// Register Window Class
	className, _ := syscall.UTF16PtrFromString("PC3000WindowClass")
	var wc wndClassEx
	wc.Size = uint32(unsafe.Sizeof(wc))
	wc.WndProc = syscall.NewCallback(wndProc)
	wc.Instance = 0
	wc.Background = BrushSlateDark // Modern Dark theme background brush!
	wc.ClassName = className
	_, _, _ = procRegisterClassExW.Call(uintptr(unsafe.Pointer(&wc)))

	// Create Main Window
	titleStr := "★ PC-3000 Advanced Hardware Recovery Workstation - ليفل روسي احترافي ★"
	title, _ := syscall.UTF16PtrFromString(titleStr)
	hwnd, _, _ := procCreateWindowExW.Call(
		0,
		uintptr(unsafe.Pointer(className)),
		uintptr(unsafe.Pointer(title)),
		WS_OVERLAPPEDWINDOW|WS_VISIBLE,
		100, 100, 850, 650,
		0, 0, 0, 0,
	)
	MainHWND = syscall.Handle(hwnd)

	// Main Win32 Message Loop
	var msg [48]byte
	for {
		ret, _, _ := procGetMessageW.Call(uintptr(unsafe.Pointer(&msg[0])), 0, 0, 0)
		if ret == 0 {
			break
		}
		_, _, _ = procTranslateMessage.Call(uintptr(unsafe.Pointer(&msg[0])))
		_, _, _ = procDispatchMessageW.Call(uintptr(unsafe.Pointer(&msg[0])))
	}
}

// WndProc handles Win32 messages - type of msg is uintptr to prevent stack misalignment in x64 callbacks
func wndProc(hwnd syscall.Handle, msg uintptr, wParam, lParam uintptr) uintptr {
	switch msg {
	case WM_DESTROY:
		procPostQuitMessage.Call(0)
		return 0

	case 0x0001: // WM_CREATE
		createUiControls(hwnd)
		return 0

	case WM_COMMAND:
		handleCommand(wParam, lParam)
		return 0

	case WM_NOTIFY:
		hdr := (*nmhdr)(unsafe.Pointer(lParam))
		if hdr.HwndFrom == TabHWND && hdr.Code == TCN_SELCHANGE {
			curSel, _, _ := procSendMessageW.Call(uintptr(TabHWND), 0x130B, 0, 0) // TCM_GETCURSEL
			switchPanel(int(curSel))
		}
		return 0

	case WM_TIMER:
		handleTimer(wParam)
		return 0

	case WM_USER_LOG: // Thread-safe custom window message handler
		utf16Ptr := (*uint16)(unsafe.Pointer(wParam))
		// Log the string on the main thread safely
		addLogToBoxRaw(utf16Ptr)
		return 0

	// Handle custom dark theme coloring for all standard controls
	case WM_CTLCOLORSTATIC:
		hdc := syscall.Handle(wParam)
		childHWND := syscall.Handle(lParam)

		if childHWND == VictoriaGridHWND { // custom drawing for Victoria 2D Grid
			procSetTextColor.Call(uintptr(hdc), uintptr(ColorTerminal))
			procSetBkColor.Call(uintptr(hdc), uintptr(ColorSlateDark))
			return uintptr(BrushSlateDark)
		}

		// Dark background for normal static labels
		procSetTextColor.Call(uintptr(hdc), uintptr(ColorPurple80))
		procSetBkColor.Call(uintptr(hdc), uintptr(ColorSlateDark))
		return uintptr(BrushSlateDark)

	case WM_CTLCOLORLISTBOX:
		hdc := syscall.Handle(wParam)
		procSetTextColor.Call(uintptr(hdc), uintptr(ColorTerminal))
		procSetBkColor.Call(uintptr(hdc), uintptr(ColorSlateCard))
		return uintptr(BrushSlateCard)

	case WM_CTLCOLOREDIT:
		hdc := syscall.Handle(wParam)
		procSetTextColor.Call(uintptr(hdc), uintptr(ColorTerminal))
		procSetBkColor.Call(uintptr(hdc), uintptr(ColorSlateCard))
		return uintptr(BrushSlateCard)

	case WM_PAINT:
		// Draw Victoria 2D Heatmap Grid manually
		if ActivePanelID == 2 { // Victoria Panel
			drawVictoriaHeatmap()
		}
	}

	ret, _, _ := procDefWindowProcW.Call(uintptr(hwnd), msg, wParam, lParam)
	return ret
}

func createUiControls(hwnd syscall.Handle) {
	// Create Tab Control
	tabClass, _ := syscall.UTF16PtrFromString("SysTabControl32")
	tabH, _, _ := procCreateWindowExW.Call(
		0,
		uintptr(unsafe.Pointer(tabClass)),
		0,
		WS_CHILD|WS_VISIBLE,
		10, 10, 810, 580,
		uintptr(hwnd),
		1000, 0, 0,
	)
	TabHWND = syscall.Handle(tabH)

	// Add Tabs with Modern High-Tech labels
	addTabItem("1. AI Control & Repair / الصيانة التلقائية", 0)
	addTabItem("2. S.M.A.R.T. Attributes / مستشعرات القرص", 1)
	addTabItem("3. Victoria Block Heatmap / مسح القطاعات", 2)
	addTabItem("4. SpinRite Pulse / نبضات الاستقرار", 3)
	addTabItem("5. Partition Magic / مدير تقسيم الملفات", 4)
	addTabItem("6. Signature Carver Recovery / استعادة الداتا", 5)

	// Static control for headers
	createStaticLabel(hwnd, "★ ACE LAB PC-3000 WINDOWS HARDWARE WORKSTATION ★", 20, 45, 780, 25, 0)

	// PANEL 1: AI Repair & Control
	createStaticLabel(hwnd, "Select Target Device Interface / حدد واجهة التخزين الفيزيائية:", 30, 80, 400, 20, 1)
	createComboBox(hwnd, 30, 105, 450, 200, 1, 1)
	createStaticLabel(hwnd, "STATUS DIAGNOSTICS REGISTERS / تقييم صحة الميديا والمنطقة الخدمية:", 30, 150, 450, 20, 1)
	StatusLabelHWND = createStaticLabel(hwnd, "• Drive Status: Standby / بانتظار تحديد القرص الصلب لتقييمه.", 30, 175, 450, 40, 1)
	InfoLabelHWND = createStaticLabel(hwnd, "• Connection Type: Simulated Port Engine.", 30, 215, 450, 20, 1)

	AiRepairBtnHWND = createButton(hwnd, "APPLY AUTONOMOUS AI DRIVE REPAIR / تطبيق الصيانة الفورية", 30, 260, 450, 40, 101, 1)
	InfoBoxHWND = createEditBox(hwnd, "ACE Lab Novosibirsk Russia. Low-Level Firmware recovery console.", 30, 320, 450, 230, 102, 1)

	// PANEL 2: S.M.A.R.T
	createStaticLabel(hwnd, "S.M.A.R.T. Health Telemetry Mappings / تفاصيل مستشعرات صحة الهارد ديسك:", 30, 80, 750, 20, 2)
	SmartListHWND = createListBox(hwnd, 30, 110, 750, 440, 201, 2)

	// PANEL 3: Victoria Scanner
	createStaticLabel(hwnd, "Victoria Sector Latency block scan / فحص قطاعات القرص ببرنامج فيكتوريا:", 30, 80, 750, 20, 3)
	VictoriaBtnHWND = createButton(hwnd, "START PLATER HEATMAP SCAN / بدء فحص قطاعات الميديا", 30, 110, 350, 40, 301, 3)
	VictoriaProgHWND = createProgressBar(hwnd, 395, 118, 385, 25, 3)

	// Dynamic 2D Heatmap Grid Display Canvas (Uses Static control as target context)
	createStaticLabel(hwnd, "LIVE CLUSTER ANALYSIS PLOT (خريطة قطاعات الذاكرة التفاعلية):", 30, 160, 750, 20, 3)
	VictoriaGridHWND = createStaticLabel(hwnd, "", 30, 185, 750, 150, 3)

	VictoriaHistHWND = createListBox(hwnd, 30, 345, 750, 200, 302, 3)

	// PANEL 4: SpinRite Pulse
	createStaticLabel(hwnd, "SpinRite Magnetic Pulse Surface Recovery / نبضات استعدال المغنطة السطحية:", 30, 80, 750, 20, 4)
	SpinRiteBtnHWND = createButton(hwnd, "INITIATE SPINRITE HYSTERESIS / بث نبضات الاستعدال", 30, 110, 500, 40, 401, 4)
	SpinRiteWaveHWND = createStaticLabel(hwnd, "Oscilloscope Standby - Waveform visualizer.", 30, 160, 750, 120, 4)
	SpinRiteStatsHWND = createListBox(hwnd, 30, 290, 750, 260, 402, 4)

	// PANEL 5: Partition Magic
	createStaticLabel(hwnd, "Partition Magic Volume Manager / جدول وتفاصيل تقسيمات الهارد ديسك الحالية:", 30, 80, 750, 20, 5)
	PartListHWND = createListBox(hwnd, 30, 110, 750, 260, 501, 5)
	PartFormatBtnHWND = createButton(hwnd, "Format Partition (NTFS/FAT32)", 30, 390, 230, 35, 502, 5)
	PartWipeBtnHWND = createButton(hwnd, "Secure Zero-Wipe Volume", 280, 390, 230, 35, 503, 5)
	PartWipeProgHWND = createProgressBar(hwnd, 30, 440, 480, 25, 5)

	// PANEL 6: Carver Recovery
	createStaticLabel(hwnd, "Deep File signature carver / استعادة الملفات المحذوفة بالتوقيع الرقمي:", 30, 80, 750, 20, 6)
	CarveBtnHWND = createButton(hwnd, "RUN DEEP SECTOR CARVER / فحص واستعادة الملفات والبارتشنات", 30, 110, 500, 40, 601, 6)
	CarveListHWND = createListBox(hwnd, 30, 165, 750, 330, 602, 6)
	SaveCarvedBtnHWND = createButton(hwnd, "SAVE RECOVERED FILE / حفظ الملف المسترجع على الكمبيوتر", 30, 510, 500, 35, 603, 6)

	// Populate disk presets
	populateDrivePresets()
	switchPanel(0)
}

func switchPanel(panelID int) {
	ActivePanelID = panelID
	for i := 1; i <= 6; i++ {
		showPanelControls(i, i == panelID+1)
	}
}

func showPanelControls(panelID int, show bool) {
	cmd := uintptr(0)
	if show {
		cmd = 1 // SW_SHOW
	}

	// Loop over all controls mapped to this panel and hide/show them dynamically to avoid overlaps
	for _, ctrl := range PanelControls[panelID] {
		procShowWindow.Call(uintptr(ctrl), cmd)
	}
}

func handleCommand(wParam, lParam uintptr) {
	controlID := int(wParam & 0xFFFF)
	notificationCode := int((wParam >> 16) & 0xFFFF)

	if controlID == 1 && notificationCode == 1 { // Drive Combo Selection
		curSel, _, _ := procSendMessageW.Call(uintptr(DriveComboHWND), 0x0147, 0, 0) // CB_GETCURSEL
		handleDriveSelection(int(curSel))
	}

	switch controlID {
	case 101: // APPLY AI AUTO-REPAIR
		if SelectedDrive == nil {
			msgBox("يرجى اختيار قرص صلب أولاً لتقييمه وإصلاحه! / Please select a drive first.")
			return
		}
		if ActiveFailure == nil {
			msgBox("القرص الحالي سليم ومستقر بالكامل ولا يتطلب أي إصلاح! / Drive is already healthy.")
			return
		}
		triggerAiRepair()

	case 301: // START VICTORIA SCAN
		if SelectedDrive == nil {
			msgBox("يرجى اختيار قرص صلب أولاً لبدء المسح! / Please select a drive.")
			return
		}
		startVictoriaScan()

	case 401: // START SPINRITE REPAIR
		if SelectedDrive == nil {
			msgBox("يرجى اختيار قرص صلب أولاً لبدء المغنطة! / Please select a drive.")
			return
		}
		startSpinRiteRepair()

	case 502: // FORMAT PARTITION
		formatSelectedPartition()

	case 503: // SECURE WIPE
		wipeSelectedPartition()

	case 601: // DEEP CARVER
		startDeepCarver()

	case 603: // SAVE CARVED FILE
		saveCarvedFile()
	}
}

func handleTimer(wParam uintptr) {
	if wParam == 1 { // Victoria Scanner Timer
		updateVictoriaScan()
	} else if wParam == 2 { // SpinRite Wave Timer
		updateSpinRiteWave()
	} else if wParam == 3 { // Wipe Timer
		updateWipeProgress()
	}
}

// Drive selection helper
func handleDriveSelection(selIndex int) {
	if selIndex < 0 || selIndex >= len(DrivesList) {
		return
	}

	drive := DrivesList[selIndex]
	SelectedDrive = &drive

	// Extract active failure and profile
	ActiveFailure = nil
	profile := HealthyHDD
	for _, fail := range DetectedFailures {
		if fail.ID == drive.ActiveIssue {
			ActiveFailure = &fail
			profile = FailingHDD
			break
		}
	}
	if drive.ActiveIssue == "BAD_SECTOR_CASCADE" {
		profile = FailingSSD
	}

	CurrentProfile = profile
	SmartAttrs = GetMockSmartAttributes(profile)
	Partitions = GetDefaultPartitions(drive.CapacityBytes)

	// Update GUI text labels
	var statusText string
	if ActiveFailure != nil {
		statusText = fmt.Sprintf("• عطل الميكروكود المكتشف: %s\n• failure: %s", ActiveFailure.TitleAr, ActiveFailure.TitleEn)
	} else {
		statusText = "• حالة الميديا: سليم تماماً وبحالة فيزيائية ممتازة. جاهز للفحص.\n• Platter status: Healthy & Stable. Ready."
	}
	uStatusText, _ := syscall.UTF16PtrFromString(statusText)
	procSetWindowTextW.Call(uintptr(StatusLabelHWND), uintptr(unsafe.Pointer(uStatusText)))

	infoText := fmt.Sprintf("• SN: %s | FW: %s | Size: %s", drive.Serial, drive.Firmware, FormatSize(drive.CapacityBytes))
	uInfoText, _ := syscall.UTF16PtrFromString(infoText)
	procSetWindowTextW.Call(uintptr(InfoLabelHWND), uintptr(unsafe.Pointer(uInfoText)))

	// Update S.M.A.R.T. Listbox
	updateSmartListBox()

	// Update Partitions Listbox
	updatePartitionListBox()

	// Clear scanner grids
	clearListBox(VictoriaHistHWND)
	clearListBox(SpinRiteStatsHWND)
	clearListBox(CarveListHWND)
	ScannedBlocks = nil
	procSendMessageW.Call(uintptr(VictoriaProgHWND), PBM_SETPOS, 0, 0)
	procSendMessageW.Call(uintptr(PartWipeProgHWND), PBM_SETPOS, 0, 0)

	// Force redraw of Victoria Grid
	procInvalidateRect.Call(uintptr(VictoriaGridHWND), 0, 1)
}

func updateSmartListBox() {
	clearListBox(SmartListHWND)
	addListBoxItem(SmartListHWND, "  ID   | Attribute Name                   | Value | Worst | Thresh | Raw Hex        | Status")
	addListBoxItem(SmartListHWND, "  -----------------------------------------------------------------------------------------")
	for _, attr := range SmartAttrs {
		line := fmt.Sprintf("  %-5s | %-32s | %-5d | %-5d | %-6d | %-14s | %s",
			attr.HexID, attr.Name, attr.Value, attr.Worst, attr.Threshold, attr.RawHex, attr.Status)
		addListBoxItem(SmartListHWND, line)
	}
}

func updatePartitionListBox() {
	clearListBox(PartListHWND)
	addListBoxItem(PartListHWND, "  ID    | Volume Label         | FS Type      | Size           | Bootable")
	addListBoxItem(PartListHWND, "  -----------------------------------------------------------------------")
	for _, part := range Partitions {
		boot := "No"
		if part.IsBootable {
			boot = "Yes"
		}
		label := part.Label
		if part.IsUnallocated {
			label = "[Unallocated Space]"
		}
		line := fmt.Sprintf("  %-5d | %-20s | %-12s | %-14s | %s",
			part.ID, label, part.FileSystem, FormatSize(part.SizeBytes), boot)
		addListBoxItem(PartListHWND, line)
	}
}

func triggerAiRepair() {
	if ActiveFailure == nil {
		return
	}
	issue := *ActiveFailure

	// Clear previous editbox logs
	procSetWindowTextW.Call(uintptr(InfoBoxHWND), 0)

	// Log repair sequence to console editbox thread-safely
	threadSafeLog("==== STARTING AI FIRMWARE AUTONOMOUS RESTORATION ====")
	threadSafeLog(fmt.Sprintf("[+] Executing repair patch: %s", issue.TitleEn))
	threadSafeLog(fmt.Sprintf("[+] Diagnosis profile: %s", issue.RecommendEn))

	// Simulate repair stages on background thread
	go func() {
		time.Sleep(800 * time.Millisecond)
		threadSafeLog("• [25%] Establishing low-level register link (BSY=1, DRDY=0)...")
		time.Sleep(800 * time.Millisecond)
		threadSafeLog("• [50%] Sending direct microcode instructions to drive RAM cache...")
		time.Sleep(800 * time.Millisecond)
		threadSafeLog("• [75%] Overwriting SA translator parameters or patching EEPROM ROM...")
		time.Sleep(800 * time.Millisecond)
		threadSafeLog("• [100%] Flushing controller buffers and restarting spindle motor...")
		time.Sleep(500 * time.Millisecond)

		// Fix consequence
		if issue.FirmwareAction == "TRANSLATOR" {
			SelectedDrive.CapacityBytes = 12000398934016
			SelectedDrive.TotalSectors = SelectedDrive.CapacityBytes / 512
			Partitions = GetDefaultPartitions(SelectedDrive.CapacityBytes)
		}
		ActiveFailure = nil
		SmartAttrs = GetMockSmartAttributes(HealthyHDD)

		threadSafeLog("✔ SUCCESS: Low-level drive firmware exception successfully patched and resolved!")
		threadSafeLog("✔ All logical-to-physical block access and platter sectors restored.")

		// Marshal UI update calls back to the main UI thread safely
		time.Sleep(100 * time.Millisecond)

		uStatusText, _ := syscall.UTF16PtrFromString("• حالة الميديا: سليم تماماً ومستقر بعد الإصلاح الذاتي بالذكاء الاصطناعي.\n• Platter status: Healthy & Stabilized via Autonomous AI Repair.")
		procSetWindowTextW.Call(uintptr(StatusLabelHWND), uintptr(unsafe.Pointer(uStatusText)))
		updateSmartListBox()
		updatePartitionListBox()

		msgBox("✔ تم إصلاح وتجاوز عطل الفيرموير بنجاح تام! / Firmware successfully repaired and restored!")
	}()
}

// Thread-safe UI logging helper via Win32 message queue (PostMessage)
func threadSafeLog(text string) {
	uText, _ := syscall.UTF16PtrFromString(text)
	// Post message asynchronously to the main thread's message loop
	procPostMessageW.Call(uintptr(MainHWND), WM_USER_LOG, uintptr(unsafe.Pointer(uText)), 0)
}

// Victoria scanning handlers
func startVictoriaScan() {
	clearListBox(VictoriaHistHWND)
	addListBoxItem(VictoriaHistHWND, "  LBA Block Sector  | Delay Latency (ms)  | Platter Cell Speed")
	addListBoxItem(VictoriaHistHWND, "  -------------------------------------------------------------")
	ScanProgress = 0
	ScannedBlocks = nil
	procSetTimer.Call(uintptr(MainHWND), 1, 150, 0)
}

func updateVictoriaScan() {
	ScanProgress++
	if ScanProgress > 100 {
		procKillTimer.Call(uintptr(MainHWND), 1)
		msgBox("✔ تم فحص كامل خلايا الميديا بنجاح! / Victoria plater block scan complete!")
		return
	}

	procSendMessageW.Call(uintptr(VictoriaProgHWND), PBM_SETPOS, uintptr(ScanProgress), 0)

	// Add simulated block detail
	lba := int64(ScanProgress) * 16384
	latency, delayType := SimulatedLatency(CurrentProfile, ScanProgress, 100)
	latencyStr := strconv.FormatInt(latency, 10) + " ms"
	if delayType == DelayError {
		latencyStr = "ERROR (TIMEOUT)"
	}

	// Real drive direct reading integration
	if SelectedDrive != nil && !SelectedDrive.IsSimulated {
		buf, err := ReadPhysicalSector(SelectedDrive.Path, lba)
		if err == nil && len(buf) == 512 {
			latencyStr = "READ OK (0ms)"
			delayType = Delay3ms
		}
	}

	block := BlockDetail{LBA: lba, LatencyMs: latency, Type: delayType}
	ScannedBlocks = append(ScannedBlocks, block)

	line := fmt.Sprintf("  0x%08X        | %-20s | %s", lba, latencyStr, delayType)
	addListBoxItem(VictoriaHistHWND, line)

	// Force redraw of Victoria Grid to render the new square block
	procInvalidateRect.Call(uintptr(VictoriaGridHWND), 0, 1)
}

// GDI Custom paint for Victoria Heatmap Grid
func drawVictoriaHeatmap() {
	hdc, _, _ := procGetDC.Call(uintptr(VictoriaGridHWND))
	if hdc == 0 {
		return
	}
	defer procReleaseDC.Call(uintptr(VictoriaGridHWND), hdc)

	// Clear canvas to dark SlateDark background
	var rc rect
	procGetClientRect.Call(uintptr(VictoriaGridHWND), uintptr(unsafe.Pointer(&rc)))
	procRectangle.Call(hdc, 0, 0, uintptr(rc.Right), uintptr(rc.Bottom))

	// Draw beautiful grid blocks
	blockWidth := int32(14)
	blockHeight := int32(14)
	spacing := int32(3)

	cols := (rc.Right - spacing) / (blockWidth + spacing)
	if cols <= 0 {
		cols = 1
	}

	for idx, b := range ScannedBlocks {
		col := int32(idx) % cols
		row := int32(idx) / cols

		x := spacing + col*(blockWidth+spacing)
		y := spacing + row*(blockHeight+spacing)

		// Choose high-fidelity color matching the delay type
		var brushColor uint32
		switch b.Type {
		case Delay3ms:
			brushColor = RGB(64, 64, 64)       // Dark Grey
		case Delay10ms:
			brushColor = RGB(160, 160, 160)    // Light Grey
		case Delay50ms:
			brushColor = RGB(57, 255, 20)      // Neon Green
		case Delay150ms:
			brushColor = RGB(245, 158, 11)     // Neon Amber
		case Delay500ms:
			brushColor = RGB(239, 68, 68)      // Red
		case DelayOver:
			brushColor = RGB(139, 92, 246)     // Purple
		case DelayError:
			brushColor = RGB(59, 130, 246)     // Neon Blue
		default:
			brushColor = RGB(64, 64, 64)
		}

		hBrush, _, _ := procCreateSolidBrush.Call(uintptr(brushColor))
		if hBrush != 0 {
			procSelectObject.Call(hdc, hBrush)
			procRectangle.Call(hdc, uintptr(x), uintptr(y), uintptr(x+blockWidth), uintptr(y+blockHeight))
			procDeleteObject.Call(hBrush)
		}
	}
}

// SpinRite Repair handlers
func startSpinRiteRepair() {
	clearListBox(SpinRiteStatsHWND)
	addListBoxItem(SpinRiteStatsHWND, "  LBA Coordinate   | Read Signal (mV)   | Hysteresis Phase Mode")
	addListBoxItem(SpinRiteStatsHWND, "  ---------------------------------------------------------------")
	PulseHistory = make([]int, 70)
	for i := range PulseHistory {
		PulseHistory[i] = 400 + rand.Intn(100)
	}
	OscilloscopeX = 0
	procSetTimer.Call(uintptr(MainHWND), 2, 200, 0)
}

func updateSpinRiteWave() {
	OscilloscopeX++
	if OscilloscopeX > 30 {
		procKillTimer.Call(uintptr(MainHWND), 2)
		msgBox("✔ تم الانتهاء من استعدال نبضات الميديا! / SpinRite signal repair complete!")
		return
	}

	phases := []string{"FORWARD_DEMAG", "REVERSE_DEMAG", "FLUX_INVERSION", "BURST_POLARIZATION"}
	phase := phases[rand.Intn(len(phases))]
	mv := SimulatedOscilloscopePulse(phase, OscilloscopeX)
	PulseHistory = append(PulseHistory[1:], mv)

	lba := int64(OscilloscopeX*4500 + 8500)
	line := fmt.Sprintf("  0x%08X         | %-18s | %s", lba, strconv.Itoa(mv)+" mV", phase)
	addListBoxItem(SpinRiteStatsHWND, line)

	// Draw ascii-oscilloscope on standard label
	waveAscii := GenerateOscilloscopeWaveform(PulseHistory, 6, 68)
	uWave, _ := syscall.UTF16PtrFromString(waveAscii)
	procSetWindowTextW.Call(uintptr(SpinRiteWaveHWND), uintptr(unsafe.Pointer(uWave)))
}

// Partition format and wipe
func formatSelectedPartition() {
	curSel, _, _ := procSendMessageW.Call(uintptr(PartListHWND), 0x0188, 0, 0) // LB_GETCURSEL
	if int(curSel) < 2 {                                              // Ignore headers
		msgBox("يرجى اختيار التقسيم المراد تهيئته من القائمة! / Please select a partition to format.")
		return
	}

	partIdx := int(curSel) - 2
	if partIdx >= 0 && partIdx < len(Partitions) {
		part := Partitions[partIdx]
		if part.IsUnallocated {
			msgBox("لا يمكن تهيئة مساحة فارغة! / Cannot format unallocated space.")
			return
		}

		Partitions[partIdx].FileSystem = "NTFS"
		updatePartitionListBox()
		msgBox(fmt.Sprintf("✔ تم تهيئة وتنسيق التقسيم [%s] بنجاح كامل! / Formatted to NTFS.", part.Label))
	}
}

func wipeSelectedPartition() {
	curSel, _, _ := procSendMessageW.Call(uintptr(PartListHWND), 0x0188, 0, 0) // LB_GETCURSEL
	if int(curSel) < 2 {
		msgBox("يرجى اختيار التقسيم لتصفيره! / Please select a partition to wipe.")
		return
	}

	WipeProgress = 0
	procSetTimer.Call(uintptr(MainHWND), 3, 150, 0)
}

func updateWipeProgress() {
	WipeProgress += 10
	if WipeProgress > 100 {
		procKillTimer.Call(uintptr(MainHWND), 3)
		msgBox("✔ تم مسح وتصفير كافة قطاعات البارتشن بنجاح! / Secure zero-wipe complete!")
		return
	}
	procSendMessageW.Call(uintptr(PartWipeProgHWND), PBM_SETPOS, uintptr(WipeProgress), 0)
}

// Carver handlers
func startDeepCarver() {
	clearListBox(CarveListHWND)
	addListBoxItem(CarveListHWND, "  Carved File Name        | File Type  | Capacity  | Sector LBA Coordinate | Integrity")
	addListBoxItem(CarveListHWND, "  --------------------------------------------------------------------------------")

	CarvedFiles = DeepScanFileCarver(SelectedDrive.Path, 0, 1000, SelectedDrive.IsSimulated, false)
	for _, f := range CarvedFiles {
		line := fmt.Sprintf("  %-23s | %-9s | %-9s | %-21d | %s",
			f.Name, f.Type, FormatSize(f.SizeBytes), f.SectorLBA, f.Integrity)
		addListBoxItem(CarveListHWND, line)
	}

	msgBox(fmt.Sprintf("✔ تم العثور واسترجاع %d ملفاً محذوفاً بنجاح! / Carved %d files.", len(CarvedFiles), len(CarvedFiles)))
}

func saveCarvedFile() {
	curSel, _, _ := procSendMessageW.Call(uintptr(CarveListHWND), 0x0188, 0, 0) // LB_GETCURSEL
	if int(curSel) < 2 {
		msgBox("يرجى اختيار ملف من قائمة الملفات المسترجعة لحفظه! / Please select a file to save.")
		return
	}

	fileIdx := int(curSel) - 2
	if fileIdx >= 0 && fileIdx < len(CarvedFiles) {
		file := CarvedFiles[fileIdx]
		SaveFileToDisk(file, "./Recovered_Files", false)
		msgBox(fmt.Sprintf("✔ تم حفظ واستعادة الملف المحذوف بنجاح إلى مجلد ./Recovered_Files/\nFile saved: %s", file.Name))
	}
}

// Win32 Helper functions to make UI creation simple
func createStaticLabel(parent syscall.Handle, text string, x, y, w, h int, panelID int) syscall.Handle {
	class, _ := syscall.UTF16PtrFromString("STATIC")
	uText, _ := syscall.UTF16PtrFromString(text)
	hwndChild, _, _ := procCreateWindowExW.Call(
		0,
		uintptr(unsafe.Pointer(class)),
		uintptr(unsafe.Pointer(uText)),
		WS_CHILD|WS_VISIBLE,
		uintptr(x), uintptr(y), uintptr(w), uintptr(h),
		uintptr(parent),
		0, 0, 0,
	)
	hCtrl := syscall.Handle(hwndChild)
	PanelControls[panelID] = append(PanelControls[panelID], hCtrl)
	return hCtrl
}

func createButton(parent syscall.Handle, text string, x, y, w, h int, id int, panelID int) syscall.Handle {
	class, _ := syscall.UTF16PtrFromString("BUTTON")
	uText, _ := syscall.UTF16PtrFromString(text)
	hwndChild, _, _ := procCreateWindowExW.Call(
		0,
		uintptr(unsafe.Pointer(class)),
		uintptr(unsafe.Pointer(uText)),
		WS_CHILD|WS_VISIBLE,
		uintptr(x), uintptr(y), uintptr(w), uintptr(h),
		uintptr(parent),
		uintptr(id), 0, 0,
	)
	hCtrl := syscall.Handle(hwndChild)
	PanelControls[panelID] = append(PanelControls[panelID], hCtrl)
	return hCtrl
}

func createEditBox(parent syscall.Handle, text string, x, y, w, h int, id int, panelID int) syscall.Handle {
	class, _ := syscall.UTF16PtrFromString("EDIT")
	uText, _ := syscall.UTF16PtrFromString(text)
	hwndChild, _, _ := procCreateWindowExW.Call(
		WS_BORDER,
		uintptr(unsafe.Pointer(class)),
		uintptr(unsafe.Pointer(uText)),
		WS_CHILD|WS_VISIBLE|WS_VSCROLL|0x0004|0x0008, // ES_MULTILINE | ES_AUTOVSCROLL
		uintptr(x), uintptr(y), uintptr(w), uintptr(h),
		uintptr(parent),
		uintptr(id), 0, 0,
	)
	hCtrl := syscall.Handle(hwndChild)
	PanelControls[panelID] = append(PanelControls[panelID], hCtrl)
	return hCtrl
}

func createComboBox(parent syscall.Handle, x, y, w, h int, id int, panelID int) {
	class, _ := syscall.UTF16PtrFromString("COMBOBOX")
	hwndChild, _, _ := procCreateWindowExW.Call(
		WS_BORDER,
		uintptr(unsafe.Pointer(class)),
		0,
		WS_CHILD|WS_VISIBLE|CBS_DROPDOWNLIST|CBS_HASSTRINGS,
		uintptr(x), uintptr(y), uintptr(w), uintptr(h),
		uintptr(parent),
		uintptr(id), 0, 0,
	)
	DriveComboHWND = syscall.Handle(hwndChild)
	PanelControls[panelID] = append(PanelControls[panelID], DriveComboHWND)
}

func createListBox(parent syscall.Handle, x, y, w, h int, id int, panelID int) syscall.Handle {
	class, _ := syscall.UTF16PtrFromString("LISTBOX")
	hwndChild, _, _ := procCreateWindowExW.Call(
		WS_BORDER,
		uintptr(unsafe.Pointer(class)),
		0,
		WS_CHILD|WS_VISIBLE|WS_VSCROLL|LBS_NOTIFY,
		uintptr(x), uintptr(y), uintptr(w), uintptr(h),
		uintptr(parent),
		uintptr(id), 0, 0,
	)
	hCtrl := syscall.Handle(hwndChild)
	PanelControls[panelID] = append(PanelControls[panelID], hCtrl)
	return hCtrl
}

func createProgressBar(parent syscall.Handle, x, y, w, h int, panelID int) syscall.Handle {
	class, _ := syscall.UTF16PtrFromString("msctls_progress32")
	hwndChild, _, _ := procCreateWindowExW.Call(
		0,
		uintptr(unsafe.Pointer(class)),
		0,
		WS_CHILD|WS_VISIBLE,
		uintptr(x), uintptr(y), uintptr(w), uintptr(h),
		uintptr(parent),
		0, 0, 0,
	)
	procSendMessageW.Call(uintptr(hwndChild), PBM_SETRANGE, 0, 100|(0<<16))
	hCtrl := syscall.Handle(hwndChild)
	PanelControls[panelID] = append(PanelControls[panelID], hCtrl)
	return hCtrl
}

func addTabItem(text string, index int) {
	class, _ := syscall.UTF16PtrFromString(text)
	var tie [7]uintptr // TCITEM structure
	tie[0] = 0x0001    // TCIF_TEXT
	tie[3] = uintptr(unsafe.Pointer(class))
	procSendMessageW.Call(uintptr(TabHWND), 0x133E, uintptr(index), uintptr(unsafe.Pointer(&tie[0]))) // TCM_INSERTITEMW
}

func populateDrivePresets() {
	// First, scan and append actual physical drives from Windows
	realDrives := EnumeratePhysicalDrives()
	for _, drive := range realDrives {
		DrivesList = append(DrivesList, drive)

		displayLabel := fmt.Sprintf("\\\\.\\PhysicalDrive%d - Real Disk [%s]", drive.Index, FormatSize(drive.CapacityBytes))
		uName, _ := syscall.UTF16PtrFromString(displayLabel)
		procSendMessageW.Call(uintptr(DriveComboHWND), 0x0143, 0, uintptr(unsafe.Pointer(uName))) // CB_ADDSTRING
	}

	// Simulation Profiles fallback options
	presets := []struct {
		name   string
		vendor string
		model  string
		fw     string
		sn     string
		size   int64
		issue  string
	}{
		{"WD Blue 1TB HDD [Healthy Platter]", "Western Digital", "WDC WD10EZEX-08WN4A0", "80.00A80", "WD-WCC6Y1AK7SF2", 1000204886016, ""},
		{"WD Red Plus 12TB (Translator Failure - LBA 0)", "Western Digital", "WDC WD120EFBX-68B0AY0", "85.00A85", "WD-WMC6N0F9L8K2", 0, "TRANSLATOR_FAILURE"},
		{"WD Blue 2TB (Module 32 Slow responding Loop)", "Western Digital", "WDC WD20EZAZ-00GGJB0", "80.00A80", "WD-WCC6Y2BK3SS1", 2000398934016, "WD_SLOW_RESPONSE"},
		{"Seagate CC49 8TB (Terminal locked - LED:000000CC)", "Seagate", "ST8000DM004-2CX188", "CC49", "WFA4N8K2", 8001563222016, "SEAGATE_TERMINAL_LOCK"},
		{"Toshiba Enterprise 16TB (Head 2 Click - pre-amp Fail)", "Toshiba", "MG08ACA16TE", "0101", "Y9K0A028FJ9D", 16000593500160, "PHYSICAL_HEAD_FAILURE"},
		{"Samsung 990 Pro SSD 4TB (Bad Block Platter Cascade)", "Samsung", "SSD 990 PRO 4TB NVMe", "1B2QJXD7", "S73HNE0W302910M", 4000787030016, "BAD_SECTOR_CASCADE"},
	}

	for _, p := range presets {
		DrivesList = append(DrivesList, DriveInfo{
			Index:          len(DrivesList),
			Path:           "\\\\.\\SimulatedDrive" + strconv.Itoa(len(DrivesList)),
			Vendor:         p.vendor,
			Model:          p.model,
			Firmware:       p.fw,
			Serial:         p.sn,
			CapacityBytes:  p.size,
			TotalSectors:   p.size / 512,
			SectorSize:     512,
			ConnectionType: "SATA Bridge (Simulated)",
			IsSimulated:    true,
			ActiveIssue:    p.issue,
		})

		uName, _ := syscall.UTF16PtrFromString("[SIM] " + p.name)
		procSendMessageW.Call(uintptr(DriveComboHWND), 0x0143, 0, uintptr(unsafe.Pointer(uName))) // CB_ADDSTRING
	}

	// Select first drive entry in the list
	procSendMessageW.Call(uintptr(DriveComboHWND), 0x014E, 0, 0) // CB_SETCURSEL
	handleDriveSelection(0)
}

func addListBoxItem(listHWND syscall.Handle, item string) {
	uItem, _ := syscall.UTF16PtrFromString(item)
	procSendMessageW.Call(uintptr(listHWND), 0x0180, 0, uintptr(unsafe.Pointer(uItem))) // LB_ADDSTRING
}

func clearListBox(listHWND syscall.Handle) {
	procSendMessageW.Call(uintptr(listHWND), 0x0184, 0, 0) // LB_RESETCONTENT
}

// Low-level raw string log writer
func addLogToBoxRaw(utf16Ptr *uint16) {
	curLen, _, _ := procSendMessageW.Call(uintptr(InfoBoxHWND), 0x000E, 0, 0) // WM_GETTEXTLENGTH
	procSendMessageW.Call(uintptr(InfoBoxHWND), 0x00B1, curLen, curLen)       // EM_SETSEL
	procSendMessageW.Call(uintptr(InfoBoxHWND), 0x00C2, 0, uintptr(unsafe.Pointer(utf16Ptr))) // EM_REPLACESEL

	// Scroll to bottom
	procSendMessageW.Call(uintptr(InfoBoxHWND), 0x00B6, 0, 1) // EM_LINESCROLL
}

func addLogToBox(log string) {
	uLog, _ := syscall.UTF16PtrFromString(log + "\r\n")
	addLogToBoxRaw(uLog)
}

func msgBox(message string) {
	uTitle, _ := syscall.UTF16PtrFromString("PC-3000 Workstation")
	uMsg, _ := syscall.UTF16PtrFromString(message)
	proc := user32.NewProc("MessageBoxW")
	proc.Call(uintptr(MainHWND), uintptr(unsafe.Pointer(uMsg)), uintptr(unsafe.Pointer(uTitle)), 0) // MB_OK
}
