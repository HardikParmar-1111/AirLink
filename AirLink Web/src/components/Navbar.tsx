"use client";

import Image from "next/image";
import Link from "next/link";
import ThemeToggle from "./ThemeToggle";
import { Heart, ShieldCheck } from "lucide-react";

export default function Navbar() {
  return (
    <nav className="fixed bottom-6 inset-x-0 z-50 flex justify-center px-4 pointer-events-none">
      <div className="pointer-events-auto flex items-center gap-1 sm:gap-2 px-2.5 sm:px-3 py-1.5 sm:py-2 rounded-full bg-white/70 dark:bg-black/70 backdrop-blur-md border border-black/10 dark:border-white/10 shadow-[0_8px_32px_rgba(0,0,0,0.12)] transition-all max-w-[95vw] sm:max-w-none">
        {/* Primary App Identifier */}
        <Link
          href="#"
          className="flex items-center gap-2 pl-1.5 pr-2.5 py-1 rounded-full hover:bg-black/5 dark:hover:bg-white/10 transition-colors"
        >
          <Image
            src="/app-icon.png"
            alt="AirLink Logo"
            width={26}
            height={26}
            className="w-6 h-6 sm:w-6.5 sm:h-6.5 object-contain select-none"
            priority
          />
          <span className="font-semibold text-xs sm:text-sm text-zinc-950 dark:text-white tracking-tight">
            AirLink
          </span>
        </Link>

        {/* Ghost Button: Design */}
        <Link
          href="#features"
          title="Design Features"
          className="flex items-center gap-1.5 px-2.5 sm:px-3 py-1.5 rounded-full text-xs sm:text-sm font-medium text-zinc-700 hover:text-zinc-950 dark:text-zinc-300 dark:hover:text-white hover:bg-black/5 dark:hover:bg-white/10 transition-colors"
        >
          <Heart className="w-3.5 h-3.5 sm:w-4 sm:h-4 stroke-[2]" />
          <span className="hidden sm:inline">Design</span>
        </Link>

        {/* Ghost Button: Security */}
        <Link
          href="#security"
          title="Security Protocols"
          className="flex items-center gap-1.5 px-2.5 sm:px-3 py-1.5 rounded-full text-xs sm:text-sm font-medium text-zinc-700 hover:text-zinc-950 dark:text-zinc-300 dark:hover:text-white hover:bg-black/5 dark:hover:bg-white/10 transition-colors"
        >
          <ShieldCheck className="w-3.5 h-3.5 sm:w-4 sm:h-4 stroke-[2]" />
          <span className="hidden sm:inline">Security</span>
        </Link>

        {/* Theme Toggle */}
        <ThemeToggle />

        {/* Prominent Dark Download Button */}
        <Link
          href="#download"
          className="px-4 sm:px-5 py-1.5 sm:py-2 rounded-full text-xs sm:text-sm font-semibold bg-black text-white hover:bg-zinc-800 dark:bg-white dark:text-black dark:hover:bg-zinc-200 shadow-sm transition-all active:scale-95"
        >
          Download
        </Link>
      </div>
    </nav>
  );
}
