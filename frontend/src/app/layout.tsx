import type { Metadata } from "next";
import { Suspense } from "react";
import { Geist } from "next/font/google";
import { FontScaleInitializer } from "@/features/settings";
import { DEFAULT_FONT_SCALE } from "@/features/settings/lib/fontScale";
import { QueryProvider } from "@/shared/api/QueryProvider";
import { PwaRuntime } from "@/shared/pwa";
import { AppShell } from "@/widgets/app-shell/AppShell";
import { DemoEnvironmentBanner } from "@/widgets/demo-environment-banner/DemoEnvironmentBanner";
import "./globals.css";
import "@/shared/pwa/pwa.css";
import "leaflet/dist/leaflet.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "난 농장 관리",
  description: "비닐하우스 난 농장 운영 관리 시스템",
};

export const dynamic = "force-dynamic";

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const demoMode = process.env.DEMO_MODE === "true";

  return (
    <html
      lang="ko"
      className={`${geistSans.variable} h-full antialiased`}
      style={
        {
          "--font-scale": String(DEFAULT_FONT_SCALE),
        } as React.CSSProperties
      }
    >
      <body className="min-h-full bg-[#f7f8f6] text-[#1f2a24]">
        <FontScaleInitializer />
        <PwaRuntime />
        <QueryProvider>
          <div
            className={
              demoMode
                ? "flex h-[var(--app-viewport-height)] flex-col"
                : undefined
            }
          >
            {demoMode ? <DemoEnvironmentBanner /> : null}
            <div className={demoMode ? "min-h-0 flex-1" : undefined}>
              <Suspense fallback={null}>
                <AppShell demoMode={demoMode}>{children}</AppShell>
              </Suspense>
            </div>
          </div>
        </QueryProvider>
      </body>
    </html>
  );
}
