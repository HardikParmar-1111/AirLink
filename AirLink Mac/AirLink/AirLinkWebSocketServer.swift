//
//  AirLinkWebSocketServer.swift
//  AirLink
//
//  Local WebSocket listener advertised over Bonjour as _airlink._tcp.
//  Powers Phase 2 (Universal Clipboard) and Notch Drop bidirectional sync.
//

import AppKit
import Foundation
import Network
import UserNotifications
import CryptoKit
import AirLinkShare

final class AirLinkWebSocketServer: NSObject, NetServiceDelegate {
	static let shared = AirLinkWebSocketServer()

	static let bonjourType = "_airlink._tcp."
	static let defaultPort: UInt16 = 8080

	private var listener: NWListener?
	private var bonjourService: NetService?
	private var connections: [ObjectIdentifier: NWConnection] = [:]
	/// Most recently ready Android WebSocket. Dropped files go here directly.
	private var activeConnection: NWConnection?
	public var hasActiveConnection: Bool { activeConnection != nil }
	public private(set) var connectedDeviceName: String? = nil
	public private(set) var connectedDeviceIP: String? = nil
	private let queue = DispatchQueue(label: "com.airlink.websocket", qos: .userInteractive)

	private override init() {
		super.init()
	}

	func start(port: UInt16 = AirLinkWebSocketServer.defaultPort) {
		guard listener == nil else { return }

		do {
			let parameters = NWParameters.tcp
			parameters.allowLocalEndpointReuse = true
			parameters.includePeerToPeer = true
			let webSocketOptions = NWProtocolWebSocket.Options()
			webSocketOptions.autoReplyPing = true
			parameters.defaultProtocolStack.applicationProtocols.insert(webSocketOptions, at: 0)

			guard let nwPort = NWEndpoint.Port(rawValue: port) else {
				print("AirLink WebSocket: invalid port \(port)")
				return
			}

			let listener = try NWListener(using: parameters, on: nwPort)
			self.listener = listener

			listener.stateUpdateHandler = { [weak self] state in
				switch state {
				case .ready:
					let boundPort = listener.port?.rawValue ?? port
					print("AirLink WebSocket: listening on port \(boundPort)")
					self?.publishBonjour(port: Int32(boundPort))
				case .failed(let error):
					print("AirLink WebSocket: listener failed: \(error)")
				case .cancelled:
					print("AirLink WebSocket: listener cancelled")
				default:
					break
				}
			}

			listener.newConnectionHandler = { [weak self] connection in
				self?.handleNewConnection(connection)
			}

			listener.start(queue: queue)
		} catch {
			print("AirLink WebSocket: unable to start listener \(error)")
		}
	}

	func stop() {
		DispatchQueue.main.async { [weak self] in
			self?.bonjourService?.stop()
			self?.bonjourService = nil
		}
		connections.values.forEach { $0.cancel() }
		connections.removeAll()
		activeConnection = nil
		connectedDeviceName = nil
		connectedDeviceIP = nil
		AirLinkSecurityManager.shared.clearActiveKey()
		DispatchQueue.main.async {
			AirLinkModel.shared.connectedDeviceName = nil
			AirLinkModel.shared.connectedDeviceIP = nil
			AirLinkModel.shared.batteryLevel = nil
			AirLinkModel.shared.isBatteryCharging = false
			AirLinkModel.shared.activeTransferStatus = "Disconnected"
			AirLinkModel.shared.isScanning = true
			AppDelegate.shared?.updateConnectedDevice(name: nil)
			PhoneMirroringWindowController.shared.closeMirroringWindow(notifyRemote: false)
		}
		listener?.cancel()
		listener = nil
	}

	private func publishBonjour(port: Int32 = 8080) {
		DispatchQueue.main.async { [weak self] in
			guard let self = self else { return }
			self.bonjourService?.stop()
			let hostName = Host.current().localizedName ?? "AirLink Mac"
			let service = NetService(domain: "local.", type: Self.bonjourType, name: hostName, port: port)
			service.delegate = self
			service.includesPeerToPeer = true
			service.schedule(in: .main, forMode: .common)
			service.publish()
			self.bonjourService = service
			print("AirLink WebSocket: advertising Bonjour service \(Self.bonjourType) as \"\(hostName)\" on port \(port)")
			fflush(stdout)
		}
	}

	// MARK: - Connection Lifecycle & Crash Prevention
	private func handleNewConnection(_ connection: NWConnection) {
		let id = ObjectIdentifier(connection)
		connections[id] = connection

		connection.stateUpdateHandler = { [weak self, weak connection] state in
			guard let self, let connection else { return }
			switch state {
			case .ready:
				self.activeConnection = connection
				let clientIP = self.extractIP(from: connection)
				if let clientIP, !clientIP.isEmpty {
					self.connectedDeviceIP = clientIP
					print("AirLink: Client Connected from IP: \(clientIP)")
					DispatchQueue.main.async {
						AirLinkModel.shared.connectedDeviceIP = clientIP
						PhoneMirroringModel.shared.updateDeviceIP(clientIP)
					}
				} else {
					print("AirLink: Client Connected")
				}
				fflush(stdout)
			case .failed(let error):
				print("AirLink WebSocket connection failed: \(error)")
				self.handleDisconnect(for: connection, reason: "Connection failed: \(error.localizedDescription)")
			case .cancelled:
				self.handleDisconnect(for: connection, reason: "Connection cancelled")
			case .waiting(let error):
				print("AirLink WebSocket connection waiting: \(error)")
			default:
				break
			}
		}

		connection.start(queue: queue)
		receive(on: connection)
	}

	private func handleDisconnect(for connection: NWConnection, reason: String) {
		let id = ObjectIdentifier(connection)
		if connections.removeValue(forKey: id) != nil {
			print("AirLink: Client Disconnected (\(reason))")
			fflush(stdout)
			connection.cancel()
		}
		if activeConnection === connection {
			activeConnection = connections.values.first { conn in
				if case .ready = conn.state { return true }
				return false
			}
		}
		if connections.isEmpty || activeConnection == nil {
			handleAllClientsDisconnected()
		}
	}

	private func handleAllClientsDisconnected() {
		connectedDeviceName = nil
		connectedDeviceIP = nil
		AirLinkSecurityManager.shared.clearActiveKey()

		DispatchQueue.main.async {
			// 1. Close mirroring window safely without crashing
			PhoneMirroringWindowController.shared.closeMirroringWindow(notifyRemote: false)

			// 2. Reset all UI state variables to "Disconnected"
			AirLinkModel.shared.connectedDeviceName = nil
			AirLinkModel.shared.connectedDeviceIP = nil
			AirLinkModel.shared.batteryLevel = nil
			AirLinkModel.shared.isBatteryCharging = false
			AirLinkModel.shared.activeTransferStatus = "Disconnected"
			AirLinkModel.shared.isScanning = true
			AppDelegate.shared?.updateConnectedDevice(name: nil)

			// 3. Restart mDNS device discovery to discover available devices
			AirLinkConnectionManager.shared.stopDeviceDiscovery()
			AirLinkConnectionManager.shared.startDeviceDiscovery()

			print("AirLink: UI state reset to Disconnected, restarted mDNS device discovery.")
			fflush(stdout)
		}
	}

	// MARK: - Receiving Data (Android -> Mac)
	private func receive(on connection: NWConnection) {
		connection.receiveMessage { [weak self, weak connection] data, context, _, error in
			guard let self, let connection else { return }

			if let error {
				print("AirLink WebSocket receive error: \(error)")
				self.handleDisconnect(for: connection, reason: "Receive error: \(error.localizedDescription)")
				return
			}

			if let metadata = context?.protocolMetadata(definition: NWProtocolWebSocket.definition) as? NWProtocolWebSocket.Metadata,
			   metadata.opcode == .close {
				self.handleDisconnect(for: connection, reason: "Client closed WebSocket")
				return
			}

			if let data, !data.isEmpty {
				self.processIncomingData(data, context: context, from: connection)
			}

			self.receive(on: connection)
		}
	}

	/// Decrypts incoming AES-256-GCM payloads (video frames or JSON) and routes accordingly
	private func processIncomingData(_ data: Data, context: NWConnection.ContentContext?, from connection: NWConnection) {
		let metadata = context?.protocolMetadata(definition: NWProtocolWebSocket.definition) as? NWProtocolWebSocket.Metadata
		let isBinaryOpcode = (metadata?.opcode == .binary)

		// 1. Attempt AES-256-GCM decryption if payload is encrypted binary
		var effectiveData = data
		if let decrypted = AirLinkSecurityManager.shared.decrypt(combinedData: data) {
			effectiveData = decrypted
		}

		// 2. Check if decrypted or plain data is a video image frame
		let hasImageHeader = hasImageMagicHeader(effectiveData)
		if isBinaryOpcode || hasImageHeader {
			PhoneMirroringModel.shared.applyBinaryFrame(effectiveData)
			return
		}

		// 3. Otherwise parse as JSON text
		guard let jsonString = String(data: effectiveData, encoding: .utf8) ?? String(data: effectiveData, encoding: .isoLatin1),
		      !jsonString.isEmpty else {
			return
		}

		let preview = jsonString.count > 100 ? String(jsonString.prefix(100)) + "..." : jsonString
		print("Raw Received: \(preview)")
		fflush(stdout)
		routeIncomingMessage(jsonString: jsonString, from: connection)
	}

	private func hasImageMagicHeader(_ data: Data) -> Bool {
		guard data.count >= 4 else { return false }
		// JPEG: 0xFF, 0xD8
		if data[0] == 0xFF && data[1] == 0xD8 { return true }
		// PNG: 0x89, 'P', 'N', 'G' (0x89, 0x50, 0x4E, 0x47)
		if data[0] == 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47 { return true }
		// WebP: RIFF (0x52, 0x49, 0x46, 0x46)
		if data[0] == 0x52 && data[1] == 0x49 && data[2] == 0x46 && data[3] == 0x46 { return true }
		return false
	}

	func extractIP(from connection: NWConnection) -> String? {
		let endpoint = connection.currentPath?.remoteEndpoint ?? connection.endpoint
		switch endpoint {
		case .hostPort(let host, _):
			let hostStr: String
			switch host {
			case .ipv4(let addr):
				hostStr = "\(addr)"
			case .ipv6(let addr):
				hostStr = "\(addr)"
			case .name(let name, _):
				hostStr = name
			@unknown default:
				hostStr = "\(host)"
			}
			let clean = hostStr.components(separatedBy: "%").first?.trimmingCharacters(in: .whitespacesAndNewlines) ?? hostStr
			return clean.isEmpty ? nil : clean
		default:
			let endpointStr = "\(endpoint)"
			if let first = endpointStr.components(separatedBy: ":").first?.trimmingCharacters(in: .whitespacesAndNewlines), !first.isEmpty {
				let clean = first.components(separatedBy: "%").first?.trimmingCharacters(in: .whitespacesAndNewlines) ?? first
				return clean.isEmpty ? nil : clean
			}
			return nil
		}
	}

	private func parseAndApplyBattery(from json: [String: Any]) {
		let rawLevel = json["level"]
			?? json["battery"]
			?? json["batteryLevel"]
			?? json["battery_level"]
			?? json["percentage"]
			?? json["percent"]
			?? json["data"]

		let level: Int? = {
			if let intVal = rawLevel as? Int { return intVal }
			if let doubleVal = rawLevel as? Double { return Int(doubleVal) }
			if let numVal = rawLevel as? NSNumber { return numVal.intValue }
			if let strVal = rawLevel as? String {
				let cleaned = strVal.replacingOccurrences(of: "%", with: "").trimmingCharacters(in: .whitespacesAndNewlines)
				return Int(cleaned)
			}
			return nil
		}()

		let isCharging: Bool = {
			if let boolVal = (json["isCharging"] as? Bool) ?? (json["charging"] as? Bool) ?? (json["is_charging"] as? Bool) {
				return boolVal
			}
			if let numVal = (json["isCharging"] as? NSNumber) ?? (json["charging"] as? NSNumber) ?? (json["is_charging"] as? NSNumber) {
				return numVal.boolValue
			}
			if let strVal = (json["isCharging"] as? String) ?? (json["charging"] as? String) ?? (json["is_charging"] as? String) {
				let lower = strVal.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
				return lower == "true" || lower == "1" || lower == "yes"
			}
			return false
		}()

		if let level = level {
			let clamped = max(0, min(100, level))
			print("AirLink Battery: Parsed level: \(clamped)%, isCharging: \(isCharging)")
			fflush(stdout)
			DispatchQueue.main.async {
				AirLinkModel.shared.batteryLevel = clamped
				AirLinkModel.shared.isBatteryCharging = isCharging
			}
		}
	}

	private func handleIncomingNotification(from json: [String: Any]) {
		let nested = (json["notification"] as? [String: Any])
			?? (json["data"] as? [String: Any])
			?? json

		let appName = (nested["appName"] as? String)
			?? (nested["app_name"] as? String)
			?? (nested["app"] as? String)
			?? (json["appName"] as? String)
			?? (json["app_name"] as? String)

		let rawTitle = (nested["title"] as? String)
			?? (nested["header"] as? String)
			?? (json["title"] as? String)
			?? appName
			?? (self.connectedDeviceName ?? "Android Notification")

		let rawBody = (nested["text"] as? String)
			?? (nested["body"] as? String)
			?? (nested["message"] as? String)
			?? (nested["content"] as? String)
			?? (nested["data"] as? String)
			?? (json["text"] as? String)
			?? (json["body"] as? String)
			?? (json["message"] as? String)
			?? (json["data"] as? String)
			?? ""

		let subTitle: String? = {
			if let app = appName, !app.isEmpty, app != rawTitle {
				return app
			}
			if let device = self.connectedDeviceName, !device.isEmpty {
				return "from \(device)"
			}
			return nil
		}()

		print("AirLink Notification: Incoming '\(rawTitle)' (\(subTitle ?? "")) - '\(rawBody)'")
		fflush(stdout)

		showSystemNotification(title: rawTitle, subtitle: subTitle, body: rawBody)

		DispatchQueue.main.async {
			AirLinkNotchDropManager.shared.triggerReceivedFeedback(
				title: rawTitle,
				subtitle: rawBody.isEmpty ? (subTitle ?? "Notification") : rawBody
			)
			AppDelegate.shared?.flashMenuBarIcon()
		}
	}

	func showSystemNotification(title: String, subtitle: String? = nil, body: String) {
		let content = UNMutableNotificationContent()
		content.title = title.isEmpty ? (self.connectedDeviceName ?? "Phone Notification") : title
		if let subtitle, !subtitle.isEmpty {
			content.subtitle = subtitle
		}
		content.body = body
		content.sound = .default

		let id = "android_notif_\(UUID().uuidString)"
		let request = UNNotificationRequest(identifier: id, content: content, trigger: nil)

		UNUserNotificationCenter.current().add(request) { error in
			if let error = error {
				print("AirLink Notification: Failed to post system notification: \(error)")
			} else {
				print("AirLink Notification: Successfully posted system notification: '\(title)'")
			}
			fflush(stdout)
		}
	}

	private func routeIncomingMessage(jsonString: String, from connection: NWConnection) {
		let trimmed = jsonString.trimmingCharacters(in: .whitespacesAndNewlines)
		guard let data = trimmed.data(using: .utf8),
		      let json = (try? JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])) as? [String: Any] else {
			print("AirLink Router: invalid JSON structure (not a dictionary): \(trimmed)")
			return
		}

		// Dynamic IP capture from incoming payload or active connection
		let payloadIP = (json["ip"] as? String)
			?? (json["deviceIp"] as? String)
			?? (json["device_ip"] as? String)
			?? (json["host"] as? String)
		let resolvedIP = payloadIP?.trimmingCharacters(in: .whitespacesAndNewlines) ?? extractIP(from: connection)
		if let resolvedIP, !resolvedIP.isEmpty, resolvedIP != "localhost" {
			self.connectedDeviceIP = resolvedIP
			DispatchQueue.main.async {
				AirLinkModel.shared.connectedDeviceIP = resolvedIP
				PhoneMirroringModel.shared.updateDeviceIP(resolvedIP)
			}
		}

		// Battery status capture if present in payload
		if json["battery"] != nil || json["batteryLevel"] != nil || json["battery_level"] != nil || json["level"] != nil {
			parseAndApplyBattery(from: json)
		}

		let rawType = (json["type"] as? String) ?? (json["action"] as? String) ?? ""
		let type = rawType.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()

		// Decrypt AES-256-GCM envelope if received as {"type": "encrypted", "payload": "<base64>"}
		if type == "encrypted" {
			let rawPayload = (json["payload"] as? String) ?? (json["data"] as? String)
			if let rawPayload,
			   let cipherData = Data(base64Encoded: rawPayload),
			   let decryptedData = AirLinkSecurityManager.shared.decrypt(combinedData: cipherData),
			   let decryptedString = String(data: decryptedData, encoding: .utf8) {
				routeIncomingMessage(jsonString: decryptedString, from: connection)
				return
			} else {
				print("AirLink Security: Failed to decrypt incoming JSON envelope")
				return
			}
		}

		// 1. First-time device pairing flow with AES-256-GCM key generation & exchange
		if type == "pairing_request" || type == "pairing" {
			let deviceName = (json["deviceName"] as? String)
				?? (json["device_name"] as? String)
				?? "Android Device"
			let deviceId = (json["deviceId"] as? String)
				?? (json["device_id"] as? String)
				?? deviceName

			print("AirLink Pairing: Received pairing_request from '\(deviceName)' (id: \(deviceId))")
			fflush(stdout)

			// Generate or retrieve persistent 256-bit AES shared key
			let keyResult = AirLinkSecurityManager.shared.getOrCreateKey(for: deviceId)

			// Step 2: Check Known Devices in UserDefaults
			if AirLinkPairingManager.shared.isDeviceTrusted(id: deviceId) {
				print("AirLink Pairing: Device '\(deviceName)' (\(deviceId)) is trusted. Auto-accepting with AES-256-GCM.")
				fflush(stdout)
				self.connectedDeviceName = deviceName
				DispatchQueue.main.async {
					AirLinkModel.shared.connectedDeviceName = deviceName
					AppDelegate.shared?.updateConnectedDevice(name: deviceName)
				}
				self.sendPairingResponse(accepted: true, to: connection, sharedKeyBase64: keyResult.base64)
				return
			}

			// Step 3: Prompt User via Notch UI
			print("AirLink Pairing: Unknown device '\(deviceName)' (\(deviceId)). Requesting Notch approval.")
			fflush(stdout)
			AirLinkNotchDropManager.shared.showPairingPrompt(
				deviceName: deviceName,
				deviceId: deviceId,
				onAccept: { [weak self, weak connection] in
					guard let self = self else { return }
					print("AirLink Pairing: User accepted '\(deviceName)' (id: \(deviceId))")
					fflush(stdout)
					AirLinkPairingManager.shared.trustDevice(id: deviceId)
					self.connectedDeviceName = deviceName
					DispatchQueue.main.async {
						AirLinkModel.shared.connectedDeviceName = deviceName
						AppDelegate.shared?.updateConnectedDevice(name: deviceName)
					}
					if let connection = connection {
						self.sendPairingResponse(accepted: true, to: connection, sharedKeyBase64: keyResult.base64)
					}
				},
				onDecline: { [weak self, weak connection] in
					guard let self = self else { return }
					print("AirLink Pairing: User declined '\(deviceName)' (id: \(deviceId))")
					fflush(stdout)
					if let connection = connection {
						self.sendPairingResponse(accepted: false, to: connection)
						self.handleDisconnect(for: connection, reason: "Pairing declined by user")
					}
				}
			)
			return
		}

		// Safe dictionary extraction for device connection handshake
		if type == "connection" || type == "connect" || type == "handshake" || json["deviceName"] != nil || json["device_name"] != nil {
			let extractedName = (json["deviceName"] as? String)
				?? (json["device_name"] as? String)
				?? (json["deviceName"] as? CustomStringConvertible)?.description
				?? (json["device_name"] as? CustomStringConvertible)?.description
				?? (json["name"] as? String)
				?? (json["device"] as? String)

			if let deviceName = extractedName?.trimmingCharacters(in: .whitespacesAndNewlines), !deviceName.isEmpty {
				self.connectedDeviceName = deviceName
				print("AirLink Handshake: Connected deviceName: '\(deviceName)'")
				fflush(stdout)
				DispatchQueue.main.async {
					AirLinkModel.shared.connectedDeviceName = deviceName
					AppDelegate.shared?.updateConnectedDevice(name: deviceName)
				}
			} else {
				print("AirLink Router: 'connection' payload missing 'deviceName': \(json)")
			}
			return
		}

		switch type {
		case "clipboard":
			let clipboardText = (json["data"] as? String)
				?? (json["text"] as? String)
				?? (json["content"] as? String)
			if let clipboardText, !clipboardText.isEmpty {
				print("AirLink Router: received clipboard text (\(clipboardText.count) chars)")
				fflush(stdout)
				AirLinkClipboardManager.shared.applyRemoteClipboard(text: clipboardText)
				DispatchQueue.main.async {
					AirLinkNotchDropManager.shared.triggerReceivedFeedback(
						title: "Received from Android",
						subtitle: "Clipboard: \(clipboardText.prefix(30))"
					)
					AppDelegate.shared?.flashMenuBarIcon()
				}
			} else {
				print("AirLink Router: 'clipboard' message missing valid string 'data': \(json)")
			}

		case "file":
			let filename = (json["fileName"] as? String)
				?? (json["filename"] as? String)
				?? "received_file"
			let base64Str = (json["fileData"] as? String)
				?? (json["data"] as? String)
			if let base64Str, let fileData = Data(base64Encoded: base64Str) {
				saveIncomingFile(filename: filename, data: fileData)
				DispatchQueue.main.async {
					AirLinkNotchDropManager.shared.triggerReceivedFeedback(
						title: "Received from Android",
						subtitle: filename
					)
					AppDelegate.shared?.flashMenuBarIcon()
				}
			} else {
				print("AirLink Router: invalid file payload: \(json)")
			}

		case "notification", "android_notification", "remote_notification":
			handleIncomingNotification(from: json)

		case "battery", "battery_status", "battery_info":
			parseAndApplyBattery(from: json)

		case "frame", "video_frame", "screen_frame", "mirror_frame":
			let frameData = (json["data"] as? String)
				?? (json["frame"] as? String)
				?? (json["image"] as? String)
			if let frameData, !frameData.isEmpty {
				DispatchQueue.global(qos: .userInteractive).async {
					PhoneMirroringModel.shared.applyFrame(base64: frameData)
				}
			}

		case "mirror_stream", "stream_info", "video_stream":
			if let urlStr = (json["url"] as? String) ?? (json["streamUrl"] as? String) {
				print("AirLink Router: Received video stream URL: \(urlStr)")
				DispatchQueue.main.async {
					PhoneMirroringModel.shared.setStreamURL(urlStr)
				}
			} else if let port = (json["port"] as? Int) ?? (json["port"] as? String).flatMap(Int.init),
			          let ip = self.connectedDeviceIP ?? extractIP(from: connection) {
				let constructedURL = "http://\(ip):\(port)/video"
				print("AirLink Router: Constructed stream URL from port: \(constructedURL)")
				DispatchQueue.main.async {
					PhoneMirroringModel.shared.setStreamURL(constructedURL)
				}
			}

		case "stop_mirroring", "stop_mirror":
			print("AirLink Router: Received stop_mirroring from phone, closing mirror window and resetting state")
			fflush(stdout)
			DispatchQueue.main.async {
				PhoneMirroringWindowController.shared.closeMirroringWindow(notifyRemote: false)
			}

		case "start_mirroring", "start_mirror":
			print("AirLink Router: Received start_mirroring from phone, opening mirror window")
			fflush(stdout)
			DispatchQueue.main.async {
				PhoneMirroringWindowController.shared.showMirroringWindow()
			}

		default:
			print("AirLink Router: unknown message type '\(type)' in JSON: \(json)")
		}
	}

	private func saveIncomingFile(filename: String, data: Data) {
		DispatchQueue.global(qos: .userInitiated).async {
			let downloadsURL = FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask).first ?? URL(fileURLWithPath: NSHomeDirectory() + "/Downloads")
			var destinationURL = downloadsURL.appendingPathComponent(filename)

			// Avoid collision
			var counter = 1
			let stem = (filename as NSString).deletingPathExtension
			let ext = (filename as NSString).pathExtension
			while FileManager.default.fileExists(atPath: destinationURL.path) {
				let newName = ext.isEmpty ? "\(stem)_\(counter)" : "\(stem)_\(counter).\(ext)"
				destinationURL = downloadsURL.appendingPathComponent(newName)
				counter += 1
			}

			do {
				try data.write(to: destinationURL)
				print("AirLink WebSocket: saved file to \(destinationURL.path)")
				DispatchQueue.main.async {
					AirLinkModel.shared.recentTransfers.insert(
						RecentTransferItem(
							id: UUID(),
							title: "Received: \(destinationURL.lastPathComponent)",
							subtitle: "Saved to Downloads",
							timestamp: Date()
						),
						at: 0
					)
				}
			} catch {
				print("AirLink WebSocket: failed to save incoming file: \(error)")
			}
		}
	}

	// MARK: - Sending Data (Mac -> Android) with AES-256-GCM
	func sendMessage(json: String) {
		// Encrypt outgoing JSON payloads with AES-256-GCM if shared key is established
		let finalJSON: String
		if let plainData = json.data(using: .utf8),
		   let encryptedData = AirLinkSecurityManager.shared.encrypt(data: plainData) {
			let envelope: [String: Any] = [
				"type": "encrypted",
				"payload": encryptedData.base64EncodedString()
			]
			if let envelopeData = try? JSONSerialization.data(withJSONObject: envelope),
			   let envelopeStr = String(data: envelopeData, encoding: .utf8) {
				finalJSON = envelopeStr
			} else {
				finalJSON = json
			}
		} else {
			finalJSON = json
		}

		guard let data = finalJSON.data(using: .utf8) else { return }
		let metadata = NWProtocolWebSocket.Metadata(opcode: .text)
		let context = NWConnection.ContentContext(identifier: "textContext", metadata: [metadata])

		queue.async { [weak self] in
			guard let self = self else { return }
			for connection in self.connections.values {
				connection.send(content: data, contentContext: context, isComplete: true, completion: .contentProcessed { [weak self, weak connection] error in
					if let error = error {
						print("AirLink WebSocket send error: \(error)")
						if let connection = connection {
							self?.handleDisconnect(for: connection, reason: "Socket send error: \(error.localizedDescription)")
						}
					}
				})
			}
		}
	}

	/// Sends a remote command to ring the connected Android phone
	func ringPhone() {
		let payload: [String: Any] = ["type": "ring_phone"]
		if let data = try? JSONSerialization.data(withJSONObject: payload),
		   let json = String(data: data, encoding: .utf8) {
			sendMessage(json: json)
			print("AirLink: Dispatched ring_phone command over WebSocket")
			fflush(stdout)
		}
	}

	var hasActiveClient: Bool {
		queue.sync {
			if let activeConnection, case .ready = activeConnection.state {
				return true
			}
			return connections.values.contains { conn in
				if case .ready = conn.state { return true }
				return false
			}
		}
	}

	/// Reads dropped file URLs and sends each one on the active Android `NWConnection`.
	/// Returns true if at least one file was handed to the socket.
	@discardableResult
	func sendDroppedFiles(urls: [URL]) -> Bool {
		guard !urls.isEmpty else { return false }
		guard hasActiveClient else {
			print("AirLink WebSocket: drop ignored — no connected Android client")
			return false
		}
		var sentAny = false
		for url in urls {
			let accessed = url.startAccessingSecurityScopedResource()
			defer {
				if accessed {
					url.stopAccessingSecurityScopedResource()
				}
			}
			do {
				let fileData = try Data(contentsOf: url)
				sendFileToActiveClient(filename: url.lastPathComponent, data: fileData)
				sentAny = true
			} catch {
				print("AirLink WebSocket: failed to read dropped file '\(url.lastPathComponent)': \(error)")
			}
		}
		return sentAny
	}

	/// Encodes a file and writes it immediately to the active WebSocket client as Base64 JSON.
	func sendFileToActiveClient(filename: String, data: Data) {
		let base64String = data.base64EncodedString()
		let payload: [String: Any] = [
			"type": "file",
			"fileName": filename,
			"fileData": base64String,
			"filename": filename,
			"data": base64String
		]
		guard let jsonData = try? JSONSerialization.data(withJSONObject: payload),
		      let jsonString = String(data: jsonData, encoding: .utf8) else {
			print("AirLink WebSocket: failed to encode file payload for '\(filename)'")
			return
		}

		sendMessage(json: jsonString)
	}

	private func readyActiveConnection() -> NWConnection? {
		if let activeConnection, case .ready = activeConnection.state {
			return activeConnection
		}
		let ready = connections.values.first { conn in
			if case .ready = conn.state { return true }
			return false
		}
		activeConnection = ready
		return ready
	}

	func broadcastClipboard(text: String) {
		let payload: [String: Any] = [
			"type": "clipboard",
			"data": text
		]
		if let jsonData = try? JSONSerialization.data(withJSONObject: payload),
		   let jsonString = String(data: jsonData, encoding: .utf8) {
			sendMessage(json: jsonString)
			print("AirLink Sent: Clipboard")
		}
	}

	func broadcastFile(filename: String, data: Data) {
		sendFileToActiveClient(filename: filename, data: data)
	}

	// MARK: - NetServiceDelegate
	func netServiceDidPublish(_ sender: NetService) {
		print("AirLink WebSocket: Bonjour published \(sender.type) as \"\(sender.name)\" on port \(sender.port)")
		fflush(stdout)
	}

	func netService(_ sender: NetService, didNotPublish errorDict: [String: NSNumber]) {
		print("AirLink WebSocket: Bonjour publish failed: \(errorDict)")
		fflush(stdout)
	}

	// MARK: - Pairing Response Sender with AES Key Exchange
	func sendPairingResponse(accepted: Bool, to connection: NWConnection, sharedKeyBase64: String? = nil) {
		let type = accepted ? "pairing_accepted" : "pairing_rejected"
		var payload: [String: Any] = ["type": type]
		if accepted, let key = sharedKeyBase64 {
			payload["key"] = key
			payload["encryption"] = "AES-256-GCM"
		}
		guard let data = try? JSONSerialization.data(withJSONObject: payload) else { return }
		let metadata = NWProtocolWebSocket.Metadata(opcode: .text)
		let context = NWConnection.ContentContext(identifier: "pairingContext", metadata: [metadata])

		connection.send(content: data, contentContext: context, isComplete: true, completion: .contentProcessed { error in
			if let error {
				print("AirLink WebSocket send \(type) error: \(error)")
			} else {
				print("AirLink WebSocket: sent '\(type)' to client (AES-256-GCM key exchanged: \(sharedKeyBase64 != nil))")
				fflush(stdout)
			}
		})
	}

	func netServiceDidStop(_ sender: NetService) {
		print("AirLink WebSocket: Bonjour service stopped")
		fflush(stdout)
	}
}

// MARK: - Pairing & Trusted Devices Manager
final class AirLinkPairingManager {
	static let shared = AirLinkPairingManager()
	private let trustedDevicesKey = "AirLinkTrustedDeviceIds"

	func isDeviceTrusted(id: String) -> Bool {
		let trusted = UserDefaults.standard.stringArray(forKey: trustedDevicesKey) ?? []
		return trusted.contains(id)
	}

	func trustDevice(id: String) {
		var trusted = UserDefaults.standard.stringArray(forKey: trustedDevicesKey) ?? []
		if !trusted.contains(id) {
			trusted.append(id)
			UserDefaults.standard.set(trusted, forKey: trustedDevicesKey)
			print("AirLink Pairing: Saved deviceId '\(id)' to trusted devices in UserDefaults")
			fflush(stdout)
		}
	}

	func removeTrustedDevice(id: String) {
		var trusted = UserDefaults.standard.stringArray(forKey: trustedDevicesKey) ?? []
		trusted.removeAll { $0 == id }
		UserDefaults.standard.set(trusted, forKey: trustedDevicesKey)
	}
}

// MARK: - Mac Clipboard Monitor
final class AirLinkClipboardManager {
	static let shared = AirLinkClipboardManager()

	private var lastChangeCount: Int
	private var lastCopiedText: String = ""
	private var lastSentString: String = ""
	private var timer: Timer?
	private var remoteWrittenChangeCount: Int = -1

	private init() {
		self.lastChangeCount = NSPasteboard.general.changeCount
	}

	func startMonitoring() {
		guard timer == nil else { return }
		DispatchQueue.main.async {
			self.lastChangeCount = NSPasteboard.general.changeCount
			let timer = Timer(timeInterval: 1.0, repeats: true) { [weak self] _ in
				self?.checkPasteboard()
			}
			RunLoop.main.add(timer, forMode: .common)
			self.timer = timer
			print("AirLink Clipboard: monitoring active (1.0s interval)")
		}
	}

	func stopMonitoring() {
		DispatchQueue.main.async {
			self.timer?.invalidate()
			self.timer = nil
			print("AirLink Clipboard: monitoring stopped")
		}
	}

	private func checkPasteboard() {
		let currentCount = NSPasteboard.general.changeCount
		guard currentCount != lastChangeCount else { return }
		lastChangeCount = currentCount

		// Prevent echo loop when receiving remote clipboard payload
		if currentCount == remoteWrittenChangeCount {
			return
		}

		guard let copiedText = NSPasteboard.general.string(forType: .string),
		      !copiedText.isEmpty,
		      copiedText != lastSentString,
		      copiedText != lastCopiedText else {
			return
		}

		lastCopiedText = copiedText
		lastSentString = copiedText

		let payload: [String: Any] = [
			"type": "clipboard",
			"data": copiedText
		]
		if let jsonData = try? JSONSerialization.data(withJSONObject: payload),
		   let jsonString = String(data: jsonData, encoding: .utf8) {
			AirLinkWebSocketServer.shared.sendMessage(json: jsonString)
			print("AirLink Sent: Clipboard (\(copiedText.count) chars)")
			fflush(stdout)
		}
	}

	/// Writes remote clipboard text received from Android to the macOS system clipboard.
	func applyRemoteClipboard(text: String) {
		DispatchQueue.main.async {
			guard !text.isEmpty else { return }
			// Loop prevention: check against last sent or copied string to avoid echo loops
			guard text != self.lastSentString && text != self.lastCopiedText else {
				print("AirLink Clipboard: skipping remote text (already in sync/preventing echo loop)")
				return
			}
			print("AirLink Clipboard: writing remote text to local NSPasteboard (\(text.count) chars)")
			fflush(stdout)

			self.lastCopiedText = text
			self.lastSentString = text

			// Safe NSPasteboard write
			let pasteboard = NSPasteboard.general
			pasteboard.clearContents()
			pasteboard.setString(text, forType: .string)

			self.remoteWrittenChangeCount = pasteboard.changeCount
			self.lastChangeCount = pasteboard.changeCount

			// Show in Recent Transfers list
			let preview = text.prefix(35) + (text.count > 35 ? "..." : "")
			AirLinkModel.shared.recentTransfers.insert(
				RecentTransferItem(
					id: UUID(),
					title: "Universal Clipboard",
					subtitle: "Synced: \"\(preview)\"",
					timestamp: Date()
				),
				at: 0
			)
		}
	}
}

// MARK: - AES-256-GCM Security Manager
final class AirLinkSecurityManager {
	static let shared = AirLinkSecurityManager()

	private(set) var activeSymmetricKey: SymmetricKey?
	private let keyLock = NSLock()

	/// Generates or retrieves a 256-bit symmetric key for a given deviceId
	func getOrCreateKey(for deviceId: String) -> (key: SymmetricKey, base64: String) {
		keyLock.lock()
		defer { keyLock.unlock() }

		let userDefaultsKey = "AirLink_AES_Key_\(deviceId)"
		if let existingBase64 = UserDefaults.standard.string(forKey: userDefaultsKey),
		   let keyData = Data(base64Encoded: existingBase64),
		   keyData.count == 32 {
			let key = SymmetricKey(data: keyData)
			self.activeSymmetricKey = key
			return (key, existingBase64)
		}

		// Generate a new 256-bit (32 bytes) random symmetric key
		let newKey = SymmetricKey(size: .bits256)
		let keyData = newKey.withUnsafeBytes { Data($0) }
		let base64String = keyData.base64EncodedString()
		UserDefaults.standard.set(base64String, forKey: userDefaultsKey)
		self.activeSymmetricKey = newKey
		print("AirLink Security: Generated new AES-256-GCM key for device '\(deviceId)'")
		fflush(stdout)
		return (newKey, base64String)
	}

	func setActiveKey(base64: String) {
		keyLock.lock()
		defer { keyLock.unlock() }
		guard let keyData = Data(base64Encoded: base64), keyData.count == 32 else { return }
		self.activeSymmetricKey = SymmetricKey(data: keyData)
		print("AirLink Security: Set active AES-256-GCM key from client handshake")
		fflush(stdout)
	}

	func clearActiveKey() {
		keyLock.lock()
		defer { keyLock.unlock() }
		self.activeSymmetricKey = nil
	}

	/// Encrypts plaintext data using AES-256-GCM
	func encrypt(data: Data) -> Data? {
		keyLock.lock()
		guard let key = activeSymmetricKey else {
			keyLock.unlock()
			return nil
		}
		keyLock.unlock()

		do {
			let sealedBox = try AES.GCM.seal(data, using: key)
			return sealedBox.combined
		} catch {
			print("AirLink Security: Encryption failed: \(error)")
			return nil
		}
	}

	/// Decrypts AES-256-GCM combined data (nonce + ciphertext + tag)
	func decrypt(combinedData: Data) -> Data? {
		keyLock.lock()
		guard let key = activeSymmetricKey else {
			keyLock.unlock()
			return nil
		}
		keyLock.unlock()

		// A valid AES-GCM sealed box must have at least 28 bytes (12 bytes nonce + 16 bytes tag)
		guard combinedData.count >= 28 else { return nil }

		do {
			let sealedBox = try AES.GCM.SealedBox(combined: combinedData)
			let decrypted = try AES.GCM.open(sealedBox, using: key)
			return decrypted
		} catch {
			return nil
		}
	}
}
