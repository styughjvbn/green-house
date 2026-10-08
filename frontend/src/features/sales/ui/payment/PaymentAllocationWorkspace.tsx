"use client";

import { useRef, useState, useSyncExternalStore } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useSearchParams, usePathname } from "next/navigation";
import Link from "next/link";
import type { Route } from "next";
import { salesV2Href } from "@/shared/config/routes";
import { ApiError } from "@/shared/api/client";
import { useUrlSearchParamsWriter } from "@/shared/lib/useUrlSearchParamsWriter";
import { useRuntimeContext } from "@/shared/runtime/RuntimeContext";
import { createUuid } from "@/shared/lib/id";
import { PaginationControls } from "@/shared/ui/PaginationControls";
import {
  Dialog,
  DialogContent,
  DialogTitle,
  DialogDescription,
} from "@/shared/ui/primitives/dialog";
import { BusinessPartnerSelect } from "../common/BusinessPartnerSelect";
import {
  getAllocationReceipts,
  getAllocationReceipt,
  getReceiptAllocations,
  getAllocationTargets,
  getAllocationMetadata,
  submitPaymentRequest,
} from "../../api/salesApi";
import type { AllocationTargetType } from "../../api/types";
import { readAllocationRouteState } from "../../lib/salesRouteParams";
import {
  allocationInput,
  correctionInput,
  type AllocationDraft,
} from "../../lib/paymentAllocationPayload";
import { paymentRequests as requests } from "../../model/paymentRequests";
import type { ReceiptRequest } from "../../lib/receiptRequest";
import { salesQueryKeys } from "../../model/salesQueryKeys";

const base = ["sales", "allocationWorkspace"] as const;
const inputClass = "mt-1 w-full rounded border bg-white p-2";
const buttonClass = "rounded border px-3 py-2 disabled:opacity-40";
const typeLabel = (type: AllocationTargetType) =>
  type === "SALES_SLIP"
    ? "일반 판매 전표"
    : type === "AUCTION_PROCEEDS"
      ? "경매 대금"
      : type;

export function PaymentAllocationWorkspace() {
  const params = useSearchParams();
  const write = useUrlSearchParamsWriter();
  const { partnerId } = readAllocationRouteState(params);
  return (
    <div className="min-h-0 flex-1 space-y-4 overflow-auto">
      <BusinessPartnerSelect
        label="배분 거래처"
        value={partnerId ? String(partnerId) : ""}
        onChange={(value) =>
          write((p) => {
            if (value) p.set("receiptPartnerId", value);
            else p.delete("receiptPartnerId");
            [
              "receiptPage",
              "sourcePage",
              "receiptId",
              "allocationPage",
            ].forEach((key) => p.delete(key));
          }, "push")
        }
      />
      {partnerId ? (
        <AllocationPanel key={partnerId} partnerId={partnerId} />
      ) : (
        <p>거래처를 선택하세요.</p>
      )}
    </div>
  );
}
function AllocationPanel({ partnerId }: { partnerId: number }) {
  const v2 = usePathname().startsWith("/sales-v2/");
  const params = useSearchParams();
  const write = useUrlSearchParamsWriter();
  const { page, receiptId, allocationPage } = readAllocationRouteState(params);
  const cache = useQueryClient();
  const { businessDate } = useRuntimeContext();
  const pending = useSyncExternalStore(
    requests.subscribe,
    () => requests.read(partnerId),
    () => null,
  );
  const source = useQuery({
    queryKey: [...base, partnerId, "sources", page, 10],
    queryFn: ({ signal }) => getAllocationReceipts(partnerId, page, signal),
  });
  const selected = useQuery({
    queryKey: [...base, partnerId, "receipt", receiptId],
    queryFn: ({ signal }) =>
      getAllocationReceipt(partnerId, receiptId!, signal),
    enabled: !!receiptId,
  });
  const history = useQuery({
    queryKey: [
      ...base,
      partnerId,
      "allocations",
      receiptId,
      allocationPage,
      10,
    ],
    queryFn: ({ signal }) =>
      getReceiptAllocations(partnerId, receiptId!, allocationPage, signal),
    enabled: !!receiptId,
  });
  const metadata = useQuery({
    queryKey: [...base, "metadata"],
    queryFn: ({ signal }) => getAllocationMetadata(signal),
  });
  const [checked, setChecked] = useState<number[]>([]);
  const eligibleIds = checked.filter((id) =>
    history.data?.content.some(
      (event) => event.id === id && event.cancellationAllowed,
    ),
  );
  const [mode, setMode] = useState<"ALLOCATE" | "CORRECT" | null>(null);
  const [cancellationIds, setCancellationIds] = useState<number[]>([]);
  const [rows, setRows] = useState<AllocationDraft[]>([]);
  const [date, setDate] = useState(businessDate);
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const opener = useRef<HTMLButtonElement | null>(null);
  const heading = useRef<HTMLHeadingElement | null>(null);
  function addRow() {
    setRows((current) => [
      ...current,
      {
        key: createUuid(),
        receiptId: String(receiptId ?? ""),
        targetType: metadata.data?.targetTypes[0] ?? "",
        targetId: "",
        amount: "",
      },
    ]);
  }
  function open(next: "ALLOCATE" | "CORRECT", button: HTMLButtonElement) {
    opener.current = button;
    setMode(next);
    setRows([]);
    setDate(businessDate);
    setReason("");
    setError(null);
    setCancellationIds(next === "CORRECT" ? eligibleIds : []);
    if (next === "ALLOCATE")
      setRows([
        {
          key: createUuid(),
          receiptId: String(receiptId ?? ""),
          targetType: metadata.data?.targetTypes[0] ?? "",
          targetId: "",
          amount: "",
        },
      ]);
  }
  async function execute(request: ReceiptRequest) {
    setSaving(true);
    setError(null);
    try {
      await submitPaymentRequest(partnerId, request);
      requests.complete(partnerId, request.payload.idempotencyKey);
      setMode(null);
      setChecked([]);
      await Promise.all([
        cache.invalidateQueries({ queryKey: [...base, partnerId] }),
        cache.invalidateQueries({ queryKey: salesQueryKeys.slips.all }),
        cache.invalidateQueries({
          queryKey: salesQueryKeys.auction.proceedsPages,
        }),
        cache.invalidateQueries({
          queryKey: ["sales", "auctionProceeds", "detail"],
        }),
        cache.invalidateQueries({ queryKey: ["sales", "paymentEvents"] }),
        cache.invalidateQueries({
          queryKey: salesQueryKeys.payments.unassigned(partnerId),
        }),
        cache.invalidateQueries({
          queryKey: salesQueryKeys.payments.balance(partnerId),
        }),
        cache.invalidateQueries({ queryKey: salesQueryKeys.partners.all }),
      ]);
    } catch (failure) {
      if (
        failure instanceof ApiError &&
        [400, 404, 409].includes(failure.status) &&
        failure.code !== "IDEMPOTENCY_KEY_REUSED"
      ) {
        requests.complete(partnerId, request.payload.idempotencyKey);
        await cache.invalidateQueries({ queryKey: [...base, partnerId] });
      }
      setError(
        failure instanceof Error
          ? failure.message
          : "배분을 저장하지 못했습니다.",
      );
    } finally {
      setSaving(false);
    }
  }
  return (
    <div className="space-y-4">
      <p>
        기록된 수납을 여러 전표·경매 대금에 배분합니다. 정정은 기존 배분을
        취소하며 원래 입금은 유지합니다.
      </p>
      {pending && (
        <div role="status" className="rounded border p-3">
          처리 결과 확인이 필요합니다.{" "}
          <button
            className={buttonClass}
            disabled={saving}
            onClick={() => void execute(pending)}
          >
            같은 요청 다시 확인
          </button>
        </div>
      )}
      {error && (
        <p role="alert" className="text-red-700">
          {error}
        </p>
      )}
      <h2 className="font-bold">수납 선택</h2>
      {source.isError ? (
        <p role="alert">
          수납을 불러오지 못했습니다.{" "}
          <button onClick={() => void source.refetch()}>다시 조회</button>
        </p>
      ) : source.isPending ? (
        <p>수납 확인 중</p>
      ) : (
        <>
          {!source.data.content.length && <p>수납 내역이 없습니다.</p>}
          <ul className="divide-y rounded border">
            {source.data.content.map((item) => (
              <li key={item.id} className="p-3">
                <button
                  type="button"
                  aria-pressed={receiptId === item.id}
                  className={buttonClass}
                  onClick={() => {
                    setChecked([]);
                    write((p) => {
                      p.set("receiptId", String(item.id));
                      p.delete("allocationPage");
                    }, "push");
                  }}
                >
                  수납 #{item.id} · {item.paymentDate} ·{" "}
                  {item.amount.toLocaleString()}원 · 미배분{" "}
                  {item.availableAmount.toLocaleString()}원
                  {item.reviewRequired ? " · 검토 필요" : ""}
                  {item.status === "CANCELLED" ? " · 취소됨" : ""}
                </button>
              </li>
            ))}
          </ul>
          <PaginationControls
            pageIndex={page}
            pageCount={source.data.totalPages}
            onPageChange={(next) =>
              write((p) => p.set("sourcePage", String(next)), "push")
            }
          />
        </>
      )}
      {receiptId && (
        <section className="space-y-3">
          <h2 ref={heading} tabIndex={-1} className="font-bold">
            선택 수납 #{receiptId}의 배분 내역
          </h2>
          {selected.isError && (
            <p role="alert">
              선택 수납을 불러오지 못했습니다.{" "}
              <button onClick={() => void selected.refetch()}>다시 조회</button>
            </p>
          )}
          {selected.data && (
            <p>
              입금 {selected.data.amount.toLocaleString()}원 / 미배분{" "}
              {selected.data.availableAmount.toLocaleString()}원
              {selected.data.reviewRequired ? " · 원장 검토가 필요합니다." : ""}
            </p>
          )}
          <div className="flex gap-2">
            <button
              className={buttonClass}
              disabled={
                saving ||
                !!pending ||
                !selected.data?.allocationAllowed ||
                !metadata.data
              }
              onClick={(e) => open("ALLOCATE", e.currentTarget)}
            >
              배분 추가
            </button>
            <button
              className={buttonClass}
              disabled={
                saving ||
                !!pending ||
                !selected.data?.correctionAllowed ||
                !eligibleIds.length ||
                !metadata.data
              }
              onClick={(e) => open("CORRECT", e.currentTarget)}
            >
              선택 배분 정정
            </button>
          </div>
          {history.isError ? (
            <p role="alert">
              배분 내역을 불러오지 못했습니다.{" "}
              <button onClick={() => void history.refetch()}>다시 조회</button>
            </p>
          ) : history.isPending ? (
            <p>배분 확인 중</p>
          ) : (
            <>
              {!history.data.content.length && <p>배분 내역이 없습니다.</p>}
              <ul className="divide-y rounded border">
                {history.data.content.map((item) => (
                  <li key={item.id} className="p-3">
                    <label>
                      <input
                        type="checkbox"
                        aria-label={`배분 #${item.id} 선택`}
                        checked={eligibleIds.includes(item.id)}
                        disabled={
                          saving || !!pending || !item.cancellationAllowed
                        }
                        onChange={(e) =>
                          setChecked((ids) =>
                            e.target.checked
                              ? [...ids, item.id]
                              : ids.filter((id) => id !== item.id),
                          )
                        }
                      />{" "}
                      {typeLabel(item.targetType)} #{item.targetId} ·{" "}
                      {item.amount.toLocaleString()}원 ·{" "}
                      {item.status === "CANCELLED" ? "취소됨" : "유효 배분"}
                    </label>
                    {v2 && item.targetId != null ? (
                      <Link
                        className="ml-3 text-sm text-green-800 underline"
                        href={
                          salesV2Href(
                            item.targetType === "SALES_SLIP"
                              ? "slips"
                              : "auction",
                            item.targetType === "SALES_SLIP"
                              ? { slipId: item.targetId, paymentPage: 0 }
                              : {
                                  panel: "proceeds",
                                  proceedsId: item.targetId,
                                  paymentPage: 0,
                                },
                          ) as Route
                        }
                      >
                        배분 대상 보기
                      </Link>
                    ) : null}
                  </li>
                ))}
              </ul>
              <PaginationControls
                pageIndex={allocationPage}
                pageCount={history.data.totalPages}
                onPageChange={(next) => {
                  setChecked([]);
                  write((p) => p.set("allocationPage", String(next)), "push");
                }}
              />
            </>
          )}
        </section>
      )}
      {metadata.isError && (
        <p role="alert">
          배분 업무 정보를 불러오지 못했습니다.{" "}
          <button onClick={() => void metadata.refetch()}>다시 조회</button>
        </p>
      )}
      <Dialog
        open={mode != null}
        onOpenChange={(open) => {
          if (!open && !saving) setMode(null);
        }}
      >
        <DialogContent
          showCloseButton={false}
          className="max-h-[90vh] overflow-auto sm:max-w-3xl"
          onCloseAutoFocus={(e) => {
            e.preventDefault();
            if (opener.current?.isConnected && !opener.current.disabled)
              opener.current.focus();
            else heading.current?.focus();
          }}
          onEscapeKeyDown={(e) => {
            if (saving) e.preventDefault();
          }}
          onPointerDownOutside={(e) => {
            if (saving) e.preventDefault();
          }}
        >
          <DialogTitle>
            {mode === "CORRECT" ? "배분 정정" : "수납 배분"}
          </DialogTitle>
          <DialogDescription>
            {mode === "CORRECT"
              ? `${cancellationIds.length}건을 취소합니다. 새 배분을 추가하면 함께 반영됩니다. 실제 입금은 취소하지 않습니다.`
              : "같은 거래처의 수납과 배분 대상을 선택하세요."}
          </DialogDescription>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              try {
                const input =
                  mode === "CORRECT"
                    ? {
                        operation: "CORRECT" as const,
                        payload: correctionInput(
                          rows,
                          date,
                          cancellationIds,
                          reason,
                        ),
                      }
                    : {
                        operation: "ALLOCATE" as const,
                        payload: allocationInput(rows, date),
                      };
                void execute(requests.prepare(partnerId, input));
              } catch (failure) {
                setError(
                  failure instanceof Error
                    ? failure.message
                    : "입력 값을 확인하세요.",
                );
              }
            }}
          >
            <fieldset disabled={saving || !!pending} className="space-y-3">
              <label className="block">
                {mode === "CORRECT" ? "정정일" : "배분일"}
                <input
                  className={inputClass}
                  type="date"
                  required
                  value={date}
                  onChange={(e) => setDate(e.target.value)}
                />
              </label>
              {mode === "CORRECT" && (
                <label className="block">
                  정정 사유
                  <input
                    className={inputClass}
                    required
                    maxLength={1000}
                    value={reason}
                    onChange={(e) => setReason(e.target.value)}
                  />
                </label>
              )}
              {rows.map((row, index) => (
                <div key={row.key} className="space-y-2 rounded border p-3">
                  <h3>새 배분 {index + 1}</h3>
                  <AllocationRow
                    partnerId={partnerId}
                    row={row}
                    correcting={mode === "CORRECT"}
                    types={metadata.data?.targetTypes ?? []}
                    onChange={(updated) =>
                      setRows((all) =>
                        all.map((value) =>
                          value.key === row.key ? updated : value,
                        ),
                      )
                    }
                  />
                  <button
                    type="button"
                    className={buttonClass}
                    onClick={() =>
                      setRows((all) =>
                        all.filter((value) => value.key !== row.key),
                      )
                    }
                  >
                    항목 삭제
                  </button>
                </div>
              ))}
              <button
                type="button"
                className={buttonClass}
                disabled={rows.length >= 100}
                onClick={addRow}
              >
                배분 항목 추가
              </button>
              <button
                type="submit"
                className="ml-2 rounded bg-[#159447] px-4 py-2 text-white"
              >
                {mode === "CORRECT" ? "정정 확정" : "배분 확정"}
              </button>
            </fieldset>
            {error && (
              <p role="alert" className="text-red-700">
                {error}
              </p>
            )}
            <button
              type="button"
              className={`${buttonClass} mt-3`}
              disabled={saving}
              onClick={() => setMode(null)}
            >
              닫기
            </button>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
function AllocationRow({
  partnerId,
  row,
  types,
  correcting,
  onChange,
}: {
  partnerId: number;
  row: AllocationDraft;
  types: AllocationTargetType[];
  correcting: boolean;
  onChange: (row: AllocationDraft) => void;
}) {
  const [sourcePage, setSourcePage] = useState(0);
  const [targetPage, setTargetPage] = useState(0);
  const [keyword, setKeyword] = useState("");
  const sources = useQuery({
    queryKey: [...base, partnerId, "sources", sourcePage, 10],
    queryFn: ({ signal }) =>
      getAllocationReceipts(partnerId, sourcePage, signal),
  });
  const selectedSource = useQuery({
    queryKey: [...base, partnerId, "receipt", Number(row.receiptId)],
    queryFn: ({ signal }) =>
      getAllocationReceipt(partnerId, Number(row.receiptId), signal),
    enabled: !!row.receiptId,
  });
  const targets = useQuery({
    queryKey: [
      ...base,
      partnerId,
      "targets",
      row.targetType,
      keyword,
      targetPage,
      10,
    ],
    queryFn: ({ signal }) =>
      getAllocationTargets(
        partnerId,
        row.targetType as AllocationTargetType,
        keyword,
        targetPage,
        signal,
      ),
    enabled: !!row.targetType,
  });
  const options = sources.data?.content ?? [];
  const extra =
    selectedSource.data &&
    !options.some((item) => item.id === selectedSource.data.id)
      ? [selectedSource.data]
      : [];
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <div>
        <label className="block">
          배분할 수납
          <select
            className={inputClass}
            required
            aria-label="배분할 수납"
            value={row.receiptId}
            onChange={(e) => onChange({ ...row, receiptId: e.target.value })}
          >
            <option value="">선택</option>
            {[...extra, ...options].map((item) => (
              <option
                key={item.id}
                value={item.id}
                disabled={
                  !(
                    item.allocationAllowed ||
                    (correcting && item.correctionAllowed)
                  )
                }
              >
                #{item.id} · 미배분 {item.availableAmount.toLocaleString()}원
              </option>
            ))}
          </select>
        </label>
        {sources.isError && (
          <p role="alert">
            수납 조회 실패{" "}
            <button type="button" onClick={() => void sources.refetch()}>
              다시 조회
            </button>
          </p>
        )}
        {selectedSource.isError && <p role="alert">선택 수납 확인 실패</p>}
        <PaginationControls
          pageIndex={sourcePage}
          pageCount={sources.data?.totalPages ?? 0}
          onPageChange={setSourcePage}
        />
      </div>
      <label>
        대상 종류
        <select
          className={inputClass}
          required
          aria-label="대상 종류"
          value={row.targetType}
          onChange={(e) => {
            setTargetPage(0);
            onChange({
              ...row,
              targetType: e.target.value as AllocationTargetType,
              targetId: "",
            });
          }}
        >
          <option value="">선택</option>
          {types.map((type) => (
            <option key={type} value={type}>
              {typeLabel(type)}
            </option>
          ))}
        </select>
      </label>
      <div>
        <label className="block">
          대상 번호 검색
          <input
            className={inputClass}
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value);
              setTargetPage(0);
              onChange({ ...row, targetId: "" });
            }}
          />
        </label>
        <label className="block">
          배분 대상
          <select
            className={inputClass}
            required
            aria-label="배분 대상"
            value={row.targetId}
            onChange={(e) => onChange({ ...row, targetId: e.target.value })}
          >
            <option value="">선택</option>
            {targets.data?.content.map((item) => (
              <option
                key={item.id}
                value={item.id}
                disabled={
                  !(correcting
                    ? item.correctionAllowed
                    : item.allocationAllowed)
                }
              >
                {item.sourceReference ?? `#${item.id}`} · 배분 가능{" "}
                {item.availableAmount == null
                  ? "미확인"
                  : `${item.availableAmount.toLocaleString()}원`}
                {item.reviewRequired ? " · 검토 필요" : ""}
              </option>
            ))}
          </select>
        </label>
        {targets.isError && (
          <p role="alert">
            대상 조회 실패{" "}
            <button type="button" onClick={() => void targets.refetch()}>
              다시 조회
            </button>
          </p>
        )}
        <PaginationControls
          pageIndex={targetPage}
          pageCount={targets.data?.totalPages ?? 0}
          onPageChange={(next) => {
            setTargetPage(next);
            onChange({ ...row, targetId: "" });
          }}
        />
      </div>
      <label>
        배분액
        <input
          className={inputClass}
          required
          type="number"
          min="1"
          max={Number.MAX_SAFE_INTEGER}
          step="1"
          value={row.amount}
          onChange={(e) => onChange({ ...row, amount: e.target.value })}
        />
      </label>
    </div>
  );
}
