import { fetchApi } from "@/shared/api/client";
import type {
  DashboardSummary,
  FarmStatusMapData,
  FarmStatusOrchidGroupList,
  FarmStatusTargetType,
  FarmStatusZoomData,
} from "@/entities/farm/types";

export async function getDashboardSummary() {
  return fetchApi<DashboardSummary>("/dashboard/summary");
}

export async function getFarmStatusMap() {
  return fetchApi<FarmStatusMapData>("/farm-status/map");
}

export async function getFarmStatusOrchidGroups(targetType: FarmStatusTargetType, targetId: number) {
  return fetchApi<FarmStatusOrchidGroupList>(`/farm-status/orchid-groups?targetType=${targetType}&targetId=${targetId}`);
}

export async function getFarmStatusHouseZoom(houseId: number) {
  return fetchApi<FarmStatusZoomData>(`/farm-status/zoom?level=HOUSE&houseId=${houseId}`);
}
