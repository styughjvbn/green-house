"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { TabError, TabSplit, TabStack } from "@/shared/ui/TabLayout";
import { readInboundRouteState } from "../lib/inventoryRouteState";
import { useInboundRecords } from "../model/useInboundRecords";
import type { InboundRecordUpdatePayload } from "../model/types";
import { InboundDetailCard } from "./inbound/InboundDetailCard";
import {
  CancelDialog,
  InboundCreateDialog,
  InboundPottingDialog,
} from "./inbound/InboundDialogs";
import { InboundFilters } from "./inbound/InboundFilters";
import { InboundList } from "./inbound/InboundList";

export function InventoryInboundPage() {
  const routeState = readInboundRouteState(useSearchParams());
  const inbound = useInboundRecords({ routeState });
  const [dialog, setDialog] = useState<"create" | "potting" | "cancel" | null>(
    null,
  );
  const [editing, setEditing] = useState(false);
  const [editForm, setEditForm] = useState<InboundRecordUpdatePayload>({
    inboundDate: "",
  });
  const selected = inbound.selected;

  function openPlacementDialog(nextDialog: "create" | "potting") {
    setDialog(nextDialog);
    inbound.enableHouses();
  }

  const placementDialogOpen = dialog === "create" || dialog === "potting";
  const placementDialogWaiting =
    placementDialogOpen &&
    (!inbound.houses.length || inbound.housesLoading || !!inbound.housesError);

  return (
    <main className="flex h-full min-h-0 min-w-0 flex-col">
      <TabStack>
        <InboundFilters
          filters={inbound.filters}
          onChange={inbound.updateFilter}
          onReset={inbound.reset}
          onSearch={inbound.search}
        />
        <TabError message={inbound.error} />

        <TabSplit
          columns="lg:grid-cols-[minmax(0,1.15fr)_minmax(24rem,0.95fr)]"
          gap="gap-3"
        >
          <InboundList
            loading={inbound.loading}
            pageData={inbound.pageData}
            selectedId={selected?.id}
            onOpenCreate={() => openPlacementDialog("create")}
            onPageChange={inbound.changePage}
            onPageSizeChange={inbound.changePageSize}
            onSelect={(id) => {
              setEditing(false);
              inbound.select(id);
            }}
          />

          {selected ? (
            <InboundDetailCard
              editForm={editForm}
              editing={editing}
              record={selected}
              onDelete={() => {
                if (!window.confirm("취소된 입고 기록을 삭제할까요?")) {
                  return;
                }
                void inbound.remove(selected.id);
              }}
              onEditFormChange={(updater) =>
                setEditForm((current) => updater(current))
              }
              onOpenCancel={() => setDialog("cancel")}
              onOpenPotting={() => openPlacementDialog("potting")}
              onSubmitUpdate={async () => {
                await inbound.update(selected.id, editForm);
                setEditing(false);
              }}
              onToggleEditing={(nextEditing, form) => {
                setEditing(nextEditing);
                if (nextEditing) {
                  setEditForm(form);
                }
              }}
            />
          ) : null}
        </TabSplit>

        <InboundCreateDialog
          houses={inbound.houses}
          open={dialog === "create" && !placementDialogWaiting}
          varieties={inbound.varietyOptions}
          onClose={() => setDialog(null)}
          onSubmit={async (payload) => {
            await inbound.create(payload);
            inbound.changePage(0);
            setDialog(null);
          }}
        />

        <InboundPottingDialog
          houses={inbound.houses}
          open={dialog === "potting" && !!selected && !placementDialogWaiting}
          record={selected}
          onClose={() => setDialog(null)}
          onSubmit={async (payload) => {
            if (!selected) return;
            await inbound.pot(selected.id, payload);
            setDialog(null);
          }}
        />

        <PlacementDataDialog
          error={inbound.housesError}
          loading={placementDialogOpen && inbound.housesLoading}
          open={placementDialogWaiting}
          onClose={() => setDialog(null)}
          onRetry={() => void inbound.retryHouses()}
        />

        <CancelDialog
          open={dialog === "cancel" && !!selected}
          title="입고 기록 취소"
          onClose={() => setDialog(null)}
          onSubmit={async (memo) => {
            if (!selected) return;
            await inbound.cancel(selected.id, memo);
            setDialog(null);
          }}
        />
      </TabStack>
    </main>
  );
}

function PlacementDataDialog({
  error,
  loading,
  open,
  onClose,
  onRetry,
}: {
  error: string | null;
  loading: boolean;
  open: boolean;
  onClose: () => void;
  onRetry: () => void;
}) {
  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-[1200] flex items-center justify-center bg-black/30 p-4"
      role="presentation"
      onMouseDown={onClose}
    >
      <section
        className="w-full max-w-sm rounded-md bg-white p-5 shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-label="농장 배치 정보"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <h2 className="text-base font-bold text-[#17251b]">농장 배치 정보</h2>
        <p className="mt-2 text-sm text-[#526057]">
          {error
            ? error
            : loading
              ? "농장 배치 정보를 불러오는 중입니다."
              : "농장 배치 정보를 준비하고 있습니다."}
        </p>
        <div className="mt-4 flex justify-end gap-2">
          <button
            className="rounded-md border border-[#d4dbd5] px-4 py-2 text-sm font-semibold"
            type="button"
            onClick={onClose}
          >
            닫기
          </button>
          {error ? (
            <button
              className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white"
              type="button"
              onClick={onRetry}
            >
              다시 시도
            </button>
          ) : null}
        </div>
      </section>
    </div>
  );
}
