import SwiftUI
import Combine

class ContentViewModel: ObservableObject {
    @Published var currentSession: MeasurementSession?
    @Published var selectedIDs: Set<UUID> = []
    @Published var errorMessage: String?
    @Published var bladeCount: Int = 2

    let analyzer = RPMAnalyzer()
    private var timer: AnyCancellable?

    var isRunning: Bool { analyzer.isRunning }

    func start(dataStore: DataStore) {
        analyzer.bladeCount = bladeCount
        var session = MeasurementSession()
        currentSession = session

        // Sample once per second from the latest FFT result
        timer = Timer.publish(every: 1.0, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in
                guard let self, self.analyzer.isRunning else { return }
                let rpm = self.analyzer.currentRPM
                if rpm > 0 {
                    session.readings.append(RPMReading(rpm: rpm))
                    self.currentSession = session
                }
            }

        do {
            try analyzer.start()
        } catch {
            errorMessage = "Microphone error: \(error.localizedDescription)"
            currentSession = nil
            timer = nil
        }
    }

    func stop(dataStore: DataStore) {
        analyzer.stop()
        timer?.cancel()
        timer = nil
        if let session = currentSession, !session.readings.isEmpty {
            dataStore.addSession(session)
        }
        currentSession = nil
    }
}

struct ContentView: View {
    @EnvironmentObject var dataStore: DataStore
    @StateObject private var viewModel = ContentViewModel()

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Live RPM display
                if viewModel.analyzer.isRunning {
                    LiveRPMView(rpm: viewModel.analyzer.currentRPM)
                        .transition(.move(edge: .top).combined(with: .opacity))
                }

                // Blade count picker
                Picker("Blades", selection: $viewModel.bladeCount) {
                    Text("1 blade").tag(1)
                    Text("2 blades").tag(2)
                    Text("3 blades").tag(3)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)
                .padding(.vertical, 8)
                .disabled(viewModel.isRunning)

                // Sessions list
                List(dataStore.sessions) { session in
                    SessionRowView(
                        session: session,
                        isSelected: viewModel.selectedIDs.contains(session.id)
                    ) {
                        if viewModel.selectedIDs.contains(session.id) {
                            viewModel.selectedIDs.remove(session.id)
                        } else {
                            viewModel.selectedIDs.insert(session.id)
                        }
                    }
                }
                .listStyle(.plain)
                .overlay {
                    if dataStore.sessions.isEmpty && !viewModel.analyzer.isRunning {
                        ContentUnavailableView(
                            "No Measurements",
                            systemImage: "waveform",
                            description: Text("Press Start to begin measuring RPM")
                        )
                    }
                }

                // Control bar
                ControlBarView(
                    isRunning: viewModel.analyzer.isRunning,
                    hasSelection: !viewModel.selectedIDs.isEmpty,
                    onStart: { viewModel.start(dataStore: dataStore) },
                    onStop: { viewModel.stop(dataStore: dataStore) },
                    onDelete: {
                        dataStore.deleteSessions(ids: viewModel.selectedIDs)
                        viewModel.selectedIDs.removeAll()
                    }
                )
            }
            .navigationTitle("RPM Gauge")
            .animation(.easeInOut, value: viewModel.analyzer.isRunning)
            .alert("Error", isPresented: Binding(
                get: { viewModel.errorMessage != nil },
                set: { if !$0 { viewModel.errorMessage = nil } }
            )) {
                Button("OK") { viewModel.errorMessage = nil }
            } message: {
                Text(viewModel.errorMessage ?? "")
            }
        }
    }
}
