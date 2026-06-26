import Foundation
import Combine

class DataStore: ObservableObject {
    @Published var sessions: [MeasurementSession] = []

    private let storageURL: URL = {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return docs.appendingPathComponent("sessions.json")
    }()

    init() {
        load()
    }

    func addSession(_ session: MeasurementSession) {
        sessions.insert(session, at: 0)
        save()
    }

    func deleteSessions(ids: Set<UUID>) {
        sessions.removeAll { ids.contains($0.id) }
        save()
    }

    private func save() {
        do {
            let data = try JSONEncoder().encode(sessions)
            try data.write(to: storageURL, options: .atomic)
        } catch {
            print("DataStore save error: \(error)")
        }
    }

    private func load() {
        guard let data = try? Data(contentsOf: storageURL),
              let decoded = try? JSONDecoder().decode([MeasurementSession].self, from: data) else { return }
        sessions = decoded
    }
}
