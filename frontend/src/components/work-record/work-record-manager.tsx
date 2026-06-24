"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { API_BASE_URL } from "@/lib/api";
import type { BedZone, HouseStatusSummary, OrchidGroup, PhysicalBed, WorkRecord, WorkRecordTargetType } from "@/types/farm";

type WorkRecordManagerProps = {
  initialRecords: WorkRecord[];
  houses: HouseStatusSummary[];
  workTypes: string[];
};

type WorkRecordFormState = {
  workType: string;
  workDate: string;
  targetType: WorkRecordTargetType;
  houseId: string;
  physicalBedId: string;
  bedZoneId: string;
  orchidGroupId: string;
  materialName: string;
  dilutionRatio: string;
  quantity: string;
  worker: string;
  memo: string;
};

export function WorkRecordManager({ initialRecords, houses, workTypes }: WorkRecordManagerProps) {
  const today = new Date().toISOString().slice(0, 10);
  const [records, setRecords] = useState(initialRecords);
  const [physicalBeds, setPhysicalBeds] = useState<PhysicalBed[]>([]);
  const [bedZones, setBedZones] = useState<BedZone[]>([]);
  const [orchidGroups, setOrchidGroups] = useState<OrchidGroup[]>([]);
  const [form, setForm] = useState<WorkRecordFormState>({
    workType: workTypes[0] ?? "농약",
    workDate: today,
    targetType: "FARM",
    houseId: houses[0] ? String(houses[0].houseId) : "",
    physicalBedId: "",
    bedZoneId: "",
    orchidGroupId: "",
    materialName: "",
    dilutionRatio: "",
    quantity: "",
    worker: "",
    memo: "",
  });
  const [saving, setSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const safePhysicalBedId = form.physicalBedId && physicalBeds.some((bed) => String(bed.id) === form.physicalBedId)
    ? form.physicalBedId
    : physicalBeds[0]
      ? String(physicalBeds[0].id)
      : "";
  const safeBedZoneId = form.bedZoneId && bedZones.some((zone) => String(zone.id) === form.bedZoneId)
    ? form.bedZoneId
    : bedZones[0]
      ? String(bedZones[0].id)
      : "";
  const safeOrchidGroupId = form.orchidGroupId && orchidGroups.some((group) => String(group.id) === form.orchidGroupId)
    ? form.orchidGroupId
    : orchidGroups[0]
      ? String(orchidGroups[0].id)
      : "";

  const selectedTargetId = useMemo(() => {
    if (form.targetType === "HOUSE") {
      return Number(form.houseId);
    }
    if (form.targetType === "PHYSICAL_BED") {
      return Number(safePhysicalBedId);
    }
    if (form.targetType === "BED_ZONE") {
      return Number(safeBedZoneId);
    }
    if (form.targetType === "ORCHID_GROUP") {
      return Number(safeOrchidGroupId);
    }
    return null;
  }, [form.houseId, form.targetType, safeBedZoneId, safeOrchidGroupId, safePhysicalBedId]);

  useEffect(() => {
    if (!form.houseId) {
      return;
    }
    let cancelled = false;
    async function loadHouseScopedOptions() {
      const [bedsResponse, zonesResponse, groupsResponse] = await Promise.all([
        fetch(`${API_BASE_URL}/physical-beds?houseId=${form.houseId}`, { cache: "no-store" }),
        fetch(`${API_BASE_URL}/bed-zones?houseId=${form.houseId}`, { cache: "no-store" }),
        fetch(`${API_BASE_URL}/orchid-groups?houseId=${form.houseId}`, { cache: "no-store" }),
      ]);
      const [bedsPayload, zonesPayload, groupsPayload] = await Promise.all([
        bedsResponse.json(),
        zonesResponse.json(),
        groupsResponse.json(),
      ]);
      if (cancelled) {
        return;
      }
      setPhysicalBeds(bedsPayload.data ?? []);
      setBedZones(zonesPayload.data ?? []);
      setOrchidGroups(groupsPayload.data ?? []);
    }
    void loadHouseScopedOptions().catch((error) => {
      if (!cancelled) {
        setErrorMessage(error instanceof Error ? error.message : "대상 목록을 불러오지 못했습니다.");
      }
    });
    return () => {
      cancelled = true;
    };
  }, [form.houseId]);

  function updateForm<K extends keyof WorkRecordFormState>(field: K, value: WorkRecordFormState[K]) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setErrorMessage(null);
    try {
      const response = await fetch(`${API_BASE_URL}/work-records`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          workType: form.workType,
          workDate: form.workDate,
          targetType: form.targetType,
          targetId: selectedTargetId,
          materialName: nullableText(form.materialName),
          dilutionRatio: nullableText(form.dilutionRatio),
          quantity: nullableText(form.quantity),
          worker: nullableText(form.worker),
          memo: nullableText(form.memo),
        }),
      });
      const payload = await response.json();
      if (!response.ok) {
        throw new Error(payload?.error?.message ?? "작업 이력을 저장하지 못했습니다.");
      }
      setRecords((current) => [payload.data as WorkRecord, ...current]);
      setForm((current) => ({
        ...current,
        materialName: "",
        dilutionRatio: "",
        quantity: "",
        memo: "",
      }));
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="grid gap-5 xl:grid-cols-[420px_minmax(0,1fr)]">
      <section className="rounded-md border border-[#d7ddd4] bg-white p-4 shadow-sm">
        <div>
          <p className="text-sm font-semibold text-[#3d6f91]">작업 등록</p>
          <h2 className="mt-1 text-2xl font-semibold">새 작업 이력</h2>
        </div>
        <form className="mt-4 space-y-3" onSubmit={handleSubmit}>
          <div className="grid grid-cols-2 gap-3">
            <SelectField label="작업 유형" value={form.workType} onChange={(value) => updateForm("workType", value)}>
              {workTypes.map((workType) => (
                <option key={workType} value={workType}>
                  {workType}
                </option>
              ))}
            </SelectField>
            <TextField label="작업일" required type="date" value={form.workDate} onChange={(value) => updateForm("workDate", value)} />
          </div>
          <SelectField label="대상 유형" value={form.targetType} onChange={(value) => updateForm("targetType", value as WorkRecordTargetType)}>
            <option value="FARM">전체 농장</option>
            <option value="HOUSE">동</option>
            <option value="PHYSICAL_BED">물리 배드</option>
            <option value="BED_ZONE">논리 구역</option>
            <option value="ORCHID_GROUP">난 묶음</option>
          </SelectField>
          {form.targetType !== "FARM" ? (
            <SelectField label="동" value={form.houseId} onChange={(value) => updateForm("houseId", value)}>
              {houses.map((house) => (
                <option key={house.houseId} value={house.houseId}>
                  {house.houseNumber}동
                </option>
              ))}
            </SelectField>
          ) : null}
          {form.targetType === "PHYSICAL_BED" ? (
            <SelectField label="배드" value={safePhysicalBedId} onChange={(value) => updateForm("physicalBedId", value)}>
              {physicalBeds.map((bed) => (
                <option key={bed.id} value={bed.id}>
                  {bed.number}배드
                </option>
              ))}
            </SelectField>
          ) : null}
          {form.targetType === "BED_ZONE" ? (
            <SelectField label="구역" value={safeBedZoneId} onChange={(value) => updateForm("bedZoneId", value)}>
              {bedZones.map((zone) => (
                <option key={zone.id} value={zone.id}>
                  {zone.physicalBedNumber}배드 {zone.side === "LEFT" ? "좌" : "우"}
                </option>
              ))}
            </SelectField>
          ) : null}
          {form.targetType === "ORCHID_GROUP" ? (
            <SelectField label="난 묶음" value={safeOrchidGroupId} onChange={(value) => updateForm("orchidGroupId", value)}>
              {orchidGroups.map((group) => (
                <option key={group.id} value={group.id}>
                  {group.physicalBedNumber}배드 {group.bedZoneName} / {group.varietyName}
                </option>
              ))}
            </SelectField>
          ) : null}
          <div className="grid grid-cols-2 gap-3">
            <TextField label="자재명" value={form.materialName} onChange={(value) => updateForm("materialName", value)} />
            <TextField label="희석 배수" value={form.dilutionRatio} onChange={(value) => updateForm("dilutionRatio", value)} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <TextField label="수량" value={form.quantity} onChange={(value) => updateForm("quantity", value)} />
            <TextField label="작업자" value={form.worker} onChange={(value) => updateForm("worker", value)} />
          </div>
          <label className="block">
            <span className="text-sm font-semibold text-[#435047]">메모</span>
            <textarea
              className="mt-1 min-h-20 w-full rounded-md border border-[#cfd8cc] px-3 py-2 text-sm"
              value={form.memo}
              onChange={(event) => updateForm("memo", event.target.value)}
            />
          </label>
          {errorMessage ? <p className="rounded-md bg-[#fff1ec] p-3 text-sm text-[#9b341e]">{errorMessage}</p> : null}
          <button className="w-full rounded-md bg-[#159447] px-4 py-3 text-sm font-semibold text-white disabled:opacity-60" disabled={saving} type="submit">
            {saving ? "저장 중" : "작업 이력 저장"}
          </button>
        </form>
      </section>

      <section className="rounded-md border border-[#d7ddd4] bg-white p-4 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-sm font-semibold text-[#3d6f91]">작업 목록</p>
            <h2 className="mt-1 text-2xl font-semibold">최근 작업 이력</h2>
          </div>
          <span className="rounded-full bg-[#eef7ec] px-3 py-1 text-sm font-semibold text-[#246b38]">{records.length}건</span>
        </div>
        <div className="mt-4 overflow-x-auto">
          <table className="w-full min-w-[720px] border-separate border-spacing-y-2 text-left text-sm">
            <thead className="text-[#637063]">
              <tr>
                <th className="px-3 font-semibold">작업일</th>
                <th className="px-3 font-semibold">유형</th>
                <th className="px-3 font-semibold">대상</th>
                <th className="px-3 font-semibold">자재/수량</th>
                <th className="px-3 font-semibold">작업자</th>
                <th className="px-3 font-semibold">메모</th>
              </tr>
            </thead>
            <tbody>
              {records.map((record) => (
                <tr key={record.id} className="bg-[#f8faf7]">
                  <td className="rounded-l-md px-3 py-3 font-medium">{record.workDate}</td>
                  <td className="px-3 py-3">{record.workType}</td>
                  <td className="px-3 py-3">{formatTarget(record)}</td>
                  <td className="px-3 py-3">{[record.materialName, record.dilutionRatio, record.quantity].filter(Boolean).join(" / ") || "-"}</td>
                  <td className="px-3 py-3">{record.worker ?? "-"}</td>
                  <td className="rounded-r-md px-3 py-3">{record.memo ?? "-"}</td>
                </tr>
              ))}
              {records.length === 0 ? (
                <tr>
                  <td className="rounded-md bg-[#f8faf7] px-3 py-8 text-center text-[#5c6a60]" colSpan={6}>
                    아직 등록된 작업 이력이 없습니다.
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}

function TextField({
  label,
  onChange,
  required = false,
  type = "text",
  value,
}: {
  label: string;
  onChange: (value: string) => void;
  required?: boolean;
  type?: "date" | "text";
  value: string;
}) {
  return (
    <label className="block">
      <span className="text-sm font-semibold text-[#435047]">{label}</span>
      <input
        className="mt-1 w-full rounded-md border border-[#cfd8cc] px-3 py-2 text-sm"
        required={required}
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

function SelectField({
  children,
  label,
  onChange,
  value,
}: {
  children: React.ReactNode;
  label: string;
  onChange: (value: string) => void;
  value: string;
}) {
  return (
    <label className="block">
      <span className="text-sm font-semibold text-[#435047]">{label}</span>
      <select className="mt-1 w-full rounded-md border border-[#cfd8cc] px-3 py-2 text-sm" value={value} onChange={(event) => onChange(event.target.value)}>
        {children}
      </select>
    </label>
  );
}

function nullableText(value: string) {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function formatTarget(record: WorkRecord) {
  if (record.targetType === "FARM") {
    return "전체 농장";
  }
  const label = {
    HOUSE: "동",
    PHYSICAL_BED: "배드",
    BED_ZONE: "구역",
    ORCHID_GROUP: "난 묶음",
  }[record.targetType];
  return `${label} #${record.targetId}`;
}
