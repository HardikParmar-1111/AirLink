import Navbar from "@/components/Navbar";
import Hero from "@/components/Hero";
import VideoShowcase from "@/components/VideoShowcase";
import ContinuityLab from "@/components/ContinuityLab";
import BentoFeatures from "@/components/BentoFeatures";
import SecuritySection from "@/components/SecuritySection";
import SpecsTable from "@/components/SpecsTable";
import FAQSection from "@/components/FAQSection";
import DownloadSection from "@/components/DownloadSection";
import Footer from "@/components/Footer";

export default function Home() {
  return (
    <div className="relative min-h-screen flex flex-col selection:bg-blue-500/20 selection:text-blue-600 dark:selection:text-blue-300">
      {/* Top Floating Glass Navigation */}
      <Navbar />

      <main className="flex-1">
        {/* Glance-style Apple Typography Hero */}
        <Hero />

        {/* Real Edge-to-Edge Looping Video */}
        <VideoShowcase />

        {/* Glance Signature Bento Grid Section */}
        <BentoFeatures />

        {/* Interactive Continuity Lab Simulator */}
        <ContinuityLab />

        {/* Security & Cryptography Protocol Deep Dive */}
        <SecuritySection />

        {/* Native Technical Architecture Specifications */}
        <SpecsTable />

        {/* FAQ Accordion */}
        <FAQSection />

        {/* Download Hub */}
        <DownloadSection />
      </main>

      {/* Footer */}
      <Footer />
    </div>
  );
}
