# RPM Gauge

An iPhone app that measures the RPM of a single-cylinder 2-stroke model engine by analysing the sound frequency it produces in real time.

## Overview

RPM Gauge uses the iPhone microphone and a fast Fourier transform (FFT) to detect the fundamental firing frequency of the engine. Because a 2-stroke fires once per revolution, the relationship between frequency and RPM is direct:

```
RPM = frequency (Hz) × 60
```

The target measurement range is **20,000 – 50,000 RPM** (≈ 333 – 833 Hz).

---

## Features

- **Live RPM display** – large monospaced readout that updates continuously while measuring, colour-coded green (in range), orange (below range), or red (above range)
- **Per-second logging** – one RPM sample is recorded every second and stored with its timestamp
- **Session management** – each Start/Stop cycle is saved as a named session, listed by its start timestamp on the main screen
- **Session detail view** – tap any session to see every individual reading, plus a summary of average, min, and max RPM
- **Checkbox-based deletion** – check one or more sessions and press Delete to remove them
- **Persistent storage** – sessions are saved as JSON in the app's Documents directory and survive app restarts
- **Dark & tinted icon variants** – adaptive app icon for all iOS appearance modes

---

## Requirements

| Requirement | Version |
|---|---|
| iOS | 17.0+ |
| Xcode | 16.0+ |
| Swift | 5.0+ |
| Device | Physical iPhone (microphone not available in Simulator) |

---

## Project Structure

```
RPMGauge/
├── RPMGauge.xcodeproj/
└── RPMGauge/
    ├── RPMGaugeApp.swift        # App entry point, injects DataStore
    ├── Models.swift             # RPMReading and MeasurementSession data models
    ├── DataStore.swift          # JSON persistence (Documents/sessions.json)
    ├── RPMAnalyzer.swift        # AVAudioEngine tap + vDSP FFT analysis
    ├── ContentView.swift        # Main screen and ContentViewModel
    ├── SessionRowView.swift     # Row cell, live RPM banner, control bar
    ├── SessionDetailView.swift  # Per-session readings list and summary
    └── Assets.xcassets/
        └── AppIcon.appiconset/  # Light, dark, and tinted icon variants
```

---

## Getting Started

### 1. Open the project

Open `RPMGauge/RPMGauge.xcodeproj` in Xcode.

### 2. Sign the app

1. Select the **RPMGauge** project in the Project Navigator
2. Select the **RPMGauge** target → **Signing & Capabilities** tab
3. Choose your **Team** from the dropdown (a free Apple ID is sufficient for personal device testing)

### 3. Connect your iPhone

Select your device from the run destination dropdown in the Xcode toolbar. The app requires a physical device — the microphone tap is not available in the iOS Simulator.

### 4. Build & Run

Press **⌘R**. On first launch iOS will ask for microphone permission; the app cannot measure RPM without it.

---

## How It Works

### Audio capture

`RPMAnalyzer` starts an `AVAudioSession` in `.record` / `.measurement` mode and installs a tap on the input node of an `AVAudioEngine`, receiving 4096-frame buffers at the device's native sample rate (typically 44,100 or 48,000 Hz).

### FFT analysis

Each buffer is processed with Apple's `vDSP` framework:

1. A **Hann window** is applied to reduce spectral leakage
2. A **4096-point real FFT** converts the time-domain signal to the frequency domain
3. Only the bins corresponding to 333–833 Hz (20,000–50,000 RPM) are examined
4. The **peak magnitude bin** is found within that range
5. **Parabolic interpolation** on the three bins around the peak gives sub-bin frequency precision
6. Readings outside the valid RPM range are discarded

### Per-second sampling

A `Timer` in `ContentViewModel` fires every second and snapshots `RPMAnalyzer.currentRPM` (the latest FFT result). This gives exactly one `RPMReading` per second in the session log.

---

## Microphone Permission

The `NSMicrophoneUsageDescription` key is set in the build settings to:

> *"RPM Gauge needs microphone access to measure engine RPM from sound frequency."*

This string is shown to the user by iOS when permission is requested. No audio is recorded or stored — only the computed RPM value is saved.

---

## License

This project is provided as-is for personal use.
