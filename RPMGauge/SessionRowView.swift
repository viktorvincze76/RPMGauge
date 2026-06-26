import SwiftUI

struct LiveRPMView: View {
    let rpm: Double

    private var isInRange: Bool { rpm >= 20000 && rpm <= 50000 }
    private var color: Color {
        guard rpm > 0 else { return .secondary }
        if rpm < 20000 { return .orange }
        if rpm > 50000 { return .red }
        return .green
    }

    var body: some View {
        VStack(spacing: 6) {
            Text(rpm > 0 ? String(format: "%.0f RPM", rpm) : "Listening…")
                .font(.system(size: 48, weight: .bold, design: .monospaced))
                .foregroundStyle(color)
                .contentTransition(.numericText())

            if rpm > 0 {
                Text(isInRange ? "In range" : (rpm < 20000 ? "Below range" : "Above range"))
                    .font(.caption)
                    .foregroundStyle(color)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 20)
        .background(Color(.systemGray6))
    }
}

struct SessionRowView: View {
    let session: MeasurementSession
    let isSelected: Bool
    let onToggle: () -> Void

    private static let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateStyle = .medium
        f.timeStyle = .medium
        return f
    }()

    var body: some View {
        HStack {
            Button(action: onToggle) {
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(isSelected ? .blue : .secondary)
                    .imageScale(.large)
            }
            .buttonStyle(.plain)

            NavigationLink(destination: SessionDetailView(session: session)) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(Self.dateFormatter.string(from: session.startTime))
                        .font(.subheadline)
                    HStack(spacing: 12) {
                        Label(String(format: "%.0f", session.averageRPM), systemImage: "chart.bar")
                        Text("avg RPM")
                            .foregroundStyle(.secondary)
                        Text("·")
                            .foregroundStyle(.secondary)
                        Text("\(session.readings.count) samples")
                            .foregroundStyle(.secondary)
                    }
                    .font(.caption)
                }
            }
        }
        .padding(.vertical, 4)
    }
}

struct ControlBarView: View {
    let isRunning: Bool
    let hasSelection: Bool
    let onStart: () -> Void
    let onStop: () -> Void
    let onDelete: () -> Void

    var body: some View {
        HStack(spacing: 20) {
            Button(action: onStart) {
                Label("Start", systemImage: "play.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(.green)
            .disabled(isRunning)

            Button(action: onStop) {
                Label("Stop", systemImage: "stop.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(.red)
            .disabled(!isRunning)

            Button(action: onDelete) {
                Label("Delete", systemImage: "trash")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(.gray)
            .disabled(!hasSelection)
        }
        .padding()
        .background(Color(.systemBackground))
        .overlay(alignment: .top) {
            Divider()
        }
    }
}
