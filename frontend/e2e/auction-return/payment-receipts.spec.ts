import { test, expect } from "@playwright/test";

test.beforeEach(async ({ page, request, context }) => {
  await request.post("http://127.0.0.1:14100/__control", {
    data: { reset: true },
  });
  await context.addCookies([
    {
      name: "JSESSIONID",
      value: "isolated-ui-fixture",
      url: "http://127.0.0.1:13100",
    },
  ]);
  await page.goto("/sales/settlement?view=receipts&receiptPartnerId=7");
  await expect(
    page.getByRole("button", { name: "수납 기록", exact: true }),
  ).toBeEnabled();
});

test("cash receipt and input correction refresh history, balances and focus", async ({
  page,
}) => {
  await page.getByLabel("입금액", { exact: true }).fill("1000");
  await expect(page.getByLabel("입금일", { exact: true })).toHaveValue(
    "2026-10-08",
  );
  await page.getByRole("button", { name: "수납 기록", exact: true }).click();
  await expect(
    page.getByText("미배분 수납 1,000원", { exact: true }),
  ).toBeVisible();
  const cancel = page.getByRole("button", { name: "오입력 취소", exact: true });
  await cancel.click();
  const dialog = page.getByRole("dialog", {
    name: "수납 오입력 취소",
    exact: true,
  });
  await dialog.press("Escape");
  await expect(cancel).toBeFocused();
  await cancel.click();
  await dialog.getByLabel("정정 사유").fill("입금액 오입력");
  await dialog
    .getByRole("button", { name: "오입력 취소 확정", exact: true })
    .click();
  await expect(dialog).not.toBeVisible();
  await expect(
    page.getByText("미배분 수납 0원", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText("수납 1,000원 (취소됨)", { exact: true }),
  ).toBeVisible();
  await expect(page.getByText("원본 #1", { exact: true })).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "수납·정정 내역", exact: true }),
  ).toBeFocused();
});

test("lost receipt response can be retried after reload without another cash receipt", async ({
  page,
  request,
}) => {
  await page.getByLabel("입금액", { exact: true }).fill("2000");
  await page.getByLabel("메모", { exact: true }).fill("원본 입력");
  await request.post("http://127.0.0.1:14100/__control", {
    data: { loseNextResponse: true },
  });
  await page.getByRole("button", { name: "수납 기록", exact: true }).click();
  await expect(
    page.getByText("수납을 저장하지 못했습니다.", { exact: true }),
  ).toBeVisible();
  await page.reload();
  const retry = page.getByRole("button", {
    name: "같은 요청 다시 확인",
    exact: true,
  });
  await expect(retry).toBeVisible();
  await retry.click();
  await expect(retry).not.toBeVisible();
  await expect(
    page.getByText("미배분 수납 2,000원", { exact: true }),
  ).toBeVisible();
  const control = await (
    await request.post("http://127.0.0.1:14100/__control", { data: {} })
  ).json();
  const calls = control.data.calls;
  expect(calls).toHaveLength(2);
  expect(calls[0].body).toEqual(calls[1].body);
  await expect(
    page.getByRole("button", { name: "오입력 취소", exact: true }),
  ).toHaveCount(1);
});
