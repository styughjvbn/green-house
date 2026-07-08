"use client";

import { useMemo, useState } from "react";
import { MapPin, Search, X } from "lucide-react";
import type { SalesOrchidGroupOption } from "@/entities/farm/types";
import { searchSalesOrchidGroups } from "../../api/salesApi";

type StatusFilter = "ALL" | "정상" | "주의" | "이상";

export function SalesOrchidGroupSearchSelect({
  disabled = false,
  initialKeyword = "",
  selectedIds = [],
  onSelect,
}: {
  disabled?: boolean;
  initialKeyword?: string;
  selectedIds?: string[];
  onSelect: (value: SalesOrchidGroupOption) => void;
}) {
  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState(initialKeyword);
  const [status, setStatus] = useState<StatusFilter>("ALL");
  const [selectedHouse, setSelectedHouse] = useState<number | null>(null);
  const [items, setItems] = useState<SalesOrchidGroupOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const selectedIdSet = useMemo(() => new Set(selectedIds), [selectedIds]);

  async function load(nextKeyword = keyword) {
    setLoading(true);
    setError(null);
    try {
      const result = await searchSalesOrchidGroups(nextKeyword);
      setItems(result);
      setSelectedHouse((current) => {
        if (
          current != null &&
          result.some((item) => item.houseNumber === current)
        ) {
          return current;
        }
        return result[0]?.houseNumber ?? null;
      });
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "난 묶음을 불러오지 못했습니다.",
      );
    } finally {
      setLoading(false);
    }
  }

  function openPicker() {
    const nextKeyword = initialKeyword.trim();
    setKeyword(nextKeyword);
    void load(nextKeyword);
    setOpen(true);
  }

  const filteredItems = useMemo(() => {
    return items.filter((item) => {
      if (status !== "ALL" && item.status !== status) return false;
      return true;
    });
  }, [items, status]);

  const houseCounts = useMemo(() => {
    const counts = new Map<number, number>();
    filteredItems.forEach((item) => {
      counts.set(item.houseNumber, (counts.get(item.houseNumber) ?? 0) + 1);
    });
    return counts;
  }, [filteredItems]);

  const visibleItems = useMemo(() => {
    const scoped =
      selectedHouse == null
        ? filteredItems
        : filteredItems.filter((item) => item.houseNumber === selectedHouse);

    return [...scoped].sort((a, b) => {
      if (a.houseNumber !== b.houseNumber) return a.houseNumber - b.houseNumber;
      if (a.physicalBedNumber !== b.physicalBedNumber) {
        return a.physicalBedNumber - b.physicalBedNumber;
      }
      return a.bedZoneName.localeCompare(b.bedZoneName);
    });
  }, [filteredItems, selectedHouse]);

  const bedGroups = useMemo(() => groupByBed(visibleItems), [visibleItems]);

  return (
    <>
      <button
        className="flex h-10 w-full items-center justify-center gap-2 rounded-md border border-[#cfd8cc] bg-white px-3 text-sm font-semibold text-[#29422e] shadow-sm transition hover:bg-[#f7faf6] disabled:cursor-not-allowed disabled:opacity-60"
        disabled={disabled}
        type="button"
        onClick={openPicker}
      >
        <MapPin className="h-4 w-4" />난 묶음 선택
      </button>

      {open ? (
        <div
          className="fixed inset-0 z-[1200] flex items-center justify-center bg-black/35 p-4"
          role="presentation"
          onMouseDown={() => setOpen(false)}
        >
          <section
            className="flex max-h-[88vh] w-full max-w-6xl flex-col overflow-hidden rounded-md bg-white shadow-xl"
            role="dialog"
            aria-modal="true"
            aria-label="난 묶음 선택"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <header className="flex items-center justify-between border-b border-[#e1e7df] px-4 py-3">
              <div>
                <h2 className="text-base font-bold text-[#17251b]">
                  난 묶음 선택
                </h2>
                <p className="mt-0.5 text-xs text-[#66736a]">
                  위치를 확인한 뒤 판매 품목에 배분할 묶음을 선택하세요.
                </p>
              </div>
              <button
                className="flex h-8 w-8 items-center justify-center rounded-md border border-[#d7ddd4]"
                type="button"
                onClick={() => setOpen(false)}
                aria-label="닫기"
              >
                <X className="h-4 w-4" />
              </button>
            </header>

            <div className="grid min-h-0 flex-1 gap-0 lg:grid-cols-[280px_minmax(0,1fr)]">
              <aside className="border-b border-[#e1e7df] bg-[#f8faf7] p-4 lg:border-r lg:border-b-0">
                <form
                  className="space-y-3"
                  onSubmit={(event) => {
                    event.preventDefault();
                    void load();
                  }}
                >
                  <label className="block space-y-1">
                    <span className="text-xs font-semibold text-[#425047]">
                      검색어
                    </span>
                    <div className="flex gap-2">
                      <input
                        className="h-9 min-w-0 flex-1 rounded-md border border-[#cfd8cc] px-3 text-sm outline-none focus:border-[#159447] focus:ring-1 focus:ring-[#159447]"
                        value={keyword}
                        placeholder="품종명, 속, 위치"
                        onChange={(event) => setKeyword(event.target.value)}
                      />
                      <button
                        className="flex h-9 w-9 items-center justify-center rounded-md bg-[#159447] text-white"
                        type="submit"
                        aria-label="검색"
                      >
                        <Search className="h-4 w-4" />
                      </button>
                    </div>
                  </label>

                  <label className="block space-y-1">
                    <span className="text-xs font-semibold text-[#425047]">
                      상태
                    </span>
                    <select
                      className="h-9 w-full rounded-md border border-[#cfd8cc] px-3 text-sm outline-none focus:border-[#159447] focus:ring-1 focus:ring-[#159447]"
                      value={status}
                      onChange={(event) =>
                        setStatus(event.target.value as StatusFilter)
                      }
                    >
                      <option value="ALL">전체</option>
                      <option value="정상">정상</option>
                      <option value="주의">주의</option>
                      <option value="이상">이상</option>
                    </select>
                  </label>
                </form>

                <div className="mt-4">
                  <div className="mb-2 flex items-center justify-between">
                    <p className="text-xs font-bold text-[#425047]">동 선택</p>
                    <button
                      className={`rounded px-2 py-1 text-xs font-semibold ${
                        selectedHouse == null
                          ? "bg-[#159447] text-white"
                          : "border border-[#d7ddd4] bg-white text-[#425047]"
                      }`}
                      type="button"
                      onClick={() => setSelectedHouse(null)}
                    >
                      전체
                    </button>
                  </div>
                  <div className="grid grid-cols-5 gap-1.5">
                    {Array.from({ length: 15 }, (_, index) => index + 1).map(
                      (houseNumber) => {
                        const count = houseCounts.get(houseNumber) ?? 0;
                        const active = selectedHouse === houseNumber;

                        return (
                          <button
                            key={houseNumber}
                            className={`h-12 rounded-md border text-xs font-bold transition ${
                              active
                                ? "border-[#167c3a] bg-[#159447] text-white"
                                : count > 0
                                  ? "border-[#bddcc3] bg-white text-[#214d2e] hover:bg-[#eef8ef]"
                                  : "border-[#e1e6df] bg-[#f0f3ef] text-[#9aa49d]"
                            }`}
                            type="button"
                            onClick={() => setSelectedHouse(houseNumber)}
                          >
                            <span className="block">{houseNumber}동</span>
                            <span className="text-[10px] font-semibold">
                              {count}개
                            </span>
                          </button>
                        );
                      },
                    )}
                  </div>
                </div>

                <div className="mt-4 rounded-md border border-[#dfe5dc] bg-white p-3 text-xs text-[#66736a]">
                  검색 결과 {filteredItems.length.toLocaleString()}개 / 표시{" "}
                  {visibleItems.length.toLocaleString()}개
                </div>
              </aside>

              <div className="min-h-0 overflow-y-auto p-4">
                {error ? (
                  <p className="mb-3 rounded-md border border-[#efc6c2] bg-[#fff2f0] px-3 py-2 text-sm font-semibold text-[#b33d35]">
                    {error}
                  </p>
                ) : null}
                {loading ? (
                  <p className="rounded-md bg-[#f5f7f4] px-3 py-10 text-center text-sm text-[#66736a]">
                    난 묶음을 불러오는 중입니다.
                  </p>
                ) : visibleItems.length === 0 ? (
                  <p className="rounded-md bg-[#f5f7f4] px-3 py-10 text-center text-sm text-[#66736a]">
                    선택 가능한 난 묶음이 없습니다.
                  </p>
                ) : (
                  <div className="space-y-4">
                    {bedGroups.map((group) => (
                      <section
                        key={`${group.houseNumber}-${group.bedNumber}-${group.zoneName}`}
                        className="rounded-md border border-[#dfe5dc] bg-[#fbfcfa] p-3"
                      >
                        <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                          <h3 className="text-sm font-bold text-[#17251b]">
                            {group.houseNumber}동 {group.bedNumber}배드{" "}
                            {group.zoneName}
                          </h3>
                          <span className="rounded-full bg-[#eaf7eb] px-2 py-0.5 text-xs font-semibold text-[#167c3a]">
                            {group.items.length}개
                          </span>
                        </div>
                        <div className="grid gap-2 md:grid-cols-2 xl:grid-cols-3">
                          {group.items.map((item) => (
                            <OrchidGroupChoice
                              key={item.id}
                              disabled={selectedIdSet.has(String(item.id))}
                              item={item}
                              onSelect={() => {
                                onSelect(item);
                                setOpen(false);
                              }}
                            />
                          ))}
                        </div>
                      </section>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </section>
        </div>
      ) : null}
    </>
  );
}

function OrchidGroupChoice({
  disabled,
  item,
  onSelect,
}: {
  disabled: boolean;
  item: SalesOrchidGroupOption;
  onSelect: () => void;
}) {
  const statusClass =
    item.status === "정상"
      ? "bg-[#20a64d]"
      : item.status === "주의"
        ? "bg-[#f59e0b]"
        : "bg-[#ef4444]";

  return (
    <button
      className="min-w-0 rounded-md border border-[#dfe5dc] bg-white p-3 text-left shadow-sm transition hover:border-[#159447] hover:bg-[#f7fbf6] disabled:cursor-not-allowed disabled:opacity-55"
      disabled={disabled}
      type="button"
      onClick={onSelect}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate text-sm font-bold text-[#17251b]">
            {item.varietyName}
          </p>
          <p className="mt-0.5 truncate text-xs text-[#66736a]">
            {item.genus || "-"} / {item.potSize ?? "-"} /{" "}
            {item.ageYear != null ? `${item.ageYear}년생` : "-"}
          </p>
        </div>
        <span className={`h-2.5 w-2.5 shrink-0 rounded-full ${statusClass}`} />
      </div>
      <div className="mt-3 grid grid-cols-3 gap-2 text-xs">
        <Metric label="전체" value={`${item.quantity}분`} />
        <Metric label="예약" value={`${item.reservedQuantity}분`} />
        <Metric label="가용" value={`${item.availableQuantity}분`} strong />
      </div>
      <p className="mt-2 text-xs font-semibold text-[#486051]">
        {item.houseNumber}동 {item.physicalBedNumber}배드 {item.bedZoneName}
      </p>
      {disabled ? (
        <p className="mt-2 rounded bg-[#f0f3ef] px-2 py-1 text-center text-xs font-semibold text-[#66736a]">
          이미 선택됨
        </p>
      ) : null}
    </button>
  );
}

function Metric({
  label,
  value,
  strong = false,
}: {
  label: string;
  value: string;
  strong?: boolean;
}) {
  return (
    <span className="rounded bg-[#f5f7f4] px-2 py-1">
      <span className="block text-[10px] text-[#768179]">{label}</span>
      <span
        className={`block ${strong ? "font-bold text-[#159447]" : "font-semibold text-[#344039]"}`}
      >
        {value}
      </span>
    </span>
  );
}

function groupByBed(items: SalesOrchidGroupOption[]) {
  const groups = new Map<
    string,
    {
      houseNumber: number;
      bedNumber: number;
      zoneName: string;
      items: SalesOrchidGroupOption[];
    }
  >();

  items.forEach((item) => {
    const key = `${item.houseNumber}-${item.physicalBedNumber}-${item.bedZoneName}`;
    const group = groups.get(key);
    if (group) {
      group.items.push(item);
      return;
    }
    groups.set(key, {
      houseNumber: item.houseNumber,
      bedNumber: item.physicalBedNumber,
      zoneName: item.bedZoneName,
      items: [item],
    });
  });

  return Array.from(groups.values()).map((group) => ({
    ...group,
    items: [...group.items].sort((a, b) =>
      a.varietyName.localeCompare(b.varietyName),
    ),
  }));
}
