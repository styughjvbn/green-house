import { API_BASE_URL } from "@/shared/api/client";
import { fetchApi } from "@/shared/api/client";
import type {
  FarmStatusMapData,
  FarmStatusOrchidGroupList,
  FarmStatusTargetType,
  FarmStatusZoomData,
} from "@/entities/farm/types";

type ApiPayload<T> = {
  data: T;
};

async function readJson<T>(
  response: Response,
  fallbackMessage: string,
): Promise<T> {
  const body = (await response.json()) as
    | ApiPayload<T>
    | { error?: { message?: string } };

  if (!response.ok) {
    const message = "error" in body ? body.error?.message : undefined;
    throw new Error(message ?? fallbackMessage);
  }

  return (body as ApiPayload<T>).data;
}

export async function fetchFarmStatusOrchidGroups(
  type: FarmStatusTargetType,
  id: number,
): Promise<FarmStatusOrchidGroupList> {
  const response = await fetch(
    `${API_BASE_URL}/farm-status/orchid-groups?targetType=${type}&targetId=${id}`,
    { cache: "no-store", credentials: "include" },
  );

  return readJson<FarmStatusOrchidGroupList>(
    response,
    "선택한 범위의 난 묶음을 불러오지 못했습니다.",
  );
}

export async function fetchFarmStatusHouseZoom(
  houseId: number,
): Promise<FarmStatusZoomData> {
  const response = await fetch(
    `${API_BASE_URL}/farm-status/zoom?level=HOUSE&houseId=${houseId}`,
    {
      cache: "no-store",
      credentials: "include",
    },
  );

  return readJson<FarmStatusZoomData>(
    response,
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
