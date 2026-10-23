//
//  AirLinkApp.swift
//  AirLink
//

import SwiftUI

@main
struct AirLinkApp: App {
	@NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
	@StateObject private var model = AirLinkModel.shared

	var body: some Scene {
		Settings {
			EmptyView()
		}
	}
}
