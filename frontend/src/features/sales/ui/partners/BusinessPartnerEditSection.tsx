import type { FormEvent, ReactNode } from "react";
import { useState } from "react";
import { Pencil, Save, X } from "lucide-react";
import type { BusinessPartner } from "@/entities/farm/types";
import {
  DetailActionButton,
  DetailCard,
  DetailEmpty,
  DetailHeader,
} from "@/shared/ui/DetailCard";
import type { BusinessPartnerForm } from "../../model/types";
import { TextField } from "../common/FormFields";

export type BusinessPartnerDetailMode = "read" | "basic" | "settlement";

export function BusinessPartnerEditSection({
  partner,
  form,
  saving,
  mode,
  onChange,
  onModeChange,
  errorMessage,
  children,
  onSubmit,
}: {
  partner: BusinessPartner | null;
  form: BusinessPartnerForm;
  saving: boolean;
  mode: BusinessPartnerDetailMode;
  onChange: <K extends keyof BusinessPartnerForm>(
    field: K,
    value: BusinessPartnerForm[K],
  ) => void;
  onModeChange: (mode: BusinessPartnerDetailMode) => void;
  errorMessage: string | null;
  children?: ReactNode;
  onSubmit: (event: FormEvent<HTMLFormElement>) => boolean | Promise<boolean>;
}) {
  const [saved, setSaved] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    setSaved(false);
    const result = await onSubmit(event);
    setSaved(result);
    if (result) onModeChange("read");
  }

  function change<K extends keyof BusinessPartnerForm>(
    field: K,
    value: BusinessPartnerForm[K],
  ) {
    setSaved(false);
    onChange(field, value);
  }

  if (!partner) {
    return <DetailEmpty>상세 정보를 확인할 거래처를 선택하세요.</DetailEmpty>;
  }

  return (
    <DetailCard>
      <DetailHeader
        eyebrow="거래처 상세"
        title={modeTitle(mode, partner.name)}
        actions={
          mode === "read" ? (
            <>
              <DetailActionButton
                icon={Pencil}
                onClick={() => onModeChange("basic")}
              >
                기본 정보 수정
              </DetailActionButton>
              <DetailActionButton
                icon={Pencil}
                onClick={() => onModeChange("settlement")}
              >
                정산 수정
              </DetailActionButton>
            </>
          ) : (
            <DetailActionButton icon={X} onClick={() => onModeChange("read")}>
              취소
            </DetailActionButton>
          )
        }
      />

      <div className="space-y-5 p-4">
        {errorMessage ? (
          <p className="rounded-md bg-red-50 px-3 py-2 text-xs font-semibold text-red-700">
            {errorMessage}
          </p>
        ) : saved ? (
          <p className="rounded-md bg-[#eef7ec] px-3 py-2 text-xs font-semibold text-[#246b38]">
            거래처 정보 저장 완료
          </p>
        ) : null}

        {mode === "basic" ? (
          <form className="space-y-3" onSubmit={submit}>
            <label className="block text-sm font-semibold text-[#415047]">
              거래처 유형
              <select
                className={controlClass}
                value={form.partnerType}
                onChange={(event) =>
                  change(
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
              onChange={(value) => change("name", value)}
            />
            <div className="grid grid-cols-2 gap-3">
              <TextField
                label="대표자"
                value={form.ownerName}
                onChange={(value) => change("ownerName", value)}
              />
              <TextField
                label="전화번호"
                value={form.phone}
                onChange={(value) => change("phone", value)}
              />
            </div>
            <TextField
              label="주소"
              value={form.address}
              onChange={(value) => change("address", value)}
            />
            <TextField
              label="메모"
              value={form.memo}
              onChange={(value) => change("memo", value)}
            />
            <div className="flex justify-end">
              <button
                className="inline-flex h-9 items-center gap-1.5 rounded-md bg-[#159447] px-3 text-xs font-semibold text-white disabled:opacity-50"
                type="submit"
                disabled={saving}
              >
                <Save className="h-3.5 w-3.5" />
                {saving ? "저장 중" : "정보 저장"}
              </button>
            </div>
          </form>
        ) : mode === "settlement" ? (
          children
        ) : (
          <section>
            <SectionTitle>기본 정보</SectionTitle>
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              <ReadField label="거래처명" value={partner.name} />
              <ReadField
                label="거래처 유형"
                value={partnerTypeLabel(partner.partnerType)}
              />
              <ReadField label="대표자" value={partner.ownerName} />
              <ReadField label="전화번호" value={partner.phone} />
              <ReadField
                label="주소"
                value={partner.address}
                className="sm:col-span-2"
              />
              <ReadField
                label="메모"
                value={partner.memo}
                className="sm:col-span-2"
              />
            </div>
            {children ? (
              <div className="mt-5 border-t border-[#edf0ec] pt-5">
                {children}
              </div>
            ) : null}
          </section>
        )}
      </div>
    </DetailCard>
  );
}

const controlClass =
  "mt-1 h-10 w-full rounded-md border border-[#cfd7cd] bg-white px-3 text-sm";

function SectionTitle({ children }: { children: ReactNode }) {
  return <h3 className="text-sm font-bold text-[#26342b]">{children}</h3>;
}

function ReadField({
  className = "",
  label,
  value,
}: {
  className?: string;
  label: string;
  value: ReactNode;
}) {
  return (
    <div className={className}>
      <p className="text-[11px] font-semibold text-[#68756c]">{label}</p>
      <p className="mt-1 min-h-[20px] text-sm font-semibold text-[#17251b]">
        {displayValue(value)}
      </p>
    </div>
  );
}

function displayValue(value: ReactNode) {
  return value == null || value === "" ? "-" : value;
}

function partnerTypeLabel(type: BusinessPartner["partnerType"]) {
  if (type === "AUCTION_HOUSE") return "경매장";
  if (type === "RETAIL") return "소매";
  return "도매";
}

function modeTitle(mode: BusinessPartnerDetailMode, partnerName: string) {
  if (mode === "basic") return "기본 정보 수정";
  if (mode === "settlement") return "정산 수정";
  return partnerName;
}
