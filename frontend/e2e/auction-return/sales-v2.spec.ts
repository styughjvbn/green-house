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

test("v2 menus and cleaned documents coexist with the legacy screen", async ({
  page,
}) => {
  await page.goto("/sales-v2");
  await expect(page).toHaveURL(/\/sales-v2\/slips$/);
  await expect(
    page.getByRole("heading", { name: /^판매 관리 v2 >/, level: 1 }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "전표", exact: true }),
  ).toHaveCount(0);
  await expect(page.getByText("전표 #TEST-11", { exact: true })).toBeVisible();
  await expect(
    page.getByRole("button", { name: "전표 복사", exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("link", { name: "거래처 수납·배분 조회", exact: true }),
  ).toHaveAttribute(
    "href",
    "/sales-v2/payments?view=allocations&receiptPartnerId=7",
  );
  await page.goto("/sales-v2/slips?slipId=13");
  await expect(page.getByText("전표 #TEST-13", { exact: true })).toBeVisible();
  await expect(
    page.getByRole("link", { name: "경매장 출하 조회", exact: true }),
  ).toBeVisible();
  await page.goto("/sales/slips");
  await expect(
    page.getByRole("heading", { name: /^판매 관리 >/, level: 1 }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "전표 복사", exact: true }),
  ).toBeVisible();
});

test("auction proceeds stays inside auction and restores lot selection", async ({
  page,
}) => {
  await page.goto("/sales-v2/auction?lotId=42&market=테스트");
  await expect(page.getByText("LOT #42", { exact: true })).toBeVisible();
  await page
    .getByRole("button", { name: "경매 대금 확인", exact: true })
    .click();
  await expect(page).toHaveURL(/panel=proceeds/);
  await expect(
    page.getByRole("link", { name: "LOT #42", exact: true }),
  ).toBeVisible();
  await page.reload();
  await expect(
    page.getByRole("link", { name: "경매장 수납·배분 조회", exact: true }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "출하 목록으로", exact: true })
    .click();
  await expect(page.getByText("LOT #42", { exact: true })).toBeVisible();
  expect(new URL(page.url()).searchParams.get("market")).toBe("테스트");
  await page.goBack();
  await expect(
    page.getByRole("link", { name: "LOT #42", exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: "LOT #42", exact: true }).click();
  await expect(page).toHaveURL(/\/sales-v2\/auction\?lotId=42$/);
  await expect(page.getByText("LOT #42", { exact: true })).toBeVisible();
});

test("payments keep receipt selection across views, allocation and target tracing", async ({
  page,
}) => {
  await page.goto(
    "/sales-v2/payments?view=allocations&receiptPartnerId=7&receiptId=101",
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
  await expect(page).toHaveURL(/\/sales-v2\/slips\?slipId=11&paymentPage=0$/);
  await expect(page.getByText("전표 #TEST-11", { exact: true })).toBeVisible();
});

test("partner selection reload and links keep the selected partner", async ({
  page,
}) => {
  await page.goto("/sales-v2/partners");
  await page
    .getByRole("cell", { name: "수납 테스트 거래처", exact: true })
    .click();
  await expect(page).toHaveURL(/partnerId=7/);
  await page.reload();
  await expect(
    page.getByRole("link", { name: "거래처 전표 조회", exact: true }),
  ).toHaveAttribute("href", "/sales-v2/slips?partnerId=7");
  await page
    .getByRole("link", { name: "거래처 수납·배분 조회", exact: true })
    .click();
  await expect(page).toHaveURL(
    /\/sales-v2\/payments\?view=allocations&receiptPartnerId=7$/,
  );
  await expect(
    page.getByRole("button", { name: "수납 #101", exact: false }),
  ).toBeVisible();
});

test("payment workspace uses separate panels on desktop and stacks without overflow on mobile", async ({
  page,
}) => {
  await page.goto(
    "/sales-v2/payments?view=allocations&receiptPartnerId=7&receiptId=101",
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
