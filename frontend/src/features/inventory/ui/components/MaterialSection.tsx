"use client";

import { Pencil, Plus } from "lucide-react";
import { useMemo, useState } from "react";
import type { Material, MaterialPayload } from "../../model/types";
import {
  DetailRow,
  Field,
  inputClass,
  StatusBadge,
} from "./InventoryPrimitives";

export function MaterialSection({
  materials,
  selectedId,
  onSelect,
  onCreate,
  onUpdate,
  onDeactivate,
}: {
  materials: Material[];
  selectedId: number;
  onSelect: (id: number) => void;
  onCreate: () => void;
  onUpdate: (materialId: number, payload: MaterialPayload) => Promise<void>;
  onDeactivate: (materialId: number) => Promise<void>;
}) {
  const [category, setCategory] = useState("전체");
  const [keyword, setKeyword] = useState("");
  const [manufacturer, setManufacturer] = useState("");
  const [status, setStatus] = useState("전체");
  const selected =
    materials.find((item) => item.id === selectedId) ?? materials[0];
  const categories = useMemo(
    () => ["전체", ...new Set(materials.map((item) => item.category))],
    [materials],
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
  const filtered = materials.filter(
    (item) =>
      (category === "전체" || item.category === category) &&
      (!keyword || `${item.code} ${item.name}`.includes(keyword)) &&
      (!manufacturer || item.manufacturer.includes(manufacturer)) &&
      (status === "전체" || item.status === status),
  );
  const reset = () => {
    setCategory("전체");
    setKeyword("");
    setManufacturer("");
    setStatus("전체");
  };

  return (
    <>
      <div className="grid gap-3 rounded-md border border-[#dce2dc] bg-white p-3 shadow-sm md:grid-cols-2 xl:grid-cols-[1fr_1.1fr_1.1fr_1fr_auto_auto] xl:items-end">
        <Field label="자재 종류">
          <select
            className={inputClass}
            value={category}
            onChange={(event) => setCategory(event.target.value)}
          >
            {categories.map((value) => (
              <option key={value}>{value}</option>
            ))}
          </select>
        </Field>
        <Field label="자재명">
          <input
            className={inputClass}
            placeholder="자재명을 입력하세요"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
          />
        </Field>
        <Field label="제조사">
          <input
            className={inputClass}
            placeholder="제조사를 입력하세요"
            value={manufacturer}
            onChange={(event) => setManufacturer(event.target.value)}
          />
        </Field>
        <Field label="상태">
          <select
            className={inputClass}
            value={status}
            onChange={(event) => setStatus(event.target.value)}
          >
            <option>전체</option>
            <option value="ACTIVE">활성</option>
            <option value="INACTIVE">비활성</option>
          </select>
        </Field>
        <button
          className="h-9 rounded-md border border-[#d7ddd8] px-4 text-sm font-semibold"
          type="button"
          onClick={reset}
        >
          초기화
        </button>
        <button
          className="h-9 rounded-md bg-[#159447] px-6 text-sm font-semibold text-white"
          type="button"
        >
          검색
        </button>
      </div>

      <div className="mt-3 grid min-w-0 gap-3 2xl:grid-cols-[minmax(0,1fr)_22rem]">
        <section className="min-w-0 rounded-md border border-[#dce2dc] bg-white p-3 shadow-sm">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-sm font-bold">
              자재 목록{" "}
              <span className="ml-2 text-xs text-[#159447]">
                (총 {filtered.length}개)
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
                {filtered.map((item) => (
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
        </section>

        {selected ? (
          <aside className="min-w-0 rounded-md border border-[#dce2dc] bg-white p-4 shadow-sm">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-bold">자재 상세 정보</h2>
              <div className="flex items-center gap-2">
                {selected.status === "ACTIVE" ? (
                  <button
                    className="rounded border border-[#e2c8c8] px-3 py-1.5 text-xs font-semibold text-[#a14545]"
                    type="button"
                    onClick={() => void onDeactivate(selected.id)}
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
                  void onUpdate(selected.id, form).then(() =>
                    setEditing(false),
                  );
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
