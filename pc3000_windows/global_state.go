package main

// Global state variables for CLI and Windows GUI compatibility
var (
	SelectedDrive  *DriveInfo
	ActiveFailure  *DriveFailure
	Partitions     []Partition
	SmartAttrs     []SmartAttribute
	DrivesList     []DriveInfo
	CurrentProfile SmartProfile = HealthyHDD
	CarvedFiles    []RecoveredFile
	DetectedFailures []DriveFailure = GetFailureDetails()
)
