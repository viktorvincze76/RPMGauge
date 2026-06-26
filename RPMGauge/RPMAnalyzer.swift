import Foundation
import AVFoundation
import Accelerate

class RPMAnalyzer: ObservableObject {
    @Published var currentRPM: Double = 0
    @Published var isRunning: Bool = false

    // Number of propeller blades — used to detect and correct BPF harmonic interference
    var bladeCount: Int = 2

    // Target: 20,000 – 50,000 RPM  =>  333 – 833 Hz fundamental
    // A 2-stroke fires once per revolution, so 1 RPM = 1/60 Hz
    private let minHz: Double = 20000 / 60   // ≈ 333 Hz
    private let maxHz: Double = 50000 / 60   // ≈ 833 Hz

    private var audioEngine: AVAudioEngine?
    private var inputNode: AVAudioInputNode?
    private let bufferSize: AVAudioFrameCount = 4096

    func start() throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.record, mode: .measurement, options: [])
        try session.setActive(true)

        let engine = AVAudioEngine()
        audioEngine = engine
        inputNode = engine.inputNode
        guard let inputNode else { return }

        let format = inputNode.inputFormat(forBus: 0)
        let sampleRate = format.sampleRate

        inputNode.installTap(onBus: 0, bufferSize: bufferSize, format: format) { [weak self] buffer, _ in
            self?.process(buffer: buffer, sampleRate: sampleRate)
        }

        try engine.start()
        DispatchQueue.main.async { self.isRunning = true }
    }

    func stop() {
        audioEngine?.stop()
        inputNode?.removeTap(onBus: 0)
        audioEngine = nil
        inputNode = nil
        try? AVAudioSession.sharedInstance().setActive(false)
        DispatchQueue.main.async {
            self.isRunning = false
            self.currentRPM = 0
        }
    }

    private func process(buffer: AVAudioPCMBuffer, sampleRate: Double) {
        guard let channelData = buffer.floatChannelData?[0] else { return }
        let frameCount = Int(buffer.frameLength)
        guard frameCount > 0 else { return }

        // FFT
        let log2n = vDSP_Length(log2(Float(frameCount)))
        let fftSize = 1 << log2n
        guard let fftSetup = vDSP_create_fftsetup(log2n, FFTRadix(FFT_RADIX2)) else { return }
        defer { vDSP_destroy_fftsetup(fftSetup) }

        var real = [Float](repeating: 0, count: fftSize / 2)
        var imag = [Float](repeating: 0, count: fftSize / 2)
        var splitComplex = DSPSplitComplex(realp: &real, imagp: &imag)

        // Apply Hann window then pack into split-complex
        var windowed = [Float](UnsafeBufferPointer(start: channelData, count: min(frameCount, fftSize)))
        if windowed.count < fftSize { windowed.append(contentsOf: [Float](repeating: 0, count: fftSize - windowed.count)) }
        var window = [Float](repeating: 0, count: fftSize)
        vDSP_hann_window(&window, vDSP_Length(fftSize), Int32(vDSP_HANN_NORM))
        vDSP_vmul(windowed, 1, window, 1, &windowed, 1, vDSP_Length(fftSize))

        windowed.withUnsafeBytes { ptr in
            let floatPtr = ptr.bindMemory(to: DSPComplex.self)
            vDSP_ctoz(floatPtr.baseAddress!, 2, &splitComplex, 1, vDSP_Length(fftSize / 2))
        }
        vDSP_fft_zrip(fftSetup, &splitComplex, 1, log2n, FFTDirection(FFT_FORWARD))

        var magnitudes = [Float](repeating: 0, count: fftSize / 2)
        vDSP_zvmags(&splitComplex, 1, &magnitudes, 1, vDSP_Length(fftSize / 2))

        // Frequency resolution
        let freqResolution = sampleRate / Double(fftSize)
        let minBin = Int(minHz / freqResolution)
        let maxBin = min(Int(maxHz / freqResolution), fftSize / 2 - 1)
        guard minBin <= maxBin else { return }

        // Find peak bin in target range
        let slice = magnitudes[minBin...maxBin]
        guard let maxIdx = slice.indices.max(by: { slice[$0] < slice[$1] }) else { return }

        // Parabolic interpolation for sub-bin precision
        let peak: Double
        if maxIdx > minBin && maxIdx < maxBin {
            let y0 = Double(magnitudes[maxIdx - 1])
            let y1 = Double(magnitudes[maxIdx])
            let y2 = Double(magnitudes[maxIdx + 1])
            let delta = 0.5 * (y0 - y2) / (y0 - 2 * y1 + y2)
            peak = (Double(maxIdx) + delta) * freqResolution
        } else {
            peak = Double(maxIdx) * freqResolution
        }

        let rpm = peak * 60.0
        guard rpm >= 20000 && rpm <= 50000 else { return }

        // BPF correction: if bladeCount > 1 the dominant peak might be the blade passage
        // frequency (BPF = fundamental × bladeCount). Check for a sub-harmonic at
        // peak/bladeCount with at least 15% of the BPF magnitude; if found, use it instead.
        let correctedRPM: Double
        let blades = bladeCount
        if blades > 1 {
            let fundamentalHz = peak / Double(blades)
            let fundamentalRPM = fundamentalHz * 60.0
            if fundamentalRPM >= 20000 && fundamentalRPM <= 50000 {
                let subBin = Int(fundamentalHz / freqResolution)
                if subBin >= 0 && subBin < fftSize / 2 {
                    let subMag = Double(magnitudes[subBin])
                    let peakMag = Double(magnitudes[maxIdx])
                    correctedRPM = subMag >= peakMag * 0.15 ? fundamentalRPM : rpm
                } else {
                    correctedRPM = rpm
                }
            } else {
                correctedRPM = rpm
            }
        } else {
            correctedRPM = rpm
        }

        DispatchQueue.main.async {
            self.currentRPM = correctedRPM
        }
    }
}
