"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { PageHeader } from "@/widgets/page-header";
import {
  BarChart3,
  ClipboardList,
  Flower2,
  Home,
  PackageCheck,
  Printer,
  Settings,
  ShoppingBag,
  Sprout,
  type LucideIcon,
} from "lucide-react";

const pageMeta = [
  {
    href: "/",
    title: "대시보드",
    description: "농장 운영 현황을 한눈에 확인하세요.",
  },
  {
    href: "/farm-status",
    title: "농장 현황",
    description: "전체 농장 구조와 난 묶음 현황을 한눈에 확인하세요.",
  },
  {
    href: "/orchid-groups",
    title: "난 묶음 관리",
    description: "난 묶음 정보를 등록하고 관리하세요.",
  },
  {
    href: "/work-records",
    title: "작업 이력",
    description: "농장 작업 기록을 확인하고 관리하세요.",
  },
  {
    href: "/sales",
    title: "판매 관리",
    description: "판매 내역과 거래 정보를 관리하세요.",
  },
  {
    href: "/print",
    title: "출력",
    description: "라벨, 전표, 문서를 출력하세요.",
  },
  {
    href: "/analytics",
    title: "분석",
    description:
      "출하, 판매, 농장 현황 데이터를 분석하여 운영 의사결정을 도와드립니다.",
  },
  {
    href: "/inventory",
    title: "품종/자재 관리",
    description: "난 품종과 농약, 비료, 자재 정보를 등록하고 관리하세요.",
  },
  {
    href: "/settings",
    title: "설정",
    description: "시스템 설정을 관리하세요.",
  },
];
function getCurrentPageMeta(pathname: string) {
  return (
    pageMeta.find((item) => {
      if (item.href === "/") {
        return pathname === "/";
      }

      return pathname === item.href || pathname.startsWith(`${item.href}/`);
    }) ?? {
      title: "난 농장 관리",
      description: "난 농장 관리 시스템",
    }
  );
}

const navigation: {
  href: string;
  label: string;
  icon: LucideIcon;
}[] = [
  { href: "/", label: "대시보드", icon: Home },
  { href: "/farm-status", label: "농장 현황", icon: Sprout },
  { href: "/orchid-groups", label: "난 묶음 관리", icon: Flower2 },
  { href: "/work-records", label: "작업 이력", icon: ClipboardList },
  { href: "/sales", label: "판매 관리", icon: ShoppingBag },
  { href: "/analytics", label: "분석", icon: BarChart3 },
  { href: "/print", label: "출력", icon: Printer },
  { href: "/inventory", label: "품종/자재 관리", icon: PackageCheck },
  { href: "/settings", label: "설정", icon: Settings },
];

function NavItem({
  href,
  label,
  icon: Icon,
  active,
}: {
  href: string;
  label: string;
  icon: LucideIcon;
  active: boolean;
}) {
  return (
    <Link
      href={href}
      className={`flex items-center gap-3 rounded-md px-3 py-3 text-sm font-medium transition ${
        active
          ? "bg-[#2f8f4e] text-white"
          : "text-[#dcebe0] hover:bg-white/10 hover:text-white"
      }`}
    >
      <Icon className="h-4 w-4 shrink-0" strokeWidth={1.8} aria-hidden="true" />
      <span>{label}</span>
    </Link>
  );
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const currentPage = getCurrentPageMeta(pathname);

  return (
    <div className="flex min-h-screen bg-[#f7f8f5]">
      <aside className="hidden w-44 shrink-0 bg-[#003b1f] px-2 py-4 lg:block">
        <div className="flex items-center gap-3 px-2 py-2">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center">
            <img src="/flower.png" alt="Logo" className="h-10 w-10" />
          </div>

          <div>
            <p className="text-base leading-none font-semibold text-white">
              난 농장
            </p>
            <p className="mt-2 text-xs text-[#c8d8cd]">관리 시스템</p>
          </div>
        </div>

        <nav className="mt-8 space-y-3">
          {navigation.map((item) => (
            <NavItem
              key={item.href}
              href={item.href}
              label={item.label}
              icon={item.icon}
              active={pathname === item.href}
            />
          ))}
        </nav>
      </aside>

      <div className="min-w-0 flex-1">
        <header className="border-b border-[#d7ddd4] bg-white px-4 py-4 lg:hidden">
          <p className="text-xl font-semibold">난 농장 관리</p>

          <nav className="mt-3 flex gap-2 overflow-x-auto">
            {navigation.map((item) => {
              const Icon = item.icon;
              const active = pathname === item.href;

              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`flex shrink-0 items-center gap-2 rounded-md px-4 py-2 text-base font-medium ${
                    active
                      ? "bg-[#e7f0e6] text-[#214f31]"
                      : "bg-[#f0f3ef] text-[#435047]"
                  }`}
                >
                  <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
                  <span>{item.label}</span>
                </Link>
              );
            })}
          </nav>
        </header>
        <PageHeader
          title={currentPage.title}
          description={currentPage.description}
        />

        <main className="app-content px-5 py-6 md:px-8 lg:px-6">
          {children}
        </main>
      </div>
    </div>
  );
}
