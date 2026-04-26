import Foundation

enum ReportSeverity: String, Codable {
    case critical = "CRITICAL"
    case warning = "WARNING"
    case information = "INFORMATION"
}

enum ReportStatus: String, Codable {
    case new = "New"
    case inProgress = "In Progress"
    case resolved = "Resolved"
}

enum UserType: String, Codable {
    case minorFlag = "Minor Flag"
    case standardUser = "Standard User"
}

struct UserProfile: Identifiable, Codable {
    var id: UUID = UUID()
    var username: String
    var avatarURL: String?
    var type: UserType
}

struct Report: Identifiable, Codable {
    var id: String // E.g., REP-2023-8942
    var title: String
    var severity: ReportSeverity
    var timestamp: Date
    var status: ReportStatus
    var location: String
    var source: String
    var user: UserProfile
    var descriptionQuote: String
}

struct AiEvent: Decodable {
    let id: String
    let source: String
    let platform: String
    let riskType: String
    let riskLevel: String
    let summary: String
    let rawText: String?
    let emojiTags: [String]
    let locationState: String?
    let locationCity: String?
    let score: Double?
    let visionLabel: String?
    let visionObjects: [String]
    let detectedUser: String?
    let screenContext: String?
    let createdAt: Date
    let minor: AiEventUser?
}

struct AiEventUser: Decodable {
    let id: String
    let name: String
    let email: String?
    let locationState: String?
    let locationCity: String?
}
