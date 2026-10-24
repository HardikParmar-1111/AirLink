"use client";

import { CheckCircle2, Cpu, Sparkles } from "lucide-react";

export default function SpecsTable() {
  const specs = [
    {
      category: "Video Streaming",
      details: [
        { label: "Codec Engine", value: "Custom Binary Image Stream (JPEG over WebSockets)" },
        { label: "Resolution & Framerate", value: "Adaptive 1080p @ 60 FPS (up to 2400x1080)" },
        { label: "Bitrate Cap", value: "Dynamic 12 Mbps - 24 Mbps bitrate adaptation" },
        { label: "Glass-to-Glass Latency", value: "< 24 ms average over 5 GHz 802.11ax / ac" },
      ],
    },
    {
      category: "Input & Continuity",
      details: [
        { label: "Touch Injection", value: "Android AccessibilityService (60 Hz polling)" },
        { label: "Trackpad Gestures", value: "Pinch zoom, two-finger momentum scroll, right-click context" },
        { label: "Clipboard Sync", value: "Bidirectional zero-click sync (< 4 ms latency)" },
        { label: "Telemetry HUD", value: "MacBook notch drop + Menu bar battery & signal monitor" },
      ],
    },
    {
      category: "Platform & Requirements",
      details: [
        { label: "macOS Compatibility", value: "macOS 13.0+ (Ventura, Sonoma, Sequoia) • Universal (Apple Silicon & Intel)" },
        { label: "Android Compatibility", value: "Android 10.0+ (API 29+) • Compatible with Pixel, Galaxy, OnePlus, Xiaomi" },
        { label: "Network Protocol", value: "Local WebSocket (RFC 6455) + Bonjour / mDNS Service Discovery" },
        { label: "Resource Usage", value: "< 35 MB RAM memory footprint, < 3% macOS CPU overhead" },
      ],
    },
  ];

  return (
    <section id="specs" className="py-16 sm:py-24 px-4 max-w-5xl mx-auto">
      <div className="text-center max-w-2xl mx-auto mb-12">
        <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-zinc-500/10 text-zinc-600 dark:text-zinc-400 border border-zinc-500/20 mb-3">
          <Cpu className="w-3.5 h-3.5" />
          <span>Technical Architecture</span>
        </div>
        <h2 className="text-3xl sm:text-4xl font-bold tracking-tight text-zinc-900 dark:text-white">
          System Specifications
        </h2>
        <p className="mt-3 text-sm sm:text-base text-zinc-600 dark:text-zinc-400">
          Built natively in Swift for macOS and Kotlin for Android for maximum operating system efficiency.
        </p>
      </div>

      <div className="space-y-6">
        {specs.map((group) => (
          <div
            key={group.category}
            className="glass-panel rounded-3xl p-6 sm:p-8 shadow-sm hover:shadow-md transition-shadow"
          >
            <h3 className="text-lg font-bold text-zinc-900 dark:text-white pb-4 border-b border-black/5 dark:border-white/10 flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-blue-500" />
              <span>{group.category}</span>
            </h3>

            <div className="mt-4 divide-y divide-black/5 dark:divide-white/5">
              {group.details.map((item) => (
                <div key={item.label} className="py-3 sm:py-3.5 flex flex-col sm:flex-row sm:items-center justify-between text-xs sm:text-sm gap-1">
                  <span className="text-zinc-500 dark:text-zinc-400 font-medium">{item.label}</span>
                  <span className="text-zinc-900 dark:text-zinc-100 font-mono font-medium sm:text-right">
                    {item.value}
                  </span>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
