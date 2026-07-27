"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { TabError } from "@/shared/ui/TabLayout";
import {
  workHousesQueryOptions,
  workTypesQueryOptions,
} from "../model/workRecordQueryOptions";
import { useWorkRecordInvalidation } from "../model/useWorkRecordInvalidation";
import { WorkOperationRegistrationDialog } from "./registration/WorkOperationRegistrationDialog";
import { WorkWorkspacePage } from "./WorkWorkspacePage";

export function WorkRecordWorkspace() {
  const { invalidateWorkData } = useWorkRecordInvalidation();
  const [showOperationForm, setShowOperationForm] = useState(false);
  const housesQuery = useQuery({
    ...workHousesQueryOptions(),
    enabled: showOperationForm,
  });
  const workTypesQuery = useQuery(workTypesQueryOptions());
  const houses = housesQuery.data ?? [];
  const workTypes = workTypesQuery.data ?? [];
  const referenceError = housesQuery.error ?? workTypesQuery.error;

  return (
    <div className="flex h-full min-h-0 flex-col gap-4">
      <TabError
        message={
          referenceError instanceof Error ? referenceError.message : null
        }
      />

      {showOperationForm && housesQuery.data != null ? (
        <WorkOperationRegistrationDialog
          houses={houses}
          workTypes={workTypes}
          onClose={() => setShowOperationForm(false)}
          onSaved={() => void invalidateWorkData()}
        />
      ) : null}
      {showOperationForm && housesQuery.data == null ? (
        <div
          className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 p-4"
          role="presentation"
          onMouseDown={() => setShowOperationForm(false)}
        >
          <section
            aria-label="작업 등록 정보"
            aria-modal="true"
            className="w-full max-w-sm rounded-md bg-white p-5 shadow-xl"
            role="dialog"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <p className="text-sm font-semibold text-[#26352b]">
              {housesQuery.error
                ? "작업 등록 정보를 불러오지 못했습니다."
                : "작업 등록 정보를 불러오는 중입니다."}
            </p>
            <div className="mt-4 flex justify-end gap-2">
              <button
                className="rounded-md border border-[#cfd8cc] px-3 py-2 text-sm font-semibold text-[#435047]"
                type="button"
                onClick={() => setShowOperationForm(false)}
              >
                닫기
              </button>
              {housesQuery.error ? (
                <button
                  className="rounded-md bg-[#159447] px-3 py-2 text-sm font-semibold text-white"
                  type="button"
                  onClick={() => void housesQuery.refetch()}
                >
                  다시 시도
                </button>
              ) : null}
            </div>
          </section>
        </div>
      ) : null}

      <WorkWorkspacePage onCreateWork={() => setShowOperationForm(true)} />
    </div>
  );
}
