import Foundation
import StreetComplete
import SwiftUI

@main
struct iOSApp: App {
    private let links: IosAppLinks
    private let notificationRouter: NSObject

    init() {
        let links = IosAppLinks()
        self.links = links
        // Register before launch finishes so notification taps also survive a cold start.
        self.notificationRouter = IosQuestNotificationRouterKt.createQuestNotificationRouter(links: links)
        KoinKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView(links: links)
                .onOpenURL { handleIncomingURL($0) }
        }
    }

    @MainActor
    private func handleIncomingURL(_ url: URL) {
        guard url.isFileURL else {
            links.openUri(uri: url.absoluteString)
            return
        }
        Task {
            do {
                let copy = try await copyIncomingDocument(from: url)
                links.openImportedFile(path: copy.path)
            } catch {
                links.notifyFileImportError()
            }
        }
    }
}

/// Preserve access to an external document only while copying it into our own temporary directory.
/// Kotlin receives the private copy's path, rather than a path stripped of its security scope.
private func copyIncomingDocument(from url: URL) async throws -> URL {
    try await Task.detached(priority: .userInitiated) {
        let accessed = url.startAccessingSecurityScopedResource()
        defer { if accessed { url.stopAccessingSecurityScopedResource() } }
        let files = FileManager.default
        let directory = files.temporaryDirectory
            .appendingPathComponent("SCEEImports", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try files.createDirectory(at: directory, withIntermediateDirectories: true)
        do {
            let destination = directory.appendingPathComponent(url.lastPathComponent, isDirectory: false)
            var coordinationError: NSError?
            var copyError: Error?
            NSFileCoordinator(filePresenter: nil).coordinate(readingItemAt: url, options: [], error: &coordinationError) { source in
                do {
                    let attributes = try source.resourceValues(forKeys: [.isRegularFileKey])
                    guard attributes.isRegularFile == true else { throw CocoaError(.fileReadUnknown) }
                    try files.copyItem(at: source, to: destination)
                } catch {
                    copyError = error
                }
            }
            if let coordinationError { throw coordinationError }
            if let copyError { throw copyError }
            guard files.fileExists(atPath: destination.path) else { throw CocoaError(.fileReadUnknown) }
            return destination
        } catch {
            try? files.removeItem(at: directory)
            throw error
        }
    }.value
}
