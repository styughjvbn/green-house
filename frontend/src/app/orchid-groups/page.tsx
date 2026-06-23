import { OrchidManagementMap } from "@/components/orchid-group/orchid-management-map";
import { fetchApi } from "@/lib/api";
import type { FarmStatusMapData, House } from "@/types/farm";

type OrchidGroupsPageProps = {
  searchParams: Promise<{
    houseId?: string;
  }>;
};

export const dynamic = "force-dynamic";

export default async function OrchidGroupsPage({ searchParams }: OrchidGroupsPageProps) {
  const params = await searchParams;
  const mapData = await fetchApi<FarmStatusMapData>("/farm-status/map");
  const requestedHouseId = Number(params.houseId);
  const defaultHouse = mapData.houses.find((house) => house.orchidGroupCount > 0) ?? mapData.houses[0];
  const selectedHouseId = Number.isFinite(requestedHouseId) && requestedHouseId > 0 ? requestedHouseId : defaultHouse?.houseId;
  const house = selectedHouseId ? await fetchApi<House>(`/houses/${selectedHouseId}`) : null;

  if (!house) {
    return (
      <main className="space-y-6">
        <p className="text-sm font-semibold text-[#3d6f91]">난 묶음 관리</p>
        <h1 className="text-3xl font-semibold">동 상세맵 작업 화면</h1>
        <div className="rounded-md border border-[#d7ddd4] bg-white p-8 text-[#5c6a60]">표시할 동 데이터가 없습니다.</div>
      </main>
    );
  }

  return (
    <main className="space-y-8">
      <section className="flex flex-col gap-4 rounded-md border border-[#d7ddd4] bg-white p-5 shadow-sm lg:flex-row lg:items-center lg:justify-between">
        <div>
          <p className="text-sm font-semibold text-[#3d6f91]">난 묶음 관리</p>
          <h1 className="mt-2 text-3xl font-semibold">동 상세맵 작업 화면</h1>
          <p className="mt-2 max-w-3xl text-lg text-[#5c6a60]">
            선택한 동의 상세 지도를 확인하고 난 묶음 위치를 조회합니다. 데이터 변경은 다음 단계에서 연결합니다.
          </p>
        </div>
        <div className="flex flex-wrap gap-2 rounded-md border border-[#d7ddd4] bg-[#f8faf7] px-4 py-3 text-base">
          <span><span className="mr-2 text-[#159447]">●</span>정상</span>
          <span><span className="mr-2 text-[#f59e0b]">●</span>주의</span>
          <span><span className="mr-2 text-[#e52d2d]">●</span>이상</span>
        </div>
      </section>
      <OrchidManagementMap mapData={mapData} house={house} />
    </main>
  );
}
