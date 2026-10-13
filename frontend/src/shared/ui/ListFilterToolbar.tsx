"use client";

import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { Funnel, X } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import {
  FilterInput,
  FilterResetButton,
  FilterSearchButton,
} from "./FilterControls";

export type AppliedFilterBadge = {
  key: string;
  label: string;
  onRemove?: () => void;
};

export function ListFilterToolbar({
  activeCount = 0,
  appliedBadges = [],
  appliedKeyword = "",
  checkboxFilters,
  children,
  keyword,
  leadingControls,
  loading = false,
  searchLabel = "키워드",
  searchPlaceholder = "검색",
  settingsControl,
  showSearch = true,
  onClearKeyword,
  onKeywordChange,
  onReset,
  onResetFilters,
  onSearch,
}: {
  activeCount?: number;
  appliedBadges?: readonly AppliedFilterBadge[];
  appliedKeyword?: string;
  checkboxFilters?: ReactNode;
  children: ReactNode;
  keyword: string;
  leadingControls?: ReactNode;
  loading?: boolean;
  searchLabel?: string;
  searchPlaceholder?: string;
  settingsControl?: ReactNode;
  showSearch?: boolean;
  onClearKeyword: () => void;
  onKeywordChange: (keyword: string) => void;
  onReset: () => void;
  onResetFilters: () => void;
  onSearch: () => void;
}) {
  const [open, setOpen] = useState(false);
  const filterId = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLDivElement>(null);
  const hasAppliedSearch = Boolean(appliedKeyword.trim());

  useEffect(() => {
    if (!open) return;
    panelRef.current
      ?.querySelector<HTMLElement>("button, input, select")
      ?.focus();
    function handlePointerDown(event: PointerEvent) {
      if (
        event.target instanceof Node &&
        !rootRef.current?.contains(event.target)
      )
        setOpen(false);
    }
    document.addEventListener("pointerdown", handlePointerDown);
    return () => document.removeEventListener("pointerdown", handlePointerDown);
  }, [open]);

  function close() {
    setOpen(false);
    triggerRef.current?.focus();
  }

  return (
    <div className="space-y-2">
      <div className="relative flex items-center gap-2">
        {leadingControls ? (
          <div className="shrink-0">{leadingControls}</div>
        ) : null}
        {showSearch ? (
          <form
            className="group/search flex min-w-0 flex-1 items-center gap-2"
            onSubmit={(event) => {
              event.preventDefault();
              onSearch();
            }}
          >
            <div ref={searchRef} className="relative min-w-0 flex-1">
              <FilterInput
                className="pr-9 pl-3"
                hideLabel
                label={searchLabel}
                placeholder={searchPlaceholder}
                value={keyword}
                onChange={onKeywordChange}
              />
              {keyword.length > 0 || appliedKeyword.length > 0 ? (
                <button
                  aria-label="검색어 조건 해제"
                  className="absolute top-1/2 right-2 inline-flex h-5 w-5 -translate-y-1/2 items-center justify-center rounded text-[#657168] hover:bg-[#eef5ed] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#16713a]"
                  type="button"
                  onClick={() => {
                    onClearKeyword();
                    searchRef.current?.querySelector("input")?.focus();
                  }}
                >
                  <X aria-hidden="true" className="h-3 w-3" strokeWidth={1.8} />
                </button>
              ) : null}
            </div>
            <FilterSearchButton
              className={cn(
                "shrink-0 border border-[#dfe5dc] px-0 text-[#435047] group-has-[input:focus]/search:border-[#cfd8cc] group-has-[input:focus]/search:bg-[#eef5ed] group-has-[input:focus]/search:text-[#16713a] group-has-[input:focus]/search:hover:bg-[#e7f0e6] lg:mt-0",
                hasAppliedSearch
                  ? "border-[#cfd8cc] bg-[#eef5ed] text-[#16713a] hover:bg-[#e7f0e6]"
                  : "bg-white hover:bg-[#f6f8f5]",
              )}
              disabled={loading}
              iconOnly
              label="검색"
            />
          </form>
        ) : (
          <div className="flex-1" />
        )}
        <div
          ref={rootRef}
          className="shrink-0"
          onBlur={(event) => {
            if (
              event.relatedTarget instanceof Node &&
              !event.currentTarget.contains(event.relatedTarget)
            )
              setOpen(false);
          }}
          onKeyDown={(event) => {
            if (open && event.key === "Escape") {
              event.preventDefault();
              event.stopPropagation();
              close();
            }
          }}
        >
          <button
            ref={triggerRef}
            aria-controls={open ? `${filterId}-panel` : undefined}
            aria-expanded={open}
            aria-haspopup="dialog"
            aria-label={`필터 선택${activeCount > 0 ? `, ${activeCount}개 적용` : ""}`}
            className="relative inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md border border-[#dfe5dc] bg-white text-[#344138] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#16713a]"
            type="button"
            onClick={() => setOpen((value) => !value)}
          >
            <Funnel aria-hidden="true" className="h-4 w-4" strokeWidth={1.8} />
            {activeCount > 0 ? (
              <span className="text-xxs absolute -top-1 -right-1 rounded bg-[#e7f0e6] px-1 leading-3 text-[#214f31]">
                {activeCount}
              </span>
            ) : null}
          </button>
          {open ? (
            <div
              ref={panelRef}
              aria-labelledby={`${filterId}-title`}
              id={`${filterId}-panel`}
              role="dialog"
              tabIndex={-1}
              className={cn(
                "absolute top-10 right-0 z-30 flex max-h-[calc(100dvh-16rem)] w-[32rem] max-w-[calc(100vw-2rem)] flex-col overflow-hidden rounded-md border border-[#dfe5dc] bg-white shadow-xl sm:max-h-[calc(100dvh-8rem)]",
                settingsControl
                  ? "sm:right-10 sm:max-w-[min(calc(100%-2.5rem),calc(100vw-2rem))]"
                  : "sm:max-w-[min(100%,calc(100vw-2rem))]",
              )}
            >
              <header className="flex shrink-0 items-center justify-between gap-2 border-b border-[#e7ebe5] px-4 py-3">
                <h3
                  className="text-sm font-bold text-[#17251b]"
                  id={`${filterId}-title`}
                >
                  필터
                </h3>
              </header>
              <form
                className="flex min-h-0 flex-col"
                onSubmit={(event) => {
                  event.preventDefault();
                  onSearch();
                  close();
                }}
              >
                <div className="min-h-0 overflow-y-auto px-4">
                  {children}
                  {checkboxFilters ? (
                    <div className="flex flex-wrap gap-x-4 gap-y-1 py-3">
                      {checkboxFilters}
                    </div>
                  ) : null}
                </div>
                <footer className="flex shrink-0 items-center justify-between gap-2 border-t border-[#e7ebe5] p-4">
                  <FilterResetButton
                    className="lg:mt-0"
                    onClick={onResetFilters}
                  />
                  <button
                    className="h-8 rounded-md bg-[#159447] px-3 text-xs font-semibold text-white disabled:opacity-60"
                    disabled={loading}
                    type="submit"
                  >
                    필터 적용
                  </button>
                </footer>
              </form>
            </div>
          ) : null}
        </div>
        {settingsControl}
      </div>
      {appliedBadges.length > 0 ? (
        <div
          aria-label="적용된 필터"
          role="group"
          className="flex min-w-0 items-center gap-2"
        >
          <ul
            aria-label="적용된 필터"
            className="flex min-w-0 flex-1 [scrollbar-width:thin] items-center gap-1.5 overflow-x-auto"
          >
            {appliedBadges.map((badge) => (
              <li
                className="inline-flex shrink-0 items-center gap-1 rounded-md border border-[#cfd8cc] bg-[#eef5ed] px-2 py-1 text-xs font-medium whitespace-nowrap text-[#16713a]"
                key={badge.key}
              >
                {badge.label}
                {badge.onRemove ? (
                  <button
                    aria-label={`${badge.label} 조건 해제`}
                    className="inline-flex h-5 w-5 shrink-0 items-center justify-center rounded hover:bg-[#dcebd9] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#16713a]"
                    type="button"
                    onClick={badge.onRemove}
                  >
                    <X
                      aria-hidden="true"
                      className="h-3 w-3"
                      strokeWidth={1.8}
                    />
                  </button>
                ) : null}
              </li>
            ))}
          </ul>
          <FilterResetButton
            className="shrink-0 lg:mt-0"
            label="전체 초기화"
            onClick={onReset}
          />
        </div>
      ) : null}
    </div>
  );
}
