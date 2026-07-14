"use client";

import { Pencil, Plus, Trash2 } from "lucide-react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useMemo, useState } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { formatShortDate } from "@/shared/lib/dateFormat";
import { DataTable } from "@/shared/ui/DataTable";
import {
  DetailActionButton,
  DetailCard,
  DetailHeader,
  DetailSummary,
} from "@/shared/ui/DetailCard";
import {
  FilterField,
  FilterGrid,
  FilterPanel,
  FilterResetButton,
  FilterSearchButton,
} from "@/shared/ui/FilterControls";
import { TabSplit, TabStack } from "@/shared/ui/TabLayout";
import { StatusBadge } from "@/shared/ui/StatusBadge";
import type {
  InventoryPageResult,
  Variety,
  VarietyPayload,
} from "../../model/types";
import { DetailRow, Field, inputClass } from "./InventoryPrimitives";
import { PotSizeInput } from "./PotSizeInput";

export function VarietySection({
  pageData,
  connectedGroups,
  genera,
  selectedId,
  loadingGroups = false,
  onSelect,
  onCreate,
  onUpdate,
  onDeactivate,
  onDelete,
}: {
  pageData: InventoryPageResult<Variety>;
  connectedGroups: Variety["connectedGroups"];
  genera: string[];
  selectedId: number;
  loadingGroups?: boolean;
  onSelect: (id: number) => void;
  onCreate: () => void;
  onUpdate: (varietyId: number, payload: VarietyPayload) => Promise<void>;
  onDeactivate: (varietyId: number) => Promise<void>;
  onDelete: (varietyId: number) => Promise<void>;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const keyword = searchParams.get("varietyKeyword") ?? "";
  const genus = searchParams.get("varietyGenus") ?? "전체";
  const status = searchParams.get("varietyStatus") ?? "전체";
  const sale = searchParams.get("varietySale") ?? "전체";
  const selected =
    pageData.content.find((item) => item.id === selectedId) ??
    pageData.content[0];
  const genusOptions = useMemo(() => ["전체", ...new Set(genera)], [genera]);
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<VarietyPayload>({
    genus: selected?.genus ?? "",
    name: selected?.name ?? "",
    alias: selected?.alias ?? "",
    defaultPotSize: selected?.potSize ?? "",
    saleEnabled: selected?.saleEnabled ?? true,
    description: selected?.description ?? "",
    memo: selected?.memo ?? "",
  });
  const columns = useMemo<ColumnDef<Variety, unknown>[]>(
    () => [
      {
        accessorKey: "code",
        header: "품종코드",
        size: 110,
        meta: { cellClassName: "font-semibold text-[#16793a]" },
      },
      { accessorKey: "genus", header: "속", size: 120 },
      {
        accessorKey: "name",
        header: "품종명",
        size: 180,
        meta: { cellClassName: "font-semibold" },
      },
      { accessorKey: "potSize", header: "기본 화분", size: 100 },
      {
        accessorKey: "saleEnabled",
        header: "판매 사용",
        cell: ({ row }) => (
          <StatusBadge tone={row.original.saleEnabled ? "green" : "gray"}>
            {row.original.saleEnabled ? "사용" : "미사용"}
          </StatusBadge>
        ),
        size: 100,
      },
      {
        accessorKey: "status",
        header: "상태",
        cell: ({ row }) => (
          <StatusBadge
            tone={row.original.status === "ACTIVE" ? "green" : "gray"}
          >
            {row.original.status === "ACTIVE" ? "활성" : "비활성"}
          </StatusBadge>
        ),
        size: 90,
      },
      {
        accessorKey: "registeredAt",
        header: "등록일",
        cell: ({ row }) => formatShortDate(row.original.registeredAt),
        size: 100,
      },
    ],
    [],
  );

  const updateParams = (updater: (params: URLSearchParams) => void) => {
    const params = new URLSearchParams(searchParams.toString());
    updater(params);
    router.replace(`${pathname}?${params.toString()}`);
  };

  return (
    <TabStack>
      <form
        className="shrink-0"
        onSubmit={(event) => {
          event.preventDefault();
          const formData = new FormData(event.currentTarget);

          updateParams((params) => {
            setQueryParam(params, "varietyGenus", formData.get("varietyGenus"));
            setQueryParam(
              params,
              "varietyKeyword",
              formData.get("varietyKeyword"),
            );
            setQueryParam(
              params,
              "varietyStatus",
              formData.get("varietyStatus"),
              "전체",
            );
            setQueryParam(
              params,
              "varietySale",
              formData.get("varietySale"),
              "전체",
            );
            params.set("varietyPage", "0");
          });
        }}
      >
        <FilterPanel>
          <FilterGrid className="lg:grid-cols-[1fr_1.1fr_1fr_1fr_auto_auto] lg:items-end">
            <FilterField label="속">
              <select
                className={inputClass}
                defaultValue={genus}
                name="varietyGenus"
              >
                {genusOptions.map((value) => (
                  <option key={value}>{value}</option>
                ))}
              </select>
            </FilterField>
            <FilterField label="품종명">
              <input
                className={inputClass}
                defaultValue={keyword}
                name="varietyKeyword"
                placeholder="품종명을 입력하세요"
              />
            </FilterField>
            <FilterField label="상태">
              <select
                className={inputClass}
                defaultValue={status}
                name="varietyStatus"
              >
                <option>전체</option>
                <option value="ACTIVE">활성</option>
                <option value="INACTIVE">비활성</option>
              </select>
            </FilterField>
            <FilterField label="판매 사용">
              <select
                className={inputClass}
                defaultValue={sale}
                name="varietySale"
              >
                <option>전체</option>
                <option>사용</option>
                <option>미사용</option>
              </select>
            </FilterField>
            <FilterResetButton
              className="h-9 lg:mt-5"
              onClick={() => {
                updateParams((params) => {
                  [
                    "varietyGenus",
                    "varietyKeyword",
                    "varietyStatus",
                    "varietySale",
                  ].forEach((key) => params.delete(key));
                  params.set("varietyPage", "0");
                });
              }}
            />
            <FilterSearchButton className="h-9 lg:mt-5" />
          </FilterGrid>
        </FilterPanel>
      </form>

      <TabSplit
        columns="lg:grid-cols-[minmax(0,1.15fr)_minmax(25rem,1fr)]"
        gap="gap-3"
      >
        <DataTable
          actions={
            <button
              className="inline-flex h-9 items-center gap-2 rounded-md bg-[#159447] px-3 text-xs font-semibold whitespace-nowrap text-white shadow-sm"
              type="button"
              onClick={onCreate}
            >
              <Plus className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
              새 품종 등록
            </button>
          }
          columns={columns}
          data={pageData.content}
          emptyMessage="조건에 맞는 품종이 없습니다."
          getRowId={(row) => String(row.id)}
          pageIndex={pageData.page}
          pageSize={pageData.size}
          pageSizeOptions={[10, 20, 50]}
          selectedRowId={selected == null ? null : String(selected.id)}
          settingsKey="inventory.varieties"
          title="품종 목록"
          totalLabel={`총 ${pageData.totalElements.toLocaleString()}개`}
          totalPages={pageData.totalPages}
          onPageChange={(pageIndex) =>
            updateParams((params) => {
              params.set("varietyPage", String(pageIndex));
            })
          }
          onPageSizeChange={(pageSize) =>
            updateParams((params) => {
              params.set("varietySize", String(pageSize));
              params.set("varietyPage", "0");
            })
          }
          onRowClick={(row) => {
            setEditing(false);
            onSelect(row.id);
          }}
        />

        {selected ? (
          <DetailCard>
            <DetailHeader
              eyebrow="품종 상세"
              title={selected.name}
              actions={
                <>
                  <DetailActionButton
                    icon={Trash2}
                    tone="danger"
                    onClick={() => {
                      if (!window.confirm("이 품종을 삭제할까요?")) return;
                      void onDelete(selected.id).catch((error: Error) => {
                        window.alert(error.message);
                      });
                    }}
                  >
                    삭제
                  </DetailActionButton>
                  {selected.status === "ACTIVE" ? (
                    <DetailActionButton
                      tone="danger"
                      onClick={() =>
                        void onDeactivate(selected.id).catch((error: Error) => {
                          window.alert(error.message);
                        })
                      }
                    >
                      비활성화
                    </DetailActionButton>
                  ) : null}
                  <DetailActionButton
                    icon={Pencil}
                    onClick={() => {
                      if (editing) {
                        setEditing(false);
                        return;
                      }

                      setForm({
                        genus: selected.genus,
                        name: selected.name,
                        alias: selected.alias,
                        defaultPotSize: selected.potSize,
                        saleEnabled: selected.saleEnabled,
                        description: selected.description,
                        memo: selected.memo,
                      });
                      setEditing(true);
                    }}
                  >
                    {editing ? "취소" : "수정"}
                  </DetailActionButton>
                </>
              }
              summary={
                <DetailSummary
                  items={[
                    {
                      label: "보유 묶음 수",
                      value: `${selected.connectedGroupCount}개`,
                    },
                    {
                      label: "총 보유 수량",
                      value: `${selected.totalQuantity}분`,
                    },
                    {
                      label: "판매 가능 수량",
                      value: `${selected.saleableQuantity}분`,
                    },
                    {
                      label: "최근 작업일",
                      value: selected.recentWorkDate ?? "-",
                    },
                  ]}
                />
              }
            />
            <div className="grid gap-4 p-4 sm:grid-cols-[9rem_minmax(0,1fr)]">
              <div className="flex aspect-square items-center justify-center rounded-md border border-[#d9e0d9] bg-[#eff7ed] text-[#159447]">
                <span className="text-5xl">✿</span>
              </div>
              {editing ? (
                <form
                  className="grid gap-3"
                  onSubmit={(event) => {
                    event.preventDefault();
                    void onUpdate(selected.id, form)
                      .then(() => setEditing(false))
                      .catch((error: Error) => {
                        window.alert(error.message);
                      });
                  }}
                >
                  <DetailRow label="품종코드" value={selected.code} />
                  <Field label="속">
                    <input
                      className={inputClass}
                      required
                      value={form.genus}
                      onChange={(event) =>
                        setForm((current) => ({
                          ...current,
                          genus: event.target.value,
                        }))
                      }
                    />
                  </Field>
                  <Field label="품종명">
                    <input
                      className={inputClass}
                      required
                      value={form.name}
                      onChange={(event) =>
                        setForm((current) => ({
                          ...current,
                          name: event.target.value,
                        }))
                      }
                    />
                  </Field>
                  <Field label="별칭">
                    <input
                      className={inputClass}
                      value={form.alias}
                      onChange={(event) =>
                        setForm((current) => ({
                          ...current,
                          alias: event.target.value,
                        }))
                      }
                    />
                  </Field>
                  <PotSizeInput
                    label="기본 화분 크기"
                    value={form.defaultPotSize}
                    onChange={(value) =>
                      setForm((current) => ({
                        ...current,
                        defaultPotSize: value,
                      }))
                    }
                  />
                  <label className="flex items-center gap-2 text-sm font-semibold text-[#425047]">
                    <input
                      checked={form.saleEnabled}
                      type="checkbox"
                      onChange={(event) =>
                        setForm((current) => ({
                          ...current,
                          saleEnabled: event.target.checked,
                        }))
                      }
                    />
                    판매 사용
                  </label>
                  <label className="space-y-1 text-xs font-semibold text-[#425047]">
                    <span>특징/설명</span>
                    <textarea
                      className="min-h-20 w-full rounded-md border border-[#d7ddd8] bg-white px-3 py-2 text-sm outline-none focus:border-[#159447] focus:ring-1 focus:ring-[#159447]"
                      value={form.description}
                      onChange={(event) =>
                        setForm((current) => ({
                          ...current,
                          description: event.target.value,
                        }))
                      }
                    />
                  </label>
                  <label className="space-y-1 text-xs font-semibold text-[#425047]">
                    <span>메모</span>
                    <textarea
                      className="min-h-20 w-full rounded-md border border-[#d7ddd8] bg-white px-3 py-2 text-sm outline-none focus:border-[#159447] focus:ring-1 focus:ring-[#159447]"
                      value={form.memo}
                      onChange={(event) =>
                        setForm((current) => ({
                          ...current,
                          memo: event.target.value,
                        }))
                      }
                    />
                  </label>
                  <div className="flex justify-end">
                    <button
                      className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white"
                      type="submit"
                    >
                      저장
                    </button>
                  </div>
                </form>
              ) : (
                <dl className="space-y-1">
                  <DetailRow label="품종코드" value={selected.code} />
                  <DetailRow label="속" value={selected.genus} />
                  <DetailRow label="품종명" value={selected.name} />
                  <DetailRow label="별칭" value={selected.alias || "-"} />
                  <DetailRow label="기본 화분 크기" value={selected.potSize} />
                  <DetailRow label="특징/설명" value={selected.description} />
                  <DetailRow
                    label="판매 사용"
                    value={
                      <StatusBadge
                        tone={selected.saleEnabled ? "green" : "gray"}
                      >
                        {selected.saleEnabled ? "사용" : "미사용"}
                      </StatusBadge>
                    }
                  />
                  <DetailRow
                    label="상태"
                    value={
                      <StatusBadge
                        tone={selected.status === "ACTIVE" ? "green" : "gray"}
                      >
                        {selected.status === "ACTIVE" ? "활성" : "비활성"}
                      </StatusBadge>
                    }
                  />
                  <DetailRow label="메모" value={selected.memo} />
                  <DetailRow label="등록일" value={selected.registeredAt} />
                  <DetailRow label="수정일" value={selected.updatedAt} />
                </dl>
              )}
            </div>
            <div className="border-t border-[#dce2dc] px-4 py-3">
              <h3 className="text-xs font-bold">
                연결된 난 묶음{" "}
                <span className="text-[#159447]">
                  ({selected.connectedGroupCount}개)
                </span>
              </h3>
              <div className="mt-2 overflow-x-auto">
                <table className="w-full min-w-[440px] text-xs">
                  <thead className="bg-[#f7f9f6]">
                    <tr>
                      {["위치", "수량(분)", "상태", "최근 작업"].map(
                        (label) => (
                          <th
                            className="px-2 py-1.5 text-left font-semibold"
                            key={label}
                          >
                            {label}
                          </th>
                        ),
                      )}
                    </tr>
                  </thead>
                  <tbody>
                    {connectedGroups.length ? (
                      connectedGroups.map((group) => (
                        <tr
                          className="border-b border-[#e4e8e4]"
                          key={group.id}
                        >
                          <td className="px-2 py-1.5">{group.location}</td>
                          <td className="px-2 py-1.5">{group.quantity}</td>
                          <td className="px-2 py-1.5">{group.status}</td>
                          <td className="px-2 py-1.5">
                            {group.latestWork ?? "-"}
                          </td>
                        </tr>
                      ))
                    ) : loadingGroups ? (
                      <tr>
                        <td
                          className="px-2 py-6 text-center text-[#758078]"
                          colSpan={4}
                        >
                          불러오는 중
                        </td>
                      </tr>
                    ) : (
                      <tr>
                        <td
                          className="px-2 py-6 text-center text-[#758078]"
                          colSpan={4}
                        >
                          연결된 난 묶음 없음
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </DetailCard>
        ) : null}
      </TabSplit>
    </TabStack>
  );
}

function setQueryParam(
  params: URLSearchParams,
  key: string,
  value: FormDataEntryValue | null,
  emptyValue = "전체",
) {
  const normalized = typeof value === "string" ? value.trim() : "";

  if (!normalized || normalized === emptyValue) {
    params.delete(key);
    return;
  }

  params.set(key, normalized);
}
