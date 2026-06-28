"use client";

import { X } from "lucide-react";
import { useState } from "react";
import { Field, inputClass } from "./InventoryPrimitives";

interface InventoryDialogProps {
  kind: "variety" | "material";
  open: boolean;
  onClose: () => void;
  onSubmit: (values: { name: string; secondary: string }) => void;
}

export function InventoryDialog({
  kind,
  open,
  onClose,
  onSubmit,
}: InventoryDialogProps) {
  const [name, setName] = useState("");
  const [secondary, setSecondary] = useState("");

  if (!open) return null;

  const isVariety = kind === "variety";

  return (
    <div
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 p-4"
      role="presentation"
      onMouseDown={onClose}
    >
      <section
        className="w-full max-w-md rounded-md bg-white p-5 shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-label={isVariety ? "새 품종 등록" : "새 자재 등록"}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="flex items-center justify-between">
          <h2 className="text-base font-bold">
            {isVariety ? "새 품종 등록" : "새 자재 등록"}
          </h2>
          <button
            className="flex h-8 w-8 items-center justify-center rounded border border-[#d9dfda]"
            type="button"
            onClick={onClose}
            aria-label="닫기"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
        <form
          className="mt-5 space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            if (!name.trim()) return;
            onSubmit({ name: name.trim(), secondary: secondary.trim() });
            onClose();
          }}
        >
          <Field label={isVariety ? "품종명" : "자재명"}>
            <input
              className={inputClass}
              required
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
          </Field>
          <Field label={isVariety ? "속" : "제조사"}>
            <input
              className={inputClass}
              required
              value={secondary}
              onChange={(event) => setSecondary(event.target.value)}
            />
          </Field>
          <div className="flex justify-end gap-2 pt-2">
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
              등록
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
