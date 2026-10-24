"use client";

import { motion } from "framer-motion";
import { ShieldCheck, Lock, EyeOff, KeyRound, ServerOff, Terminal } from "lucide-react";

export default function SecuritySection() {
  const securityPillars = [
    {
      icon: ServerOff,
      title: "100% Local Network",
      desc: "Packets never leave your local Wi-Fi router. There is zero external cloud relay, zero SaaS subscriptions, and zero tracking.",
      color: "text-blue-500",
      bg: "bg-blue-500/10",
    },
    {
      icon: Lock,
      title: "AES-256-GCM Encryption",
      desc: "All video chunks, clipboard snippets, and touch coordinates are encrypted using military-grade AES-256-GCM with authenticated tags.",
      color: "text-emerald-500",
      bg: "bg-emerald-500/10",
    },
    {
      icon: KeyRound,
      title: "Replay Attack Protection",
      desc: "Every WebSocket message utilizes a strictly randomized, unique 12-byte Initialization Vector (IV) to prevent packet replay or spoofing.",
      color: "text-purple-500",
      bg: "bg-purple-500/10",
    },
    {
      icon: EyeOff,
      title: "Zero Analytics or Telemetry",
      desc: "No Google Analytics, no Firebase Crashlytics, no third-party SDKs. Your screen content and keystrokes belong only to you.",
      color: "text-amber-500",
      bg: "bg-amber-500/10",
    },
  ];

  return (
    <section id="security" className="py-16 sm:py-24 px-4 max-w-6xl mx-auto">
      <div className="glass-panel rounded-3xl p-8 sm:p-14 relative overflow-hidden border border-black/10 dark:border-white/10 shadow-2xl">
        {/* Background glow */}
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-96 h-96 bg-emerald-500/10 rounded-full blur-3xl pointer-events-none -z-10" />

        <div className="text-center max-w-2xl mx-auto mb-12 sm:mb-16">
          <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20 mb-3">
            <ShieldCheck className="w-3.5 h-3.5" />
            <span>Cryptographic Privacy</span>
          </div>
          <h2 className="text-3xl sm:text-4xl font-bold tracking-tight text-zinc-900 dark:text-white">
            Security Designed into Every Packet
          </h2>
          <p className="mt-3 text-sm sm:text-base text-zinc-600 dark:text-zinc-400">
            Unlike proprietary tools that tunnel your sensitive screen and clipboard data through foreign cloud servers, AirLink stays strictly inside your room.
          </p>
        </div>

        {/* 4 Security Pillars */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {securityPillars.map((pillar) => {
            const Icon = pillar.icon;
            return (
              <div
                key={pillar.title}
                className="p-6 rounded-2xl bg-black/[0.02] dark:bg-white/[0.03] border border-black/5 dark:border-white/5 space-y-3"
              >
                <div className={`w-10 h-10 rounded-xl ${pillar.bg} ${pillar.color} flex items-center justify-center`}>
                  <Icon className="w-5 h-5" />
                </div>
                <h3 className="text-lg font-bold text-zinc-900 dark:text-white">{pillar.title}</h3>
                <p className="text-sm text-zinc-600 dark:text-zinc-400 leading-relaxed">{pillar.desc}</p>
              </div>
            );
          })}
        </div>

        {/* Security Architecture Diagram Banner */}
        <div className="mt-8 p-5 rounded-2xl bg-zinc-900 text-zinc-300 font-mono text-xs border border-zinc-800 space-y-2">
          <div className="flex items-center gap-2 text-zinc-400 pb-2 border-b border-zinc-800">
            <Terminal className="w-4 h-4 text-emerald-400" />
            <span>AirLink Local Network Architecture & Protocol Specification</span>
          </div>
          <div className="overflow-x-auto text-[11px] leading-relaxed text-zinc-400 py-1">
            [Android Client] ⇄ AES-256-GCM (Payload + 12-byte IV + Auth Tag) ⇄ [mDNS ZeroConf / Local WebSocket] ⇄ [macOS AppKit]
          </div>
          <div className="text-[10px] text-emerald-400/90 pt-1">
            ✓ Verified: Zero internet outbound packets generated during screen streaming or clipboard synchronizations.
          </div>
        </div>
      </div>
    </section>
  );
}
