"use client";

import { X } from "lucide-react";
import type { ReactNode } from "react";
import { useState } from "react";
import type { MaterialPayload, VarietyPayload } from "../../model/types";
import { Field, inputClass } from "./InventoryPrimitives";

type InventoryDialogProps =
  | {
      kind: "variety";
      open: boolean;
      onClose: () => void;
      onSubmit: (values: VarietyPayload) => void;
    }
  | {
      kind: "material";
      open: boolean;
      onClose: () => void;
      onSubmit: (values: MaterialPayload) => void;
    };

export function InventoryDialog(props: InventoryDialogProps) {
  if (!props.open) return null;

  if (props.kind === "variety") {
    return (
      <DialogShell title="새 품종 등록" onClose={props.onClose}>
        <VarietyDialogForm onClose={props.onClose} onSubmit={props.onSubmit} />
      </DialogShell>
    );
  }

  return (
    <DialogShell title="새 자재 등록" onClose={props.onClose}>
      <MaterialDialogForm onClose={props.onClose} onSubmit={props.onSubmit} />
    </DialogShell>
  );
}

function VarietyDialogForm({
  onClose,
  onSubmit,
}: {
  onClose: () => void;
  onSubmit: (values: VarietyPayload) => void;
}) {
  const [form, setForm] = useState<VarietyPayload>({
    genus: "",
    name: "",
    alias: "",
    defaultPotSize: "",
    saleEnabled: true,
    description: "",
    memo: "",
  });

  return (
    <form
      className="mt-5 grid gap-3 md:grid-cols-2"
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit(form);
        onClose();
      }}
    >
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
      <Field label="기본 화분">
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
      <label className="flex items-center gap-2 text-sm font-semibold text-[#425047] md:col-span-2">
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
      <label className="space-y-1 text-xs font-semibold text-[#425047] md:col-span-2">
        <span>설명</span>
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
      <label className="space-y-1 text-xs font-semibold text-[#425047] md:col-span-2">
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
      <DialogActions onCancel={onClose} submitLabel="등록" />
    </form>
  );
}

function MaterialDialogForm({
  onClose,
  onSubmit,
}: {
  onClose: () => void;
  onSubmit: (values: MaterialPayload) => void;
}) {
  const [form, setForm] = useState<MaterialPayload>({
    category: "자재",
    name: "",
    manufacturer: "",
    specification: "",
    stockQuantity: "",
    storageLocation: "",
    usage: "",
  });

  return (
    <form
      className="mt-5 grid gap-3 md:grid-cols-2"
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit(form);
        onClose();
      }}
    >
      <Field label="자재 종류">
        <select
          className={inputClass}
          value={form.category}
          onChange={(event) =>
            setForm((current) => ({
              ...current,
              category: event.target.value as MaterialPayload["category"],
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
      <label className="space-y-1 text-xs font-semibold text-[#425047] md:col-span-2">
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
      <DialogActions onCancel={onClose} submitLabel="등록" />
    </form>
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
        className="w-full max-w-xl rounded-md bg-white p-5 shadow-xl"
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

function DialogActions({
  onCancel,
  submitLabel,
}: {
  onCancel: () => void;
  submitLabel: string;
}) {
  return (
    <div className="flex justify-end gap-2 pt-2 md:col-span-2">
      <button
        className="rounded-md border border-[#d4dbd5] px-4 py-2 text-sm font-semibold"
        type="button"
        onClick={onCancel}
      >
        취소
      </button>
      <button
        className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white"
        type="submit"
      >
        {submitLabel}
      </button>
    </div>
  );
}
