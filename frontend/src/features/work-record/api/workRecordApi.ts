import { API_BASE_URL } from "@/shared/api/client";
import type {
  BedZone,
  OrchidGroup,
  PhysicalBed,
  WorkRecord,
} from "@/entities/farm/types";
import type {
  CreateWorkRecordPayload,
  WorkRecordTargetOptions,
} from "../model/types";

export async function getWorkRecordTargetOptions(
  houseId: string,
): Promise<WorkRecordTargetOptions> {
  const [bedsResponse, zonesResponse, groupsResponse] = await Promise.all([
    fetch(`${API_BASE_URL}/physical-beds?houseId=${houseId}`, {
      cache: "no-store",
      credentials: "include",
    }),
    fetch(`${API_BASE_URL}/bed-zones?houseId=${houseId}`, {
      cache: "no-store",
      credentials: "include",
    }),
    fetch(`${API_BASE_URL}/orchid-groups?houseId=${houseId}`, {
      cache: "no-store",
      credentials: "include",
    }),
  ]);

  if (!bedsResponse.ok || !zonesResponse.ok || !groupsResponse.ok) {
    throw new Error("대상 목록을 불러오지 못했습니다.");
  }

  const [bedsPayload, zonesPayload, groupsPayload] = await Promise.all([
    bedsResponse.json(),
    zonesResponse.json(),
    groupsResponse.json(),
  ]);

  return {
    physicalBeds: (bedsPayload.data ?? []) as PhysicalBed[],
    bedZones: (zonesPayload.data ?? []) as BedZone[],
    orchidGroups: (groupsPayload.data ?? []) as OrchidGroup[],
  };
}

export async function createWorkRecord(
  payload: CreateWorkRecordPayload,
): Promise<WorkRecord> {
  const response = await fetch(`${API_BASE_URL}/work-records`, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });

  const body = await response.json();

  if (!response.ok) {
    throw new Error(body?.error?.message ?? "작업 이력을 저장하지 못했습니다.");
  }

  return body.data as WorkRecord;
}
