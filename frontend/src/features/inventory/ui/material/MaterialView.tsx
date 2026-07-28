"use client";

import { Pencil, Plus, Trash2 } from "lucide-react";
import { useMemo, useState } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import type { Page } from "@/shared/api/page";
import { formatShortDate } from "@/shared/lib/dateFormat";
import { DataTable } from "@/shared/ui/DataTable";
import {
  DetailActionButton,
  DetailCard,
  DetailHeader,
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
  Material,
  MaterialFilterState,
  MaterialPayload,
} from "../../model/types";
import { DetailRow, Field, inputClass } from "../common/InventoryPrimitives";

export function MaterialView({
  filters,
  loading,
  pageData,
  selectedId,
  onSelect,
  onCreate,
  onUpdate,
  onDeactivate,
  onDelete,
  onFilterChange,
  onPageChange,
  onPageSizeChange,
  onReset,
  onSearch,
}: {
  filters: MaterialFilterState;
  loading: boolean;
  pageData: Page<Material>;
  selectedId: number;
  onSelect: (id: number) => void;
  onCreate: () => void;
  onUpdate: (materialId: number, payload: MaterialPayload) => Promise<void>;
  onDeactivate: (materialId: number) => Promise<void>;
  onDelete: (materialId: number) => Promise<void>;
  onFilterChange: <K extends keyof MaterialFilterState>(
    field: K,
    value: MaterialFilterState[K],
  ) => void;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  onReset: () => void;
  onSearch: () => void;
}) {
  const selected =
    pageData.content.find((item) => item.id === selectedId) ??
    pageData.content[0];
  const categories = ["자재", "농약", "비료"];
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<MaterialPayload>({
    category: selected?.category ?? "자재",
    name: selected?.name ?? "",
    manufacturer: selected?.manufacturer ?? "",
    specification: selected?.specification ?? "",
    stockQuantity: selected?.stockQuantity ?? "",
    storageLocation: selected?.storageLocation ?? "",
    usage: selected?.usage ?? "",
  });
  const columns = useMemo<ColumnDef<Material, unknown>[]>(
    () => [
      {
        accessorKey: "code",
        header: "자재코드",
        size: 110,
        meta: { cellClassName: "font-semibold text-[#16793a]" },
      },
      { accessorKey: "category", header: "종류", size: 90 },
      {
        accessorKey: "name",
        header: "자재명",
        size: 180,
        meta: { cellClassName: "font-semibold" },
      },
      { accessorKey: "manufacturer", header: "제조사", size: 130 },
      { accessorKey: "specification", header: "규격/용량", size: 140 },
      { accessorKey: "storageLocation", header: "보관 위치", size: 130 },
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
  return (
    <TabStack>
      <form
        className="shrink-0"
        onSubmit={(event) => {
          event.preventDefault();
          onSearch();
        }}
      >
        <FilterPanel>
          <FilterGrid className="md:grid-cols-2 lg:grid-cols-[1fr_1.1fr_1.1fr_1fr_auto_auto] lg:items-end">
            <FilterField label="자재 종류">
              <select
                className={inputClass}
                value={filters.category}
                name="materialCategory"
                onChange={(event) =>
                  onFilterChange("category", event.target.value)
                }
              >
                <option value="">전체</option>
                {categories.map((value) => (
                  <option key={value}>{value}</option>
                ))}
              </select>
            </FilterField>
            <FilterField label="자재명">
              <input
                className={inputClass}
                value={filters.keyword}
                name="materialKeyword"
                placeholder="자재명을 입력하세요"
                onChange={(event) =>
                  onFilterChange("keyword", event.target.value)
                }
              />
            </FilterField>
            <FilterField label="제조사">
              <input
                className={inputClass}
                value={filters.manufacturer}
                name="materialManufacturer"
                placeholder="제조사를 입력하세요"
                onChange={(event) =>
                  onFilterChange("manufacturer", event.target.value)
                }
              />
            </FilterField>
            <FilterField label="상태">
              <select
                className={inputClass}
                value={filters.status}
                name="materialStatus"
                onChange={(event) =>
                  onFilterChange(
                    "status",
                    event.target.value as MaterialFilterState["status"],
                  )
                }
              >
                <option value="">전체</option>
                <option value="ACTIVE">활성</option>
                <option value="INACTIVE">비활성</option>
              </select>
            </FilterField>
            <FilterResetButton className="h-9 lg:mt-5" onClick={onReset} />
            <FilterSearchButton className="h-9 lg:mt-5" />
          </FilterGrid>
        </FilterPanel>
      </form>

      <TabSplit columns="lg:grid-cols-[minmax(0,1fr)_22rem]" gap="gap-3">
        <DataTable
          actions={
            <button
              className="inline-flex h-9 items-center gap-2 rounded-md bg-[#159447] px-3 text-xs font-semibold whitespace-nowrap text-white shadow-sm"
              type="button"
              onClick={onCreate}
            >
              <Plus className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
              새 자재 등록
            </button>
          }
          columns={columns}
          data={pageData.content}
          emptyMessage="조건에 맞는 자재가 없습니다."
          getRowId={(row) => String(row.id)}
          isLoading={loading}
          pageIndex={pageData.page}
          pageSize={pageData.size}
          pageSizeOptions={[10, 20, 50]}
          selectedRowId={selected == null ? null : String(selected.id)}
          settingsKey="inventory.materials"
          title="자재 목록"
          totalLabel={`총 ${pageData.totalElements.toLocaleString()}개`}
          totalPages={pageData.totalPages}
          onPageChange={onPageChange}
          onPageSizeChange={onPageSizeChange}
          onRowClick={(row) => {
            setEditing(false);
            onSelect(row.id);
          }}
        />

        {selected ? (
          <DetailCard>
            <DetailHeader
              eyebrow="자재 상세"
              title={selected.name}
              actions={
                <>
                  <DetailActionButton
                    icon={Trash2}
                    tone="danger"
                    onClick={() => {
                      if (!window.confirm("이 자재를 삭제할까요?")) return;
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
                        category: selected.category,
                        name: selected.name,
                        manufacturer: selected.manufacturer,
                        specification: selected.specification,
                        stockQuantity: selected.stockQuantity,
                        storageLocation: selected.storageLocation,
                        usage: selected.usage,
                      });
                      setEditing(true);
                    }}
                  >
                    {editing ? "취소" : "수정"}
                  </DetailActionButton>
                </>
              }
            />
            {editing ? (
              <form
                className="grid gap-3 p-4"
                onSubmit={(event) => {
                  event.preventDefault();
                  void onUpdate(selected.id, form)
                    .then(() => setEditing(false))
                    .catch((error: Error) => {
                      window.alert(error.message);
                    });
                }}
              >
                <DetailRow label="자재코드" value={selected.code} />
                <Field label="종류">
                  <select
                    className={inputClass}
                    value={form.category}
                    onChange={(event) =>
                      setForm((current) => ({
                        ...current,
                        category: event.target
                          .value as MaterialPayload["category"],
                      }))
                    }
                  >
                    <option value="자재">자재</option>
                    <option value="농약">농약</option>
                    <option value="비료">비료</option>
                  </select>
                </Field>
                <Field label="자재명">
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
                <Field label="제조사">
                  <input
                    className={inputClass}
                    value={form.manufacturer}
                    onChange={(event) =>
                      setForm((current) => ({
                        ...current,
                        manufacturer: event.target.value,
                      }))
                    }
                  />
                </Field>
                <Field label="규격/용량">
                  <input
                    className={inputClass}
                    value={form.specification}
                    onChange={(event) =>
                      setForm((current) => ({
                        ...current,
                        specification: event.target.value,
                      }))
                    }
                  />
                </Field>
                <Field label="현재 수량">
                  <input
                    className={inputClass}
                    value={form.stockQuantity}
                    onChange={(event) =>
                      setForm((current) => ({
                        ...current,
                        stockQuantity: event.target.value,
                      }))
                    }
                  />
                </Field>
                <Field label="보관 위치">
                  <input
                    className={inputClass}
                    value={form.storageLocation}
                    onChange={(event) =>
                      setForm((current) => ({
                        ...current,
                        storageLocation: event.target.value,
                      }))
                    }
                  />
                </Field>
                <label className="space-y-1 text-xs font-semibold text-[#425047]">
                  <span>사용 방법</span>
                  <textarea
                    className="min-h-20 w-full rounded-md border border-[#d7ddd8] bg-white px-3 py-2 text-sm outline-none focus:border-[#159447] focus:ring-1 focus:ring-[#159447]"
                    value={form.usage}
                    onChange={(event) =>
                      setForm((current) => ({
                        ...current,
                        usage: event.target.value,
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
              <dl className="mt-3 space-y-1 px-4 pb-3">
                <DetailRow label="자재코드" value={selected.code} />
                <DetailRow label="종류" value={selected.category} />
                <DetailRow label="자재명" value={selected.name} />
                <DetailRow label="제조사" value={selected.manufacturer} />
                <DetailRow label="규격/용량" value={selected.specification} />
                <DetailRow label="현재 수량" value={selected.stockQuantity} />
                <DetailRow label="사용 방법" value={selected.usage} />
                <DetailRow label="보관 위치" value={selected.storageLocation} />
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
                <DetailRow label="등록일" value={selected.registeredAt} />
                <DetailRow label="수정일" value={selected.updatedAt} />
              </dl>
            )}
          </DetailCard>
        ) : null}
      </TabSplit>
    </TabStack>
  );
}
