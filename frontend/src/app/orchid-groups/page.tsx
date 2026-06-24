import { OrchidManagementMap } from "@/features/orchid-management/ui/OrchidManagementMap";
import { fetchApi } from "@/shared/api/client";
import type { FarmStatusMapData, House } from "@/entities/farm/types";

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
      <main className="space-y-4">
        <p className="text-sm font-semibold text-[#3d6f91]">난 묶음 관리</p>
        <h1 className="text-2xl font-semibold">동 상세맵 작업 화면</h1>
        <div className="rounded-md border border-[#d7ddd4] bg-white p-5 text-sm text-[#5c6a60]">표시할 동 데이터가 없습니다.</div>
      </main>
    );
  }

  return (
    <main className="space-y-4">
      <section className="flex flex-col gap-3 rounded-md border border-[#d7ddd4] bg-white p-4 shadow-sm lg:flex-row lg:items-center lg:justify-between">
        <div>
          <p className="text-sm font-semibold text-[#3d6f91]">난 묶음 관리</p>
          <h1 className="mt-1 text-2xl font-semibold">동 상세맵 작업 화면</h1>
          <p className="mt-1 max-w-3xl text-sm text-[#5c6a60]">
            선택한 동의 상세 지도를 확인하고 난 묶음 위치를 조회합니다. 데이터 변경은 다음 단계에서 연결합니다.
          </p>
        </div>
        <div className="flex flex-wrap gap-2 rounded-md border border-[#d7ddd4] bg-[#f8faf7] px-3 py-2 text-sm">
          <span><span className="mr-2 text-[#159447]">●</span>정상</span>
          <span><span className="mr-2 text-[#f59e0b]">●</span>주의</span>
          <span><span className="mr-2 text-[#e52d2d]">●</span>이상</span>
        </div>
      </section>
      <OrchidManagementMap mapData={mapData} house={house} />
    </main>
  );
}
