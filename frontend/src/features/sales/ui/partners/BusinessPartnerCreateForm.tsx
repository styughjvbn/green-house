import type { FormEvent } from "react";
import { X } from "lucide-react";
import type { BusinessPartnerForm } from "../../model/types";
import { TextField } from "../common/FormFields";

export function BusinessPartnerCreateForm({
  form,
  saving,
  onChange,
  onClose,
  onSubmit,
}: {
  form: BusinessPartnerForm;
  saving: boolean;
  onChange: <K extends keyof BusinessPartnerForm>(
    field: K,
    value: BusinessPartnerForm[K],
  ) => void;
  onClose: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <div
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 p-4"
      role="presentation"
      onMouseDown={onClose}
    >
      <section
        className="flex max-h-[calc(100dvh-2rem)] w-full max-w-xl flex-col rounded-md bg-white shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-label="거래처 등록"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="flex shrink-0 items-start justify-between gap-3 border-b border-[#edf0ec] p-5">
          <div>
            <p className="text-sm font-semibold text-[#3d6f91]">거래처</p>
            <h2 className="mt-1 text-xl font-semibold">거래처 등록</h2>
          </div>
          <button
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded border border-[#d9dfda] text-[#435047] hover:bg-[#f4f7f3]"
            type="button"
            onClick={onClose}
            aria-label="닫기"
          >
            <X className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
          </button>
        </div>

        <form
          className="min-h-0 space-y-3 overflow-y-auto p-5"
          onSubmit={onSubmit}
        >
          <label className="block text-sm font-semibold text-[#415047]">
            거래처 유형
            <select
              className="mt-1 h-10 w-full rounded-md border border-[#cfd7cd] bg-white px-3 text-sm"
              value={form.partnerType}
              onChange={(event) =>
                onChange(
                  "partnerType",
                  event.target.value as BusinessPartnerForm["partnerType"],
                )
              }
            >
              <option value="WHOLESALE">도매</option>
              <option value="RETAIL">소매</option>
              <option value="AUCTION_HOUSE">경매장</option>
            </select>
          </label>
          <TextField
            label="거래처명"
            required
            value={form.name}
            onChange={(value) => onChange("name", value)}
          />
          <div className="grid grid-cols-2 gap-3">
            <TextField
              label="대표자"
              value={form.ownerName}
              onChange={(value) => onChange("ownerName", value)}
            />
            <TextField
              label="전화번호"
              value={form.phone}
              onChange={(value) => onChange("phone", value)}
            />
          </div>
          <TextField
            label="주소"
            value={form.address}
            onChange={(value) => onChange("address", value)}
          />
          <TextField
            label="메모"
            value={form.memo}
            onChange={(value) => onChange("memo", value)}
          />
          <button
            className="w-full rounded-md bg-[#159447] px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-60"
            disabled={saving}
            type="submit"
          >
            {saving ? "저장 중" : "거래처 저장"}
          </button>
        </form>
      </section>
    </div>
  );
}
