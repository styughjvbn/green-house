"use client";

import type { House } from "@/entities/farm/types";
import type { Route } from "next";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { fetchApi } from "@/shared/api/client";
import { TabSplit, TabStack } from "@/shared/ui/TabLayout";
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
import { createInboundEditForm, setQueryParam } from "../../lib/inboundUi";
import {
  CancelDialog,
  InboundCreateDialog,
  InboundPottingDialog,
} from "./inbound/InboundDialogs";
import { InboundDetailCard } from "./inbound/InboundDetailCard";
import { InboundFilterCard } from "./inbound/InboundFilterCard";
import { InboundListCard } from "./inbound/InboundListCard";

export function InboundSection({
  pageData,
  varieties,
  selectedId,
  onSelect,
  onUpdate,
  onCreate,
  onPotting,
  onCancel,
  onDelete,
}: {
  pageData: InventoryPageResult<InboundRecord>;
  varieties: Variety[];
  selectedId: number;
  onSelect: (id: number) => void;
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
  onDelete: (inboundRecordId: number) => Promise<void>;
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
  const [houses, setHouses] = useState<House[] | null>(null);
  const [housesLoading, setHousesLoading] = useState(false);
  const [housesError, setHousesError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [editForm, setEditForm] = useState<InboundRecordUpdatePayload>({
    inboundDate: "",
  });

  const selected =
    pageData.content.find((item) => item.id === selectedId) ??
    pageData.content[0];

  const updateParams = (updater: (params: URLSearchParams) => void) => {
    const params = new URLSearchParams(searchParams.toString());
    updater(params);
    router.replace(`${pathname}?${params.toString()}` as Route);
  };

  const loadHouses = async () => {
    if (houses || housesLoading) {
      return;
    }
    setHousesLoading(true);
    setHousesError(null);
    try {
      setHouses(await fetchApi<House[]>("/houses"));
    } catch (error) {
      setHousesError(
        error instanceof Error
          ? error.message
          : "농장 배치 정보를 불러오지 못했습니다.",
      );
    } finally {
      setHousesLoading(false);
    }
  };

  const openPlacementDialog = (nextDialog: "create" | "potting") => {
    setDialog(nextDialog);
    void loadHouses();
  };

  const placementDialogOpen = dialog === "create" || dialog === "potting";
  const placementDialogWaiting =
    placementDialogOpen && (!houses || housesLoading || !!housesError);

  return (
    <TabStack>
      <InboundFilterCard
        inboundType={inboundType}
        keyword={keyword}
        status={status}
        onReset={() => {
          updateParams((params) => {
            ["inboundKeyword", "inboundStatus", "inboundType"].forEach((key) =>
              params.delete(key),
            );
            params.set("page", "0");
          });
        }}
        onSubmit={(formData) => {
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
            params.set("page", "0");
          });
        }}
      />

      <TabSplit
        columns="lg:grid-cols-[minmax(0,1.15fr)_minmax(24rem,0.95fr)]"
        gap="gap-3"
      >
        <InboundListCard
          pageData={pageData}
          selectedId={selected?.id}
          onOpenCreate={() => openPlacementDialog("create")}
          onPageChange={(pageIndex) =>
            updateParams((params) => {
              params.set("page", String(pageIndex));
            })
          }
          onPageSizeChange={(pageSize) =>
            updateParams((params) => {
              params.set("size", String(pageSize));
              params.set("page", "0");
            })
          }
          onSelect={(id) => {
            setEditing(false);
            onSelect(id);
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
              void onDelete(selected.id).catch((error: Error) => {
                window.alert(error.message);
              });
            }}
            onEditFormChange={(updater) =>
              setEditForm((current) => updater(current))
            }
            onOpenCancel={() => setDialog("cancel")}
            onOpenPotting={() => openPlacementDialog("potting")}
            onSubmitUpdate={async () => {
              await onUpdate(selected.id, editForm);
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
        houses={houses ?? []}
        open={dialog === "create" && !placementDialogWaiting}
        varieties={varieties}
        onClose={() => setDialog(null)}
        onSubmit={async (payload) => {
          await onCreate(payload);
          setDialog(null);
        }}
      />

      <InboundPottingDialog
        houses={houses ?? []}
        open={dialog === "potting" && !!selected && !placementDialogWaiting}
        record={selected ?? null}
        onClose={() => setDialog(null)}
        onSubmit={async (payload) => {
          if (!selected) return;
          await onPotting(selected.id, payload);
          setDialog(null);
        }}
      />

      <PlacementDataDialog
        error={housesError}
        loading={placementDialogOpen && housesLoading}
        open={placementDialogWaiting}
        onClose={() => setDialog(null)}
        onRetry={() => {
          setHouses(null);
          void loadHouses();
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
    </TabStack>
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
