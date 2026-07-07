"use client";

import { ChevronLeft, ChevronRight, Pencil, Plus } from "lucide-react";
import { useMemo, useState } from "react";
import type { Variety, VarietyPayload } from "../../model/types";
import {
  DetailRow,
  Field,
  inputClass,
  StatusBadge,
} from "./InventoryPrimitives";

export function VarietySection({
  varieties,
  selectedId,
  loadingGroups = false,
  onSelect,
  onCreate,
  onUpdate,
  onDeactivate,
}: {
  varieties: Variety[];
  selectedId: number;
  loadingGroups?: boolean;
  onSelect: (id: number) => void;
  onCreate: () => void;
  onUpdate: (varietyId: number, payload: VarietyPayload) => Promise<void>;
  onDeactivate: (varietyId: number) => Promise<void>;
}) {
  const [genus, setGenus] = useState("전체");
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState("전체");
  const [sale, setSale] = useState("전체");
  const selected =
    varieties.find((item) => item.id === selectedId) ?? varieties[0];
  const genera = useMemo(
    () => ["전체", ...new Set(varieties.map((item) => item.genus))],
    [varieties],
  );
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
  const filtered = varieties.filter(
    (item) =>
      (genus === "전체" || item.genus === genus) &&
      (!keyword || `${item.code} ${item.name}`.includes(keyword)) &&
      (status === "전체" || item.status === status) &&
      (sale === "전체" || item.saleEnabled === (sale === "사용")),
  );

  const reset = () => {
    setGenus("전체");
    setKeyword("");
    setStatus("전체");
    setSale("전체");
  };

  return (
    <>
      <div className="grid gap-3 rounded-md border border-[#dce2dc] bg-white p-3 shadow-sm md:grid-cols-2 xl:grid-cols-[1fr_1.1fr_1fr_1fr_auto_auto] xl:items-end">
        <Field label="속">
          <select
            className={inputClass}
            value={genus}
            onChange={(event) => setGenus(event.target.value)}
          >
            {genera.map((value) => (
              <option key={value}>{value}</option>
            ))}
          </select>
        </Field>
        <Field label="품종명">
          <input
            className={inputClass}
            placeholder="품종명을 입력하세요"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
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
        <Field label="판매 사용">
          <select
            className={inputClass}
            value={sale}
            onChange={(event) => setSale(event.target.value)}
          >
            <option>전체</option>
            <option>사용</option>
            <option>미사용</option>
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

      <div className="mt-3 grid min-w-0 gap-3 2xl:grid-cols-[minmax(0,1.15fr)_minmax(25rem,1fr)]">
        <section className="min-w-0 rounded-md border border-[#dce2dc] bg-white p-3 shadow-sm">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-sm font-bold">
              품종 목록{" "}
              <span className="ml-2 text-xs font-semibold text-[#159447]">
                (총 {filtered.length}개)
              </span>
            </h2>
            <button
              className="flex items-center gap-2 rounded-md bg-[#159447] px-3 py-2 text-xs font-semibold text-white shadow-sm"
              type="button"
              onClick={onCreate}
            >
              <Plus className="h-3.5 w-3.5" />새 품종 등록
            </button>
          </div>
          <div className="mt-3 overflow-x-auto">
            <table className="w-full min-w-[680px] border-collapse text-xs">
              <thead className="border-y border-[#dce2dc] bg-[#f7f9f6] text-[#536057]">
                <tr>
                  {[
                    "품종코드",
                    "속",
                    "품종명",
                    "기본 화분",
                    "판매 사용",
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
                    className={`cursor-pointer border-b border-[#e5e9e5] hover:bg-[#f3f9f3] ${item.id === selected?.id ? "bg-[#eaf7eb]" : ""}`}
                    key={item.id}
                    onClick={() => {
                      setEditing(false);
                      onSelect(item.id);
                    }}
                  >
                    <td className="px-3 py-2 font-semibold text-[#16793a]">
                      {item.code}
                    </td>
                    <td className="px-3 py-2">{item.genus}</td>
                    <td className="px-3 py-2 font-semibold">{item.name}</td>
                    <td className="px-3 py-2">{item.potSize}</td>
                    <td className="px-3 py-2">
                      <StatusBadge
                        active={item.saleEnabled}
                        labels={["사용", "미사용"]}
                      />
                    </td>
                    <td className="px-3 py-2">
                      <StatusBadge active={item.status === "ACTIVE"} />
                    </td>
                    <td className="px-3 py-2">{item.registeredAt}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="mt-3 flex items-center justify-between">
            <div className="flex gap-1">
              <button
                className="grid h-8 w-8 place-items-center rounded border border-[#d7ddd8]"
                type="button"
              >
                <ChevronLeft className="h-4 w-4" />
              </button>
              <button
                className="h-8 min-w-8 rounded bg-[#159447] px-2 text-xs font-bold text-white"
                type="button"
              >
                1
              </button>
              <button
                className="h-8 min-w-8 rounded border border-[#d7ddd8] px-2 text-xs"
                type="button"
              >
                2
              </button>
              <button
                className="grid h-8 w-8 place-items-center rounded border border-[#d7ddd8]"
                type="button"
              >
                <ChevronRight className="h-4 w-4" />
              </button>
            </div>
            <select className="h-8 rounded border border-[#d7ddd8] px-3 text-xs">
              <option>10개씩 보기</option>
              <option>20개씩 보기</option>
            </select>
          </div>
        </section>

        {selected ? (
          <section className="min-w-0 rounded-md border border-[#dce2dc] bg-white p-4 shadow-sm">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-bold">품종 상세 정보</h2>
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
                  <Pencil className="h-3.5 w-3.5" />
                  {editing ? "취소" : "수정"}
                </button>
              </div>
            </div>
            <div className="mt-3 grid gap-4 sm:grid-cols-[9rem_minmax(0,1fr)]">
              <div className="flex aspect-square items-center justify-center rounded-md border border-[#d9e0d9] bg-[#eff7ed] text-[#159447]">
                <span className="text-5xl">✿</span>
              </div>
              {editing ? (
                <form
                  className="grid gap-3"
                  onSubmit={(event) => {
                    event.preventDefault();
                    void onUpdate(selected.id, form).then(() =>
                      setEditing(false),
                    );
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
                  <Field label="기본 화분 크기">
                    <input
                      className={inputClass}
                      value={form.defaultPotSize}
                      onChange={(event) =>
                        setForm((current) => ({
                          ...current,
                          defaultPotSize: event.target.value,
                        }))
                      }
                    />
                  </Field>
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
                        active={selected.saleEnabled}
                        labels={["사용", "미사용"]}
                      />
                    }
                  />
                  <DetailRow
                    label="상태"
                    value={
                      <StatusBadge active={selected.status === "ACTIVE"} />
                    }
                  />
                  <DetailRow label="메모" value={selected.memo} />
                  <DetailRow label="등록일" value={selected.registeredAt} />
                  <DetailRow label="수정일" value={selected.updatedAt} />
                </dl>
              )}
            </div>
            <div className="mt-4 grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
              <SummaryCard
                label="보유 묶음 수"
                value={`${selected.connectedGroupCount}개`}
              />
              <SummaryCard
                label="총 보유 수량"
                value={`${selected.totalQuantity}분`}
              />
              <SummaryCard
                label="판매 가능 수량"
                value={`${selected.saleableQuantity}분`}
              />
              <SummaryCard
                label="최근 작업일"
                value={selected.recentWorkDate ?? "-"}
              />
            </div>
            <div className="mt-4 border-t border-[#dce2dc] pt-3">
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
                    {selected.connectedGroups.length ? (
                      selected.connectedGroups.map((group) => (
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
          </section>
        ) : null}
      </div>
    </>
  );
}

function SummaryCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md border border-[#e1e6e1] bg-[#fbfcfa] px-3 py-2">
      <p className="text-[11px] text-[#68756d]">{label}</p>
      <strong className="mt-1 block text-sm">{value}</strong>
    </div>
  );
}
