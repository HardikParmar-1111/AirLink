"use client";

import { useState } from "react";
import { ChevronDown, HelpCircle } from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";

export default function FAQSection() {
  const [openIndex, setOpenIndex] = useState<number | null>(0);

  const faqs = [
    {
      q: "Does AirLink require an active internet connection?",
      a: "No! AirLink communicates strictly across your local Wi-Fi network, hotspot, or local subnet. You can use it on airplanes, off-grid locations, or in coffee shops without transmitting a single byte outside your local network.",
    },
    {
      q: "How does the latency compare to tools like scrcpy or commercial apps?",
      a: "AirLink achieves sub-25ms glass-to-glass latency by piping optimized binary JPEG frame payloads directly over high-speed local WebSockets without slow video container handshakes or cloud relay bottlenecks.",
    },
    {
      q: "Do I need USB cables or ADB debugging enabled every time?",
      a: "No! After standard initial setup (granting Screen Projection and Accessibility permissions on Android), AirLink connects completely wirelessly via Bonjour / mDNS zero-configuration discovery every time you open the apps.",
    },
    {
      q: "How does the remote trackpad and touch injection work?",
      a: "The macOS AirLink window captures cursor positions, clicks, and multi-touch trackpad scroll events, maps their normalized coordinates (0.0 to 1.0), and sends them over WebSocket to the Android Accessibility Service which injects native MotionEvents at 60 Hz.",
    },
    {
      q: "Is AirLink completely free and open source?",
      a: "Yes, 100%. AirLink is licensed under the permissive MIT license. Both the Swift macOS code and Android Kotlin code are hosted publicly on GitHub with zero paywalls, advertisements, or proprietary locks.",
    },
  ];

  return (
    <section id="faq" className="py-16 sm:py-24 px-4 max-w-4xl mx-auto">
      <div className="text-center max-w-2xl mx-auto mb-12 sm:mb-16">
        <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-zinc-500/10 text-zinc-600 dark:text-zinc-400 border border-zinc-500/20 mb-3">
          <HelpCircle className="w-3.5 h-3.5" />
          <span>Got Questions?</span>
        </div>
        <h2 className="text-3xl sm:text-4xl font-bold tracking-tight text-zinc-900 dark:text-white">
          Frequently Asked Questions
        </h2>
        <p className="mt-3 text-sm sm:text-base text-zinc-600 dark:text-zinc-400">
          Everything you need to know about latency, security, and setup.
        </p>
      </div>

      <div className="space-y-4">
        {faqs.map((faq, index) => {
          const isOpen = openIndex === index;
          return (
            <div
              key={faq.q}
              className="glass-panel rounded-2xl overflow-hidden border border-black/5 dark:border-white/10 transition-colors"
            >
              <button
                onClick={() => setOpenIndex(isOpen ? null : index)}
                type="button"
                className="w-full p-5 sm:p-6 text-left flex items-center justify-between gap-4 cursor-pointer"
              >
                <span className="font-semibold text-sm sm:text-base text-zinc-900 dark:text-white">
                  {faq.q}
                </span>
                <ChevronDown
                  className={`w-4 h-4 text-zinc-500 transition-transform duration-200 shrink-0 ${
                    isOpen ? "rotate-180" : ""
                  }`}
                />
              </button>

              <AnimatePresence>
                {isOpen && (
                  <motion.div
                    initial={{ height: 0, opacity: 0 }}
                    animate={{ height: "auto", opacity: 1 }}
                    exit={{ height: 0, opacity: 0 }}
                    transition={{ duration: 0.2 }}
                  >
                    <div className="px-5 pb-5 sm:px-6 sm:pb-6 text-xs sm:text-sm text-zinc-600 dark:text-zinc-400 leading-relaxed border-t border-black/5 dark:border-white/5 pt-3">
                      {faq.a}
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          );
        })}
      </div>
    </section>
  );
}
