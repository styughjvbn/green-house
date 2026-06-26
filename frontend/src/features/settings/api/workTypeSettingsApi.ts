import { API_BASE_URL } from "@/shared/api/client";
import type { WorkType, WorkTypeTemplate } from "@/entities/farm/types";

type WorkTypePayload = {
  name: string;
  template: WorkTypeTemplate;
  active?: boolean;
};

export async function getSettingWorkTypes() {
  return request<WorkType[]>("/work-types?includeInactive=true");
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
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...init?.headers,
    },
  });
  const body = await response.json();

  if (!response.ok) {
    throw new Error(body?.error?.message ?? "요청을 처리하지 못했습니다.");
  }

  return body.data as T;
}
