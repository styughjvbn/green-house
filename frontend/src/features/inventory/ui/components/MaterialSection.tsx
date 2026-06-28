"use client";

import { Pencil } from "lucide-react";
import { useMemo, useState } from "react";
import type { Material } from "../../model/types";
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
}: {
  materials: Material[];
  selectedId: number;
  onSelect: (id: number) => void;
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
    <section className="mt-5 border-t border-[#d6ddd7] pt-4">
      <div className="flex items-center justify-between">
        <h2 className="border-b-2 border-[#159447] px-3 pb-2 text-sm font-bold text-[#16843d]">
          자재 관리
        </h2>
      </div>
      <div className="mt-2 grid gap-3 md:grid-cols-2 xl:grid-cols-[1fr_1.1fr_1.1fr_1fr_auto_auto] xl:items-end">
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
      <div className="mt-3 grid gap-3 2xl:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="min-w-0">
          <h3 className="text-sm font-bold">
            자재 목록{" "}
            <span className="ml-2 text-xs text-[#159447]">
              (총 {filtered.length}개)
            </span>
          </h3>
          <div className="mt-2 overflow-x-auto rounded-md border border-[#dce2dc]">
            <table className="w-full min-w-[850px] text-xs">
              <thead className="bg-[#f7f9f6]">
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
                    onClick={() => onSelect(item.id)}
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
        </div>
        {selected ? (
          <aside className="rounded-md border border-[#dce2dc] bg-white p-4">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold">자재 상세 정보</h3>
              <button
                className="flex items-center gap-1 rounded border border-[#d7ddd8] px-3 py-1.5 text-xs font-semibold"
                type="button"
              >
                <Pencil className="h-3.5 w-3.5" />
                수정
              </button>
            </div>
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
            </dl>
          </aside>
        ) : null}
      </div>
    </section>
  );
}
