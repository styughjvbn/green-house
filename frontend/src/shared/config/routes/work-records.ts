export const WORK_RECORD_TABS = {
  list: {
    label: "작업 목록",
  },
  calendar: {
    label: "캘린더",
  },
  history: {
    label: "작업 이력",
  },
} as const;

export type WorkRecordTab = keyof typeof WORK_RECORD_TABS;

export const WORK_RECORD_ROUTE = {
  root: "/work-records",
  tab: (tab: WorkRecordTab) => `/work-records/${tab}` as const,
} as const;

export const DEFAULT_WORK_RECORD_TAB: WorkRecordTab = "list";

export function isWorkRecordTab(value: string): value is WorkRecordTab {
  return Object.hasOwn(WORK_RECORD_TABS, value);
}

export const WORK_RECORD_NAV_ITEMS = Object.entries(WORK_RECORD_TABS).map(
  ([tab, config]) => ({
    tab: tab as WorkRecordTab,
    label: config.label,
    href: WORK_RECORD_ROUTE.tab(tab as WorkRecordTab),
  }),
);
