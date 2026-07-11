package main

import (
	"fmt"
	"math"
	"math/rand"
	"strings"
)

// RepairStats represents SpinRite-style keraunic magnetic realignment statistics
type RepairStats struct {
	ActiveSector           int64
	SectorsRepaired        int
	SectorsRecovered       int
	SignalsRegenerated     int
	ScanProgress           float64
	CurrentHysteresisPhase string // "FORWARD_DEMAG", "REVERSE_DEMAG", "FLUX_INVERSION", etc.
	SurfaceVoltageMv       int
	PulseHistory           []int  // sliding window for real-time oscilloscope plotting
}

// GenerateOscilloscopeWaveform draws a beautiful CRT oscilloscope display in ANSI terminal text
func GenerateOscilloscopeWaveform(pulses []int, height int, width int) string {
	if len(pulses) == 0 {
		return "[Oscilloscope Standby - Awaiting SpinRite Pulse Inversion]"
	}

	// Grid canvas
	canvas := make([][]string, height)
	for y := 0; y < height; y++ {
		canvas[y] = make([]string, width)
		for x := 0; x < width; x++ {
			// Oscilloscope grid markers
			if y == height/2 {
				canvas[y][x] = "\033[90m-\033[0m" // center zero axis
			} else if x == width/2 {
				canvas[y][x] = "\033[90m|\033[0m" // vertical phase axis
			} else if y%3 == 0 && x%5 == 0 {
				canvas[y][x] = "\033[90m.\033[0m" // background grid points
			} else {
				canvas[y][x] = " "
			}
		}
	}

	// Plot values onto canvas
	step := float64(len(pulses)) / float64(width)
	for x := 0; x < width; x++ {
		pulseIdx := int(float64(x) * step)
		if pulseIdx >= len(pulses) {
			pulseIdx = len(pulses) - 1
		}

		val := pulses[pulseIdx]
		// Map 0 - 1000mV to canvas height
		yRatio := float64(val) / 1000.0
		yPlot := int(float64(height-1) - (yRatio * float64(height-1)))
		if yPlot < 0 {
			yPlot = 0
		}
		if yPlot >= height {
			yPlot = height - 1
		}

		// Bright Neon Green curve for active signal waveform
		canvas[yPlot][x] = "\033[92m~\033[0m"
	}

	// Format canvas to string
	var sb strings.Builder
	sb.WriteString("\033[36m┌" + strings.Repeat("─", width) + "┐\033[0m\n")
	for y := 0; y < height; y++ {
		sb.WriteString("\033[36m│\033[0m")
		for x := 0; x < width; x++ {
			sb.WriteString(canvas[y][x])
		}
		sb.WriteString("\033[36m│\033[0m\n")
	}
	sb.WriteString("\033[36m└" + strings.Repeat("─", width) + "┘\033[0m")
	return sb.String()
}

// SimulatedOscilloscopePulse generates magnetic flux voltage values for different phases
func SimulatedOscilloscopePulse(phase string, ticks int) int {
	rad := float64(ticks) * 0.25
	sine := math.Sin(rad)
	noise := rand.Float64() * 0.1

	baseAmp := 510.0
	switch phase {
	case "FORWARD_DEMAG":
		baseAmp = 350.0 + (sine * 150.0)
	case "REVERSE_DEMAG":
		baseAmp = 220.0 + (math.Cos(rad) * 120.0)
	case "FLUX_INVERSION":
		baseAmp = 680.0 + (sine * sine * 200.0)
	case "BURST_POLARIZATION":
		baseAmp = 750.0 + (sine * 220.0)
	case "DATA_INTEGRITY_VERIFY":
		baseAmp = 500.0 + (sine * 40.0)
	}

	mv := int(baseAmp + (noise * 50.0))
	if mv < 50 {
		mv = 50
	}
	if mv > 990 {
		mv = 990
	}
	return mv
}

// PrintSpinRiteStatus outputs the signal alignment progress block
func PrintSpinRiteStatus(stats RepairStats, ticks int, arabic bool) {
	osc := GenerateOscilloscopeWaveform(stats.PulseHistory, 8, 70)
	fmt.Println(osc)

	if arabic {
		fmt.Printf("\n┌──────────────────────── نظام إصلاح الميديا والمغنطة ────────────────────────┐\n")
		fmt.Printf("│ • العنوان المنطقي LBA: %-15d | القطاعات المعالجة: %-12d    │\n", stats.ActiveSector, stats.SectorsRepaired)
		fmt.Printf("│ • القطاعات المستعادة: %-15d | الإشارات المعالجة: %-12d    │\n", stats.SectorsRecovered, stats.SignalsRegenerated)
		fmt.Printf("│ • فولتية خلايا القراءة: %-13s | طور المغناطيسية: %-21s │\n",
			fmt.Sprintf("%d mV", stats.SurfaceVoltageMv), stats.CurrentHysteresisPhase)
		fmt.Printf("│ • نسبة الإنجاز الإجمالية: %-6.2f%%                                            │\n", stats.ScanProgress*100)
		fmt.Printf("└──────────────────────────────────────────────────────────────────────────────┘\n")
	} else {
		fmt.Printf("\n┌──────────────────────── SPINRITE MAGNETIC RECOVERY SYSTEM ───────────────────┐\n")
		fmt.Printf("│ • Active LBA Sector: %-14d | Sectors Processed: %-15d │\n", stats.ActiveSector, stats.SectorsRepaired)
		fmt.Printf("│ • Sectors Recovered: %-14d | Weak Signals Restored: %-11d │\n", stats.SectorsRecovered, stats.SignalsRegenerated)
		fmt.Printf("│ • Platter Read Voltage: %-11s | Hysteresis Phase: %-22s │\n",
			fmt.Sprintf("%d mV", stats.SurfaceVoltageMv), stats.CurrentHysteresisPhase)
		fmt.Printf("│ • Total Platter Progress: %-6.2f%%                                           │\n", stats.ScanProgress*100)
		fmt.Printf("└──────────────────────────────────────────────────────────────────────────────┘\n")
	}
}
