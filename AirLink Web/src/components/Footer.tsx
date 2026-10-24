"use client";

import Image from "next/image";
import Link from "next/link";
import { GithubIcon } from "./Icons";

export default function Footer() {
  return (
    <footer className="border-t border-black/5 dark:border-white/10 pt-12 pb-32 px-4 max-w-6xl mx-auto">
      <div className="flex flex-col md:flex-row items-center justify-between gap-6 text-center md:text-left">
        {/* Brand with Official AirLink Icon */}
        <div className="flex items-center gap-2.5">
          <Image
            src="/app-icon.png"
            alt="AirLink Logo"
            width={24}
            height={24}
            className="w-6 h-6 object-contain"
          />
          <span className="font-semibold text-sm tracking-tight text-zinc-900 dark:text-white">
            AirLink
          </span>
          <span className="text-xs text-zinc-400">
            • Open Source Android & macOS Continuity
          </span>
        </div>

        {/* Navigation Links */}
        <div className="flex flex-wrap items-center justify-center gap-6 text-xs text-zinc-600 dark:text-zinc-400 font-medium">
          <Link href="#features" className="hover:text-zinc-900 dark:hover:text-white transition-colors">
            Design
          </Link>
          <Link href="#showcase" className="hover:text-zinc-900 dark:hover:text-white transition-colors">
            Video
          </Link>
          <Link href="#playground" className="hover:text-zinc-900 dark:hover:text-white transition-colors">
            Continuity Lab
          </Link>
          <Link href="#security" className="hover:text-zinc-900 dark:hover:text-white transition-colors">
            Security
          </Link>
          <Link href="#specs" className="hover:text-zinc-900 dark:hover:text-white transition-colors">
            Specs
          </Link>
          <a
            href="https://github.com/HardikParmar-1111/AirLink"
            target="_blank"
            rel="noopener noreferrer"
            className="hover:text-zinc-900 dark:hover:text-white transition-colors flex items-center gap-1"
          >
            <GithubIcon className="w-3.5 h-3.5" />
            <span>GitHub</span>
          </a>
        </div>

        {/* Clean Copyright (Built with Next.js & Tailwind CSS removed) */}
        <div className="text-xs text-zinc-400 dark:text-zinc-500">
          Released under the MIT License.
        </div>
      </div>
    </footer>
  );
}
