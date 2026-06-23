import { FarmStatusMap } from "@/components/farm-map/farm-status-map";
import { fetchApi } from "@/lib/api";
import type { FarmStatusMapData, FarmStatusOrchidGroupList, FarmStatusZoomData } from "@/types/farm";

export const dynamic = "force-dynamic";

export default async function FarmStatusPage() {
  const mapData = await fetchApi<FarmStatusMapData>("/farm-status/map");
  const firstHouse = mapData.houses[0];
  const initialSelection = firstHouse
    ? await fetchApi<FarmStatusOrchidGroupList>(`/farm-status/orchid-groups?targetType=HOUSE&targetId=${firstHouse.houseId}`)
    : null;
  const initialZoom = firstHouse
    ? await fetchApi<FarmStatusZoomData>(`/farm-status/zoom?level=HOUSE&houseId=${firstHouse.houseId}`)
    : null;

  return (
    <main className="space-y-8">
      <section>
        <p className="text-sm font-semibold text-[#3d6f91]">농장 현황</p>
        <h1 className="mt-2 text-3xl font-semibold">전체 농장맵 조회</h1>
        <p className="mt-3 max-w-3xl text-lg text-[#5c6a60]">
          동, 물리 배드, 논리 구역을 선택해 포함된 난 묶음 목록을 확인합니다. 이 화면에서는 데이터를 변경하지 않습니다.
        </p>
      </section>
      <FarmStatusMap mapData={mapData} initialSelection={initialSelection} initialZoom={initialZoom} />
    </main>
  );
}
