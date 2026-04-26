import SwiftUI

struct CommandCenterView: View {
    @StateObject private var viewModel = CommandCenterViewModel()
    @State private var selectedTab = 2 // Evidence
    
    var body: some View {
        TabView(selection: $selectedTab) {
            Text("Feed View")
                .tabItem {
                    Label("Feed", systemImage: "list.bullet.rectangle.portrait")
                }
                .tag(0)
            
            Text("Map View")
                .tabItem {
                    Label("Map", systemImage: "mappin.and.ellipse")
                }
                .tag(1)
            
            EvidenceView(viewModel: viewModel)
                .tabItem {
                    Label("Evidence", systemImage: "archivebox.fill")
                }
                .tag(2)
            
            Text("Settings")
                .tabItem {
                    Label("Settings", systemImage: "shield.righthalf.filled")
                }
                .tag(3)
        }
        .accentColor(.black)
    }
}

struct EvidenceView: View {
    @ObservedObject var viewModel: CommandCenterViewModel
    
    var body: some View {
        VStack(spacing: 0) {
            // Top Bar
            HStack {
                Image(systemName: "shield.fill")
                    .font(.system(size: 20))
                Text("Safety Command Center")
                    .font(.system(size: 18, weight: .bold))
                
                Spacer()
                
                Circle()
                    .fill(Color.blue.opacity(0.1))
                    .frame(width: 32, height: 32)
                    .overlay(
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 14))
                            .foregroundColor(.primary)
                    )

                Button {
                    viewModel.fetchReports()
                } label: {
                    Circle()
                        .fill(Color.green.opacity(0.12))
                        .frame(width: 32, height: 32)
                        .overlay(
                            Image(systemName: "arrow.clockwise")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(.primary)
                        )
                }
                .buttonStyle(.plain)
                
                Circle()
                    .fill(Color.orange.opacity(0.3))
                    .frame(width: 32, height: 32)
                    .overlay(
                        Image(systemName: "person.crop.circle.fill")
                            .foregroundColor(.gray)
                    )
            }
            .padding(.horizontal)
            .padding(.vertical, 12)
            
            Divider()
            
            ScrollView {
                VStack(spacing: 16) {
                    // Search & Filter
                    HStack {
                        HStack {
                            Image(systemName: "magnifyingglass")
                                .foregroundColor(.gray)
                            TextField("Search reports by ID, user, or keyword", text: $viewModel.searchText)
                                .font(.system(size: 14))
                        }
                        .padding(10)
                        .background(Color.white)
                        .cornerRadius(8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(Color.gray.opacity(0.3), lineWidth: 1)
                        )
                        
                        Button(action: {}) {
                            Image(systemName: "slider.horizontal.3")
                                .foregroundColor(.primary)
                                .padding(10)
                                .background(Color.white)
                                .cornerRadius(8)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 8)
                                        .stroke(Color.gray.opacity(0.3), lineWidth: 1)
                                )
                        }
                    }
                    .padding(.horizontal)
                    .padding(.top, 16)
                    
                    // Filter Chips
                    HStack(spacing: 12) {
                        FilterChip(
                            title: "Critical",
                            count: viewModel.count(for: .critical),
                            color: .red,
                            isSelected: viewModel.isSeveritySelected(.critical)
                        ) {
                            viewModel.toggleSeverity(.critical)
                        }
                        FilterChip(
                            title: "Warning",
                            count: viewModel.count(for: .warning),
                            color: .orange,
                            isSelected: viewModel.isSeveritySelected(.warning)
                        ) {
                            viewModel.toggleSeverity(.warning)
                        }
                        FilterChip(
                            title: "Information",
                            count: viewModel.count(for: .information),
                            color: .blue,
                            isSelected: viewModel.isSeveritySelected(.information)
                        ) {
                            viewModel.toggleSeverity(.information)
                        }
                        Spacer()
                    }
                    .padding(.horizontal)
                    
                    Divider()
                        .padding(.vertical, 8)

                    if viewModel.isLoading {
                        HStack(spacing: 10) {
                            ProgressView()
                            Text("Cargando eventos de IA")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(.secondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal)
                    }

                    if let errorMessage = viewModel.errorMessage {
                        HStack(spacing: 10) {
                            Image(systemName: "wifi.exclamationmark")
                                .foregroundColor(.orange)
                            Text(errorMessage)
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(.secondary)
                            Spacer()
                            Button("Reintentar") {
                                viewModel.fetchReports()
                            }
                            .font(.system(size: 13, weight: .semibold))
                        }
                        .padding(12)
                        .background(Color.orange.opacity(0.08))
                        .cornerRadius(8)
                        .padding(.horizontal)
                    }
                    
                    // Header
                    HStack(alignment: .bottom) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Eventos de IA recientes")
                                .font(.system(size: 20, weight: .bold))
                                .foregroundColor(Color(red: 0.1, green: 0.15, blue: 0.3)) // Dark blueish
                            Text("Mostrando \(viewModel.filteredReports.count) eventos detectados")
                                .font(.system(size: 14))
                                .foregroundColor(.gray)
                        }
                        Spacer()
                        Text("Orden: recientes")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.primary)
                    }
                    .padding(.horizontal)
                    
                    // Report Cards
                    if viewModel.filteredReports.isEmpty {
                        EmptyArchiveStateView()
                    } else {
                        ForEach(viewModel.filteredReports) { report in
                            ReportCardView(report: report)
                        }
                    }
                }
                .padding(.bottom, 20)
            }
            .background(Color(white: 0.98)) // Very light gray background like mockup
        }
    }
}

struct FilterChip: View {
    let title: String
    let count: Int?
    let color: Color
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Circle()
                    .fill(color)
                    .frame(width: 8, height: 8)
                Text(title)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(isSelected ? color : .primary)
                if let count {
                    Text("\(count)")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(isSelected ? color : .secondary)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(isSelected ? color.opacity(0.15) : Color.white)
            .cornerRadius(16)
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(isSelected ? color.opacity(0.3) : Color.gray.opacity(0.3), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}

struct EmptyArchiveStateView: View {
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "archivebox")
                .font(.system(size: 28))
                .foregroundColor(.gray)
            Text("No reports match these filters")
                .font(.system(size: 16, weight: .semibold))
            Text("Adjust the severity chips or search text to see archived evidence.")
                .font(.system(size: 13))
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(Color.white)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.gray.opacity(0.2), lineWidth: 1)
        )
        .padding(.horizontal)
    }
}
