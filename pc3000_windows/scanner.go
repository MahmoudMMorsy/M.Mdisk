package main

import (
	"fmt"
	"math/rand"
	"time"
)

// ScanMode is the sector operation mode
type ScanMode string

const (
	ModeRead    ScanMode = "READ"
	ModeRemap   ScanMode = "REMAP"
	ModeErase   ScanMode = "ERASE"
	ModeRestore ScanMode = "RESTORE"
)

// BlockDelayType is the categorized latency speed
type BlockDelayType string

const (
	Delay3ms    BlockDelayType = "< 3ms"
	Delay10ms   BlockDelayType = "< 10ms"
	Delay50ms   BlockDelayType = "< 50ms"
	Delay150ms  BlockDelayType = "< 150ms"
	Delay500ms  BlockDelayType = "< 500ms"
	DelayOver   BlockDelayType = "> 500ms"
	DelayError  BlockDelayType = "ERROR"
)

// BlockScanStats reports live metrics
type BlockScanStats struct {
	CurrentLBA      int64
	StartLBA        int64
	EndLBA          int64
	BlocksScanned   int
	BlocksTotal     int
	ElapsedSeconds  float64
	EstRemainingSec float64
	AverageSpeedMbs float64
	RemappedCount   int
	ErrorCount      int
	Bin3            int
	Bin10           int
	Bin50           int
	Bin150          int
	Bin500          int
	BinOver         int
	BinError        int
}

// BlockDetail represents scanned block latency
type BlockDetail struct {
	LBA       int64
	LatencyMs int64
	Type      BlockDelayType
}

// SimulatedLatency calculates block scan delay depending on sector health profile
func SimulatedLatency(profile SmartProfile, blockIdx int, totalBlocks int) (int64, BlockDelayType) {
	r := rand.Float64()

	// High-fidelity specific sector deterioration bands (making it extremely realistic)
	isHotZone := (blockIdx >= totalBlocks/3 && blockIdx <= totalBlocks/3+12) ||
		(blockIdx >= totalBlocks*2/3 && blockIdx <= totalBlocks*2/3+8)

	badChance := 0.001
	slowChance := 0.01

	if profile == FailingHDD || profile == FailingSSD {
		badChance = 0.04
		slowChance = 0.18
	}

	if r < badChance || (isHotZone && r < 0.25) {
		return 9999, DelayError
	}
	if r < slowChance || (isHotZone && r < 0.65) {
		latency := int64(150 + rand.Intn(700))
		if latency > 500 {
			return latency, DelayOver
		}
		return latency, Delay500ms
	}

	// Normal healthy bands
	if r < 0.60 {
		return int64(1 + rand.Intn(2)), Delay3ms
	} else if r < 0.85 {
		return int64(3 + rand.Intn(7)), Delay10ms
	} else if r < 0.95 {
		return int64(10 + rand.Intn(40)), Delay50ms
	}
	return int64(50 + rand.Intn(100)), Delay150ms
}

// PrintAnsiHeatmapBlock returns the ANSI escape-coded block representation
func PrintAnsiHeatmapBlock(delayType BlockDelayType) string {
	switch delayType {
	case Delay3ms:
		return "\033[90m■\033[0m" // Dark Grey
	case Delay10ms:
		return "\033[37m■\033[0m" // Light Grey
	case Delay50ms:
		return "\033[32m■\033[0m" // Green
	case Delay150ms:
		return "\033[33m■\033[0m" // Amber
	case Delay500ms:
		return "\033[31m■\033[0m" // Orange-Red
	case DelayOver:
		return "\033[35m■\033[0m" // Violet-Red (Deep delay)
	case DelayError:
		return "\033[94m■\033[0m" // Bright Blue (Bad Sector)
	default:
		return "■"
	}
}

// RenderHeatmapLine formats a slice of blocks to display a compact live map
func RenderHeatmapLine(blocks []BlockDetail, width int) string {
	line := ""
	for i, b := range blocks {
		if i > 0 && i%width == 0 {
			line += "\n"
		}
		line += PrintAnsiHeatmapBlock(b.Type) + " "
	}
	return line
}

// FormatStats prints a beautifully aligned status box in English and Arabic
func FormatStats(stats BlockScanStats, mode ScanMode, arabic bool) {
	progress := 0.0
	if stats.BlocksTotal > 0 {
		progress = (float64(stats.BlocksScanned) / float64(stats.BlocksTotal)) * 100.0
	}

	if arabic {
		fmt.Printf("\n┌──────────────────────────── إحصائيات فحص الميديا ────────────────────────────┐\n")
		fmt.Printf("│ • النمط النشط: %-15s | نسبة الإنجاز: %-6.1f%%                      │\n", mode, progress)
		fmt.Printf("│ • العنوان المنطقي LBA: %-12d | القطاعات التالفة: %-12d            │\n", stats.CurrentLBA, stats.ErrorCount)
		fmt.Printf("│ • السرعة المتوسطة: %-8.1f MB/s | القطاعات المصلحة (Remap): %-12d │\n", stats.AverageSpeedMbs, stats.RemappedCount)
		fmt.Printf("│ • الوقت المنقضي: %-10s | الوقت المتبقي: %-15s         │\n",
			fmt.Sprintf("%.1fث", stats.ElapsedSeconds),
			fmt.Sprintf("%.1fث", stats.EstRemainingSec))
		fmt.Printf("├─────────────────────────────── توزيع زمن الاستجابة ────────────────────────────┤\n")
		fmt.Printf("│ < 3ms (رمادي غامق): %-7d | < 10ms (رمادي فاتح): %-7d | < 50ms (أخضر): %-7d  │\n", stats.Bin3, stats.Bin10, stats.Bin50)
		fmt.Printf("│ < 150ms (برتقالي):  %-7d | < 500ms (أحمر فاتح): %-7d | > 500ms (أحمر):%-7d  │\n", stats.Bin150, stats.Bin500, stats.BinOver)
		fmt.Printf("│ قطاعات تالفة (أزرق): %-54d │\n", stats.BinError)
		fmt.Printf("└────────────────────────────────────────────────────────────────────────────────┘\n")
	} else {
		fmt.Printf("\n┌───────────────────────────── MEDIA SCAN STATISTICS ────────────────────────────┐\n")
		fmt.Printf("│ • Active Mode: %-15s | Progress: %-6.1f%%                         │\n", mode, progress)
		fmt.Printf("│ • Current LBA: %-15d | Bad Sectors: %-15d              │\n", stats.CurrentLBA, stats.ErrorCount)
		fmt.Printf("│ • Average Speed: %-10.1f MB/s | Sectors Remapped: %-12d            │\n", stats.AverageSpeedMbs, stats.RemappedCount)
		fmt.Printf("│ • Elapsed Time: %-11s | Est. Remaining: %-16s       │\n",
			fmt.Sprintf("%.1fs", stats.ElapsedSeconds),
			fmt.Sprintf("%.1fs", stats.EstRemainingSec))
		fmt.Printf("├──────────────────────────────── LATENCY HISTOGRAM ─────────────────────────────┤\n")
		fmt.Printf("│ < 3ms (Dark Grey): %-8d | < 10ms (Lt Grey): %-8d | < 50ms (Green): %-8d   │\n", stats.Bin3, stats.Bin10, stats.Bin50)
		fmt.Printf("│ < 150ms (Amber):   %-8d | < 500ms (Orange): %-8d | > 500ms (Red):  %-8d   │\n", stats.Bin150, stats.Bin500, stats.BinOver)
		fmt.Printf("│ ERROR (Bright Blue): %-58d │\n", stats.BinError)
		fmt.Printf("└────────────────────────────────────────────────────────────────────────────────┘\n")
	}
}

// SleepMs mock delay
func SleepMs(ms int) {
	time.Sleep(time.Duration(ms) * time.Millisecond)
}
