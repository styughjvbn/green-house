"use client";

import { ArrowDown, ArrowUp, Plus } from "lucide-react";
import { useEffect, useState } from "react";
import type { WorkType, WorkTypeTemplate } from "@/entities/farm/types";
import {
  getWorkTypeTemplateLabel,
  WORK_TYPE_TEMPLATES,
} from "@/entities/farm/workTypes";
import {
  createSettingWorkType,
  getSettingWorkTypes,
  reorderSettingWorkTypes,
  updateSettingWorkType,
} from "../api/workTypeSettingsApi";

export function WorkTypeSettingsSection() {
  const [workTypes, setWorkTypes] = useState<WorkType[]>([]);
  const [newName, setNewName] = useState("");
  const [newTemplate, setNewTemplate] = useState<WorkTypeTemplate>("MEMO");
  const [saving, setSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    void load();
  }, []);

  async function load() {
    try {
      setWorkTypes(await getSettingWorkTypes());
      setErrorMessage(null);
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "작업 유형을 불러오지 못했습니다.",
      );
    }
  }

  async function createWorkType() {
    if (!newName.trim()) return;
    await run(async () => {
      const created = await createSettingWorkType({
        name: newName.trim(),
        template: newTemplate,
      });
      setWorkTypes((current) => [...current, created]);
      setNewName("");
      setNewTemplate("MEMO");
    });
  }

  async function updateWorkType(
    workType: WorkType,
    patch: Partial<Pick<WorkType, "name" | "template" | "active">>,
  ) {
    await run(async () => {
      const updated = await updateSettingWorkType(workType, {
        name: patch.name ?? workType.name,
        template: patch.template ?? workType.template,
        active: patch.active ?? workType.active,
      });
      setWorkTypes((current) =>
        current.map((candidate) =>
          candidate.id === updated.id ? updated : candidate,
        ),
      );
    });
  }

  async function move(workType: WorkType, direction: -1 | 1) {
    const index = workTypes.findIndex(
      (candidate) => candidate.id === workType.id,
    );
    const nextIndex = index + direction;
    if (index < 0 || nextIndex < 0 || nextIndex >= workTypes.length) return;

    const reordered = [...workTypes];
    [reordered[index], reordered[nextIndex]] = [
      reordered[nextIndex],
      reordered[index],
    ];
    setWorkTypes(reordered);
    await run(async () => {
      setWorkTypes(
        await reorderSettingWorkTypes(reordered.map((item) => item.id)),
      );
    });
  }

  async function run(action: () => Promise<void>) {
    setSaving(true);
    setErrorMessage(null);
    try {
      await action();
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "저장하지 못했습니다.",
      );
      await load();
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="rounded-md border border-[#d7ddd4] bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h2 className="text-xl font-semibold">작업 유형 관리</h2>
          <p className="mt-1 text-sm text-[#5c6a60]">
            작업 기록 입력 항목은 선택한 유형 템플릿에 따라 바뀝니다.
          </p>
        </div>
        <div className="rounded-md bg-[#eef7ec] px-3 py-2 text-sm font-semibold text-[#246b38]">
          {workTypes.filter((workType) => workType.active).length}개 사용 중
        </div>
      </div>

      <div className="mt-5 grid gap-2 lg:grid-cols-[minmax(0,1fr)_180px_auto]">
        <input
          className="rounded-md border border-[#cfd8cc] px-3 py-2 text-sm"
          placeholder="새 작업 유형 이름"
          value={newName}
          onChange={(event) => setNewName(event.target.value)}
        />
        <TemplateSelect value={newTemplate} onChange={setNewTemplate} />
        <button
          className="inline-flex items-center justify-center gap-2 rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
          type="button"
          disabled={saving || !newName.trim()}
          onClick={createWorkType}
        >
          <Plus className="h-4 w-4" aria-hidden="true" />
          추가
        </button>
      </div>

      {errorMessage ? (
        <p className="mt-3 rounded-md bg-[#fff1ec] p-3 text-sm text-[#9b341e]">
          {errorMessage}
        </p>
      ) : null}

      <div className="mt-5 space-y-2">
        {workTypes.map((workType, index) => (
          <div
            key={workType.id}
            className="grid gap-2 rounded-md border border-[#edf0ec] bg-[#fbfcfa] p-3 lg:grid-cols-[minmax(0,1fr)_180px_110px_100px]"
          >
            <input
              className="rounded-md border border-[#cfd8cc] bg-white px-3 py-2 text-sm font-semibold disabled:bg-[#f0f2ef] disabled:text-[#6a766e]"
              disabled={saving || workType.systemType}
              value={workType.name}
              onChange={(event) =>
                setWorkTypes((current) =>
                  current.map((candidate) =>
                    candidate.id === workType.id
                      ? { ...candidate, name: event.target.value }
                      : candidate,
                  ),
                )
              }
              onBlur={(event) =>
                updateWorkType(workType, { name: event.target.value.trim() })
              }
            />
            <TemplateSelect
              disabled={saving || workType.systemType}
              value={workType.template}
              onChange={(template) => updateWorkType(workType, { template })}
            />
            <button
              className={`rounded-md border px-3 py-2 text-sm font-semibold ${
                workType.active
                  ? "border-[#94c49a] bg-[#eef7ec] text-[#246b38]"
                  : "border-[#d7ddd4] bg-white text-[#6a766e]"
              } disabled:opacity-50`}
              type="button"
              disabled={saving || workType.systemType}
              onClick={() =>
                updateWorkType(workType, { active: !workType.active })
              }
            >
              {workType.active ? "사용" : "미사용"}
            </button>
            <div className="flex gap-1">
              <button
                className="h-10 flex-1 rounded-md border border-[#d7ddd4] disabled:opacity-40"
                type="button"
                disabled={saving || index === 0}
                onClick={() => move(workType, -1)}
              >
                <ArrowUp className="mx-auto h-4 w-4" aria-hidden="true" />
              </button>
              <button
                className="h-10 flex-1 rounded-md border border-[#d7ddd4] disabled:opacity-40"
                type="button"
                disabled={saving || index === workTypes.length - 1}
                onClick={() => move(workType, 1)}
              >
                <ArrowDown className="mx-auto h-4 w-4" aria-hidden="true" />
              </button>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

function TemplateSelect({
  disabled = false,
  value,
  onChange,
}: {
  disabled?: boolean;
  value: WorkTypeTemplate;
  onChange: (value: WorkTypeTemplate) => void;
}) {
  return (
    <select
      className="rounded-md border border-[#cfd8cc] bg-white px-3 py-2 text-sm disabled:bg-[#f0f2ef]"
      disabled={disabled}
      value={value}
      onChange={(event) => onChange(event.target.value as WorkTypeTemplate)}
    >
      {WORK_TYPE_TEMPLATES.filter((template) => template !== "MOVEMENT").map(
        (template) => (
          <option key={template} value={template}>
            {getWorkTypeTemplateLabel(template)}
          </option>
        ),
      )}
      {value === "MOVEMENT" ? (
        <option value="MOVEMENT">{getWorkTypeTemplateLabel("MOVEMENT")}</option>
      ) : null}
    </select>
  );
}
