export type WorkType = '농약' | '비료' | '분갈이' | '상태 기록' | '일반 메모';
export type TargetType = 'FARM' | 'HOUSE' | 'BED' | 'ORCHID_GROUP';
export type PaymentStatus = '미입금' | '일부 입금' | '입금 완료';

export type Bed = { id: number; houseId: number; houseNumber: number; number: number; memo?: string };
export type House = { id: number; number: number; name: string; beds: Bed[] };
export type OrchidGroup = { id: number; bedId: number; houseNumber: number; bedNumber: number; genus: string; varietyName: string; quantity: number; potSize: string; ageYear: number; status: string; memo: string; createdAt: string; updatedAt: string };
export type WorkRecord = { id: number; workType: WorkType; workDate: string; targetType: TargetType; targetId?: number; materialName?: string; dilutionRatio?: string; quantity?: string; worker?: string; memo?: string; createdAt: string; updatedAt: string };
export type Customer = { id: number; name: string; ownerName: string; phone: string; address: string; memo: string };
export type SalesSlipItem = { id: number; itemName: string; genus: string; spec: string; quantity: number; unitPrice: number; amount: number; memo?: string };
export type SalesSlip = { id: number; slipNumber: string; saleDate: string; customerId: number; paymentStatus: PaymentStatus; paymentMethod: string; memo: string; items: SalesSlipItem[] };

export const houses: House[] = Array.from({ length: 15 }, (_, houseIndex) => {
  const houseNumber = houseIndex + 1;
  return {
    id: houseNumber,
    number: houseNumber,
    name: `${houseNumber}동`,
    beds: Array.from({ length: 3 }, (_, bedIndex) => ({
      id: houseIndex * 3 + bedIndex + 1,
      houseId: houseNumber,
      houseNumber,
      number: bedIndex + 1,
      memo: bedIndex === 1 && houseNumber === 3 ? '샘플 데이터 배드' : undefined,
    })),
  };
});

export const initialOrchidGroups: OrchidGroup[] = [
  { id: 1, bedId: 8, houseNumber: 3, bedNumber: 2, genus: '카틀레야', varietyName: '카틀레야 A', quantity: 120, potSize: '4치', ageYear: 2, status: '정상', memo: '대표 샘플 묶음', createdAt: '2026-06-01', updatedAt: '2026-06-10' },
  { id: 2, bedId: 8, houseNumber: 3, bedNumber: 2, genus: '카틀레야', varietyName: '카틀레야 B', quantity: 80, potSize: '5치', ageYear: 3, status: '개화중', memo: '꽃대 확인 필요', createdAt: '2026-06-01', updatedAt: '2026-06-12' },
  { id: 3, bedId: 8, houseNumber: 3, bedNumber: 2, genus: '덴드로비움', varietyName: '덴드로비움 C', quantity: 200, potSize: '3.5치', ageYear: 1, status: '판매 가능', memo: '이번 주 출하 가능', createdAt: '2026-06-01', updatedAt: '2026-06-15' },
  { id: 4, bedId: 1, houseNumber: 1, bedNumber: 1, genus: '기타', varietyName: '호접란 화이트', quantity: 90, potSize: '4치', ageYear: 2, status: '신아 약함', memo: '관찰 필요', createdAt: '2026-06-03', updatedAt: '2026-06-16' },
  { id: 5, bedId: 15, houseNumber: 5, bedNumber: 3, genus: '덴드로비움', varietyName: '덴드로비움 핑크', quantity: 140, potSize: '3치', ageYear: 1, status: '뿌리 약함', memo: '과습 주의', createdAt: '2026-06-05', updatedAt: '2026-06-17' },
  { id: 6, bedId: 31, houseNumber: 11, bedNumber: 1, genus: '카틀레야', varietyName: '카틀레야 골드', quantity: 60, potSize: '5치', ageYear: 4, status: '판매 가능', memo: '거래처 문의 있음', createdAt: '2026-06-08', updatedAt: '2026-06-18' },
];

export const initialWorkRecords: WorkRecord[] = [
  { id: 1, workType: '농약', workDate: '2026-06-01', targetType: 'BED', targetId: 8, materialName: '살균제 A', dilutionRatio: '1000배', quantity: '20L', worker: '아버지', memo: '정기 방제', createdAt: '2026-06-01', updatedAt: '2026-06-01' },
  { id: 2, workType: '비료', workDate: '2026-05-20', targetType: 'BED', targetId: 8, materialName: '액비 B', dilutionRatio: '2000배', quantity: '15L', worker: '관리자', memo: '생육 촉진', createdAt: '2026-05-20', updatedAt: '2026-05-20' },
  { id: 3, workType: '분갈이', workDate: '2026-03-12', targetType: 'BED', targetId: 8, quantity: '120분', worker: '어머니', memo: '카틀레야 A 중심', createdAt: '2026-03-12', updatedAt: '2026-03-12' },
  { id: 4, workType: '농약', workDate: '2026-06-10', targetType: 'HOUSE', targetId: 1, materialName: '살충제 C', dilutionRatio: '1500배', quantity: '40L', worker: '아버지', memo: '1동 전체 살포', createdAt: '2026-06-10', updatedAt: '2026-06-10' },
  { id: 5, workType: '상태 기록', workDate: '2026-06-18', targetType: 'ORCHID_GROUP', targetId: 5, worker: '관리자', memo: '뿌리 약함 기록', createdAt: '2026-06-18', updatedAt: '2026-06-18' },
];

export const initialCustomers: Customer[] = [
  { id: 1, name: '서울화훼', ownerName: '김대표', phone: '010-1111-2222', address: '서울 양재동', memo: '월 정산' },
  { id: 2, name: '부산난원', ownerName: '박사장', phone: '010-3333-4444', address: '부산 사상구', memo: '카틀레야 선호' },
];

export const initialSalesSlips: SalesSlip[] = [
  { id: 1, slipNumber: 'S-202606-001', saleDate: '2026-06-18', customerId: 1, paymentStatus: '미입금', paymentMethod: '계좌이체', memo: '출력 후 전달', items: [
    { id: 1, itemName: '덴드로비움 C', genus: '덴드로비움', spec: '3.5치', quantity: 20, unitPrice: 12000, amount: 240000 },
    { id: 2, itemName: '카틀레야 B', genus: '카틀레야', spec: '5치', quantity: 10, unitPrice: 18000, amount: 180000 },
  ] },
];

export const workTypes: WorkType[] = ['농약', '비료', '분갈이', '상태 기록', '일반 메모'];
export const statuses = ['정상', '약함', '신아 약함', '신아 폐사 의심', '뿌리 약함', '병해충 의심', '개화중', '판매 가능', '폐기 예정'];
export const genera = ['카틀레야', '덴드로비움', '기타'];

export function getBedId(houseNumber: number, bedNumber: number) {
  return (houseNumber - 1) * 3 + bedNumber;
}

export function findBed(bedId: number) {
  return houses.flatMap((house) => house.beds).find((bed) => bed.id === bedId);
}

export function getRelevantRecords(records: WorkRecord[], bed: Bed, orchidGroupId?: number) {
  return records.filter((record) => {
    if (record.targetType === 'FARM') return true;
    if (record.targetType === 'HOUSE') return record.targetId === bed.houseNumber;
    if (record.targetType === 'BED') return record.targetId === bed.id;
    if (record.targetType === 'ORCHID_GROUP') return orchidGroupId ? record.targetId === orchidGroupId : false;
    return false;
  });
}

export function getLatestWorkDate(records: WorkRecord[], bed: Bed, workType: WorkType) {
  const dates = getRelevantRecords(records, bed)
    .filter((record) => record.workType === workType)
    .map((record) => record.workDate)
    .sort()
    .reverse();
  return dates[0] ?? '기록 없음';
}

export function currency(value: number) {
  return new Intl.NumberFormat('ko-KR').format(value);
}
