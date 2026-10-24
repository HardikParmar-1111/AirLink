"use client";

import { motion } from "framer-motion";
import { AppleLogo } from "./Icons";

export default function Hero() {
  return (
    <section className="relative pt-20 pb-8 sm:pt-28 sm:pb-12 px-4 flex flex-col items-center text-center">
      {/* Glance Signature Atmospheric Vertical Light Beam Reflection */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 0.75 }}
        transition={{ duration: 1.2, ease: "easeOut" }}
        className="absolute top-0 inset-x-0 h-96 pointer-events-none -z-10 flex justify-center"
      >
        <div className="w-full max-w-5xl h-full bg-[radial-gradient(ellipse_80%_60%_at_50%_0%,rgba(186,215,255,0.45),rgba(240,244,250,0.2)_60%,transparent_100%)]" />
      </motion.div>

      {/* Main Headline with Authentic Apple Logo */}
      <motion.h1
        initial={{ opacity: 0, y: 24 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.7, ease: [0.16, 1, 0.3, 1] }}
        className="text-5xl sm:text-7xl lg:text-8xl font-bold tracking-tight text-zinc-950 dark:text-white leading-[1.04]"
      >
        Android and{" "}
        <span className="inline-flex items-center">
          <AppleLogo className="w-[0.80em] h-[0.80em] inline -mt-1 sm:-mt-2 mr-1" />
          Mac.
        </span>
        <br />
        Finally in sync.
      </motion.h1>

      {/* Sub-headline */}
      <motion.p
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.7, delay: 0.15, ease: [0.16, 1, 0.3, 1] }}
        className="mt-5 max-w-2xl text-base sm:text-lg text-zinc-600 dark:text-zinc-400 font-normal leading-relaxed"
      >
        Experience high-FPS screen mirroring, universal clipboard, and full touch control—entirely over your local network.
      </motion.p>

      {/* 4 Pill Buttons in a Row - Exact Glance Style */}
      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, delay: 0.25, ease: [0.16, 1, 0.3, 1] }}
        className="mt-8 flex flex-wrap items-center justify-center gap-2 sm:gap-2.5"
      >
        <a
          href="#download"
          className="px-5 py-2.5 rounded-full text-xs sm:text-sm font-semibold bg-black text-white hover:bg-zinc-800 transition-all shadow-sm active:scale-95"
        >
          Download for Mac
        </a>

        <a
          href="#download"
          className="px-4 py-2.5 rounded-full text-xs sm:text-sm font-medium bg-zinc-200/70 hover:bg-zinc-300/70 dark:bg-zinc-800 dark:hover:bg-zinc-700 text-zinc-900 dark:text-zinc-100 transition-all active:scale-95"
        >
          Android APK
        </a>

        <a
          href="https://github.com/HardikParmar-1111/AirLink"
          target="_blank"
          rel="noopener noreferrer"
          className="px-4 py-2.5 rounded-full text-xs sm:text-sm font-medium bg-zinc-200/70 hover:bg-zinc-300/70 dark:bg-zinc-800 dark:hover:bg-zinc-700 text-zinc-900 dark:text-zinc-100 transition-all flex items-center gap-1.5 active:scale-95"
        >
          <span>☆</span>
          <span>GitHub</span>
        </a>

        <div className="px-4 py-2.5 rounded-full text-xs sm:text-sm font-medium bg-zinc-200/70 dark:bg-zinc-800 text-zinc-800 dark:text-zinc-200 flex items-center gap-1.5 cursor-default">
          <span>⚡</span>
          <span>60 FPS</span>
        </div>
      </motion.div>
    </section>
  );
}
