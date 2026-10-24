"use client";

import { motion } from "framer-motion";

export default function VideoShowcase() {
  return (
    <section id="showcase" className="relative px-4 max-w-5xl mx-auto my-4 sm:my-8">
      {/* Clean Edge-to-Edge Rounded Card matching Glance video frame with Framer Motion */}
      <motion.div
        initial={{ opacity: 0, scale: 0.97, y: 30 }}
        whileInView={{ opacity: 1, scale: 1, y: 0 }}
        viewport={{ once: true, margin: "-80px" }}
        transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1] }}
        className="relative w-full rounded-[2.2rem] sm:rounded-[2.8rem] overflow-hidden bg-black shadow-xl border border-black/5 dark:border-white/10 aspect-[16/10] sm:aspect-[16/9]"
      >
        <video
          src="/videos/airlink-hero.mp4"
          playsInline
          autoPlay
          muted
          loop
          className="w-full h-full object-cover select-none pointer-events-none"
        />
      </motion.div>
    </section>
  );
}
