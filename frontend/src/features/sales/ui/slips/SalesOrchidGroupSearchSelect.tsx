"use client";

import { useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { MapPin } from "lucide-react";
import { fetchApi } from "@/shared/api/client";
import type { House, SalesOrchidGroupOption } from "@/entities/farm/types";
import { FarmGroupSelectionDialog } from "@/entities/farm/ui/FarmGroupSelectionDialog";
import { searchSalesOrchidGroups } from "../../api/salesApi";

export function SalesOrchidGroupSearchSelect({
  label = "난 묶음 선택",
  buttonClassName,
  disabled = false,
  selectedIds = [],
  initialKeyword = "",
  onSelect,
}: {
  label?: string;
  buttonClassName?: string;
  disabled?: boolean;
  selectedIds?: string[];
  initialKeyword?: string;
  onSelect: (value: SalesOrchidGroupOption[]) => void;
}) {
  const [open, setOpen] = useState(false);
  const opener = useRef<HTMLButtonElement>(null);
  const houses = useQuery({
    queryKey: ["farm-structure", "houses"],
    queryFn: ({ signal }) => fetchApi<House[]>("/houses", { signal }),
    enabled: open,
  });
  const options = useQuery({
    queryKey: ["sales", "orchid-group-options", ""],
    queryFn: ({ signal }) => searchSalesOrchidGroups("", undefined, signal),
    enabled: open,
    staleTime: 0,
  });
  const available = useMemo(
    () =>
      new Map(
        (options.data ?? [])
          .filter(
            (group) =>
              !selectedIds.includes(String(group.id)) &&
              group.availableQuantity > 0,
          )
          .map((group) => [group.id, group]),
      ),
    [options.data, selectedIds],
  );
  const groups = useMemo(
    () =>
      (houses.data ?? [])
        .flatMap((house) =>
          house.physicalBeds.flatMap((bed) =>
            bed.bedZones.flatMap((zone) => zone.orchidGroups),
          ),
        )
        .filter((group) => available.has(group.id))
        .map((group) => ({
          ...group,
          quantity: available.get(group.id)!.availableQuantity,
        })),
    [houses.data, available],
  );
  const close = () => {
    setOpen(false);
    opener.current?.focus();
  };
  return (
    <>
      <button
        ref={opener}
        className={
          buttonClassName ??
          "flex h-10 w-full items-center justify-center gap-2 rounded-md border border-[#cfd8cc] bg-white px-3 text-sm font-semibold text-[#29422e] shadow-sm hover:bg-[#f7faf6] disabled:opacity-60"
        }
        disabled={disabled}
        type="button"
        onClick={() => setOpen(true)}
      >
        <MapPin className="h-4 w-4" aria-hidden="true" />
        {label}
      </button>
      {open ? (
        <FarmGroupSelectionDialog
          loading={houses.isPending || options.isFetching}
          error={
            houses.isError || options.isError
              ? "난 묶음을 불러오지 못했습니다."
              : null
          }
          onRetry={() => {
            void houses.refetch();
            void options.refetch();
          }}
          title="난 묶음 선택"
          description="여러 품종을 함께 선택하세요. 같은 속·품종끼리 품목이 만들어집니다. 수량은 가용 수량입니다."
          searchPlaceholder="위치 또는 품종 검색"
          houses={houses.data ?? []}
          groups={groups}
          initialSelectedIds={new Set()}
          initialKeyword={initialKeyword}
          onClose={close}
          onConfirm={(ids) => {
            onSelect(
              [...ids].flatMap((id) => {
                const option = available.get(id);
                return option ? [option] : [];
              }),
            );
            close();
          }}
        />
      ) : null}
    </>
  );
}
