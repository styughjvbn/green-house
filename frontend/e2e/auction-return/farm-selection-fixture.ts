import type { Page } from "@playwright/test";

export const selectionGroups = [99, 100, 101, 102].map((id, index) => ({
  id,
  bedZoneId: 6,
  varietyId: id === 101 ? 10 : 9,
  varietyName: id === 101 ? "다른 품종" : "수납 테스트 품종",
  genus: "호접란",
  quantity: id === 100 ? 50 : 100,
  status: "정상",
  color: null,
  potSize: "3호",
  potSizeCode: "3",
  ageYear: 1,
  placementType: "POT",
  trayCount: null,
  startPosition: index * 2 + 1,
  endPosition: index * 2 + 2,
  sortOrder: index,
  houseId: 1,
  houseNumber: 1,
  physicalBedNumber: 1,
  bedZoneName: "반환 구역",
  memo: null,
}));

export async function installFarmSelectionFixture(page: Page) {
  const bed = {
    id: 2,
    houseId: 1,
    houseNumber: 1,
    number: 1,
    displayOrder: 1,
    lengthCm: 100,
    widthCm: 100,
    wireCount: 1,
    supportIntervalCm: 10,
    positionUnitCount: 10,
    positionUnitLabel: "칸",
    memo: null,
    bedZones: [
      {
        id: 6,
        physicalBedId: 2,
        physicalBedNumber: 1,
        houseId: 1,
        houseNumber: 1,
        name: "반환 구역",
        side: "LEFT",
        zoneType: "NORMAL",
        sortOrder: 1,
        active: true,
        memo: null,
        orchidGroups: selectionGroups,
      },
    ],
  };
  const send = (data: unknown) => ({
    json: { success: true, data, message: null },
  });
  await page.route("**/api/houses", (route) =>
    route.fulfill(
      send([
        {
          id: 1,
          number: 1,
          name: "선택 테스트 농장",
          memo: null,
          physicalBeds: [bed],
        },
      ]),
    ),
  );
  await page.route("**/api/farm-status/orchid-management?**", (route) =>
    route.fulfill(send({ beds: [bed], bedOrder: [], totalBeds: 1 })),
  );
  await page.route("**/api/farm-status/orchid-management/bed-order", (route) =>
    route.fulfill(send([{ id: 2, houseId: 1, houseNumber: 1, number: 1 }])),
  );
}
