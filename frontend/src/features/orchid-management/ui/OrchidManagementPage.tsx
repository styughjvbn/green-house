import type { FarmStatusMapData, House } from "@/entities/farm/types";
import { PageHeader } from "@/widgets/page-header";
import { OrchidManagementMap } from "./OrchidManagementMap";

type OrchidManagementPageProps = {
  mapData: FarmStatusMapData;
  house: House | null;
  workTypes: string[];
};

export function OrchidManagementPage({
  mapData,
  house,
  workTypes,
}: OrchidManagementPageProps) {
  if (!house) {
    return (
      <main className="space-y-4">
        <PageHeader title="난 묶음 관리" description="선택한 동의 상세 지도에서 난 묶음을 관리합니다." />
        <div className="rounded-md border border-[#d7ddd4] bg-white p-5 text-sm text-[#5c6a60]">
          표시할 동 데이터가 없습니다.
        </div>
      </main>
    );
  }

  return (
    <main className="space-y-4">
      <PageHeader title="난 묶음 관리" description="선택한 동의 상세 지도에서 난 묶음 위치와 작업 정보를 확인합니다." />
      <section className="flex justify-end">
        <div className="flex flex-wrap gap-2 rounded-md border border-[#d7ddd4] bg-white px-3 py-2 text-sm shadow-sm">
          <span>
            <span className="mr-2 text-[#159447]">●</span>정상
          </span>
          <span>
            <span className="mr-2 text-[#f59e0b]">●</span>주의
          </span>
          <span>
            <span className="mr-2 text-[#e52d2d]">●</span>이상
          </span>
        </div>
      </section>
      <OrchidManagementMap
        mapData={mapData}
        house={house}
        workTypes={workTypes}
      />
    </main>
  );
}
