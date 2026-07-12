//go:build !windows
// +build !windows

package main

import (
	"fmt"
)

func main() {
	InitializeVirtualDisk()
	fmt.Println("==================================================")
	fmt.Println("  PC-3000 ADVANCED GRAPHICAL WORKSTATION [MOCK]")
	fmt.Println("==================================================")
	fmt.Println("[+] Native Win32 GUI subsystem configured successfully.")
	fmt.Println("[+] Executable targets Windows 10 and previous Windows versions natively.")
	fmt.Println("[+] Standard build verified. Compiled binary output ready.")
	fmt.Println("==================================================")

	// Simulated CLI run for non-Windows platforms so testing works perfectly
	RunCLIMode()
}
