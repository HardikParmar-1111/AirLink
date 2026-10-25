//
//  IncomingNotificationAlert.swift
//  AirLink
//
//  Samsung-style incoming alert with one-tap reply templates
//  plus a custom text field.
//

import AppKit
import SwiftUI

enum AirLinkReplyTemplates {
	static let callBackLater = "I will call back later"
	static let pleaseTextMe = "Please text me"
	static let inAMeeting = "In a meeting"

	static let all: [String] = [callBackLater, pleaseTextMe, inAMeeting]
}

struct IncomingNotificationAlertView: View {
	let appName: String
	let title: String
	let bodyText: String
	let notificationKey: String
	let isPhoneCall: Bool
	let phoneNumber: String
	var onDismiss: () -> Void

	@State private var customReply: String = ""

	var body: some View {
		VStack(alignment: .leading, spacing: 12) {
			HStack(alignment: .top, spacing: 10) {
				ZStack {
					Circle()
						.fill(Color.accentColor.opacity(0.18))
						.frame(width: 34, height: 34)
					Image(systemName: isPhoneCall ? "phone.fill" : "message.fill")
						.font(.system(size: 14, weight: .semibold))
						.foregroundColor(.accentColor)
				}

				VStack(alignment: .leading, spacing: 2) {
					if !appName.isEmpty {
						Text(appName)
							.font(.caption.weight(.semibold))
							.foregroundStyle(.secondary)
					}
					Text(title.isEmpty ? (isPhoneCall ? "Incoming Call" : "New message") : title)
						.font(.headline)
						.foregroundStyle(.primary)
						.lineLimit(1)
					if !bodyText.isEmpty {
						Text(bodyText)
							.font(.subheadline)
							.foregroundStyle(.secondary)
							.lineLimit(2)
					}
				}

				Spacer(minLength: 8)

				Button(action: onDismiss) {
					Image(systemName: "xmark")
						.font(.system(size: 11, weight: .bold))
						.foregroundStyle(.secondary)
						.frame(width: 22, height: 22)
						.background(Color.secondary.opacity(0.12), in: Circle())
				}
				.buttonStyle(.plain)
			}

			VStack(spacing: 8) {
				HStack(spacing: 8) {
					templateButton(AirLinkReplyTemplates.callBackLater)
					templateButton(AirLinkReplyTemplates.pleaseTextMe)
				}
				HStack(spacing: 8) {
					templateButton(AirLinkReplyTemplates.inAMeeting)
					Spacer(minLength: 0)
				}
			}

			HStack(spacing: 8) {
				TextField("Type a reply...", text: $customReply)
					.textFieldStyle(.plain)
					.font(.subheadline)
					.padding(.horizontal, 10)
					.padding(.vertical, 8)
					.background(Color.secondary.opacity(0.12), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
					.onSubmit { sendCustomReply() }

				Button("Send") {
					sendCustomReply()
				}
				.buttonStyle(.borderedProminent)
				.controlSize(.small)
				.disabled(customReply.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
			}
		}
		.padding(16)
		.frame(width: 360)
		.background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
		.overlay(
			RoundedRectangle(cornerRadius: 18, style: .continuous)
				.stroke(Color.white.opacity(0.12), lineWidth: 1)
		)
		.shadow(color: Color.black.opacity(0.28), radius: 22, x: 0, y: 10)
	}

	private func templateButton(_ label: String) -> some View {
		Button {
			sendReply(label)
		} label: {
			Text(label)
				.font(.system(size: 11, weight: .semibold))
				.foregroundColor(.primary)
				.lineLimit(1)
				.minimumScaleFactor(0.85)
				.padding(.horizontal, 10)
				.padding(.vertical, 7)
				.frame(maxWidth: .infinity)
				.background(Color.secondary.opacity(0.14), in: Capsule())
		}
		.buttonStyle(.plain)
	}

	private func sendCustomReply() {
		let text = customReply.trimmingCharacters(in: .whitespacesAndNewlines)
		guard !text.isEmpty else { return }
		sendReply(text)
	}

	private func sendReply(_ message: String) {
		if isPhoneCall {
			AirLinkWebSocketServer.shared.sendRejectCallWithSms(number: phoneNumber, message: message)
		} else {
			AirLinkWebSocketServer.shared.sendNotificationReply(key: notificationKey, message: message)
		}
		onDismiss()
	}
}

final class IncomingNotificationAlertController {
	static let shared = IncomingNotificationAlertController()

	private var panel: NSPanel?
	private var hideWorkItem: DispatchWorkItem?

	private init() {}

	func present(appName: String, title: String, body: String, key: String) {
		present(
			appName: appName,
			title: title,
			body: body,
			notificationKey: key,
			isPhoneCall: false,
			phoneNumber: ""
		)
	}

	func presentIncomingCall(callerName: String, number: String, deviceName: String) {
		present(
			appName: deviceName,
			title: "Incoming Call",
			body: callerName == number ? "Calling: \(number)" : "\(callerName) (\(number))",
			notificationKey: "",
			isPhoneCall: true,
			phoneNumber: number
		)
	}

	private func present(
		appName: String,
		title: String,
		body: String,
		notificationKey: String,
		isPhoneCall: Bool,
		phoneNumber: String
	) {
		let showBlock = { [weak self] in
			guard let self else { return }
			self.hideWorkItem?.cancel()
			self.ensurePanel()
			guard let panel = self.panel else { return }

			let root = IncomingNotificationAlertView(
				appName: appName,
				title: title,
				bodyText: body,
				notificationKey: notificationKey,
				isPhoneCall: isPhoneCall,
				phoneNumber: phoneNumber,
				onDismiss: { [weak self] in
					self?.dismiss()
				}
			)
			let hosting = NSHostingView(rootView: root)
			let fitted = hosting.fittingSize
			if fitted.width > 0, fitted.height > 0 {
				hosting.frame.size = fitted
				panel.setContentSize(fitted)
			}
			panel.contentView = hosting
			self.position(panel)
			panel.orderFrontRegardless()
			NSApp.activate(ignoringOtherApps: false)

			let hide = DispatchWorkItem { [weak self] in
				self?.dismiss()
			}
			self.hideWorkItem = hide
			DispatchQueue.main.asyncAfter(deadline: .now() + 20, execute: hide)
		}

		if Thread.isMainThread {
			showBlock()
		} else {
			DispatchQueue.main.async(execute: showBlock)
		}
	}

	func dismiss() {
		let hideBlock = { [weak self] in
			self?.hideWorkItem?.cancel()
			self?.hideWorkItem = nil
			self?.panel?.orderOut(nil)
		}
		if Thread.isMainThread {
			hideBlock()
		} else {
			DispatchQueue.main.async(execute: hideBlock)
		}
	}

	private func ensurePanel() {
		guard panel == nil else { return }
		let panel = NSPanel(
			contentRect: NSRect(x: 0, y: 0, width: 392, height: 220),
			styleMask: [.borderless, .nonactivatingPanel],
			backing: .buffered,
			defer: false
		)
		panel.isFloatingPanel = true
		panel.level = .statusBar
		panel.isOpaque = false
		panel.backgroundColor = .clear
		panel.hasShadow = true
		panel.hidesOnDeactivate = false
		panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary, .ignoresCycle]
		panel.isMovableByWindowBackground = true
		self.panel = panel
	}

	private func position(_ panel: NSPanel) {
		guard let screen = NSScreen.main ?? NSScreen.screens.first else { return }
		let visible = screen.visibleFrame
		let size = panel.frame.size
		let x = visible.maxX - size.width - 18
		let y = visible.maxY - size.height - 18
		panel.setFrameOrigin(NSPoint(x: x, y: y))
	}
}
