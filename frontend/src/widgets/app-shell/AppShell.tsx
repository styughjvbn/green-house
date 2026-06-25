"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  LayoutDashboard,
  Sprout,
  Flower2,
  ClipboardList,
  ShoppingCart,
  Printer,
  Settings,
  type LucideIcon,
} from "lucide-react";

const navigation: {
  href: string;
  label: string;
  icon: LucideIcon;
}[] = [
  { href: "/", label: "대시보드", icon: LayoutDashboard },
  { href: "/farm-status", label: "농장 현황", icon: Sprout },
  { href: "/orchid-groups", label: "난 묶음 관리", icon: Flower2 },
  { href: "/work-records", label: "작업 이력", icon: ClipboardList },
  { href: "/sales", label: "판매 관리", icon: ShoppingCart },
  { href: "/print", label: "출력", icon: Printer },
  { href: "/settings", label: "설정", icon: Settings },
];

function NavItem({
  href,
  label,
  icon: Icon,
  active,
  mobile = false,
}: {
  href: string;
  label: string;
  icon: LucideIcon;
  active: boolean;
  mobile?: boolean;
}) {
  return (
    <Link
      href={href}
      className={`flex items-center gap-3 font-medium transition ${
        mobile ? "shrink-0 px-4 py-2 text-base" : "px-4 py-3 text-lg"
      } ${
        active
          ? "bg-[#e7f0e6] text-[#214f31]"
          : mobile
            ? "bg-[#f0f3ef] text-[#435047]"
            : "text-[#435047] hover:bg-[#f0f3ef]"
      }`}
    >
      <Icon
        className={`${mobile ? "h-4 w-4" : "h-5 w-5"} shrink-0`}
        strokeWidth={2}
        aria-hidden="true"
      />
      <span>{label}</span>
    </Link>
  );
}

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
        </header>

        <div className="app-content px-5 py-6 md:px-8 lg:px-10">{children}</div>
      </div>
    </div>
  );
}
