import type { Metadata } from "next";
import { Geist } from "next/font/google";
import { AppShell } from "@/widgets/app-shell/AppShell";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "난 농장 관리",
  description: "비닐하우스 난 농장 운영 관리 시스템",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko" className={`${geistSans.variable} h-full antialiased`}>
      <body className="min-h-full bg-[#f7f8f6] text-[#1f2a24]">
        <AppShell>{children}</AppShell>
      </body>
    </html>
  );
}
