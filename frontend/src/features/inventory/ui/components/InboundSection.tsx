"use client";

import type { House } from "@/entities/farm/types";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { ChevronLeft, ChevronRight, Plus, Scissors, X } from "lucide-react";
import type { ReactNode } from "react";
import { useState } from "react";
import type {
  InboundPottingPayload,
  InboundRecord,
  InventoryPageResult,
  InboundRecordPayload,
  InboundStatus,
  InboundType,
  InboundRecordUpdatePayload,
  Variety,
} from "../../model/types";
import { DetailRow, Field, inputClass } from "./InventoryPrimitives";

const INBOUND_TYPE_LABELS: Record<InboundType, string> = {
  FLASK_SEEDLING: "유리병 모종",
  POTTED_SEEDLING: "포트 모종",
  PRODUCT_POT: "상품분",
  SAMPLE: "샘플",
  ETC: "기타",
};

const INBOUND_STATUS_LABELS: Record<InboundStatus, string> = {
  TEMP_STORED: "임시보관",
  POTTING_PENDING: "포트작업대기",
  POTTED: "포트작업완료",
  PLACED: "배치완료",
  CANCELED: "취소",
};

export function InboundSection({
  pageData,
  houses,
  varieties,
  selectedId,
  onSelect,
  onOpenCreate,
  onUpdate,
  onCreate,
  onPotting,
  onCancel,
}: {
  pageData: InventoryPageResult<InboundRecord>;
  houses: House[];
  varieties: Variety[];
  selectedId: number;
  onSelect: (id: number) => void;
  onOpenCreate: () => void;
  onUpdate: (
    inboundRecordId: number,
    payload: InboundRecordUpdatePayload,
  ) => Promise<void>;
  onCreate: (payload: InboundRecordPayload) => Promise<void>;
  onPotting: (
    inboundRecordId: number,
    payload: InboundPottingPayload,
  ) => Promise<void>;
  onCancel: (inboundRecordId: number, memo?: string) => Promise<void>;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const keyword = searchParams.get("inboundKeyword") ?? "";
  const status = (searchParams.get("inboundStatus") ?? "ALL") as
    | InboundStatus
    | "ALL";
  const inboundType = (searchParams.get("inboundType") ?? "ALL") as
    | InboundType
    | "ALL";
  const [dialog, setDialog] = useState<"create" | "potting" | "cancel" | null>(
    null,
  );
  const [editing, setEditing] = useState(false);
  const [editForm, setEditForm] = useState<InboundRecordUpdatePayload>({
    inboundDate: "",
  });

  const selected =
    pageData.content.find((item) => item.id === selectedId) ??
    pageData.content[0];

  const pageNumbers = buildPageNumbers(pageData.page, pageData.totalPages);

  const updateParams = (updater: (params: URLSearchParams) => void) => {
    const params = new URLSearchParams(searchParams.toString());
    updater(params);
    router.replace(`${pathname}?${params.toString()}`);
  };

  return (
    <>
      <form
        className="grid gap-3 rounded-md border border-[#dce2dc] bg-white p-3 shadow-sm md:grid-cols-2 xl:grid-cols-[1fr_1fr_1fr_auto_auto] xl:items-end"
        onSubmit={(event) => {
          event.preventDefault();
          const formData = new FormData(event.currentTarget);

          updateParams((params) => {
            setQueryParam(
              params,
              "inboundType",
              formData.get("inboundType"),
              "ALL",
            );
            setQueryParam(
              params,
              "inboundStatus",
              formData.get("inboundStatus"),
              "ALL",
            );
            setQueryParam(
              params,
              "inboundKeyword",
              formData.get("inboundKeyword"),
              "",
            );
            params.set("inboundPage", "0");
          });
        }}
      >
        <Field label="입고 유형">
          <select
            className={inputClass}
            defaultValue={inboundType}
            name="inboundType"
          >
            <option value="ALL">전체</option>
            {Object.entries(INBOUND_TYPE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </Field>
        <Field label="상태">
          <select
            className={inputClass}
            defaultValue={status}
            name="inboundStatus"
          >
            <option value="ALL">전체</option>
            {Object.entries(INBOUND_STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </Field>
        <Field label="품종명">
          <input
            className={inputClass}
            defaultValue={keyword}
            name="inboundKeyword"
            placeholder="품종명, 위치"
          />
        </Field>
        <button
          className="h-9 rounded-md border border-[#d7ddd8] px-4 text-sm font-semibold"
          type="button"
          onClick={() => {
            updateParams((params) => {
              ["inboundKeyword", "inboundStatus", "inboundType"].forEach(
                (key) => params.delete(key),
              );
              params.set("inboundPage", "0");
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

      <div className="mt-3 grid min-w-0 gap-3 2xl:grid-cols-[minmax(0,1.15fr)_minmax(24rem,0.95fr)]">
        <section className="min-w-0 rounded-md border border-[#dce2dc] bg-white p-3 shadow-sm">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-sm font-bold">
              입고 목록
              <span className="ml-2 text-xs font-semibold text-[#159447]">
                총 {pageData.totalElements}건
              </span>
            </h2>
            <button
              className="flex items-center gap-2 rounded-md bg-[#159447] px-3 py-2 text-xs font-semibold text-white shadow-sm"
              type="button"
              onClick={() => {
                onOpenCreate();
                setDialog("create");
              }}
            >
              <Plus className="h-3.5 w-3.5" />새 입고 등록
            </button>
          </div>
          <div className="mt-3 overflow-x-auto">
            <table className="w-full min-w-[880px] text-xs">
              <thead className="border-y border-[#dce2dc] bg-[#f7f9f6] text-[#536057]">
                <tr>
                  {[
                    "입고일",
                    "유형",
                    "품종명",
                    "예상",
                    "실제",
                    "현재 위치",
                    "상태",
                    "예정일",
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
                    key={item.id}
                    className={`cursor-pointer border-b border-[#e5e9e5] hover:bg-[#f3f9f3] ${item.id === selected?.id ? "bg-[#eaf7eb]" : ""}`}
                    onClick={() => {
                      setEditing(false);
                      onSelect(item.id);
                    }}
                  >
                    <td className="px-3 py-2">{item.inboundDate}</td>
                    <td className="px-3 py-2">
                      {INBOUND_TYPE_LABELS[item.inboundType]}
                    </td>
                    <td className="px-3 py-2 font-semibold">
                      {item.varietyName}
                    </td>
                    <td className="px-3 py-2">
                      {item.estimatedQuantity ?? item.bottleCount ?? "-"}
                    </td>
                    <td className="px-3 py-2">{item.actualQuantity ?? "-"}</td>
                    <td className="px-3 py-2">{item.currentLocation ?? "-"}</td>
                    <td className="px-3 py-2">
                      {INBOUND_STATUS_LABELS[item.status]}
                    </td>
                    <td className="px-3 py-2">{item.pottingDueDate ?? "-"}</td>
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
                    params.set("inboundPage", String(pageData.page - 1));
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
                      params.set("inboundPage", String(pageNumber));
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
                    params.set("inboundPage", String(pageData.page + 1));
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
                  params.set("inboundSize", event.target.value);
                  params.set("inboundPage", "0");
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
          <section className="min-w-0 rounded-md border border-[#dce2dc] bg-white p-4 shadow-sm">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 className="text-sm font-bold">입고 상세</h2>
              <div className="flex flex-wrap gap-2">
                {selected.status !== "CANCELED" ? (
                  <button
                    className="flex items-center gap-1 rounded-md border border-[#d7ddd8] px-3 py-1.5 text-xs font-semibold"
                    type="button"
                    onClick={() => {
                      setEditing((current) => !current);
                      setEditForm({
                        inboundDate: selected.inboundDate,
                        bottleCount: selected.bottleCount ?? undefined,
                        estimatedQuantity:
                          selected.estimatedQuantity ?? undefined,
                        actualQuantity: selected.actualQuantity ?? undefined,
                        tempLocation: selected.tempLocation ?? undefined,
                        pottingDueDate: selected.pottingDueDate ?? undefined,
                        potSize: selected.potSize ?? undefined,
                        ageYear: selected.ageYear ?? undefined,
                        growthStage: selected.growthStage ?? undefined,
                        placementType: selected.placementType ?? undefined,
                        trayCount: selected.trayCount ?? undefined,
                        worker: selected.worker ?? undefined,
                        memo: selected.memo ?? undefined,
                      });
                    }}
                  >
                    수정
                  </button>
                ) : null}
                {selected.inboundType === "FLASK_SEEDLING" &&
                !selected.createdOrchidGroupId &&
                selected.status !== "CANCELED" ? (
                  <button
                    className="flex items-center gap-1 rounded-md border border-[#d7ddd8] px-3 py-1.5 text-xs font-semibold"
                    type="button"
                    onClick={() => setDialog("potting")}
                  >
                    <Scissors className="h-3.5 w-3.5" />
                    포트 작업 등록
                  </button>
                ) : null}
                {!selected.createdOrchidGroupId &&
                selected.status !== "CANCELED" ? (
                  <button
                    className="rounded-md border border-[#e2c8c8] px-3 py-1.5 text-xs font-semibold text-[#a14545]"
                    type="button"
                    onClick={() => setDialog("cancel")}
                  >
                    입고 취소
                  </button>
                ) : null}
              </div>
            </div>
            {editing ? (
              <form
                className="mt-3 grid gap-3 md:grid-cols-2"
                onSubmit={(event) => {
                  event.preventDefault();
                  void onUpdate(selected.id, editForm).then(() =>
                    setEditing(false),
                  );
                }}
              >
                <DetailRow
                  label="입고 유형"
                  value={INBOUND_TYPE_LABELS[selected.inboundType]}
                />
                <DetailRow
                  label="품종명"
                  value={`${selected.genus} / ${selected.varietyName}`}
                />
                <Field label="입고일">
                  <input
                    className={inputClass}
                    required
                    type="date"
                    value={editForm.inboundDate}
                    onChange={(event) =>
                      setEditForm((current) => ({
                        ...current,
                        inboundDate: event.target.value,
                      }))
                    }
                  />
                </Field>
                <Field label="병 수">
                  <input
                    className={inputClass}
                    type="number"
                    value={editForm.bottleCount ?? ""}
                    onChange={(event) =>
                      setEditForm((current) => ({
                        ...current,
                        bottleCount: toOptionalNumber(event.target.value),
                      }))
                    }
                  />
                </Field>
                <Field label="예상 수량">
                  <input
                    className={inputClass}
                    type="number"
                    value={editForm.estimatedQuantity ?? ""}
                    onChange={(event) =>
                      setEditForm((current) => ({
                        ...current,
                        estimatedQuantity: toOptionalNumber(event.target.value),
                      }))
                    }
                  />
                </Field>
                <Field label="실제 수량">
                  <input
                    className={inputClass}
                    type="number"
                    value={editForm.actualQuantity ?? ""}
                    onChange={(event) =>
                      setEditForm((current) => ({
                        ...current,
                        actualQuantity: toOptionalNumber(event.target.value),
                      }))
                    }
                  />
                </Field>
                <Field label="임시 위치">
                  <input
                    className={inputClass}
                    value={editForm.tempLocation ?? ""}
                    onChange={(event) =>
                      setEditForm((current) => ({
                        ...current,
                        tempLocation: event.target.value,
                      }))
                    }
                  />
                </Field>
                <Field label="포트 예정일">
                  <input
                    className={inputClass}
                    type="date"
                    value={editForm.pottingDueDate ?? ""}
                    onChange={(event) =>
                      setEditForm((current) => ({
                        ...current,
                        pottingDueDate: event.target.value || undefined,
                      }))
                    }
                  />
                </Field>
                <Field label="화분 크기">
                  <input
                    className={inputClass}
                    value={editForm.potSize ?? ""}
                    onChange={(event) =>
                      setEditForm((current) => ({
                        ...current,
                        potSize: event.target.value,
                      }))
                    }
                  />
                </Field>
                <Field label="연차">
                  <input
                    className={inputClass}
                    type="number"
                    value={editForm.ageYear ?? ""}
                    onChange={(event) =>
                      setEditForm((current) => ({
                        ...current,
                        ageYear: toOptionalNumber(event.target.value),
                      }))
                    }
                  />
                </Field>
                <Field label="생육 단계">
                  <input
                    className={inputClass}
                    value={editForm.growthStage ?? ""}
                    onChange={(event) =>
                      setEditForm((current) => ({
                        ...current,
                        growthStage: event.target.value,
                      }))
                    }
                  />
                </Field>
                <Field label="배치 형태">
                  <input
                    className={inputClass}
                    value={editForm.placementType ?? ""}
                    onChange={(event) =>
                      setEditForm((current) => ({
                        ...current,
                        placementType: event.target.value,
                      }))
                    }
                  />
                </Field>
                <Field label="판 수">
                  <input
                    className={inputClass}
                    type="number"
                    value={editForm.trayCount ?? ""}
                    onChange={(event) =>
                      setEditForm((current) => ({
                        ...current,
                        trayCount: toOptionalNumber(event.target.value),
                      }))
                    }
                  />
                </Field>
                <Field label="작업자">
                  <input
                    className={inputClass}
                    value={editForm.worker ?? ""}
                    onChange={(event) =>
                      setEditForm((current) => ({
                        ...current,
                        worker: event.target.value,
                      }))
                    }
                  />
                </Field>
                <label className="space-y-1 text-xs font-semibold text-[#425047] md:col-span-2">
                  <span>메모</span>
                  <textarea
                    className="min-h-20 w-full rounded-md border border-[#d7ddd8] bg-white px-3 py-2 text-sm outline-none focus:border-[#159447] focus:ring-1 focus:ring-[#159447]"
                    value={editForm.memo ?? ""}
                    onChange={(event) =>
                      setEditForm((current) => ({
                        ...current,
                        memo: event.target.value,
                      }))
                    }
                  />
                </label>
                <div className="flex justify-end md:col-span-2">
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
                <DetailRow label="입고일" value={selected.inboundDate} />
                <DetailRow
                  label="입고 유형"
                  value={INBOUND_TYPE_LABELS[selected.inboundType]}
                />
                <DetailRow label="속" value={selected.genus} />
                <DetailRow label="품종명" value={selected.varietyName} />
                <DetailRow
                  label="상태"
                  value={INBOUND_STATUS_LABELS[selected.status]}
                />
                <DetailRow label="병 수" value={selected.bottleCount} />
                <DetailRow
                  label="예상 수량"
                  value={selected.estimatedQuantity}
                />
                <DetailRow label="실제 수량" value={selected.actualQuantity} />
                <DetailRow label="임시 위치" value={selected.tempLocation} />
                <DetailRow label="현재 위치" value={selected.currentLocation} />
                <DetailRow
                  label="포트 예정일"
                  value={selected.pottingDueDate}
                />
                <DetailRow label="포트 작업일" value={selected.pottingDate} />
                <DetailRow label="화분 크기" value={selected.potSize} />
                <DetailRow label="연차" value={selected.ageYear} />
                <DetailRow label="생육 단계" value={selected.growthStage} />
                <DetailRow label="배치 형태" value={selected.placementType} />
                <DetailRow label="판 수" value={selected.trayCount} />
                <DetailRow label="작업자" value={selected.worker} />
                <DetailRow
                  label="생성 난 묶음"
                  value={selected.createdOrchidGroupId ?? "-"}
                />
                <DetailRow label="메모" value={selected.memo} />
              </dl>
            )}
          </section>
        ) : null}
      </div>

      <InboundCreateDialog
        houses={houses}
        open={dialog === "create"}
        varieties={varieties}
        onClose={() => setDialog(null)}
        onSubmit={async (payload) => {
          await onCreate(payload);
          setDialog(null);
        }}
      />

      <InboundPottingDialog
        houses={houses}
        open={dialog === "potting" && !!selected}
        record={selected ?? null}
        onClose={() => setDialog(null)}
        onSubmit={async (payload) => {
          if (!selected) return;
          await onPotting(selected.id, payload);
          setDialog(null);
        }}
      />

      <CancelDialog
        open={dialog === "cancel" && !!selected}
        title="입고 기록 취소"
        onClose={() => setDialog(null)}
        onSubmit={async (memo) => {
          if (!selected) return;
          await onCancel(selected.id, memo);
          setDialog(null);
        }}
      />
    </>
  );
}

function InboundCreateDialog({
  open,
  varieties,
  houses,
  onClose,
  onSubmit,
}: {
  open: boolean;
  varieties: Variety[];
  houses: House[];
  onClose: () => void;
  onSubmit: (payload: InboundRecordPayload) => Promise<void>;
}) {
  const [inboundType, setInboundType] = useState<InboundType>("FLASK_SEEDLING");
  const [inboundDate, setInboundDate] = useState(
    new Date().toISOString().slice(0, 10),
  );
  const [varietyMode, setVarietyMode] = useState<"existing" | "new">(
    "existing",
  );
  const [varietyId, setVarietyId] = useState(varieties[0]?.id ?? 0);
  const [newGenus, setNewGenus] = useState("");
  const [newName, setNewName] = useState("");
  const [newPotSize, setNewPotSize] = useState("");
  const [bottleCount, setBottleCount] = useState("");
  const [estimatedQuantity, setEstimatedQuantity] = useState("");
  const [actualQuantity, setActualQuantity] = useState("");
  const [tempLocation, setTempLocation] = useState("");
  const [pottingDueDate, setPottingDueDate] = useState("");
  const [potSize, setPotSize] = useState("");
  const [ageYear, setAgeYear] = useState("");
  const [growthStage, setGrowthStage] = useState("");
  const [placementType, setPlacementType] = useState("");
  const [trayCount, setTrayCount] = useState("");
  const [bedZoneId, setBedZoneId] = useState("");
  const [worker, setWorker] = useState("");
  const [memo, setMemo] = useState("");

  if (!open) return null;

  const zoneOptions = flattenZones(houses);
  const flaskType = inboundType === "FLASK_SEEDLING";

  return (
    <DialogShell title="새 입고 등록" onClose={onClose}>
      <form
        className="mt-4 grid gap-3 md:grid-cols-2"
        onSubmit={(event) => {
          event.preventDefault();
          void onSubmit({
            inboundDate,
            inboundType,
            varietyId: varietyMode === "existing" ? varietyId : undefined,
            newVariety:
              varietyMode === "new"
                ? {
                    genus: newGenus,
                    name: newName,
                    defaultPotSize: newPotSize,
                    memo,
                  }
                : undefined,
            bottleCount: toNumber(bottleCount),
            estimatedQuantity: toNumber(estimatedQuantity),
            actualQuantity: toNumber(actualQuantity),
            tempLocation: tempLocation.trim() || undefined,
            pottingDueDate: pottingDueDate || undefined,
            potSize: potSize.trim() || undefined,
            ageYear: toNumber(ageYear),
            growthStage: growthStage.trim() || undefined,
            placementType: placementType.trim() || undefined,
            trayCount: toNumber(trayCount),
            bedZoneId: toNumber(bedZoneId),
            worker: worker.trim() || undefined,
            memo: memo.trim() || undefined,
          });
        }}
      >
        <Field label="입고일">
          <input
            className={inputClass}
            required
            type="date"
            value={inboundDate}
            onChange={(event) => setInboundDate(event.target.value)}
          />
        </Field>
        <Field label="입고 유형">
          <select
            className={inputClass}
            value={inboundType}
            onChange={(event) =>
              setInboundType(event.target.value as InboundType)
            }
          >
            {Object.entries(INBOUND_TYPE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </Field>
        <Field label="품종 선택 방식">
          <select
            className={inputClass}
            value={varietyMode}
            onChange={(event) =>
              setVarietyMode(event.target.value as "existing" | "new")
            }
          >
            <option value="existing">기존 품종</option>
            <option value="new">새 품종</option>
          </select>
        </Field>
        {varietyMode === "existing" ? (
          <Field label="품종">
            <select
              className={inputClass}
              value={varietyId}
              onChange={(event) => setVarietyId(Number(event.target.value))}
            >
              {varieties.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.genus} / {item.name}
                </option>
              ))}
            </select>
          </Field>
        ) : (
          <div className="grid gap-3 md:col-span-2 md:grid-cols-3">
            <Field label="새 속">
              <input
                className={inputClass}
                required
                value={newGenus}
                onChange={(event) => setNewGenus(event.target.value)}
              />
            </Field>
            <Field label="새 품종명">
              <input
                className={inputClass}
                required
                value={newName}
                onChange={(event) => setNewName(event.target.value)}
              />
            </Field>
            <Field label="기본 화분">
              <input
                className={inputClass}
                value={newPotSize}
                onChange={(event) => setNewPotSize(event.target.value)}
              />
            </Field>
          </div>
        )}
        {flaskType ? (
          <>
            <Field label="유리병 수">
              <input
                className={inputClass}
                required
                type="number"
                value={bottleCount}
                onChange={(event) => setBottleCount(event.target.value)}
              />
            </Field>
            <Field label="예상 수량">
              <input
                className={inputClass}
                required
                type="number"
                value={estimatedQuantity}
                onChange={(event) => setEstimatedQuantity(event.target.value)}
              />
            </Field>
            <Field label="임시 위치">
              <input
                className={inputClass}
                value={tempLocation}
                onChange={(event) => setTempLocation(event.target.value)}
              />
            </Field>
            <Field label="포트 작업 예정일">
              <input
                className={inputClass}
                type="date"
                value={pottingDueDate}
                onChange={(event) => setPottingDueDate(event.target.value)}
              />
            </Field>
          </>
        ) : (
          <>
            <Field label="실제 수량">
              <input
                className={inputClass}
                required
                type="number"
                value={actualQuantity}
                onChange={(event) => setActualQuantity(event.target.value)}
              />
            </Field>
            <Field label="화분 크기">
              <input
                className={inputClass}
                value={potSize}
                onChange={(event) => setPotSize(event.target.value)}
              />
            </Field>
            <Field label="연차">
              <input
                className={inputClass}
                type="number"
                value={ageYear}
                onChange={(event) => setAgeYear(event.target.value)}
              />
            </Field>
            <Field label="생육 단계">
              <input
                className={inputClass}
                value={growthStage}
                onChange={(event) => setGrowthStage(event.target.value)}
              />
            </Field>
            <Field label="배치 형태">
              <input
                className={inputClass}
                value={placementType}
                onChange={(event) => setPlacementType(event.target.value)}
              />
            </Field>
            <Field label="판 수">
              <input
                className={inputClass}
                type="number"
                value={trayCount}
                onChange={(event) => setTrayCount(event.target.value)}
              />
            </Field>
            <Field label="배치 위치">
              <select
                className={inputClass}
                required
                value={bedZoneId}
                onChange={(event) => setBedZoneId(event.target.value)}
              >
                <option value="">선택</option>
                {zoneOptions.map((zone) => (
                  <option key={zone.id} value={zone.id}>
                    {zone.label}
                  </option>
                ))}
              </select>
            </Field>
          </>
        )}
        <Field label="작업자">
          <input
            className={inputClass}
            value={worker}
            onChange={(event) => setWorker(event.target.value)}
          />
        </Field>
        <label className="space-y-1 text-xs font-semibold text-[#425047] md:col-span-2">
          <span>메모</span>
          <textarea
            className="min-h-24 w-full rounded-md border border-[#d7ddd8] bg-white px-3 py-2 text-sm outline-none focus:border-[#159447] focus:ring-1 focus:ring-[#159447]"
            value={memo}
            onChange={(event) => setMemo(event.target.value)}
          />
        </label>
        <div className="flex justify-end gap-2 md:col-span-2">
          <button
            className="rounded-md border border-[#d4dbd5] px-4 py-2 text-sm font-semibold"
            type="button"
            onClick={onClose}
          >
            취소
          </button>
          <button
            className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white"
            type="submit"
          >
            저장
          </button>
        </div>
      </form>
    </DialogShell>
  );
}

function InboundPottingDialog({
  open,
  record,
  houses,
  onClose,
  onSubmit,
}: {
  open: boolean;
  record: InboundRecord | null;
  houses: House[];
  onClose: () => void;
  onSubmit: (payload: InboundPottingPayload) => Promise<void>;
}) {
  const [pottingDate, setPottingDate] = useState(
    new Date().toISOString().slice(0, 10),
  );
  const [actualQuantity, setActualQuantity] = useState("");
  const [potSize, setPotSize] = useState("");
  const [ageYear, setAgeYear] = useState("");
  const [growthStage, setGrowthStage] = useState("");
  const [placementType, setPlacementType] = useState("");
  const [trayCount, setTrayCount] = useState("");
  const [bedZoneId, setBedZoneId] = useState("");
  const [worker, setWorker] = useState("");
  const [memo, setMemo] = useState("");

  if (!open || !record) return null;

  const zoneOptions = flattenZones(houses);

  return (
    <DialogShell title="포트 작업 등록" onClose={onClose}>
      <form
        className="mt-4 grid gap-3 md:grid-cols-2"
        onSubmit={(event) => {
          event.preventDefault();
          void onSubmit({
            pottingDate,
            actualQuantity: Number(actualQuantity),
            potSize: potSize.trim() || undefined,
            ageYear: toNumber(ageYear),
            growthStage: growthStage.trim() || undefined,
            placementType: placementType.trim() || undefined,
            trayCount: toNumber(trayCount),
            bedZoneId: Number(bedZoneId),
            worker: worker.trim() || undefined,
            memo: memo.trim() || undefined,
          });
        }}
      >
        <Field label="입고 품종">
          <input className={inputClass} disabled value={record.varietyName} />
        </Field>
        <Field label="포트 작업일">
          <input
            className={inputClass}
            required
            type="date"
            value={pottingDate}
            onChange={(event) => setPottingDate(event.target.value)}
          />
        </Field>
        <Field label="실제 생성 수량">
          <input
            className={inputClass}
            required
            type="number"
            value={actualQuantity}
            onChange={(event) => setActualQuantity(event.target.value)}
          />
        </Field>
        <Field label="화분 크기">
          <input
            className={inputClass}
            value={potSize}
            onChange={(event) => setPotSize(event.target.value)}
          />
        </Field>
        <Field label="연차">
          <input
            className={inputClass}
            type="number"
            value={ageYear}
            onChange={(event) => setAgeYear(event.target.value)}
          />
        </Field>
        <Field label="생육 단계">
          <input
            className={inputClass}
            value={growthStage}
            onChange={(event) => setGrowthStage(event.target.value)}
          />
        </Field>
        <Field label="배치 형태">
          <input
            className={inputClass}
            value={placementType}
            onChange={(event) => setPlacementType(event.target.value)}
          />
        </Field>
        <Field label="판 수">
          <input
            className={inputClass}
            type="number"
            value={trayCount}
            onChange={(event) => setTrayCount(event.target.value)}
          />
        </Field>
        <Field label="배치 위치">
          <select
            className={inputClass}
            required
            value={bedZoneId}
            onChange={(event) => setBedZoneId(event.target.value)}
          >
            <option value="">선택</option>
            {zoneOptions.map((zone) => (
              <option key={zone.id} value={zone.id}>
                {zone.label}
              </option>
            ))}
          </select>
        </Field>
        <Field label="작업자">
          <input
            className={inputClass}
            value={worker}
            onChange={(event) => setWorker(event.target.value)}
          />
        </Field>
        <label className="space-y-1 text-xs font-semibold text-[#425047] md:col-span-2">
          <span>메모</span>
          <textarea
            className="min-h-24 w-full rounded-md border border-[#d7ddd8] bg-white px-3 py-2 text-sm outline-none focus:border-[#159447] focus:ring-1 focus:ring-[#159447]"
            value={memo}
            onChange={(event) => setMemo(event.target.value)}
          />
        </label>
        <div className="flex justify-end gap-2 md:col-span-2">
          <button
            className="rounded-md border border-[#d4dbd5] px-4 py-2 text-sm font-semibold"
            type="button"
            onClick={onClose}
          >
            취소
          </button>
          <button
            className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white"
            type="submit"
          >
            저장
          </button>
        </div>
      </form>
    </DialogShell>
  );
}

function CancelDialog({
  open,
  title,
  onClose,
  onSubmit,
}: {
  open: boolean;
  title: string;
  onClose: () => void;
  onSubmit: (memo: string) => Promise<void>;
}) {
  const [memo, setMemo] = useState("");

  if (!open) return null;

  return (
    <DialogShell title={title} onClose={onClose}>
      <form
        className="mt-4 space-y-3"
        onSubmit={(event) => {
          event.preventDefault();
          void onSubmit(memo);
        }}
      >
        <label className="space-y-1 text-xs font-semibold text-[#425047]">
          <span>사유</span>
          <textarea
            className="min-h-24 w-full rounded-md border border-[#d7ddd8] bg-white px-3 py-2 text-sm outline-none focus:border-[#159447] focus:ring-1 focus:ring-[#159447]"
            value={memo}
            onChange={(event) => setMemo(event.target.value)}
          />
        </label>
        <div className="flex justify-end gap-2">
          <button
            className="rounded-md border border-[#d4dbd5] px-4 py-2 text-sm font-semibold"
            type="button"
            onClick={onClose}
          >
            닫기
          </button>
          <button
            className="rounded-md bg-[#a14545] px-4 py-2 text-sm font-semibold text-white"
            type="submit"
          >
            취소 확정
          </button>
        </div>
      </form>
    </DialogShell>
  );
}

function DialogShell({
  title,
  children,
  onClose,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
}) {
  return (
    <div
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 p-4"
      role="presentation"
      onMouseDown={onClose}
    >
      <section
        className="w-full max-w-3xl rounded-md bg-white p-5 shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="flex items-center justify-between">
          <h2 className="text-base font-bold">{title}</h2>
          <button
            className="flex h-8 w-8 items-center justify-center rounded border border-[#d9dfda]"
            type="button"
            onClick={onClose}
            aria-label="닫기"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
        {children}
      </section>
    </div>
  );
}

function flattenZones(houses: House[]) {
  return houses.flatMap((house) =>
    house.physicalBeds.flatMap((bed) =>
      bed.bedZones.map((zone) => ({
        id: zone.id,
        label: `${house.number}동 ${bed.number}배드 ${zone.name}`,
      })),
    ),
  );
}

function toNumber(value: string) {
  if (!value.trim()) return undefined;
  return Number(value);
}

function toOptionalNumber(value: string) {
  if (!value.trim()) return undefined;
  return Number(value);
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
