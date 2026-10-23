//
//  AirLinkConnectionManager.swift
//  AirLink
//
//  Created by Grishka on 08.04.2023.
//

import Foundation
import Network
import System
import CryptoKit
import SwiftECC

public struct RemoteDeviceInfo{
	public let name:String
	public let type:DeviceType
	public let qrCodeData:Data?
	public var id:String?
	
	init(name: String, type: DeviceType, id: String? = nil) {
		self.name = name
		self.type = type
		self.id = id
		self.qrCodeData = nil
	}
	
	init(info:EndpointInfo, id: String? = nil){
		self.name=info.name!
		self.type=info.deviceType
		self.qrCodeData=info.qrCodeData
		self.id=id
	}
	
	public enum DeviceType:Int32{
		case unknown=0
		case phone
		case tablet
		case computer
		
		public static func fromRawValue(value:Int) -> DeviceType{
			switch value {
			case 0:
				return .unknown
			case 1:
				return .phone
			case 2:
				return .tablet
			case 3:
				return .computer
			default:
				return .unknown
			}
		}
	}
}


public enum AirLinkError:Error{
	case protocolError(_ message:String)
	case requiredFieldMissing(_ message:String)
	case ukey2
	case inputOutput
	case canceled(reason:CancellationReason)
	
	public enum CancellationReason{
		case userRejected, userCanceled, notEnoughSpace, unsupportedType, timedOut
	}
}

public struct TransferMetadata{
	public let files:[FileMetadata]
	public let id:String
	public let pinCode:String?
	public let textDescription:String?
	
	init(files: [FileMetadata], id: String, pinCode: String?, textDescription: String?=nil){
		self.files = files
		self.id = id
		self.pinCode = pinCode
		self.textDescription = textDescription
	}
}

public struct FileMetadata{
	public let name:String
	public let size:Int64
	public let mimeType:String
}

struct FoundServiceInfo{
	let service:NWBrowser.Result
	var device:RemoteDeviceInfo?
}

struct OutgoingTransferInfo{
	let service:NWBrowser.Result
	let device:RemoteDeviceInfo
	let connection:OutboundAirLinkConnection
	let delegate:ShareExtensionDelegate
}

struct EndpointInfo{
	var name:String?
	let deviceType:RemoteDeviceInfo.DeviceType
	let qrCodeData:Data?
	
	init(name: String, deviceType: RemoteDeviceInfo.DeviceType){
		self.name = name
		self.deviceType = deviceType
		self.qrCodeData=nil
	}
	
	init?(data:Data){
		guard data.count>17 else {return nil}
		let hasName=(data[0] & 0x10)==0
		let deviceNameLength:Int
		let deviceName:String?
		if hasName{
			deviceNameLength=Int(data[17])
			guard data.count>=deviceNameLength+18 else {return nil}
			guard let _deviceName=String(data: data[18..<(18+deviceNameLength)], encoding: .utf8) else {return nil}
			deviceName=_deviceName
		}else{
			deviceNameLength=0
			deviceName=nil
		}
		let rawDeviceType:Int=Int(data[0] & 7) >> 1
		self.name=deviceName
		self.deviceType=RemoteDeviceInfo.DeviceType.fromRawValue(value: rawDeviceType)
		var offset=1+16
		if hasName{
			offset=offset+1+deviceNameLength
		}
		var qrCodeData:Data?=nil
		while data.count-offset>2{ // read TLV records, if any
			let type=data[offset]
			let length=Int(data[offset+1])
			offset=offset+2
			if data.count-offset>=length{
				if type==1{ // QR code data
					qrCodeData=data.subdata(in: offset..<offset+length)
				}
				offset=offset+length
			}
		}
		self.qrCodeData=qrCodeData
	}
	
	func serialize()->Data{
		// 1 byte: Version(3 bits)|Visibility(1 bit)|Device Type(3 bits)|Reserved(1 bits)
		// Device types: unknown=0, phone=1, tablet=2, laptop=3
		var endpointInfo:[UInt8]=[UInt8(deviceType.rawValue << 1)]
		// 16 bytes: unknown random bytes
		for _ in 0...15{
			endpointInfo.append(UInt8.random(in: 0...255))
		}
		// Device name in UTF-8 prefixed with 1-byte length
		var nameChars=[UInt8](name!.utf8)
		if nameChars.count>255{
			nameChars=[UInt8](nameChars[0..<255])
		}
		endpointInfo.append(UInt8(nameChars.count))
		for ch in nameChars{
			endpointInfo.append(UInt8(ch))
		}
		return Data(endpointInfo)
	}
}

public protocol MainAppDelegate{
	func obtainUserConsent(for transfer:TransferMetadata, from device:RemoteDeviceInfo)
	func incomingTransfer(id:String, didFinishWith error:Error?)
}

public protocol ShareExtensionDelegate:AnyObject{
	func addDevice(device:RemoteDeviceInfo)
	func removeDevice(id:String)
	func startTransferWithQrCode(device:RemoteDeviceInfo)
	func connectionWasEstablished(pinCode:String)
	func connectionFailed(with error:Error)
	func transferAccepted()
	func transferProgress(progress:Double)
	func transferFinished()
}

public class AirLinkConnectionManager : NSObject, NetServiceDelegate, InboundAirLinkConnectionDelegate, OutboundAirLinkConnectionDelegate{
	
	private var tcpListener:NWListener;
	public let endpointID:[UInt8]=generateEndpointID()
	private var mdnsServices: [NetService] = []
	private var activeConnections:[String:InboundAirLinkConnection]=[:]
	private var foundServices:[String:FoundServiceInfo]=[:]
	private var shareExtensionDelegates:[ShareExtensionDelegate]=[]
	private var outgoingTransfers:[String:OutgoingTransferInfo]=[:]
	public var mainAppDelegate:(any MainAppDelegate)?
	private var discoveryRefCount=0
	
	private var browsers: [NWBrowser] = []
	private let discoveryQueue = DispatchQueue(label: "com.airlink.discovery", qos: .userInitiated)
	private var didStartTCPListener=false
	private var wantsToBeVisible=false
	
	private let pathMonitor = NWPathMonitor()
	private let pathMonitorQueue = DispatchQueue(label: "com.airlink.pathMonitor", qos: .utility)
	private var currentPathStatus: NWPath.Status?
	private var currentInterfaceNames: Set<String> = []
	private var restartWorkItem: DispatchWorkItem?
	
	private var qrCodePublicKey:ECPublicKey?
	private var qrCodePrivateKey:ECPrivateKey?
	private var qrCodeAdvertisingToken:Data?
	private var qrCodeNameEncryptionKey:SymmetricKey?
	private var qrCodeData:Data?
	
	public static let shared=AirLinkConnectionManager()
	
	private static func createListenerParameters() -> NWParameters {
		let parameters = NWParameters(tls: .none)
		parameters.allowLocalEndpointReuse = true
		parameters.includePeerToPeer = true
		return parameters
	}
	
	override init() {
		tcpListener = try! NWListener(using: Self.createListenerParameters())
		super.init()
		startPathMonitoring()
	}
	
	deinit {
		pathMonitor.cancel()
		tcpListener.cancel()
		stopAllMDNSServices()
	}
	
	private func startPathMonitoring() {
		pathMonitor.pathUpdateHandler = { [weak self] path in
			guard let self = self else { return }
			let newInterfaces = Set(path.availableInterfaces.map { $0.name })
			let statusChanged = self.currentPathStatus != path.status
			let interfacesChanged = self.currentInterfaceNames != newInterfaces
			
			self.currentPathStatus = path.status
			self.currentInterfaceNames = newInterfaces
			
			print("AirLinkConnectionManager: Network path update - status: \(path.status), interfaces: \(newInterfaces)")
			
			if path.status == .satisfied {
				if (statusChanged || interfacesChanged) && self.wantsToBeVisible {
					print("AirLinkConnectionManager: Active network interface changed (hotspot/tethering), restarting broadcast across all interfaces...")
					self.restartBroadcast()
				}
			} else {
				print("AirLinkConnectionManager: Network unsatisfied, pausing mDNS")
				self.stopAllMDNSServices()
			}
		}
		pathMonitor.start(queue: pathMonitorQueue)
	}
	
	private func restartBroadcast() {
		restartWorkItem?.cancel()
		let workItem = DispatchWorkItem { [weak self] in
			guard let self = self, self.wantsToBeVisible else { return }
			print("AirLinkConnectionManager: Executing broadcast refresh on new network path")
			if self.tcpListener.state != .ready {
				self.restartTCPListener()
			} else {
				self.initMDNS()
			}
		}
		restartWorkItem = workItem
		DispatchQueue.global(qos: .userInitiated).asyncAfter(deadline: .now() + 0.6, execute: workItem)
	}
	
	public func becomeVisible(){
		wantsToBeVisible=true
		if !didStartTCPListener{
			didStartTCPListener=true
			startTCPListener()
			return
		}
		if mdnsServices.isEmpty, tcpListener.port != nil{
			initMDNS()
		}
	}

	public func becomeInvisible(){
		wantsToBeVisible=false
		stopAllMDNSServices()
	}
	
	private func stopAllMDNSServices() {
		for s in mdnsServices {
			s.stop()
		}
		mdnsServices.removeAll()
	}
	
	private func restartTCPListener() {
		tcpListener.cancel()
		do {
			tcpListener = try NWListener(using: Self.createListenerParameters())
			didStartTCPListener = true
			startTCPListener()
		} catch {
			print("AirLinkConnectionManager: Failed to restart TCP listener: \(error)")
		}
	}
	
	private func startTCPListener(){
		tcpListener.stateUpdateHandler = { [weak self] (state: NWListener.State) in
			guard let self = self else { return }
			switch state {
			case .ready:
				print("AirLinkConnectionManager: TCP listener ready on port \(self.tcpListener.port?.rawValue ?? 0)")
				if self.wantsToBeVisible {
					self.initMDNS()
				}
			case .failed(let error):
				print("AirLinkConnectionManager: TCP listener failed: \(error)")
				if self.wantsToBeVisible {
					self.restartTCPListener()
				}
			case .cancelled:
				print("AirLinkConnectionManager: TCP listener cancelled")
			default:
				break
			}
		}
		tcpListener.newConnectionHandler = { [weak self] (connection: NWConnection) in
			guard let self = self else { return }
			let id = UUID().uuidString
			let conn = InboundAirLinkConnection(connection: connection, id: id)
			self.activeConnections[id] = conn
			conn.delegate = self
			conn.start()
		}
		tcpListener.start(queue: .global(qos: .utility))
	}
	
	private static func generateEndpointID()->[UInt8]{
		var id:[UInt8]=[]
		let alphabet="0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".compactMap {UInt8($0.asciiValue!)}
		for _ in 0...3{
			id.append(alphabet[Int.random(in: 0..<alphabet.count)])
		}
		return id
	}
	
	private func initMDNS(){
		stopAllMDNSServices()
		
		let nameBytes:[UInt8]=[
			0x23, // PCP
			endpointID[0], endpointID[1], endpointID[2], endpointID[3],
			0xFC, 0x9F, 0x5E, // Service ID hash
			0, 0
		]
		let name=Data(nameBytes).urlSafeBase64EncodedString()
		let endpointInfo=EndpointInfo(name: Host.current().localizedName ?? "Mac", deviceType: .computer)
		
		guard let portRaw = tcpListener.port?.rawValue else {
			print("AirLinkConnectionManager: TCP listener port is nil, cannot publish mDNS")
			return
		}
		let port:Int32=Int32(portRaw)
		let txtData = NetService.data(fromTXTRecord: [
			"n": endpointInfo.serialize().urlSafeBase64EncodedString().data(using: .utf8)!
		])
		
		// Publish the canonical Google Quick Share protocol Bonjour service
		// domain: "" automatically binds to all active network interfaces (Wi-Fi, Mobile Hotspots, USB tethering)
		let service = NetService(domain: "", type: "_FC9F5ED42C8A._tcp.", name: name, port: port)
		service.delegate = self
		service.setTXTRecord(txtData)
		service.schedule(in: .main, forMode: .common)
		service.publish()
		mdnsServices.append(service)
		print("AirLinkConnectionManager: publishing mDNS service _FC9F5ED42C8A._tcp. as '\(name)' on port \(port)")
	}
	
	// MARK: - NetServiceDelegate
	public func netServiceDidPublish(_ sender: NetService) {
		print("AirLinkConnectionManager: Successfully published mDNS service \(sender.type) with name '\(sender.name)' on port \(sender.port)")
	}

	public func netService(_ sender: NetService, didNotPublish errorDict: [String : NSNumber]) {
		print("AirLinkConnectionManager: Failed to publish mDNS service \(sender.type): \(errorDict)")
	}

	public func netServiceDidStop(_ sender: NetService) {
		print("AirLinkConnectionManager: mDNS service \(sender.type) stopped")
	}
	
	func obtainUserConsent(for transfer: TransferMetadata, from device: RemoteDeviceInfo, connection: InboundAirLinkConnection) {
		guard let delegate=mainAppDelegate else {return}
		delegate.obtainUserConsent(for: transfer, from: device)
	}
	
	func connectionWasTerminated(connection:InboundAirLinkConnection, error:Error?){
		guard let delegate=mainAppDelegate else {return}
		delegate.incomingTransfer(id: connection.id, didFinishWith: error)
		activeConnections.removeValue(forKey: connection.id)
	}
	
	public func submitUserConsent(transferID:String, accept:Bool){
		guard let conn=activeConnections[transferID] else {return}
		conn.submitUserConsent(accepted: accept)
	}
	
	public func startDeviceDiscovery(){
		if discoveryRefCount == 0 {
			foundServices.removeAll()
			stopBrowsers()
			
			let serviceTypes = ["_FC9F5ED42C8A._tcp.", "_FC9F5ED7._tcp."]
			for serviceType in serviceTypes {
				let descriptor = NWBrowser.Descriptor.bonjourWithTXTRecord(type: serviceType, domain: nil)
				let parameters = NWParameters()
				parameters.includePeerToPeer = true
				let browser = NWBrowser(for: descriptor, using: parameters)
				browser.browseResultsChangedHandler = { [weak self] newResults, changes in
					guard let self = self else { return }
					for change in changes {
						switch change {
						case let .added(res):
							self.maybeAddFoundDevice(service: res)
						case let .removed(res):
							self.maybeRemoveFoundDevice(service: res)
						default:
							break
						}
					}
				}
				browser.start(queue: discoveryQueue)
				browsers.append(browser)
				print("AirLinkConnectionManager: Started background discovery for \(serviceType)")
			}
		}
		discoveryRefCount += 1
	}

	private func stopBrowsers() {
		for browser in browsers {
			browser.cancel()
		}
		browsers.removeAll()
	}
	
	public func stopDeviceDiscovery(){
		discoveryRefCount -= 1
		assert(discoveryRefCount >= 0)
		if discoveryRefCount == 0 {
			stopBrowsers()
		}
	}
	
	public func addShareExtensionDelegate(_ delegate:ShareExtensionDelegate){
		shareExtensionDelegates.append(delegate)
		for service in foundServices.values{
			guard let device=service.device else {continue}
			delegate.addDevice(device: device)
		}
	}
	
	public func removeShareExtensionDelegate(_ delegate:ShareExtensionDelegate){
		shareExtensionDelegates.removeAll(where: {$0===delegate})
	}
	
	public func cancelOutgoingTransfer(id:String){
		guard let transfer=outgoingTransfers[id] else {return}
		transfer.connection.cancel()
	}
	
	private func endpointID(for service:NWBrowser.Result)->String?{
		guard case let NWEndpoint.service(name: serviceName, type: _, domain: _, interface: _)=service.endpoint else {return nil}
		if let nameData=Data.dataFromUrlSafeBase64(serviceName), nameData.count>=10, nameData[0]==0x23 {
			let endpointID=String(data: nameData.subdata(in: 1..<5), encoding: .ascii)!
			return endpointID
		}
		return serviceName
	}
	
	private func maybeAddFoundDevice(service:NWBrowser.Result){
		#if DEBUG
		print("found service \(service)")
		#endif
		for interface in service.interfaces{
			if case .loopback=interface.type{
				#if DEBUG
				print("ignoring localhost service")
				#endif
				return
			}
		}
		guard let endpointID=endpointID(for: service) else {return}
		#if DEBUG
		print("service name is valid, endpoint ID \(endpointID)")
		#endif
		var foundService=FoundServiceInfo(service: service)
		
		guard case let NWBrowser.Result.Metadata.bonjour(txtRecord)=service.metadata else {return}
		var endpointInfo: EndpointInfo?
		if let endpointInfoEncoded=txtRecord.dictionary["n"],
		   let endpointInfoSerialized=Data.dataFromUrlSafeBase64(endpointInfoEncoded) {
			endpointInfo = EndpointInfo(data: endpointInfoSerialized)
		}
		
		let resolvedInfo = endpointInfo ?? EndpointInfo(name: endpointID, deviceType: .phone)
		let deviceInfo = addFoundDevice(foundService: &foundService, endpointInfo: resolvedInfo, endpointID: endpointID)
		
		if let qrData=endpointInfo?.qrCodeData, let _=qrCodeAdvertisingToken{
#if DEBUG
			print("Device has QR data: \(qrData.base64EncodedString()), our advertising token is \(qrCodeAdvertisingToken!.base64EncodedString())")
#endif
			if qrData==qrCodeAdvertisingToken!{
				DispatchQueue.main.async {
					for delegate in self.shareExtensionDelegates{
						delegate.startTransferWithQrCode(device: deviceInfo)
					}
				}
			}else if qrData.count>28{
				do{
					let box=try AES.GCM.SealedBox(combined: qrData)
					let decryptedName=try AES.GCM.open(box, using: qrCodeNameEncryptionKey!, authenticating: qrCodeAdvertisingToken!)
					guard let name=String.init(data: decryptedName, encoding: .utf8) else {return}
					var customInfo = resolvedInfo
					customInfo.name = name
					let updatedDevice = addFoundDevice(foundService: &foundService, endpointInfo: customInfo, endpointID: endpointID)
					DispatchQueue.main.async {
						for delegate in self.shareExtensionDelegates{
							delegate.startTransferWithQrCode(device: updatedDevice)
						}
					}
				}catch{
#if DEBUG
					print("Error decrypting QR code data of an invisible device: \(error)")
#endif
				}
			}
		}
	}
	
	private func addFoundDevice(foundService:inout FoundServiceInfo, endpointInfo:EndpointInfo, endpointID:String) -> RemoteDeviceInfo{
		let deviceInfo=RemoteDeviceInfo(info: endpointInfo, id: endpointID)
		foundService.device=deviceInfo
		foundServices[endpointID]=foundService
		DispatchQueue.main.async {
			for delegate in self.shareExtensionDelegates{
				delegate.addDevice(device: deviceInfo)
			}
		}
		return deviceInfo
	}
	
	private func maybeRemoveFoundDevice(service:NWBrowser.Result){
		guard let endpointID=endpointID(for: service) else {return}
		guard let _=foundServices.removeValue(forKey: endpointID) else {return}
		DispatchQueue.main.async {
			for delegate in self.shareExtensionDelegates {
				delegate.removeDevice(id: endpointID)
			}
		}
	}
	
	public func generateQrCodeKey() -> String{
		let domain=Domain.instance(curve: .EC256r1)
		let (pubKey, privKey)=domain.makeKeyPair()
		qrCodePublicKey=pubKey
		qrCodePrivateKey=privKey
		var keyData=Data()
		keyData.append(contentsOf: [0, 0, 2])
		let keyBytes=Data(pubKey.w.x.asSignedBytes())
		// Sometimes, for some keys, there will be a leading zero byte. Strip that, Android really hates it (it breaks the endpoint info)
		keyData.append(keyBytes.suffixOfAtMost(numBytes: 32))
		
		let ikm=SymmetricKey(data: keyData)
		qrCodeAdvertisingToken=AirLinkConnection.hkdf(inputKeyMaterial: ikm, salt: Data(), info: "advertisingContext".data(using: .utf8)!, outputByteCount: 16).data()
		qrCodeNameEncryptionKey=AirLinkConnection.hkdf(inputKeyMaterial: ikm, salt: Data(), info: "encryptionKey".data(using: .utf8)!, outputByteCount: 16)
		qrCodeData=keyData
		
		return keyData.urlSafeBase64EncodedString()
	}
	
	public func clearQrCodeKey(){
		qrCodePublicKey=nil
		qrCodePrivateKey=nil
		qrCodeAdvertisingToken=nil
		qrCodeNameEncryptionKey=nil
		qrCodeData=nil
	}
	
	public func startOutgoingTransfer(deviceID:String, delegate:ShareExtensionDelegate, urls:[URL]){
		guard let info=foundServices[deviceID] else {return}
		let tcp=NWProtocolTCP.Options.init()
		tcp.noDelay=true
		let nwconn=NWConnection(to: info.service.endpoint, using: NWParameters(tls: .none, tcp: tcp))
		let conn=OutboundAirLinkConnection(connection: nwconn, id: deviceID, urlsToSend: urls)
		conn.delegate=self
		conn.qrCodePrivateKey=qrCodePrivateKey
		let transfer=OutgoingTransferInfo(service: info.service, device: info.device!, connection: conn, delegate: delegate)
		outgoingTransfers[deviceID]=transfer
		conn.start()
	}
	
	func outboundConnectionWasEstablished(connection: OutboundAirLinkConnection) {
		guard let transfer=outgoingTransfers[connection.id] else {return}
		DispatchQueue.main.async {
			transfer.delegate.connectionWasEstablished(pinCode: connection.pinCode!)
		}
	}
	
	func outboundConnectionTransferAccepted(connection: OutboundAirLinkConnection) {
		guard let transfer=outgoingTransfers[connection.id] else {return}
		DispatchQueue.main.async {
			transfer.delegate.transferAccepted()
		}
	}
	
	func outboundConnection(connection: OutboundAirLinkConnection, transferProgress: Double) {
		guard let transfer=outgoingTransfers[connection.id] else {return}
		DispatchQueue.main.async {
			transfer.delegate.transferProgress(progress: transferProgress)
		}
	}
	
	func outboundConnection(connection: OutboundAirLinkConnection, failedWithError: Error) {
		guard let transfer=outgoingTransfers[connection.id] else {return}
		DispatchQueue.main.async {
			transfer.delegate.connectionFailed(with: failedWithError)
		}
		outgoingTransfers.removeValue(forKey: connection.id)
	}
	
	func outboundConnectionTransferFinished(connection: OutboundAirLinkConnection) {
		guard let transfer=outgoingTransfers[connection.id] else {return}
		DispatchQueue.main.async {
			transfer.delegate.transferFinished()
		}
		outgoingTransfers.removeValue(forKey: connection.id)
	}
}

