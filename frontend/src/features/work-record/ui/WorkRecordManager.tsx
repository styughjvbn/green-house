"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import type { WorkRecordManagerProps } from "../model/types";
import { deriveWorkTargetSelectionOptions } from "../model/workTargetSelectionOptions";
import { WorkOperationRegistrationDialog } from "./components/WorkOperationRegistrationDialog";
import { WorkWorkspacePage } from "./WorkWorkspacePage";

export function WorkRecordManager(props: WorkRecordManagerProps) {
  const router = useRouter();
  const targetOptions = useMemo(
    () => deriveWorkTargetSelectionOptions(props.houses),
    [props.houses],
  );
  const [showOperationForm, setShowOperationForm] = useState(false);
  const [operationInitialTypeCode, setOperationInitialTypeCode] = useState<
    string | null
  >(null);
  const [operationSavedVersion, setOperationSavedVersion] = useState(0);

  return (
    <div className="flex h-full min-h-0 flex-col gap-4">
      {showOperationForm ? (
        <WorkOperationRegistrationDialog
          houses={props.houses}
          initialWorkTypeCode={operationInitialTypeCode}
          workTypes={props.workTypes}
          onClose={() => setShowOperationForm(false)}
          onSaved={() => {
            setOperationSavedVersion((current) => current + 1);
            router.refresh();
          }}
        />
      ) : null}

      <WorkWorkspacePage
        bedZones={targetOptions.bedZones}
        houses={props.houses}
        orchidGroups={targetOptions.orchidGroups}
        refreshKey={operationSavedVersion}
        onCreateWork={() => {
          setOperationInitialTypeCode(null);
          setShowOperationForm(true);
        }}
      />
    </div>
  );
}
