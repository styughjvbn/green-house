"use client";

import { useMemo, useState } from "react";
import type {
  House,
  OrchidGroup,
  WorkOperationTarget,
  WorkType,
} from "@/entities/farm/types";
import type { FarmPlacementReference } from "@/entities/farm/model/placement";
import type { CreateWorkOperationPayload } from "../../../model/types";
import type {
  StructureChangeExecutionPayload,
  StructureChangeRecordPayload,
} from "../../../api/workRecordApi";
import { StructureChangeExecutionDialog } from "./StructureChangeExecutionDialog";
import { movementDiscardConfirmation } from "../../../model/work-types/structure-change/useStructureChangeExecution";
import {
  sourceReferencePlacements,
  type ResultRow,
} from "../../../model/work-types/structure-change/structureChangeExecutionModel";

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
  const [resultRowsByGroup, setResultRowsByGroup] = useState<
    Map<string, ResultRow[]>
  >(new Map());
  const [saving, setSaving] = useState(false);
  const [isDirty, setIsDirty] = useState(false);

  if (groups.length === 0) return null;

  function requestClose() {
    if (
      !isDirty ||
      window.confirm("작성 중인 내용이 초기화됩니다. 닫을까요?")
    ) {
      onClose();
    }
  }

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
        sourceDerivedGroupKey: undefined,
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
    if (workType.code === "MOVEMENT") {
      const discardQuantity = [...records.values()].reduce(
        (sum, record) =>
          sum +
          Math.max(
            0,
            record.execution.sources.reduce(
              (sourceSum, source) => sourceSum + source.inputQuantity,
              0,
            ) -
              record.execution.results.reduce(
                (resultSum, result) => resultSum + result.quantity,
                0,
              ),
          ),
        0,
      );
      if (
        discardQuantity > 0 &&
        !window.confirm(movementDiscardConfirmation(discardQuantity))
      ) {
        return;
      }
    }
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
      onMouseDown={() => {
        if (!isDirty) onClose();
      }}
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
          hiddenOtherVarietySourceIds={groups.flatMap((relatedGroup) => {
            if (relatedGroup.key === group.key) return [];
            return configuredSourceIds(
              relatedGroup,
              resultRowsByGroup.get(relatedGroup.key) ?? [],
            );
          })}
          otherVarietyReferences={groups.flatMap(
            (relatedGroup): FarmPlacementReference[] => {
              if (relatedGroup.key === group.key) return [];
              return otherVarietyPlacementReferences(
                relatedGroup,
                resultRowsByGroup.get(relatedGroup.key) ?? [],
                orchidGroups,
              );
            },
          )}
          recordNavigation={{
            activeKey,
            allCompleted: records.size === groups.length,
            items: navigationItems,
            saving,
            onSave: saveAll,
            onSelect: setActiveKey,
          }}
          onClose={requestClose}
          onRecordDirty={() => {
            setIsDirty(true);
            setRecords((current) => {
              if (!current.has(group.key)) return current;
              const next = new Map(current);
              next.delete(group.key);
              return next;
            });
          }}
          onResultRowsChange={(rows) => {
            setResultRowsByGroup((current) => {
              if (current.get(group.key) === rows) return current;
              const next = new Map(current);
              next.set(group.key, rows);
              return next;
            });
          }}
          onSubmitRecord={async (execution) => {
            setIsDirty(true);
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

function configuredSourceIds(
  group: VarietyTargetGroup,
  rows: ResultRow[],
): number[] {
  return group.targets.flatMap((target) => {
    const sourceId = target.orchidGroupId;
    if (sourceId == null) return [];
    const sourceRows = rows.filter((row) =>
      row.sourceOrchidGroupIds.includes(sourceId),
    );
    return sourceRows.length > 0 &&
      sourceRows.every(
        (row) => row.placement != null && row.placementConfigured,
      )
      ? [sourceId]
      : [];
  });
}

function otherVarietyPlacementReferences(
  group: VarietyTargetGroup,
  rows: ResultRow[],
  orchidGroups: OrchidGroup[],
): FarmPlacementReference[] {
  const configuredSourceIdSet = new Set(configuredSourceIds(group, rows));
  const sourceReferences = sourceReferencePlacements(
    group.targets.flatMap((target) => {
      if (
        target.orchidGroupId == null ||
        configuredSourceIdSet.has(target.orchidGroupId)
      ) {
        return [];
      }
      const source = orchidGroups.find(
        (orchidGroup) => orchidGroup.id === target.orchidGroupId,
      );
      return source ? [{ group: source }] : [];
    }),
  ).map(
    (reference): FarmPlacementReference => ({
      ...reference,
      kind: "OTHER_VARIETY_SOURCE",
    }),
  );
  const resultReferences = rows.flatMap(
    (row, index): FarmPlacementReference[] =>
      row.placement != null && row.placementConfigured
        ? [
            {
              ...row.placement,
              label: `${group.varietyName} · 결과 ${index + 1} · ${Number(row.quantity || 0).toLocaleString()}분`,
              kind: "OTHER_VARIETY_RESULT",
            },
          ]
        : [],
  );
  return [...sourceReferences, ...resultReferences];
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
