"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useMemo, useState } from "react";
import { API_BASE_URL } from "@/lib/api";
import type {
  BedZone,
  FarmStatusMapData,
  House,
  HouseStatusSummary,
  OrchidGroup,
  OrchidManagementViewMode,
  PhysicalBed,
  SelectedBedZone,
  SelectedOrchidGroup,
} from "@/types/farm";

type OrchidSelection = SelectedBedZone | SelectedOrchidGroup;
type MutationMode = "CREATE" | "EDIT" | null;

type OrchidFormState = {
  genus: string;
  varietyName: string;
  quantity: string;
  potSize: string;
  ageYear: string;
  status: string;
  placementType: string;
  trayCount: string;
  memo: string;
};

type MutationPayload = {
  genus: string | null;
  varietyName: string;
  quantity: number;
  potSize: string | null;
  ageYear: number | null;
  status: string;
  placementType: string | null;
  trayCount: number | null;
  memo: string | null;
};

type OrchidManagementMapProps = {
  mapData: FarmStatusMapData;
  house: House;
};

export function OrchidManagementMap({ mapData, house }: OrchidManagementMapProps) {
  const router = useRouter();
  const firstOrchidGroup = useMemo(() => {
    for (const bed of house.physicalBeds) {
      for (const zone of bed.bedZones) {
        if (zone.orchidGroups[0]) {
          return zone.orchidGroups[0];
        }
      }
    }
    return null;
  }, [house.physicalBeds]);

  const [selection, setSelection] = useState<OrchidSelection | null>(
    firstOrchidGroup ? { type: "ORCHID_GROUP", orchidGroupId: firstOrchidGroup.id } : null,
  );
  const [viewMode, setViewMode] = useState<OrchidManagementViewMode>("REAL_DIRECTION");
  const [mutationMode, setMutationMode] = useState<MutationMode>(null);
  const [saving, setSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const selectedOrchidGroup = selection?.type === "ORCHID_GROUP" ? findOrchidGroup(house, selection.orchidGroupId) : null;
  const selectedBedZone = selection?.type === "BED_ZONE" ? findBedZone(house, selection.bedZoneId)?.zone ?? null : null;
  const resolvedZone = selectedOrchidGroup ? findBedZone(house, selectedOrchidGroup.bedZoneId)?.zone ?? null : selectedBedZone;

  async function handleCreate(payload: MutationPayload) {
    if (!resolvedZone) {
      setErrorMessage("난 묶음을 추가할 구역을 선택하세요.");
      return;
    }
    await submitMutation("/orchid-groups", "POST", { ...payload, bedZoneId: resolvedZone.id });
  }

  async function handleUpdate(payload: MutationPayload) {
    if (!selectedOrchidGroup) {
      setErrorMessage("수정할 난 묶음을 선택하세요.");
      return;
    }
    await submitMutation(`/orchid-groups/${selectedOrchidGroup.id}`, "PATCH", payload);
  }

  async function submitMutation(path: string, method: "POST" | "PATCH", payload: MutationPayload & { bedZoneId?: number }) {
    setSaving(true);
    setErrorMessage(null);
    try {
      const response = await fetch(`${API_BASE_URL}${path}`, {
        method,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      const body = await response.json();
      if (!response.ok) {
        throw new Error(body?.error?.message ?? "저장하지 못했습니다.");
      }
      setMutationMode(null);
      router.refresh();
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.");
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete() {
    if (!selectedOrchidGroup) {
      return;
    }
    const confirmed = window.confirm(`${selectedOrchidGroup.varietyName} 난 묶음을 삭제할까요?`);
    if (!confirmed) {
      return;
    }
    setSaving(true);
    setErrorMessage(null);
    try {
      const response = await fetch(`${API_BASE_URL}/orchid-groups/${selectedOrchidGroup.id}`, { method: "DELETE" });
      if (!response.ok) {
        const body = await response.json();
        throw new Error(body?.error?.message ?? "삭제하지 못했습니다.");
      }
      setSelection(resolvedZone ? { type: "BED_ZONE", bedZoneId: resolvedZone.id } : null);
      setMutationMode(null);
      router.refresh();
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="grid gap-6 2xl:grid-cols-[320px_minmax(0,1fr)_380px]">
      <HouseSelectorPanel houses={mapData.houses} selectedHouseId={house.id} />
      <section className="space-y-4">
        <HouseDetailHeader house={house} viewMode={viewMode} onViewModeChange={setViewMode} />
        <HouseDetailMap
          house={house}
          selection={selection}
          onSelectBedZone={(bedZoneId) => {
            setSelection({ type: "BED_ZONE", bedZoneId });
            setMutationMode(null);
          }}
          onSelectOrchidGroup={(orchidGroupId) => {
            setSelection({ type: "ORCHID_GROUP", orchidGroupId });
            setMutationMode(null);
          }}
        />
        <MapLegend />
      </section>
      <OrchidSelectionPanel
        errorMessage={errorMessage}
        house={house}
        mutationMode={mutationMode}
        resolvedZone={resolvedZone}
        saving={saving}
        selectedBedZone={selectedBedZone}
        selectedOrchidGroup={selectedOrchidGroup}
        onCancelMutation={() => setMutationMode(null)}
        onCreate={handleCreate}
        onDelete={handleDelete}
        onEdit={handleUpdate}
        onOpenCreate={() => {
          if (resolvedZone) {
            setMutationMode("CREATE");
            setErrorMessage(null);
          } else {
            setErrorMessage("먼저 구역을 선택하세요.");
          }
        }}
        onOpenEdit={() => {
          if (selectedOrchidGroup) {
            setMutationMode("EDIT");
            setErrorMessage(null);
          }
        }}
      />
    </div>
  );
}

function HouseSelectorPanel({ houses, selectedHouseId }: { houses: HouseStatusSummary[]; selectedHouseId: number }) {
  return (
    <aside className="rounded-md border border-[#d7ddd4] bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-[#3d6f91]">전체 동 보기</p>
          <h2 className="mt-1 text-2xl font-semibold">동 선택</h2>
        </div>
        <span className="rounded-full border border-[#d7ddd4] px-2 py-1 text-sm text-[#5c6a60]">15</span>
      </div>
      <div className="mt-5 grid grid-cols-3 gap-3 2xl:grid-cols-2">
        {houses.map((house) => {
          const selected = house.houseId === selectedHouseId;
          const warning = house.warningCount > 0;
          return (
            <Link
              key={house.houseId}
              href={`/orchid-groups?houseId=${house.houseId}`}
              className={`min-h-32 rounded-md border p-3 text-center shadow-sm transition hover:border-[#159447] ${
                selected ? "border-[#159447] bg-[#eef7ec] ring-2 ring-[#159447]" : "border-[#d7ddd4] bg-white"
              }`}
            >
              <p className="text-xl font-semibold">{house.houseNumber}동</p>
              <div className="mx-auto mt-3 h-12 w-16 rounded-t-2xl border border-[#b9c7b9] bg-[linear-gradient(90deg,#f5f7f4_0,#f5f7f4_18%,#dfe7de_19%,#f5f7f4_20%,#f5f7f4_48%,#dfe7de_49%,#f5f7f4_50%,#f5f7f4_78%,#dfe7de_79%,#f5f7f4_80%)]" />
              <p className={`mt-2 text-sm font-semibold ${warning ? "text-[#f59e0b]" : "text-[#159447]"}`}>
                {warning ? "주의" : "정상"}
              </p>
            </Link>
          );
        })}
      </div>
    </aside>
  );
}

function HouseDetailHeader({
  house,
  viewMode,
  onViewModeChange,
}: {
  house: House;
  viewMode: OrchidManagementViewMode;
  onViewModeChange: (viewMode: OrchidManagementViewMode) => void;
}) {
  const viewModes: Array<{ value: OrchidManagementViewMode; label: string }> = [
    { value: "REAL_DIRECTION", label: "실제 방향 보기" },
    { value: "ROTATED", label: "회전 보기" },
    { value: "BY_BED", label: "배드별 보기" },
  ];

  return (
    <section className="rounded-md border border-[#d7ddd4] bg-white p-5 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="text-sm font-semibold text-[#3d6f91]">선택한 동</p>
          <h1 className="mt-1 text-3xl font-semibold">{house.number}동 상세 보기</h1>
        </div>
        <div className="flex flex-wrap gap-2">
          {viewModes.map((mode) => (
            <button
              key={mode.value}
              className={`rounded-md px-4 py-2 text-base font-semibold ${
                viewMode === mode.value ? "bg-[#159447] text-white" : "border border-[#d7ddd4] bg-white text-[#435047]"
              }`}
              onClick={() => onViewModeChange(mode.value)}
              type="button"
            >
              {mode.label}
            </button>
          ))}
        </div>
      </div>
      <div className="mt-4 flex flex-wrap gap-3 rounded-md border border-[#b9d0ff] bg-[#f3f7ff] px-4 py-3 text-base font-semibold text-[#246df2]">
        <span>보기 모드에서 난 묶음 추가, 수정, 삭제가 가능합니다.</span>
        <span className="text-[#b4c2dc]">|</span>
        <span>위치 이동은 다음 단계에서 연결합니다.</span>
      </div>
    </section>
  );
}

function HouseDetailMap({
  house,
  selection,
  onSelectBedZone,
  onSelectOrchidGroup,
}: {
  house: House;
  selection: OrchidSelection | null;
  onSelectBedZone: (bedZoneId: number) => void;
  onSelectOrchidGroup: (orchidGroupId: number) => void;
}) {
  return (
    <section className="rounded-md border border-[#d7ddd4] bg-white p-5 shadow-sm">
      <div className="grid gap-5 xl:grid-cols-3">
        {house.physicalBeds.map((bed) => (
          <PhysicalBedBlock
            key={bed.id}
            bed={bed}
            selection={selection}
            onSelectBedZone={onSelectBedZone}
            onSelectOrchidGroup={onSelectOrchidGroup}
          />
        ))}
      </div>
    </section>
  );
}

function PhysicalBedBlock({
  bed,
  selection,
  onSelectBedZone,
  onSelectOrchidGroup,
}: {
  bed: PhysicalBed;
  selection: OrchidSelection | null;
  onSelectBedZone: (bedZoneId: number) => void;
  onSelectOrchidGroup: (orchidGroupId: number) => void;
}) {
  return (
    <div className="rounded-md border border-[#cfe0cc] bg-[#f7faf6] p-3">
      <h3 className="text-center text-2xl font-semibold">{bed.number}배드</h3>
      <div className="mt-4 grid grid-cols-2 gap-3">
        {bed.bedZones.map((zone) => (
          <BedZoneBlock
            key={zone.id}
            zone={zone}
            selected={selection?.type === "BED_ZONE" && selection.bedZoneId === zone.id}
            selectedOrchidGroupId={selection?.type === "ORCHID_GROUP" ? selection.orchidGroupId : null}
            onSelectBedZone={onSelectBedZone}
            onSelectOrchidGroup={onSelectOrchidGroup}
          />
        ))}
      </div>
    </div>
  );
}

function BedZoneBlock({
  zone,
  selected,
  selectedOrchidGroupId,
  onSelectBedZone,
  onSelectOrchidGroup,
}: {
  zone: BedZone;
  selected: boolean;
  selectedOrchidGroupId: number | null;
  onSelectBedZone: (bedZoneId: number) => void;
  onSelectOrchidGroup: (orchidGroupId: number) => void;
}) {
  const emptySlotCount = Math.max(1, 5 - zone.orchidGroups.length);

  return (
    <div
      className={`min-h-[520px] rounded-md border p-3 text-left transition ${
        selected ? "border-[#246df2] bg-[#f3f7ff] ring-2 ring-[#246df2]" : "border-[#d7ddd4] bg-white hover:border-[#159447]"
      }`}
      onClick={() => onSelectBedZone(zone.id)}
      role="button"
      tabIndex={0}
    >
      <p className="text-center text-xl font-semibold">{zone.side === "LEFT" ? "좌" : "우"}</p>
      <div className="mt-4 space-y-3">
        {zone.orchidGroups.map((orchidGroup) => (
          <OrchidGroupBlock
            key={orchidGroup.id}
            orchidGroup={orchidGroup}
            selected={selectedOrchidGroupId === orchidGroup.id}
            onSelect={(event) => {
              event.stopPropagation();
              onSelectOrchidGroup(orchidGroup.id);
            }}
          />
        ))}
        {Array.from({ length: emptySlotCount }, (_, index) => (
          <div key={index} className="min-h-20 rounded-md border border-dashed border-[#d7ddd4] bg-[#f0f1ef]" />
        ))}
      </div>
    </div>
  );
}

function OrchidGroupBlock({
  orchidGroup,
  selected,
  onSelect,
}: {
  orchidGroup: OrchidGroup;
  selected: boolean;
  onSelect: (event: React.MouseEvent<HTMLDivElement>) => void;
}) {
  const warning = orchidGroup.status !== "정상" && orchidGroup.status !== "판매 가능";

  return (
    <div
      className={`min-h-24 cursor-pointer rounded-md border p-3 shadow-sm transition ${
        selected ? "border-[#246df2] bg-[#dcecff] ring-2 ring-[#246df2]" : "border-[#82c886] bg-[#bfe2b8] hover:border-[#159447]"
      }`}
      onClick={onSelect}
      role="button"
      tabIndex={0}
    >
      <div className="flex items-start justify-between gap-2">
        <p className="font-semibold">{orchidGroup.varietyName}</p>
        <span className={warning ? "text-[#f59e0b]" : "text-[#159447]"}>●</span>
      </div>
      <p className="mt-1 text-sm font-semibold">{orchidGroup.quantity}분</p>
      <p className="mt-1 text-sm text-[#435047]">
        {[orchidGroup.potSize, orchidGroup.ageYear ? `${orchidGroup.ageYear}년생` : null].filter(Boolean).join(" · ")}
      </p>
    </div>
  );
}

function OrchidSelectionPanel({
  errorMessage,
  house,
  mutationMode,
  resolvedZone,
  saving,
  selectedBedZone,
  selectedOrchidGroup,
  onCancelMutation,
  onCreate,
  onDelete,
  onEdit,
  onOpenCreate,
  onOpenEdit,
}: {
  errorMessage: string | null;
  house: House;
  mutationMode: MutationMode;
  resolvedZone: BedZone | null;
  saving: boolean;
  selectedBedZone: BedZone | null;
  selectedOrchidGroup: OrchidGroup | null;
  onCancelMutation: () => void;
  onCreate: (payload: MutationPayload) => Promise<void>;
  onDelete: () => Promise<void>;
  onEdit: (payload: MutationPayload) => Promise<void>;
  onOpenCreate: () => void;
  onOpenEdit: () => void;
}) {
  const zone = selectedOrchidGroup ? findBedZone(house, selectedOrchidGroup.bedZoneId)?.zone ?? null : selectedBedZone;
  const totalQuantity = zone?.orchidGroups.reduce((sum, orchidGroup) => sum + orchidGroup.quantity, 0) ?? 0;

  return (
    <aside className="space-y-4">
      <section className="rounded-md border border-[#d7ddd4] bg-white p-5 shadow-sm">
        <p className="text-sm font-semibold text-[#3d6f91]">선택한 난 묶음</p>
        {selectedOrchidGroup ? (
          <div className="mt-4">
            <div className="flex gap-4">
              <div className="flex h-20 w-20 items-center justify-center rounded-md bg-[#d8edd5] text-4xl">●</div>
              <div>
                <h2 className="text-2xl font-semibold">{selectedOrchidGroup.varietyName}</h2>
                <p className="mt-1 text-lg text-[#435047]">{selectedOrchidGroup.quantity}분</p>
                <p className="mt-1 text-[#246df2]">
                  {selectedOrchidGroup.houseNumber}동 &gt; {selectedOrchidGroup.physicalBedNumber}배드 &gt; {selectedOrchidGroup.bedZoneName}
                </p>
              </div>
            </div>
            <div className="mt-5 grid gap-3">
              <ActionButton label="난 묶음 추가" onClick={onOpenCreate} primary />
              <ActionButton label="난 묶음 수정" onClick={onOpenEdit} />
              <ActionButton label="난 묶음 삭제" onClick={onDelete} danger disabled={saving} />
              <DisabledAction label="다른 위치로 이동" />
              <DisabledAction label="작업 기록 추가" />
              <DisabledAction label="출력" />
            </div>
          </div>
        ) : (
          <div className="mt-4">
            <p className="text-[#5c6a60]">구역을 선택한 뒤 난 묶음을 추가할 수 있습니다.</p>
            <button className="mt-4 w-full rounded-md bg-[#159447] px-4 py-3 text-base font-semibold text-white" onClick={onOpenCreate} type="button">
              난 묶음 추가
            </button>
          </div>
        )}
        {errorMessage ? <p className="mt-4 rounded-md border border-[#f1b0a0] bg-[#fff1ec] p-3 text-sm text-[#9b341e]">{errorMessage}</p> : null}
      </section>

      {mutationMode ? (
        <OrchidGroupForm
          initialValue={mutationMode === "EDIT" ? selectedOrchidGroup : null}
          mode={mutationMode}
          saving={saving}
          targetZone={resolvedZone}
          onCancel={onCancelMutation}
          onSubmit={mutationMode === "EDIT" ? onEdit : onCreate}
        />
      ) : null}

      <section className="rounded-md border border-[#d7ddd4] bg-white p-5 shadow-sm">
        <div className="flex items-center justify-between gap-3">
          <p className="text-xl font-semibold">선택한 구역 정보</p>
          {zone ? <span className="rounded-md bg-[#e6f0ff] px-2 py-1 text-sm font-semibold text-[#246df2]">{zone.name}</span> : null}
        </div>
        {zone ? (
          <>
            <div className="mt-5 grid grid-cols-4 gap-3 text-center">
              <InfoMetric label="난 묶음 수" value={`${zone.orchidGroups.length}개`} />
              <InfoMetric label="총 수량" value={`${totalQuantity}분`} />
              <InfoMetric label="빈 공간" value={`${Math.max(0, 5 - zone.orchidGroups.length)}칸`} />
              <InfoMetric label="상태" value="정상" />
            </div>
            <div className="mt-5 border-t border-[#e1e6df] pt-4">
              <p className="font-semibold">최근 작업 요약</p>
              <dl className="mt-3 space-y-2 text-sm text-[#5c6a60]">
                <div className="flex justify-between"><dt>최근 농약</dt><dd>다음 단계에서 연결</dd></div>
                <div className="flex justify-between"><dt>최근 비료</dt><dd>다음 단계에서 연결</dd></div>
                <div className="flex justify-between"><dt>최근 분갈이</dt><dd>다음 단계에서 연결</dd></div>
              </dl>
            </div>
          </>
        ) : (
          <p className="mt-4 text-[#5c6a60]">구역 또는 난 묶음을 선택하세요.</p>
        )}
      </section>
    </aside>
  );
}

function OrchidGroupForm({
  initialValue,
  mode,
  saving,
  targetZone,
  onCancel,
  onSubmit,
}: {
  initialValue: OrchidGroup | null;
  mode: Exclude<MutationMode, null>;
  saving: boolean;
  targetZone: BedZone | null;
  onCancel: () => void;
  onSubmit: (payload: MutationPayload) => Promise<void>;
}) {
  const [form, setForm] = useState<OrchidFormState>(() => ({
    genus: initialValue?.genus ?? "",
    varietyName: initialValue?.varietyName ?? "",
    quantity: initialValue ? String(initialValue.quantity) : "1",
    potSize: initialValue?.potSize ?? "",
    ageYear: initialValue?.ageYear ? String(initialValue.ageYear) : "",
    status: initialValue?.status ?? "정상",
    placementType: initialValue?.placementType ?? "",
    trayCount: initialValue?.trayCount ? String(initialValue.trayCount) : "",
    memo: initialValue?.memo ?? "",
  }));

  function updateField(field: keyof OrchidFormState, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void onSubmit({
      genus: nullableText(form.genus),
      varietyName: form.varietyName.trim(),
      quantity: Number(form.quantity),
      potSize: nullableText(form.potSize),
      ageYear: nullableNumber(form.ageYear),
      status: form.status.trim(),
      placementType: nullableText(form.placementType),
      trayCount: nullableNumber(form.trayCount),
      memo: nullableText(form.memo),
    });
  }

  return (
    <section className="rounded-md border border-[#b9d0ff] bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-[#246df2]">{mode === "CREATE" ? "난 묶음 추가" : "난 묶음 수정"}</p>
          <h3 className="mt-1 text-xl font-semibold">{targetZone?.name ?? "구역 선택 필요"}</h3>
        </div>
        <button className="rounded-md border border-[#d7ddd4] px-3 py-2 text-sm font-semibold" onClick={onCancel} type="button">
          닫기
        </button>
      </div>
      <form className="mt-4 space-y-3" onSubmit={handleSubmit}>
        <TextField label="품종명" required value={form.varietyName} onChange={(value) => updateField("varietyName", value)} />
        <div className="grid grid-cols-2 gap-3">
          <TextField label="속명" value={form.genus} onChange={(value) => updateField("genus", value)} />
          <TextField label="수량" required type="number" value={form.quantity} onChange={(value) => updateField("quantity", value)} />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <TextField label="화분 크기" value={form.potSize} onChange={(value) => updateField("potSize", value)} />
          <TextField label="년생" type="number" value={form.ageYear} onChange={(value) => updateField("ageYear", value)} />
        </div>
        <label className="block">
          <span className="text-sm font-semibold text-[#435047]">상태</span>
          <select
            className="mt-1 w-full rounded-md border border-[#cfd8cc] px-3 py-2 text-base"
            value={form.status}
            onChange={(event) => updateField("status", event.target.value)}
          >
            <option value="정상">정상</option>
            <option value="주의">주의</option>
            <option value="이상">이상</option>
            <option value="판매 가능">판매 가능</option>
          </select>
        </label>
        <div className="grid grid-cols-2 gap-3">
          <TextField label="배치 유형" value={form.placementType} onChange={(value) => updateField("placementType", value)} />
          <TextField label="트레이 수" type="number" value={form.trayCount} onChange={(value) => updateField("trayCount", value)} />
        </div>
        <label className="block">
          <span className="text-sm font-semibold text-[#435047]">메모</span>
          <textarea
            className="mt-1 min-h-24 w-full rounded-md border border-[#cfd8cc] px-3 py-2 text-base"
            value={form.memo}
            onChange={(event) => updateField("memo", event.target.value)}
          />
        </label>
        <button
          className="w-full rounded-md bg-[#159447] px-4 py-3 text-base font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60"
          disabled={saving || !targetZone}
          type="submit"
        >
          {saving ? "저장 중" : "저장"}
        </button>
      </form>
    </section>
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
  type?: "number" | "text";
  value: string;
}) {
  return (
    <label className="block">
      <span className="text-sm font-semibold text-[#435047]">{label}</span>
      <input
        className="mt-1 w-full rounded-md border border-[#cfd8cc] px-3 py-2 text-base"
        min={type === "number" ? 0 : undefined}
        required={required}
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

function ActionButton({
  danger = false,
  disabled = false,
  label,
  onClick,
  primary = false,
}: {
  danger?: boolean;
  disabled?: boolean;
  label: string;
  onClick: () => void;
  primary?: boolean;
}) {
  const className = danger
    ? "border border-[#e0b3aa] bg-white text-[#b43b24]"
    : primary
      ? "bg-[#159447] text-white"
      : "border border-[#d7ddd4] bg-white text-[#435047]";

  return (
    <button className={`rounded-md px-4 py-3 text-base font-semibold ${className}`} disabled={disabled} onClick={onClick} type="button">
      {label}
    </button>
  );
}

function DisabledAction({ label }: { label: string }) {
  return (
    <button className="rounded-md border border-[#d7ddd4] bg-white px-4 py-3 text-base font-semibold text-[#435047] opacity-60" disabled type="button">
      {label}
    </button>
  );
}

function InfoMetric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-sm text-[#5c6a60]">{label}</p>
      <p className="mt-1 text-lg font-semibold">{value}</p>
    </div>
  );
}

function MapLegend() {
  return (
    <div className="flex flex-wrap gap-6 rounded-md border border-[#d7ddd4] bg-white p-4 text-base shadow-sm">
      <span><span className="mr-2 inline-block h-5 w-5 rounded border border-[#246df2] bg-[#dcecff] align-middle" />선택 영역</span>
      <span><span className="mr-2 inline-block h-5 w-5 rounded border border-[#82c886] bg-[#bfe2b8] align-middle" />난 묶음 있음</span>
      <span><span className="mr-2 inline-block h-5 w-5 rounded border border-[#d7ddd4] bg-[#f0f1ef] align-middle" />비어 있음</span>
    </div>
  );
}

function nullableText(value: string) {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function nullableNumber(value: string) {
  const trimmed = value.trim();
  return trimmed.length > 0 ? Number(trimmed) : null;
}

function findOrchidGroup(house: House, orchidGroupId: number) {
  for (const bed of house.physicalBeds) {
    for (const zone of bed.bedZones) {
      const orchidGroup = zone.orchidGroups.find((item) => item.id === orchidGroupId);
      if (orchidGroup) {
        return orchidGroup;
      }
    }
  }
  return null;
}

function findBedZone(house: House, bedZoneId: number) {
  for (const bed of house.physicalBeds) {
    for (const zone of bed.bedZones) {
      if (zone.id === bedZoneId) {
        return { bed, zone };
      }
    }
  }
  return null;
}
