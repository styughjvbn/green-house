export const SALES_TABS = {
  slips: {
    label: "전표 관리",
  },
  auction: {
    label: "경매 관리",
  },
  // TODO: 수납 비활성화 — 전표 등록 마무리 후 연결 예정.
  settlement: {
    enabled: false,
    label: "경매 정산",
  },
  payments: {
    enabled: false,
    label: "수납 관리",
  },
  partners: {
    label: "거래처 관리",
  },
} as const;

export type SalesTab = keyof typeof SALES_TABS;

export const SALES_ROUTE = {
  root: "/sales",
  tab: (tab: SalesTab) => `/sales/${tab}` as const,
} as const;

export const DEFAULT_SALES_TAB: SalesTab = "slips";

export function isSalesTab(value: string): value is SalesTab {
  if (!Object.hasOwn(SALES_TABS, value)) return false;
  const config = SALES_TABS[value as SalesTab];
  return !("enabled" in config) || config.enabled !== false;
}

export const SALES_NAV_ITEMS = Object.entries(SALES_TABS)
  .filter(([tab]) => isSalesTab(tab))
  .map(([tab, config]) => ({
    tab: tab as SalesTab,
    label: config.label,
    href: SALES_ROUTE.tab(tab as SalesTab),
  }));

export function salesHref(
  tab: SalesTab,
  values: Record<string, string | number> = {},
) {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(values))
    params.set(key, String(value));
  const query = params.toString();
  return `${SALES_ROUTE.tab(tab)}${query ? `?${query}` : ""}`;
}
