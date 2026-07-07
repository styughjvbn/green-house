"use client";

import { ChevronLeft, ChevronRight, Pencil, Plus, Trash2 } from "lucide-react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useMemo, useState } from "react";
import type {
  InventoryPageResult,
  Material,
  MaterialPayload,
} from "../../model/types";
import {
  DetailRow,
  Field,
  inputClass,
  StatusBadge,
} from "./InventoryPrimitives";

export function MaterialSection({
  pageData,
  selectedId,
  onSelect,
  onCreate,
  onUpdate,
  onDeactivate,
  onDelete,
}: {
  pageData: InventoryPageResult<Material>;
  selectedId: number;
  onSelect: (id: number) => void;
  onCreate: () => void;
  onUpdate: (materialId: number, payload: MaterialPayload) => Promise<void>;
  onDeactivate: (materialId: number) => Promise<void>;
  onDelete: (materialId: number) => Promise<void>;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const category = searchParams.get("materialCategory") ?? "전체";
  const keyword = searchParams.get("materialKeyword") ?? "";
  const manufacturer = searchParams.get("materialManufacturer") ?? "";
  const status = searchParams.get("materialStatus") ?? "전체";
  const selected =
    pageData.content.find((item) => item.id === selectedId) ??
    pageData.content[0];
  const categories = useMemo(
    () => ["전체", ...new Set(pageData.content.map((item) => item.category))],
    [pageData.content],
  );
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
  const pageNumbers = buildPageNumbers(pageData.page, pageData.totalPages);

  const updateParams = (updater: (params: URLSearchParams) => void) => {
    const params = new URLSearchParams(searchParams.toString());
    updater(params);
    router.replace(`${pathname}?${params.toString()}`);
  };

  return (
    <>
      <form
        className="grid gap-3 rounded-md border border-[#dce2dc] bg-white p-3 shadow-sm md:grid-cols-2 xl:grid-cols-[1fr_1.1fr_1.1fr_1fr_auto_auto] xl:items-end"
        onSubmit={(event) => {
          event.preventDefault();
          const formData = new FormData(event.currentTarget);

          updateParams((params) => {
            setQueryParam(
              params,
              "materialCategory",
              formData.get("materialCategory"),
              "전체",
            );
            setQueryParam(
              params,
              "materialKeyword",
              formData.get("materialKeyword"),
              "",
            );
            setQueryParam(
              params,
              "materialManufacturer",
              formData.get("materialManufacturer"),
              "",
            );
            setQueryParam(
              params,
              "materialStatus",
              formData.get("materialStatus"),
              "전체",
            );
            params.set("materialPage", "0");
          });
        }}
      >
        <Field label="자재 종류">
          <select
            className={inputClass}
            defaultValue={category}
            name="materialCategory"
          >
            {categories.map((value) => (
              <option key={value}>{value}</option>
            ))}
          </select>
        </Field>
        <Field label="자재명">
          <input
            className={inputClass}
            defaultValue={keyword}
            name="materialKeyword"
            placeholder="자재명을 입력하세요"
          />
        </Field>
        <Field label="제조사">
          <input
            className={inputClass}
            defaultValue={manufacturer}
            name="materialManufacturer"
            placeholder="제조사를 입력하세요"
          />
        </Field>
        <Field label="상태">
          <select
            className={inputClass}
            defaultValue={status}
            name="materialStatus"
          >
            <option>전체</option>
            <option value="ACTIVE">활성</option>
            <option value="INACTIVE">비활성</option>
          </select>
        </Field>
        <button
          className="h-9 rounded-md border border-[#d7ddd8] px-4 text-sm font-semibold"
          type="button"
          onClick={() => {
            updateParams((params) => {
              [
                "materialCategory",
                "materialKeyword",
                "materialManufacturer",
                "materialStatus",
              ].forEach((key) => params.delete(key));
              params.set("materialPage", "0");
            });
          }}
        >
          초기화
        </button>
        <button
          className="h-9 rounded-md bg-[#159447] px-6 text-sm font-semibold text-white"
          type="submit"
        >
          검색
        </button>
      </form>

      <div className="mt-3 grid min-w-0 gap-3 2xl:grid-cols-[minmax(0,1fr)_22rem]">
        <section className="min-w-0 rounded-md border border-[#dce2dc] bg-white p-3 shadow-sm">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-sm font-bold">
              자재 목록{" "}
              <span className="ml-2 text-xs text-[#159447]">
                (총 {pageData.totalElements}개)
              </span>
            </h2>
            <button
              className="flex items-center gap-2 rounded-md bg-[#159447] px-3 py-2 text-xs font-semibold text-white shadow-sm"
              type="button"
              onClick={onCreate}
            >
              <Plus className="h-3.5 w-3.5" />새 자재 등록
            </button>
          </div>
          <div className="mt-3 overflow-x-auto">
            <table className="w-full min-w-[850px] text-xs">
              <thead className="border-y border-[#dce2dc] bg-[#f7f9f6] text-[#536057]">
                <tr>
                  {[
                    "자재코드",
                    "종류",
                    "자재명",
                    "제조사",
                    "규격/용량",
                    "보관 위치",
                    "상태",
                    "등록일",
                  ].map((label) => (
                    <th
                      className="px-3 py-2 text-left font-semibold"
                      key={label}
                    >
                      {label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {pageData.content.map((item) => (
                  <tr
                    className={`cursor-pointer border-t border-[#e4e8e4] hover:bg-[#f3f9f3] ${item.id === selected?.id ? "bg-[#eaf7eb]" : ""}`}
                    key={item.id}
                    onClick={() => {
                      setEditing(false);
                      onSelect(item.id);
                    }}
                  >
                    <td className="px-3 py-2 font-semibold text-[#16793a]">
                      {item.code}
                    </td>
                    <td className="px-3 py-2">{item.category}</td>
                    <td className="px-3 py-2 font-semibold">{item.name}</td>
                    <td className="px-3 py-2">{item.manufacturer}</td>
                    <td className="px-3 py-2">{item.specification}</td>
                    <td className="px-3 py-2">{item.storageLocation}</td>
                    <td className="px-3 py-2">
                      <StatusBadge active={item.status === "ACTIVE"} />
                    </td>
                    <td className="px-3 py-2">{item.registeredAt}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="mt-3 flex items-center justify-between gap-3">
            <div className="flex items-center gap-1">
              <button
                className="grid h-8 w-8 place-items-center rounded border border-[#d7ddd8] disabled:opacity-40"
                disabled={pageData.page === 0}
                type="button"
                onClick={() =>
                  updateParams((params) => {
                    params.set("materialPage", String(pageData.page - 1));
                  })
                }
              >
                <ChevronLeft className="h-4 w-4" />
              </button>
              {pageNumbers.map((pageNumber) => (
                <button
                  key={pageNumber}
                  className={`h-8 min-w-8 rounded px-2 text-xs ${pageNumber === pageData.page ? "bg-[#159447] font-bold text-white" : "border border-[#d7ddd8]"}`}
                  type="button"
                  onClick={() =>
                    updateParams((params) => {
                      params.set("materialPage", String(pageNumber));
                    })
                  }
                >
                  {pageNumber + 1}
                </button>
              ))}
              <button
                className="grid h-8 w-8 place-items-center rounded border border-[#d7ddd8] disabled:opacity-40"
                disabled={pageData.page >= pageData.totalPages - 1}
                type="button"
                onClick={() =>
                  updateParams((params) => {
                    params.set("materialPage", String(pageData.page + 1));
                  })
                }
              >
                <ChevronRight className="h-4 w-4" />
              </button>
            </div>
            <select
              className="h-8 rounded border border-[#d7ddd8] px-3 text-xs"
              value={String(pageData.size)}
              onChange={(event) =>
                updateParams((params) => {
                  params.set("materialSize", event.target.value);
                  params.set("materialPage", "0");
                })
              }
            >
              <option value="10">10개씩 보기</option>
              <option value="20">20개씩 보기</option>
              <option value="50">50개씩 보기</option>
            </select>
          </div>
        </section>

        {selected ? (
          <aside className="min-w-0 rounded-md border border-[#dce2dc] bg-white p-4 shadow-sm">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-bold">자재 상세 정보</h2>
              <div className="flex items-center gap-2">
                <button
                  className="rounded border border-[#e2c8c8] px-3 py-1.5 text-xs font-semibold text-[#a14545]"
                  type="button"
                  onClick={() => {
                    if (!window.confirm("이 자재를 삭제할까요?")) return;
                    void onDelete(selected.id).catch((error: Error) => {
                      window.alert(error.message);
                    });
                  }}
                >
                  <Trash2 className="mr-1 inline h-3.5 w-3.5" />
                  삭제
                </button>
                {selected.status === "ACTIVE" ? (
                  <button
                    className="rounded border border-[#e2c8c8] px-3 py-1.5 text-xs font-semibold text-[#a14545]"
                    type="button"
                    onClick={() =>
                      void onDeactivate(selected.id).catch((error: Error) => {
                        window.alert(error.message);
                      })
                    }
                  >
                    비활성화
                  </button>
                ) : null}
                <button
                  className="flex items-center gap-1 rounded border border-[#d7ddd8] px-3 py-1.5 text-xs font-semibold"
                  type="button"
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
                  <Pencil className="h-3.5 w-3.5" />
                  {editing ? "취소" : "수정"}
                </button>
              </div>
            </div>
            {editing ? (
              <form
                className="mt-3 grid gap-3"
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
              <dl className="mt-3 space-y-1">
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
                  value={<StatusBadge active={selected.status === "ACTIVE"} />}
                />
                <DetailRow label="등록일" value={selected.registeredAt} />
                <DetailRow label="수정일" value={selected.updatedAt} />
              </dl>
            )}
          </aside>
        ) : null}
      </div>
    </>
  );
}

function setQueryParam(
  params: URLSearchParams,
  key: string,
  value: FormDataEntryValue | null,
  emptyValue = "",
) {
  const normalized = typeof value === "string" ? value.trim() : "";

  if (!normalized || normalized === emptyValue) {
    params.delete(key);
    return;
  }

  params.set(key, normalized);
}

function buildPageNumbers(currentPage: number, totalPages: number) {
  if (totalPages <= 0) {
    return [0];
  }

  const start = Math.max(0, currentPage - 2);
  const end = Math.min(totalPages, start + 5);

  return Array.from({ length: end - start }, (_, index) => start + index);
}
