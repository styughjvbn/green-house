import Link from "next/link";
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

  return (
    <main className="space-y-8">
      <section>
        <p className="text-sm font-semibold text-[#3d6f91]">난 묶음 관리</p>
        <h1 className="mt-2 text-3xl font-semibold">동 상세맵 작업 화면</h1>
        <p className="mt-3 max-w-3xl text-lg text-[#5c6a60]">
          선택한 동의 3개 물리 배드와 좌우 논리 구역을 확인합니다. 생성, 수정, 이동은 다음 단계에서 연결합니다.
        </p>
      </section>
      <section className="grid gap-6 xl:grid-cols-[280px_minmax(0,1fr)]">
        <aside className="rounded-md border border-[#d7ddd4] bg-white p-4 shadow-sm">
          <h2 className="text-xl font-semibold">전체 동 보기</h2>
          <div className="mt-4 grid grid-cols-3 gap-2 xl:grid-cols-2">
            {mapData.houses.map((houseSummary) => {
              const selected = houseSummary.houseId === selectedHouseId;
              return (
                <Link
                  key={houseSummary.houseId}
                  href={`/orchid-groups?houseId=${houseSummary.houseId}`}
                  className={`min-h-20 rounded-md border p-3 text-center transition ${
                    selected ? "border-[#159447] bg-[#eef7ec] ring-2 ring-[#159447]" : "border-[#d7ddd4] bg-white hover:border-[#159447]"
                  }`}
                >
                  <p className="text-lg font-semibold">{houseSummary.houseNumber}동</p>
                  <p className="mt-2 text-sm text-[#5c6a60]">
                    <span className={houseSummary.warningCount > 0 ? "text-[#f59e0b]" : "text-[#159447]"}>●</span>{" "}
                    {houseSummary.warningCount > 0 ? "주의" : "정상"}
                  </p>
                </Link>
              );
            })}
          </div>
        </aside>
        <section className="rounded-md border border-[#d7ddd4] bg-white p-5 shadow-sm">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="text-sm font-semibold text-[#3d6f91]">선택한 동</p>
              <h2 className="mt-1 text-2xl font-semibold">{house ? `${house.number}동 상세 보기` : "동 없음"}</h2>
            </div>
            <div className="rounded-md bg-[#e7f0e6] px-4 py-2 text-base font-semibold text-[#214f31]">실제 방향 보기</div>
          </div>
          <div className="mt-6 grid gap-4 lg:grid-cols-3">
            {house?.physicalBeds.map((bed) => (
              <div key={bed.id} className="rounded-md border border-[#d7ddd4] bg-[#f8faf7] p-4">
                <p className="text-center text-xl font-semibold">{bed.number}배드</p>
                <div className="mt-4 grid grid-cols-2 gap-3">
                  {bed.bedZones.map((zone) => (
                    <div key={zone.id} className="min-h-72 rounded-md border border-[#d7ddd4] bg-white p-3">
                      <p className="text-center text-lg font-semibold">{zone.side === "LEFT" ? "좌" : "우"}</p>
                      <div className="mt-3 space-y-2">
                        {zone.orchidGroups.map((orchidGroup) => (
                          <div key={orchidGroup.id} className="rounded-md border border-[#8bc58e] bg-[#cfe8ca] p-3">
                            <p className="font-semibold">{orchidGroup.varietyName}</p>
                            <p className="mt-1 text-sm">{orchidGroup.quantity}분</p>
                          </div>
                        ))}
                        {zone.orchidGroups.length === 0 ? (
                          <div className="rounded-md border border-dashed border-[#d7ddd4] bg-[#f3f4f2] p-3 text-center text-[#7b867d]">
                            빈 공간
                          </div>
                        ) : null}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </section>
      </section>
    </main>
  );
}
