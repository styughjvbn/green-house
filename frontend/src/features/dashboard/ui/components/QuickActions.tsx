import Link from "next/link";
import {
  ClipboardEdit,
  FileText,
  Map,
  PlusCircle,
  Printer,
  Search,
  type LucideIcon,
} from "lucide-react";
import { DashboardPanel } from "./DashboardPanel";

export function QuickActions() {
  const actions: Array<[string, string, LucideIcon, string]> = [
    ["농장 현황 보기", "/farm-status", Map, "green"],
    ["난 묶음 추가", "/orchid-groups", PlusCircle, "green"],
    ["작업 관리", "/work-records", ClipboardEdit, "green"],
    ["판매 전표 등록", "/sales", FileText, "green"],
    ["출력하기", "/print", Printer, "blue"],
    ["검색하기", "/farm-status", Search, "blue"],
  ];

  return (
    <DashboardPanel title="빠른 작업">
      <div className="grid grid-cols-3 gap-3">
        {actions.map(([label, href, Icon, tone]) => (
          <Link
            key={label}
            className="flex min-h-24 flex-col items-center justify-center gap-2 rounded-md border border-[#dfe5dc] bg-white text-sm font-semibold text-[#344138] hover:bg-[#eef7ec]"
            href={href}
          >
            <Icon
              className={`h-8 w-8 ${tone === "blue" ? "text-[#246df2]" : "text-[#159447]"}`}
              strokeWidth={1.8}
              aria-hidden="true"
            />
            {label}
          </Link>
        ))}
      </div>
    </DashboardPanel>
  );
}
