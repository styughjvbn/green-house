import { requestApi } from "@/shared/api/client";
import type { WorkType, WorkTypeTemplate } from "@/entities/farm/types";

type WorkTypePayload = {
  name: string;
  template: WorkTypeTemplate;
  active?: boolean;
};

export async function getSettingWorkTypes() {
  return request<WorkType[]>("/work-types?includeInactive=true");
}

export function getWorkTypeMetadata() {
  return request<{ customTypeTemplates: WorkTypeTemplate[] }>(
    "/work-types/metadata",
  );
}

export async function createSettingWorkType(payload: WorkTypePayload) {
  return request<WorkType>("/work-types", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export async function updateSettingWorkType(
  workType: WorkType,
  payload: WorkTypePayload,
) {
  return request<WorkType>(`/work-types/${workType.id}`, {
    method: "PATCH",
    body: JSON.stringify({
      name: payload.name,
      template: payload.template,
      active: payload.active ?? workType.active,
    }),
  });
}

export async function reorderSettingWorkTypes(orderedIds: number[]) {
  return request<WorkType[]>("/work-types/reorder", {
    method: "PATCH",
    body: JSON.stringify({ orderedIds }),
  });
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  return requestApi<T>(
    path,
    {
      ...init,
      headers: {
        "Content-Type": "application/json",
        ...init?.headers,
      },
    },
    "작업 유형 요청을 처리하지 못했습니다.",
  );
}
