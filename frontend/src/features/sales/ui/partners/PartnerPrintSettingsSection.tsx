"use client";

import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { Save } from "lucide-react";
import type {
  BusinessPartner,
  PartnerSettlementSettings,
} from "@/entities/farm/types";
import {
  getPartnerSettlementSettings,
  updatePartnerSettlementSettings,
} from "../../api/salesApi";

export function PartnerPrintSettingsSection({
  partner,
  embedded = false,
  mode = "read",
  onSaved,
}: {
  partner: BusinessPartner | null;
  embedded?: boolean;
  mode?: "read" | "edit";
  onSaved?: () => void;
}) {
  const [settings, setSettings] = useState<PartnerSettlementSettings | null>(
    null,
  );
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!partner) return;

    let active = true;
    void (async () => {
      setLoading(true);
      setMessage(null);
      try {
        const result = await getPartnerSettlementSettings(partner.id);
        if (!active) return;
        setSettings(result);
      } catch (error) {
        if (!active) return;
        setMessage(
          error instanceof Error
            ? error.message
            : "설정을 불러오지 못했습니다.",
        );
      } finally {
        if (active) setLoading(false);
      }
    })();

    return () => {
      active = false;
    };
  }, [partner]);

  function update<K extends keyof PartnerSettlementSettings>(
    key: K,
    value: PartnerSettlementSettings[K],
  ) {
    setSettings((current) =>
      current ? { ...current, [key]: value } : current,
    );
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!partner || !settings) return;

    setSaving(true);
    setMessage(null);
    try {
      const saved = await updatePartnerSettlementSettings(partner.id, {
        settlementUnit: settings.settlementUnit,
        paymentDelayDays: settings.paymentDelayDays,
        paymentDayMode: settings.paymentDayMode,
        autoMatchEnabled: settings.autoMatchEnabled,
        autoSettleEnabled: settings.autoSettleEnabled,
        amountTolerance: settings.amountTolerance,
        depositorAliases: settings.depositorAliases,
        allowPrepayment: settings.allowPrepayment,
        creditAutoApplyEnabled: settings.creditAutoApplyEnabled,
        ruleJson: settings.ruleJson,
        memo: settings.memo,
        printTemplate: settings.printTemplate,
        shipperCode: settings.shipperCode,
        shipperName: settings.shipperName,
      });
      setSettings(saved);
      setMessage("인쇄 설정 저장 완료");
      onSaved?.();
    } catch (error) {
      setMessage(
        error instanceof Error ? error.message : "설정을 저장하지 못했습니다.",
      );
    } finally {
      setSaving(false);
    }
  }

  if (!partner) {
    if (embedded) return null;

    return (
      <section className="rounded-md border border-[#d7ddd4] bg-white p-8 text-center text-sm text-[#68756c]">
        인쇄 설정을 확인할 거래처를 선택하세요.
      </section>
    );
  }

  const contentClass = embedded ? "" : "p-4";
  const rootClass = embedded
    ? "space-y-0"
    : "rounded-md border border-[#d7ddd4] bg-white shadow-sm";

  return (
    <section className={rootClass}>
      <div
        className={`flex items-center justify-between gap-3 ${contentClass}`}
      >
        <div>
          {embedded ? (
            <h3 className="text-sm font-bold text-[#26342b]">
              {mode === "edit" ? "인쇄 수정" : "인쇄 설정"}
            </h3>
          ) : (
            <>
              <p className="text-xs font-semibold text-[#3d6f91]">인쇄 설정</p>
              <h3 className="mt-0.5 text-base font-bold">{partner.name}</h3>
            </>
          )}
        </div>
      </div>

      {loading || !settings ? (
        <p className="py-8 text-center text-sm text-[#68756c]">불러오는 중</p>
      ) : mode === "edit" ? (
        <form
          className={`${embedded ? "mt-4" : "px-4 pb-4"} space-y-4`}
          onSubmit={save}
        >
          {/* TODO: 수납 비활성화 — 전표 등록 마무리 후 연결 예정. 정산 값은 기존 값 그대로 저장한다. */}
          {partner.partnerType === "AUCTION_HOUSE" ? (
            <>
              <section className="rounded-md border border-[#d9e2d7] bg-[#f7faf6] p-3">
                <h4 className="text-sm font-bold text-[#26342b]">
                  출하 전표 인쇄
                </h4>
                <div className="mt-3 grid gap-3 sm:grid-cols-3">
                  <Field label="기본 양식">
                    <select
                      className={controlClass}
                      value={settings.printTemplate}
                      onChange={(event) =>
                        update(
                          "printTemplate",
                          event.target
                            .value as PartnerSettlementSettings["printTemplate"],
                        )
                      }
                    >
                      <option value="STANDARD_A5">기본 A5</option>
                      <option value="YANGJAE_AUCTION_SHIPMENT">
                        양재 경매장 출하 양식
                      </option>
                    </select>
                  </Field>
                  <Field label="출하자 코드">
                    <input
                      className={controlClass}
                      maxLength={50}
                      value={settings.shipperCode ?? ""}
                      onChange={(event) =>
                        update("shipperCode", event.target.value || null)
                      }
                    />
                  </Field>
                  <Field label="출하자 성명">
                    <input
                      className={controlClass}
                      maxLength={100}
                      value={settings.shipperName ?? ""}
                      onChange={(event) =>
                        update("shipperName", event.target.value || null)
                      }
                    />
                  </Field>
                </div>
                <FieldHint tone="muted" className="mt-2">
                  저장한 출하자 정보는 인쇄 시 기본값이며 인쇄 창에서 수정할 수
                  있습니다.
                </FieldHint>
              </section>
            </>
          ) : (
            <p className="text-sm text-[#68756c]">
              일반 판매 전표는 기본 A5 양식을 사용합니다.
            </p>
          )}

          <div className="flex justify-end">
            <button
              className="inline-flex h-9 items-center gap-1.5 rounded-md bg-[#159447] px-3 text-xs font-semibold text-white disabled:opacity-50"
              type="submit"
              disabled={saving}
            >
              <Save className="h-3.5 w-3.5" />
              {saving ? "저장 중" : "설정 저장"}
            </button>
          </div>
        </form>
      ) : (
        <PrintReadView
          settings={settings}
          auctionHouse={partner.partnerType === "AUCTION_HOUSE"}
        />
      )}

      {message ? (
        <p
          className={`${embedded ? "mt-3" : "mx-4 mb-4"} text-xs font-semibold text-[#55645a]`}
        >
          {message}
        </p>
      ) : null}
    </section>
  );
}

const controlClass =
  "mt-1 h-9 w-full rounded-md border border-[#ccd5ca] bg-white px-3 text-sm disabled:cursor-not-allowed disabled:bg-[#f5f7f4] disabled:text-[#748178]";

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block text-xs font-semibold text-[#526057]">
      {label}
      {children}
    </label>
  );
}

function FieldHint({
  children,
  tone = "muted",
  className = "",
}: {
  children: ReactNode;
  tone?: "muted" | "warning";
  className?: string;
}) {
  return (
    <p
      className={`mt-1 text-[11px] ${
        tone === "warning" ? "text-[#c26a1a]" : "text-[#7a867d]"
      } ${className}`}
    >
      {children}
    </p>
  );
}

function PrintReadView({
  settings,
  auctionHouse,
}: {
  settings: PartnerSettlementSettings;
  auctionHouse: boolean;
}) {
  return (
    <div className="mt-3 grid gap-3 sm:grid-cols-2">
      <ReadField
        label="전표 기본 양식"
        value={
          auctionHouse ? printTemplateLabel(settings.printTemplate) : "기본 A5"
        }
      />
      {auctionHouse ? (
        <ReadField
          label="출하자 기본값"
          value={[settings.shipperCode, settings.shipperName]
            .filter(Boolean)
            .join(" · ")}
        />
      ) : null}
    </div>
  );
}

function printTemplateLabel(
  template: PartnerSettlementSettings["printTemplate"],
) {
  if (template === "YANGJAE_AUCTION_SHIPMENT") {
    return "양재 경매장 출하 양식";
  }
  return "기본 A5";
}

function ReadField({
  className = "",
  hint,
  label,
  value,
}: {
  className?: string;
  hint?: string;
  label: string;
  value: ReactNode;
}) {
  return (
    <div className={className}>
      <p className="text-[11px] font-semibold text-[#68756c]">{label}</p>
      <p className="mt-1 min-h-[20px] text-sm font-semibold text-[#17251b]">
        {displayValue(value)}
      </p>
      {hint ? <p className="mt-1 text-[11px] text-[#7a867d]">{hint}</p> : null}
    </div>
  );
}

function displayValue(value: ReactNode) {
  return value == null || value === "" ? "-" : value;
}
