"use client";

import Image from "next/image";
import { motion, Variants } from "framer-motion";
import { AppleLogo } from "./Icons";
import {
  MousePointer2,
  Copy,
  Zap,
  PhoneCall,
  PhoneOff,
  MessageSquare,
  ShieldCheck,
  Lock,
  BellRing,
} from "lucide-react";

export default function BentoFeatures() {
  const containerVariants: Variants = {
    hidden: { opacity: 0 },
    visible: {
      opacity: 1,
      transition: {
        staggerChildren: 0.12,
      },
    },
  };

  const cardVariants: Variants = {
    hidden: { opacity: 0, y: 24, scale: 0.98 },
    visible: {
      opacity: 1,
      y: 0,
      scale: 1,
      transition: { duration: 0.6, ease: [0.16, 1, 0.3, 1] },
    },
  };

  return (
    <section id="features" className="py-16 sm:py-24 px-4 max-w-6xl mx-auto">
      {/* Exact Glance Section Header with Authentic Apple Logo and Framer Motion */}
      <motion.div
        initial={{ opacity: 0, y: 24 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true, margin: "-60px" }}
        transition={{ duration: 0.7, ease: [0.16, 1, 0.3, 1] }}
        className="max-w-3xl mb-12 sm:mb-16"
      >
        <h2 className="text-4xl sm:text-6xl lg:text-7xl font-bold tracking-tight leading-[1.08]">
          <span className="text-zinc-400 dark:text-zinc-600 font-semibold">With the </span>
          <span className="text-zinc-950 dark:text-white font-extrabold">speed</span>
          <br />
          <span className="text-zinc-400 dark:text-zinc-600 font-semibold">and </span>
          <span className="text-zinc-950 dark:text-white font-extrabold">
            fluidity of your{" "}
            <span className="inline-flex items-center">
              <AppleLogo className="w-[0.80em] h-[0.80em] inline -mt-1 sm:-mt-2 mr-0.5" />
              Mac.
            </span>
          </span>
        </h2>
      </motion.div>

      {/* 2-Column Bento Grid with Staggered Framer Motion */}
      <motion.div
        variants={containerVariants}
        initial="hidden"
        whileInView="visible"
        viewport={{ once: true, margin: "-60px" }}
        className="grid grid-cols-1 lg:grid-cols-12 gap-5 sm:gap-6 items-stretch"
      >
        {/* LEFT COLUMN (7 cols on lg) */}
        <div className="lg:col-span-7 flex flex-col gap-5 sm:gap-6">
          {/* Card 1 (Top Left): Menu Bar & Notch */}
          <motion.div
            variants={cardVariants}
            whileHover={{ y: -3 }}
            className="p-8 sm:p-10 rounded-[2.2rem] bg-[#f4f4f7] dark:bg-zinc-900 border border-black/[0.03] dark:border-white/5 flex flex-col justify-between gap-6 transition-shadow hover:shadow-lg"
          >
            <div className="flex flex-col sm:flex-row items-center justify-around gap-6">
              {/* Menu Bar Icon Item */}
              <div className="flex flex-col items-center gap-3">
                <div className="w-20 h-20 sm:w-24 sm:h-24 rounded-[1.8rem] bg-black p-2.5 flex items-center justify-center shadow-md">
                  <Image
                    src="/app-icon.png"
                    alt="AirLink Menu Bar App"
                    width={80}
                    height={80}
                    className="w-full h-full object-contain rounded-xl"
                  />
                </div>
                <span className="text-xs sm:text-sm font-semibold text-zinc-800 dark:text-zinc-200">
                  Menu Bar App
                </span>
              </div>

              {/* Dynamic Notch Call Alert Item */}
              <div className="flex flex-col items-center gap-3">
                <div className="flex flex-col gap-2.5 justify-center w-40 sm:w-48 py-2">
                  {/* Notch Pill Alert */}
                  <div className="h-9 px-3.5 rounded-full bg-black text-white flex items-center justify-between shadow-md">
                    <div className="flex items-center gap-2">
                      <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
                      <span className="text-[11px] font-medium">Incoming Call</span>
                    </div>
                    <BellRing className="w-3.5 h-3.5 text-emerald-400" />
                  </div>

                  {/* Telemetry Pill */}
                  <div className="h-9 px-3.5 rounded-full bg-black text-white flex items-center justify-between shadow-md">
                    <span className="text-[11px] font-mono text-zinc-300">Pixel 8 Pro</span>
                    <span className="text-[11px] text-emerald-400 font-mono">88% ⚡</span>
                  </div>
                </div>
                <span className="text-xs sm:text-sm font-semibold text-zinc-800 dark:text-zinc-200">
                  Notch Alerts
                </span>
              </div>
            </div>

            <div className="text-center sm:text-left pt-2 border-t border-black/5 dark:border-white/5">
              <h3 className="text-base font-bold text-zinc-900 dark:text-white">
                Menu Bar & Notch
              </h3>
              <p className="mt-1 text-xs sm:text-sm text-zinc-600 dark:text-zinc-400 leading-relaxed">
                AirLink lives quietly in your Mac menu bar and utilizes the MacBook Notch to display incoming call alerts and phone telemetry without interrupting your workflow.
              </p>
            </div>
          </motion.div>

          {/* Card 2 (Middle Left): 3 Pillars */}
          <motion.div
            variants={cardVariants}
            whileHover={{ y: -3 }}
            className="p-8 sm:p-10 rounded-[2.2rem] bg-[#f4f4f7] dark:bg-zinc-900 border border-black/[0.03] dark:border-white/5 grid grid-cols-1 sm:grid-cols-3 gap-6 sm:gap-4 text-center transition-shadow hover:shadow-lg"
          >
            {/* Pillar 1: Full Touch Control */}
            <div className="flex flex-col items-center gap-3">
              <div className="text-zinc-900 dark:text-white">
                <MousePointer2 className="w-7 h-7 sm:w-8 sm:h-8 stroke-[2.2]" />
              </div>
              <div className="space-y-1">
                <span className="text-xs sm:text-sm font-bold text-zinc-900 dark:text-white block leading-tight">
                  Full Touch
                  <br />
                  Control
                </span>
                <p className="text-[11px] text-zinc-500 dark:text-zinc-400 leading-tight">
                  Click, swipe, and type on your phone directly from your Mac trackpad.
                </p>
              </div>
            </div>

            {/* Pillar 2: Universal Clipboard */}
            <div className="flex flex-col items-center gap-3">
              <div className="text-zinc-900 dark:text-white">
                <Copy className="w-7 h-7 sm:w-8 sm:h-8 stroke-[2.2]" />
              </div>
              <div className="space-y-1">
                <span className="text-xs sm:text-sm font-bold text-zinc-900 dark:text-white block leading-tight">
                  Universal
                  <br />
                  Clipboard
                </span>
                <p className="text-[11px] text-zinc-500 dark:text-zinc-400 leading-tight">
                  Text copied on your Mac is pasted instantly on your Android phone.
                </p>
              </div>
            </div>

            {/* Pillar 3: Binary WebSocket Stream */}
            <div className="flex flex-col items-center gap-3">
              <div className="text-zinc-900 dark:text-white">
                <Zap className="w-7 h-7 sm:w-8 sm:h-8 fill-zinc-900 dark:fill-white stroke-none" />
              </div>
              <div className="space-y-1">
                <span className="text-xs sm:text-sm font-bold text-zinc-900 dark:text-white block leading-tight">
                  Binary WebSocket
                  <br />
                  Stream
                </span>
                <p className="text-[11px] text-zinc-500 dark:text-zinc-400 leading-tight">
                  Custom ultra-fast local network tech for true zero-latency mirroring.
                </p>
              </div>
            </div>
          </motion.div>
        </div>

        {/* RIGHT COLUMN (5 cols on lg) - Smart Call Handling Card */}
        <motion.div
          variants={cardVariants}
          whileHover={{ y: -3 }}
          className="lg:col-span-5 relative rounded-[2.2rem] overflow-hidden min-h-[460px] lg:min-h-full flex flex-col justify-between p-7 sm:p-9 bg-gradient-to-b from-slate-900 via-zinc-900 to-zinc-950 text-white shadow-lg border border-black/[0.03] dark:border-white/5 transition-shadow hover:shadow-2xl"
        >
          {/* Subtle background glow */}
          <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-blue-600/25 via-emerald-950/20 to-transparent pointer-events-none" />

          {/* Incoming Call Notification Mockup */}
          <div className="relative z-10 flex flex-col items-center text-center mt-2 w-full">
            <div className="w-12 h-12 rounded-2xl bg-black/60 backdrop-blur-md border border-white/20 p-2 flex items-center justify-center shadow-lg">
              <PhoneCall className="w-6 h-6 text-emerald-400 animate-pulse" />
            </div>

            <div className="mt-3 text-xs font-medium text-emerald-400 tracking-wide uppercase">
              Incoming Android Call
            </div>
            <div className="text-2xl sm:text-3xl font-extrabold tracking-tight text-white mt-1">
              Sarah Jenkins
            </div>
            <div className="text-xs text-zinc-400 font-mono mt-0.5">+1 (555) 234-8901</div>

            {/* Smart Action Buttons */}
            <div className="mt-6 w-full max-w-xs space-y-2.5">
              <div className="grid grid-cols-2 gap-2.5">
                <button
                  type="button"
                  className="flex items-center justify-center gap-1.5 py-2.5 px-3 rounded-xl bg-rose-600/90 hover:bg-rose-600 text-white text-xs font-semibold shadow-md transition-all active:scale-95 cursor-pointer"
                >
                  <PhoneOff className="w-3.5 h-3.5" />
                  <span>Decline</span>
                </button>
                <button
                  type="button"
                  className="flex items-center justify-center gap-1.5 py-2.5 px-3 rounded-xl bg-emerald-600/90 hover:bg-emerald-600 text-white text-xs font-semibold shadow-md transition-all active:scale-95 cursor-pointer"
                >
                  <PhoneCall className="w-3.5 h-3.5" />
                  <span>Answer</span>
                </button>
              </div>

              {/* Automatic Quick SMS Reply Pill */}
              <div className="p-2.5 rounded-xl bg-white/10 backdrop-blur-md border border-white/15 text-[11px] text-zinc-200 flex items-center justify-between">
                <div className="flex items-center gap-1.5 truncate">
                  <MessageSquare className="w-3.5 h-3.5 text-blue-400 shrink-0" />
                  <span className="truncate">&quot;Can&apos;t talk now, calling you later!&quot;</span>
                </div>
                <span className="text-[10px] text-blue-400 font-semibold uppercase shrink-0 ml-1">
                  Auto SMS
                </span>
              </div>
            </div>
          </div>

          {/* Bottom Card Footer */}
          <div className="relative z-10 space-y-1.5 mt-6 pt-4 border-t border-white/10">
            <h3 className="text-lg sm:text-xl font-bold text-white">Smart Call Handling</h3>
            <p className="text-xs text-zinc-300 leading-relaxed font-normal">
              Decline Android phone calls and send automatic SMS replies right from your Mac screen without ever picking up your phone.
            </p>
          </div>
        </motion.div>

        {/* BOTTOM ROW: Native App & Zero Cloud Privacy */}
        {/* Card 4: Native App */}
        <motion.div
          variants={cardVariants}
          whileHover={{ y: -3 }}
          className="lg:col-span-5 rounded-[2.2rem] overflow-hidden bg-gradient-to-tr from-slate-950 via-zinc-900 to-indigo-950 text-white p-7 sm:p-9 relative shadow-lg border border-black/[0.03] dark:border-white/5 flex flex-col justify-between min-h-[300px] transition-shadow hover:shadow-2xl"
        >
          <div className="relative z-10">
            <h3 className="text-2xl sm:text-3xl font-extrabold text-white leading-tight">
              Native
              <br />
              App
            </h3>
            <p className="text-xs text-zinc-400 mt-2 max-w-[200px]">
              Swift-native macOS client with low CPU footprint and Apple Design System ergonomics.
            </p>
          </div>

          {/* Angled macOS Preferences Window */}
          <div className="absolute -right-4 -bottom-6 w-72 sm:w-80 rounded-2xl bg-zinc-900/95 border border-white/10 shadow-2xl p-3.5 text-xs text-zinc-300 space-y-2.5 backdrop-blur-xl rotate-[-3deg] hover:rotate-0 transition-transform">
            <div className="flex items-center gap-1.5 pb-2 border-b border-white/10">
              <span className="w-2.5 h-2.5 rounded-full bg-rose-500" />
              <span className="w-2.5 h-2.5 rounded-full bg-amber-500" />
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-500" />
              <span className="text-[10px] text-zinc-400 ml-2">AirLink Preferences</span>
            </div>

            <div className="grid grid-cols-3 gap-2">
              <div className="space-y-1 text-[11px] text-zinc-400 border-r border-white/10 pr-2">
                <div className="text-white font-semibold">General</div>
                <div>Your Devices</div>
                <div>Mirroring</div>
                <div>About</div>
              </div>
              <div className="col-span-2 space-y-2 text-[10px]">
                <div className="flex items-center justify-between">
                  <span>Launch at login</span>
                  <span className="w-6 h-3.5 rounded-full bg-blue-600 inline-block" />
                </div>
                <div className="flex items-center justify-between">
                  <span>AES-256-GCM</span>
                  <span className="w-6 h-3.5 rounded-full bg-blue-600 inline-block" />
                </div>
                <div className="flex items-center justify-between">
                  <span>Notch Alert Drop</span>
                  <span className="w-6 h-3.5 rounded-full bg-blue-600 inline-block" />
                </div>
              </div>
            </div>
          </div>
        </motion.div>

        {/* Card 5: Zero Cloud Privacy */}
        <motion.div
          variants={cardVariants}
          whileHover={{ y: -3 }}
          className="lg:col-span-7 rounded-[2.2rem] bg-[#f4f4f7] dark:bg-zinc-900 border border-black/[0.03] dark:border-white/5 p-7 sm:p-9 grid grid-cols-1 md:grid-cols-2 gap-6 items-center transition-shadow hover:shadow-lg"
        >
          <div className="space-y-3">
            <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-[11px] font-semibold bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20">
              <ShieldCheck className="w-3.5 h-3.5" />
              <span>100% Local Subnet</span>
            </div>
            <h3 className="text-2xl sm:text-3xl font-extrabold text-zinc-950 dark:text-white">
              Zero Cloud Privacy
            </h3>
            <p className="text-xs sm:text-sm text-zinc-600 dark:text-zinc-400 leading-relaxed">
              All screen streaming packets, touch gestures, and clipboard snippets are strictly encrypted with <strong className="text-zinc-900 dark:text-white">hardware AES-256-GCM</strong> and never leave your local Wi-Fi router.
            </p>
            <p className="text-xs sm:text-sm text-zinc-600 dark:text-zinc-400 leading-relaxed">
              No cloud servers, no intermediate relays, and zero telemetry data collected.
            </p>
          </div>

          {/* Privacy Security Indicators */}
          <div className="space-y-2.5">
            {[
              { label: "Local Wi-Fi Encryption", status: "AES-256-GCM Authenticated", color: "bg-emerald-500" },
              { label: "External Cloud Relays", status: "0 KB Transmitted (Blocked)", color: "bg-emerald-500" },
              { label: "Telemetry & Tracking", status: "None (Zero Analytics SDKs)", color: "bg-emerald-500" },
            ].map((item) => (
              <div
                key={item.label}
                className="p-3.5 rounded-2xl bg-zinc-900 text-white shadow-md space-y-1.5"
              >
                <div className="flex items-center justify-between text-xs font-semibold text-white">
                  <span>{item.label}</span>
                  <Lock className="w-3.5 h-3.5 text-emerald-400" />
                </div>
                <div className="text-[11px] text-emerald-400 font-mono">{item.status}</div>
                {/* 18-bar security status visualizer */}
                <div className="flex items-center gap-1 pt-0.5">
                  {[...Array(18)].map((_, barIdx) => (
                    <span
                      key={barIdx}
                      className="w-1 h-2.5 rounded-full bg-emerald-400 opacity-90"
                    />
                  ))}
                </div>
              </div>
            ))}
          </div>
        </motion.div>
      </motion.div>
    </section>
  );
}
