"use client";

import { Bell, CalendarDays, CloudSun } from "lucide-react";

type PageHeaderProps = {
  title: string;
  description: string;
  notificationCount?: number;
  temperatureLabel?: string;
};

export function PageHeader({
  title,
  description,
  notificationCount = 3,
  temperatureLabel = "24°C",
}: PageHeaderProps) {
  const todayLabel = new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    weekday: "short",
  }).format(new Date());

  return (
    <header className="flex min-h-10 items-center justify-between gap-4 border-b border-[#edf0ec] bg-white px-4 py-3 shadow-[0_1px_8px_rgba(31,42,36,0.04)] md:px-5">
      <div className="flex items-end gap-2 min-w-0">
        <h1 className="truncate text-xl font-bold text-[#17251b]">{title}</h1>
        <p className="truncate text-sm text-[#7a8680]">{description}</p>
      </div>

      <div className="flex shrink-0 items-center gap-3 text-sm text-[#4f5d55]">
        <div className="relative flex h-8 w-8 items-center justify-center text-[#718078]">
          <Bell className="h-5 w-5" strokeWidth={1.8} aria-hidden="true" />
          {notificationCount > 0 ? (
            <span className="absolute right-0 top-0 flex h-4 min-w-4 items-center justify-center rounded-full bg-[#ef2f2f] px-1 text-[10px] font-bold leading-none text-white">
              {notificationCount}
            </span>
          ) : null}
        </div>

        <span className="hidden h-6 w-px bg-[#d7ddd4] sm:block" />

        <div className="hidden items-center gap-2 sm:flex">
          <CalendarDays className="h-5 w-5 text-[#6c7a72]" strokeWidth={1.8} aria-hidden="true" />
          <span>{todayLabel}</span>
        </div>

        <span className="hidden h-6 w-px bg-[#d7ddd4] md:block" />

        <div className="hidden items-center gap-2 md:flex">
          <CloudSun className="h-6 w-6 text-[#3a8cff]" strokeWidth={1.8} aria-hidden="true" />
          <span className="font-semibold text-[#2f3a34]">{temperatureLabel}</span>
        </div>
      </div>
    </header>
  );
}
