export const INVENTORY_TABS = {
  variety: {
    label: "품종 관리",
  },
  inbound: {
    label: "입고 관리",
  },
  material: {
    label: "자재 관리",
  },
} as const;

export type InventoryTab = keyof typeof INVENTORY_TABS;

export const INVENTORY_ROUTE = {
  root: "/inventory",
  tab: (tab: InventoryTab) => `/inventory/${tab}` as const,
} as const;

export const DEFAULT_INVENTORY_TAB: InventoryTab = "variety";

export function isInventoryTab(value: string): value is InventoryTab {
  return Object.hasOwn(INVENTORY_TABS, value);
}

export const INVENTORY_NAV_ITEMS = Object.entries(INVENTORY_TABS).map(
  ([tab, config]) => ({
    tab: tab as InventoryTab,
    label: config.label,
    href: INVENTORY_ROUTE.tab(tab as InventoryTab),
  }),
);
