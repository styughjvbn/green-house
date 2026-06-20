'use client';

import { useState } from 'react';
import { genera, statuses, workTypes, type Bed, type OrchidGroup, type SalesSlip, type WorkRecord } from '@/lib/farm-data';

type GroupFormProps = { selectedBed: Bed; editingGroup?: OrchidGroup | null; onSubmit: (group: Omit<OrchidGroup, 'id' | 'createdAt' | 'updatedAt'> & { id?: number }) => void; onCancelEdit: () => void };

export function OrchidGroupForm({ selectedBed, editingGroup, onSubmit, onCancelEdit }: GroupFormProps) {
  const [form, setForm] = useState({ genus: editingGroup?.genus ?? '카틀레야', varietyName: editingGroup?.varietyName ?? '', quantity: editingGroup?.quantity ?? 1, potSize: editingGroup?.potSize ?? '4치', ageYear: editingGroup?.ageYear ?? 1, status: editingGroup?.status ?? '정상', memo: editingGroup?.memo ?? '' });

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form.varietyName.trim()) return;
    onSubmit({ id: editingGroup?.id, bedId: selectedBed.id, houseNumber: selectedBed.houseNumber, bedNumber: selectedBed.number, ...form, quantity: Number(form.quantity), ageYear: Number(form.ageYear) });
    setForm({ genus: '카틀레야', varietyName: '', quantity: 1, potSize: '4치', ageYear: 1, status: '정상', memo: '' });
  }

  return (
    <form id="group-form" onSubmit={submit} className="rounded-[2rem] border border-emerald-100 bg-white p-5 shadow-sm">
      <div className="mb-4">
        <p className="text-sm font-bold text-emerald-700">{selectedBed.houseNumber}동 {selectedBed.number}번 배드</p>
        <h2 className="text-2xl font-black text-slate-950">난 묶음 {editingGroup ? '수정' : '등록'}</h2>
      </div>
      <div className="grid gap-3 md:grid-cols-2">
        <label className="field">속<select value={form.genus} onChange={(e) => setForm({ ...form, genus: e.target.value })}>{genera.map((genus) => <option key={genus}>{genus}</option>)}</select></label>
        <label className="field">품종명<input value={form.varietyName} onChange={(e) => setForm({ ...form, varietyName: e.target.value })} placeholder="카틀레야 A" /></label>
        <label className="field">수량<input type="number" min="1" value={form.quantity} onChange={(e) => setForm({ ...form, quantity: Number(e.target.value) })} /></label>
        <label className="field">화분 크기<input value={form.potSize} onChange={(e) => setForm({ ...form, potSize: e.target.value })} placeholder="4치" /></label>
        <label className="field">연차<input type="number" min="0" value={form.ageYear} onChange={(e) => setForm({ ...form, ageYear: Number(e.target.value) })} /></label>
        <label className="field">상태<select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })}>{statuses.map((status) => <option key={status}>{status}</option>)}</select></label>
        <label className="field md:col-span-2">메모<textarea value={form.memo} onChange={(e) => setForm({ ...form, memo: e.target.value })} placeholder="현장 메모" /></label>
      </div>
      <div className="mt-4 flex gap-2">
        <button className="primary-button" type="submit">{editingGroup ? '수정 저장' : '묶음 등록'}</button>
        {editingGroup && <button className="secondary-button" type="button" onClick={onCancelEdit}>취소</button>}
      </div>
    </form>
  );
}

type WorkRecordFormProps = { selectedBed: Bed; onSubmit: (record: Omit<WorkRecord, 'id' | 'createdAt' | 'updatedAt'>) => void };

export function WorkRecordForm({ selectedBed, onSubmit }: WorkRecordFormProps) {
  const [form, setForm] = useState({ workType: '농약', workDate: '2026-06-19', targetType: 'BED', materialName: '', dilutionRatio: '', quantity: '', worker: '', memo: '' });
  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit({ workType: form.workType as WorkRecord['workType'], workDate: form.workDate, targetType: form.targetType as WorkRecord['targetType'], targetId: form.targetType === 'FARM' ? undefined : form.targetType === 'HOUSE' ? selectedBed.houseNumber : selectedBed.id, materialName: form.materialName, dilutionRatio: form.dilutionRatio, quantity: form.quantity, worker: form.worker, memo: form.memo });
    setForm({ workType: '농약', workDate: '2026-06-19', targetType: 'BED', materialName: '', dilutionRatio: '', quantity: '', worker: '', memo: '' });
  }
  return (
    <form id="work-form" onSubmit={submit} className="rounded-[2rem] border border-emerald-100 bg-white p-5 shadow-sm">
      <div className="mb-4"><p className="text-sm font-bold text-emerald-700">10초 입력용</p><h2 className="text-2xl font-black text-slate-950">작업 이력 등록</h2></div>
      <div className="grid gap-3 md:grid-cols-2">
        <label className="field">작업 유형<select value={form.workType} onChange={(e) => setForm({ ...form, workType: e.target.value })}>{workTypes.map((type) => <option key={type}>{type}</option>)}</select></label>
        <label className="field">작업일<input type="date" value={form.workDate} onChange={(e) => setForm({ ...form, workDate: e.target.value })} /></label>
        <label className="field">대상<select value={form.targetType} onChange={(e) => setForm({ ...form, targetType: e.target.value })}><option value="FARM">전체 농장</option><option value="HOUSE">선택 동 전체</option><option value="BED">선택 배드</option></select></label>
        <label className="field">작업자<input value={form.worker} onChange={(e) => setForm({ ...form, worker: e.target.value })} placeholder="작업자" /></label>
        <label className="field">약제/비료명<input value={form.materialName} onChange={(e) => setForm({ ...form, materialName: e.target.value })} placeholder="살균제 A" /></label>
        <label className="field">희석/농도<input value={form.dilutionRatio} onChange={(e) => setForm({ ...form, dilutionRatio: e.target.value })} placeholder="1000배" /></label>
        <label className="field">수량<input value={form.quantity} onChange={(e) => setForm({ ...form, quantity: e.target.value })} placeholder="20L / 120분" /></label>
        <label className="field">메모<input value={form.memo} onChange={(e) => setForm({ ...form, memo: e.target.value })} placeholder="간단 메모" /></label>
      </div>
      <button className="primary-button mt-4" type="submit">작업 기록 저장</button>
    </form>
  );
}

type SalesSlipFormProps = { onSubmit: (slip: Omit<SalesSlip, 'id'>) => void };

export function SalesSlipForm({ onSubmit }: SalesSlipFormProps) {
  const [form, setForm] = useState({ customerId: 1, itemName: '', genus: '카틀레야', spec: '4치', quantity: 1, unitPrice: 10000, paymentStatus: '미입금', paymentMethod: '계좌이체', memo: '' });
  const amount = Number(form.quantity) * Number(form.unitPrice);
  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form.itemName.trim()) return;
    const slipNumber = `S-${new Date().toISOString().slice(0, 10).replaceAll('-', '')}-${Date.now().toString().slice(-3)}`;
    onSubmit({ slipNumber, saleDate: '2026-06-19', customerId: Number(form.customerId), paymentStatus: form.paymentStatus as SalesSlip['paymentStatus'], paymentMethod: form.paymentMethod, memo: form.memo, items: [{ id: Date.now(), itemName: form.itemName, genus: form.genus, spec: form.spec, quantity: Number(form.quantity), unitPrice: Number(form.unitPrice), amount }] });
    setForm({ customerId: 1, itemName: '', genus: '카틀레야', spec: '4치', quantity: 1, unitPrice: 10000, paymentStatus: '미입금', paymentMethod: '계좌이체', memo: '' });
  }
  return (
    <form onSubmit={submit} className="rounded-[2rem] border border-emerald-100 bg-white p-5 shadow-sm">
      <div className="mb-4"><p className="text-sm font-bold text-emerald-700">수기 전표 디지털화</p><h2 className="text-2xl font-black text-slate-950">판매 전표 등록</h2></div>
      <div className="grid gap-3 md:grid-cols-3">
        <label className="field">거래처<select value={form.customerId} onChange={(e) => setForm({ ...form, customerId: Number(e.target.value) })}><option value={1}>서울화훼</option><option value={2}>부산난원</option></select></label>
        <label className="field">상품명<input value={form.itemName} onChange={(e) => setForm({ ...form, itemName: e.target.value })} /></label>
        <label className="field">속<select value={form.genus} onChange={(e) => setForm({ ...form, genus: e.target.value })}>{genera.map((genus) => <option key={genus}>{genus}</option>)}</select></label>
        <label className="field">규격<input value={form.spec} onChange={(e) => setForm({ ...form, spec: e.target.value })} /></label>
        <label className="field">수량<input type="number" min="1" value={form.quantity} onChange={(e) => setForm({ ...form, quantity: Number(e.target.value) })} /></label>
        <label className="field">단가<input type="number" min="0" value={form.unitPrice} onChange={(e) => setForm({ ...form, unitPrice: Number(e.target.value) })} /></label>
        <label className="field">입금 상태<select value={form.paymentStatus} onChange={(e) => setForm({ ...form, paymentStatus: e.target.value })}><option>미입금</option><option>일부 입금</option><option>입금 완료</option></select></label>
        <label className="field">결제 방식<input value={form.paymentMethod} onChange={(e) => setForm({ ...form, paymentMethod: e.target.value })} /></label>
        <label className="field">금액<input readOnly value={`${amount.toLocaleString()}원`} /></label>
      </div>
      <button className="primary-button mt-4" type="submit">전표 저장</button>
    </form>
  );
}
