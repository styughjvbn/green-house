import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

async function main() {
  for (let houseNumber = 1; houseNumber <= 15; houseNumber += 1) {
    const house = await prisma.house.upsert({
      where: { number: houseNumber },
      update: {},
      create: { number: houseNumber, name: `${houseNumber}동` },
    });

    for (let bedNumber = 1; bedNumber <= 3; bedNumber += 1) {
      await prisma.bed.upsert({
        where: { houseId_number: { houseId: house.id, number: bedNumber } },
        update: {},
        create: { houseId: house.id, number: bedNumber },
      });
    }
  }

  const bed = await prisma.bed.findFirst({ where: { house: { number: 3 }, number: 2 } });
  if (!bed) return;

  const groups = [
    ['카틀레야', '카틀레야 A', 120, '4치', 2, '정상', '대표 샘플 묶음'],
    ['카틀레야', '카틀레야 B', 80, '5치', 3, '개화중', '꽃대 확인 필요'],
    ['덴드로비움', '덴드로비움 C', 200, '3.5치', 1, '판매 가능', '이번 주 출하 가능'],
  ] as const;

  for (const [genus, varietyName, quantity, potSize, ageYear, status, memo] of groups) {
    const exists = await prisma.orchidGroup.findFirst({ where: { bedId: bed.id, varietyName } });
    if (!exists) await prisma.orchidGroup.create({ data: { bedId: bed.id, genus, varietyName, quantity, potSize, ageYear, status, memo } });
  }

  const records = [
    ['농약', '2026-06-01', 'BED', bed.id, '살균제 A', '1000배', '20L', '아버지', '정기 방제'],
    ['비료', '2026-05-20', 'BED', bed.id, '액비 B', '2000배', '15L', '관리자', '생육 촉진'],
    ['분갈이', '2026-03-12', 'BED', bed.id, null, null, '120분', '어머니', '카틀레야 A 중심'],
  ] as const;

  for (const [workType, workDate, targetType, targetId, materialName, dilutionRatio, quantity, worker, memo] of records) {
    const exists = await prisma.workRecord.findFirst({ where: { workType, workDate: new Date(workDate), targetType, targetId } });
    if (!exists) await prisma.workRecord.create({ data: { workType, workDate: new Date(workDate), targetType, targetId, materialName, dilutionRatio, quantity, worker, memo } });
  }
}

main().finally(async () => prisma.$disconnect());
