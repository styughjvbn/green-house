"use client";

import Image from "next/image";
import Link from "next/link";
import type { Route } from "next";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import type { MouseEventHandler } from "react";
import { SessionUserPanel } from "@/features/auth/ui/SessionUserPanel";
import {
  NAVIGATION,
  PAGE_META,
  type NavigationChild,
  type NavigationItem,
} from "@/shared/config/navigation";
import { PageHeader } from "@/widgets/page-header";
import { PanelLeftClose, PanelLeftOpen, type LucideIcon } from "lucide-react";

function getCurrentPageMeta(pathname: string) {
  return (
    PAGE_META.find((item) => {
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

function getBreadcrumbs(pathname: string, pageTitle: string) {
  const [section, tab] = pathname.split("/").filter(Boolean);
  const sectionHref = section ? `/${section}` : undefined;
  const tabLabel =
    sectionHref && tab
      ? NAVIGATION.find(
          (item) => item.sectionHref === sectionHref,
        )?.children?.find((item) => item.tab === tab)?.label
      : undefined;
  return tabLabel ? [pageTitle, tabLabel] : [pageTitle];
}

function isNavigationActive(pathname: string, item: NavigationItem) {
  if (item.href === "/") return pathname === "/";
  if (item.sectionHref) {
    return (
      pathname === item.sectionHref ||
      pathname.startsWith(`${item.sectionHref}/`)
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
  onClick,
}: {
  href: string;
  label: string;
  icon: LucideIcon;
  active: boolean;
  collapsed: boolean;
  onClick?: MouseEventHandler<HTMLAnchorElement>;
}) {
  return (
    <Link
      href={href as Route}
      title={collapsed ? label : undefined}
      onClick={(event) => {
        event.stopPropagation();
        onClick?.(event);
      }}
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
      href={href as Route}
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

function SubNavFlyout({
  items,
  activeTabPath,
  open,
}: {
  items: NavigationChild[];
  activeTabPath: string;
  open?: boolean;
}) {
  return (
    <div
      className={`absolute top-0 left-full z-10 ml-2 min-w-30 rounded-md border border-white/10 bg-[#003b1f] p-2 shadow-xl transition-[opacity,transform] duration-150 ease-out ${
        open
          ? "pointer-events-auto translate-x-0 opacity-100"
          : "pointer-events-none translate-x-1 opacity-0 group-focus-within/nav-item:pointer-events-auto group-focus-within/nav-item:translate-x-0 group-focus-within/nav-item:opacity-100 group-hover/nav-item:pointer-events-auto group-hover/nav-item:translate-x-0 group-hover/nav-item:opacity-100"
      }`}
    >
      <div className="space-y-1">
        {items.map((item) => (
          <SalesSubNavItem
            key={item.href}
            href={item.href as Route}
            label={item.label}
            active={activeTabPath === item.tab}
          />
        ))}
      </div>
    </div>
  );
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const [sidebarExpanded, setSidebarExpanded] = useState(true);
  const [compactDesktopHeader, setCompactDesktopHeader] = useState(false);
  const [thinDesktopHeader, setThinDesktopHeader] = useState(false);
  const [openSubNavFlyoutHref, setOpenSubNavFlyoutHref] = useState<
    string | null
  >(null);
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

    if (!compactDesktopHeader || (!sidebarExpanded && !openSubNavFlyoutHref)) {
      return;
    }

    sidebarIdleTimerRef.current = setTimeout(() => {
      setOpenSubNavFlyoutHref(null);
      if (sidebarExpanded) {
        setSidebarExpanded(false);
      }
    }, 2500);
  };

  useEffect(() => {
    const sidebarQuery = window.matchMedia("(min-width: 1536px)");
    const compactHeaderQuery = window.matchMedia(
      "(min-width: 1024px) and (max-width: 1535px)",
    );
    const thinHeaderQuery = window.matchMedia("(min-width: 1024px)");
    const syncLayout = () => {
      setOpenSubNavFlyoutHref(null);
      setSidebarExpanded(sidebarQuery.matches);
      setCompactDesktopHeader(compactHeaderQuery.matches);
      setThinDesktopHeader(thinHeaderQuery.matches);
    };

    syncLayout();
    sidebarQuery.addEventListener("change", syncLayout);
    compactHeaderQuery.addEventListener("change", syncLayout);
    thinHeaderQuery.addEventListener("change", syncLayout);

    return () => {
      sidebarQuery.removeEventListener("change", syncLayout);
      compactHeaderQuery.removeEventListener("change", syncLayout);
      thinHeaderQuery.removeEventListener("change", syncLayout);
    };
  }, []);

  useEffect(() => {
    scheduleSidebarIdleCollapse();

    return clearSidebarIdleTimer;
  }, [compactDesktopHeader, openSubNavFlyoutHref, sidebarExpanded]);

  if (pathname === "/login") {
    return <>{children}</>;
  }

  const currentPage = getCurrentPageMeta(pathname);
  const breadcrumbs = getBreadcrumbs(pathname, currentPage.title);
  const activeTabPath = pathname.split("/")[2] ?? "";
  const isFarmStatusPage = pathname.startsWith("/farm-status");
  const sidebarCollapsed = !sidebarExpanded;
  const activeNavigationItem = NAVIGATION.find((item) =>
    isNavigationActive(pathname, item),
  );
  const activeSubNavigation = activeNavigationItem?.children;
  const compactHeaderSubNavigation =
    compactDesktopHeader && activeSubNavigation
      ? activeSubNavigation.map((item) => ({
          href: item.href,
          label: item.label,
          active: activeTabPath === item.tab,
        }))
      : undefined;

  return (
    <div className="app-shell-root relative flex bg-[#f7f8f5]">
      <aside
        className={`app-shell-sidebar sticky top-0 z-750 hidden shrink-0 flex-col bg-[#003b1f] px-2 py-4 transition-[width,box-shadow] duration-200 lg:flex lg:max-2xl:absolute lg:max-2xl:left-0 ${
          sidebarCollapsed ? "w-12 cursor-pointer" : "w-44"
        } ${sidebarCollapsed ? "" : "lg:max-2xl:shadow-xl"}`}
        tabIndex={-1}
        onClick={(event) => {
          if (sidebarCollapsed) {
            setOpenSubNavFlyoutHref(null);
            setSidebarExpanded(true);
            event.currentTarget.focus();
          }
        }}
        onMouseMove={scheduleSidebarIdleCollapse}
        onFocus={(event) => {
          if (
            event.target === event.currentTarget &&
            compactDesktopHeader &&
            sidebarCollapsed
          ) {
            setOpenSubNavFlyoutHref(null);
            setSidebarExpanded(true);
          }
        }}
        onBlur={(event) => {
          const nextTarget = event.relatedTarget;
          if (
            compactDesktopHeader &&
            (!nextTarget || !event.currentTarget.contains(nextTarget))
          ) {
            setOpenSubNavFlyoutHref(null);
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
                onClick={() => {
                  setOpenSubNavFlyoutHref(null);
                  setSidebarExpanded(true);
                }}
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
              onClick={() => {
                setOpenSubNavFlyoutHref(null);
                setSidebarExpanded(false);
              }}
            >
              <PanelLeftClose
                className="h-4 w-4"
                strokeWidth={1.8}
                aria-hidden="true"
              />
            </button>
          </div>
        </div>

        <nav
          className={`scrollbar-hidden mt-5 min-h-0 flex-1 space-y-3 ${
            compactDesktopHeader ? "overflow-visible" : "overflow-y-auto"
          }`}
        >
          {NAVIGATION.map((item) => {
            const active = isNavigationActive(pathname, item);
            const subNavItems = item.children;
            const flyoutOpen =
              sidebarCollapsed &&
              active &&
              item.sectionHref !== undefined &&
              openSubNavFlyoutHref === item.sectionHref;
            const activeFlyoutOpen = !sidebarCollapsed && active;
            const shouldShowInlineSubNav =
              !compactDesktopHeader && !sidebarCollapsed && active;

            return (
              <div className="group/nav-item relative" key={item.href}>
                <NavItem
                  href={item.href as Route}
                  label={item.label}
                  icon={item.icon}
                  active={active}
                  collapsed={sidebarCollapsed}
                  onClick={(event) => {
                    if (
                      compactDesktopHeader &&
                      sidebarCollapsed &&
                      active &&
                      item.sectionHref &&
                      subNavItems
                    ) {
                      event.preventDefault();
                      setOpenSubNavFlyoutHref(item.sectionHref);
                    }
                  }}
                />

                {compactDesktopHeader &&
                subNavItems &&
                (!sidebarCollapsed || flyoutOpen) ? (
                  <SubNavFlyout
                    items={subNavItems}
                    activeTabPath={activeTabPath}
                    open={flyoutOpen || activeFlyoutOpen}
                  />
                ) : null}

                {shouldShowInlineSubNav && subNavItems ? (
                  <div className="mt-2 space-y-1 pl-3">
                    {subNavItems.map((subItem) => (
                      <SalesSubNavItem
                        key={subItem.href}
                        href={subItem.href as Route}
                        label={subItem.label}
                        active={activeTabPath === subItem.tab}
                      />
                    ))}
                  </div>
                ) : null}
              </div>
            );
          })}
        </nav>

        {sidebarCollapsed ? null : <SessionUserPanel />}
      </aside>

      <div className="app-shell-main min-w-0 flex-1 lg:max-2xl:ml-12">
        <header className="border-b border-[#d7ddd4] bg-white px-4 py-4 lg:hidden">
          <p className="text-xl font-semibold">난 농장 관리</p>

          <nav className="mt-3 flex gap-2 overflow-x-auto">
            {NAVIGATION.map((item) => {
              const Icon = item.icon;
              const active = isNavigationActive(pathname, item);

              return (
                <Link
                  key={item.href}
                  href={item.href as Route}
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

          {activeSubNavigation ? (
            <div className="mt-3 flex gap-2 overflow-x-auto">
              {activeSubNavigation.map((item) => (
                <Link
                  key={item.href}
                  href={item.href as Route}
                  className={`shrink-0 rounded-md px-3 py-2 text-sm font-medium ${
                    activeTabPath === item.tab
                      ? "bg-[#dcefe1] text-[#1c5f33]"
                      : "bg-[#f0f3ef] text-[#435047]"
                  }`}
                >
                  {item.label}
                </Link>
              ))}
            </div>
          ) : null}
        </header>

        <main
          className={
            isFarmStatusPage
              ? "app-content farm-status-content p-0"
              : "app-content px-4 py-4 md:px-8 lg:px-6"
          }
        >
          <PageHeader
            title={currentPage.title}
            description={currentPage.description}
            breadcrumbs={breadcrumbs}
            compactSubNavigation={compactHeaderSubNavigation}
            className={
              sidebarExpanded ? "app-header-sidebar-overlay-expanded" : ""
            }
            collapsed={thinDesktopHeader || sidebarCollapsed}
          />

          {children}
        </main>
      </div>
    </div>
  );
}
