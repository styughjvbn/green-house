"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const navigation = [
  { href: "/", label: "대시보드" },
  { href: "/farm-status", label: "농장 현황" },
  { href: "/orchid-groups", label: "난 묶음 관리" },
  { href: "/work-records", label: "작업 이력" },
  { href: "/sales", label: "판매 관리" },
  { href: "/print", label: "출력" },
  { href: "/settings", label: "설정" },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();

  return (
    <div className="flex min-h-screen">
      <aside className="hidden w-64 shrink-0 border-r border-[#d7ddd4] bg-white px-4 py-6 lg:block">
        <div className="px-3">
          <p className="text-sm font-semibold text-[#3d6f91]">Green House</p>
          <p className="mt-1 text-2xl font-semibold">난 농장 관리</p>
        </div>
        <nav className="mt-8 space-y-2">
          {navigation.map((item) => {
            const active = pathname === item.href;
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`flex items-center px-4 py-3 text-lg font-medium transition ${
                  active
                    ? "bg-[#e7f0e6] text-[#214f31]"
                    : "text-[#435047] hover:bg-[#f0f3ef]"
                }`}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>
      </aside>

      <div className="min-w-0 flex-1">
        <header className="border-b border-[#d7ddd4] bg-white px-4 py-4 lg:hidden">
          <p className="text-xl font-semibold">난 농장 관리</p>
          <nav className="mt-3 flex gap-2 overflow-x-auto">
            {navigation.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className="shrink-0 bg-[#f0f3ef] px-4 py-2 text-base font-medium text-[#435047]"
              >
                {item.label}
              </Link>
            ))}
          </nav>
        </header>
        <div className="app-content px-5 py-6 md:px-8 lg:px-10">{children}</div>
      </div>
    </div>
  );
}
