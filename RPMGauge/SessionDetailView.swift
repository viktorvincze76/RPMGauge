import SwiftUI

struct SessionDetailView: View {
    let session: MeasurementSession

    private static let timestampFormatter: DateFormatter = {
        let f = DateFormatter()
        f.timeStyle = .medium
        return f
    }()

    var body: some View {
        List {
            Section("Summary") {
                LabeledContent("Samples", value: "\(session.readings.count)")
                LabeledContent("Average RPM", value: String(format: "%.0f", session.averageRPM))
                LabeledContent("Min RPM", value: String(format: "%.0f", session.minRPM))
                LabeledContent("Max RPM", value: String(format: "%.0f", session.maxRPM))
            }

            Section("Readings") {
                ForEach(session.readings) { reading in
                    HStack {
                        Text(Self.timestampFormatter.string(from: reading.timestamp))
                            .foregroundStyle(.secondary)
                            .font(.caption)
                        Spacer()
                        Text(String(format: "%.0f RPM", reading.rpm))
                            .font(.system(.body, design: .monospaced))
                            .foregroundStyle(rpmColor(reading.rpm))
                    }
                }
            }
        }
        .navigationTitle(sessionTitle)
        .navigationBarTitleDisplayMode(.inline)
    }

    private var sessionTitle: String {
        let f = DateFormatter()
        f.dateStyle = .medium
        f.timeStyle = .short
        return f.string(from: session.startTime)
    }

    private func rpmColor(_ rpm: Double) -> Color {
        if rpm < 20000 { return .orange }
        if rpm > 50000 { return .red }
        return .primary
    }
}
