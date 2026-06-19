import React, { useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { AlertTriangle, BarChart3, CalendarDays, CheckCircle2, ClipboardList, Droplets, Flower2, Home, Package, Plus, Search, Sprout, ThermometerSun } from 'lucide-react';
import './styles.css';

const initialLots = [
  { id: 'ORC-2401', variety: '호접란 핑크레이디', house: 'A동', stage: '개화', quantity: 420, health: 94, temp: 24.2, humidity: 68, lastWatered: '2026-06-18', nextTask: '출하 선별', notes: '꽃대 2대 이상 비율 높음' },
  { id: 'ORC-2402', variety: '심비디움 골드문', house: 'B동', stage: '생장', quantity: 310, health: 86, temp: 23.1, humidity: 71, lastWatered: '2026-06-17', nextTask: '액비 관주', notes: '신아 발생 양호' },
  { id: 'ORC-2403', variety: '덴드로비움 스노우', house: 'C동', stage: '순화', quantity: 180, health: 78, temp: 25.4, humidity: 64, lastWatered: '2026-06-16', nextTask: '병해 확인', notes: '일부 잎 반점 관찰' },
  { id: 'ORC-2404', variety: '카틀레야 루비', house: 'A동', stage: '육묘', quantity: 260, health: 91, temp: 24.8, humidity: 66, lastWatered: '2026-06-18', nextTask: '분갈이 준비', notes: '근권 활착 안정' },
];

const initialTasks = [
  { id: 1, title: 'A동 호접란 출하 선별', due: '오늘', owner: '김관리', status: '진행중', priority: '높음' },
  { id: 2, title: 'B동 EC/pH 측정 및 액비 기록', due: '오늘', owner: '이재배', status: '대기', priority: '보통' },
  { id: 3, title: 'C동 잎 반점 샘플 촬영', due: '내일', owner: '박방제', status: '대기', priority: '높음' },
  { id: 4, title: '주간 출하 거래처별 수량 확정', due: '금요일', owner: '최출하', status: '완료', priority: '보통' },
];

const initialShipments = [
  { buyer: '서울화훼 공판장', variety: '호접란 핑크레이디', qty: 120, date: '2026-06-20', status: '포장 대기' },
  { buyer: '부산 난 직매장', variety: '카틀레야 루비', qty: 45, date: '2026-06-22', status: '예약' },
  { buyer: '온라인 스토어', variety: '덴드로비움 스노우', qty: 32, date: '2026-06-21', status: '검수 필요' },
];

function StatCard({ icon: Icon, label, value, hint, tone = 'green' }) {
  return <article className={`stat-card ${tone}`}><div className="stat-icon"><Icon size={22}/></div><div><p>{label}</p><strong>{value}</strong><span>{hint}</span></div></article>;
}

function App() {
  const [lots, setLots] = useState(initialLots);
  const [tasks, setTasks] = useState(initialTasks);
  const [query, setQuery] = useState('');
  const [form, setForm] = useState({ variety: '', house: 'A동', stage: '육묘', quantity: 0 });

  const filteredLots = lots.filter(lot => [lot.id, lot.variety, lot.house, lot.stage].join(' ').toLowerCase().includes(query.toLowerCase()));
  const summary = useMemo(() => ({ total: lots.reduce((sum, lot) => sum + Number(lot.quantity), 0), avgHealth: Math.round(lots.reduce((sum, lot) => sum + lot.health, 0) / lots.length), alerts: lots.filter(lot => lot.health < 80).length, ready: lots.filter(lot => lot.stage === '개화').reduce((sum, lot) => sum + lot.quantity, 0) }), [lots]);

  const addLot = event => {
    event.preventDefault();
    if (!form.variety.trim() || Number(form.quantity) <= 0) return;
    const newLot = { id: `ORC-${2400 + lots.length + 1}`, variety: form.variety.trim(), house: form.house, stage: form.stage, quantity: Number(form.quantity), health: 88, temp: 24, humidity: 67, lastWatered: '2026-06-19', nextTask: '초기 점검', notes: '신규 등록 로트' };
    setLots([newLot, ...lots]);
    setForm({ variety: '', house: 'A동', stage: '육묘', quantity: 0 });
  };

  const completeTask = id => setTasks(tasks.map(task => task.id === id ? { ...task, status: '완료' } : task));

  return <div className="app-shell">
    <aside className="sidebar"><div className="brand"><Flower2/><div><strong>OrchidFarm</strong><span>MVP Console</span></div></div><nav><a className="active"><Home size={18}/> 대시보드</a><a><Sprout size={18}/> 재배 로트</a><a><ClipboardList size={18}/> 작업 관리</a><a><Package size={18}/> 출하 관리</a><a><BarChart3 size={18}/> 리포트</a></nav></aside>
    <main>
      <header className="hero"><div><p className="eyebrow">난 농장 통합 관리</p><h1>재배·작업·출하를 한 화면에서 관리하세요</h1><p>온실별 생육 상태, 오늘의 작업, 예정 출하를 빠르게 확인하고 신규 로트를 등록할 수 있는 MVP입니다.</p></div><button><Plus size={18}/> 빠른 등록</button></header>
      <section className="stats"><StatCard icon={Sprout} label="총 재배 수량" value={`${summary.total.toLocaleString()}주`} hint="등록 로트 기준"/><StatCard icon={CheckCircle2} label="평균 건강도" value={`${summary.avgHealth}%`} hint="80% 미만 알림"/><StatCard icon={AlertTriangle} label="주의 로트" value={`${summary.alerts}건`} hint="병해/환경 확인" tone="amber"/><StatCard icon={Package} label="출하 가능" value={`${summary.ready.toLocaleString()}주`} hint="개화 단계" tone="blue"/></section>
      <section className="grid two"><div className="panel"><div className="panel-head"><div><h2>재배 로트 현황</h2><p>품종, 온실, 생육 단계별 검색</p></div><label className="search"><Search size={16}/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="로트/품종/온실 검색"/></label></div><div className="lot-list">{filteredLots.map(lot => <article className="lot-card" key={lot.id}><div><strong>{lot.variety}</strong><span>{lot.id} · {lot.house} · {lot.stage}</span></div><div className="metrics"><span><Sprout size={14}/>{lot.quantity}주</span><span><ThermometerSun size={14}/>{lot.temp}℃</span><span><Droplets size={14}/>{lot.humidity}%</span></div><div className="progress"><i style={{width:`${lot.health}%`}}/></div><p><b>다음 작업:</b> {lot.nextTask} · <b>메모:</b> {lot.notes}</p></article>)}</div></div>
      <div className="panel"><h2>신규 로트 등록</h2><p>입식 또는 분갈이 후 로트 정보를 등록합니다.</p><form onSubmit={addLot} className="lot-form"><input value={form.variety} onChange={e=>setForm({...form, variety:e.target.value})} placeholder="품종명 예: 호접란 화이트"/><select value={form.house} onChange={e=>setForm({...form, house:e.target.value})}><option>A동</option><option>B동</option><option>C동</option><option>격리실</option></select><select value={form.stage} onChange={e=>setForm({...form, stage:e.target.value})}><option>육묘</option><option>순화</option><option>생장</option><option>개화</option></select><input type="number" value={form.quantity} onChange={e=>setForm({...form, quantity:e.target.value})} placeholder="수량"/><button type="submit"><Plus size={16}/> 로트 추가</button></form></div></section>
      <section className="grid two"><div className="panel"><h2>오늘의 작업</h2><div className="task-list">{tasks.map(task => <article key={task.id} className={`task ${task.status === '완료' ? 'done' : ''}`}><div><strong>{task.title}</strong><span>{task.due} · 담당 {task.owner} · 우선순위 {task.priority}</span></div><button onClick={()=>completeTask(task.id)}>{task.status}</button></article>)}</div></div><div className="panel"><h2>출하 예정</h2><div className="shipment-list">{initialShipments.map((ship, index) => <article key={index}><CalendarDays size={18}/><div><strong>{ship.buyer}</strong><span>{ship.date} · {ship.variety} {ship.qty}주</span></div><em>{ship.status}</em></article>)}</div></div></section>
    </main>
  </div>;
}

createRoot(document.getElementById('root')).render(<App />);
