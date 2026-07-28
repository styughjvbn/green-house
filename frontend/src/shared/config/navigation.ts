import {
  ANALYTICS_NAV_ITEMS,
  ANALYTICS_ROUTE,
  COMMON_ROUTES,
  INVENTORY_NAV_ITEMS,
  INVENTORY_ROUTE,
  SALES_NAV_ITEMS,
  SALES_ROUTE,
} from "@/shared/config/routes";
import {
  BarChart3,
  ClipboardList,
  Flower2,
  Home,
  PackageCheck,
  Settings,
  ShoppingBag,
  Sprout,
  type LucideIcon,
} from "lucide-react";

export type NavigationChild = {
  href: string;
  label: string;
  tab: string;
};

export type NavigationItem = {
  href: string;
  sectionHref?: string;
  label: string;
  icon: LucideIcon;
  children?: NavigationChild[];
};

export type PageMeta = {
  href: string;
  title: string;
  description: string;
};

export const PAGE_META = [
  {
    href: COMMON_ROUTES.home,
    title: "대시보드",
    description: "농장 운영 현황을 한눈에 확인하세요.",
  },
  {
    href: COMMON_ROUTES.farmStatus,
    title: "농장 현황",
    description: "전체 농장 구조와 묶음 현황을 한눈에 확인하세요.",
  },
  {
    href: COMMON_ROUTES.orchidGroups,
    title: "난 묶음 관리",
    description: "난 묶음의 위치와 상태를 등록하고 관리하세요.",
  },
  {
    href: COMMON_ROUTES.workRecords,
    title: "작업 관리",
    description: "농장 작업을 등록하고 일정과 이력을 관리하세요.",
  },
  {
    href: SALES_ROUTE.root,
    title: "판매 관리",
    description: "판매 내역과 거래 정보를 관리하세요.",
  },
  {
    href: ANALYTICS_ROUTE.root,
    title: "분석",
    description: "출하, 판매, 농장 현황 데이터를 분석하세요.",
  },
  {
    href: INVENTORY_ROUTE.root,
    title: "품종/자재 관리",
    description: "품종과 자재, 비료 정보를 등록하고 관리하세요.",
  },
  {
    href: COMMON_ROUTES.settings,
    title: "설정",
    description: "서비스 설정을 관리하세요.",
  },
] satisfies PageMeta[];

export const NAVIGATION = [
  { href: COMMON_ROUTES.home, label: "대시보드", icon: Home },
  { href: COMMON_ROUTES.farmStatus, label: "농장 현황", icon: Sprout },
  { href: COMMON_ROUTES.orchidGroups, label: "난 묶음 관리", icon: Flower2 },
  {
    href: COMMON_ROUTES.workRecords,
    label: "작업 관리",
    icon: ClipboardList,
  },
  {
    href: SALES_ROUTE.tab("slips"),
    sectionHref: SALES_ROUTE.root,
    label: "판매 관리",
    icon: ShoppingBag,
    children: SALES_NAV_ITEMS,
  },
  {
    href: ANALYTICS_ROUTE.tab("sales"),
    sectionHref: ANALYTICS_ROUTE.root,
    label: "분석",
    icon: BarChart3,
    children: ANALYTICS_NAV_ITEMS,
  },
  {
    href: INVENTORY_ROUTE.tab("variety"),
    sectionHref: INVENTORY_ROUTE.root,
    label: "품종/자재 관리",
    icon: PackageCheck,
    children: INVENTORY_NAV_ITEMS,
  },
  { href: COMMON_ROUTES.settings, label: "설정", icon: Settings },
] satisfies NavigationItem[];
