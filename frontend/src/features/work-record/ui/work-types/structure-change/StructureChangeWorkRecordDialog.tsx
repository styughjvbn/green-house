"use client";

import { useMemo, useState } from "react";
import type {
  House,
  OrchidGroup,
  WorkOperationTarget,
  WorkType,
} from "@/entities/farm/types";
import type { CreateWorkOperationPayload } from "../../../model/types";
import type {
  StructureChangeExecutionPayload,
  StructureChangeRecordPayload,
} from "../../../api/workRecordApi";
import { StructureChangeExecutionDialog } from "./StructureChangeExecutionDialog";

type VarietyTargetGroup = {
  key: string;
  varietyName: string;
  targets: WorkOperationTarget[];
};

export function StructureChangeWorkRecordDialog({
  baseOperation,
  houses,
  orchidGroups,
  targets,
  workType,
  onClose,
  onSubmit,
}: {
  baseOperation: CreateWorkOperationPayload;
  houses: House[];
  orchidGroups: OrchidGroup[];
  targets: WorkOperationTarget[];
  workType: WorkType;
  onClose: () => void;
  onSubmit: (records: StructureChangeRecordPayload[]) => Promise<void>;
}) {
  const groups = useMemo(
    () => groupTargetsByVariety(targets, orchidGroups),
    [orchidGroups, targets],
  );
  const [activeKey, setActiveKey] = useState(groups[0]?.key ?? "");
  const [records, setRecords] = useState<
    Map<string, StructureChangeRecordPayload>
  >(new Map());
  const [saving, setSaving] = useState(false);

  if (groups.length === 0) return null;

  function buildRecord(
    group: VarietyTargetGroup,
    execution: StructureChangeExecutionPayload,
  ): StructureChangeRecordPayload {
    const title =
      groups.length > 1
        ? `${baseOperation.title} - ${group.varietyName}`
        : baseOperation.title;
    return {
      operation: {
        ...baseOperation,
        title,
        sourceScopeType: "MANUAL_SELECTION" as const,
        sourceScopeId: undefined,
        sourceScopeKey: undefined,
        sourceOrchidGroupIds: group.targets.flatMap((target) =>
          target.orchidGroupId == null ? [] : [target.orchidGroupId],
        ),
        excludedOrchidGroupIds: [],
      },
      execution,
    };
  }

  async function saveAll() {
    if (records.size !== groups.length) return;
    setSaving(true);
    try {
      await onSubmit(
        groups.map((group) => records.get(group.key)!).filter(Boolean),
      );
    } finally {
      setSaving(false);
    }
  }

  const navigationItems = groups.map((group) => ({
    key: group.key,
    label: group.varietyName,
    completed: records.has(group.key),
  }));

  return (
    <div
      className="fixed inset-0 z-[1300] bg-black/45"
      role="presentation"
      onMouseDown={onClose}
    >
      {groups.map((group) => (
        <StructureChangeExecutionDialog
          active={group.key === activeKey}
          closeAfterSubmit={false}
          embedded
          houses={houses}
          orchidGroups={orchidGroups}
          key={group.key}
          operation={{
            id: 0,
            plannedStartDate: baseOperation.plannedStartDate,
            targets: group.targets,
            title: baseOperation.title,
            worker: baseOperation.worker,
            workType: `${workType.name} · ${group.varietyName}`,
            workTypeCode: workType.code,
          }}
          recordMode
          recordNavigation={{
            activeKey,
            allCompleted: records.size === groups.length,
            items: navigationItems,
            saving,
            onSave: saveAll,
            onSelect: setActiveKey,
          }}
          onClose={onClose}
          onRecordDirty={() =>
            setRecords((current) => {
              if (!current.has(group.key)) return current;
              const next = new Map(current);
              next.delete(group.key);
              return next;
            })
          }
          onSubmitRecord={async (execution) => {
            setRecords((current) => {
              const next = new Map(current);
              next.set(group.key, buildRecord(group, execution));
              return next;
            });
          }}
        />
      ))}
    </div>
  );
}

function groupTargetsByVariety(
  targets: WorkOperationTarget[],
  orchidGroups: OrchidGroup[],
): VarietyTargetGroup[] {
  const orchidGroupById = new Map(
    orchidGroups.map((group) => [group.id, group]),
  );
  const grouped = new Map<string, VarietyTargetGroup>();
  targets.forEach((target) => {
    const orchidGroup =
      target.orchidGroupId == null
        ? null
        : orchidGroupById.get(target.orchidGroupId);
    const key =
      orchidGroup?.varietyId == null
        ? `name:${target.varietyName}`
        : `id:${orchidGroup.varietyId}`;
    const current = grouped.get(key);
    if (current) {
      current.targets.push(target);
      return;
    }
    grouped.set(key, {
      key,
      varietyName: target.varietyName,
      targets: [target],
    });
  });
  return [...grouped.values()];
}
