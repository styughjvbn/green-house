"use client";

import type { House } from "@/entities/farm/types";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
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
  houses,
  varieties,
  selectedId,
  onSelect,
  onOpenCreate,
  onUpdate,
  onCreate,
  onPotting,
  onCancel,
  onDelete,
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
    router.replace(`${pathname}?${params.toString()}`);
  };

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
            params.set("inboundPage", "0");
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
            params.set("inboundPage", "0");
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
          onOpenCreate={() => {
            onOpenCreate();
            setDialog("create");
          }}
          onPageChange={(pageIndex) =>
            updateParams((params) => {
              params.set("inboundPage", String(pageIndex));
            })
          }
          onPageSizeChange={(pageSize) =>
            updateParams((params) => {
              params.set("inboundSize", String(pageSize));
              params.set("inboundPage", "0");
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
            onOpenPotting={() => setDialog("potting")}
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
    </TabStack>
  );
}
