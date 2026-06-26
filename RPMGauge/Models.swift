import Foundation

struct RPMReading: Identifiable, Codable {
    let id: UUID
    let timestamp: Date
    let rpm: Double

    init(rpm: Double) {
        self.id = UUID()
        self.timestamp = Date()
        self.rpm = rpm
    }
}

struct MeasurementSession: Identifiable, Codable {
    let id: UUID
    let startTime: Date
    var readings: [RPMReading]

    init() {
        self.id = UUID()
        self.startTime = Date()
        self.readings = []
    }

    var averageRPM: Double {
        guard !readings.isEmpty else { return 0 }
        return readings.map(\.rpm).reduce(0, +) / Double(readings.count)
    }

    var maxRPM: Double {
        readings.map(\.rpm).max() ?? 0
    }

    var minRPM: Double {
        readings.map(\.rpm).min() ?? 0
    }
}
