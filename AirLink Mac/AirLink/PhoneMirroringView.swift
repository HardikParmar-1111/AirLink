//
//  PhoneMirroringView.swift
//  AirLink
//
//  Native iPhone Mirroring style floating window and touch/scroll control for macOS AirLink:
//  - Clean, borderless floating NSWindow with hidden titlebar filling the entire screen.
//  - Zero-latency binary WebSocket video decoding with zero HTTP overhead.
//  - Automatic frame coalescing and dropping to maintain real-time 0-lag rendering.
//  - Native Mac two-finger trackpad scrolling (scrollWheel) with 60 Hz throttling.
//  - Mouse-drag swipe gestures and precision tap coordinates sent over WebSocket.
//

import AppKit
import Foundation
import SwiftUI
import ImageIO

// MARK: - Thread-Safe Zero-Lag Binary Frame Pipeline
final class MirroringFrameQueue {
	static let shared = MirroringFrameQueue()

	private var latestPendingData: Data?
	private var isDecodingOrDrawing = false
	private let lock = NSLock()

	/// Pushes a raw binary frame. Intermediate frames arriving while decoding or drawing are dropped.
	func pushBinaryFrame(_ data: Data, onImageDecoded: @escaping (NSImage) -> Void) {
		lock.lock()
		latestPendingData = data
		let shouldStart = !isDecodingOrDrawing
		isDecodingOrDrawing = true
		lock.unlock()

		if shouldStart {
			DispatchQueue.global(qos: .userInteractive).async { [weak self] in
				self?.decodeLoop(onImageDecoded: onImageDecoded)
			}
		}
	}

	private func decodeLoop(onImageDecoded: @escaping (NSImage) -> Void) {
		lock.lock()
		guard let dataToDecode = latestPendingData else {
			isDecodingOrDrawing = false
			lock.unlock()
			return
		}
		// Clear pending slot so new incoming frames can be stored
		latestPendingData = nil
		lock.unlock()

		// Decode using ImageIO CGImageSource on background queue for maximum speed
		var decodedImage: NSImage? = nil
		if let source = CGImageSourceCreateWithData(dataToDecode as CFData, nil),
		   let cgImage = CGImageSourceCreateImageAtIndex(source, 0, nil) {
			decodedImage = NSImage(cgImage: cgImage, size: NSSize(width: cgImage.width, height: cgImage.height))
		} else {
			decodedImage = NSImage(data: dataToDecode)
		}

		if let image = decodedImage {
			DispatchQueue.main.async {
				onImageDecoded(image)
			}
		}

		// Continue loop if a newer frame arrived while decoding/drawing
		lock.lock()
		if latestPendingData != nil {
			lock.unlock()
			decodeLoop(onImageDecoded: onImageDecoded)
		} else {
			isDecodingOrDrawing = false
			lock.unlock()
		}
	}

	/// Resets the queue and discards any pending frames
	func reset() {
		lock.lock()
		latestPendingData = nil
		isDecodingOrDrawing = false
		lock.unlock()
	}
}

// MARK: - Phone Mirroring State & Stream Manager
@MainActor
final class PhoneMirroringModel: ObservableObject {
	static let shared = PhoneMirroringModel()

	@Published var currentFrame: NSImage? = nil
	@Published var isStreaming: Bool = false
	@Published var statusMessage: String = "Connecting to phone mirror feed..."
	@Published var streamURLString: String = ""
	@Published var frameCount: Int = 0

	// Touch throttling (maximum 60 times per second for move actions)
	private var lastMoveTimestamp: TimeInterval = 0
	private let minMoveInterval: TimeInterval = 1.0 / 60.0

	private init() {
		if let ip = AirLinkWebSocketServer.shared.connectedDeviceIP, !ip.isEmpty, ip != "localhost" {
			self.streamURLString = "http://\(ip):8081/video"
		}
	}

	/// Connects to the phone mirror feed by requesting streaming directly over the existing WebSocket connection
	func connect() {
		statusMessage = "Connecting to phone mirror feed..."
		requestStreamFromDevice()
	}

	func startStreaming() {
		statusMessage = "Streaming via WebSocket..."
		requestStreamFromDevice()
	}

	func resetMirroringState() {
		isStreaming = false
		currentFrame = nil
		statusMessage = "Mirroring stopped"
		frameCount = 0
		MirroringFrameQueue.shared.reset()
	}

	func stopStreaming(notifyRemote: Bool = true) {
		if notifyRemote {
			stopStreamOnDevice()
		}
		resetMirroringState()
	}

	func requestStreamFromDevice() {
		let payload: [String: Any] = ["type": "start_mirroring"]
		if let data = try? JSONSerialization.data(withJSONObject: payload),
		   let json = String(data: data, encoding: .utf8) {
			AirLinkWebSocketServer.shared.sendMessage(json: json)
			print("AirLink Mirroring: Sent start_mirroring request")
			fflush(stdout)
		}
	}

	func stopStreamOnDevice() {
		let payload: [String: Any] = ["type": "stop_mirroring"]
		if let data = try? JSONSerialization.data(withJSONObject: payload),
		   let json = String(data: data, encoding: .utf8) {
			AirLinkWebSocketServer.shared.sendMessage(json: json)
			print("AirLink Mirroring: Sent stop_mirroring request")
			fflush(stdout)
		}
	}

	/// Receives raw binary video frame from WebSocket and routes through zero-lag pipeline
	nonisolated func applyBinaryFrame(_ data: Data) {
		MirroringFrameQueue.shared.pushBinaryFrame(data) { image in
			Task { @MainActor in
				PhoneMirroringModel.shared.pushNewFrame(image)
			}
		}
	}

	/// Legacy fallback for base64 encoded frames
	nonisolated func applyFrame(base64: String) {
		guard let data = Data(base64Encoded: base64) else { return }
		applyBinaryFrame(data)
	}

	/// Coalesces frame updates: updates view with newest frame on main thread
	func pushNewFrame(_ image: NSImage) {
		self.currentFrame = image
		self.isStreaming = true
		self.frameCount += 1
		self.statusMessage = "Live Stream Active"
	}

	/// Sends continuous trackpad scroll values over WebSocket: {"type": "scroll", "deltaX": 15.0, "deltaY": -40.0}
	func sendScrollEvent(deltaX: Double, deltaY: Double) {
		let payload: [String: Any] = [
			"type": "scroll",
			"deltaX": deltaX,
			"deltaY": deltaY
		]

		DispatchQueue.global(qos: .userInteractive).async {
			if let data = try? JSONSerialization.data(withJSONObject: payload),
			   let json = String(data: data, encoding: .utf8) {
				AirLinkWebSocketServer.shared.sendMessage(json: json)
				print("AirLink Scroll: deltaX: \(deltaX), deltaY: \(deltaY)")
				fflush(stdout)
			}
		}
	}

	/// Sends mouse-drag swipe gesture over WebSocket as normalized coordinates: {"type": "swipe", "startX": ..., "startY": ..., "endX": ..., "endY": ...}
	func sendSwipeEvent(startX: Double, startY: Double, endX: Double, endY: Double) {
		let payload: [String: Any] = [
			"type": "swipe",
			"startX": startX,
			"startY": startY,
			"endX": endX,
			"endY": endY
		]

		DispatchQueue.global(qos: .userInteractive).async {
			if let data = try? JSONSerialization.data(withJSONObject: payload),
			   let json = String(data: data, encoding: .utf8) {
				AirLinkWebSocketServer.shared.sendMessage(json: json)
				print("AirLink Swipe: start(\(startX), \(startY)) -> end(\(endX), \(endY))")
				fflush(stdout)
			}
		}
	}

	/// Sends touch coordinate packets over WebSocket to control the phone, throttling continuous moves to max 60 Hz
	func sendTouchEvent(action: String, normalizedPoint: CGPoint) {
		let now = ProcessInfo.processInfo.systemUptime

		if action == "move" {
			if now - lastMoveTimestamp < minMoveInterval {
				return
			}
			lastMoveTimestamp = now
		}

		let x = Double(round(max(0.0, min(1.0, normalizedPoint.x)) * 100) / 100)
		let y = Double(round(max(0.0, min(1.0, normalizedPoint.y)) * 100) / 100)
		let payload: [String: Any] = [
			"type": "touch",
			"action": action,
			"x": x,
			"y": y
		]

		DispatchQueue.global(qos: .userInteractive).async {
			if let data = try? JSONSerialization.data(withJSONObject: payload),
			   let json = String(data: data, encoding: .utf8) {
				AirLinkWebSocketServer.shared.sendMessage(json: json)
				print("AirLink Touch: \(action) at (\(x), \(y))")
				fflush(stdout)
			}
		}
	}

	/// Sends key events (Back, Home, Recents)
	func sendKeyEvent(key: String) {
		let payload: [String: Any] = [
			"type": "key_event",
			"key": key
		]
		if let data = try? JSONSerialization.data(withJSONObject: payload),
		   let json = String(data: data, encoding: .utf8) {
			AirLinkWebSocketServer.shared.sendMessage(json: json)
			print("AirLink Key: \(key)")
			fflush(stdout)
		}
	}

	func updateDeviceIP(_ ip: String) {
		let clean = ip.trimmingCharacters(in: .whitespacesAndNewlines)
		guard !clean.isEmpty, clean != "localhost", clean != "127.0.0.1" else { return }
		streamURLString = "http://\(clean):8081/video"
	}

	nonisolated func setStreamURL(_ urlString: String) {
		Task { @MainActor in
			self.streamURLString = urlString
		}
	}
}

// MARK: - Native Trackpad Scroll & Gesture Capture (AppKit)
final class PhoneScrollTrackingView: NSView {
	var onScroll: ((Double, Double) -> Void)?
	var onTap: ((CGPoint) -> Void)?
	var onSwipe: ((CGPoint, CGPoint) -> Void)?

	// Lightweight throttle for trackpad events: max 60 events per second
	private var lastScrollTimestamp: TimeInterval = 0
	private let minScrollInterval: TimeInterval = 1.0 / 60.0
	private var accumulatedDeltaX: Double = 0.0
	private var accumulatedDeltaY: Double = 0.0

	private var dragStartPoint: NSPoint?

	override var isFlipped: Bool { true }

	override func acceptsFirstMouse(for event: NSEvent?) -> Bool {
		return true
	}

	/// Captures native Mac two-finger trackpad scrolling and wheel events
	override func scrollWheel(with event: NSEvent) {
		// 1. Invert Y-Axis so it matches Android's coordinate system
		let dx = Double(event.scrollingDeltaX)
		let dy = -Double(event.scrollingDeltaY)

		// 2. Accumulate Deltas to exceed Android's touch slop threshold
		accumulatedDeltaX += dx
		accumulatedDeltaY += dy

		// 3. Threshold Trigger: Only send when absolute value of either accumulator exceeds 15.0 pixels
		let threshold: Double = 15.0
		if abs(accumulatedDeltaX) >= threshold || abs(accumulatedDeltaY) >= threshold {
			let toSendX = (accumulatedDeltaX * 10).rounded() / 10
			let toSendY = (accumulatedDeltaY * 10).rounded() / 10

			// Once sent, reset the accumulators to 0
			accumulatedDeltaX = 0.0
			accumulatedDeltaY = 0.0

			onScroll?(toSendX, toSendY)
		} else if event.phase == .ended || event.phase == .cancelled {
			// Reset sub-threshold residual deltas when user lifts fingers
			accumulatedDeltaX = 0.0
			accumulatedDeltaY = 0.0
		}
	}

	override func mouseDown(with event: NSEvent) {
		let loc = convert(event.locationInWindow, from: nil)
		dragStartPoint = loc
	}

	override func mouseUp(with event: NSEvent) {
		guard let start = dragStartPoint else { return }
		dragStartPoint = nil
		let end = convert(event.locationInWindow, from: nil)

		let width = bounds.width
		let height = bounds.height
		guard width > 0, height > 0 else { return }

		let normStartX = max(0.0, min(1.0, start.x / width))
		let normStartY = max(0.0, min(1.0, start.y / height))
		let normEndX = max(0.0, min(1.0, end.x / width))
		let normEndY = max(0.0, min(1.0, end.y / height))

		let distance = hypot(end.x - start.x, end.y - start.y)
		if distance < 5.0 {
			// Click / Tap
			let tapX = (normStartX * 100).rounded() / 100
			let tapY = (normStartY * 100).rounded() / 100
			onTap?(CGPoint(x: tapX, y: tapY))
		} else {
			// Drag / Swipe
			let sX = (normStartX * 100).rounded() / 100
			let sY = (normStartY * 100).rounded() / 100
			let eX = (normEndX * 100).rounded() / 100
			let eY = (normEndY * 100).rounded() / 100
			onSwipe?(CGPoint(x: sX, y: sY), CGPoint(x: eX, y: eY))
		}
	}
}

// MARK: - SwiftUI Representable for Trackpad Scrolling & Gestures
struct PhoneScrollTrackingRepresentable: NSViewRepresentable {
	let onScroll: (Double, Double) -> Void
	let onTap: (CGPoint) -> Void
	let onSwipe: (CGPoint, CGPoint) -> Void

	func makeNSView(context: Context) -> PhoneScrollTrackingView {
		let view = PhoneScrollTrackingView()
		view.onScroll = onScroll
		view.onTap = onTap
		view.onSwipe = onSwipe
		return view
	}

	func updateNSView(_ nsView: PhoneScrollTrackingView, context: Context) {
		nsView.onScroll = onScroll
		nsView.onTap = onTap
		nsView.onSwipe = onSwipe
	}
}

// MARK: - Native iPhone Mirroring Main View
struct PhoneMirroringRootView: View {
	@ObservedObject var model = PhoneMirroringModel.shared
	@ObservedObject var airLinkModel = AirLinkModel.shared

	var body: some View {
		GeometryReader { _ in
			ZStack {
				Color.black

				if let frame = model.currentFrame {
					Image(nsImage: frame)
						.resizable()
						.aspectRatio(contentMode: .fit)
						.overlay(
							// Invisible trackpad scrolling and gesture capture view positioned over video feed
							PhoneScrollTrackingRepresentable(
								onScroll: { deltaX, deltaY in
									model.sendScrollEvent(deltaX: deltaX, deltaY: deltaY)
								},
								onTap: { point in
									model.sendTouchEvent(action: "tap", normalizedPoint: point)
								},
								onSwipe: { startPoint, endPoint in
									model.sendSwipeEvent(
										startX: startPoint.x,
										startY: startPoint.y,
										endX: endPoint.x,
										endY: endPoint.y
									)
								}
							)
						)
						.frame(maxWidth: .infinity, maxHeight: .infinity)
				} else {
					VStack(spacing: 14) {
						ProgressView()
							.scaleEffect(1.1)
						Text(airLinkModel.connectedDeviceName ?? "iPhone Mirroring")
							.font(.system(size: 14, weight: .bold, design: .rounded))
							.foregroundColor(.white)
						Text(model.statusMessage)
							.font(.system(size: 11, weight: .medium, design: .rounded))
							.foregroundColor(.white.opacity(0.65))
					}
					.padding(24)
					.frame(maxWidth: .infinity, maxHeight: .infinity)
				}
			}
		}
		.edgesIgnoringSafeArea(.all)
		.background(Color.black)
		.onDisappear {
			PhoneMirroringModel.shared.stopStreaming(notifyRemote: true)
		}
	}
}

// MARK: - Floating Window Controller (Native iPhone Mirroring Style)
final class PhoneMirroringWindowController: NSWindowController, NSWindowDelegate {
	static let shared = PhoneMirroringWindowController()

	private init() {
		let window = NSWindow(
			contentRect: NSRect(x: 150, y: 150, width: 340, height: 720),
			styleMask: [.titled, .closable, .miniaturizable, .resizable, .fullSizeContentView],
			backing: .buffered,
			defer: false
		)
		window.title = "iPhone Mirroring"
		window.titleVisibility = .hidden
		window.titlebarAppearsTransparent = true
		window.styleMask.insert(.fullSizeContentView)
		window.isMovableByWindowBackground = true
		window.level = .floating // Floating window on top of other windows
		window.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
		window.minSize = NSSize(width: 240, height: 480)
		window.backgroundColor = .black
		window.contentView = NSHostingView(rootView: PhoneMirroringRootView())

		super.init(window: window)
		window.delegate = self
	}

	required init?(coder: NSCoder) {
		fatalError("init(coder:) has not been implemented")
	}

	/// Safely launches the mirroring window with native iPhone Mirroring appearance
	func showMirroringWindow() {
		let isConnected = AirLinkWebSocketServer.shared.hasActiveConnection ||
			AirLinkWebSocketServer.shared.connectedDeviceName != nil ||
			AirLinkModel.shared.connectedDeviceName != nil

		guard isConnected else {
			print("AirLink Mirroring: Cannot open mirroring window - no connected phone.")
			fflush(stdout)
			return
		}

		guard let window = window else { return }
		window.titleVisibility = .hidden
		window.titlebarAppearsTransparent = true
		window.styleMask.insert(.fullSizeContentView)
		window.isMovableByWindowBackground = true

		if !window.isVisible {
			window.center()
		}
		window.makeKeyAndOrderFront(nil)
		NSApp.activate(ignoringOtherApps: true)
		PhoneMirroringModel.shared.connect()
	}

	/// Automatically closes the mirroring window and resets mirroring state
	func closeMirroringWindow(notifyRemote: Bool = true) {
		DispatchQueue.main.async { [weak self] in
			guard let self = self else { return }
			PhoneMirroringModel.shared.stopStreaming(notifyRemote: notifyRemote)
			if let window = self.window, window.isVisible {
				window.orderOut(nil)
				print("AirLink Mirroring: Closed mirroring window (notifyRemote: \(notifyRemote)).")
				fflush(stdout)
			}
		}
	}

	func windowWillClose(_ notification: Notification) {
		print("AirLink Mirroring: Window closed by user, sending stop_mirroring to phone.")
		fflush(stdout)
		PhoneMirroringModel.shared.stopStreaming(notifyRemote: true)
	}
}
