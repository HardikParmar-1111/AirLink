"use client";

import { motion } from "framer-motion";
import {
  ShieldAlert,
  Smartphone,
  CheckCircle2,
  AlertCircle,
  ExternalLink,
  ShieldCheck,
  ChevronRight,
  MoreVertical,
} from "lucide-react";
import { AppleLogo, GithubIcon } from "./Icons";

export default function InstallationGuide() {
  const macSteps = [
    {
      num: "01",
      title: "Move to Applications",
      desc: "Download the AirLink disk image (.dmg) and drag the AirLink icon into your Mac's Applications folder.",
      highlight: "/Applications",
    },
    {
      num: "02",
      title: "Initial Gatekeeper Prompt",
      desc: "Double-click to open AirLink. macOS will display an alert stating that the developer cannot be verified. Click \"OK\" to dismiss.",
      highlight: "Cannot be verified",
    },
    {
      num: "03",
      title: "Open Privacy & Security",
      desc: "Open your Mac's System Settings, then navigate to Privacy & Security in the left sidebar.",
      highlight: "System Settings > Privacy & Security",
    },
    {
      num: "04",
      title: "Click \"Open Anyway\"",
      desc: "Scroll down to the Security section at the bottom. Click Open Anyway next to AirLink, then confirm with your password or Touch ID.",
      highlight: "Open Anyway",
    },
  ];

  const androidSteps = [
    {
      num: "01",
      title: "Install APK Package",
      desc: "Download the AirLink .apk. When prompted by your browser or file manager, allow \"Install Unknown Apps\" and proceed with installation.",
      highlight: "Install Unknown Apps",
    },
    {
      num: "02",
      title: "Open App Settings",
      desc: "Open your phone's system Settings > Apps > AirLink (or long-press the AirLink icon on your launcher and tap App Info ⓘ).",
      highlight: "Settings > Apps > AirLink",
    },
    {
      num: "03",
      title: "Allow Restricted Settings",
      desc: "Tap the three vertical dots (⋮) in the top right corner of the screen and tap Allow restricted settings, then confirm with your PIN or fingerprint.",
      highlight: "⋮ > Allow restricted settings",
    },
    {
      num: "04",
      title: "Grant Accessibility Permissions",
      desc: "Launch AirLink and toggle on the Accessibility Service when prompted. This is strictly used for zero-latency remote trackpad and touch injections.",
      highlight: "Accessibility Service",
    },
  ];

  return (
    <section id="installation" className="py-20 sm:py-28 px-4 max-w-6xl mx-auto">
      {/* Section Header */}
      <motion.div
        initial={{ opacity: 0, y: 24 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true, margin: "-60px" }}
        transition={{ duration: 0.7, ease: [0.16, 1, 0.3, 1] }}
        className="text-center max-w-3xl mx-auto mb-14 sm:mb-20 space-y-4"
      >
        <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-500/20">
          <ShieldAlert className="w-3.5 h-3.5" />
          <span>First-Time Setup Assistance</span>
        </div>

        <h2 className="text-3xl sm:text-5xl font-bold tracking-tight text-zinc-900 dark:text-white">
          Installation & Security Guide
        </h2>

        <p className="text-sm sm:text-base text-zinc-600 dark:text-zinc-400 max-w-2xl mx-auto leading-relaxed">
          Because AirLink is completely free and open-source without costly commercial certificates, modern operating systems trigger standard first-time security barriers. Follow these simple steps to bypass them in under 60 seconds.
        </p>
      </motion.div>

      {/* 2-Column Cards Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 items-stretch">
        {/* macOS Guide Card */}
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-60px" }}
          transition={{ duration: 0.7, ease: [0.16, 1, 0.3, 1] }}
          className="glance-card p-6 sm:p-10 flex flex-col justify-between relative overflow-hidden group hover:border-black/10 dark:hover:border-white/15 transition-all shadow-xl"
        >
          {/* Subtle Top-Right Corner Glow */}
          <div className="absolute top-0 right-0 w-64 h-64 bg-blue-500/10 rounded-full blur-3xl pointer-events-none -z-0" />

          <div className="relative z-10 space-y-6">
            {/* Card Platform Header */}
            <div className="flex items-start justify-between gap-4 pb-6 border-b border-black/5 dark:border-white/10">
              <div className="flex items-center gap-3.5">
                <div className="w-12 h-12 rounded-2xl bg-zinc-900 text-white dark:bg-white/10 dark:text-white flex items-center justify-center p-2.5 shadow-md">
                  <AppleLogo className="w-full h-full" />
                </div>
                <div>
                  <h3 className="text-xl font-bold text-zinc-900 dark:text-white flex items-center gap-2">
                    macOS Gatekeeper
                  </h3>
                  <p className="text-xs text-zinc-500 dark:text-zinc-400 mt-0.5">
                    For macOS Ventura, Sonoma, Sequoia & newer
                  </p>
                </div>
              </div>

              <span className="px-2.5 py-1 rounded-full text-[11px] font-medium bg-blue-500/10 text-blue-600 dark:text-blue-400 border border-blue-500/20 shrink-0">
                macOS 13.0+
              </span>
            </div>

            {/* Step-by-Step List */}
            <div className="space-y-4 pt-2">
              {macSteps.map((step) => (
                <div
                  key={step.num}
                  className="p-4 rounded-2xl bg-zinc-50 dark:bg-zinc-900/60 border border-black/5 dark:border-white/5 space-y-2 hover:bg-zinc-100/70 dark:hover:bg-zinc-800/50 transition-colors"
                >
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2.5">
                      <span className="w-6 h-6 rounded-lg bg-blue-500/10 text-blue-600 dark:text-blue-400 font-mono text-xs font-bold flex items-center justify-center">
                        {step.num}
                      </span>
                      <h4 className="text-sm font-semibold text-zinc-900 dark:text-white">
                        {step.title}
                      </h4>
                    </div>

                    <span className="text-[11px] font-mono px-2 py-0.5 rounded-md bg-black/5 dark:bg-white/5 text-zinc-600 dark:text-zinc-400 border border-black/5 dark:border-white/5 hidden sm:inline-block">
                      {step.highlight}
                    </span>
                  </div>

                  <p className="text-xs text-zinc-600 dark:text-zinc-400 leading-relaxed pl-8">
                    {step.desc}
                  </p>
                </div>
              ))}
            </div>
          </div>

          {/* Card Bottom Note */}
          <div className="relative z-10 mt-8 pt-4 border-t border-black/5 dark:border-white/10 flex items-center gap-2 text-xs text-zinc-500 dark:text-zinc-400">
            <CheckCircle2 className="w-4 h-4 text-blue-500 shrink-0" />
            <span>Only required once. Subsequent launches open instantaneously.</span>
          </div>
        </motion.div>

        {/* Android Guide Card */}
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-60px" }}
          transition={{ duration: 0.7, delay: 0.1, ease: [0.16, 1, 0.3, 1] }}
          className="glance-card p-6 sm:p-10 flex flex-col justify-between relative overflow-hidden group hover:border-black/10 dark:hover:border-white/15 transition-all shadow-xl"
        >
          {/* Subtle Top-Right Corner Glow */}
          <div className="absolute top-0 right-0 w-64 h-64 bg-emerald-500/10 rounded-full blur-3xl pointer-events-none -z-0" />

          <div className="relative z-10 space-y-6">
            {/* Card Platform Header */}
            <div className="flex items-start justify-between gap-4 pb-6 border-b border-black/5 dark:border-white/10">
              <div className="flex items-center gap-3.5">
                <div className="w-12 h-12 rounded-2xl bg-emerald-500/10 text-emerald-500 flex items-center justify-center shadow-md">
                  <Smartphone className="w-6 h-6" />
                </div>
                <div>
                  <h3 className="text-xl font-bold text-zinc-900 dark:text-white flex items-center gap-2">
                    Android Restricted Settings
                  </h3>
                  <p className="text-xs text-zinc-500 dark:text-zinc-400 mt-0.5">
                    For Android 13, 14, 15+ (One UI, Pixel, OxygenOS)
                  </p>
                </div>
              </div>

              <span className="px-2.5 py-1 rounded-full text-[11px] font-medium bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20 shrink-0">
                Android 10+
              </span>
            </div>

            {/* Step-by-Step List */}
            <div className="space-y-4 pt-2">
              {androidSteps.map((step) => (
                <div
                  key={step.num}
                  className="p-4 rounded-2xl bg-zinc-50 dark:bg-zinc-900/60 border border-black/5 dark:border-white/5 space-y-2 hover:bg-zinc-100/70 dark:hover:bg-zinc-800/50 transition-colors"
                >
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2.5">
                      <span className="w-6 h-6 rounded-lg bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 font-mono text-xs font-bold flex items-center justify-center">
                        {step.num}
                      </span>
                      <h4 className="text-sm font-semibold text-zinc-900 dark:text-white">
                        {step.title}
                      </h4>
                    </div>

                    <span className="text-[11px] font-mono px-2 py-0.5 rounded-md bg-black/5 dark:bg-white/5 text-zinc-600 dark:text-zinc-400 border border-black/5 dark:border-white/5 hidden sm:inline-block">
                      {step.highlight}
                    </span>
                  </div>

                  <p className="text-xs text-zinc-600 dark:text-zinc-400 leading-relaxed pl-8">
                    {step.desc}
                  </p>
                </div>
              ))}
            </div>
          </div>

          {/* Card Bottom Note */}
          <div className="relative z-10 mt-8 pt-4 border-t border-black/5 dark:border-white/10 flex items-center gap-2 text-xs text-zinc-500 dark:text-zinc-400">
            <CheckCircle2 className="w-4 h-4 text-emerald-500 shrink-0" />
            <span>Accessibility is strictly used for touch and cursor injection over local network.</span>
          </div>
        </motion.div>
      </div>

      {/* Open Source Transparency Callout Banner */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true, margin: "-40px" }}
        transition={{ duration: 0.6, delay: 0.2 }}
        className="mt-8 p-6 sm:p-7 rounded-3xl bg-zinc-900 text-zinc-300 border border-zinc-800 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 shadow-xl"
      >
        <div className="flex items-start sm:items-center gap-3.5">
          <div className="w-10 h-10 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center shrink-0 mt-0.5 sm:mt-0">
            <ShieldCheck className="w-5 h-5" />
          </div>
          <div>
            <h4 className="text-sm font-semibold text-white">
              Why does this happen?
            </h4>
            <p className="text-xs text-zinc-400 mt-0.5 leading-relaxed">
              Commercial Apple Developer and Play Store signing certificates cost hundreds of dollars yearly. AirLink chooses to stay 100% free and open-source forever with zero data tracking.
            </p>
          </div>
        </div>

        <a
          href="https://github.com/HardikParmar-1111/AirLink"
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-white/10 hover:bg-white/15 text-xs font-semibold text-white transition-all shrink-0 active:scale-95 border border-white/10"
        >
          <GithubIcon className="w-3.5 h-3.5" />
          <span>Verify Source on GitHub</span>
          <ChevronRight className="w-3.5 h-3.5 opacity-60" />
        </a>
      </motion.div>
    </section>
  );
}
