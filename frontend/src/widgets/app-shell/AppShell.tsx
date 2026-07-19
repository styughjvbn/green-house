"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { SessionUserPanel } from "@/features/auth/ui/SessionUserPanel";
import { PageHeader } from "@/widgets/page-header";
import {
  BarChart3,
  ClipboardList,
  Flower2,
  Home,
  PackageCheck,
  PanelLeftClose,
  PanelLeftOpen,
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
    title: "작업 관리",
    description: "농장 작업을 등록하고 일정과 이력을 관리하세요.",
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

const tabLabels: Record<string, Record<string, string>> = {
  "work-records": {
    list: "작업 목록",
    calendar: "캘린더",
    history: "작업 이력",
  },
  sales: {
    slips: "판매 전표",
    auction: "출하·경매 추적",
    settlement: "경매 정산",
    partners: "거래처 관리",
  },
  analytics: {
    sales: "매출/출하",
    variety: "품종 분석",
    customer: "거래처 분석",
    space: "농장 공간",
    work: "작업/상태",
  },
  inventory: {
    variety: "품종 관리",
    inbound: "입고 관리",
    material: "자재 관리",
  },
};

function getBreadcrumbs(pathname: string, pageTitle: string) {
  const [section, tab] = pathname.split("/").filter(Boolean);
  const tabLabel = section && tab ? tabLabels[section]?.[tab] : undefined;
  return tabLabel ? [pageTitle, tabLabel] : [pageTitle];
}

const navigation: {
  href: string;
  activeHref?: string;
  label: string;
  icon: LucideIcon;
}[] = [
  { href: "/", label: "대시보드", icon: Home },
  { href: "/farm-status", label: "농장 현황", icon: Sprout },
  { href: "/orchid-groups", label: "난 묶음 관리", icon: Flower2 },
  {
    href: "/work-records/list",
    activeHref: "/work-records",
    label: "작업 관리",
    icon: ClipboardList,
  },
  {
    href: "/sales/slips",
    activeHref: "/sales",
    label: "판매 관리",
    icon: ShoppingBag,
  },
  {
    href: "/analytics/sales",
    activeHref: "/analytics",
    label: "분석",
    icon: BarChart3,
  },
  { href: "/print", label: "출력", icon: Printer },
  {
    href: "/inventory/variety",
    activeHref: "/inventory",
    label: "품종/자재 관리",
    icon: PackageCheck,
  },
  { href: "/settings", label: "설정", icon: Settings },
];

function isNavigationActive(
  pathname: string,
  item: (typeof navigation)[number],
) {
  if (item.href === "/") return pathname === "/";
  if (item.activeHref) {
    return (
      pathname === item.activeHref || pathname.startsWith(`${item.activeHref}/`)
    );
  }
  return pathname === item.href || pathname.startsWith(`${item.href}/`);
}

function NavItem({
  href,
  label,
  icon: Icon,
  active,
  collapsed,
}: {
  href: string;
  label: string;
  icon: LucideIcon;
  active: boolean;
  collapsed: boolean;
}) {
  return (
    <Link
      href={href}
      title={collapsed ? label : undefined}
      onClick={(event) => event.stopPropagation()}
      className={`grid grid-cols-[1.5rem_minmax(0,1fr)] items-center overflow-hidden rounded-md px-1 py-3 text-sm font-medium transition-colors ${
        active
          ? "bg-[#2f8f4e] text-white"
          : "text-[#dcebe0] hover:bg-white/10 hover:text-white"
      } gap-3`}
    >
      <span className="flex h-6 w-6 items-center justify-center">
        <Icon
          className="h-4 w-4 shrink-0"
          strokeWidth={1.8}
          aria-hidden="true"
        />
      </span>
      <span
        className={`overflow-hidden whitespace-nowrap transition-[max-width,opacity] duration-200 ease-out ${
          collapsed ? "max-w-0 opacity-0" : "max-w-32 opacity-100"
        }`}
      >
        {label}
      </span>
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
      onClick={(event) => event.stopPropagation()}
      className={`block overflow-hidden rounded-md px-3 py-2 text-sm font-medium whitespace-nowrap transition ${
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
  const [sidebarExpanded, setSidebarExpanded] = useState(true);
  const [compactDesktopHeader, setCompactDesktopHeader] = useState(false);
  const sidebarIdleTimerRef = useRef<ReturnType<typeof setTimeout> | null>(
    null,
  );

  const clearSidebarIdleTimer = () => {
    if (sidebarIdleTimerRef.current) {
      clearTimeout(sidebarIdleTimerRef.current);
      sidebarIdleTimerRef.current = null;
    }
  };

  const scheduleSidebarIdleCollapse = () => {
    clearSidebarIdleTimer();

    if (!compactDesktopHeader || !sidebarExpanded) {
      return;
    }

    sidebarIdleTimerRef.current = setTimeout(() => {
      setSidebarExpanded(false);
    }, 2000);
  };

  useEffect(() => {
    const sidebarQuery = window.matchMedia("(min-width: 1536px)");
    const compactHeaderQuery = window.matchMedia(
      "(min-width: 1024px) and (max-width: 1535px)",
    );
    const syncLayout = () => {
      setSidebarExpanded(sidebarQuery.matches);
      setCompactDesktopHeader(compactHeaderQuery.matches);
    };

    syncLayout();
    sidebarQuery.addEventListener("change", syncLayout);
    compactHeaderQuery.addEventListener("change", syncLayout);

    return () => {
      sidebarQuery.removeEventListener("change", syncLayout);
      compactHeaderQuery.removeEventListener("change", syncLayout);
    };
  }, []);

  useEffect(() => {
    scheduleSidebarIdleCollapse();

    return clearSidebarIdleTimer;
  }, [compactDesktopHeader, sidebarExpanded]);

  if (pathname === "/login") {
    return <>{children}</>;
  }

  const currentPage = getCurrentPageMeta(pathname);
  const breadcrumbs = getBreadcrumbs(pathname, currentPage.title);
  const activeTabPath = pathname.split("/")[2] ?? "";
  const isWorkPage = pathname.startsWith("/work-records");
  const isSalesPage = pathname.startsWith("/sales");
  const sidebarCollapsed = !sidebarExpanded;

  return (
    <div className="app-shell-root relative flex bg-[#f7f8f5]">
      <aside
        className={`app-shell-sidebar sticky top-0 z-1500 hidden shrink-0 flex-col bg-[#003b1f] px-2 py-4 transition-[width,box-shadow] duration-200 lg:flex lg:max-2xl:absolute lg:max-2xl:left-0 ${
          sidebarCollapsed ? "w-12 cursor-pointer" : "w-44"
        } ${sidebarCollapsed ? "" : "lg:max-2xl:shadow-xl"}`}
        tabIndex={-1}
        onClick={(event) => {
          if (sidebarCollapsed) {
            setSidebarExpanded(true);
            event.currentTarget.focus();
          }
        }}
        onMouseMove={scheduleSidebarIdleCollapse}
        onFocus={() => {
          if (compactDesktopHeader && sidebarCollapsed) {
            setSidebarExpanded(true);
          }
        }}
        onBlur={(event) => {
          const nextTarget = event.relatedTarget;
          if (
            compactDesktopHeader &&
            (!nextTarget || !event.currentTarget.contains(nextTarget))
          ) {
            setSidebarExpanded(false);
          }
        }}
      >
        <div
          className="group grid grid-cols-[2rem_minmax(0,1fr)] items-center gap-3"
          onClick={(event) => event.stopPropagation()}
        >
          <div className="relative flex h-8 w-8 shrink-0 items-center justify-center">
            <Image src="/flower.png" alt="Logo" width={40} height={40} />
            {sidebarCollapsed ? (
              <button
                className="absolute flex h-10 w-10 items-center justify-center rounded-md bg-[#003b1f]/85 text-white opacity-0 transition-opacity group-hover:opacity-100"
                type="button"
                aria-label="사이드바 펼치기"
                title="펼치기"
                onClick={() => setSidebarExpanded(true)}
              >
                <PanelLeftOpen
                  className="h-4 w-4"
                  strokeWidth={1.8}
                  aria-hidden="true"
                />
              </button>
            ) : null}
          </div>

          <div
            className={`flex min-w-0 items-start justify-between gap-2 overflow-hidden transition-[max-width,opacity] duration-200 ease-out ${
              sidebarCollapsed ? "max-w-0 opacity-0" : "max-w-32 opacity-100"
            }`}
          >
            <div className="min-w-0">
              <p className="text-base leading-none font-semibold whitespace-nowrap text-white">
                난 농장
              </p>
              <p className="mt-2 text-xs whitespace-nowrap text-[#c8d8cd]">
                관리 시스템
              </p>
            </div>
            <button
              className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-[#dcebe0] hover:bg-white/10 hover:text-white"
              type="button"
              aria-label="사이드바 접기"
              title="접기"
              tabIndex={sidebarCollapsed ? -1 : 0}
              onClick={() => setSidebarExpanded(false)}
            >
              <PanelLeftClose
                className="h-4 w-4"
                strokeWidth={1.8}
                aria-hidden="true"
              />
            </button>
          </div>
        </div>

        <nav className="scrollbar-hidden mt-5 min-h-0 flex-1 space-y-3 overflow-y-auto">
          {navigation.map((item) => (
            <div key={item.href}>
              <NavItem
                href={item.href}
                label={item.label}
                icon={item.icon}
                active={isNavigationActive(pathname, item)}
                collapsed={sidebarCollapsed}
              />

              {!sidebarCollapsed &&
              item.activeHref === "/work-records" &&
              isWorkPage ? (
                <div className="mt-2 space-y-1 pl-3">
                  <SalesSubNavItem
                    href="/work-records/list"
                    label="작업 목록"
                    active={activeTabPath === "list"}
                  />
                  <SalesSubNavItem
                    href="/work-records/calendar"
                    label="캘린더"
                    active={activeTabPath === "calendar"}
                  />
                  <SalesSubNavItem
                    href="/work-records/history"
                    label="작업 이력"
                    active={activeTabPath === "history"}
                  />
                </div>
              ) : null}

              {!sidebarCollapsed &&
              item.activeHref === "/sales" &&
              pathname.startsWith("/sales") ? (
                <div className="mt-2 space-y-1 pl-3">
                  <SalesSubNavItem
                    href="/sales/slips"
                    label="판매 전표"
                    active={activeTabPath === "slips"}
                  />
                  <SalesSubNavItem
                    href="/sales/auction"
                    label="출하·경매 추적"
                    active={activeTabPath === "auction"}
                  />
                  <SalesSubNavItem
                    href="/sales/settlement"
                    label="경매 정산"
                    active={activeTabPath === "settlement"}
                  />
                  <SalesSubNavItem
                    href="/sales/partners"
                    label="거래처 관리"
                    active={activeTabPath === "partners"}
                  />
                </div>
              ) : null}

              {!sidebarCollapsed &&
              item.activeHref === "/analytics" &&
              pathname.startsWith("/analytics") ? (
                <div className="mt-2 space-y-1 pl-3">
                  <SalesSubNavItem
                    href="/analytics/sales"
                    label="매출/출하"
                    active={activeTabPath === "sales"}
                  />
                  <SalesSubNavItem
                    href="/analytics/variety"
                    label="품종 분석"
                    active={activeTabPath === "variety"}
                  />
                  <SalesSubNavItem
                    href="/analytics/customer"
                    label="거래처 분석"
                    active={activeTabPath === "customer"}
                  />
                  <SalesSubNavItem
                    href="/analytics/space"
                    label="농장 공간"
                    active={activeTabPath === "space"}
                  />
                  <SalesSubNavItem
                    href="/analytics/work"
                    label="작업/상태"
                    active={activeTabPath === "work"}
                  />
                </div>
              ) : null}

              {!sidebarCollapsed &&
              item.activeHref === "/inventory" &&
              pathname.startsWith("/inventory") ? (
                <div className="mt-2 space-y-1 pl-3">
                  <SalesSubNavItem
                    href="/inventory/variety"
                    label="품종 관리"
                    active={activeTabPath === "variety"}
                  />
                  <SalesSubNavItem
                    href="/inventory/inbound"
                    label="입고 관리"
                    active={activeTabPath === "inbound"}
                  />
                  <SalesSubNavItem
                    href="/inventory/material"
                    label="자재 관리"
                    active={activeTabPath === "material"}
                  />
                </div>
              ) : null}
            </div>
          ))}
        </nav>

        {sidebarCollapsed ? null : <SessionUserPanel />}
      </aside>

      <div className="app-shell-main min-w-0 flex-1 lg:max-2xl:ml-12">
        <header className="border-b border-[#d7ddd4] bg-white px-4 py-4 lg:hidden">
          <p className="text-xl font-semibold">난 농장 관리</p>

          <nav className="mt-3 flex gap-2 overflow-x-auto">
            {navigation.map((item) => {
              const Icon = item.icon;
              const active = isNavigationActive(pathname, item);

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

          {isWorkPage ? (
            <div className="mt-3 flex gap-2 overflow-x-auto">
              {[
                ["list", "작업 목록"],
                ["calendar", "캘린더"],
                ["history", "작업 이력"],
              ].map(([tab, label]) => (
                <Link
                  key={tab}
                  href={`/work-records/${tab}`}
                  className={`shrink-0 rounded-md px-3 py-2 text-sm font-medium ${
                    activeTabPath === tab
                      ? "bg-[#dcefe1] text-[#1c5f33]"
                      : "bg-[#f0f3ef] text-[#435047]"
                  }`}
                >
                  {label}
                </Link>
              ))}
            </div>
          ) : null}

          {isSalesPage ? (
            <div className="mt-3 flex gap-2 overflow-x-auto">
              {[
                ["slips", "판매 전표"],
                ["auction", "출하·경매 추적"],
                ["settlement", "경매 정산"],
                ["partners", "거래처 관리"],
              ].map(([tab, label]) => (
                <Link
                  key={tab}
                  href={`/sales/${tab}`}
                  className={`shrink-0 rounded-md px-3 py-2 text-sm font-medium ${
                    activeTabPath === tab
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
                ["sales", "매출/출하"],
                ["variety", "품종 분석"],
                ["customer", "거래처 분석"],
                ["space", "농장 공간"],
                ["work", "작업/상태"],
              ].map(([tab, label]) => (
                <Link
                  key={tab}
                  href={`/analytics/${tab}`}
                  className={`shrink-0 rounded-md px-3 py-2 text-sm font-medium ${
                    activeTabPath === tab
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
                ["variety", "품종 관리"],
                ["inbound", "입고 관리"],
                ["material", "자재 관리"],
              ].map(([tab, label]) => (
                <Link
                  key={tab}
                  href={`/inventory/${tab}`}
                  className={`shrink-0 rounded-md px-3 py-2 text-sm font-medium ${
                    activeTabPath === tab
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
            breadcrumbs={breadcrumbs}
            className={
              sidebarExpanded ? "app-header-sidebar-overlay-expanded" : ""
            }
            collapsed={compactDesktopHeader || sidebarCollapsed}
          />

          {children}
        </main>
      </div>
    </div>
  );
}
