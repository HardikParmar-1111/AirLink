//
//  AirLinkNotchDrop.swift
//  AirLink
//
//  Dynamic Island UI (NotchNook style) for macOS AirLink:
//  - Borderless, transparent NSPanel anchored to absolute top-center at .popUpMenu level.
//  - Permanent resting hit box (300x60) with ignoresMouseEvents = false for reliable drag capture.
//  - Flat top edge (0 radius) and rounded bottom corners (24 radius) to merge with hardware notch.
//  - Invisible resting state via Color.clear (no opacity(0) bugs) maintaining hit testing.
//  - Expanded state: 400x120 NotchNook split layout (Left: Files Tray, Right: Connected Device box).
//  - Fluid Apple-style spring animation pulling downward from the top bezel.
//

import AppKit
import Foundation
import SwiftUI

// MARK: - Notch Geometry Shape (Flat Top Edge, Rounded Bottom Corners)
struct NotchShape: Shape {
	var bottomRadius: CGFloat = 24

	var animatableData: CGFloat {
		get { bottomRadius }
		set { bottomRadius = newValue }
	}

	func path(in rect: CGRect) -> Path {
		var path = Path()
		let r = min(bottomRadius, rect.height, rect.width / 2)
		// Flat top edge: straight line from top-left (minX, minY) to top-right (maxX, minY)
		path.move(to: CGPoint(x: rect.minX, y: rect.minY))
		path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
		// Right edge down to bottom-right corner start
		path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY - r))
		// Bottom-right rounded corner
		path.addArc(
			center: CGPoint(x: rect.maxX - r, y: rect.maxY - r),
			radius: r,
			startAngle: .degrees(0),
			endAngle: .degrees(90),
			clockwise: false
		)
		// Bottom edge
		path.addLine(to: CGPoint(x: rect.minX + r, y: rect.maxY))
		// Bottom-left rounded corner
		path.addArc(
			center: CGPoint(x: rect.minX + r, y: rect.maxY - r),
			radius: r,
			startAngle: .degrees(90),
			endAngle: .degrees(180),
			clockwise: false
		)
		// Left edge straight up to top-left
		path.addLine(to: CGPoint(x: rect.minX, y: rect.minY))
		path.closeSubpath()
		return path
	}
}

// MARK: - Notch Drop State
final class NotchDropState: ObservableObject {
	static let shared = NotchDropState()

	@Published var isExpanded: Bool = false
	@Published var isSuccess: Bool = false
	@Published var isReceived: Bool = false
	@Published var isPairingRequest: Bool = false
	@Published var pairingDeviceName: String = ""
	@Published var pairingDeviceId: String = ""
	@Published var activeFileName: String? = nil
	@Published var feedbackTitle: String = "Sent to Android"
	@Published var feedbackSubtitle: String? = nil

	var onAcceptPairing: (() -> Void)?
	var onDeclinePairing: (() -> Void)?

	private init() {}
}

// MARK: - Notch Drop SwiftUI View
struct NotchDropContentView: View {
	@ObservedObject var state = NotchDropState.shared
	@ObservedObject var model = AirLinkModel.shared

	private let springAnimation = Animation.spring(response: 0.3, dampingFraction: 0.65, blendDuration: 0)

	var body: some View {
		ZStack(alignment: .top) {
			// Root notch shape: invisible Color.clear at rest (300x60), expands into black Dynamic Island (400x120)
			notchBackground(
				width: state.isExpanded ? 400 : 300,
				height: state.isExpanded ? 120 : 60,
				isExpanded: state.isExpanded
			)
			.contentShape(Rectangle())
			.shadow(
				color: Color.black.opacity(state.isExpanded ? 0.45 : 0.0),
				radius: 18,
				x: 0,
				y: 8
			)
			.overlay(
				notchStroke(
					width: state.isExpanded ? 400 : 300,
					height: state.isExpanded ? 120 : 60,
					isExpanded: state.isExpanded
				)
			)
			.overlay(
				Group {
					if state.isExpanded {
						if state.isPairingRequest {
							pairingView
								.transition(.opacity.combined(with: .scale(scale: 0.96)))
						} else {
							expandedView
								.transition(.opacity.combined(with: .scale(scale: 0.96)))
						}
					}
				}
			)
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
		.ignoresSafeArea()
		.animation(springAnimation, value: state.isExpanded)
		.animation(springAnimation, value: state.isSuccess)
		.animation(springAnimation, value: state.isReceived)
		.animation(springAnimation, value: state.isPairingRequest)
	}

	@ViewBuilder
	private func notchBackground(width: CGFloat, height: CGFloat, isExpanded: Bool) -> some View {
		if #available(macOS 14.0, *) {
			UnevenRoundedRectangle(
				topLeadingRadius: 0,
				bottomLeadingRadius: 24,
				bottomTrailingRadius: 24,
				topTrailingRadius: 0,
				style: .continuous
			)
			.fill(isExpanded ? Color.black : Color.clear)
			.frame(width: width, height: height)
		} else {
			NotchShape(bottomRadius: 24)
				.fill(isExpanded ? Color.black : Color.clear)
				.frame(width: width, height: height)
		}
	}

	@ViewBuilder
	private func notchStroke(width: CGFloat, height: CGFloat, isExpanded: Bool) -> some View {
		if #available(macOS 14.0, *) {
			UnevenRoundedRectangle(
				topLeadingRadius: 0,
				bottomLeadingRadius: 24,
				bottomTrailingRadius: 24,
				topTrailingRadius: 0,
				style: .continuous
			)
			.stroke(Color.white.opacity(isExpanded ? 0.12 : 0.0), lineWidth: 1)
			.frame(width: width, height: height)
		} else {
			NotchShape(bottomRadius: 24)
				.stroke(Color.white.opacity(isExpanded ? 0.12 : 0.0), lineWidth: 1)
				.frame(width: width, height: height)
		}
	}

	// MARK: - Expanded NotchNook Layout
	private var expandedView: some View {
		HStack(spacing: 12) {
			// Left Side: Files Tray Drop Zone
			leftTrayView

			// Right Side: Connected Device / AirLink Bounding Box
			rightStatusView
		}
		// Padded to sit cleanly below the 32pt physical hardware notch
		.padding(.top, 34)
		.padding(.bottom, 12)
		.padding(.horizontal, 14)
	}

	// MARK: - Left Side: Files Tray
	private var leftTrayView: some View {
		VStack(spacing: 5) {
			if state.isSuccess {
				Image(systemName: "checkmark.circle.fill")
					.font(.system(size: 20, weight: .bold))
					.foregroundColor(.green)
				Text("Sent!")
					.font(.system(size: 11, weight: .bold, design: .rounded))
					.foregroundColor(.white)
			} else if state.isReceived {
				Image(systemName: "arrow.down.circle.fill")
					.font(.system(size: 20, weight: .bold))
					.foregroundColor(.green)
				Text("Received!")
					.font(.system(size: 11, weight: .bold, design: .rounded))
					.foregroundColor(.white)
			} else {
				Image(systemName: "tray.and.arrow.down.fill")
					.font(.system(size: 20, weight: .bold))
					.foregroundColor(.cyan)
				Text("Drop to send")
					.font(.system(size: 11, weight: .bold, design: .rounded))
					.foregroundColor(.white)
			}
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.background(
			RoundedRectangle(cornerRadius: 14, style: .continuous)
				.fill(state.isSuccess || state.isReceived ? Color.green.opacity(0.12) : Color.cyan.opacity(0.08))
		)
		.overlay(
			RoundedRectangle(cornerRadius: 14, style: .continuous)
				.strokeBorder(
					style: StrokeStyle(lineWidth: 1.5, dash: (state.isSuccess || state.isReceived) ? [] : [5, 4])
				)
				.foregroundColor(state.isSuccess || state.isReceived ? Color.green.opacity(0.8) : Color.cyan.opacity(0.85))
		)
	}

	// MARK: - Right Side: Connected Device Box
	private var rightStatusView: some View {
		let isConnected = model.connectedDeviceName != nil
		let deviceName = model.connectedDeviceName ?? "Android Device"

		return VStack(alignment: .leading, spacing: 3) {
			HStack(spacing: 5) {
				Circle()
					.fill(isConnected ? Color.green : Color.orange)
					.frame(width: 7, height: 7)
				Text(isConnected ? "Connected" : "AirLink")
					.font(.system(size: 10, weight: .bold, design: .rounded))
					.foregroundColor(isConnected ? .green : .orange)
					.textCase(.uppercase)
				Spacer(minLength: 0)
				if isConnected {
					HStack(spacing: 4) {
						Button {
							model.ringPhone()
						} label: {
							HStack(spacing: 3) {
								Image(systemName: "bell.fill")
									.font(.system(size: 8, weight: .bold))
								Text("Ring")
									.font(.system(size: 9, weight: .bold))
							}
							.foregroundColor(.yellow)
							.padding(.horizontal, 6)
							.padding(.vertical, 2)
							.background(Color.yellow.opacity(0.18), in: Capsule())
						}
						.buttonStyle(.plain)

						Button {
							PhoneMirroringWindowController.shared.showMirroringWindow()
						} label: {
							HStack(spacing: 3) {
								Image(systemName: "iphone.gen3")
									.font(.system(size: 8, weight: .bold))
								Text("Mirror")
									.font(.system(size: 9, weight: .bold))
							}
							.foregroundColor(.cyan)
							.padding(.horizontal, 6)
							.padding(.vertical, 2)
							.background(Color.cyan.opacity(0.18), in: Capsule())
						}
						.buttonStyle(.plain)
					}
				}
			}

			HStack(spacing: 6) {
				Text(state.isSuccess || state.isReceived ? (state.feedbackSubtitle ?? deviceName) : deviceName)
					.font(.system(size: 13, weight: .bold, design: .rounded))
					.foregroundColor(.white)
					.lineLimit(1)
					.truncationMode(.tail)

				if isConnected, let battery = model.batteryLevel {
					HStack(spacing: 3) {
						Image(systemName: model.batteryIconName)
							.font(.system(size: 11, weight: .semibold))
							.foregroundColor(model.batteryColor)
						Text("\(battery)%")
							.font(.system(size: 10, weight: .bold, design: .rounded))
							.foregroundColor(.white.opacity(0.9))
					}
					.padding(.horizontal, 6)
					.padding(.vertical, 2)
					.background(Color.white.opacity(0.12), in: Capsule())
				}
			}

			Text(state.isSuccess ? "Sent via AirLink" : (state.isReceived ? "Received via AirLink" : (isConnected ? "Ready for beaming" : "Waiting for connection...")))
				.font(.system(size: 10, weight: .medium))
				.foregroundColor(.white.opacity(0.65))
				.lineLimit(1)
		}
		.padding(.horizontal, 12)
		.padding(.vertical, 8)
		.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
		.background(
			RoundedRectangle(cornerRadius: 14, style: .continuous)
				.fill(Color(white: 0.1))
		)
	}

	// MARK: - Pairing Request Layout
	private var pairingView: some View {
		HStack(spacing: 12) {
			// Left Side: Device information
			VStack(alignment: .leading, spacing: 3) {
				HStack(spacing: 5) {
					Circle()
						.fill(Color.blue)
						.frame(width: 7, height: 7)
					Text("Pairing Request")
						.font(.system(size: 10, weight: .bold, design: .rounded))
						.foregroundColor(.blue)
						.textCase(.uppercase)
					Spacer(minLength: 0)
				}

				Text(state.pairingDeviceName.isEmpty ? "Android Device" : state.pairingDeviceName)
					.font(.system(size: 13, weight: .bold, design: .rounded))
					.foregroundColor(.white)
					.lineLimit(1)
					.truncationMode(.tail)

				Text("wants to connect")
					.font(.system(size: 10, weight: .medium))
					.foregroundColor(.white.opacity(0.65))
					.lineLimit(1)
			}
			.padding(.horizontal, 12)
			.padding(.vertical, 8)
			.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
			.background(
				RoundedRectangle(cornerRadius: 14, style: .continuous)
					.fill(Color(white: 0.1))
			)

			// Right Side: Accept & Decline buttons
			HStack(spacing: 8) {
				Button(action: {
					state.onDeclinePairing?()
				}) {
					Text("Decline")
						.font(.system(size: 11, weight: .semibold, design: .rounded))
						.foregroundColor(.white.opacity(0.85))
						.frame(maxWidth: .infinity, maxHeight: .infinity)
						.background(
							RoundedRectangle(cornerRadius: 12, style: .continuous)
								.fill(Color.red.opacity(0.2))
						)
						.overlay(
							RoundedRectangle(cornerRadius: 12, style: .continuous)
								.stroke(Color.red.opacity(0.6), lineWidth: 1)
						)
				}
				.buttonStyle(.plain)

				Button(action: {
					state.onAcceptPairing?()
				}) {
					Text("Accept")
						.font(.system(size: 11, weight: .bold, design: .rounded))
						.foregroundColor(.white)
						.frame(maxWidth: .infinity, maxHeight: .infinity)
						.background(
							RoundedRectangle(cornerRadius: 12, style: .continuous)
								.fill(Color.green)
						)
				}
				.buttonStyle(.plain)
			}
			.frame(width: 140)
			.frame(maxHeight: .infinity)
		}
		.padding(.top, 34)
		.padding(.bottom, 12)
		.padding(.horizontal, 14)
	}
}

// MARK: - Zero-Latency AppKit Drag Destination View
final class NotchDropContainerView: NSView {
	var onDragEntered: (() -> Void)?
	var onDragExited: (() -> Void)?
	var onDropURLs: (([URL]) -> Void)?

	override init(frame: NSRect) {
		super.init(frame: frame)
		setupDrag()
	}

	required init?(coder: NSCoder) {
		super.init(coder: coder)
		setupDrag()
	}

	private func setupDrag() {
		registerForDraggedTypes([
			.fileURL,
			NSPasteboard.PasteboardType("NSFilenamesPboardType")
		])
	}

	override func hitTest(_ point: NSPoint) -> NSView? {
		let result = super.hitTest(point)
		return result ?? self
	}

	// Instant millisecond trigger: synchronous callback on main thread
	override func draggingEntered(_ sender: any NSDraggingInfo) -> NSDragOperation {
		let pboard = sender.draggingPasteboard
		if pboard.canReadItem(withDataConformingToTypes: [NSPasteboard.PasteboardType.fileURL.rawValue]) ||
		   pboard.types?.contains(NSPasteboard.PasteboardType("NSFilenamesPboardType")) == true {
			onDragEntered?()
			return .copy
		}
		return []
	}

	override func draggingUpdated(_ sender: any NSDraggingInfo) -> NSDragOperation {
		return .copy
	}

	override func draggingExited(_ sender: (any NSDraggingInfo)?) {
		onDragExited?()
	}

	override func prepareForDragOperation(_ sender: any NSDraggingInfo) -> Bool {
		return true
	}

	override func performDragOperation(_ sender: any NSDraggingInfo) -> Bool {
		let pboard = sender.draggingPasteboard

		var urls: [URL] = []
		if let fileURLs = pboard.readObjects(forClasses: [NSURL.self], options: [.urlReadingFileURLsOnly: true]) as? [URL] {
			urls = fileURLs
		} else if let paths = pboard.propertyList(forType: NSPasteboard.PasteboardType("NSFilenamesPboardType")) as? [String] {
			urls = paths.map { URL(fileURLWithPath: $0) }
		}

		guard !urls.isEmpty else {
			onDragExited?()
			return false
		}

		// Process .fileURL immediately — no device selection UI.
		onDropURLs?(urls)
		return true
	}
}

// MARK: - Notch Drop Window Manager
final class AirLinkNotchDropManager: NSObject {
	static let shared = AirLinkNotchDropManager()

	private var panel: NSPanel?
	private var containerView: NotchDropContainerView?
	private var hostingView: NSHostingView<NotchDropContentView>?

	// Permanent resting hit box: width 300, height 60 anchored to top-center
	private let restingWidth: CGFloat = 300
	private let restingHeight: CGFloat = 60

	// Expanded: NotchNook Dynamic Island dimensions
	private let expandedWidth: CGFloat = 400
	private let expandedHeight: CGFloat = 120

	private var collapseWorkItem: DispatchWorkItem?

	private override init() {
		super.init()
	}

	func setup() {
		guard panel == nil else { return }

		let panel = NSPanel(
			contentRect: NSRect(x: 0, y: 0, width: restingWidth, height: restingHeight),
			styleMask: [.borderless, .nonactivatingPanel],
			backing: .buffered,
			defer: false
		)

		panel.isOpaque = false
		panel.backgroundColor = .clear
		panel.hasShadow = false
		panel.hidesOnDeactivate = false
		panel.canHide = false
		panel.level = .popUpMenu // Always on top of menu bar and hardware notch
		panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary, .ignoresCycle]
		panel.isMovable = false
		panel.isMovableByWindowBackground = false
		panel.ignoresMouseEvents = false // Ensure mouse and drag events are detected

		updateFrame(panel: panel, width: restingWidth, height: restingHeight)

		let container = NotchDropContainerView(frame: NSRect(x: 0, y: 0, width: restingWidth, height: restingHeight))
		container.autoresizingMask = [.width, .height]

		let hosting = NSHostingView(rootView: NotchDropContentView())
		hosting.frame = container.bounds
		hosting.autoresizingMask = [.width, .height]

		container.addSubview(hosting)
		panel.contentView = container

		self.panel = panel
		self.containerView = container
		self.hostingView = hosting

		container.onDragEntered = { [weak self] in
			self?.expandNotch()
		}

		container.onDragExited = { [weak self] in
			self?.collapseNotch()
		}

		container.onDropURLs = { [weak self] urls in
			self?.handleDroppedFiles(urls: urls)
		}

		panel.orderFrontRegardless()

		NotificationCenter.default.addObserver(
			self,
			selector: #selector(screenParametersChanged),
			name: NSApplication.didChangeScreenParametersNotification,
			object: nil
		)
		print("AirLink Dynamic Island: Permanent hit box active (resting 300x60, expanded 400x120, ignoresMouseEvents: false)")
	}

	private func expandNotch() {
		guard let panel = panel else { return }
		collapseWorkItem?.cancel()
		collapseWorkItem = nil

		let expandBlock = {
			// Instantly expand window frame so SwiftUI spring animation is not clipped
			self.updateFrame(panel: panel, width: self.expandedWidth, height: self.expandedHeight)
			withAnimation(.spring(response: 0.3, dampingFraction: 0.65, blendDuration: 0)) {
				NotchDropState.shared.isExpanded = true
			}
		}

		if Thread.isMainThread {
			expandBlock()
		} else {
			DispatchQueue.main.async(execute: expandBlock)
		}
	}

	private func collapseNotch() {
		guard let panel = panel else { return }
		collapseWorkItem?.cancel()
		collapseWorkItem = nil

		let collapseBlock = {
			withAnimation(.spring(response: 0.3, dampingFraction: 0.65, blendDuration: 0)) {
				NotchDropState.shared.isExpanded = false
				NotchDropState.shared.isSuccess = false
				NotchDropState.shared.isReceived = false
			}
			let workItem = DispatchWorkItem { [weak self] in
				guard let self = self else { return }
				if !NotchDropState.shared.isExpanded && !NotchDropState.shared.isSuccess && !NotchDropState.shared.isReceived {
					self.updateFrame(panel: panel, width: self.restingWidth, height: self.restingHeight)
				}
			}
			self.collapseWorkItem = workItem
			DispatchQueue.main.asyncAfter(deadline: .now() + 0.35, execute: workItem)
		}

		if Thread.isMainThread {
			collapseBlock()
		} else {
			DispatchQueue.main.async(execute: collapseBlock)
		}
	}

	func triggerSentFeedback(title: String = "Sent to Android", subtitle: String? = nil) {
		DispatchQueue.main.async { [weak self] in
			guard let self = self, let panel = self.panel else { return }
			self.collapseWorkItem?.cancel()

			NotchDropState.shared.feedbackTitle = title
			NotchDropState.shared.feedbackSubtitle = subtitle
			NotchDropState.shared.isReceived = false

			self.updateFrame(panel: panel, width: self.expandedWidth, height: self.expandedHeight)
			withAnimation(.spring(response: 0.3, dampingFraction: 0.65, blendDuration: 0)) {
				NotchDropState.shared.isExpanded = true
				NotchDropState.shared.isSuccess = true
			}

			let item = DispatchWorkItem { [weak self] in
				guard let self = self else { return }
				self.collapseNotch()
			}
			self.collapseWorkItem = item
			DispatchQueue.main.asyncAfter(deadline: .now() + 2.0, execute: item)
		}
	}

	func triggerReceivedFeedback(title: String = "Received from Android", subtitle: String? = nil) {
		DispatchQueue.main.async { [weak self] in
			guard let self = self, let panel = self.panel else { return }
			self.collapseWorkItem?.cancel()

			NotchDropState.shared.feedbackTitle = title
			NotchDropState.shared.feedbackSubtitle = subtitle
			NotchDropState.shared.isSuccess = false

			self.updateFrame(panel: panel, width: self.expandedWidth, height: self.expandedHeight)
			withAnimation(.spring(response: 0.3, dampingFraction: 0.65, blendDuration: 0)) {
				NotchDropState.shared.isExpanded = true
				NotchDropState.shared.isReceived = true
			}

			let item = DispatchWorkItem { [weak self] in
				guard let self = self else { return }
				self.collapseNotch()
			}
			self.collapseWorkItem = item
			DispatchQueue.main.asyncAfter(deadline: .now() + 2.0, execute: item)
		}
	}

	private func updateFrame(panel: NSPanel, width: CGFloat, height: CGFloat) {
		let screen = NSScreen.main ?? NSScreen.screens[0]
		let screenFrame = screen.frame
		let x = screenFrame.midX - (width / 2)
		let y = screenFrame.maxY - height
		panel.setFrame(NSRect(x: x, y: y, width: width, height: height), display: true, animate: false)
	}

	private func handleDroppedFiles(urls: [URL]) {
		guard !urls.isEmpty else {
			collapseNotch()
			return
		}

		let filename = urls.count == 1 ? urls[0].lastPathComponent : "\(urls.count) files"
		print("AirLink Notch Drop: auto-sending \(filename) to active Android client")

		NotchDropState.shared.activeFileName = filename

		let sent = AirLinkWebSocketServer.shared.sendDroppedFiles(urls: urls)
		guard sent else {
			collapseNotch()
			return
		}

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
			AppDelegate.shared?.flashMenuBarIcon()
			self.triggerSentFeedback(title: "Sent to Android", subtitle: filename)
		}
	}

	func showPairingPrompt(deviceName: String, deviceId: String, onAccept: @escaping () -> Void, onDecline: @escaping () -> Void) {
		DispatchQueue.main.async { [weak self] in
			guard let self = self, let panel = self.panel else { return }
			self.collapseWorkItem?.cancel()

			NotchDropState.shared.isPairingRequest = true
			NotchDropState.shared.pairingDeviceName = deviceName
			NotchDropState.shared.pairingDeviceId = deviceId
			NotchDropState.shared.isSuccess = false
			NotchDropState.shared.isReceived = false
			NotchDropState.shared.onAcceptPairing = { [weak self] in
				onAccept()
				self?.collapsePairingPrompt()
			}
			NotchDropState.shared.onDeclinePairing = { [weak self] in
				onDecline()
				self?.collapsePairingPrompt()
			}

			self.updateFrame(panel: panel, width: self.expandedWidth, height: self.expandedHeight)
			withAnimation(.spring(response: 0.3, dampingFraction: 0.65, blendDuration: 0)) {
				NotchDropState.shared.isExpanded = true
			}
		}
	}

	func collapsePairingPrompt() {
		DispatchQueue.main.async { [weak self] in
			guard let self = self, let panel = self.panel else { return }
			self.collapseWorkItem?.cancel()
			withAnimation(.spring(response: 0.3, dampingFraction: 0.65, blendDuration: 0)) {
				NotchDropState.shared.isExpanded = false
				NotchDropState.shared.isPairingRequest = false
			}
			let workItem = DispatchWorkItem { [weak self] in
				guard let self = self else { return }
				if !NotchDropState.shared.isExpanded && !NotchDropState.shared.isPairingRequest {
					self.updateFrame(panel: panel, width: self.restingWidth, height: self.restingHeight)
				}
			}
			self.collapseWorkItem = workItem
			DispatchQueue.main.asyncAfter(deadline: .now() + 0.35, execute: workItem)
		}
	}

	@objc private func screenParametersChanged() {
		guard let panel = panel else { return }
		let isExp = NotchDropState.shared.isExpanded || NotchDropState.shared.isSuccess || NotchDropState.shared.isReceived || NotchDropState.shared.isPairingRequest
		let w = isExp ? expandedWidth : restingWidth
		let h = isExp ? expandedHeight : restingHeight
		updateFrame(panel: panel, width: w, height: h)
	}
}
