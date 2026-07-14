"use client";

import { useEffect, useState } from "react";
import { Layers3, LoaderCircle } from "lucide-react";
import type { OrchidGroup } from "@/entities/farm/types";
import { formatPotSize } from "@/entities/farm/potSizes";
import {
  getDerivedOrchidGroupMembers,
  getDerivedOrchidGroups,
} from "../../api/orchidManagementApi";
import type { DerivedOrchidGroup } from "../../model/types";

export default function DerivedGroupPanel({
  houseId,
  onSelectMembers,
}: {
  houseId: number;
  onSelectMembers: (members: OrchidGroup[]) => void;
}) {
  const [groups, setGroups] = useState<DerivedOrchidGroup[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingKey, setLoadingKey] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void getDerivedOrchidGroups(houseId)
      .then((result) => {
        if (!cancelled) setGroups(result);
      })
      .catch((error: unknown) => {
        if (!cancelled) setErrorMessage(toMessage(error));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [houseId]);

  async function selectGroup(group: DerivedOrchidGroup) {
    if (loadingKey) return;
    setLoadingKey(group.groupKey);
    setErrorMessage(null);
    try {
      onSelectMembers(
        await getDerivedOrchidGroupMembers(group.groupKey, houseId),
      );
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setLoadingKey(null);
    }
  }

  return (
    <section className="shrink-0 rounded-md border border-[#d7ddd4] bg-white p-3 shadow-sm">
      <div className="flex items-center justify-between gap-2">
        <div>
          <p className="flex items-center gap-1.5 text-sm font-semibold text-[#17251b]">
            <Layers3 className="h-4 w-4 text-[#159447]" />
            자동 그룹
          </p>
          <p className="mt-0.5 text-[11px] text-[#6a766e]">
            품종·현재 년생·화분 크기가 같은 묶음
          </p>
        </div>
        {loading ? (
          <LoaderCircle className="h-4 w-4 animate-spin text-[#159447]" />
        ) : (
          <span className="text-xs font-semibold text-[#58705e]">
            {groups.length}개
          </span>
        )}
      </div>

      <div className="mt-3 max-h-52 space-y-2 overflow-y-auto pr-1">
        {groups.map((group) => (
          <button
            className="flex w-full items-center justify-between gap-3 rounded-md border border-[#e1e6df] bg-white px-3 py-2 text-left hover:border-[#159447] disabled:opacity-60"
            disabled={loadingKey !== null}
            key={group.groupKey}
            onClick={() => void selectGroup(group)}
            type="button"
          >
            <span className="min-w-0">
              <span className="block truncate text-xs font-semibold text-[#26352b]">
                {group.varietyName}
              </span>
              <span className="mt-0.5 block text-[11px] text-[#6a766e]">
                {group.ageYear == null ? "년생 미지정" : `${group.ageYear}년생`}{" "}
                · {formatPotSize(group.potSizeCode, group.potSize)} · 위치{" "}
                {group.locationCount}곳
              </span>
            </span>
            <span className="shrink-0 text-right text-[11px] font-semibold text-[#34503b]">
              {loadingKey === group.groupKey ? (
                <LoaderCircle className="ml-auto h-4 w-4 animate-spin" />
              ) : (
                <>
                  <span className="block">{group.orchidGroupCount}묶음</span>
                  <span className="block text-[#6a766e]">
                    {group.totalQuantity}분
                  </span>
                </>
              )}
            </span>
          </button>
        ))}
        {!loading && groups.length === 0 ? (
          <p className="rounded-md bg-[#f5f7f3] p-3 text-xs text-[#5c6a60]">
            이 동에서 자동으로 묶을 난이 없습니다.
          </p>
        ) : null}
      </div>
      {errorMessage ? (
        <p className="mt-2 rounded-md border border-[#f1b0a0] bg-[#fff1ec] p-2 text-xs text-[#9b341e]">
          {errorMessage}
        </p>
      ) : null}
    </section>
  );
}

function toMessage(error: unknown) {
  return error instanceof Error
    ? error.message
    : "자동 그룹을 불러오지 못했습니다.";
}
