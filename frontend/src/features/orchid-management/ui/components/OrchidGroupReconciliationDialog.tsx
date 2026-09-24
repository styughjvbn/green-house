"use client";

import { useMemo, useState, type FormEvent } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import type { OrchidGroup, WorkOperation } from "@/entities/farm/types";
import { createUuid } from "@/shared/lib/id";
import { useRuntimeContext } from "@/shared/runtime/RuntimeContext";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/shared/ui/primitives/dialog";
import {
  getOrchidManagementHouses,
  reconcileOrchidGroup,
} from "../../api/orchidManagementApi";

export default function OrchidGroupReconciliationDialog({
  orchidGroup,
  onClose,
  onSaved,
}: {
  orchidGroup: OrchidGroup;
  onClose: () => void;
  onSaved: (operation: WorkOperation) => void;
}) {
  const { businessDate } = useRuntimeContext();
  const [quantity, setQuantity] = useState(String(orchidGroup.quantity));
  const [status, setStatus] = useState(orchidGroup.status);
  const [bedZoneId, setBedZoneId] = useState(String(orchidGroup.bedZoneId));
  const [startPosition, setStartPosition] = useState(
    orchidGroup.startPosition == null ? "" : String(orchidGroup.startPosition),
  );
  const [endPosition, setEndPosition] = useState(
    orchidGroup.endPosition == null ? "" : String(orchidGroup.endPosition),
  );
  const [workDate, setWorkDate] = useState(businessDate);
  const [worker, setWorker] = useState("");
  const [reason, setReason] = useState("");
  const [memo, setMemo] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [idempotencyKey] = useState(() =>
    `reconcile-${orchidGroup.id}-${createUuid()}`.slice(0, 100),
  );
  const housesQuery = useQuery({
    queryKey: ["orchid-management", "houses"],
    queryFn: getOrchidManagementHouses,
    staleTime: 30_000,
  });
  const bedZones = useMemo(
    () =>
      (housesQuery.data ?? []).flatMap((house) =>
        house.physicalBeds.flatMap((bed) =>
          bed.bedZones.map((zone) => ({
            id: zone.id,
            label: `${house.number}동 · ${bed.number}번 다이 · ${zone.name}`,
          })),
        ),
      ),
    [housesQuery.data],
  );
  const selectedZone = bedZones.find((zone) => String(zone.id) === bedZoneId);
  const changed =
    Number(quantity) !== orchidGroup.quantity ||
    status.trim() !== orchidGroup.status ||
    Number(bedZoneId) !== orchidGroup.bedZoneId ||
    Number(startPosition) !== orchidGroup.startPosition ||
    Number(endPosition) !== orchidGroup.endPosition;
  const mutation = useMutation({
    mutationFn: () =>
      reconcileOrchidGroup(orchidGroup.id, {
        idempotencyKey,
        title: `${orchidGroup.varietyName} 현장 상태 동기화`,
        workDate,
        worker: worker.trim() || null,
        memo: memo.trim() || null,
        reason: reason.trim(),
        actualQuantity: Number(quantity),
        actualStatus: status.trim(),
        actualBedZoneId: Number(bedZoneId),
        actualStartPosition: Number(startPosition),
        actualEndPosition: Number(endPosition),
      }),
    onSuccess(operation) {
      onSaved(operation);
      onClose();
    },
  });
  const requestError =
    mutation.error instanceof Error
      ? mutation.error.message
      : housesQuery.error instanceof Error
        ? housesQuery.error.message
        : null;

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    if (
      !workDate ||
      !reason.trim() ||
      !status.trim() ||
      !bedZoneId ||
      quantity === "" ||
      startPosition === "" ||
      endPosition === ""
    ) {
      setFormError("현장 확인값과 동기화 사유를 모두 입력해주세요.");
      return;
    }
    if (Number(quantity) < 0 || Number(startPosition) > Number(endPosition)) {
      setFormError("수량과 위치 범위를 확인해주세요.");
      return;
    }
    if (!changed) {
      setFormError("현재 시스템 값과 다른 현장 확인값이 없습니다.");
      return;
    }
    mutation.mutate();
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="flex max-h-[calc(100dvh-2rem)] max-w-2xl flex-col overflow-hidden rounded-lg">
        <header className="shrink-0 border-b border-[#e4e9e3] p-5 pr-14">
          <DialogTitle className="text-lg font-bold text-[#17251b]">
            현장 상태 동기화
          </DialogTitle>
          <DialogDescription className="mt-1 text-sm text-[#657168]">
            시스템 값과 실제 농장 상태의 차이를 새 보정 이력으로 반영합니다.
          </DialogDescription>
        </header>

        <form className="min-h-0 overflow-y-auto" onSubmit={submit}>
          <div className="space-y-4 p-5">
            <section className="rounded-md border border-[#dfe7df] bg-[#f8faf7] p-4">
              <p className="text-sm font-bold text-[#263d2c]">현재 시스템 값</p>
              <p className="mt-2 text-sm text-[#59665d]">
                {orchidGroup.quantity.toLocaleString()}분 · 상태{" "}
                {orchidGroup.status}
              </p>
              <p className="mt-1 text-sm text-[#59665d]">
                {orchidGroup.houseNumber}동 · {orchidGroup.physicalBedNumber}번
                다이 · {orchidGroup.bedZoneName} · 위치{" "}
                {orchidGroup.startPosition ?? "-"}~
                {orchidGroup.endPosition ?? "-"}
              </p>
            </section>

            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="현장 확인 수량">
                <input
                  className={inputClass}
                  min={0}
                  required
                  type="number"
                  value={quantity}
                  onChange={(event) => setQuantity(event.target.value)}
                />
              </Field>
              <Field label="현장 확인 상태">
                <input
                  className={inputClass}
                  maxLength={50}
                  required
                  value={status}
                  onChange={(event) => setStatus(event.target.value)}
                />
              </Field>
            </div>

            <Field label="현장 확인 구역">
              <select
                className={inputClass}
                disabled={housesQuery.isPending}
                required
                value={bedZoneId}
                onChange={(event) => setBedZoneId(event.target.value)}
              >
                {bedZones.length === 0 ? (
                  <option value={orchidGroup.bedZoneId}>
                    {orchidGroup.houseNumber}동 ·{" "}
                    {orchidGroup.physicalBedNumber}번 다이 ·{" "}
                    {orchidGroup.bedZoneName}
                  </option>
                ) : null}
                {bedZones.map((zone) => (
                  <option key={zone.id} value={zone.id}>
                    {zone.label}
                  </option>
                ))}
              </select>
            </Field>

            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="시작 위치">
                <input
                  className={inputClass}
                  min={0}
                  required
                  step="any"
                  type="number"
                  value={startPosition}
                  onChange={(event) => setStartPosition(event.target.value)}
                />
              </Field>
              <Field label="끝 위치">
                <input
                  className={inputClass}
                  min={0}
                  required
                  step="any"
                  type="number"
                  value={endPosition}
                  onChange={(event) => setEndPosition(event.target.value)}
                />
              </Field>
            </div>

            <section className="rounded-md border border-[#c9dce8] bg-[#f2f8fb] p-3 text-sm text-[#315f79]">
              <strong>반영 예정</strong> ·{" "}
              {Number(quantity || 0).toLocaleString()}분 · 상태 {status || "-"}{" "}
              · {selectedZone?.label ?? "구역 확인 중"} · 위치{" "}
              {startPosition || "-"}~{endPosition || "-"}
            </section>

            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="현장 확인일">
                <input
                  className={inputClass}
                  max={businessDate}
                  required
                  type="date"
                  value={workDate}
                  onChange={(event) => setWorkDate(event.target.value)}
                />
              </Field>
              <Field label="확인 작업자 (선택)">
                <input
                  className={inputClass}
                  maxLength={100}
                  value={worker}
                  onChange={(event) => setWorker(event.target.value)}
                />
              </Field>
            </div>

            <Field label="동기화 사유">
              <textarea
                className={`${inputClass} min-h-20 resize-y py-2`}
                maxLength={1000}
                placeholder="실물 확인 결과와 차이가 발생한 이유를 남겨주세요."
                required
                value={reason}
                onChange={(event) => setReason(event.target.value)}
              />
            </Field>
            <Field label="메모 (선택)">
              <textarea
                className={`${inputClass} min-h-16 resize-y py-2`}
                maxLength={1000}
                value={memo}
                onChange={(event) => setMemo(event.target.value)}
              />
            </Field>

            {formError || requestError ? (
              <p className="rounded-md border border-[#efc4b9] bg-[#fff4ef] p-3 text-sm text-[#9b341e]">
                {formError ?? requestError}
              </p>
            ) : null}
            <p className="text-xs text-[#69756d]">
              기존 이력은 수정하거나 삭제하지 않습니다. 현재 상태와 실제 상태의
              차이는 RECONCILIATION Mutation으로 기록됩니다.
            </p>
          </div>

          <footer className="sticky bottom-0 flex justify-end gap-2 border-t border-[#e4e9e3] bg-white p-4">
            <button
              className="rounded-md border border-[#d7ddd4] px-4 py-2 text-sm font-semibold"
              type="button"
              onClick={onClose}
            >
              취소
            </button>
            <button
              className="rounded-md bg-[#159447] px-4 py-2 text-sm font-bold text-white disabled:opacity-45"
              disabled={mutation.isPending || housesQuery.isPending}
              type="submit"
            >
              {mutation.isPending ? "동기화 중…" : "현장 상태 동기화"}
            </button>
          </footer>
        </form>
      </DialogContent>
    </Dialog>
  );
}

const inputClass =
  "mt-1 h-10 w-full rounded-md border border-[#cfd8cc] bg-white px-3 font-normal text-[#26332a] disabled:bg-[#f2f4f1]";

function Field({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <label className="block text-sm font-semibold text-[#435047]">
      {label}
      {children}
    </label>
  );
}
