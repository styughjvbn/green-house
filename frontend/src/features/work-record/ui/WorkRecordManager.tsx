"use client";

import { useState } from "react";
import type { WorkRecordManagerProps } from "../model/types";
import { useWorkRecordManager } from "../model/useWorkRecordManager";
import { WorkOperationPanel } from "./components/HouseWorkOperationPanel";
import { WorkWorkspacePage } from "./WorkWorkspacePage";

export function WorkRecordManager(props: WorkRecordManagerProps) {
  const manager = useWorkRecordManager();
  const [showOperationForm, setShowOperationForm] = useState(false);
  const [operationInitialTypeCode, setOperationInitialTypeCode] = useState<
    string | null
  >(null);
  const [operationSavedVersion, setOperationSavedVersion] = useState(0);
  const refreshKey = manager.operationCreatedVersion + operationSavedVersion;

  return (
    <div className="flex h-full min-h-0 flex-col gap-4">
      {manager.errorMessage ? (
        <div className="rounded-md border border-[#c25a3c] bg-[#fff1ec] p-3 text-sm text-[#8f2f19]">
          {manager.errorMessage}
        </div>
      ) : null}
      {showOperationForm ? (
        <WorkOperationPanel
          houses={props.houses}
          initialWorkTypeCode={operationInitialTypeCode}
          workTypes={props.workTypes}
          onClose={() => setShowOperationForm(false)}
          onSaved={() => {
            setOperationSavedVersion((current) => current + 1);
            manager.setOperationCreatedVersion((current) => current + 1);
          }}
        />
      ) : null}

      <WorkWorkspacePage
        bedZones={manager.bedZones}
        houses={props.houses}
        orchidGroups={manager.orchidGroups}
        refreshKey={refreshKey}
        onCreateWork={() => {
          setOperationInitialTypeCode(null);
          setShowOperationForm(true);
        }}
      />
    </div>
  );
}
