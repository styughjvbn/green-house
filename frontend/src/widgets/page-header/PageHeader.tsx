"use client";

import Link from "next/link";
import { Bell, CalendarDays, ChevronRight, CloudSun } from "lucide-react";
import type { ReactNode } from "react";
import { useSyncExternalStore } from "react";

type PageHeaderProps = {
  title: string;
  description: string;
  breadcrumbs?: string[];
  className?: string;
  collapsed?: boolean;
  compactSubNavigation?: {
    href: string;
    label: string;
    active: boolean;
  }[];
  notificationCount?: number;
  temperatureLabel?: string;
  children?: ReactNode;
};

export function PageHeader({
  title,
  description: _description,
  breadcrumbs = [title],
  className = "",
  collapsed: _collapsed = false,
  compactSubNavigation,
  notificationCount = 0,
  temperatureLabel = "24째C",
  children,
}: PageHeaderProps) {
  const todayLabel = useSyncExternalStore(
    subscribeDateLabel,
    getTodayLabel,
    getServerDateLabel,
  );

  return (
    <header
      className={`border-b border-[#edf0ec] bg-white shadow-[0_1px_8px_rgba(31,42,36,0.04)] transition-[margin-left,border-color,box-shadow] duration-200 ease-out ${className}`}
    >
      <div className="flex min-h-6 items-center justify-between gap-4 px-4 transition-[min-height,padding] duration-200 ease-out md:px-5">
        <div className="flex min-w-0 items-end gap-2">
          <h1
            aria-label={
              compactSubNavigation
                ? `${title} > ${compactSubNavigation
                    .map((item) => item.label)
                    .join(" · ")}`
                : breadcrumbs.join(" > ")
            }
            className="flex min-w-0 items-center gap-1 text-xs text-[#17251b]"
          >
            {compactSubNavigation ? (
              <>
                <span className="shrink-0 font-bold text-[#68756d]">
                  {title}
                </span>
                <ChevronRight
                  className="h-3 w-3 shrink-0 text-[#9aa49e]"
                  strokeWidth={1.8}
                  aria-hidden="true"
                />
                <span className="flex min-w-0 items-center gap-1 overflow-hidden">
                  {compactSubNavigation.map((item, index) => (
                    <span
                      className="flex min-w-0 items-center gap-1"
                      key={item.href}
                    >
                      {index > 0 ? (
                        <span className="shrink-0 text-[#9aa49e]">·</span>
                      ) : null}
                      <Link
                        href={item.href}
                        className={`inline-flex !h-[15px] !min-h-0 items-center truncate leading-[15px] hover:text-[#214f31] ${
                          item.active
                            ? "font-bold text-[#17251b]"
                            : "font-medium text-[#68756d]"
                        }`}
                      >
                        {item.label}
                      </Link>
                    </span>
                  ))}
                </span>
              </>
            ) : (
              breadcrumbs.map((item, index) => (
                <span className="flex min-w-0 items-center gap-1" key={item}>
                  {index > 0 ? (
                    <ChevronRight
                      className="h-3 w-3 shrink-0 text-[#9aa49e]"
                      strokeWidth={1.8}
                      aria-hidden="true"
                    />
                  ) : null}
                  <span
                    className={`truncate ${
                      index === breadcrumbs.length - 1
                        ? "font-bold text-[#17251b]"
                        : "font-medium text-[#68756d]"
                    }`}
                  >
                    {item}
                  </span>
                </span>
              ))
            )}
          </h1>

          {/*
            Deprecated: 넓은 페이지 헤더는 더 이상 사용하지 않는다.
            <h1 className="truncate text-xl font-bold text-[#17251b] transition-[font-size,line-height] duration-200 ease-out">
              {title}
            </h1>
            <p className="max-h-5 max-w-[40rem] translate-y-0 truncate text-sm text-[#7a8680] opacity-100 transition-[max-width,max-height,opacity,transform] duration-200 ease-out">
              {_description}
            </p>
          */}
        </div>

        <div className="flex shrink-0 items-center gap-3 text-xs text-[#4f5d55] transition-[font-size] duration-200 ease-out">
          {/* <div className="relative flex h-8 w-8 items-center justify-center text-[#718078]">
            <Bell className="h-5 w-5" strokeWidth={1.8} aria-hidden="true" />
            {notificationCount > 0 ? (
              <span className="absolute top-0 right-0 flex h-4 min-w-4 items-center justify-center rounded-full bg-[#ef2f2f] px-1 text-[10px] leading-none font-bold text-white">
                {notificationCount}
              </span>
            ) : null} TODO: 알림 기능 구현 전 비활성화
          </div>

          <span className="hidden h-6 w-px bg-[#d7ddd4] sm:block" /> */}

          <div className="hidden items-center gap-2 sm:flex">
            <CalendarDays
              className="h-[1.25em] w-[1.25em] shrink-0 text-[#6c7a72]"
              strokeWidth={1.8}
              aria-hidden="true"
            />
            <span>{todayLabel}</span>
          </div>

          {/* <span className="hidden h-6 w-px bg-[#d7ddd4] md:block" />

          <div className="hidden items-center gap-2 md:flex">
            <CloudSun
              className="h-6 w-6 text-[#3a8cff]"
              strokeWidth={1.8}
              aria-hidden="true"
            />
            <span className="font-semibold text-[#2f3a34]">
              {temperatureLabel}
            </span>
          </div> TODO: 온실 내부 온도 연동 전 비활성화*/}
        </div>
      </div>

      {/*
        Deprecated: 넓은 헤더 하단 영역은 더 이상 사용하지 않는다.
        {children ? (
          <div className="border-t border-[#edf0ec] px-4 md:px-5">
            {children}
          </div>
        ) : null}
      */}
    </header>
  );
}

function subscribeDateLabel() {
  return () => {};
}

function getServerDateLabel() {
  return "";
}

function getTodayLabel() {
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    weekday: "short",
  }).format(new Date());
}
