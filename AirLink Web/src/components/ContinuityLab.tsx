"use client";

import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  Smartphone,
  Laptop,
  ClipboardCopy,
  BatteryCharging,
  Wifi,
  MousePointer,
  Sparkles,
  Check,
  Send,
  Camera,
  MessageSquare,
  Settings,
  Globe,
} from "lucide-react";

export default function ContinuityLab() {
  const [activeTab, setActiveTab] = useState<"mirror" | "clipboard" | "notch">("mirror");

  // Mirror simulator state
  const [activeApp, setActiveApp] = useState<string>("home");
  const [taps, setTaps] = useState<{ id: number; x: number; y: number }[]>([]);

  // Clipboard simulator state
  const [macClipText, setMacClipText] = useState("⚡ Meeting passcode: 849-210-994");
  const [phoneClipText, setPhoneClipText] = useState("⚡ Meeting passcode: 849-210-994");
  const [isSyncing, setIsSyncing] = useState(false);
  const [copiedMac, setCopiedMac] = useState(false);
  const [copiedPhone, setCopiedPhone] = useState(false);

  // Notch simulator state
  const [notchExpanded, setNotchExpanded] = useState(true);
  const [batteryLevel, setBatteryLevel] = useState(88);
  const [isCharging, setIsCharging] = useState(true);

  const handlePhoneTap = (e: React.MouseEvent<HTMLDivElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    const newTap = { id: Date.now(), x, y };
    setTaps((prev) => [...prev.slice(-4), newTap]);
    setTimeout(() => {
      setTaps((prev) => prev.filter((t) => t.id !== newTap.id));
    }, 700);
  };

  const handleSyncClipboard = (from: "mac" | "phone") => {
    setIsSyncing(true);
    setTimeout(() => {
      if (from === "mac") {
        setPhoneClipText(macClipText);
      } else {
        setMacClipText(phoneClipText);
      }
      setIsSyncing(false);
    }, 280);
  };

  return (
    <section id="playground" className="py-16 sm:py-24 px-4 max-w-6xl mx-auto">
      {/* Title */}
      <div className="text-center max-w-2xl mx-auto mb-10 sm:mb-14">
        <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 border border-indigo-500/20 mb-3">
          <Sparkles className="w-3.5 h-3.5" />
          <span>Interactive Playground</span>
        </div>
        <h2 className="text-3xl sm:text-4xl font-bold tracking-tight text-zinc-900 dark:text-white">
          AirLink Continuity Lab
        </h2>
        <p className="mt-3 text-sm sm:text-base text-zinc-600 dark:text-zinc-400">
          Try the features yourself right in your browser. Switch modes to explore screen control, instant clipboard sync, and the dynamic notch.
        </p>

        {/* Tab Switcher Pills */}
        <div className="mt-8 inline-flex max-w-full overflow-x-auto no-scrollbar p-1 sm:p-1.5 rounded-full bg-black/5 dark:bg-white/10 border border-black/5 dark:border-white/10 backdrop-blur-md">
          <button
            onClick={() => setActiveTab("mirror")}
            type="button"
            className={`flex items-center gap-1.5 sm:gap-2 px-3 sm:px-5 py-2 rounded-full text-xs sm:text-sm font-medium whitespace-nowrap transition-all ${
              activeTab === "mirror"
                ? "bg-white dark:bg-zinc-800 text-zinc-900 dark:text-white shadow-sm"
                : "text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-white"
            }`}
          >
            <Smartphone className="w-4 h-4 shrink-0" />
            <span><span className="hidden sm:inline">Screen </span>Mirroring</span>
          </button>

          <button
            onClick={() => setActiveTab("clipboard")}
            type="button"
            className={`flex items-center gap-1.5 sm:gap-2 px-3 sm:px-5 py-2 rounded-full text-xs sm:text-sm font-medium whitespace-nowrap transition-all ${
              activeTab === "clipboard"
                ? "bg-white dark:bg-zinc-800 text-zinc-900 dark:text-white shadow-sm"
                : "text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-white"
            }`}
          >
            <ClipboardCopy className="w-4 h-4 shrink-0" />
            <span><span className="hidden sm:inline">Universal </span>Clipboard</span>
          </button>

          <button
            onClick={() => setActiveTab("notch")}
            type="button"
            className={`flex items-center gap-1.5 sm:gap-2 px-3 sm:px-5 py-2 rounded-full text-xs sm:text-sm font-medium whitespace-nowrap transition-all ${
              activeTab === "notch"
                ? "bg-white dark:bg-zinc-800 text-zinc-900 dark:text-white shadow-sm"
                : "text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-white"
            }`}
          >
            <Laptop className="w-4 h-4 shrink-0" />
            <span><span className="hidden sm:inline">Dynamic </span>Notch Drop</span>
          </button>
        </div>
      </div>

      {/* Simulator Display Panel */}
      <div className="glass-panel rounded-3xl p-4 sm:p-8 lg:p-10 shadow-2xl relative overflow-hidden">
        {/* Background glow accent */}
        <div className="absolute top-0 right-0 w-80 h-80 bg-blue-500/10 rounded-full blur-3xl pointer-events-none -z-10" />

        <AnimatePresence mode="wait">
          {/* TAB 1: SCREEN MIRRORING SIMULATOR */}
          {activeTab === "mirror" && (
            <motion.div
              key="mirror"
              initial={{ opacity: 0, y: 15 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -15 }}
              transition={{ duration: 0.3 }}
              className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center"
            >
              <div className="lg:col-span-6 space-y-4 text-left">
                <span className="inline-block px-3 py-1 rounded-full text-xs font-semibold bg-blue-500/10 text-blue-600 dark:text-blue-400">
                  Sub-30ms Latency • Touch Injection
                </span>
                <h3 className="text-2xl sm:text-3xl font-bold tracking-tight text-zinc-900 dark:text-white">
                  Full Touch & Trackpad Control
                </h3>
                <p className="text-sm sm:text-base text-zinc-600 dark:text-zinc-400 leading-relaxed">
                  Click or drag anywhere on the simulated phone display. In AirLink, clicks and trackpad gestures translate into native Android accessibility input packets in real time at 60 Hz.
                </p>

                <div className="pt-2 grid grid-cols-2 gap-3">
                  <div className="p-3.5 rounded-2xl bg-black/[0.03] dark:bg-white/[0.05] border border-black/5 dark:border-white/5">
                    <div className="text-xs text-zinc-500 dark:text-zinc-400">Target Bitrate</div>
                    <div className="text-lg font-bold text-zinc-900 dark:text-white">18 Mbps</div>
                  </div>
                  <div className="p-3.5 rounded-2xl bg-black/[0.03] dark:bg-white/[0.05] border border-black/5 dark:border-white/5">
                    <div className="text-xs text-zinc-500 dark:text-zinc-400">Network Jitter</div>
                    <div className="text-lg font-bold text-emerald-500">&lt; 3 ms</div>
                  </div>
                </div>

                <div className="flex items-center gap-2 pt-2 text-xs text-zinc-500">
                  <MousePointer className="w-4 h-4 text-blue-500" />
                  <span>Click apps or navigation buttons on the phone to interact</span>
                </div>
              </div>

              {/* Phone Frame */}
              <div className="lg:col-span-6 flex justify-center">
                <div
                  onClick={handlePhoneTap}
                  className="relative w-64 sm:w-72 h-[480px] rounded-[2.5rem] p-3 bg-zinc-900 border-4 border-zinc-800 shadow-2xl cursor-pointer select-none overflow-hidden"
                >
                  {/* Phone Speaker & Camera punch hole */}
                  <div className="absolute top-4 left-1/2 -translate-x-1/2 w-4 h-4 rounded-full bg-zinc-950 border border-zinc-700/60 z-30" />

                  {/* Phone Screen Canvas */}
                  <div className="relative w-full h-full rounded-[2rem] bg-gradient-to-b from-slate-900 to-indigo-950 text-white flex flex-col justify-between p-4 overflow-hidden">
                    {/* Status bar */}
                    <div className="flex items-center justify-between text-[10px] text-zinc-300 font-medium px-2 pt-1 z-20">
                      <span>9:41</span>
                      <div className="flex items-center gap-1.5">
                        <Wifi className="w-3 h-3" />
                        <BatteryCharging className="w-3.5 h-3.5 text-emerald-400" />
                      </div>
                    </div>

                    {/* App Content */}
                    <div className="flex-1 flex flex-col items-center justify-center p-2 z-10 text-center">
                      {activeApp === "home" && (
                        <div className="grid grid-cols-3 gap-4 w-full">
                          {[
                            { name: "Camera", icon: Camera, color: "bg-rose-500" },
                            { name: "Chat", icon: MessageSquare, color: "bg-emerald-500" },
                            { name: "Browser", icon: Globe, color: "bg-amber-500" },
                            { name: "Settings", icon: Settings, color: "bg-zinc-600" },
                          ].map((app) => {
                            const Icon = app.icon;
                            return (
                              <button
                                key={app.name}
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setActiveApp(app.name.toLowerCase());
                                }}
                                className="flex flex-col items-center gap-1.5 group cursor-pointer"
                              >
                                <div
                                  className={`w-11 h-11 rounded-2xl ${app.color} flex items-center justify-center text-white shadow-lg group-hover:scale-110 transition-transform`}
                                >
                                  <Icon className="w-5 h-5" />
                                </div>
                                <span className="text-[10px] text-zinc-300">{app.name}</span>
                              </button>
                            );
                          })}
                        </div>
                      )}

                      {activeApp !== "home" && (
                        <div className="space-y-3">
                          <div className="inline-block p-3 rounded-2xl bg-white/10 backdrop-blur-md">
                            <Sparkles className="w-6 h-6 text-blue-400" />
                          </div>
                          <div className="text-sm font-semibold capitalize">{activeApp} Launched</div>
                          <p className="text-[11px] text-zinc-400">
                            Rendered at 60 FPS via custom Binary WebSocket frame stream.
                          </p>
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              setActiveApp("home");
                            }}
                            className="px-3 py-1 rounded-full bg-white/20 hover:bg-white/30 text-xs font-medium cursor-pointer"
                          >
                            Return to Home
                          </button>
                        </div>
                      )}
                    </div>

                    {/* Android Navigation Pill Bar */}
                    <div className="flex items-center justify-center pt-2 z-20">
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          setActiveApp("home");
                        }}
                        className="w-24 h-1.5 rounded-full bg-white/50 hover:bg-white cursor-pointer transition-colors"
                        title="Home Button"
                      />
                    </div>

                    {/* Tap Visualizer Waves */}
                    {taps.map((tap) => (
                      <span
                        key={tap.id}
                        style={{ left: tap.x, top: tap.y }}
                        className="absolute w-8 h-8 -ml-4 -mt-4 rounded-full border-2 border-blue-400 bg-blue-400/30 pointer-events-none animate-ping"
                      />
                    ))}
                  </div>
                </div>
              </div>
            </motion.div>
          )}

          {/* TAB 2: UNIVERSAL CLIPBOARD SIMULATOR */}
          {activeTab === "clipboard" && (
            <motion.div
              key="clipboard"
              initial={{ opacity: 0, y: 15 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -15 }}
              transition={{ duration: 0.3 }}
              className="space-y-8"
            >
              <div className="max-w-2xl mx-auto text-center space-y-2">
                <span className="inline-block px-3 py-1 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
                  AES-256-GCM Encrypted Payload
                </span>
                <h3 className="text-2xl sm:text-3xl font-bold tracking-tight text-zinc-900 dark:text-white">
                  Two-Way Universal Clipboard
                </h3>
                <p className="text-sm text-zinc-600 dark:text-zinc-400">
                  Copy on macOS, paste immediately on Android. Or copy on your phone and press Cmd+V on your Mac.
                </p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-6 relative">
                {/* Mac Side */}
                <div className="p-6 rounded-2xl bg-black/[0.02] dark:bg-white/[0.04] border border-black/5 dark:border-white/10 space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2 text-sm font-semibold text-zinc-900 dark:text-white">
                      <Laptop className="w-4 h-4 text-blue-500" />
                      <span>macOS Pasteboard</span>
                    </div>
                    <button
                      onClick={() => {
                        navigator.clipboard?.writeText(macClipText);
                        setCopiedMac(true);
                        setTimeout(() => setCopiedMac(false), 1500);
                      }}
                      className="text-xs text-zinc-500 hover:text-zinc-900 dark:hover:text-white flex items-center gap-1 cursor-pointer"
                    >
                      {copiedMac ? <Check className="w-3.5 h-3.5 text-emerald-500" /> : <ClipboardCopy className="w-3.5 h-3.5" />}
                      <span>{copiedMac ? "Copied" : "Copy"}</span>
                    </button>
                  </div>

                  <textarea
                    value={macClipText}
                    onChange={(e) => setMacClipText(e.target.value)}
                    rows={3}
                    className="w-full text-xs sm:text-sm p-3 rounded-xl bg-white dark:bg-zinc-900 border border-black/10 dark:border-white/10 text-zinc-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono resize-none"
                  />

                  <button
                    onClick={() => handleSyncClipboard("mac")}
                    disabled={isSyncing}
                    className="w-full flex items-center justify-center gap-2 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs sm:text-sm font-medium transition-all shadow-md active:scale-98 cursor-pointer disabled:opacity-50"
                  >
                    <Send className="w-3.5 h-3.5" />
                    <span>{isSyncing ? "Syncing over LAN..." : "Beam to Android Clipboard"}</span>
                  </button>
                </div>

                {/* Android Side */}
                <div className="p-6 rounded-2xl bg-black/[0.02] dark:bg-white/[0.04] border border-black/5 dark:border-white/10 space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2 text-sm font-semibold text-zinc-900 dark:text-white">
                      <Smartphone className="w-4 h-4 text-emerald-500" />
                      <span>Android Clipboard Manager</span>
                    </div>
                    <button
                      onClick={() => {
                        navigator.clipboard?.writeText(phoneClipText);
                        setCopiedPhone(true);
                        setTimeout(() => setCopiedPhone(false), 1500);
                      }}
                      className="text-xs text-zinc-500 hover:text-zinc-900 dark:hover:text-white flex items-center gap-1 cursor-pointer"
                    >
                      {copiedPhone ? <Check className="w-3.5 h-3.5 text-emerald-500" /> : <ClipboardCopy className="w-3.5 h-3.5" />}
                      <span>{copiedPhone ? "Copied" : "Copy"}</span>
                    </button>
                  </div>

                  <textarea
                    value={phoneClipText}
                    onChange={(e) => setPhoneClipText(e.target.value)}
                    rows={3}
                    className="w-full text-xs sm:text-sm p-3 rounded-xl bg-white dark:bg-zinc-900 border border-black/10 dark:border-white/10 text-zinc-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-emerald-500 font-mono resize-none"
                  />

                  <button
                    onClick={() => handleSyncClipboard("phone")}
                    disabled={isSyncing}
                    className="w-full flex items-center justify-center gap-2 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs sm:text-sm font-medium transition-all shadow-md active:scale-98 cursor-pointer disabled:opacity-50"
                  >
                    <Send className="w-3.5 h-3.5" />
                    <span>{isSyncing ? "Syncing over LAN..." : "Beam to Mac Clipboard"}</span>
                  </button>
                </div>
              </div>
            </motion.div>
          )}

          {/* TAB 3: NOTCH DROP SIMULATOR */}
          {activeTab === "notch" && (
            <motion.div
              key="notch"
              initial={{ opacity: 0, y: 15 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -15 }}
              transition={{ duration: 0.3 }}
              className="space-y-6 text-center"
            >
              <div className="max-w-xl mx-auto space-y-2">
                <span className="inline-block px-3 py-1 rounded-full text-xs font-semibold bg-purple-500/10 text-purple-600 dark:text-purple-400">
                  macOS Native Dynamic Island
                </span>
                <h3 className="text-2xl sm:text-3xl font-bold tracking-tight text-zinc-900 dark:text-white">
                  MacBook Notch Telemetry
                </h3>
                <p className="text-sm text-zinc-600 dark:text-zinc-400">
                  AirLink docks into the MacBook camera notch, displaying live Android battery telemetry and quick connection controls.
                </p>
              </div>

              {/* Simulated MacBook Display Top with Notch */}
              <div className="relative max-w-2xl mx-auto h-72 sm:h-80 rounded-2xl bg-gradient-to-b from-zinc-900 via-slate-900 to-indigo-950 p-3 sm:p-4 border border-zinc-700/80 shadow-2xl flex flex-col justify-between overflow-hidden">
                {/* Menu bar */}
                <div className="flex items-center justify-between text-xs text-zinc-300 px-2 sm:px-3 py-1.5 border-b border-white/5 select-none relative z-10">
                  <div className="flex items-center gap-2 sm:gap-4 font-semibold shrink-0">
                    <span className="text-sm"></span>
                    <span className="font-medium text-white">AirLink</span>
                    <span className="font-normal text-zinc-400 hidden md:inline">File</span>
                    <span className="font-normal text-zinc-400 hidden md:inline">View</span>
                  </div>
                  <div className="flex items-center gap-2 sm:gap-3 text-[11px] shrink-0 font-mono">
                    <span className="text-emerald-400 hidden sm:flex items-center gap-1">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
                      Pixel 8 Pro • Connected
                    </span>
                    <span className="text-emerald-400 sm:hidden flex items-center gap-1">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
                    </span>
                    <span className="text-zinc-400 whitespace-nowrap">
                      <span className="hidden sm:inline">Fri Oct 24 </span>1:30 PM
                    </span>
                  </div>
                </div>

                {/* The Notch Element */}
                <div className="absolute top-0 left-1/2 -translate-x-1/2 flex flex-col items-center z-30 w-full max-w-full px-2 pointer-events-auto">
                  <motion.div
                    animate={{
                      width: notchExpanded ? "min(340px, 94%)" : "156px",
                      height: notchExpanded ? 112 : 32,
                    }}
                    transition={{ type: "spring", stiffness: 320, damping: 26 }}
                    onClick={() => setNotchExpanded(!notchExpanded)}
                    className="bg-black text-white rounded-b-2xl sm:rounded-b-3xl px-3 sm:px-4 py-2 cursor-pointer shadow-2xl border-x border-b border-white/10 flex flex-col justify-between mx-auto select-none"
                  >
                    {/* Compact state */}
                    <div className="flex items-center justify-between w-full">
                      <div className="flex items-center gap-1 text-[11px] sm:text-xs text-emerald-400 font-mono">
                        <BatteryCharging className="w-3.5 h-3.5 sm:w-4 sm:h-4" />
                        <span>{batteryLevel}%</span>
                      </div>
                      <div className="w-2.5 h-2.5 rounded-full bg-zinc-800 border border-zinc-700 mx-2 shrink-0" />
                      <div className="text-[10px] sm:text-[11px] text-zinc-400 font-mono">14 ms</div>
                    </div>

                    {/* Expanded details */}
                    {notchExpanded && (
                      <motion.div
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        className="pt-2 border-t border-white/10 flex items-center justify-between text-xs gap-2"
                      >
                        <div className="text-left space-y-0.5 min-w-0">
                          <div className="font-semibold text-white truncate text-xs sm:text-sm">Google Pixel 8 Pro</div>
                          <div className="text-[10px] sm:text-[11px] text-zinc-400 truncate">
                            {isCharging ? "Fast Charging (27W)" : "Discharging on Battery"}
                          </div>
                        </div>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            setIsCharging(!isCharging);
                            setBatteryLevel((prev) => (prev > 95 ? 45 : prev + 10));
                          }}
                          className="px-2.5 py-1 rounded-full bg-white/20 hover:bg-white/30 text-[10px] sm:text-[11px] font-medium transition-colors shrink-0 cursor-pointer active:scale-95"
                        >
                          Simulate Chg
                        </button>
                      </motion.div>
                    )}
                  </motion.div>
                  <span className="text-[10px] text-zinc-400 mt-2 select-none">
                    Click notch to {notchExpanded ? "collapse" : "expand"}
                  </span>
                </div>

                <div className="text-center text-xs text-zinc-400 pb-2 relative z-10 px-2">
                  Live macOS desktop overlay with zero CPU overhead.
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </section>
  );
}
