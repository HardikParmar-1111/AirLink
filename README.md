<div align="center">

  # ⚡ AirLink

  ### The Secure, Zero-Latency Bridge Between Android and macOS

  [![Release](https://img.shields.io/github/v/release/HardikParmar-1111/AirLink?style=for-the-badge&color=007AFF&logo=github)](https://github.com/HardikParmar-1111/AirLink/releases)
  [![Platforms](https://img.shields.io/badge/Platform-macOS%20%7C%20Android-34C759?style=for-the-badge&logo=apple&logoColor=white)](https://github.com/HardikParmar-1111/AirLink)
  [![Security](https://img.shields.io/badge/Security-AES--256--GCM-FF9500?style=for-the-badge&logo=shield)](https://github.com/HardikParmar-1111/AirLink)
  [![License](https://img.shields.io/badge/License-MIT-5856D6?style=for-the-badge)](LICENSE)
  [![Web](https://img.shields.io/badge/Website-Live%20on%20GitHub%20Pages-000000?style=for-the-badge&logo=nextdotjs&logoColor=white)](https://hardikparmar-1111.github.io/AirLink/)

  <br />

  <!-- Hero Banner Image -->
  <img src="assets/banner.png" alt="AirLink Hero Banner" width="850" style="max-width: 100%; border-radius: 16px; box-shadow: 0 20px 50px rgba(0,0,0,0.3);" />

  <p align="center">
    <strong>AirLink</strong> brings true Apple-native Continuity to Android and macOS. Experience real-time screen streaming, interactive touch injection, cross-platform clipboard synchronization, Notch Drop file transfers, and remote incoming call handling with instant SMS Quick Reply—all over your private, encrypted local network.
  </p>

  <p align="center">
    <a href="#-key-features">Key Features</a> •
    <a href="#-quick-start">Quick Start</a> •
    <a href="#-architecture--security">Architecture</a> •
    <a href="#-tech-stack">Tech Stack</a> •
    <a href="#-building-from-source">Building from Source</a> •
    <a href="#-contributing">Contributing</a>
  </p>

</div>

---

## 📑 Table of Contents

- [Overview](#-overview)
- [Key Features](#-key-features)
- [Architecture & Security](#-architecture--security)
- [Quick Start](#-quick-start)
  - [macOS Client](#1-macos-client)
  - [Android Client](#2-android-client)
  - [Web Showcase](#3-web-showcase--landing-page)
- [Building from Source](#-building-from-source)
  - [Android Client (`.apk`)](#prerequisites-for-android)
  - [macOS Client (`AirLink.app`)](#prerequisites-for-macos)
  - [Landing Page (`AirLink Web`)](#prerequisites-for-airlink-web)
- [Tech Stack](#-tech-stack)
- [Permissions & Privacy](#-permissions--privacy)
- [Troubleshooting](#-troubleshooting)
- [Contributing](#-contributing)
- [License](#-license)

---

## 💡 Overview

AirLink eliminates the barrier between Google's Android and Apple's macOS ecosystem without relying on cloud relays, third-party subscriptions, or proprietary locks. Operating entirely over your local Wi-Fi subnet with zero telemetry, AirLink provides a unified workstation experience: stream your phone display at native responsiveness, control apps using your Mac trackpad, beam files through the MacBook notch, synchronize clipboards bidirectionally, and decline calls with automated SMS replies right from macOS notifications.

---

## ✨ Key Features

<table>
  <tr>
    <td width="50%">
      <h3>📱 Real-Time Screen Mirroring</h3>
      <p>High-fidelity, low-latency screen streaming from Android to macOS. Features custom binary JPEG frame streaming over WebSockets with responsive window scaling and native macOS rendering.</p>
    </td>
    <td width="50%">
      <h3>🖱️ Remote Trackpad & Gesture Control</h3>
      <p>Directly control your Android device using your Mac's trackpad and keyboard. Native tap, swipe, and scroll gestures are translated into Android input events via an Accessibility Service.</p>
    </td>
  </tr>
  <tr>
    <td width="50%">
      <h3>📞 Call Quick Reply ("Decline & Text")</h3>
      <p>Receive native macOS notification banners when your phone rings. Click <strong>Decline & Text</strong> to silently reject the call on Android via <code>TelecomManager</code> and immediately dispatch an automated SMS reply.</p>
    </td>
    <td width="50%">
      <h3>📋 Universal Clipboard Sync</h3>
      <p>Copy text, URLs, or code on Android and paste instantly on Mac—or vice versa. Continuous monitoring keeps both system clipboards in lockstep with zero manual steps.</p>
    </td>
  </tr>
  <tr>
    <td width="50%">
      <h3>🛸 Notch Drop Zone & Instant File Beaming</h3>
      <p>Drag any file onto your MacBook's notch or menu bar icon to wirelessly beam it straight to your Android phone's storage with visual haptic feedback.</p>
    </td>
    <td width="50%">
      <h3>🔒 Hardened AES-256-GCM Cryptography</h3>
      <p>Zero plaintext on the wire. Screen frames, control gestures, call notifications, and telemetry payloads are signed and encrypted with 256-bit symmetric keys, 12-byte IVs, and 128-bit authentication tags.</p>
    </td>
  </tr>
  <tr>
    <td width="50%">
      <h3>🎨 Modern Jetpack Compose Experience</h3>
      <p>A completely redesigned Android UI featuring an Apple-inspired radar scanning screen, frosted-glass paired dashboard widgets, and edge-to-edge system insets padding.</p>
    </td>
    <td width="50%">
      <h3>🔋 Real-Time Device Telemetry</h3>
      <p>Monitor your phone's battery percentage, charging state, and connectivity status directly from the macOS Menu Bar and Notch Drop overlay.</p>
    </td>
  </tr>
</table>

---

## 🛡️ Architecture & Security

AirLink is engineered around an offline-first peer-to-peer local network topology:

```mermaid
graph TD
    subgraph Android["📱 Android Device"]
        SC[ScreenCapture Service]
        AS[Accessibility Engine]
        CB_A[Clipboard Manager]
        CR[Telephony & Call Receiver]
        SMS[Automated SMS Dispatcher]
        ENC[AES-256-GCM Engine]
    end

    subgraph Transport["🔒 Encrypted Local Network (mDNS & WebSockets :8080)"]
        WS["Full-Duplex Encrypted Channel\n(12-byte IV + AES-GCM Authenticated Frames)"]
    end

    subgraph macOS["💻 macOS (Companion App)"]
        DEC[Decryption Engine]
        MV[Phone Mirroring View]
        CB_M[NSPasteboard Sync]
        NOTIF[UNUserNotificationCenter\n(Incoming Call & Quick Reply)]
        ND[Notch Drop & Menu Bar]
    end

    SC -->|Raw Screen Payloads| ENC
    CB_A <-->|Clipboard Events| ENC
    CR -->|Call State & Number| ENC
    ENC -->|Ciphertext + Auth Tag| WS

    WS -->|Encrypted Frames| DEC
    DEC -->|Video Stream| MV
    DEC <-->|Clipboard Packets| CB_M
    DEC -->|Incoming Call Alert| NOTIF
    DEC -->|Battery & Telemetry| ND

    NOTIF -.->|Decline & Text Command| WS
    MV -.->|Trackpad Gestures| WS
    ND -.->|File Transfer Beams| WS

    WS -.->|Remote Commands| ENC
    ENC -.->|Decline Call Action| CR
    ENC -.->|Send SMS Payload| SMS
    ENC -.->|Touch & Scroll Events| AS
```

- **Zero Cloud Relay**: Packets are routed strictly within your local Wi-Fi subnet. No third-party servers ever touch your data.
- **Cross-Platform Cryptography**: End-to-end authenticated encryption using Java Cryptography Architecture (JCA) on Android and Apple's CryptoKit on macOS.
- **Decoupled Architecture**: High-frequency video frame transmission operates independently of control queues, preventing UI input lags.

---

## 🚀 Quick Start

Get up and running in under two minutes:

### 1. macOS Client

1. Download `AirLink.app` from the **[Latest Release](https://github.com/HardikParmar-1111/AirLink/releases)**.
2. Move **AirLink.app** to your `/Applications` folder.
3. Launch **AirLink**. The menu bar icon will appear and listen on local port `8080`.
   > **Note on Gatekeeper:** If macOS displays an *unidentified developer* warning:
   > - Right-click **AirLink.app** and select **Open**.
   > - Confirm by clicking **Open** in the dialog (or allow in **System Settings** → **Privacy & Security**).

### 2. Android Client

1. Download `AirLink-Android-v1.0.apk` from the **[Releases](https://github.com/HardikParmar-1111/AirLink/releases)** page onto your Android device.
2. Install the APK and grant permissions when requested:
   - **Screen Recording (MediaProjection)**: Required for screen mirroring.
   - **Accessibility Service**: Required to synthesize taps, scrolls, and swipes from your Mac trackpad.
   - **Phone & SMS (Optional for Quick Reply)**: Required to detect incoming calls and send automated SMS replies when declined from Mac.
3. Open AirLink; your Mac will be automatically detected on the local network. Tap **Pair** to connect.

### 3. Web Showcase & Landing Page

Visit our interactive web landing page hosted on GitHub Pages:
**[https://hardikparmar-1111.github.io/AirLink/](https://hardikparmar-1111.github.io/AirLink/)**

---

## 🛠️ Building from Source

### Prerequisites for Android
- **Android Studio** Ladybug (2024.2+) or later
- **JDK 17 or JDK 21**
- Android SDK Platform 35 (`minSdk 26`, `targetSdk 35`)

```bash
# Clone the repository
git clone https://github.com/HardikParmar-1111/AirLink.git
cd AirLink/"AirLink mobile"

# Build debug or release APK
./gradlew assembleRelease
```
The compiled APK will be located in `app/build/outputs/apk/release/`.

---

### Prerequisites for macOS
- **macOS Sonoma (14.0)** or later
- **Xcode 15.0+** with Command Line Tools installed

```bash
cd ../"AirLink Mac"

# Build using xcodebuild
xcodebuild -project AirLink.xcodeproj \
           -scheme AirLink \
           -configuration Release \
           -derivedDataPath ./build clean build
```

---

### Prerequisites for AirLink Web
- **Node.js 18+** & `npm`

```bash
cd ../"AirLink Web"

# Install dependencies and run locally
npm install
npm run dev

# Export static production bundle (for GitHub Pages / docs)
npm run build
```

---

## 💻 Tech Stack

| Domain | Technology / Library | Purpose |
| :--- | :--- | :--- |
| **Android UI** | Jetpack Compose, Material 3 | Modern radar scanner and paired dashboard widgets |
| **Android Core** | Kotlin, Coroutines, Flow | Asynchronous I/O and reactive state orchestration |
| **Display Capture** | Android `MediaProjectionManager` | Low-overhead screen capture pipeline |
| **Telephony & SMS** | `TelephonyCallback`, `TelecomManager`, `SmsManager` | Background call detection and automated SMS quick reply |
| **Input Injection** | `AccessibilityService` API | Remote cursor, tap, scroll, and drag synthesis |
| **macOS Native** | Swift 5.9+, SwiftUI, AppKit | Menu Bar utility, floating mirror window, Notch Drop |
| **Notifications** | `UserNotifications` (UNUserNotificationCenter) | Interactive incoming call alerts with custom action buttons |
| **Web Landing Page** | Next.js 15, React, Tailwind CSS | Product landing page with floating pill dock |
| **Networking** | WebSockets & mDNS / Bonjour | P2P local discovery and high-throughput streaming |
| **Security** | Java JCA & Apple CryptoKit | End-to-end AES-256-GCM symmetric encryption |

---

## 🔒 Permissions & Privacy

AirLink is privacy-first. We do not track telemetry, collect personal data, or communicate with external servers.

| Platform | Permission | Rationale |
| :--- | :--- | :--- |
| **Android** | `MEDIA_PROJECTION` | Captures screen frames to project to your Mac display. |
| **Android** | `ACCESSIBILITY_SERVICE` | Translates Mac trackpad movements and gestures into on-screen taps and swipes. |
| **Android** | `READ_PHONE_STATE` | Detects when a phone call is ringing to notify your Mac. |
| **Android** | `ANSWER_PHONE_CALLS` | Silently declines calls when the Mac user clicks "Decline & Text". |
| **Android** | `SEND_SMS` | Dispatches the automated Quick Reply text message to the caller. |
| **Android** | `ACCESS_NETWORK_STATE` & `INTERNET` | Establishes the local WebSocket transport over your shared Wi-Fi. |
| **macOS** | Local Network & Notifications | Receives data packets from Android and presents interactive call alerts. |

---

## ❓ Troubleshooting

<details>
<summary><strong>Devices do not discover each other on the network</strong></summary>

- Confirm that both your Mac and Android phone are on the exact same Wi-Fi subnet (e.g., both on the 5 GHz band).
- Disable AP/Client Isolation on your Wi-Fi router, as it prevents local peer-to-peer communication.
- Ensure macOS Firewall permits incoming connections to AirLink on port `8080`.
</details>

<details>
<summary><strong>Call Quick Reply does not trigger an SMS</strong></summary>

- Ensure `SEND_SMS`, `READ_PHONE_STATE`, and `ANSWER_PHONE_CALLS` permissions are granted on Android.
- If Android battery saver or OEM aggressive task killers are enabled, set AirLink's battery usage to *Unrestricted*.
</details>

<details>
<summary><strong>Trackpad clicks do not register on Android</strong></summary>

- Verify that **AirLink Accessibility Service** is enabled in Android **Settings** → **Accessibility** → **Installed Apps**.
</details>

<details>
<summary><strong>High latency or dropped frames</strong></summary>

- Connect to a 5 GHz Wi-Fi network rather than 2.4 GHz to minimize interference.
- Ensure Doze mode or aggressive battery saver is not throttling the background socket.
</details>

---

## 🤝 Contributing

Contributions make the open-source community an incredible place to learn, inspire, and create. Any contributions you make are **greatly appreciated**.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

Distributed under the **MIT License**. See [`LICENSE`](LICENSE) for more information.

---

<div align="center">
  <sub>Engineered with precision for cross-platform interoperability. Maintained by <a href="https://github.com/HardikParmar-1111">Hardik Parmar</a>.</sub>
</div>