//
//  AppDelegate.swift
//  AirLink
//
//  Created by Grishka on 08.04.2023.
//

import Cocoa
import UserNotifications
import AirLinkShare
import SwiftUI

class AppDelegate: NSObject, NSApplicationDelegate, UNUserNotificationCenterDelegate, MainAppDelegate {
	static private(set) var shared: AppDelegate?
	private var activeIncomingTransfers: [String: TransferInfo] = [:]
	private var statusItem: NSStatusItem?
	private var popover: NSPopover?
	private var dropOverlayView: StatusItemDropView?

	func applicationDidFinishLaunching(_ aNotification: Notification) {
		AppDelegate.shared = self
		setupStatusItem()

		let nc = UNUserNotificationCenter.current()
		nc.requestAuthorization(options: [.alert, .sound, .badge]) { granted, err in
			if !granted {
				DispatchQueue.main.async {
					self.showNotificationsDeniedAlert()
				}
			}
		}
		nc.delegate = self
		let incomingTransfersCategory = UNNotificationCategory(identifier: "INCOMING_TRANSFERS", actions: [
			UNNotificationAction(identifier: "ACCEPT", title: NSLocalizedString("Accept", comment: ""), options: UNNotificationActionOptions.authenticationRequired),
			UNNotificationAction(identifier: "DECLINE", title: NSLocalizedString("Decline", comment: ""))
		], intentIdentifiers: [])
		let errorsCategory = UNNotificationCategory(identifier: "ERRORS", actions: [], intentIdentifiers: [])

		let declineAndTextAction = UNNotificationAction(
			identifier: "DECLINE_AND_TEXT",
			title: "Decline & Text",
			options: [.destructive]
		)
		let incomingCallCategory = UNNotificationCategory(
			identifier: "INCOMING_CALL",
			actions: [declineAndTextAction],
			intentIdentifiers: [],
			options: [.customDismissAction]
		)

		let quickReplyAction = UNTextInputNotificationAction(
			identifier: "REPLY_ACTION",
			title: "Reply",
			options: [],
			textInputButtonTitle: "Send",
			textInputPlaceholder: "Type a reply..."
		)
		let replyCategory = UNNotificationCategory(
			identifier: "REPLY_NOTIFICATION",
			actions: [quickReplyAction],
			intentIdentifiers: [],
			options: []
		)

		nc.setNotificationCategories([incomingTransfersCategory, errorsCategory, incomingCallCategory, replyCategory])
		AirLinkConnectionManager.shared.mainAppDelegate = self
		
		DispatchQueue.global(qos: .userInitiated).async {
			AirLinkWebSocketServer.shared.start(port: 8080)
			AirLinkConnectionManager.shared.becomeVisible()
		}
		
		// Start Phase 2: Universal Clipboard continuous monitoring
		AirLinkClipboardManager.shared.startMonitoring()

		// Start Notch Drop Zone for instant file beaming
		AirLinkNotchDropManager.shared.setup()
	}

	// MARK: - Menu Bar & Drop Target Setup
	private func setupStatusItem() {
		// Initialize the NSPopover hosting SwiftUI MenuBarRootView
		let popover = NSPopover()
		popover.contentSize = NSSize(width: 320, height: 380)
		popover.behavior = .transient
		popover.contentViewController = NSHostingController(
			rootView: MenuBarRootView().environmentObject(AirLinkModel.shared)
		)
		self.popover = popover

		// Create NSStatusItem
		statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
		guard let button = statusItem?.button else { return }

		button.image = NSImage(named: "MenuBarIcon")
		button.image?.isTemplate = true
		button.imagePosition = .imageOnly
		button.title = ""
		button.toolTip = "Waiting for connection..."

		// Overlay view to intercept and handle drag-and-drop
		let dropView = StatusItemDropView(frame: button.bounds)
		dropView.autoresizingMask = [.width, .height]
		dropView.onDropURLs = { [weak self] urls in
			self?.initiateFileSend(urls: urls)
		}
		dropView.onClick = { [weak self] in
			self?.togglePopover()
		}
		button.addSubview(dropView)
		self.dropOverlayView = dropView
	}

	override init() {
		super.init()
		AppDelegate.shared = self
	}

	private var connectionMenuItem = NSMenuItem(title: "Waiting for connection...", action: nil, keyEquivalent: "")

	var currentConnectionText: String {
		if let name = AirLinkWebSocketServer.shared.connectedDeviceName, !name.isEmpty {
			return "Connected: \(name)"
		}
		return "Waiting for connection..."
	}

	func updateConnectedDevice(name: String?) {
		DispatchQueue.main.async {
			guard let button = self.statusItem?.button else {
				print("AirLink: statusItem button is nil")
				return
			}
			// Only display the icon in the menu bar — no device name in the title
			button.imagePosition = .imageOnly
			button.title = ""
			if let name = name, !name.isEmpty {
				button.toolTip = "Connected: \(name)"
				self.statusItem?.toolTip = "Connected: \(name)"
				self.dropOverlayView?.toolTip = "Connected: \(name)"
				self.connectionMenuItem.title = "Connected: \(name)"
				print("Updated Menu Bar tooltip with: \(name) (icon only in menu bar)")
				fflush(stdout)
			} else {
				button.toolTip = "Waiting for connection..."
				self.statusItem?.toolTip = "Waiting for connection..."
				self.dropOverlayView?.toolTip = "Waiting for connection..."
				self.connectionMenuItem.title = "Waiting for connection..."
			}
		}
	}

	@objc func togglePopover() {
		guard let button = statusItem?.button, let popover = popover else { return }
		if popover.isShown {
			popover.performClose(nil)
		} else {
			showPopover()
		}
	}

	func showPopover() {
		guard let button = statusItem?.button, let popover = popover else { return }
		NSApp.activate(ignoringOtherApps: true)
		popover.show(relativeTo: button.bounds, of: button, preferredEdge: .minY)
	}

	func flashMenuBarIcon() {
		DispatchQueue.main.async {
			guard let button = self.statusItem?.button else { return }
			button.isHighlighted = true
			DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
				button.isHighlighted = false
				DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
					button.isHighlighted = true
					DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
						button.isHighlighted = false
					}
				}
			}
		}
	}

	// MARK: - File Sending Entry Point (Direct WebSocket Beam)
	func initiateFileSend(urls: [URL]) {
		guard !urls.isEmpty else { return }
		let filename = urls.count == 1 ? urls[0].lastPathComponent : "\(urls.count) files"
		print("AirLink Menu Bar Drop: auto-sending \(filename) to active Android client")

		let sent = AirLinkWebSocketServer.shared.sendDroppedFiles(urls: urls)
		guard sent else { return }

		Task { @MainActor in
			AirLinkModel.shared.recentTransfers.insert(
				RecentTransferItem(
					id: UUID(),
					title: "Sent to Android",
					subtitle: filename,
					timestamp: Date()
				),
				at: 0
			)
			self.flashMenuBarIcon()
			AirLinkNotchDropManager.shared.triggerSentFeedback(
				title: "Sent to Android",
				subtitle: filename
			)
		}
	}

	func applicationShouldHandleReopen(_ sender: NSApplication, hasVisibleWindows flag: Bool) -> Bool {
		return true
	}

	func applicationWillTerminate(_ aNotification: Notification) {
		UNUserNotificationCenter.current().removeAllDeliveredNotifications()
		AirLinkClipboardManager.shared.stopMonitoring()
		AirLinkWebSocketServer.shared.stop()
		AirLinkConnectionManager.shared.becomeInvisible()
	}

	func applicationSupportsSecureRestorableState(_ app: NSApplication) -> Bool {
		return true
	}

	func showNotificationsDeniedAlert() {
		let alert = NSAlert()
		alert.alertStyle = .critical
		alert.messageText = NSLocalizedString("NotificationsDenied.Title", value: "Notification Permission Required", comment: "")
		alert.informativeText = NSLocalizedString("NotificationsDenied.Message", value: "AirLink needs to be able to display notifications for incoming file transfers. Please allow notifications in System Settings.", comment: "")
		alert.addButton(withTitle: NSLocalizedString("NotificationsDenied.OpenSettings", value: "Open settings", comment: ""))
		alert.addButton(withTitle: NSLocalizedString("Quit", value: "Quit AirLink", comment: ""))
		let result = alert.runModal()
		if result == NSApplication.ModalResponse.alertFirstButtonReturn {
			NSWorkspace.shared.open(URL(string: "x-apple.systempreferences:com.apple.preference.notifications")!)
		} else if result == NSApplication.ModalResponse.alertSecondButtonReturn {
			NSApplication.shared.terminate(nil)
		}
	}

	func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
		if let transferID = response.notification.request.content.userInfo["transferID"] as? String {
			AirLinkConnectionManager.shared.submitUserConsent(transferID: transferID, accept: response.actionIdentifier == "ACCEPT")
			if response.actionIdentifier != "ACCEPT" {
				activeIncomingTransfers.removeValue(forKey: transferID)
			}
		}

		if response.actionIdentifier == "DECLINE_AND_TEXT" {
			let quickReplyText = "I will call back later, please text me."
			print("AirLink: User pressed Decline & Text. Dispatching WebSocket command to Android...")
			var payload: [String: Any] = [
				"type": "decline_and_text",
				"message": quickReplyText
			]
			if let phoneNumber = response.notification.request.content.userInfo["phoneNumber"] as? String {
				payload["number"] = phoneNumber
				payload["phoneNumber"] = phoneNumber
			}
			if let data = try? JSONSerialization.data(withJSONObject: payload),
			   let jsonString = String(data: data, encoding: .utf8) {
				AirLinkWebSocketServer.shared.sendMessage(json: jsonString)
			}
		}

		if let textResponse = response as? UNTextInputNotificationResponse {
			let replyText = textResponse.userText.trimmingCharacters(in: .whitespacesAndNewlines)
			let notificationKey = response.notification.request.content.userInfo["key"] as? String ?? ""
			if !notificationKey.isEmpty && !replyText.isEmpty {
				print("AirLink: User typed quick reply '\(replyText)' for notification key '\(notificationKey)'. Dispatching to Android...")
				fflush(stdout)
				let payload: [String: Any] = [
					"action": "REPLY",
					"key": notificationKey,
					"message": replyText
				]
				if let data = try? JSONSerialization.data(withJSONObject: payload),
				   let jsonString = String(data: data, encoding: .utf8) {
					AirLinkWebSocketServer.shared.sendMessage(json: jsonString)
				}
			}
		}

		completionHandler()
	}

	func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
		if #available(macOS 11.0, *) {
			completionHandler([.banner, .sound, .badge, .list])
		} else {
			completionHandler([.alert, .sound, .badge])
		}
	}

	func obtainUserConsent(for transfer: TransferMetadata, from device: RemoteDeviceInfo) {
		let fileStr: String
		if let textTitle = transfer.textDescription {
			fileStr = textTitle
		} else if transfer.files.count == 1 {
			fileStr = transfer.files[0].name
		} else {
			fileStr = String.localizedStringWithFormat(NSLocalizedString("NFiles", value: "%d files", comment: ""), transfer.files.count)
		}
		let notificationContent = UNMutableNotificationContent()
		notificationContent.title = "AirLink"
		notificationContent.subtitle = String(format: NSLocalizedString("PinCode", value: "PIN: %@", comment: ""), arguments: [transfer.pinCode!])
		notificationContent.body = String(format: NSLocalizedString("DeviceSendingFiles", value: "%1$@ is sending you %2$@", comment: ""), arguments: [device.name, fileStr])
		notificationContent.sound = .default
		notificationContent.categoryIdentifier = "INCOMING_TRANSFERS"
		notificationContent.userInfo = ["transferID": transfer.id]
		if #available(macOS 11.0, *) {
			AirLinkNotificationCenterHackery.removeDefaultAction(notificationContent)
		}
		let notificationReq = UNNotificationRequest(identifier: "transfer_" + transfer.id, content: notificationContent, trigger: nil)
		UNUserNotificationCenter.current().add(notificationReq)
		self.activeIncomingTransfers[transfer.id] = TransferInfo(device: device, transfer: transfer)
	}

	func incomingTransfer(id: String, didFinishWith error: Error?) {
		guard let transfer = self.activeIncomingTransfers[id] else { return }
		if let error = error {
			let notificationContent = UNMutableNotificationContent()
			notificationContent.title = String(format: NSLocalizedString("TransferError", value: "Failed to receive files from %@", comment: ""), arguments: [transfer.device.name])
			if let ne = (error as? AirLinkError) {
				switch ne {
				case .inputOutput:
					notificationContent.body = "I/O Error"
				case .protocolError(_):
					notificationContent.body = NSLocalizedString("Error.Protocol", value: "Communication error", comment: "")
				case .requiredFieldMissing:
					notificationContent.body = NSLocalizedString("Error.Protocol", value: "Communication error", comment: "")
				case .ukey2:
					notificationContent.body = NSLocalizedString("Error.Crypto", value: "Encryption error", comment: "")
				case .canceled(reason: _):
					break
				}
			} else {
				notificationContent.body = error.localizedDescription
			}
			notificationContent.categoryIdentifier = "ERRORS"
			UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "transferError_" + id, content: notificationContent, trigger: nil))
		}
		UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: ["transfer_" + id])
		self.activeIncomingTransfers.removeValue(forKey: id)
	}
}

struct TransferInfo {
	let device: RemoteDeviceInfo
	let transfer: TransferMetadata
}

// MARK: - Drag and Drop Destination View for Menu Bar
final class StatusItemDropView: NSView {
	var onDropURLs: (([URL]) -> Void)?
	var onClick: (() -> Void)?

	override init(frame: NSRect) {
		super.init(frame: frame)
		registerForDraggedTypes([.fileURL])
	}

	required init?(coder: NSCoder) {
		super.init(coder: coder)
		registerForDraggedTypes([.fileURL])
	}

	// MARK: - NSDraggingDestination
	override func draggingEntered(_ sender: any NSDraggingInfo) -> NSDragOperation {
		let pboard = sender.draggingPasteboard
		if pboard.canReadItem(withDataConformingToTypes: [NSPasteboard.PasteboardType.fileURL.rawValue]) {
			(superview as? NSStatusBarButton)?.isHighlighted = true
			return .copy
		}
		return []
	}

	override func draggingUpdated(_ sender: any NSDraggingInfo) -> NSDragOperation {
		return .copy
	}

	override func draggingExited(_ sender: (any NSDraggingInfo)?) {
		(superview as? NSStatusBarButton)?.isHighlighted = false
	}

	override func prepareForDragOperation(_ sender: any NSDraggingInfo) -> Bool {
		return true
	}

	override func performDragOperation(_ sender: any NSDraggingInfo) -> Bool {
		(superview as? NSStatusBarButton)?.isHighlighted = false
		let pboard = sender.draggingPasteboard
		guard let fileURLs = pboard.readObjects(forClasses: [NSURL.self], options: [.urlReadingFileURLsOnly: true]) as? [URL], !fileURLs.isEmpty else {
			return false
		}
		// Immediate auto-send — no device picker, no popover.
		onDropURLs?(fileURLs)
		return true
	}

	// MARK: - Mouse Click
	override func mouseDown(with event: NSEvent) {
		(superview as? NSStatusBarButton)?.isHighlighted = true
	}

	override func mouseUp(with event: NSEvent) {
		(superview as? NSStatusBarButton)?.isHighlighted = false
		let location = convert(event.locationInWindow, from: nil)
		if bounds.contains(location) {
			onClick?()
		}
	}

	override func rightMouseDown(with event: NSEvent) {
		let menu = NSMenu()
		let statusText = AppDelegate.shared?.currentConnectionText ?? "Waiting for connection..."
		let item = NSMenuItem(title: statusText, action: nil, keyEquivalent: "")
		item.isEnabled = false
		menu.addItem(item)
		menu.addItem(NSMenuItem.separator())
		menu.addItem(NSMenuItem(title: "Quit AirLink", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q"))
		NSMenu.popUpContextMenu(menu, with: event, for: self)
	}
}
