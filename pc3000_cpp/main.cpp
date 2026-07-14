#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif

#include <windows.h>
#include <commctrl.h>
#include <iostream>
#include <vector>
#include <string>
#include <sstream>
#include <iomanip>
#include <algorithm>
#include <chrono>
#include <thread>
#include <memory>
#include <random>

// Ensure common controls are linked
#pragma comment(lib, "comctl32.lib")
#pragma comment(lib, "user32.lib")
#pragma comment(lib, "gdi32.lib")

// Low-Level Structure Definitions
struct Partition {
    int id;
    std::wstring label;
    std::wstring fileSystem; // FAT32, NTFS, exFAT, ext4, Unallocated
    long long sizeBytes;
    bool isBootable;
    bool isUnallocated;
};

struct DriveInfo {
    int index;
    std::wstring path;
    std::wstring vendor;
    std::wstring model;
    std::wstring firmware;
    std::wstring serial;
    long long capacityBytes;
    long long totalSectors;
    int sectorSize;
    std::wstring connectionType;
    bool isSimulated;
    std::wstring activeIssue;
};

struct SmartAttribute {
    int id;
    std::wstring hexId;
    std::wstring name;
    int value;
    int worst;
    int threshold;
    long long rawValue;
    std::wstring rawHex;
    std::wstring status; // OK, WARNING, CRITICAL
    bool isCritical;
};

struct DefectRecord {
    long long lba;
    int cylinder;
    int head;
    int sector;
    std::wstring type;
    std::wstring source; // P-List, G-List
};

struct FirmwareModule {
    std::wstring id;
    std::wstring name;
    int sizeSectors;
    std::wstring status; // OK, CORRUPT, UNREADABLE
    std::wstring location;
};

struct RecoveredFile {
    std::wstring name;
    std::wstring type;
    long long sizeBytes;
    std::wstring integrity;
    std::wstring signature;
    long long sectorLba;
};

// Global State
std::vector<DriveInfo> g_Drives;
DriveInfo* g_SelectedDrive = nullptr;
std::vector<Partition> g_Partitions;
std::vector<SmartAttribute> g_SmartAttrs;
std::vector<DefectRecord> g_DefectTable;
std::vector<FirmwareModule> g_FwModules;
std::vector<RecoveredFile> g_CarvedFiles;
std::vector<int> g_PulseHistory(70, 500);

// Global UI Handles
HWND g_MainHWND = NULL;
HWND g_TabHWND = NULL;
HWND g_DriveCombo = NULL;
HWND g_StatusLabel = NULL;
HWND g_InfoLabel = NULL;
HWND g_InfoBox = NULL;
HWND g_SmartList = NULL;
HWND g_VictoriaProg = NULL;
HWND g_VictoriaList = NULL;
HWND g_SpinRiteWave = NULL;
HWND g_SpinRiteList = NULL;
HWND g_PartList = NULL;
HWND g_PartWipeProg = NULL;
HWND g_CarveList = NULL;
HWND g_ImagerList = NULL;
HWND g_FwSaList = NULL;

// Timers
UINT_PTR g_ScanTimer = 0;
UINT_PTR g_SpinTimer = 0;
UINT_PTR g_WipeTimer = 0;
UINT_PTR g_ImagerTimer = 0;

int g_ScanProgress = 0;
int g_SpinProgress = 0;
int g_WipeProgress = 0;
long long g_ImagerLba = 0;

// Panel control vectors (1-based index)
std::vector<HWND> g_PanelControls[9];

// Helper Functions
std::wstring FormatSize(long long bytes) {
    const long long KB = 1024;
    const long long MB = KB * 1024;
    const long long GB = MB * 1024;
    const long long TB = GB * 1024;

    std::wstringstream ss;
    ss << std::fixed << std::setprecision(2);
    if (bytes >= TB) ss << (double)bytes / TB << L" TB";
    else if (bytes >= GB) ss << (double)bytes / GB << L" GB";
    else if (bytes >= MB) ss << (double)bytes / MB << L" MB";
    else if (bytes >= KB) ss << (double)bytes / KB << L" KB";
    else ss << bytes << L" B";
    return ss.str();
}

std::vector<Partition> GetDefaultPartitions(long long capacityBytes) {
    std::vector<Partition> list;
    long long MB = 1024 * 1024;
    long long GB = MB * 1024;

    if (capacityBytes > 500 * GB) {
        list.push_back({1, L"SYSTEM_RESERVED", L"FAT32", 500 * MB, true, false});
        list.push_back({2, L"OS_WIN10", L"NTFS", (long long)(capacityBytes * 0.4), false, false});
        list.push_back({3, L"DATA_VOLUME", L"exFAT", (long long)(capacityBytes * 0.5), false, false});
        list.push_back({4, L"UNALLOCATED_POOL", L"Unallocated", (long long)(capacityBytes * 0.1), false, true});
    } else {
        list.push_back({1, L"EFI_BOOT", L"FAT32", 200 * MB, true, false});
        list.push_back({2, L"RECOVERY_IMAGE", L"NTFS", 15 * GB, false, false});
        list.push_back({3, L"FAT32_SHARE", L"FAT32", (long long)(capacityBytes * 0.75), false, false});
        list.push_back({4, L"RAW_EMPTY", L"Unallocated", (long long)(capacityBytes * 0.1), false, true});
    }
    return list;
}

std::vector<SmartAttribute> GetMockSmartAttributes(bool healthy) {
    std::vector<SmartAttribute> list;
    if (healthy) {
        list.push_back({0x01, L"0x01", L"Raw Read Error Rate", 100, 100, 51, 0, L"0x000000000000", L"OK", true});
        list.push_back({0x03, L"0x03", L"Spin-Up Time", 92, 92, 21, 3810, L"0x000000000EE2", L"OK", false});
        list.push_back({0x05, L"0x05", L"Reallocated Sectors Count", 200, 200, 140, 0, L"0x000000000000", L"OK", true});
        list.push_back({0x09, L"0x09", L"Power-On Hours", 89, 89, 0, 9420, L"0x0000000024CC", L"OK", false});
        list.push_back({0xC2, L"0xC2", L"Temperature", 38, 38, 0, 38, L"0x000000000026", L"OK", false});
        list.push_back({0xC5, L"0xC5", L"Current Pending Sector Count", 200, 200, 0, 0, L"0x000000000000", L"OK", true});
    } else {
        list.push_back({0x01, L"0x01", L"Raw Read Error Rate", 42, 38, 51, 8824, L"0x000000002278", L"CRITICAL", true});
        list.push_back({0x03, L"0x03", L"Spin-Up Time", 84, 84, 21, 5910, L"0x000000001716", L"OK", false});
        list.push_back({0x05, L"0x05", L"Reallocated Sectors Count", 112, 112, 140, 184, L"0x0000000000B8", L"CRITICAL", true});
        list.push_back({0x09, L"0x09", L"Power-On Hours", 42, 42, 0, 48210, L"0x00000000BC52", L"OK", false});
        list.push_back({0xC2, L"0xC2", L"Temperature", 54, 58, 0, 54, L"0x000000000036", L"WARNING", false});
        list.push_back({0xC5, L"0xC5", L"Current Pending Sector Count", 130, 130, 0, 85, L"0x000000000055", L"CRITICAL", true});
    }
    return list;
}

std::vector<DefectRecord> GetDefaultDefects() {
    return {
        {204848, 122, 0, 14, L"Physical Surface", L"P-List"},
        {10543900, 1140, 2, 89, L"Magnetic Weakness", L"P-List"},
        {88439200, 8740, 1, 102, L"Servo Disturbance", L"P-List"},
    };
}

std::vector<FirmwareModule> GetDefaultFwModules() {
    return {
        {L"0x01", L"Directory Index (DIR)", 16, L"OK", L"SA Cyl -2"},
        {L"0x02", L"Configuration Overlay (CONFIG)", 32, L"OK", L"SA Cyl -2"},
        {L"0x03", L"Zone Translation Table (ZONES)", 128, L"OK", L"SA Cyl -2"},
        {L"0x11", L"S.M.A.R.T. Log Database (SMART)", 64, L"CORRUPT", L"SA Cyl -1"},
        {L"0x28", L"Primary Defect Table (P-List)", 512, L"OK", L"SA Cyl -1"},
        {L"0x35", L"Grown Defect Table (G-List)", 128, L"UNREADABLE", L"SA Cyl -1"},
    };
}

// GUI Element Helpers
HWND CreateStaticLabel(HWND parent, std::wstring text, int x, int y, int w, int h, int panelID) {
    HWND hwnd = CreateWindowEx(0, L"STATIC", text.c_str(), WS_CHILD | WS_VISIBLE, x, y, w, h, parent, NULL, NULL, NULL);
    g_PanelControls[panelID].push_back(hwnd);
    return hwnd;
}

HWND CreateButton(HWND parent, std::wstring text, int x, int y, int w, int h, int id, int panelID) {
    HWND hwnd = CreateWindowEx(0, L"BUTTON", text.c_str(), WS_CHILD | WS_VISIBLE, x, y, w, h, parent, (HMENU)(INT_PTR)id, NULL, NULL);
    g_PanelControls[panelID].push_back(hwnd);
    return hwnd;
}

HWND CreateListBox(HWND parent, int x, int y, int w, int h, int id, int panelID) {
    HWND hwnd = CreateWindowEx(WS_EX_CLIENTEDGE, L"LISTBOX", L"", WS_CHILD | WS_VISIBLE | WS_VSCROLL | LBS_NOTIFY, x, y, w, h, parent, (HMENU)(INT_PTR)id, NULL, NULL);
    g_PanelControls[panelID].push_back(hwnd);
    return hwnd;
}

HWND CreateProgressBar(HWND parent, int x, int y, int w, int h, int panelID) {
    HWND hwnd = CreateWindowEx(0, PROGRESS_CLASS, L"", WS_CHILD | WS_VISIBLE, x, y, w, h, parent, NULL, NULL, NULL);
    SendMessage(hwnd, PBM_SETRANGE, 0, MAKELPARAM(0, 100));
    g_PanelControls[panelID].push_back(hwnd);
    return hwnd;
}

HWND CreateEditBox(HWND parent, std::wstring text, int x, int y, int w, int h, int id, int panelID) {
    HWND hwnd = CreateWindowEx(WS_EX_CLIENTEDGE, L"EDIT", text.c_str(), WS_CHILD | WS_VISIBLE | WS_VSCROLL | ES_MULTILINE | ES_AUTOVSCROLL | ES_READONLY, x, y, w, h, parent, (HMENU)(INT_PTR)id, NULL, NULL);
    g_PanelControls[panelID].push_back(hwnd);
    return hwnd;
}

void AddListBoxItem(HWND listbox, std::wstring text) {
    SendMessage(listbox, LB_ADDSTRING, 0, (LPARAM)text.c_str());
}

void ClearListBox(HWND listbox) {
    SendMessage(listbox, LB_RESETCONTENT, 0, 0);
}

void AddTabItem(HWND tab, std::wstring text, int index) {
    TCITEM tie;
    tie.mask = TCIF_TEXT;
    tie.pszText = (LPWSTR)text.c_str();
    SendMessage(tab, TCM_INSERTITEM, index, (LPARAM)&tie);
}

// Logic implementations for GUI
void UpdateSmartListBox() {
    ClearListBox(g_SmartList);
    AddListBoxItem(g_SmartList, L"  ID   | Attribute Name                   | Value | Worst | Thresh | Raw Hex        | Status");
    AddListBoxItem(g_SmartList, L"  -----------------------------------------------------------------------------------------");
    for (const auto& attr : g_SmartAttrs) {
        std::wstringstream ss;
        ss << L"  " << std::left << std::setw(5) << attr.hexId
           << L" | " << std::left << std::setw(32) << attr.name
           << L" | " << std::left << std::setw(5) << attr.value
           << L" | " << std::left << std::setw(5) << attr.worst
           << L" | " << std::left << std::setw(6) << attr.threshold
           << L" | " << std::left << std::setw(14) << attr.rawHex
           << L" | " << attr.status;
        AddListBoxItem(g_SmartList, ss.str());
    }
}

void UpdatePartitionListBox() {
    ClearListBox(g_PartList);
    AddListBoxItem(g_PartList, L"  ID    | Volume Label         | FS Type      | Size           | Bootable");
    AddListBoxItem(g_PartList, L"  -----------------------------------------------------------------------");
    for (const auto& part : g_Partitions) {
        std::wstring boot = part.isBootable ? L"Yes" : L"No";
        std::wstring label = part.isUnallocated ? L"[Unallocated Space]" : part.label;
        std::wstringstream ss;
        ss << L"  " << std::left << std::setw(5) << part.id
           << L" | " << std::left << std::setw(20) << label
           << L" | " << std::left << std::setw(12) << part.fileSystem
           << L" | " << std::left << std::setw(14) << FormatSize(part.sizeBytes)
           << L" | " << boot;
        AddListBoxItem(g_PartList, ss.str());
    }
}

void UpdateFwSaListBox() {
    ClearListBox(g_FwSaList);
    AddListBoxItem(g_FwSaList, L"  ID   | Firmware Module Name             | Size (Sect) | SA Location  | Status");
    AddListBoxItem(g_FwSaList, L"  -------------------------------------------------------------------------------");
    for (const auto& m : g_FwModules) {
        std::wstringstream ss;
        ss << L"  " << std::left << std::setw(4) << m.id
           << L" | " << std::left << std::setw(32) << m.name
           << L" | " << std::left << std::setw(11) << m.sizeSectors
           << L" | " << std::left << std::setw(12) << m.location
           << L" | " << m.status;
        AddListBoxItem(g_FwSaList, ss.str());
    }
}

void ThreadSafeLog(std::wstring text) {
    if (g_InfoBox) {
        int len = GetWindowTextLength(g_InfoBox);
        SendMessage(g_InfoBox, EM_SETSEL, len, len);
        SendMessage(g_InfoBox, EM_REPLACESEL, 0, (LPARAM)(text + L"\r\n").c_str());
    }
}

void HandleDriveSelection(int selIndex) {
    if (selIndex < 0 || selIndex >= (int)g_Drives.size()) return;

    g_SelectedDrive = &g_Drives[selIndex];
    bool healthy = g_SelectedDrive->activeIssue.empty();

    g_SmartAttrs = GetMockSmartAttributes(healthy);
    g_Partitions = GetDefaultPartitions(g_SelectedDrive->capacityBytes);
    g_FwModules = GetDefaultFwModules();

    std::wstring statusText = healthy ?
        L"• حالة الميديا: سليم تماماً وبحالة فيزيائية ممتازة. جاهز للفحص.\r\n• Platter status: Healthy & Stable. Ready." :
        L"• عطل الميكروكود المكتشف: " + g_SelectedDrive->activeIssue + L"\r\n• Failure state registered in SA modules.";

    SetWindowText(g_StatusLabel, statusText.c_str());

    std::wstring infoText = L"• SN: " + g_SelectedDrive->serial + L" | FW: " + g_SelectedDrive->firmware + L" | Size: " + FormatSize(g_SelectedDrive->capacityBytes);
    SetWindowText(g_InfoLabel, infoText.c_str());

    UpdateSmartListBox();
    UpdatePartitionListBox();
    UpdateFwSaListBox();

    ClearListBox(g_VictoriaList);
    ClearListBox(g_SpinRiteList);
    ClearListBox(g_CarveList);
    ClearListBox(g_ImagerList);
}

void ShowPanelControls(int panelID, bool show) {
    int cmd = show ? SW_SHOW : SW_HIDE;
    for (HWND hwnd : g_PanelControls[panelID]) {
        ShowWindow(hwnd, cmd);
    }
}

void SwitchPanel(int panelID) {
    for (int i = 1; i <= 8; i++) {
        ShowPanelControls(i, i == panelID + 1);
    }
}

// CLI Interactive Console Mode
void RunCLIMode() {
    std::wcout << L"\n\033[95m====================================================================\033[0m" << std::endl;
    std::wcout << L"\033[96m    NOOR OS / PC-3000 ADVANCED STORAGE & FIRMWARE SUITE (C++ NATIVE) \033[0m" << std::endl;
    std::wcout << L"\033[95m====================================================================\033[0m" << std::endl;

    g_Drives = {
        {0, L"\\\\.\\PhysicalDrive0", L"Western Digital", L"WDC WD10EZEX-08WN4A0", L"80.00A80", L"WD-WCC6Y1AK7SF2", 1000204886016, 1000204886016 / 512, 512, L"SATA Direct", true, L""},
        {1, L"\\\\.\\PhysicalDrive1", L"Western Digital", L"WDC WD120EFBX-68B0AY0", L"85.00A85", L"WD-WMC6N0F9L8K2", 0, 0, 512, L"SATA Bridge (Simulated)", true, L"TRANSLATOR_FAILURE"},
    };

    g_SelectedDrive = &g_Drives[0];
    g_SmartAttrs = GetMockSmartAttributes(true);
    g_Partitions = GetDefaultPartitions(g_SelectedDrive->capacityBytes);
    g_DefectTable = GetDefaultDefects();
    g_FwModules = GetDefaultFwModules();

    while (true) {
        std::wcout << L"\n\033[92m[+] Selected Drive: " << g_SelectedDrive->vendor << L" " << g_SelectedDrive->model << L" (" << FormatSize(g_SelectedDrive->capacityBytes) << L")\033[0m" << std::endl;
        std::wcout << L"--------------------------------------------------------------------" << std::endl;
        std::wcout << L"1. Select Target Physical Disk / اختيار القرص الصلب" << std::endl;
        std::wcout << L"2. S.M.A.R.T. Health Telemetry & Diagnostics / فحص المستشعرات" << std::endl;
        std::wcout << L"3. Victoria Platter Latency Scan / فحص قطاعات القرص فيكتوريا" << std::endl;
        std::wcout << L"4. SpinRite Magnetic Pulse Surface Recovery / بث نبضات المغنطة" << std::endl;
        std::wcout << L"5. Partition Management & MBR/GPT Recovery / إدارة الأقسام" << std::endl;
        std::wcout << L"6. Deep File Carver Signature Recovery / استعادة الملفات" << std::endl;
        std::wcout << L"7. Advanced Imager Core (Skip/Retry/Reverse) / الاستنساخ المتقدم" << std::endl;
        std::wcout << L"8. Firmware SA / Boot Maintenance / صيانة الفيرموير ومناطق الخدمة" << std::endl;
        std::wcout << L"9. Exit / خروج" << std::endl;
        std::wcout << L"--------------------------------------------------------------------" << std::endl;
        std::wcout << L"\033[93mEnter choice [1-9]: \033[0m";

        int choice = 0;
        std::cin >> choice;
        if (std::cin.fail()) {
            std::cin.clear();
            std::cin.ignore(10000, '\n');
            continue;
        }

        if (choice == 9) {
            std::wcout << L"\nThank you for choosing Noor OS C++ Core. Exiting..." << std::endl;
            break;
        }

        switch (choice) {
        case 1: {
            std::wcout << L"\n[ Available Drives / الأقراص المتاحة ]:" << std::endl;
            for (size_t i = 0; i < g_Drives.size(); ++i) {
                std::wcout << i + 1 << L". " << g_Drives[i].vendor << L" " << g_Drives[i].model << L" [" << FormatSize(g_Drives[i].capacityBytes) << L"]" << std::endl;
            }
            std::wcout << L"Choose Drive Index: ";
            int idx = 0;
            std::cin >> idx;
            if (idx >= 1 && idx <= (int)g_Drives.size()) {
                g_SelectedDrive = &g_Drives[idx - 1];
                bool healthy = g_SelectedDrive->activeIssue.empty();
                g_SmartAttrs = GetMockSmartAttributes(healthy);
                g_Partitions = GetDefaultPartitions(g_SelectedDrive->capacityBytes);
                std::wcout << L"\033[92m✔ Drive selected successfully!\033[0m" << std::endl;
            }
            break;
        }
        case 2:
            std::wcout << L"\n[ S.M.A.R.T. Health Telemetry Mappings ]:" << std::endl;
            for (const auto& attr : g_SmartAttrs) {
                std::wcout << attr.hexId << L" | " << attr.name << L" | Val: " << attr.value << L" | " << attr.status << std::endl;
            }
            break;

        case 3: {
            std::wcout << L"\nStarting Victoria Latency Platter Block Scan..." << std::endl;
            for (int i = 1; i <= 40; ++i) {
                std::this_thread::sleep_for(std::chrono::milliseconds(20));
                std::wcout << L"\033[92m■\033[0m ";
                if (i % 20 == 0) std::wcout << std::endl;
            }
            std::wcout << L"\n\033[92m✔ Platter media scan completed successfully!\033[0m" << std::endl;
            break;
        }
        case 4:
            std::wcout << L"\nStarting SpinRite Magnetic Hysteresis Surface Realignment..." << std::endl;
            std::wcout << L"Waveform Phase: BURST_POLARIZATION (782 mV)" << std::endl;
            std::wcout << L"\033[92m✔ Realignment pulses completed successfully!\033[0m" << std::endl;
            break;

        case 5: {
            std::wcout << L"\n[ Partition Volume Management & Layout ]" << std::endl;
            for (const auto& part : g_Partitions) {
                std::wcout << L"ID: " << part.id << L" | FS: " << part.fileSystem << L" | Size: " << FormatSize(part.sizeBytes) << std::endl;
            }
            std::wcout << L"\nOptions: 1. Create, 2. Delete, 3. Rebuild Table MBR/GPT" << std::endl;
            int opt = 0;
            std::cin >> opt;
            if (opt == 3) {
                std::wcout << L"Rebuilding corrupt partitions headers... Success!" << std::endl;
            }
            break;
        }
        case 6:
            std::wcout << L"\nInitiating Deep Signature Carver Scanner on raw platter layers..." << std::endl;
            std::wcout << L"Found: 1. SYSTEM_LOG.TXT (Excellent Integrity)" << std::endl;
            std::wcout << L"Found: 2. REPORT.HTML (Excellent Integrity)" << std::endl;
            break;

        case 7:
            std::wcout << L"\n[ Advanced Imager Core (Skip / Retry / Reverse) ]" << std::endl;
            std::wcout << L"• Timeout Limit: 2500ms | Direction: Reverse | Bad Sector Skip: True" << std::endl;
            std::wcout << L"✔ Disk image task successfully completed. saved to ./DiskImage_NoorOS_C.img" << std::endl;
            break;

        case 8:
            std::wcout << L"\n[ Firmware Service Area & Boot Maintenance ]" << std::endl;
            std::wcout << L"1. Reset Busy State, 2. Security Erase, 3. Patch ROM Head Map" << std::endl;
            int fwOpt = 0;
            std::cin >> fwOpt;
            if (fwOpt == 3) {
                std::wcout << L"ROM patched successfully! Faulty Head 2 disabled." << std::endl;
            }
            break;
        }
    }
}

// Window Procedure for C++ Win32 GUI Dashboard
LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;

    case WM_CREATE: {
        // Tab Control
        g_TabHWND = CreateWindowEx(0, WC_TABCONTROL, L"", WS_CHILD | WS_VISIBLE, 10, 10, 810, 580, hwnd, NULL, NULL, NULL);
        AddTabItem(g_TabHWND, L"1. AI Repair / إصلاح ذكي", 0);
        AddTabItem(g_TabHWND, L"2. S.M.A.R.T. / فحص ذكي", 1);
        AddTabItem(g_TabHWND, L"3. Victoria Scanner / مسح الميديا", 2);
        AddTabItem(g_TabHWND, L"4. SpinRite Pulse / مغنطة الخلايا", 3);
        AddTabItem(g_TabHWND, L"5. Partition Magic / التقسيمات", 4);
        AddTabItem(g_TabHWND, L"6. Carver Recovery / استعادة داتا", 5);
        AddTabItem(g_TabHWND, L"7. Advanced Imager / الاستنساخ", 6);
        AddTabItem(g_TabHWND, L"8. Firmware & Boot / الفيرموير", 7);

        // Standard labels always visible
        CreateStaticLabel(hwnd, L"★ ACE LAB PC-3000 WINDOWS C++ CORE UTILITY ★", 20, 45, 780, 25, 0);

        // PANEL 1: AI Repair & Control
        CreateStaticLabel(hwnd, L"Select HDD Interface / حدد القرص المراد فحصه:", 30, 80, 400, 20, 1);
        g_DriveCombo = CreateWindowEx(0, L"COMBOBOX", L"", WS_CHILD | WS_VISIBLE | CBS_DROPDOWNLIST, 30, 105, 450, 200, hwnd, (HMENU)1, NULL, NULL);
        g_PanelControls[1].push_back(g_DriveCombo);

        CreateStaticLabel(hwnd, L"STATUS DIAGNOSTICS REGISTERS / تقييم صحة الميديا:", 30, 150, 450, 20, 1);
        g_StatusLabel = CreateStaticLabel(hwnd, L"• Drive Status: Standby / بانتظار تحديد القرص الصلب لتقييمه.", 30, 175, 450, 40, 1);
        g_InfoLabel = CreateStaticLabel(hwnd, L"• Connection Type: Simulated C++ Port Engine.", 30, 215, 450, 20, 1);

        CreateButton(hwnd, L"APPLY AI AUTO-REPAIR / تطبيق الصيانة التلقائية", 30, 260, 450, 40, 101, 1);
        g_InfoBox = CreateEditBox(hwnd, L"ACE Lab C++ Advanced Firmware Recovery Console Loaded.", 30, 320, 450, 230, 102, 1);

        // PANEL 2: S.M.A.R.T
        CreateStaticLabel(hwnd, L"S.M.A.R.T. Health Telemetry Mappings / تفاصيل مستشعرات صحة الهارد ديسك:", 30, 80, 750, 20, 2);
        g_SmartList = CreateListBox(hwnd, 30, 110, 750, 440, 201, 2);

        // PANEL 3: Victoria Scanner
        CreateStaticLabel(hwnd, L"Victoria Sector Latency block scan / فحص قطاعات القرص ببرنامج فيكتوريا:", 30, 80, 750, 20, 3);
        CreateButton(hwnd, L"START PLATER HEATMAP SCAN / بدء فحص قطاعات الميديا", 30, 110, 500, 40, 301, 3);
        g_VictoriaProg = CreateProgressBar(hwnd, 30, 160, 500, 25, 3);
        g_VictoriaList = CreateListBox(hwnd, 30, 200, 750, 350, 302, 3);

        // PANEL 4: SpinRite Pulse
        CreateStaticLabel(hwnd, L"SpinRite Magnetic Pulse Surface Recovery / نبضات استعدال المغنطة السطحية:", 30, 80, 750, 20, 4);
        CreateButton(hwnd, L"INITIATE SPINRITE HYSTERESIS / بث نبضات الاستعدال", 30, 110, 500, 40, 401, 4);
        g_SpinRiteWave = CreateStaticLabel(hwnd, L"Oscilloscope Standby - Waveform visualizer.", 30, 160, 750, 120, 4);
        g_SpinRiteList = CreateListBox(hwnd, 30, 290, 750, 260, 402, 4);

        // PANEL 5: Partition Magic
        CreateStaticLabel(hwnd, L"Partition Magic Volume Manager / جدول وتفاصيل تقسيمات الهارد ديسك الحالية:", 30, 80, 750, 20, 5) ;
        g_PartList = CreateListBox(hwnd, 30, 110, 750, 180, 501, 5);
        CreateButton(hwnd, L"Create Partition / إنشاء قسم", 30, 300, 180, 30, 510, 5);
        CreateButton(hwnd, L"Delete Partition / حذف قسم", 220, 300, 180, 30, 511, 5);
        CreateButton(hwnd, L"Resize Partition / تغيير حجم قسم", 410, 300, 180, 30, 512, 5);
        CreateButton(hwnd, L"Rebuild Partition Table (MBR/GPT)", 600, 300, 180, 30, 513, 5);
        CreateButton(hwnd, L"Format Partition (NTFS/FAT32)", 30, 350, 230, 35, 502, 5);
        CreateButton(hwnd, L"Secure Zero-Wipe Volume", 280, 350, 230, 35, 503, 5);
        g_PartWipeProg = CreateProgressBar(hwnd, 30, 400, 480, 25, 5);

        // PANEL 6: Carver Recovery
        CreateStaticLabel(hwnd, L"Deep File signature carver / استعادة الملفات المحذوفة بالتوقيع الرقمي:", 30, 80, 750, 20, 6);
        CreateButton(hwnd, L"RUN DEEP SECTOR CARVER / فحص واستعادة الملفات والبارتشنات", 30, 110, 500, 40, 601, 6);
        g_CarveList = CreateListBox(hwnd, 30, 165, 750, 330, 602, 6);
        CreateButton(hwnd, L"SAVE RECOVERED FILE / حفظ الملف المسترجع على الكمبيوتر", 30, 510, 500, 35, 603, 6);

        // PANEL 7: Advanced Imager
        CreateStaticLabel(hwnd, L"Advanced Physical Imager Engine / استنساخ قطاعات القرص الصلب وتجاوز العيوب:", 30, 80, 750, 20, 7);
        CreateStaticLabel(hwnd, L"Settings / الإعدادات: Direction: Reverse, Timeout: 2500ms, Bad Sector Skip: Yes.", 30, 110, 750, 20, 7);
        CreateButton(hwnd, L"START CLONING PROCESS / بدء عملية الاستنساخ", 30, 140, 350, 40, 701, 7);
        CreateButton(hwnd, L"HALT CLONING / إيقاف مؤقت", 390, 140, 350, 40, 702, 7);
        CreateStaticLabel(hwnd, L"Active Cloning Progress & Log Monitor:", 30, 195, 750, 20, 7);
        g_ImagerList = CreateListBox(hwnd, 30, 220, 750, 320, 703, 7);

        // PANEL 8: Firmware SA & Boot Maintenance
        CreateStaticLabel(hwnd, L"Firmware Service Area & Boot Maintenance / صيانة مناطق الخدمة والإقلاع:", 30, 80, 750, 20, 8);
        CreateButton(hwnd, L"Reset Busy / LED State / إلغاء تجميد الهارد ديسك", 30, 120, 350, 35, 801, 8);
        CreateButton(hwnd, L"ATA / SCSI Security Erase / مسح أمن نهائي", 390, 120, 350, 35, 802, 8);
        CreateButton(hwnd, L"Patch ROM Head Map / تعطيل الرؤوس التالفة بروم", 30, 170, 350, 35, 803, 8);
        CreateButton(hwnd, L"Reconstruct Boot Records / إصلاح ملفات إقلاع الويندوز", 390, 170, 350, 35, 804, 8);
        CreateStaticLabel(hwnd, L"Service Area (SA) Modules Directory Integrity Map:", 30, 215, 750, 20, 8);
        g_FwSaList = CreateListBox(hwnd, 30, 240, 750, 300, 805, 8);

        // Initialize Global Drives List
        g_Drives = {
            {0, L"\\\\.\\PhysicalDrive0", L"Western Digital", L"WDC WD10EZEX-08WN4A0", L"80.00A80", L"WD-WCC6Y1AK7SF2", 1000204886016, 1000204886016 / 512, 512, L"SATA Direct", true, L""},
            {1, L"\\\\.\\PhysicalDrive1", L"Western Digital", L"WDC WD120EFBX-68B0AY0", L"85.00A85", L"WD-WMC6N0F9L8K2", 0, 0, 512, L"SATA Bridge (Simulated)", true, L"TRANSLATOR_FAILURE"},
        };

        for (const auto& drive : g_Drives) {
            std::wstring label = drive.vendor + L" " + drive.model + L" [" + FormatSize(drive.capacityBytes) + L"]";
            SendMessage(g_DriveCombo, CB_ADDSTRING, 0, (LPARAM)label.c_str());
        }

        SendMessage(g_DriveCombo, CB_SETCURSEL, 0, 0);
        HandleDriveSelection(0);
        SwitchPanel(0);
        return 0;
    }

    case WM_COMMAND: {
        int controlID = LOWORD(wParam);
        int notificationCode = HIWORD(wParam);

        if (controlID == 1 && notificationCode == CBN_SELCHANGE) {
            int curSel = SendMessage(g_DriveCombo, CB_GETCURSEL, 0, 0);
            HandleDriveSelection(curSel);
            return 0;
        }

        switch (controlID) {
        case 101: // APPLY AI AUTO-REPAIR
            if (g_SelectedDrive) {
                ThreadSafeLog(L"==== STARTING AI FIRMWARE AUTONOMOUS RESTORATION ====");
                ThreadSafeLog(L"[+] Running patch translation table logic...");
                std::thread([]() {
                    std::this_thread::sleep_for(std::chrono::milliseconds(800));
                    ThreadSafeLog(L"• [25%] Register links established (BSY=1, DRDY=0)...");
                    std::this_thread::sleep_for(std::chrono::milliseconds(800));
                    ThreadSafeLog(L"• [50%] Patching translator modules in SA tracks...");
                    std::this_thread::sleep_for(std::chrono::milliseconds(800));
                    ThreadSafeLog(L"• [100%] Flushing buffers... Restored successfully!");
                    MessageBox(NULL, L"✔ تم إصلاح وتجاوز عطل الفيرموير بنجاح تام! / Firmware repaired!", L"PC-3000 Core", MB_OK);
                }).detach();
            }
            break;

        case 301: // START VICTORIA SCAN
            ClearListBox(g_VictoriaList);
            AddListBoxItem(g_VictoriaList, L"  LBA Block Sector  | Delay Latency (ms)  | Platter Cell Speed");
            g_ScanProgress = 0;
            g_ScanTimer = SetTimer(hwnd, 1, 100, NULL);
            break;

        case 401: // INITIATE SPINRITE
            ClearListBox(g_SpinRiteList);
            AddListBoxItem(g_SpinRiteList, L"  LBA Coordinate   | Read Signal (mV)   | Hysteresis Phase Mode");
            g_SpinProgress = 0;
            g_SpinTimer = SetTimer(hwnd, 2, 100, NULL);
            break;

        case 510: // CREATE PARTITION
            g_Partitions.push_back({(int)g_Partitions.size() + 1, L"NEW_VOLUME", L"NTFS", 20000000000LL, false, false});
            UpdatePartitionListBox();
            MessageBox(NULL, L"✔ Partition created successfully!", L"Partition Magic", MB_OK);
            break;

        case 511: { // DELETE PARTITION
            int curSel = SendMessage(g_PartList, LB_GETCURSEL, 0, 0);
            if (curSel >= 2 && (curSel - 2) < (int)g_Partitions.size()) {
                g_Partitions.erase(g_Partitions.begin() + (curSel - 2));
                UpdatePartitionListBox();
                MessageBox(NULL, L"✔ Partition deleted successfully!", L"Partition Magic", MB_OK);
            }
            break;
        }
        case 513: // REBUILD TABLE MBR/GPT
            g_Partitions = GetDefaultPartitions(g_SelectedDrive->capacityBytes);
            UpdatePartitionListBox();
            MessageBox(NULL, L"✔ MBR/GPT Partition Table rebuilt successfully!", L"Partition Magic", MB_OK);
            break;

        case 701: // START IMAGING
            ClearListBox(g_ImagerList);
            AddListBoxItem(g_ImagerList, L"  LBA Index       | Imaging Strategy       | Read Timeout | Sector Status");
            g_ImagerLba = 0;
            g_ImagerTimer = SetTimer(hwnd, 4, 100, NULL);
            break;

        case 702: // HALT IMAGING
            KillTimer(hwnd, g_ImagerTimer);
            MessageBox(NULL, L"Imaging halted.", L"Imager Core", MB_OK);
            break;

        case 801: // RESET BUSY
            MessageBox(NULL, L"✔ Busy / LED State cleared successfully!", L"Firmware Core", MB_OK);
            break;

        case 802: // SECURITY ERASE
            MessageBox(NULL, L"✔ Security Erase Completed!", L"Firmware Core", MB_OK);
            break;

        case 803: // PATCH ROM HEAD MAP
            MessageBox(NULL, L"✔ ROM Patched! Faulty Head 2 disabled successfully.", L"Firmware Core", MB_OK);
            break;

        case 804: // RECONSTRUCT BOOT RECORDS
            MessageBox(NULL, L"✔ Windows Boot Records reconstructed successfully!", L"Boot Maintenance", MB_OK);
            break;
        }
        return 0;
    }

    case WM_NOTIFY: {
        NMHDR* hdr = (NMHDR*)lParam;
        if (hdr->hwndFrom == g_TabHWND && hdr->code == TCN_SELCHANGE) {
            int curSel = SendMessage(g_TabHWND, TCM_GETCURSEL, 0, 0);
            SwitchPanel(curSel);
        }
        return 0;
    }

    case WM_TIMER: {
        UINT_PTR timerID = wParam;
        if (timerID == 1) { // Victoria Scan
            g_ScanProgress += 5;
            SendMessage(g_VictoriaProg, PBM_SETPOS, g_ScanProgress, 0);
            if (g_ScanProgress >= 100) {
                KillTimer(hwnd, timerID);
                MessageBox(NULL, L"✔ Platter sector latency scan complete!", L"Victoria Core", MB_OK);
            } else {
                std::wstringstream ss;
                ss << L"  0x" << std::hex << std::uppercase << std::setfill(L'0') << std::setw(8) << g_ScanProgress * 16384
                   << L"        | 3 ms                 | Delay3ms";
                AddListBoxItem(g_VictoriaList, ss.str());
            }
        } else if (timerID == 2) { // SpinRite repair
            g_SpinProgress += 5;
            if (g_SpinProgress >= 100) {
                KillTimer(hwnd, timerID);
                MessageBox(NULL, L"✔ SpinRite surface magnetic realignment complete!", L"SpinRite Core", MB_OK);
            } else {
                std::wstringstream ss;
                ss << L"  0x" << std::hex << std::uppercase << std::setfill(L'0') << std::setw(8) << g_SpinProgress * 16384
                   << L"         | 782 mV             | BURST_POLARIZATION";
                AddListBoxItem(g_SpinRiteList, ss.str());
            }
        } else if (timerID == 4) { // Imager clone
            g_ImagerLba += 128;
            if (g_ImagerLba > 1000) {
                KillTimer(hwnd, timerID);
                MessageBox(NULL, L"✔ Sector cloning complete!", L"Imager Core", MB_OK);
            } else {
                std::wstringstream ss;
                ss << L"  0x" << std::hex << std::uppercase << std::setfill(L'0') << std::setw(8) << g_ImagerLba
                   << L"        | Reverse (Safe Platter) | 2500 ms     | SUCCESS";
                AddListBoxItem(g_ImagerList, ss.str());
            }
        }
        return 0;
    }
    }

    return DefWindowProc(hwnd, msg, wParam, lParam);
}

// C++ main entrypoint
int main(int argc, char* argv[]) {
    // Check if CLI parameter is supplied
    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        if (arg == "--cli" || arg == "-cli" || arg == "-c" || arg == "/cli") {
            RunCLIMode();
            return 0;
        }
    }

    // Otherwise, boot the Win32 Desktop Dashboard GUI!
    HINSTANCE hInst = GetModuleHandle(NULL);
    WNDCLASSEX wc;
    ZeroMemory(&wc, sizeof(wc));
    wc.cbSize = sizeof(wc);
    wc.lpfnWndProc = WndProc;
    wc.hInstance = hInst;
    wc.hbrBackground = (HBRUSH)(COLOR_WINDOW + 1);
    wc.lpszClassName = L"PC3000CppWindowClass";

    if (!RegisterClassEx(&wc)) {
        return 1;
    }

    HWND hwnd = CreateWindowEx(
        0, L"PC3000CppWindowClass",
        L"★ Noor OS / PC-3000 Advanced Storage Workstation - C++ NATIVE ★",
        WS_OVERLAPPEDWINDOW | WS_VISIBLE,
        100, 100, 850, 650,
        NULL, NULL, hInst, NULL
    );

    g_MainHWND = hwnd;

    MSG msg;
    while (GetMessage(&msg, NULL, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }

    return 0;
}
