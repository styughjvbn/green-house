"use client";

import {
  useEffect,
  useMemo,
  useState,
  type FormEvent,
  type ReactNode,
} from "react";
import { Save } from "lucide-react";
import type {
  BusinessPartner,
  PartnerSettlementSettings,
  SettlementUnit,
} from "@/entities/farm/types";
import {
  getPartnerSettlementSettings,
  updatePartnerSettlementSettings,
} from "../../api/salesApi";

type DisabledFeature = {
  enabled: boolean;
  reason: string;
};

export function PartnerSettlementSettingsSection({
  partner,
}: {
  partner: BusinessPartner | null;
}) {
  const [settings, setSettings] = useState<PartnerSettlementSettings | null>(
    null,
  );
  const [aliases, setAliases] = useState("");
  const [ruleJson, setRuleJson] = useState("");
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
        setAliases(result.depositorAliases.join(", "));
        setRuleJson(
          result.ruleJson ? JSON.stringify(result.ruleJson, null, 2) : "",
        );
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

  const disabledFeatures = useMemo(() => {
    const isAuctionHouse = partner?.partnerType === "AUCTION_HOUSE";

    return {
      settlementUnit: {
        enabled: true,
        reason: "",
      } satisfies DisabledFeature,
      monthlySettlement: {
        enabled: !isAuctionHouse,
        reason: "경매장 정산은 현재 경매일 단위만 동작",
      } satisfies DisabledFeature,
      salesSlipSettlement: {
        enabled: !isAuctionHouse,
        reason: "경매장 정산은 현재 경매일 단위만 동작",
      } satisfies DisabledFeature,
      autoMatch: {
        enabled: false,
        reason: "자동 매칭 로직 미구현",
      } satisfies DisabledFeature,
      autoSettle: {
        enabled: false,
        reason: "자동 정산 완료 처리 미구현",
      } satisfies DisabledFeature,
      prepayment: {
        enabled: false,
        reason: "예치금/선입금 처리 미구현",
      } satisfies DisabledFeature,
      autoCreditApply: {
        enabled: false,
        reason: "예치금 자동 차감 미구현",
      } satisfies DisabledFeature,
      ruleJson: {
        enabled: false,
        reason: "경매 결과 자동 수신/파싱 미구현",
      } satisfies DisabledFeature,
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

  function updateSettlementUnit(nextUnit: SettlementUnit) {
    if (!partner) return;
    if (
      partner.partnerType === "AUCTION_HOUSE" &&
      nextUnit !== "AUCTION_DATE"
    ) {
      setMessage("경매장 정산은 현재 경매일 단위만 지원합니다.");
      return;
    }
    update("settlementUnit", nextUnit);
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!partner || !settings) return;

    setSaving(true);
    setMessage(null);
    try {
      let parsedRule: Record<string, unknown> | null = null;
      if (ruleJson.trim()) {
        const parsed = JSON.parse(ruleJson) as unknown;
        if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
          throw new Error("경매 규칙은 JSON 객체 형식이어야 합니다.");
        }
        parsedRule = parsed as Record<string, unknown>;
      }

      const saved = await updatePartnerSettlementSettings(partner.id, {
        settlementUnit: settings.settlementUnit,
        paymentDelayDays: settings.paymentDelayDays,
        paymentDayMode: settings.paymentDayMode,
        autoMatchEnabled: settings.autoMatchEnabled,
        autoSettleEnabled: settings.autoSettleEnabled,
        amountTolerance: settings.amountTolerance,
        depositorAliases: aliases
          .split(/[\n,]/)
          .map((value) => value.trim())
          .filter(Boolean),
        allowPrepayment: settings.allowPrepayment,
        creditAutoApplyEnabled: settings.creditAutoApplyEnabled,
        ruleJson: parsedRule,
        memo: settings.memo,
      });
      setSettings(saved);
      setAliases(saved.depositorAliases.join(", "));
      setMessage("정산 설정 저장 완료");
    } catch (error) {
      setMessage(
        error instanceof Error ? error.message : "설정을 저장하지 못했습니다.",
      );
    } finally {
      setSaving(false);
    }
  }

  if (!partner) {
    return (
      <section className="rounded-md border border-[#d7ddd4] bg-white p-8 text-center text-sm text-[#68756c]">
        정산 설정을 확인할 거래처를 선택하세요.
      </section>
    );
  }

  return (
    <form
      className="rounded-md border border-[#d7ddd4] bg-white p-4 shadow-sm"
      onSubmit={save}
    >
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-xs font-semibold text-[#3d6f91]">정산 설정</p>
          <h3 className="mt-0.5 text-base font-bold">{partner.name}</h3>
        </div>
        <button
          className="inline-flex h-9 items-center gap-1.5 rounded-md bg-[#159447] px-3 text-xs font-semibold text-white disabled:opacity-50"
          type="submit"
          disabled={!settings || loading || saving}
        >
          <Save className="h-3.5 w-3.5" />
          {saving ? "저장 중" : "설정 저장"}
        </button>
      </div>

      {loading || !settings ? (
        <p className="py-8 text-center text-sm text-[#68756c]">불러오는 중</p>
      ) : (
        <div className="mt-4 space-y-4">
          <div className="grid gap-3 sm:grid-cols-3">
            <Field label="정산 단위">
              <select
                className={controlClass}
                value={settings.settlementUnit}
                onChange={(event) =>
                  updateSettlementUnit(event.target.value as SettlementUnit)
                }
              >
                <option
                  value="SALES_SLIP"
                  disabled={!disabledFeatures.salesSlipSettlement.enabled}
                >
                  판매 전표 단위
                </option>
                <option
                  value="MONTHLY_BATCH"
                  disabled={!disabledFeatures.monthlySettlement.enabled}
                >
                  월 정산 단위
                </option>
                <option value="AUCTION_DATE">경매일 단위</option>
              </select>
              {partner.partnerType === "AUCTION_HOUSE" ? (
                <FieldHint tone="muted">
                  {disabledFeatures.monthlySettlement.reason}
                </FieldHint>
              ) : null}
            </Field>
            <Field label="입금 지연일">
              <input
                className={controlClass}
                type="number"
                min={0}
                value={settings.paymentDelayDays}
                onChange={(event) =>
                  update("paymentDelayDays", Number(event.target.value))
                }
              />
            </Field>
            <Field label="일수 계산">
              <select
                className={controlClass}
                value={settings.paymentDayMode}
                onChange={(event) =>
                  update(
                    "paymentDayMode",
                    event.target
                      .value as PartnerSettlementSettings["paymentDayMode"],
                  )
                }
              >
                <option value="CALENDAR_DAY">달력일</option>
                <option value="BUSINESS_DAY">영업일</option>
              </select>
            </Field>
          </div>

          <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_180px]">
            <Field label="입금자명 후보">
              <input
                className={controlClass}
                value={aliases}
                placeholder="쉼표로 구분"
                onChange={(event) => setAliases(event.target.value)}
              />
            </Field>
            <Field label="금액 허용 오차">
              <input
                className={`${controlClass} text-right`}
                type="number"
                min={0}
                value={settings.amountTolerance}
                onChange={(event) =>
                  update("amountTolerance", Number(event.target.value))
                }
              />
            </Field>
          </div>

          <div className="grid gap-2 border-y border-[#edf0ec] py-3 sm:grid-cols-2">
            <Toggle
              label="자동 매칭 사용"
              checked={settings.autoMatchEnabled}
              disabled={!disabledFeatures.autoMatch.enabled}
              reason={disabledFeatures.autoMatch.reason}
              onChange={(checked) => update("autoMatchEnabled", checked)}
            />
            <Toggle
              label="자동 정산 완료"
              checked={settings.autoSettleEnabled}
              disabled={!disabledFeatures.autoSettle.enabled}
              reason={disabledFeatures.autoSettle.reason}
              onChange={(checked) => update("autoSettleEnabled", checked)}
            />
            <Toggle
              label="선입금 허용"
              checked={settings.allowPrepayment}
              disabled={!disabledFeatures.prepayment.enabled}
              reason={disabledFeatures.prepayment.reason}
              onChange={(checked) => update("allowPrepayment", checked)}
            />
            <Toggle
              label="선입금 자동 차감"
              checked={settings.creditAutoApplyEnabled}
              disabled={!disabledFeatures.autoCreditApply.enabled}
              reason={disabledFeatures.autoCreditApply.reason}
              onChange={(checked) => update("creditAutoApplyEnabled", checked)}
            />
          </div>

          {partner.partnerType === "AUCTION_HOUSE" ? (
            <details className="rounded-md border border-[#e1e6df] px-3 py-2">
              <summary className="cursor-pointer text-sm font-semibold">
                경매 결과 수신·파싱 규칙
              </summary>
              <FieldHint tone="muted" className="mt-2">
                {disabledFeatures.ruleJson.reason}
              </FieldHint>
              <textarea
                className="mt-3 min-h-36 w-full rounded-md border border-[#ccd5ca] bg-[#f5f7f4] p-3 font-mono text-xs text-[#748178] disabled:cursor-not-allowed"
                value={ruleJson}
                placeholder={'{"auctionDays":["MON","THU"]}'}
                disabled
                onChange={(event) => setRuleJson(event.target.value)}
              />
            </details>
          ) : null}

          <Field label="메모">
            <input
              className={controlClass}
              value={settings.memo ?? ""}
              onChange={(event) => update("memo", event.target.value || null)}
            />
          </Field>
        </div>
      )}

      {message ? (
        <p className="mt-3 text-xs font-semibold text-[#55645a]">{message}</p>
      ) : null}
    </form>
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

function Toggle({
  label,
  checked,
  onChange,
  disabled = false,
  reason,
}: {
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
  reason?: string;
}) {
  return (
    <div>
      <label
        className={`flex items-center gap-2 text-xs font-semibold ${
          disabled ? "text-[#8a958d]" : "text-[#526057]"
        }`}
      >
        <input
          type="checkbox"
          checked={checked}
          disabled={disabled}
          onChange={(event) => onChange(event.target.checked)}
        />
        {label}
      </label>
      {disabled && reason ? <FieldHint>{reason}</FieldHint> : null}
    </div>
  );
}
