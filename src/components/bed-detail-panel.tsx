import { getLatestWorkDate, type Bed, type OrchidGroup, type WorkRecord, workTypes } from '@/lib/farm-data';

type BedDetailPanelProps = {
  bed: Bed;
  groups: OrchidGroup[];
  workRecords: WorkRecord[];
  onEditGroup: (group: OrchidGroup) => void;
  onDeleteGroup: (groupId: number) => void;
  onPrint: () => void;
};

export function BedDetailPanel({ bed, groups, workRecords, onEditGroup, onDeleteGroup, onPrint }: BedDetailPanelProps) {
  const abnormal = groups.filter((group) => !['정상', '개화중', '판매 가능'].includes(group.status));
  return (
    <aside className="rounded-[2rem] border border-emerald-100 bg-white p-5 shadow-sm xl:sticky xl:top-6">
      <div className="mb-5 border-b border-slate-100 pb-4">
        <p className="text-sm font-bold text-emerald-700">선택 구역</p>
        <h2 className="text-2xl font-black text-slate-950">{bed.houseNumber}동 - {bed.number}번 배드</h2>
        <p className="mt-2 text-slate-600">난 묶음 {groups.length}개 · 총 {groups.reduce((sum, group) => sum + group.quantity, 0).toLocaleString()}분</p>
      </div>

      <div className="mb-5 grid grid-cols-1 gap-2 sm:grid-cols-3 xl:grid-cols-1 2xl:grid-cols-3">
        {workTypes.slice(0, 3).map((workType) => (
          <div key={workType} className="rounded-2xl bg-slate-50 p-3">
            <p className="text-xs font-bold text-slate-500">최근 {workType}</p>
            <strong className="text-lg text-slate-950">{getLatestWorkDate(workRecords, bed, workType)}</strong>
          </div>
        ))}
      </div>

      {abnormal.length > 0 && (
        <div className="mb-5 rounded-2xl border border-orange-200 bg-orange-50 p-4 text-orange-900">
          <strong>상태 이상 {abnormal.length}건</strong>
          <p className="text-sm">{abnormal.map((group) => `${group.varietyName}(${group.status})`).join(', ')}</p>
        </div>
      )}

      <div className="mb-5 flex flex-wrap gap-2 print:hidden">
        <a href="#group-form" className="rounded-2xl bg-emerald-600 px-4 py-3 text-base font-black text-white">난 묶음 추가</a>
        <a href="#work-form" className="rounded-2xl bg-slate-900 px-4 py-3 text-base font-black text-white">작업 기록 추가</a>
        <button type="button" onClick={onPrint} className="rounded-2xl border border-slate-300 px-4 py-3 text-base font-black text-slate-800">출력</button>
      </div>

      <div className="space-y-3">
        <h3 className="text-lg font-black text-slate-950">재배 난 목록</h3>
        {groups.length === 0 ? <p className="rounded-2xl bg-slate-50 p-4 text-slate-500">등록된 난 묶음이 없습니다.</p> : groups.map((group) => (
          <article key={group.id} className="rounded-2xl border border-slate-200 p-4">
            <div className="mb-2 flex items-start justify-between gap-3">
              <div>
                <strong className="text-lg text-slate-950">{group.varietyName}</strong>
                <p className="text-sm text-slate-500">{group.genus} / {group.potSize} / {group.quantity}분 / {group.ageYear}년차</p>
              </div>
              <span className={`rounded-full px-3 py-1 text-xs font-black ${['정상', '개화중', '판매 가능'].includes(group.status) ? 'bg-emerald-100 text-emerald-800' : 'bg-orange-100 text-orange-800'}`}>{group.status}</span>
            </div>
            <p className="mb-3 text-sm text-slate-600">{group.memo}</p>
            <div className="flex gap-2 print:hidden">
              <button type="button" onClick={() => onEditGroup(group)} className="rounded-xl bg-slate-100 px-3 py-2 text-sm font-bold">수정</button>
              <button type="button" onClick={() => onDeleteGroup(group.id)} className="rounded-xl bg-red-50 px-3 py-2 text-sm font-bold text-red-700">삭제</button>
            </div>
          </article>
        ))}
      </div>
    </aside>
  );
}
