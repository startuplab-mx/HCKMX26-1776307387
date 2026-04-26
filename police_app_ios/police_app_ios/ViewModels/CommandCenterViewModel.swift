import Combine
import Foundation

class CommandCenterViewModel: ObservableObject {
    @Published var reports: [Report] = []
    @Published var searchText: String = ""
    @Published private(set) var isLoading = false
    @Published private(set) var errorMessage: String?
    @Published private(set) var selectedSeverity: ReportSeverity?
    @Published var selectedSource: String?
    @Published var selectedStatus: ReportStatus?

    private let eventsURL = URL(string: "http://127.0.0.1:3000/ai-events")!

    var filteredReports: [Report] {
        reports.filter { report in
            matchesSelectedSeverity(report)
                && matchesSelectedSource(report)
                && matchesSelectedStatus(report)
                && matchesSearchText(report)
        }
    }

    var availableSources: [String] {
        Array(Set(reports.map(\.source))).sorted()
    }

    var hasActiveFilters: Bool {
        selectedSeverity != nil
            || selectedSource != nil
            || selectedStatus != nil
            || !searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
    
    init() {
        fetchReports()
    }

    func toggleSeverity(_ severity: ReportSeverity) {
        if selectedSeverity == severity {
            selectedSeverity = nil
        } else {
            selectedSeverity = severity
        }
    }

    func isSeveritySelected(_ severity: ReportSeverity) -> Bool {
        selectedSeverity == severity
    }

    func count(for severity: ReportSeverity) -> Int {
        reports.filter { $0.severity == severity }.count
    }

    func count(for source: String) -> Int {
        reports.filter { $0.source == source }.count
    }

    func count(for status: ReportStatus) -> Int {
        reports.filter { $0.status == status }.count
    }

    func toggleSource(_ source: String) {
        selectedSource = selectedSource == source ? nil : source
    }

    func toggleStatus(_ status: ReportStatus) {
        selectedStatus = selectedStatus == status ? nil : status
    }

    func clearFilters() {
        searchText = ""
        selectedSeverity = nil
        selectedSource = nil
        selectedStatus = nil
    }
    
    func fetchReports() {
        Task {
            await loadAiEvents()
        }
    }

    func loadAiEvents() async {
        isLoading = true
        errorMessage = nil

        do {
            let (data, response) = try await URLSession.shared.data(from: eventsURL)
            guard let httpResponse = response as? HTTPURLResponse,
                  200..<300 ~= httpResponse.statusCode else {
                throw URLError(.badServerResponse)
            }

            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .custom { decoder in
                let container = try decoder.singleValueContainer()
                let value = try container.decode(String.self)

                if let date = ISO8601DateFormatter.withFractionalSeconds.date(from: value) {
                    return date
                }

                if let date = ISO8601DateFormatter.standard.date(from: value) {
                    return date
                }

                throw DecodingError.dataCorruptedError(
                    in: container,
                    debugDescription: "Invalid ISO8601 date: \(value)"
                )
            }

            let events = try decoder.decode([AiEvent].self, from: data)
            reports = events.map(Self.makeReport(from:))
            isLoading = false
        } catch {
            errorMessage = "No se pudieron cargar eventos de IA desde el backend local."
            reports = []
            isLoading = false
        }
    }

    private func matchesSelectedSeverity(_ report: Report) -> Bool {
        guard let selectedSeverity else {
            return true
        }

        return report.severity == selectedSeverity
    }

    private func matchesSelectedSource(_ report: Report) -> Bool {
        guard let selectedSource else {
            return true
        }

        return report.source == selectedSource
    }

    private func matchesSelectedStatus(_ report: Report) -> Bool {
        guard let selectedStatus else {
            return true
        }

        return report.status == selectedStatus
    }

    private func matchesSearchText(_ report: Report) -> Bool {
        let trimmedSearchText = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedSearchText.isEmpty else {
            return true
        }

        let query = trimmedSearchText.localizedLowercase
        let searchableValues = [
            report.id,
            report.title,
            report.location,
            report.source,
            report.user.username,
            report.descriptionQuote
        ]

        return searchableValues.contains { value in
            value.localizedLowercase.contains(query)
        }
    }

    private static func makeReport(from event: AiEvent) -> Report {
        let severity = severity(from: event.riskLevel)
        let minorName = event.minor?.name ?? "Menor sin identificar"
        let location = [
            event.locationCity ?? event.minor?.locationCity,
            event.locationState ?? event.minor?.locationState
        ]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: ", ")

        return Report(
            id: "IA-\(event.id.prefix(8).uppercased())",
            title: title(for: event),
            severity: severity,
            timestamp: event.createdAt,
            status: status(for: event.riskLevel),
            location: location.isEmpty ? event.screenContext?.displayName ?? "Sin ubicacion" : location,
            source: event.platform.displayName,
            user: UserProfile(
                username: event.detectedUser?.firstDisplayLine ?? minorName,
                type: .minorFlag
            ),
            descriptionQuote: description(for: event)
        )
    }

    private static func severity(from riskLevel: String) -> ReportSeverity {
        switch riskLevel.uppercased() {
        case "CRITICAL":
            return .critical
        case "HIGH", "MEDIUM":
            return .warning
        default:
            return .information
        }
    }

    private static func status(for riskLevel: String) -> ReportStatus {
        switch riskLevel.uppercased() {
        case "CRITICAL", "HIGH":
            return .new
        case "MEDIUM":
            return .inProgress
        default:
            return .resolved
        }
    }

    private static func title(for event: AiEvent) -> String {
        let risk = event.riskType.replacingOccurrences(of: "_", with: " ").displayName
        let source = event.source.displayName

        if let visionLabel = event.visionLabel, !visionLabel.isEmpty {
            return "\(source): \(visionLabel.displayName)"
        }

        return "\(source): \(risk)"
    }

    private static func description(for event: AiEvent) -> String {
        var parts = [event.summary]

        if let rawText = event.rawText?.trimmingCharacters(in: .whitespacesAndNewlines),
           !rawText.isEmpty {
            parts.append("Texto detectado: \(rawText)")
        }

        if !event.emojiTags.isEmpty {
            parts.append("Emojis: \(event.emojiTags.joined(separator: " "))")
        }

        if !event.visionObjects.isEmpty {
            parts.append("Objetos: \(event.visionObjects.joined(separator: ", "))")
        }

        if let score = event.score {
            parts.append("Confianza: \(Int(score * 100))%")
        }

        return parts.joined(separator: "\n")
    }
}

private extension ISO8601DateFormatter {
    static let withFractionalSeconds: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    static let standard: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()
}

private extension String {
    var displayName: String {
        replacingOccurrences(of: "_", with: " ")
            .split(separator: " ")
            .map { word in
                word.prefix(1).uppercased() + word.dropFirst().lowercased()
            }
            .joined(separator: " ")
    }

    var firstDisplayLine: String {
        components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .first { !$0.isEmpty } ?? self
    }
}
