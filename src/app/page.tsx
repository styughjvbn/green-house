'use client';

import { useMemo, useState } from 'react';
import { BedDetailPanel } from '@/components/bed-detail-panel';
import { FarmMap } from '@/components/farm-map';
import { OrchidGroupForm, SalesSlipForm, WorkRecordForm } from '@/components/forms';
import { currency, findBed, getBedId, getLatestWorkDate, houses, initialCustomers, initialOrchidGroups, initialSalesSlips, initialWorkRecords, type OrchidGroup, type SalesSlip, type WorkRecord } from '@/lib/farm-data';

const menus = ['대시보드', '판매 관리', '농장 현황', '작업 이력', '품종 관리', '출력', '설정'];

export default function Home() {
  const [selectedHouseId, setSelectedHouseId] = useState(3);
  const [selectedBedId, setSelectedBedId] = useState(getBedId(3, 2));
  const [orchidGroups, setOrchidGroups] = useState(initialOrchidGroups);
  const [workRecords, setWorkRecords] = useState(initialWorkRecords);
  const [salesSlips, setSalesSlips] = useState(initialSalesSlips);
  const [editingGroup, setEditingGroup] = useState<OrchidGroup | null>(null);
  const [filter, setFilter] = useState('전체');

  const selectedBed = findBed(selectedBedId) ?? houses[2].beds[1];
  const selectedGroups = orchidGroups.filter((group) => group.bedId === selectedBed.id);
  const totalPlants = orchidGroups.reduce((sum, group) => sum + group.quantity, 0);
  const repottingDue = orchidGroups.filter((group) => group.ageYear >= 3 || group.status.includes('뿌리')).length;
  const abnormalGroups = orchidGroups.filter((group) => !['정상', '개화중', '판매 가능'].includes(group.status));
  const recentWorkDate = [...workRecords].sort((a, b) => b.workDate.localeCompare(a.workDate))[0]?.workDate ?? '기록 없음';
  const filteredGroups = filter === '전체' ? orchidGroups : orchidGroups.filter((group) => group.status === filter || group.genus === filter || group.potSize === filter);

  const monthlySales = useMemo(() => salesSlips.reduce((sum, slip) => sum + slip.items.reduce((itemSum, item) => itemSum + item.amount, 0), 0), [salesSlips]);

  function saveGroup(group: Omit<OrchidGroup, 'id' | 'createdAt' | 'updatedAt'> & { id?: number }) {
    const now = '2026-06-19';
    if (group.id) {
      setOrchidGroups((items) => items.map((item) => item.id === group.id ? { ...item, ...group, updatedAt: now } : item));
      setEditingGroup(null);
      return;
    }
    setOrchidGroups((items) => [{ ...group, id: Date.now(), createdAt: now, updatedAt: now }, ...items]);
  }

  function deleteGroup(groupId: number) {
    setOrchidGroups((items) => items.filter((item) => item.id !== groupId));
  }

  function saveWorkRecord(record: Omit<WorkRecord, 'id' | 'createdAt' | 'updatedAt'>) {
    setWorkRecords((items) => [{ ...record, id: Date.now(), createdAt: '2026-06-19', updatedAt: '2026-06-19' }, ...items]);
  }

  function saveSalesSlip(slip: Omit<SalesSlip, 'id'>) {
    setSalesSlips((items) => [{ ...slip, id: Date.now() }, ...items]);
  }

  return (
    <main className="min-h-screen bg-[#f3f8f4] text-slate-900">
      <div className="flex min-h-screen">
        <aside className="hidden w-64 shrink-0 bg-emerald-950 p-6 text-white lg:block print:hidden">
          <div className="mb-10 rounded-3xl bg-white/10 p-4">
            <p className="text-sm font-bold text-emerald-200">Green House</p>
            <h1 className="text-2xl font-black">난 농장 관리</h1>
          </div>
          <nav className="space-y-2">
            {menus.map((menu, index) => <a key={menu} href={`#section-${index}`} className={`block rounded-2xl px-4 py-4 text-lg font-black ${index === 0 ? 'bg-emerald-500 text-emerald-950' : 'text-emerald-50 hover:bg-white/10'}`}>{menu}</a>)}
          </nav>
        </aside>

        <div className="flex-1 p-4 md:p-8 print:p-0">
          <header className="mb-6 rounded-[2rem] bg-gradient-to-br from-emerald-800 to-emerald-600 p-6 text-white shadow-sm print:hidden">
            <p className="text-sm font-bold text-emerald-100">문서 기반 MVP · PC/태블릿 우선</p>
            <div className="mt-2 flex flex-wrap items-end justify-between gap-4">
              <div>
                <h1 className="text-3xl font-black md:text-5xl">동 → 배드 → 난 묶음 관리</h1>
                <p className="mt-3 max-w-3xl text-lg text-emerald-50">15개 동과 45개 배드를 위에서 내려다보는 맵으로 선택하고, 재배 현황·작업 이력·판매 전표·출력까지 한 화면에서 처리합니다.</p>
              </div>
              <button onClick={() => window.print()} className="rounded-2xl bg-white px-5 py-4 text-lg font-black text-emerald-800">전체 출력</button>
            </div>
          </header>

          <section id="section-0" className="mb-6 grid gap-4 md:grid-cols-2 xl:grid-cols-4 print:grid-cols-4">
            <SummaryCard title="전체 동 / 배드" value="15동 / 45배드" helper="초기 기본 데이터" />
            <SummaryCard title="분갈이 예정" value={`${repottingDue}건`} helper="3년차 이상 또는 뿌리 약함" tone="orange" />
            <SummaryCard title="최근 작업일" value={recentWorkDate} helper={`농약 ${getLatestWorkDate(workRecords, selectedBed, '농약')}`} />
            <SummaryCard title="상태 이상" value={`${abnormalGroups.length}건`} helper={abnormalGroups.map((group) => group.varietyName).slice(0, 2).join(', ') || '없음'} tone="red" />
          </section>

          <div className="grid gap-6 xl:grid-cols-[1fr_420px]">
            <FarmMap houses={houses} orchidGroups={orchidGroups} selectedHouseId={selectedHouseId} selectedBedId={selectedBedId} onSelectHouse={setSelectedHouseId} onSelectBed={setSelectedBedId} />
            <BedDetailPanel bed={selectedBed} groups={selectedGroups} workRecords={workRecords} onEditGroup={setEditingGroup} onDeleteGroup={deleteGroup} onPrint={() => window.print()} />
          </div>

          <section id="section-2" className="mt-6 grid gap-6 xl:grid-cols-2 print:hidden">
            <OrchidGroupForm key={editingGroup?.id ?? selectedBed.id} selectedBed={selectedBed} editingGroup={editingGroup} onSubmit={saveGroup} onCancelEdit={() => setEditingGroup(null)} />
            <WorkRecordForm selectedBed={selectedBed} onSubmit={saveWorkRecord} />
          </section>

          <section id="section-3" className="mt-6 rounded-[2rem] border border-emerald-100 bg-white p-5 shadow-sm">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
              <div><p className="text-sm font-bold text-emerald-700">최근 작업 이력</p><h2 className="text-2xl font-black text-slate-950">작업 유형/대상별 기록</h2></div>
              <span className="rounded-full bg-slate-100 px-4 py-2 text-sm font-bold text-slate-600">최근 작업일 자동 계산</span>
            </div>
            <div className="overflow-auto">
              <table className="w-full min-w-[760px] text-left text-base">
                <thead><tr className="border-b text-slate-500"><th className="py-3">작업일</th><th>유형</th><th>대상</th><th>약제/비료</th><th>희석/수량</th><th>작업자</th><th>메모</th></tr></thead>
                <tbody>{workRecords.map((record) => <tr key={record.id} className="border-b border-slate-100"><td className="py-3 font-bold">{record.workDate}</td><td>{record.workType}</td><td>{record.targetType} {record.targetId ?? ''}</td><td>{record.materialName || '-'}</td><td>{record.dilutionRatio || '-'} / {record.quantity || '-'}</td><td>{record.worker || '-'}</td><td>{record.memo || '-'}</td></tr>)}</tbody>
              </table>
            </div>
          </section>

          <section id="section-1" className="mt-6 grid gap-6 xl:grid-cols-[1fr_1.2fr] print:hidden">
            <SalesSlipForm onSubmit={saveSalesSlip} />
            <div className="rounded-[2rem] border border-emerald-100 bg-white p-5 shadow-sm">
              <div className="mb-4 flex items-center justify-between gap-3"><div><p className="text-sm font-bold text-emerald-700">판매 관리</p><h2 className="text-2xl font-black text-slate-950">전표 목록</h2></div><strong className="text-xl text-emerald-700">월 매출 {currency(monthlySales)}원</strong></div>
              <div className="space-y-3">{salesSlips.map((slip) => <article key={slip.id} className="rounded-2xl border border-slate-200 p-4"><div className="flex flex-wrap justify-between gap-3"><div><strong className="text-lg">{slip.slipNumber}</strong><p className="text-slate-500">{initialCustomers.find((customer) => customer.id === slip.customerId)?.name} · {slip.saleDate}</p></div><span className="rounded-full bg-emerald-100 px-3 py-1 text-sm font-black text-emerald-800">{slip.paymentStatus}</span></div>{slip.items.map((item) => <p key={item.id} className="mt-2 text-slate-700">{item.itemName} / {item.spec} / {item.quantity}개 × {currency(item.unitPrice)}원 = <b>{currency(item.amount)}원</b></p>)}<button onClick={() => window.print()} className="mt-3 rounded-xl bg-slate-100 px-3 py-2 text-sm font-bold">전표 출력</button></article>)}</div>
            </div>
          </section>

          <section id="section-4" className="mt-6 rounded-[2rem] border border-emerald-100 bg-white p-5 shadow-sm print:hidden">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3"><div><p className="text-sm font-bold text-emerald-700">농장 현황</p><h2 className="text-2xl font-black text-slate-950">품종/상태/화분 크기 필터</h2></div><select value={filter} onChange={(event) => setFilter(event.target.value)} className="rounded-2xl border border-slate-200 bg-white px-4 py-3 text-lg font-bold"><option>전체</option><option>카틀레야</option><option>덴드로비움</option><option>판매 가능</option><option>신아 약함</option><option>뿌리 약함</option><option>4치</option><option>5치</option></select></div>
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">{filteredGroups.map((group) => <article key={group.id} className="rounded-2xl bg-slate-50 p-4"><strong>{group.houseNumber}동 {group.bedNumber}번 배드 · {group.varietyName}</strong><p className="text-slate-600">{group.genus} / {group.potSize} / {group.quantity}분 / {group.status}</p></article>)}</div>
          </section>

          <section id="section-5" className="mt-6 rounded-[2rem] border border-emerald-100 bg-white p-5 shadow-sm">
            <h2 className="mb-3 text-2xl font-black text-slate-950">출력용 배드 관리표</h2>
            <p className="mb-4 text-slate-600 print:hidden">브라우저 출력 시 메뉴와 입력 폼은 숨기고, 선택 배드 관리표 중심으로 출력됩니다.</p>
            <div className="rounded-2xl border border-slate-200 p-4">
              <h3 className="text-xl font-black">{selectedBed.houseNumber}동 {selectedBed.number}번 배드 관리표</h3>
              <p>최근 농약: {getLatestWorkDate(workRecords, selectedBed, '농약')} / 최근 비료: {getLatestWorkDate(workRecords, selectedBed, '비료')} / 최근 분갈이: {getLatestWorkDate(workRecords, selectedBed, '분갈이')}</p>
              <ul className="mt-3 list-disc pl-6">{selectedGroups.map((group) => <li key={group.id}>{group.varietyName} / {group.quantity}분 / {group.status} / {group.memo}</li>)}</ul>
            </div>
          </section>
        </div>
      </div>
    </main>
  );
}

function SummaryCard({ title, value, helper, tone = 'emerald' }: { title: string; value: string; helper: string; tone?: 'emerald' | 'orange' | 'red' }) {
  const toneClass = tone === 'orange' ? 'bg-orange-50 text-orange-800' : tone === 'red' ? 'bg-red-50 text-red-800' : 'bg-white text-emerald-800';
  return <article className={`rounded-[1.5rem] border border-emerald-100 p-5 shadow-sm ${toneClass}`}><p className="font-bold opacity-80">{title}</p><strong className="mt-2 block text-3xl font-black text-slate-950">{value}</strong><span className="mt-2 block text-sm font-bold opacity-80">{helper}</span></article>;
}
