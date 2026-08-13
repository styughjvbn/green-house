import { fetchApi, requestApi } from "@/shared/api/client";
import type {
  FarmStatusMapData,
  FarmStatusOrchidGroupList,
  FarmStatusTargetType,
  FarmStatusZoomData,
  OrchidGroup,
} from "@/entities/farm/types";

export async function fetchFarmStatusOrchidGroups(
  type: FarmStatusTargetType,
  id: number,
  signal?: AbortSignal,
): Promise<FarmStatusOrchidGroupList> {
  return requestApi<FarmStatusOrchidGroupList>(
    `/farm-status/orchid-groups?targetType=${type}&targetId=${id}`,
    { signal },
    "선택한 범위의 난 묶음을 불러오지 못했습니다.",
  );
}

export async function fetchFarmStatusHouseZoom(
  houseId: number,
  signal?: AbortSignal,
): Promise<FarmStatusZoomData> {
  return requestApi<FarmStatusZoomData>(
    `/farm-status/zoom?level=HOUSE&houseId=${houseId}`,
    { signal },
    "선택한 동의 맵 정보를 불러오지 못했습니다.",
  );
}

export function getFarmStatusMap() {
  return fetchApi<FarmStatusMapData>("/farm-status/map");
}

export function getFarmStatusOrchidGroups(
  type: FarmStatusTargetType,
  id: number,
) {
  return fetchApi<FarmStatusOrchidGroupList>(
    `/farm-status/orchid-groups?targetType=${type}&targetId=${id}`,
  );
}

export function getFarmStatusHouseZoom(houseId: number) {
  return fetchApi<FarmStatusZoomData>(
    `/farm-status/zoom?level=HOUSE&houseId=${houseId}`,
  );
}

export function searchFarmStatusOrchidGroups({
  keyword,
  status,
}: {
  keyword: string;
  status: string;
}) {
  const params = new URLSearchParams();
  if (keyword.trim()) {
    params.set("keyword", keyword.trim());
  }
  if (status.trim()) {
    params.set("status", status.trim());
  }

  const query = params.toString();
  return fetchApi<OrchidGroup[]>(`/orchid-groups${query ? `?${query}` : ""}`);
}
