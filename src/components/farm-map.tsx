import type { Bed, House, OrchidGroup } from '@/lib/farm-data';

type FarmMapProps = {
  houses: House[];
  orchidGroups: OrchidGroup[];
  selectedHouseId: number;
  selectedBedId: number;
  onSelectHouse: (houseId: number) => void;
  onSelectBed: (bedId: number) => void;
};

const warningStatuses = ['약함', '신아 약함', '신아 폐사 의심', '뿌리 약함', '병해충 의심', '폐기 예정'];

function bedStatusTone(bed: Bed, groups: OrchidGroup[]) {
  const bedGroups = groups.filter((group) => group.bedId === bed.id);
  if (bedGroups.some((group) => warningStatuses.includes(group.status))) return 'border-orange-500 bg-orange-50 text-orange-900';
  if (bedGroups.some((group) => group.status === '판매 가능')) return 'border-emerald-500 bg-emerald-50 text-emerald-900';
  if (bedGroups.length > 0) return 'border-sky-500 bg-sky-50 text-sky-900';
  return 'border-slate-200 bg-slate-50 text-slate-500';
}

export function FarmMap({ houses, orchidGroups, selectedHouseId, selectedBedId, onSelectHouse, onSelectBed }: FarmMapProps) {
  return (
    <section className="rounded-[2rem] border border-emerald-100 bg-white p-5 shadow-sm">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-sm font-bold text-emerald-700">농장 맵 · 전체 보기</p>
          <h2 className="text-2xl font-black text-slate-950">15개 동 / 45개 배드</h2>
        </div>
        <div className="flex rounded-full bg-slate-100 p-1 text-sm font-bold text-slate-600">
          <span className="rounded-full bg-emerald-600 px-4 py-2 text-white">전체</span>
          <span className="px-4 py-2">동</span>
          <span className="px-4 py-2">배드</span>
          <span className="px-4 py-2">세부구역</span>
        </div>
      </div>
      <div className="grid grid-cols-1 gap-4 md:grid-cols-3 xl:grid-cols-5 print:grid-cols-5">
        {houses.map((house) => {
          const selectedHouse = selectedHouseId === house.id;
          return (
            <button
              type="button"
              key={house.id}
              onClick={() => onSelectHouse(house.id)}
              className={`rounded-3xl border-2 p-3 text-left transition hover:-translate-y-0.5 hover:shadow-lg ${selectedHouse ? 'border-emerald-600 bg-emerald-50' : 'border-slate-200 bg-slate-50'}`}
            >
              <div className="mb-3 flex items-center justify-between">
                <strong className="text-xl font-black text-slate-900">{house.name}</strong>
                <span className="rounded-full bg-white px-2 py-1 text-xs font-bold text-slate-500">배드 3</span>
              </div>
              <div className="grid h-48 grid-cols-3 gap-2">
                {house.beds.map((bed) => {
                  const selectedBed = selectedBedId === bed.id;
                  const groups = orchidGroups.filter((group) => group.bedId === bed.id);
                  return (
                    <span
                      role="button"
                      tabIndex={0}
                      key={bed.id}
                      onClick={(event) => {
                        event.stopPropagation();
                        onSelectHouse(house.id);
                        onSelectBed(bed.id);
                      }}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') onSelectBed(bed.id);
                      }}
                      className={`flex h-full flex-col items-center justify-between rounded-2xl border-2 px-1 py-3 text-center text-sm font-black transition ${selectedBed ? 'border-blue-700 bg-blue-600 text-white shadow-lg ring-4 ring-blue-100' : bedStatusTone(bed, orchidGroups)}`}
                    >
                      <span className="writing-vertical">배드{bed.number}</span>
                      <span className="rounded-full bg-white/80 px-2 py-1 text-[11px] text-slate-700">{groups.reduce((sum, group) => sum + group.quantity, 0)}분</span>
                    </span>
                  );
                })}
              </div>
            </button>
          );
        })}
      </div>
    </section>
  );
}
