import { test, expect } from "@playwright/test";
import {
  installFarmSelectionFixture,
  selectionGroups,
} from "./farm-selection-fixture";

test.beforeEach(async ({ request, context, page }) => {
  await request.post("http://127.0.0.1:14100/__control", {
    data: { reset: true, allocationSeed: true },
  });
  await context.addCookies([
    {
      name: "JSESSIONID",
      value: "isolated-ui-fixture",
      url: "http://127.0.0.1:13100",
    },
  ]);
  await installFarmSelectionFixture(page);
});

test("house cards move the shared layout and search moves to the first matching house", async ({
  page,
}) => {
  const send = (data: unknown) => ({ json: { success: true, data } });
  const houses = [1, 2, 3].map((houseNumber) => ({
    id: houseNumber,
    number: houseNumber,
    physicalBeds: [1, 2, 3].map((bedNumber) => {
      const id = houseNumber * 10 + bedNumber;
      const zoneId = id * 10;
      const group =
        bedNumber === 1
          ? [
              {
                ...selectionGroups[0],
                id: houseNumber * 100,
                houseId: houseNumber,
                houseNumber,
                physicalBedNumber: bedNumber,
                bedZoneId: zoneId,
                varietyName:
                  houseNumber === 1
                    ? "첫번째 품종"
                    : `검색 대상 ${houseNumber === 2 ? "A" : "B"}`,
              },
            ]
          : [];
      return {
        id,
        houseId: houseNumber,
        houseNumber,
        number: bedNumber,
        positionUnitCount: 10,
        bedZones: [
          {
            id: zoneId,
            name: "앞",
            side: "LEFT",
            active: true,
            orchidGroups: group,
          },
        ],
      };
    }),
  }));
  const beds = houses.flatMap((house) => house.physicalBeds);
  const groups = beds.flatMap((bed) =>
    bed.bedZones.flatMap((zone) => zone.orchidGroups),
  );
  const bedOrder = beds.map((bed) => ({
    id: bed.id,
    houseId: bed.houseId,
    houseNumber: bed.houseNumber,
    number: bed.number,
  }));
  await page.route("**/api/houses", (route) => route.fulfill(send(houses)));
  await page.route("**/api/sales/orchid-groups/search**", (route) =>
    route.fulfill(
      send(
        groups.map((group) => ({
          ...group,
          reservedQuantity: 0,
          availableQuantity: group.quantity,
        })),
      ),
    ),
  );
  await page.route("**/api/farm-status/orchid-management/bed-order", (route) =>
    route.fulfill(send(bedOrder)),
  );
  await page.route("**/api/farm-status/orchid-management?**", (route) => {
    const startBedId = Number(
      new URL(route.request().url()).searchParams.get("startBedId"),
    );
    const startIndex = Math.max(
      0,
      beds.findIndex((bed) => bed.id === startBedId),
    );
    return route.fulfill(
      send({
        beds: beds.slice(startIndex, startIndex + 3),
        bedOrder,
        totalBeds: beds.length,
      }),
    );
  });

  await page.goto("/sales/slips");
  await page.getByRole("button", { name: "전표 작성", exact: true }).click();
  await page.getByRole("button", { name: "난 묶음 선택", exact: true }).click();
  const dialog = page.getByRole("dialog", {
    name: "난 묶음 선택",
    exact: true,
  });
  await expect(
    dialog.getByRole("textbox", { name: "난 묶음 검색" }),
  ).toHaveAttribute("placeholder", "위치 또는 품종 검색");
  const currentHouse = dialog.getByRole("combobox", { name: "동 이동" });
  await expect(currentHouse).toHaveValue("1");
  await dialog.getByRole("button", { name: "3동 배치 보기" }).click();
  await expect(currentHouse).toHaveValue("3");
  await expect(dialog.getByText("0묶음 · 0분", { exact: true })).toBeVisible();
  await dialog.getByRole("checkbox", { name: "1동 전체" }).check();
  await expect(currentHouse).toHaveValue("1");

  const search = dialog.getByRole("textbox", { name: "난 묶음 검색" });
  await search.fill("검색 대상");
  await expect(currentHouse).toHaveValue("2");
  await dialog.getByRole("button", { name: "3동 배치 보기" }).click();
  await expect(currentHouse).toHaveValue("3");
  await search.fill("검색 대상 A");
  await expect(currentHouse).toHaveValue("2");
});

test("sales creates variety-grouped items from a mixed selection and keeps the parent form", async ({
  page,
}) => {
  await page.route("**/api/sales/orchid-groups/search**", (route) =>
    route.fulfill({
      json: {
        success: true,
        data: selectionGroups.slice(0, 3).map((group) => ({
          ...group,
          reservedQuantity: group.id === 99 ? 20 : 0,
          availableQuantity: group.id === 99 ? 80 : group.quantity,
        })),
      },
    }),
  );
  await page.goto("/sales/slips");
  await page.getByRole("button", { name: "전표 작성", exact: true }).click();
  const form = page.getByRole("dialog", { name: "전표 작성", exact: true });
  const opener = form.getByRole("button", {
    name: "난 묶음 선택",
    exact: true,
  });
  await opener.first().click();
  const dialog = page.getByRole("dialog", {
    name: "난 묶음 선택",
    exact: true,
  });
  await expect(
    dialog.getByRole("button", { name: /수납 테스트 품종 80분/ }).first(),
  ).toBeVisible();
  await expect(dialog.getByRole("button", { name: /7칸 수납/ })).toHaveCount(0);
  await dialog
    .getByRole("button", { name: /수납 테스트 품종 80분/ })
    .first()
    .click();
  await dialog
    .getByRole("button", { name: /다른 품종/ })
    .first()
    .click();
  await dialog
    .getByRole("button", { name: /수납 테스트 품종 50분/ })
    .first()
    .click();
  await expect(
    dialog.getByText("3묶음 · 230분", { exact: true }),
  ).toBeVisible();
  await dialog.getByRole("button", { name: "대상 확정", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(opener).toHaveCount(1);
  await expect(
    form.getByText("다른 품종 / 호접란", { exact: true }).first(),
  ).toBeVisible();
  const variety = form.getByRole("region", {
    name: "수납 테스트 품종 / 호접란 품목",
  });
  await expect(variety).toHaveCount(1);
  await variety.getByRole("button", { name: "품목 분리" }).click();
  await expect(variety.locator("[data-sales-item-index]")).toHaveCount(2);
  const first = form.locator('[data-sales-item-index="0"]');
  const second = form.locator('[data-sales-item-index="1"]');
  const other = form.locator('[data-sales-item-index="2"]');
  await first.getByRole("spinbutton", { name: "수량", exact: true }).fill("90");
  await second
    .getByRole("spinbutton", { name: "수량", exact: true })
    .fill("40");
  await other.getByRole("spinbutton", { name: "수량", exact: true }).fill("30");
  await first.getByRole("button", { name: "배분 조정" }).click();
  await second.getByRole("button", { name: "배분 조정" }).click();
  await expect(
    first.getByRole("spinbutton", { name: "배분 수량" }).nth(0),
  ).toHaveValue("55");
  await expect(
    first.getByRole("spinbutton", { name: "배분 수량" }).nth(1),
  ).toHaveValue("35");
  await expect(
    second.getByRole("spinbutton", { name: "배분 수량" }).nth(0),
  ).toHaveValue("25");
  await expect(
    second.getByRole("spinbutton", { name: "배분 수량" }).nth(1),
  ).toHaveValue("15");
  await first.getByRole("spinbutton", { name: "배분 수량" }).nth(0).fill("50");
  await first.getByRole("spinbutton", { name: "배분 수량" }).nth(1).fill("40");
  await expect(
    second.getByRole("spinbutton", { name: "배분 수량" }).nth(0),
  ).toHaveValue("30");
  await expect(
    second.getByRole("spinbutton", { name: "배분 수량" }).nth(1),
  ).toHaveValue("10");
  await first.getByRole("button", { name: "자동 배분", exact: true }).click();
  await expect(
    first.getByRole("spinbutton", { name: "배분 수량" }).nth(0),
  ).toHaveValue("55");
  await opener.click();
  await page.keyboard.press("Escape");
  await expect(dialog).toHaveCount(0);
  await expect(form).toBeVisible();
  await expect(opener).toBeFocused();
  await second.getByRole("button", { name: "제거", exact: true }).click();
  await expect(variety.locator("[data-sales-item-index]")).toHaveCount(1);
});

test("work keeps manual multi-selection and user-group selection in the shared dialog", async ({
  page,
}) => {
  const send = (data: unknown) => ({ json: { success: true, data } });
  await page.route("**/api/work-types", (route) =>
    route.fulfill(
      send([
        {
          id: 1,
          code: "MEMO",
          name: "메모",
          template: "MEMO",
          defaultType: true,
          systemType: false,
          active: true,
          sortOrder: 1,
          settingsEditable: true,
          registrationModes: ["PLAN", "RECORD"],
          workflow: "GENERIC",
          targetSource: "ORCHID_GROUP",
        },
      ]),
    ),
  );
  await page.route("**/api/orchid-groups/derived-groups", (route) =>
    route.fulfill(
      send([
        {
          groupKey: "fixture-auto",
          varietyName: "수납 테스트 품종",
          ageYear: 1,
          potSize: "3호",
          orchidGroupCount: 2,
          totalQuantity: 150,
        },
      ]),
    ),
  );
  await page.route(
    "**/api/orchid-groups/derived-groups/fixture-auto/members",
    (route) => route.fulfill(send(selectionGroups.slice(0, 2))),
  );
  await page.route("**/api/orchid-group-collections", (route) =>
    route.fulfill(
      send([
        {
          id: 1,
          name: "선택 테스트 그룹",
          status: "ACTIVE",
          orchidGroupCount: 2,
          totalQuantity: 150,
          members: [{ orchidGroupId: 99 }, { orchidGroupId: 100 }],
        },
      ]),
    ),
  );
  await page.goto("/work-records");
  await expect(async () => {
    await page.getByRole("button", { name: "작업 등록", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "작업 대상 확인", exact: true }),
    ).toBeVisible({ timeout: 1500 });
  }).toPass({ timeout: 15000 });
  const opener = page.getByRole("button", {
    name: "작업 대상 확인",
    exact: true,
  });
  await opener.first().click();
  const dialog = page.getByRole("dialog", {
    name: "작업 대상 선택",
    exact: true,
  });
  await dialog
    .getByRole("button", { name: /수납 테스트 품종 100분/ })
    .first()
    .click();
  await dialog
    .getByRole("button", { name: /다른 품종 100분/ })
    .first()
    .click();
  await expect(
    dialog.getByText("2묶음 · 200분", { exact: true }),
  ).toBeVisible();
  await dialog.getByRole("button", { name: "대상 확정", exact: true }).click();
  await expect(opener).toBeVisible();
  await opener.first().click();
  await dialog
    .getByRole("button", { name: "선택 초기화", exact: true })
    .click();
  await dialog
    .getByRole("textbox", { name: "난 묶음 검색", exact: true })
    .fill("선택 테스트 그룹");
  await dialog
    .getByRole("checkbox", {
      name: "선택 테스트 그룹 사용자 그룹",
      exact: true,
    })
    .click();
  await expect(
    dialog.getByText("2묶음 · 150분", { exact: true }),
  ).toBeVisible();
  await dialog.getByRole("button", { name: "대상 확정", exact: true }).click();
  await expect(
    page.getByText("선택 테스트 그룹", { exact: true }),
  ).toBeVisible();
  await opener.first().click();
  await dialog
    .getByRole("button", { name: "선택 초기화", exact: true })
    .click();
  await dialog
    .getByRole("textbox", { name: "난 묶음 검색", exact: true })
    .fill("수납 테스트 품종");
  await dialog
    .getByRole("checkbox", { name: "수납 테스트 품종 자동 그룹", exact: true })
    .click();
  await expect(
    dialog.getByText("2묶음 · 150분", { exact: true }),
  ).toBeVisible();
  await dialog.getByRole("button", { name: "대상 확정", exact: true }).click();
  await expect(
    page.getByText("수납 테스트 품종", { exact: true }),
  ).toBeVisible();
});
