import { test, expect } from "@playwright/test";

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

test("one sales menu keeps auction settlement and adds payments", async ({
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
    page.getByRole("link", { name: "입금", exact: true }),
  ).toHaveAttribute("href", "/sales/payments");
  await expect(
    page.getByRole("link", { name: "판매 관리 v2", exact: true }),
  ).toHaveCount(0);
  await page.getByRole("link", { name: "경매 정산", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "경매 대금", exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: "입금", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "수납 등록·오입력 정정", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "수납 조회·배분·정정", exact: true }),
  ).toBeVisible();
  const response = await page.goto("/sales-v2");
  expect(response?.status()).toBe(404);
});
test("payments keep receipt selection across views, allocation and target tracing", async ({
  page,
}) => {
  await page.goto(
    "/sales/payments?view=allocations&receiptPartnerId=7&receiptId=101",
  );
  await page.getByRole("button", { name: "배분 추가", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "수납 배분", exact: true });
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
    page.getByText("입금 1,500원 / 미배분 900원", { exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: "배분 대상 보기", exact: true }).click();
  await expect(page).toHaveURL(/\/sales\/slips\?slipId=11&paymentPage=0$/);
  await expect(page.getByText("LOT #TEST-11", { exact: true })).toBeVisible();
});

test("payment workspace uses separate panels on desktop and stacks without overflow on mobile", async ({
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
