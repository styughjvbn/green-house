import type { FormEvent } from "react";
import type { CustomerForm } from "../../model/types";
import { TextField } from "./FormFields";

export function CustomerCreateForm({
  form,
  saving,
  onChange,
  onSubmit,
}: {
  form: CustomerForm;
  saving: boolean;
  onChange: <K extends keyof CustomerForm>(
    field: K,
    value: CustomerForm[K],
  ) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <form
      className="rounded-md border border-[#d7ddd4] bg-white p-4 shadow-sm"
      onSubmit={onSubmit}
    >
      <p className="text-sm font-semibold text-[#3d6f91]">거래처</p>
      <h2 className="mt-1 text-xl font-semibold">거래처 등록</h2>
      <div className="mt-4 space-y-3">
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
      </div>
    </form>
  );
}
