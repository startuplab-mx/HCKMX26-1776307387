import SwiftUI

struct ReportCardView: View {
    var report: Report
    
    var severityColor: Color {
        switch report.severity {
        case .critical: return Color.red
        case .warning: return Color.orange
        case .information: return Color.blue
        }
    }
    
    var severityIcon: String {
        switch report.severity {
        case .critical: return "exclamationmark.triangle.fill"
        case .warning: return "exclamationmark.circle.fill"
        case .information: return "info.circle.fill"
        }
    }
    
    var body: some View {
        HStack(spacing: 0) {
            // Left color bar indicator
            Rectangle()
                .fill(severityColor)
                .frame(width: 4)
            
            VStack(alignment: .leading, spacing: 12) {
                // Header row
                HStack {
                    HStack(spacing: 4) {
                        Image(systemName: severityIcon)
                            .font(.system(size: 10))
                        Text(report.severity.rawValue)
                            .font(.system(size: 10, weight: .bold))
                    }
                    .padding(.horizontal, 6)
                    .padding(.vertical, 4)
                    .background(severityColor.opacity(0.2))
                    .foregroundColor(severityColor)
                    .cornerRadius(4)
                    
                    Text("\(report.id) • \(timeAgo(from: report.timestamp))")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(.gray)
                    
                    Spacer()
                    
                    Text(report.status.rawValue)
                        .font(.system(size: 10, weight: .medium))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(statusBackgroundColor)
                        .foregroundColor(statusTextColor)
                        .cornerRadius(10)
                }
                
                // Title
                Text(report.title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.primary)
                
                // Location & Source
                HStack(spacing: 12) {
                    HStack(spacing: 4) {
                        Image(systemName: "mappin.and.ellipse")
                            .foregroundColor(.gray)
                        Text(report.location)
                            .font(.system(size: 13))
                            .foregroundColor(.secondary)
                    }
                    Text("•").foregroundColor(.gray)
                    HStack(spacing: 4) {
                        Image(systemName: report.source == "Discord" ? "bubble.left.fill" : "iphone")
                            .foregroundColor(.gray)
                        Text(report.source)
                            .font(.system(size: 13))
                            .foregroundColor(.secondary)
                    }
                }
                
                // User info box
                HStack(spacing: 8) {
                    Circle()
                        .fill(Color.gray.opacity(0.3))
                        .frame(width: 32, height: 32)
                        .overlay(
                            Image(systemName: "person.fill")
                                .foregroundColor(.gray)
                        )
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text(report.user.username)
                            .font(.system(size: 12, weight: .semibold))
                        HStack(spacing: 2) {
                            if report.user.type == .minorFlag {
                                Image(systemName: "checkmark.shield.fill")
                                    .foregroundColor(.red)
                                    .font(.system(size: 8))
                            }
                            Text(report.user.type.rawValue)
                                .font(.system(size: 10))
                                .foregroundColor(report.user.type == .minorFlag ? .red : .gray)
                        }
                    }
                    Spacer()
                }
                .padding(8)
                .background(Color.blue.opacity(0.05))
                .cornerRadius(8)
                
                // Quote
                Text("\"\(report.descriptionQuote)\"")
                    .font(.system(size: 13, weight: .medium))
                    .italic()
                    .foregroundColor(.gray)
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4)
                            .stroke(Color.gray.opacity(0.2), lineWidth: 1)
                    )
                
                // Action Button
                Button(action: {
                    // Action handler
                }) {
                    HStack {
                        Spacer()
                        Text(report.severity == .critical ? "View Full Evidence" : "Review Map Data")
                            .font(.system(size: 13, weight: .semibold))
                        Image(systemName: report.severity == .critical ? "arrow.right" : "map.fill")
                            .font(.system(size: 12, weight: .semibold))
                        Spacer()
                    }
                    .padding(.vertical, 10)
                    .background(report.severity == .critical ? Color.black : Color.blue.opacity(0.15))
                    .foregroundColor(report.severity == .critical ? .white : .black)
                    .cornerRadius(8)
                }
            }
            .padding(16)
        }
        .background(Color.white)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(severityColor, lineWidth: 1) // It looks like the whole card has a colored border based on the mockup. Wait, mockup shows red border for critical, orange border for warning.
        )
        .padding(.horizontal)
    }
    
    // Helpers
    var statusBackgroundColor: Color {
        switch report.status {
        case .new: return Color.blue.opacity(0.1)
        case .inProgress: return Color.blue.opacity(0.15)
        case .resolved: return Color.green.opacity(0.1)
        }
    }
    
    var statusTextColor: Color {
        switch report.status {
        case .new: return Color.blue
        case .inProgress: return Color.blue
        case .resolved: return Color.green
        }
    }
    
    func timeAgo(from date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .full
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}
