import { test, expect } from "@playwright/test";
import { installFarmSelectionFixture } from "./farm-selection-fixture";

test.beforeEach(async ({ request, context }) => {
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
});

// TODO: 수납·경매 정산 화면 재연결 시 활성화. 현재 메뉴와 직접 URL 접근이 비활성화되어 있습니다.
test.skip("one sales menu keeps auction settlement and adds payments", async ({
  page,
}) => {
  await page.goto("/sales");
  await expect(page).toHaveURL(/\/sales\/slips$/);
  await expect(
    page.getByRole("heading", { name: /^판매 관리 >/, level: 1 }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "경매 정산", exact: true }),
  ).toHaveAttribute("href", "/sales/settlement");
  await expect(
    page.getByRole("link", { name: "수납 관리", exact: true }),
  ).toHaveAttribute("href", "/sales/payments");
  await expect(
    page.getByRole("link", { name: "판매 관리 v2", exact: true }),
  ).toHaveCount(0);
  await page.getByRole("link", { name: "경매 정산", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "경매 대금", exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: "수납 관리", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "수납 등록·오입력 정정", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "수납 조회·배분·정정", exact: true }),
  ).toBeVisible();
  const response = await page.goto("/sales-v2");
  expect(response?.status()).toBe(404);
});

test("auction slip editor omits price and total fields", async ({ page }) => {
  await page.goto("/sales/slips");
  await page.getByRole("button", { name: "전표 작성", exact: true }).click();
  const form = page.getByRole("dialog", { name: "전표 작성", exact: true });
  await expect(
    form.getByRole("spinbutton", { name: "단가", exact: true }),
  ).toBeVisible();
  await expect(form.getByText(/^합계 /)).toBeVisible();
  await form.getByRole("button", { name: "경매 판매", exact: true }).click();
  await expect(
    form.getByRole("spinbutton", { name: "단가", exact: true }),
  ).toHaveCount(0);
  await expect(form.getByText("금액", { exact: true })).toHaveCount(0);
  await expect(form.getByText(/^합계 /)).toHaveCount(0);
  await expect(
    form.getByRole("spinbutton", { name: "수량", exact: true }),
  ).toBeVisible();
});
// TODO: 수납·경매 정산 화면 재연결 시 활성화. 현재 메뉴와 직접 URL 접근이 비활성화되어 있습니다.
test.skip("payments keep receipt selection across views, allocation and target tracing", async ({
  page,
}) => {
  await page.goto(
    "/sales/payments?view=allocations&receiptPartnerId=7&receiptId=101",
  );
  await page.getByRole("button", { name: "배분 추가", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "수납 배분", exact: true });
  await expect(
    dialog.getByRole("option", { name: "경매 대금", exact: true }),
  ).toHaveCount(0);
  await expect(
    dialog.getByRole("option", { name: "일반 판매 전표", exact: true }),
  ).toHaveCount(1);
  await dialog.getByLabel("배분 대상", { exact: true }).selectOption("11");
  await dialog.getByLabel("배분액", { exact: true }).fill("600");
  await dialog.getByRole("button", { name: "배분 확정", exact: true }).click();
  await expect(dialog).not.toBeVisible();
  await expect(
    page.getByRole("link", { name: "배분 대상 보기", exact: true }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "수납 등록·오입력 정정", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "경매 대금", exact: true }),
  ).toHaveCount(0);
  await page.reload();
  await page
    .getByRole("button", { name: "수납 조회·배분·정정", exact: true })
    .click();
  await expect(
    page.getByText("수납 1,500원 / 미배분 900원", { exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: "배분 대상 보기", exact: true }).click();
  await expect(page).toHaveURL(/\/sales\/slips\?slipId=11&paymentPage=0$/);
  await expect(page.getByText("LOT #TEST-11", { exact: true })).toBeVisible();
  const history = page
    .locator("p")
    .filter({ hasText: /^수납 이력 · 총/ })
    .locator("..");
  await expect(history.getByText("600원", { exact: true })).toBeVisible();
  await expect(history.getByText("1,500원", { exact: true })).toHaveCount(0);
});

// TODO: 수납·경매 정산 화면 재연결 시 활성화. 현재 메뉴와 직접 URL 접근이 비활성화되어 있습니다.
test.skip("payment workspace uses separate panels on desktop and stacks without overflow on mobile", async ({
  page,
}) => {
  await page.goto(
    "/sales/payments?view=allocations&receiptPartnerId=7&receiptId=101",
  );
  const sources = page.locator('[data-sales-payment-panel="sources"]');
  const allocation = page.locator('[data-sales-payment-panel="allocation"]');
  await expect(
    page.getByRole("button", { name: "배분 추가", exact: true }),
  ).toBeEnabled();
  const left = await sources.boundingBox();
  const right = await allocation.boundingBox();
  expect(right!.x).toBeGreaterThanOrEqual(left!.x + left!.width);
  await page.setViewportSize({ width: 390, height: 844 });
  const top = await sources.boundingBox();
  const bottom = await allocation.boundingBox();
  expect(bottom!.y).toBeGreaterThanOrEqual(top!.y + top!.height);
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth),
  ).toBeLessThanOrEqual(390);
  await page
    .getByRole("button", { name: "수납 조회·배분·정정", exact: true })
    .focus();
  await expect(
    page.getByRole("button", { name: "수납 조회·배분·정정", exact: true }),
  ).toBeFocused();
});

test("auction documents show linked lot facts and navigate to the exact lot", async ({
  page,
  request,
}) => {
  await request.post("http://127.0.0.1:14100/__control", {
    data: { auctionDocumentSeed: true },
  });
  for (const route of ["/sales/slips?slipId=13"]) {
    await page.goto(route);
    const row = page.getByRole("row").filter({ hasText: "호접란" });
    await expect(row.getByText("재경매대기", { exact: true })).toBeVisible();
    await expect(
      page.getByRole("columnheader", { name: /^낙찰 / }),
    ).toBeVisible();
    await expect(
      page.getByRole("columnheader", { name: /^반환 / }),
    ).toBeVisible();
    const cells = row.getByRole("cell");
    await expect(cells.nth(5)).toHaveText("0");
    await expect(cells.nth(6)).toHaveText("30");
    await expect(cells.nth(7)).toHaveText("0");
    const link = row.getByRole("link", { name: "상세 보기", exact: true });
    await expect(link).toHaveAttribute("href", "/sales/auction?lotId=42");
    await link.click();
    await expect(page.getByText("LOT #42", { exact: true })).toBeVisible();
    await page.goBack();
    await expect(row.getByText("재경매대기", { exact: true })).toBeVisible();
  }
  await page.goto("/sales/slips?slipId=11");
  await expect(page.getByRole("columnheader", { name: /^낙찰 / })).toHaveCount(
    0,
  );
});

test("document filters scope partners by sales type and keep grouped dispatch search in the URL", async ({
  page,
}) => {
  await page.goto("/sales/slips");
  const filters = page.getByRole("dialog", { name: "필터", exact: true });
  const openFilters = () =>
    page.getByRole("button", { name: /^필터 선택/ }).click();
  await openFilters();
  const partner = filters.getByRole("combobox", {
    name: "거래처",
    exact: true,
  });
  await partner.click();
  await expect(
    page.getByRole("option", { name: "수납 테스트 거래처", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("option", { name: "경매 선택지", exact: true }),
  ).toBeVisible();
  await page
    .getByRole("option", { name: "수납 테스트 거래처", exact: true })
    .click();
  await page
    .getByRole("combobox", { name: "판매 유형", exact: true })
    .selectOption("AUCTION");
  await partner.click();
  await expect(
    page.getByRole("option", { name: "수납 테스트 거래처", exact: true }),
  ).toHaveCount(0);
  await page.getByRole("option", { name: "경매 선택지", exact: true }).click();
  await page
    .getByRole("combobox", { name: "출고·출하", exact: true })
    .selectOption({ label: "완료" });
  await filters.getByRole("button", { name: "필터 적용", exact: true }).click();
  await expect(page).toHaveURL(/salesType=AUCTION/);
  expect(new URL(page.url()).searchParams.get("salesStatus")).toBe("완료");
  expect(new URL(page.url()).searchParams.get("partnerId")).toBe("8");
  await page.reload();
  await openFilters();
  await expect(
    page.getByRole("combobox", { name: "판매 유형", exact: true }),
  ).toHaveValue("AUCTION");
  await expect(
    page.getByRole("combobox", { name: "출고·출하", exact: true }),
  ).toHaveValue("완료");
  await page
    .getByRole("combobox", { name: "판매 유형", exact: true })
    .selectOption("DIRECT");
  await partner.click();
  await expect(
    page.getByRole("option", { name: "경매 선택지", exact: true }),
  ).toHaveCount(0);
  await filters.getByRole("button", { name: "필터 적용", exact: true }).click();
  await expect(page).toHaveURL(/salesType=DIRECT/);
  expect(new URL(page.url()).searchParams.has("partnerId")).toBe(false);
  await openFilters();
  await filters
    .getByRole("combobox", { name: "판매 유형", exact: true })
    .selectOption("");
  await filters.getByRole("button", { name: "필터 적용", exact: true }).click();
  await expect(page).not.toHaveURL(/salesType=/);
  expect(new URL(page.url()).searchParams.get("salesStatus")).toBe("완료");
});

// TODO: 수납·경매 정산 화면 재연결 시 활성화. 현재 메뉴와 직접 URL 접근이 비활성화되어 있습니다.
test.skip("direct document creation collects cash separately from dispatch and removes status-only completion", async ({
  page,
  request,
}) => {
  const fixture = await (
    await request.get("http://127.0.0.1:14100/api/sales-slips/11")
  ).json();
  let submitted: Record<string, unknown> | undefined;
  await page.route("**/api/sales-slips", async (route) => {
    if (route.request().method() !== "POST") return route.continue();
    submitted = route.request().postDataJSON();
    await route.fulfill({
      json: {
        ...fixture,
        data: {
          ...fixture.data,
          paidAmount: 2000,
          remainingAmount: 3000,
          paymentStatus: "부분입금",
          salesStatus: "출고 완료",
        },
      },
    });
  });
  await installFarmSelectionFixture(page);
  await page.route("**/api/sales/orchid-groups/search**", (route) =>
    route.fulfill({
      json: {
        success: true,
        data: [
          {
            id: 99,
            varietyId: 9,
            varietyName: "수납 테스트 품종",
            genus: "호접란",
            houseNumber: 1,
            physicalBedNumber: 1,
            bedZoneName: "앞",
            quantity: 100,
            reservedQuantity: 0,
            availableQuantity: 100,
            status: "정상",
            ageYear: 1,
            potSize: "3호",
          },
        ],
      },
    }),
  );
  await page.goto("/sales/slips");
  await page.getByRole("button", { name: "전표 작성", exact: true }).click();
  const form = page.getByRole("dialog", { name: "전표 작성", exact: true });
  await expect(form.getByText("결제 방법", { exact: true })).toHaveCount(0);
  await expect(form.getByRole("combobox", { name: /^입금/ })).toHaveCount(0);
  await form.getByRole("combobox", { name: "거래처", exact: true }).click();
  await page
    .getByRole("option", { name: "수납 테스트 거래처", exact: true })
    .click();
  await form.getByRole("button", { name: "난 묶음 선택", exact: true }).click();
  const selector = page.getByRole("dialog", {
    name: "난 묶음 선택",
    exact: true,
  });
  await selector
    .getByRole("button", { name: /수납 테스트 품종 100분/ })
    .first()
    .click();
  await selector
    .getByRole("button", { name: "대상 확정", exact: true })
    .click();
  await form.getByRole("spinbutton", { name: "수량", exact: true }).fill("5");
  await form
    .getByRole("spinbutton", { name: "단가", exact: true })
    .fill("1000");
  await form.getByRole("checkbox", { name: "수납 함께 기록" }).check();
  await expect(
    form.getByRole("spinbutton", { name: "수납액", exact: true }),
  ).toHaveValue("5000");
  await form.getByRole("spinbutton", { name: "단가", exact: true }).fill("800");
  await expect(
    form.getByText(/초과분 1,000원은 거래처 미배분 잔액/),
  ).toBeVisible();
  await form
    .getByRole("combobox", { name: /^출고·출하/ })
    .selectOption("출고 완료");
  await form
    .getByRole("button", { name: "판매 전표 저장", exact: true })
    .click();
  await expect(form).toHaveCount(0);
  expect(submitted?.initialReceipt).toMatchObject({
    amount: 5000,
    method: "CASH",
  });
  expect(submitted?.paymentStatus).toBe("미입금");
  expect(submitted?.paymentMethod).toBeNull();
  expect(submitted?.salesStatus).toBe("출고 완료");
});

// TODO: 수납·경매 정산 화면 재연결 시 활성화. 현재 메뉴와 직접 URL 접근이 비활성화되어 있습니다.
test.skip("direct payment records an amount above the slip balance", async ({
  page,
}) => {
  let receivedAmount: number | null = null;
  await page.route("**/api/sales-slips/11", async (route) => {
    const response = await route.fetch();
    const body = await response.json();
    await route.fulfill({
      json: {
        ...body,
        data: { ...body.data, availableActions: ["CONFIRM_PAYMENT"] },
      },
    });
  });
  await page.route("**/api/sales-slips/11/confirm-payment", async (route) => {
    receivedAmount = route.request().postDataJSON().amount;
    const response = await route.fetch({
      method: "GET",
      url: "http://127.0.0.1:14100/api/sales-slips/11",
    });
    const body = await response.json();
    await route.fulfill({
      json: {
        ...body,
        data: {
          ...body.data,
          paidAmount: 1000,
          remainingAmount: 0,
          availableActions: [],
        },
      },
    });
  });

  await page.goto("/sales/slips");
  await page.getByRole("row", { name: /1 26-10-01 일반/ }).click();
  await page.getByRole("button", { name: "수납 기록", exact: true }).click();
  const amount = page.getByRole("spinbutton", { name: "수납액", exact: true });
  await amount.fill("1200");
  expect(
    await amount.evaluate((input: HTMLInputElement) => input.checkValidity()),
  ).toBe(true);
  await expect(
    page.getByText(/초과 수납액은 거래처 미배분 잔액으로 남습니다/),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "수납 기록", exact: true })
    .last()
    .click();
  await expect(page.getByText("수납 기록 완료", { exact: true })).toBeVisible();
  expect(receivedAmount).toBe(1200);
});

// TODO: 수납·경매 정산 화면 재연결 시 활성화. 현재 메뉴와 직접 URL 접근이 비활성화되어 있습니다.
test.skip("fully collected direct documents retain receipt history while new collection is disabled", async ({
  page,
  request,
}) => {
  await request.post(
    "http://127.0.0.1:14100/api/business-partners/7/payment-allocations",
    {
      data: {
        allocationDate: "2026-10-08",
        idempotencyKey: "fully-paid-history",
        allocations: [
          {
            receiptId: 101,
            targetType: "SALES_SLIP",
            targetId: 11,
            amount: 1000,
          },
        ],
      },
    },
  );
  await page.goto("/sales/slips?slipId=11&paymentPage=0");
  const history = page
    .locator("p")
    .filter({ hasText: /^수납 이력 · 총/ })
    .locator("..");
  await expect(history.getByText("1,000원", { exact: true })).toBeVisible();
  await expect(
    page.getByRole("spinbutton", { name: "수납액", exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "수납 이력", exact: true }),
  ).toBeVisible();
});
