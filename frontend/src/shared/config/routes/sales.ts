export const SALES_TABS = {
  slips: {
    label: "전표 관리",
  },
  auction: {
    label: "경매 관리",
  },
  settlement: {
    label: "경매 정산",
  },
  payments: {
    label: "입금 관리",
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
  return Object.hasOwn(SALES_TABS, value);
}

export const SALES_NAV_ITEMS = Object.entries(SALES_TABS).map(
  ([tab, config]) => ({
    tab: tab as SalesTab,
    label: config.label,
    href: SALES_ROUTE.tab(tab as SalesTab),
  }),
);

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
