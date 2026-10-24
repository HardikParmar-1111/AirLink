import type { Metadata } from "next";
import { Inter, JetBrains_Mono } from "next/font/google";
import "./globals.css";

const inter = Inter({
  variable: "--font-inter",
  subsets: ["latin"],
  display: "swap",
});

const jetbrainsMono = JetBrains_Mono({
  variable: "--font-mono",
  subsets: ["latin"],
  display: "swap",
});

export const metadata: Metadata = {
  metadataBase: new URL("https://airlink.app"),
  title: "AirLink",
  description:
    "Low-latency 60 FPS screen mirroring, universal clipboard sync, remote trackpad gestures, and battery telemetry. 100% local, offline, AES-256-GCM encrypted.",
  keywords: [
    "AirLink",
    "macOS Android Continuity",
    "Screen Mirroring",
    "Universal Clipboard",
    "Remote Trackpad",
    "Open Source",
    "Apple Glance Style",
  ],
  authors: [{ name: "Hardik Parmar" }],
  openGraph: {
    title: "AirLink",
    description:
      "Seamless 60 FPS screen mirroring, universal clipboard synchronization, and remote trackpad control between Android and macOS.",
    url: "https://airlink.app",
    siteName: "AirLink",
    images: [
      {
        url: "/banner.png",
        width: 1200,
        height: 630,
        alt: "AirLink Preview Banner",
      },
    ],
    locale: "en_US",
    type: "website",
  },
  twitter: {
    card: "summary_large_image",
    title: "AirLink",
    description:
      "Seamless 60 FPS screen mirroring, universal clipboard synchronization, and remote trackpad control between Android and macOS.",
    images: ["/banner.png"],
  },
  icons: {
    icon: [
      { url: "/favicon.ico", sizes: "any" },
      { url: "/icon.png", type: "image/png", sizes: "512x512" },
      { url: "/app-icon.png", type: "image/png" },
    ],
    apple: [
      { url: "/apple-touch-icon.png", sizes: "180x180", type: "image/png" },
    ],
    shortcut: ["/app-icon.png"],
  },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" suppressHydrationWarning className={`${inter.variable} ${jetbrainsMono.variable}`}>
      <head>
        <script
          dangerouslySetInnerHTML={{
            __html: `
              (function() {
                try {
                  const stored = localStorage.getItem('airlink-theme');
                  if (stored === 'dark') {
                    document.documentElement.classList.add('dark');
                  } else if (stored === 'light') {
                    document.documentElement.classList.remove('dark');
                  }
                } catch (e) {}
              })();
            `,
          }}
        />
      </head>
      <body className="min-h-screen font-sans bg-[var(--bg-page)] text-[var(--text-primary)] antialiased selection:bg-blue-500/20 selection:text-blue-600 dark:selection:text-blue-300">
        {children}
      </body>
    </html>
  );
}
