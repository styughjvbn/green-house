import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: '난 농장 관리 시스템 MVP',
  description: '15개 동/45개 배드 기반 난 농장 관리 MVP',
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
