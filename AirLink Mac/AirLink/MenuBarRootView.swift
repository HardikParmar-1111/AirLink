//
//  MenuBarRootView.swift
//  AirLink
//

import SwiftUI
import UniformTypeIdentifiers

struct MenuBarRootView: View {
	@EnvironmentObject private var model: AirLinkModel

	var body: some View {
		VStack(alignment: .leading, spacing: 14) {
			HStack {
				VStack(alignment: .leading, spacing: 2) {
					Text("AirLink")
						.font(.headline)
					Text(Host.current().localizedName ?? "Mac")
						.font(.caption)
						.foregroundStyle(.secondary)
				}
				Spacer()
				if let deviceName = model.connectedDeviceName {
					HStack(spacing: 6) {
						Circle()
							.fill(Color.green)
							.frame(width: 7, height: 7)
						Text("Connected: \(deviceName)")
							.font(.caption.weight(.medium))
							.foregroundColor(.green)
						if let battery = model.batteryLevel {
							HStack(spacing: 2) {
								Image(systemName: model.batteryIconName)
									.font(.system(size: 10, weight: .semibold))
									.foregroundColor(model.batteryColor)
								Text("\(battery)%")
									.font(.system(size: 9, weight: .bold, design: .rounded))
									.foregroundColor(.secondary)
							}
						}
					}
					.padding(.horizontal, 8)
					.padding(.vertical, 4)
					.background(Color.green.opacity(0.12), in: Capsule())
				} else {
					HStack(spacing: 5) {
						Circle()
							.fill(Color.orange)
							.frame(width: 7, height: 7)
						Text("Waiting for connection...")
							.font(.caption)
							.foregroundColor(.secondary)
					}
					.padding(.horizontal, 8)
					.padding(.vertical, 4)
					.background(Color.secondary.opacity(0.1), in: Capsule())
				}
			}

			Toggle("Discoverable", isOn: $model.isDiscoverable)
				.toggleStyle(.switch)

			if let status = model.activeTransferStatus {
				HStack(spacing: 8) {
					Image(systemName: "arrow.triangle.2.circlepath")
						.font(.subheadline)
						.foregroundStyle(.tint)
					Text(status)
						.font(.caption.weight(.medium))
				}
				.padding(8)
				.frame(maxWidth: .infinity, alignment: .leading)
				.background(.tint.opacity(0.12), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
			}

			// MARK: - Phone Controls (Only visible when connected)
			let isConnected = (model.connectedDeviceName != nil && !(model.connectedDeviceName?.isEmpty ?? true))
			if isConnected {
				VStack(spacing: 8) {
					// Mirror Phone Button
					Button {
						PhoneMirroringWindowController.shared.showMirroringWindow()
					} label: {
						HStack(spacing: 10) {
							ZStack {
								Circle()
									.fill(Color.accentColor.opacity(0.15))
									.frame(width: 28, height: 28)
								Image(systemName: "iphone.gen3")
									.font(.system(size: 13, weight: .semibold))
									.foregroundColor(.accentColor)
							}
							VStack(alignment: .leading, spacing: 1) {
								Text("Mirror Phone")
									.font(.subheadline.weight(.semibold))
									.foregroundColor(.primary)
								Text("Live view & remote touch control")
									.font(.caption2)
									.foregroundColor(.secondary)
							}
							Spacer()
							Image(systemName: "arrow.up.right.square")
								.font(.footnote)
								.foregroundColor(.secondary)
						}
						.padding(.horizontal, 10)
						.padding(.vertical, 8)
						.background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
					}
					.buttonStyle(.plain)

					// Find My Phone Button
					Button {
						model.ringPhone()
					} label: {
						HStack(spacing: 10) {
							ZStack {
								Circle()
									.fill(Color.orange.opacity(0.15))
									.frame(width: 28, height: 28)
								Image(systemName: "bell.badge.fill")
									.font(.system(size: 13, weight: .semibold))
									.foregroundColor(.orange)
							}
							VStack(alignment: .leading, spacing: 1) {
								Text("Find My Phone")
									.font(.subheadline.weight(.semibold))
									.foregroundColor(.primary)
								Text("Ring phone at full volume")
									.font(.caption2)
									.foregroundColor(.secondary)
							}
							Spacer()
							Image(systemName: "speaker.wave.3.fill")
								.font(.footnote)
								.foregroundColor(.secondary)
						}
						.padding(.horizontal, 10)
						.padding(.vertical, 8)
						.background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
					}
					.buttonStyle(.plain)
				}
			}

			InstantDropZone()

			VStack(alignment: .leading, spacing: 8) {
				Text("Recent Transfers")
					.font(.subheadline.weight(.semibold))
				ForEach(model.recentTransfers) { item in
					VStack(alignment: .leading, spacing: 2) {
						Text(item.title)
							.font(.callout)
						Text(item.subtitle)
							.font(.caption)
							.foregroundStyle(.secondary)
					}
					.frame(maxWidth: .infinity, alignment: .leading)
					.padding(8)
					.background(.quaternary.opacity(0.4), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
				}
			}

			Divider()

			Button("Quit AirLink") {
				NSApplication.shared.terminate(nil)
			}
			.buttonStyle(.plain)
			.foregroundStyle(.secondary)
		}
		.padding(16)
		.frame(width: 320)
	}
}

struct InstantDropZone: View {
	@State private var isTargeted = false

	var body: some View {
		VStack(spacing: 6) {
			Image(systemName: isTargeted ? "paperplane.fill" : "square.and.arrow.down")
				.font(.title2)
			Text(isTargeted ? "Release to send" : "Drop to send to Android")
				.font(.subheadline.weight(.medium))
			Text("Goes to the connected phone instantly. No device picker.")
				.font(.caption)
				.foregroundStyle(.secondary)
				.multilineTextAlignment(.center)
		}
		.frame(maxWidth: .infinity)
		.padding(.vertical, 24)
		.background(
			RoundedRectangle(cornerRadius: 10, style: .continuous)
				.fill(isTargeted ? Color.accentColor.opacity(0.12) : Color.clear)
		)
		.overlay(
			RoundedRectangle(cornerRadius: 10, style: .continuous)
				.strokeBorder(style: StrokeStyle(lineWidth: 1.5, dash: [6, 4]))
				.foregroundStyle(isTargeted ? Color.accentColor : .secondary.opacity(0.7))
		)
		.onDrop(of: [UTType.fileURL], isTargeted: $isTargeted) { providers in
			loadDroppedFileURLs(from: providers)
			return true
		}
	}

	private func loadDroppedFileURLs(from providers: [NSItemProvider]) {
		for provider in providers {
			_ = provider.loadObject(ofClass: URL.self) { url, _ in
				guard let url, url.isFileURL else { return }
				DispatchQueue.main.async {
					AppDelegate.shared?.initiateFileSend(urls: [url])
				}
			}
		}
	}
}
