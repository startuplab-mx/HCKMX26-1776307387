import Combine
import Foundation

class CommandCenterViewModel: ObservableObject {
    @Published var reports: [Report] = []
    @Published var searchText: String = ""
    @Published private(set) var selectedSeverity: ReportSeverity?

    var filteredReports: [Report] {
        reports.filter { report in
            matchesSelectedSeverity(report) && matchesSearchText(report)
        }
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
    
    func fetchReports() {
        // TODO: Replace this mock data with actual fetch from local database once implemented.
        let mockReports = [
            Report(
                id: "REP-2023-8942",
                title: "Suspected Predatory Behavior",
                severity: .critical,
                timestamp: Calendar.current.date(byAdding: .minute, value: -10, to: Date()) ?? Date(),
                status: .new,
                location: "Sector North, Park Ave",
                source: "Discord",
                user: UserProfile(username: "@sarah_j99", type: .minorFlag),
                descriptionQuote: "Transcript snippet flagged by NLP: 'Don't tell your parents where we are meeting...'"
            ),
            Report(
                id: "REP-2023-8941",
                title: "Geofence Breach Detected",
                severity: .warning,
                timestamp: Calendar.current.date(byAdding: .minute, value: -45, to: Date()) ?? Date(),
                status: .inProgress,
                location: "Sector East, Mall Area",
                source: "Device GPS",
                user: UserProfile(username: "@mike_t", type: .standardUser),
                descriptionQuote: "Device exited approved 'School Zone' geofence at 14:32 during restricted hours."
            ),
            Report(
                id: "REP-2023-8940",
                title: "Suspicious Group Message Review",
                severity: .information,
                timestamp: Calendar.current.date(byAdding: .hour, value: -2, to: Date()) ?? Date(),
                status: .resolved,
                location: "Sector West, Community Center",
                source: "Discord",
                user: UserProfile(username: "@lina_reports", type: .standardUser),
                descriptionQuote: "Automated review flagged language for analyst follow-up, but no direct threat indicators were confirmed."
            )
        ]
        
        self.reports = mockReports
    }

    private func matchesSelectedSeverity(_ report: Report) -> Bool {
        guard let selectedSeverity else {
            return true
        }

        return report.severity == selectedSeverity
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
}
