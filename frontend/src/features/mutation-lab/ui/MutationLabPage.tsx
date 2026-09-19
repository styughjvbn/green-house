"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowRight, Database, GitBranch, RefreshCw } from "lucide-react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import type { Route } from "next";
import type { FormEvent } from "react";
import { PaginationControls } from "@/shared/ui/PaginationControls";
import {
  MUTATION_SOURCE_DOMAINS,
  MUTATION_TYPES,
  readMutationLabFilters,
  writeMutationLabFilters,
} from "../lib/mutationLabUrl";
import { mutationLabQueryOptions } from "../model/mutationLabQueryOptions";
import type {
  MutationEntry,
  MutationRelation,
  MutationState,
  OrchidGroupMutation,
} from "../model/types";

const TYPE_LABELS: Record<string, string> = {
  BASELINE_IMPORT: "기준 상태 이관",
  CREATE: "생성",
  UPDATE_DETAILS: "상세 수정",
  MOVE: "이동",
  RESERVE: "판매 예약",
  RELEASE_RESERVATION: "예약 해제",
  CONSUME_RESERVATION: "출고",
  RESTORE_OUTBOUND: "출고 복구",
  DISCARD: "폐기",
  TRANSFORM: "구조 변경",
  CORRECTION: "보정",
  COMPENSATION: "보상",
  CANCEL_CREATION: "생성 취소",
  DELETE: "삭제",
};

const DOMAIN_LABELS: Record<string, string> = {
  FARM: "농장",
  WORK: "작업",
  SALES: "판매",
  INBOUND: "입고",
  MIGRATION: "이관",
};

export function MutationLabPage() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const filters = readMutationLabFilters(searchParams);
  const query = useQuery(mutationLabQueryOptions(filters));
  const page = query.data;

  function navigate(updates: Partial<typeof filters>) {
    const queryString = writeMutationLabFilters(searchParams, updates);
    router.push(
      (queryString ? `${pathname}?${queryString}` : pathname) as Route,
    );
  }

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const groupValue = String(form.get("orchidGroupId") ?? "").trim();
    navigate({
      orchidGroupId: groupValue ? Number(groupValue) : null,
      mutationType:
        (String(
          form.get("mutationType") || "",
        ) as typeof filters.mutationType) || null,
      sourceDomain:
        (String(
          form.get("sourceDomain") || "",
        ) as typeof filters.sourceDomain) || null,
      page: 0,
    });
  }

  return (
    <div className="space-y-4 pb-8">
      <section className="rounded-md border border-[#d8e2d8] bg-white p-4 shadow-sm">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <div className="flex items-center gap-2 text-[#1d6f3a]">
              <GitBranch className="h-5 w-5" aria-hidden="true" />
              <h2 className="text-lg font-bold">Mutation 원장 탐색기</h2>
            </div>
            <p className="mt-1 text-sm text-[#647168]">
              변경 원인과 난 묶음별 revision, 전후 snapshot, 보정 관계를 읽기
              전용으로 확인합니다.
            </p>
          </div>
          <button
            className="inline-flex h-9 items-center gap-2 rounded-md border border-[#cfd8cc] px-3 text-sm font-semibold text-[#35533e] disabled:opacity-50"
            type="button"
            disabled={query.isFetching}
            onClick={() => void query.refetch()}
          >
            <RefreshCw
              className={`h-4 w-4 ${query.isFetching ? "animate-spin" : ""}`}
              aria-hidden="true"
            />
            새로고침
          </button>
        </div>

        <form
          key={`${filters.orchidGroupId}-${filters.mutationType}-${filters.sourceDomain}`}
          className="mt-4 grid gap-3 md:grid-cols-[1fr_1fr_1fr_auto_auto] md:items-end"
          onSubmit={search}
        >
          <FilterField label="난 묶음 ID">
            <input
              className={fieldClass}
              defaultValue={filters.orchidGroupId ?? ""}
              min="1"
              name="orchidGroupId"
              placeholder="전체"
              type="number"
            />
          </FilterField>
          <FilterField label="Mutation 유형">
            <select
              className={fieldClass}
              defaultValue={filters.mutationType ?? ""}
              name="mutationType"
            >
              <option value="">전체</option>
              {MUTATION_TYPES.map((type) => (
                <option key={type} value={type}>
                  {TYPE_LABELS[type]}
                </option>
              ))}
            </select>
          </FilterField>
          <FilterField label="발생 도메인">
            <select
              className={fieldClass}
              defaultValue={filters.sourceDomain ?? ""}
              name="sourceDomain"
            >
              <option value="">전체</option>
              {MUTATION_SOURCE_DOMAINS.map((domain) => (
                <option key={domain} value={domain}>
                  {DOMAIN_LABELS[domain]}
                </option>
              ))}
            </select>
          </FilterField>
          <button
            className="h-10 rounded-md bg-[#159447] px-4 text-sm font-bold text-white"
            type="submit"
          >
            조회
          </button>
          <button
            className="h-10 rounded-md border border-[#cfd8cc] px-4 text-sm font-semibold text-[#526158]"
            type="button"
            onClick={() => router.push(pathname as Route)}
          >
            초기화
          </button>
        </form>
      </section>

      <div className="flex flex-wrap items-center justify-between gap-2 px-1 text-sm text-[#5b685f]">
        <span>
          총{" "}
          <strong className="text-[#1d4f2c]">{page?.totalElements ?? 0}</strong>
          개 Mutation
        </span>
        <span>최신 ID 순 · 페이지당 {filters.size}개</span>
      </div>

      {query.isError ? (
        <MessagePanel tone="error">
          Mutation 원장을 불러오지 못했습니다. {query.error.message}
        </MessagePanel>
      ) : query.isPending ? (
        <MessagePanel>Mutation 원장을 불러오는 중입니다.</MessagePanel>
      ) : page?.content.length === 0 ? (
        <MessagePanel>조건에 맞는 Mutation이 없습니다.</MessagePanel>
      ) : (
        <div className="space-y-4">
          {page?.content.map((mutation, index) => (
            <MutationCard
              key={mutation.id}
              mutation={mutation}
              open={index === 0}
            />
          ))}
        </div>
      )}

      <section className="rounded-md border border-[#dfe5dc] bg-white p-3">
        <PaginationControls
          pageCount={page?.totalPages ?? 0}
          pageIndex={filters.page}
          pageSize={filters.size}
          pageSizeOptions={[10, 20, 50]}
          onPageChange={(pageIndex) => navigate({ page: pageIndex })}
          onPageSizeChange={(size) => navigate({ page: 0, size })}
        />
      </section>
    </div>
  );
}

function MutationCard({
  mutation,
  open,
}: {
  mutation: OrchidGroupMutation;
  open: boolean;
}) {
  return (
    <details
      className="group overflow-hidden rounded-md border border-[#d8e2d8] bg-white shadow-sm"
      open={open}
    >
      <summary className="cursor-pointer list-none px-4 py-3 marker:hidden">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex min-w-0 items-center gap-3">
            <span
              className={`h-10 w-1.5 shrink-0 rounded-full ${typeColor(mutation.mutationType)}`}
            />
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <strong className="text-base text-[#17251b]">
                  #{mutation.id} {TYPE_LABELS[mutation.mutationType]}
                </strong>
                <Badge>{DOMAIN_LABELS[mutation.sourceDomain]}</Badge>
                <Badge>{mutation.entries.length}개 Entry</Badge>
                {mutation.relations.length ? (
                  <Badge>{mutation.relations.length}개 관계</Badge>
                ) : null}
              </div>
              <p className="mt-1 truncate text-xs text-[#657269]">
                {mutation.sourceType} #{mutation.sourceReferenceId} ·{" "}
                {mutation.sourceOperationKey}
              </p>
            </div>
          </div>
          <div className="text-right text-xs text-[#657269]">
            <p className="font-semibold text-[#3e5545]">
              {mutation.effectiveBusinessDate}
            </p>
            <p>{formatDateTime(mutation.occurredAt)}</p>
          </div>
        </div>
      </summary>

      <div className="border-t border-[#e5ebe3] bg-[#f8faf7] p-4">
        <div className="grid gap-3 xl:grid-cols-[minmax(0,1fr)_20rem]">
          <div className="space-y-3">
            {mutation.entries.map((entry) => (
              <MutationEntryFlow key={entry.id} entry={entry} />
            ))}
          </div>
          <aside className="space-y-3">
            <MetadataCard mutation={mutation} />
            <RelationCard
              mutationId={mutation.id}
              relations={mutation.relations}
            />
          </aside>
        </div>
      </div>
    </details>
  );
}

function MutationEntryFlow({ entry }: { entry: MutationEntry }) {
  const changes = changedFields(entry.beforeState, entry.afterState);
  return (
    <section className="rounded-md border border-[#dbe4d9] bg-white p-3">
      <header className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <Database className="h-4 w-4 text-[#2b7b43]" aria-hidden="true" />
          <h3 className="font-bold text-[#22372a]">
            난 묶음 #{entry.orchidGroupId}
          </h3>
          <Badge>{entry.role}</Badge>
          <Badge>{entry.entryKind}</Badge>
        </div>
        <span className="text-xs font-semibold text-[#5f6d63]">
          revision {entry.stateRevisionBefore ?? "-"} →{" "}
          {entry.stateRevisionAfter}
        </span>
      </header>

      <div className="grid items-stretch gap-2 lg:grid-cols-[minmax(0,1fr)_2rem_minmax(0,1fr)]">
        <StateCard title="Before" state={entry.beforeState} />
        <div className="flex items-center justify-center text-[#6a796e]">
          <ArrowRight
            className="h-5 w-5 rotate-90 lg:rotate-0"
            aria-hidden="true"
          />
        </div>
        <StateCard title="After" state={entry.afterState} />
      </div>

      {changes.length ? (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {changes.map((change) => (
            <span
              key={change.label}
              className="rounded-full bg-[#fff4d6] px-2 py-1 text-xs font-semibold text-[#7b5710]"
            >
              {change.label}: {displayValue(change.before)} →{" "}
              {displayValue(change.after)}
            </span>
          ))}
        </div>
      ) : null}
    </section>
  );
}

function StateCard({
  title,
  state,
}: {
  title: string;
  state?: MutationState | null;
}) {
  if (!state) {
    return (
      <div className="flex min-h-32 items-center justify-center rounded-md border border-dashed border-[#d8dfd6] bg-[#fafbfa] text-sm font-semibold text-[#8a948c]">
        {title}: 상태 없음
      </div>
    );
  }
  return (
    <div className="rounded-md border border-[#e0e6de] bg-[#fbfcfa] p-3">
      <p className="mb-2 text-xs font-bold tracking-wide text-[#6a776d] uppercase">
        {title}
      </p>
      <div className="grid grid-cols-2 gap-x-3 gap-y-2 text-xs">
        <StateValue label="수량" value={`${state.quantity ?? 0}분`} />
        <StateValue label="예약" value={`${state.reservedQuantity ?? 0}분`} />
        <StateValue label="상태" value={state.status} />
        <StateValue label="품종" value={state.varietyName} />
        <StateValue label="구역 ID" value={state.bedZoneId} />
        <StateValue label="배치" value={positionLabel(state)} />
        <StateValue label="화분" value={state.potSizeCode} />
        <StateValue label="년생" value={state.ageYear} />
      </div>
    </div>
  );
}

function StateValue({ label, value }: { label: string; value: unknown }) {
  return (
    <div className="min-w-0">
      <span className="block text-[11px] text-[#7a867d]">{label}</span>
      <strong className="block truncate text-[#2b3d30]">
        {displayValue(value)}
      </strong>
    </div>
  );
}

function MetadataCard({ mutation }: { mutation: OrchidGroupMutation }) {
  return (
    <section className="rounded-md border border-[#dbe4d9] bg-white p-3 text-xs">
      <h3 className="font-bold text-[#263b2d]">원인 정보</h3>
      <dl className="mt-2 space-y-2">
        <Metadata
          label="Source"
          value={`${mutation.sourceDomain} / ${mutation.sourceType}`}
        />
        <Metadata label="Reference" value={mutation.sourceReferenceId} />
        <Metadata label="Operation" value={mutation.sourceOperationKey} />
        <Metadata label="Correlation" value={mutation.correlationId} mono />
        <Metadata
          label="Fingerprint"
          value={mutation.commandFingerprint}
          mono
        />
        <Metadata
          label="기록 시각"
          value={formatDateTime(mutation.recordedAt)}
        />
        <Metadata label="사유" value={mutation.reason ?? "-"} />
      </dl>
    </section>
  );
}

function Metadata({
  label,
  value,
  mono = false,
}: {
  label: string;
  value: string;
  mono?: boolean;
}) {
  return (
    <div>
      <dt className="text-[#7b877e]">{label}</dt>
      <dd
        className={`font-semibold break-all text-[#33463a] ${mono ? "font-mono" : ""}`}
      >
        {value}
      </dd>
    </div>
  );
}

function RelationCard({
  mutationId,
  relations,
}: {
  mutationId: number;
  relations: MutationRelation[];
}) {
  return (
    <section className="rounded-md border border-[#dbe4d9] bg-white p-3 text-xs">
      <h3 className="font-bold text-[#263b2d]">Mutation 관계</h3>
      {relations.length === 0 ? (
        <p className="mt-2 text-[#7b877e]">연결된 보정·보상 관계가 없습니다.</p>
      ) : (
        <div className="mt-2 space-y-2">
          {relations.map((relation) => {
            const outgoing = relation.mutationId === mutationId;
            return (
              <div
                key={relation.id}
                className="rounded bg-[#f3f7f2] px-2 py-2 font-semibold text-[#3b5542]"
              >
                #{relation.mutationId} → #{relation.relatedMutationId}
                <span className="ml-1 text-[#2c7a43]">
                  {relation.relationType}
                </span>
                <span className="ml-1 text-[#758078]">
                  ({outgoing ? "현재가 원인" : "현재가 대상"})
                </span>
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}

function FilterField({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <label className="block min-w-0 space-y-1">
      <span className="text-sm font-semibold text-[#435047]">{label}</span>
      {children}
    </label>
  );
}

function Badge({ children }: { children: React.ReactNode }) {
  return (
    <span className="rounded-full bg-[#edf4ec] px-2 py-0.5 text-[11px] font-bold text-[#45604b]">
      {children}
    </span>
  );
}

function MessagePanel({
  children,
  tone = "default",
}: {
  children: React.ReactNode;
  tone?: "default" | "error";
}) {
  return (
    <div
      className={`rounded-md border px-4 py-12 text-center text-sm font-semibold ${
        tone === "error"
          ? "border-[#efc6c2] bg-[#fff5f3] text-[#a33b32]"
          : "border-[#dfe5dc] bg-white text-[#69756c]"
      }`}
    >
      {children}
    </div>
  );
}

function changedFields(
  before?: MutationState | null,
  after?: MutationState | null,
) {
  const fields: Array<[keyof MutationState, string]> = [
    ["quantity", "수량"],
    ["reservedQuantity", "예약"],
    ["status", "상태"],
    ["bedZoneId", "구역"],
    ["varietyName", "품종"],
    ["startPosition", "시작 위치"],
    ["endPosition", "종료 위치"],
    ["potSizeCode", "화분"],
  ];
  return fields
    .filter(([key]) => before?.[key] !== after?.[key])
    .map(([key, label]) => ({
      label,
      before: before?.[key],
      after: after?.[key],
    }));
}

function positionLabel(state: MutationState) {
  if (state.startPosition == null && state.endPosition == null) return "-";
  return `${state.startPosition ?? "?"}–${state.endPosition ?? "?"}`;
}

function displayValue(value: unknown) {
  if (value == null || value === "") return "-";
  if (typeof value === "boolean") return value ? "예" : "아니오";
  return String(value);
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "short",
    timeStyle: "medium",
  }).format(new Date(value));
}

function typeColor(type: string) {
  if (["CORRECTION", "COMPENSATION", "RESTORE_OUTBOUND"].includes(type)) {
    return "bg-[#f59e0b]";
  }
  if (["DELETE", "DISCARD", "CANCEL_CREATION"].includes(type)) {
    return "bg-[#dc5b4d]";
  }
  if (["CREATE", "BASELINE_IMPORT"].includes(type)) return "bg-[#3182ce]";
  return "bg-[#159447]";
}

const fieldClass =
  "h-10 w-full rounded-md border border-[#cfd8cc] bg-white px-3 text-sm outline-none focus:border-[#159447] focus:ring-1 focus:ring-[#159447]";
