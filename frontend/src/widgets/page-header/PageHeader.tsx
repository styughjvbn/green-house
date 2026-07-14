"use client";

import { Bell, CalendarDays, CloudSun } from "lucide-react";
import type { ReactNode } from "react";
import { useSyncExternalStore } from "react";

type PageHeaderProps = {
  title: string;
  description: string;
  collapsed?: boolean;
  notificationCount?: number;
  temperatureLabel?: string;
  children?: ReactNode;
};

export function PageHeader({
  title,
  description,
  collapsed = false,
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
    <header className="border-b border-[#edf0ec] bg-white shadow-[0_1px_8px_rgba(31,42,36,0.04)] transition-[border-color,box-shadow] duration-200 ease-out">
      <div
        className={`flex items-center justify-between gap-4 px-4 transition-[min-height,padding] duration-200 ease-out md:px-5 ${
          collapsed ? "min-h-6" : "min-h-10 py-3"
        }`}
      >
        <div className="flex min-w-0 items-end gap-2">
          <h1
            className={`truncate font-bold text-[#17251b] transition-[font-size,line-height] duration-200 ease-out ${
              collapsed ? "text-xs" : "text-xl"
            }`}
          >
            {title}
          </h1>
          <p
            className={`truncate text-sm text-[#7a8680] transition-[max-width,max-height,opacity,transform] duration-200 ease-out ${
              collapsed
                ? "max-h-0 max-w-0 translate-y-0.5 opacity-0"
                : "max-h-5 max-w-[40rem] translate-y-0 opacity-100"
            }`}
          >
            {description}
          </p>
        </div>

        <div
          className={`flex shrink-0 items-center gap-3 text-[#4f5d55] transition-[font-size] duration-200 ease-out ${collapsed ? "text-xs" : "text-sm"} `}
        >
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

      {children && !collapsed ? (
        <div className="border-t border-[#edf0ec] px-4 md:px-5">{children}</div>
      ) : null}
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
