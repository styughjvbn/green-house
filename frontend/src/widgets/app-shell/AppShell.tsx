"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { SessionUserPanel } from "@/features/auth/ui/SessionUserPanel";
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
    description: "전체 농장 구조와 묶음 현황을 한눈에 확인하세요.",
  },
  {
    href: "/orchid-groups",
    title: "난 묶음 관리",
    description: "난 묶음의 위치와 상태를 등록하고 관리하세요.",
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
    description: "출하표, 전표, 문서를 출력하세요.",
  },
  {
    href: "/analytics",
    title: "분석",
    description: "출하, 판매, 농장 현황 데이터를 분석하세요.",
  },
  {
    href: "/inventory",
    title: "품종/자재 관리",
    description: "품종과 자재, 비료 정보를 등록하고 관리하세요.",
  },
  {
    href: "/settings",
    title: "설정",
    description: "서비스 설정을 관리하세요.",
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
      title: "판매 관리",
      description: "판매 관리 시스템입니다.",
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

function SalesSubNavItem({
  href,
  label,
  active,
}: {
  href: string;
  label: string;
  active: boolean;
}) {
  return (
    <Link
      href={href}
      className={`block rounded-md px-3 py-2 text-sm font-medium transition ${
        active
          ? "bg-white/12 text-white"
          : "text-[#c8d8cd] hover:bg-white/10 hover:text-white"
      }`}
    >
      {label}
    </Link>
  );
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  if (pathname === "/login") {
    return <>{children}</>;
  }

  const currentPage = getCurrentPageMeta(pathname);
  const salesTab = searchParams.get("tab") ?? "SLIPS";
  const isSalesPage = pathname.startsWith("/sales");

  return (
    <div className="app-shell-root flex bg-[#f7f8f5]">
      <aside className="app-shell-sidebar sticky top-0 hidden w-44 shrink-0 flex-col bg-[#003b1f] px-2 py-4 lg:flex">
        <div className="flex items-center gap-3 px-2 py-2">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center">
            <Image src="/flower.png" alt="Logo" width={40} height={40} />
          </div>

          <div>
            <p className="text-base leading-none font-semibold text-white">
              난 농장
            </p>
            <p className="mt-2 text-xs text-[#c8d8cd]">관리 시스템</p>
          </div>
        </div>

        <nav className="mt-8 min-h-0 flex-1 space-y-3 overflow-y-auto pr-1">
          {navigation.map((item) => (
            <div key={item.href}>
              <NavItem
                href={item.href}
                label={item.label}
                icon={item.icon}
                active={
                  item.href === "/sales"
                    ? pathname.startsWith("/sales")
                    : pathname === item.href
                }
              />

              {item.href === "/sales" && pathname.startsWith("/sales") ? (
                <div className="mt-2 space-y-1 pl-3">
                  <SalesSubNavItem
                    href="/sales?tab=SLIPS"
                    label="판매 전표"
                    active={salesTab === "SLIPS"}
                  />
                  <SalesSubNavItem
                    href="/sales?tab=AUCTION"
                    label="출하·경매 추적"
                    active={salesTab === "AUCTION"}
                  />
                  <SalesSubNavItem
                    href="/sales?tab=SETTLEMENT"
                    label="경매 정산"
                    active={salesTab === "SETTLEMENT"}
                  />
                  <SalesSubNavItem
                    href="/sales?tab=PARTNERS"
                    label="거래처 관리"
                    active={salesTab === "PARTNERS"}
                  />
                </div>
              ) : null}

              {item.href === "/analytics" &&
              pathname.startsWith("/analytics") ? (
                <div className="mt-2 space-y-1 pl-3">
                  <SalesSubNavItem
                    href="/analytics?tab=SALES"
                    label="매출/출하"
                    active={salesTab === "SALES"}
                  />
                  <SalesSubNavItem
                    href="/analytics?tab=VARIETY"
                    label="품종 분석"
                    active={salesTab === "VARIETY"}
                  />
                  <SalesSubNavItem
                    href="/analytics?tab=CUSTOMER"
                    label="거래처 분석"
                    active={salesTab === "CUSTOMER"}
                  />
                  <SalesSubNavItem
                    href="/analytics?tab=SPACE"
                    label="농장 공간"
                    active={salesTab === "SPACE"}
                  />
                  <SalesSubNavItem
                    href="/analytics?tab=WORK"
                    label="작업/상태"
                    active={salesTab === "WORK"}
                  />
                </div>
              ) : null}

              {item.href === "/inventory" &&
              pathname.startsWith("/inventory") ? (
                <div className="mt-2 space-y-1 pl-3">
                  <SalesSubNavItem
                    href="/inventory?tab=VARIETY"
                    label="품종 관리"
                    active={salesTab === "VARIETY"}
                  />
                  <SalesSubNavItem
                    href="/inventory?tab=INBOUND"
                    label="입고 관리"
                    active={salesTab === "INBOUND"}
                  />
                  <SalesSubNavItem
                    href="/inventory?tab=MATERIAL"
                    label="자재 관리"
                    active={salesTab === "MATERIAL"}
                  />
                </div>
              ) : null}
            </div>
          ))}
        </nav>

        <SessionUserPanel />
      </aside>

      <div className="app-shell-main min-w-0 flex-1">
        <header className="border-b border-[#d7ddd4] bg-white px-4 py-4 lg:hidden">
          <p className="text-xl font-semibold">난 농장 관리</p>

          <nav className="mt-3 flex gap-2 overflow-x-auto">
            {navigation.map((item) => {
              const Icon = item.icon;
              const active =
                item.href === "/sales"
                  ? pathname.startsWith("/sales")
                  : pathname === item.href;

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

          {isSalesPage ? (
            <div className="mt-3 flex gap-2 overflow-x-auto">
              {[
                ["SLIPS", "판매 전표"],
                ["AUCTION", "출하·경매 추적"],
                ["SETTLEMENT", "경매 정산"],
                ["PARTNERS", "거래처 관리"],
              ].map(([tab, label]) => (
                <Link
                  key={tab}
                  href={`/sales?tab=${tab}`}
                  className={`shrink-0 rounded-md px-3 py-2 text-sm font-medium ${
                    salesTab === tab
                      ? "bg-[#dcefe1] text-[#1c5f33]"
                      : "bg-[#f0f3ef] text-[#435047]"
                  }`}
                >
                  {label}
                </Link>
              ))}
            </div>
          ) : null}

          {pathname.startsWith("/analytics") ? (
            <div className="mt-3 flex gap-2 overflow-x-auto">
              {[
                ["SALES", "매출/출하"],
                ["VARIETY", "품종 분석"],
                ["CUSTOMER", "거래처 분석"],
                ["SPACE", "농장 공간"],
                ["WORK", "작업/상태"],
              ].map(([tab, label]) => (
                <Link
                  key={tab}
                  href={`/analytics?tab=${tab}`}
                  className={`shrink-0 rounded-md px-3 py-2 text-sm font-medium ${
                    salesTab === tab
                      ? "bg-[#dcefe1] text-[#1c5f33]"
                      : "bg-[#f0f3ef] text-[#435047]"
                  }`}
                >
                  {label}
                </Link>
              ))}
            </div>
          ) : null}

          {pathname.startsWith("/inventory") ? (
            <div className="mt-3 flex gap-2 overflow-x-auto">
              {[
                ["VARIETY", "품종 관리"],
                ["INBOUND", "입고 관리"],
                ["MATERIAL", "자재 관리"],
              ].map(([tab, label]) => (
                <Link
                  key={tab}
                  href={`/inventory?tab=${tab}`}
                  className={`shrink-0 rounded-md px-3 py-2 text-sm font-medium ${
                    salesTab === tab
                      ? "bg-[#dcefe1] text-[#1c5f33]"
                      : "bg-[#f0f3ef] text-[#435047]"
                  }`}
                >
                  {label}
                </Link>
              ))}
            </div>
          ) : null}
        </header>

        <main className="app-content px-4 py-4 md:px-8 lg:px-6">
          <PageHeader
            title={currentPage.title}
            description={currentPage.description}
          />

          {children}
        </main>
      </div>
    </div>
  );
}
