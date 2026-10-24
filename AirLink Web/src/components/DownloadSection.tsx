"use client";

import { useState } from "react";
import { motion } from "framer-motion";
import { Smartphone, Terminal, Check, Copy, Download, ShieldCheck } from "lucide-react";
import { AppleLogo, GithubIcon } from "./Icons";

export default function DownloadSection() {
  const [copiedBrew, setCopiedBrew] = useState(false);

  const copyBrewCommand = () => {
    navigator.clipboard?.writeText("brew install --cask airlink");
    setCopiedBrew(true);
    setTimeout(() => setCopiedBrew(false), 2000);
  };

  return (
    <section id="download" className="py-20 sm:py-28 px-4 max-w-5xl mx-auto">
      <motion.div
        initial={{ opacity: 0, y: 30 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true, margin: "-80px" }}
        transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1] }}
        className="relative rounded-[2.5rem] p-8 sm:p-14 bg-gradient-to-b from-zinc-900 to-zinc-950 text-white shadow-2xl border border-zinc-800 overflow-hidden"
      >
        {/* Glow orb */}
        <div className="absolute top-0 right-1/4 w-96 h-96 bg-blue-600/20 rounded-full blur-3xl pointer-events-none -z-0" />
        <div className="absolute bottom-0 left-1/4 w-96 h-96 bg-indigo-600/20 rounded-full blur-3xl pointer-events-none -z-0" />

        <div className="relative z-10 text-center max-w-2xl mx-auto space-y-4">
          <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-white/10 text-zinc-200 border border-white/15">
            <Download className="w-3.5 h-3.5" />
            <span>Ready for Immediate Deployment</span>
          </div>
          <h2 className="text-3xl sm:text-5xl font-bold tracking-tight text-white">
            Get AirLink Today
          </h2>
          <p className="text-sm sm:text-base text-zinc-400">
            Completely free, open-source, and offline. Download the native macOS app and Android client below.
          </p>
        </div>

        {/* 2 Main Platform Cards */}
        <div className="relative z-10 mt-12 grid grid-cols-1 md:grid-cols-2 gap-6">
          {/* macOS Card */}
          <motion.div
            whileHover={{ y: -4 }}
            className="p-6 sm:p-8 rounded-[2rem] bg-white/[0.04] border border-white/10 hover:border-white/20 transition-all flex flex-col justify-between"
          >
            <div className="space-y-4">
              <div className="w-12 h-12 rounded-2xl bg-white/10 flex items-center justify-center text-white p-2.5">
                <AppleLogo className="w-full h-full" />
              </div>
              <div>
                <h3 className="text-xl font-bold text-white">macOS Universal Binary</h3>
                <p className="text-xs text-zinc-400 mt-1">
                  Optimized for Apple Silicon (M1, M2, M3, M4) & Intel Macs running macOS 13.0+.
                </p>
              </div>

              <div className="space-y-1.5 text-xs text-zinc-300">
                <div className="flex items-center gap-2">
                  <ShieldCheck className="w-4 h-4 text-emerald-400" />
                  <span>Notarized & Code-Signed Binary</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 rounded-full bg-blue-400 ml-1.5 mr-1" />
                  <span>Version: 1.0.0 Stable (Release)</span>
                </div>
              </div>
            </div>

            <div className="mt-8">
              <a
                href="https://github.com/HardikParmar-1111/AirLink/releases"
                target="_blank"
                rel="noopener noreferrer"
                className="w-full flex items-center justify-center gap-2 py-3 rounded-full bg-white hover:bg-zinc-100 text-zinc-950 font-semibold text-sm transition-all shadow-lg active:scale-95"
              >
                <Download className="w-4 h-4" />
                <span>Download AirLink for Mac (.dmg)</span>
              </a>
            </div>
          </motion.div>

          {/* Android Card */}
          <motion.div
            whileHover={{ y: -4 }}
            className="p-6 sm:p-8 rounded-[2rem] bg-white/[0.04] border border-white/10 hover:border-white/20 transition-all flex flex-col justify-between"
          >
            <div className="space-y-4">
              <div className="w-12 h-12 rounded-2xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center">
                <Smartphone className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-xl font-bold text-white">Android Client (APK)</h3>
                <p className="text-xs text-zinc-400 mt-1">
                  Native Android application with Binary frame streaming & Accessibility service.
                </p>
              </div>

              <div className="space-y-1.5 text-xs text-zinc-300">
                <div className="flex items-center gap-2">
                  <ShieldCheck className="w-4 h-4 text-emerald-400" />
                  <span>Zero trackers or background analytics</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 ml-1.5 mr-1" />
                  <span>Requires Android 10.0+ (API 29+)</span>
                </div>
              </div>
            </div>

            <div className="mt-8">
              <a
                href="https://github.com/HardikParmar-1111/AirLink/releases"
                target="_blank"
                rel="noopener noreferrer"
                className="w-full flex items-center justify-center gap-2 py-3 rounded-full bg-emerald-500 hover:bg-emerald-400 text-zinc-950 font-semibold text-sm transition-all shadow-lg active:scale-95"
              >
                <Download className="w-4 h-4" />
                <span>Download Android Client (.apk)</span>
              </a>
            </div>
          </motion.div>
        </div>

        {/* Terminal / Homebrew command */}
        <div className="relative z-10 mt-8 p-4 rounded-2xl bg-black/50 border border-white/10 flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-3 w-full sm:w-auto">
            <Terminal className="w-4 h-4 text-blue-400 shrink-0" />
            <span className="text-xs sm:text-sm font-mono text-zinc-300">
              brew install --cask airlink
            </span>
          </div>

          <button
            onClick={copyBrewCommand}
            type="button"
            className="w-full sm:w-auto flex items-center justify-center gap-1.5 px-4 py-2 rounded-xl bg-white/10 hover:bg-white/15 text-xs font-medium text-white transition-colors cursor-pointer"
          >
            {copiedBrew ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
            <span>{copiedBrew ? "Copied Command" : "Copy Homebrew"}</span>
          </button>
        </div>

        {/* GitHub Source Footer Note */}
        <div className="relative z-10 mt-6 text-center">
          <a
            href="https://github.com/HardikParmar-1111/AirLink"
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-2 text-xs text-zinc-400 hover:text-white transition-colors"
          >
            <GithubIcon className="w-3.5 h-3.5" />
            <span>Prefer building from source? Inspect the code on GitHub</span>
          </a>
        </div>
      </motion.div>
    </section>
  );
}
