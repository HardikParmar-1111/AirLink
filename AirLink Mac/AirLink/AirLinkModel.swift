//
//  AirLinkModel.swift
//  AirLink
//

import Foundation
import AirLinkShare
import SwiftUI
import AppKit

struct RecentTransferItem: Identifiable {
	let id: UUID
	let title: String
	let subtitle: String
	let timestamp: Date
}

struct DiscoveredDevice: Identifiable, Equatable {
	let id: String
	let name: String
	let type: RemoteDeviceInfo.DeviceType

	var systemIcon: String {
		switch type {
		case .phone: return "iphone"
		case .tablet: return "ipad"
		case .computer: return "laptopcomputer"
		default: return "display"
		}
	}
}

@MainActor
final class AirLinkModel: ObservableObject, ShareExtensionDelegate {
	static let shared = AirLinkModel()

	@Published var isDiscoverable: Bool {
		didSet {
			applyDiscoverability()
		}
	}

	@Published var recentTransfers: [RecentTransferItem]
	@Published var discoveredDevices: [DiscoveredDevice] = []
	@Published var isScanning: Bool = true
	@Published var activeTransferStatus: String?
	@Published var connectedDeviceName: String? = nil
	@Published var connectedDeviceIP: String? = nil
	@Published var batteryLevel: Int? = nil
	@Published var isBatteryCharging: Bool = false

	var batteryIconName: String {
		guard let level = batteryLevel else { return "battery.0" }
		if isBatteryCharging {
			return "battery.100.bolt"
		}
		switch level {
		case 80...100: return "battery.100"
		case 55..<80: return "battery.75"
		case 30..<55: return "battery.50"
		case 10..<30: return "battery.25"
		default: return "battery.0"
		}
	}

	var batteryColor: Color {
		if isBatteryCharging { return .green }
		guard let level = batteryLevel else { return .secondary }
		if level <= 20 { return .red }
		if level <= 35 { return .orange }
		return .green
	}

	private init() {
		isDiscoverable = true
		recentTransfers = [
			RecentTransferItem(id: UUID(), title: "No transfers yet", subtitle: "Incoming Quick Share files will appear here", timestamp: Date())
		]
		// Register as discovery delegate and ensure continuous scanner is active
		AirLinkConnectionManager.shared.addShareExtensionDelegate(self)
		AirLinkConnectionManager.shared.startDeviceDiscovery()
	}

	func applyDiscoverability() {
		DispatchQueue.global(qos: .userInitiated).async {
			if self.isDiscoverable {
				AirLinkConnectionManager.shared.becomeVisible()
			} else {
				AirLinkConnectionManager.shared.becomeInvisible()
			}
		}
	}

	func ringPhone() {
		AirLinkWebSocketServer.shared.ringPhone()
		AirLinkNotchDropManager.shared.triggerReceivedFeedback(
			title: "Ringing Phone",
			subtitle: connectedDeviceName ?? "Android Device"
		)
	}

	func sendFiles(urls: [URL], to device: DiscoveredDevice) {
		let fileTitle = urls.count == 1 ? urls[0].lastPathComponent : "\(urls.count) files"
		activeTransferStatus = "Connecting to \(device.name)..."

		recentTransfers.insert(
			RecentTransferItem(
				id: UUID(),
				title: "Sending: \(fileTitle)",
				subtitle: "Connecting to \(device.name)...",
				timestamp: Date()
			),
			at: 0
		)

		AirLinkConnectionManager.shared.startOutgoingTransfer(
			deviceID: device.id,
			delegate: self,
			urls: urls
		)
	}

	// MARK: - ShareExtensionDelegate
	nonisolated func addDevice(device: RemoteDeviceInfo) {
		guard let id = device.id else { return }
		Task { @MainActor in
			if !self.discoveredDevices.contains(where: { $0.id == id }) {
				let item = DiscoveredDevice(id: id, name: device.name, type: device.type)
				self.discoveredDevices.append(item)
				print("AirLinkModel: Discovered device '\(device.name)' (\(id))")
			}
		}
	}

	nonisolated func removeDevice(id: String) {
		Task { @MainActor in
			self.discoveredDevices.removeAll(where: { $0.id == id })
			print("AirLinkModel: Device removed '\(id)'")
		}
	}

	nonisolated func startTransferWithQrCode(device: RemoteDeviceInfo) {}

	nonisolated func connectionWasEstablished(pinCode: String) {
		Task { @MainActor in
			self.activeTransferStatus = "PIN: \(pinCode) — Confirm on recipient"
		}
	}

	nonisolated func connectionFailed(with error: Error) {
		Task { @MainActor in
			self.activeTransferStatus = "Transfer failed: \(error.localizedDescription)"
		}
	}

	nonisolated func transferAccepted() {
		Task { @MainActor in
			self.activeTransferStatus = "Transfer accepted. Sending payload..."
		}
	}

	nonisolated func transferProgress(progress: Double) {
		Task { @MainActor in
			let percent = Int(progress * 100)
			self.activeTransferStatus = "Sending... \(percent)%"
		}
	}

	nonisolated func transferFinished() {
		Task { @MainActor in
			self.activeTransferStatus = "Sent successfully!"
			DispatchQueue.main.asyncAfter(deadline: .now() + 4.0) {
				self.activeTransferStatus = nil
			}
		}
	}
}
