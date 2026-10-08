import { test, expect } from "@playwright/test";

test.beforeEach(async ({ page, request, context }) => {
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
  await page.goto(
    "/sales/settlement?view=allocations&receiptPartnerId=7&receiptId=101",
  );
  await expect(
    page.getByRole("button", { name: "배분 추가", exact: true }),
  ).toBeEnabled();
});

test("split allocation and cancellation with replacement refresh server balances and preserve focus", async ({
  page,
  request,
}) => {
  const open = page.getByRole("button", { name: "배분 추가", exact: true });
  await open.click();
  let dialog = page.getByRole("dialog", { name: "수납 배분", exact: true });
  await dialog.press("Escape");
  await expect(open).toBeFocused();
  await open.click();
  await dialog
    .getByLabel("배분 대상", { exact: true })
    .first()
    .selectOption("11");
  await dialog.getByLabel("배분액", { exact: true }).first().fill("1000");
  await dialog
    .getByRole("button", { name: "배분 항목 추가", exact: true })
    .click();
  await dialog
    .getByLabel("배분 대상", { exact: true })
    .nth(1)
    .selectOption("12");
  await dialog.getByLabel("배분액", { exact: true }).nth(1).fill("500");
  await dialog.getByRole("button", { name: "배분 확정", exact: true }).click();
  await expect(dialog).not.toBeVisible();
  await expect(
    page.getByText("입금 1,500원 / 미배분 0원", { exact: true }),
  ).toBeVisible();
  await page.getByLabel("배분 #1 선택", { exact: true }).check();
  await page
    .getByRole("button", { name: "선택 배분 정정", exact: true })
    .click();
  dialog = page.getByRole("dialog", { name: "배분 정정", exact: true });
  await dialog.getByLabel("정정 사유").fill("배분액 오입력");
  await dialog
    .getByRole("button", { name: "배분 항목 추가", exact: true })
    .click();
  await dialog.getByLabel("배분 대상", { exact: true }).selectOption("11");
  await dialog.getByLabel("배분액", { exact: true }).fill("700");
  await dialog.getByRole("button", { name: "정정 확정", exact: true }).click();
  await expect(dialog).not.toBeVisible();
  await expect(
    page.getByText("입금 1,500원 / 미배분 300원", { exact: true }),
  ).toBeVisible();
  await expect(page.getByLabel("배분 #1 선택", { exact: true })).toBeDisabled();
  const control = await (
    await request.post("http://127.0.0.1:14100/__control", { data: {} })
  ).json();
  expect(control.data.calls).toHaveLength(2);
  expect(control.data.calls[1].body.cancellationIds).toEqual([1]);
  expect(control.data.calls[1].body.allocations[0].amount).toBe(700);
});

test("lost allocation response retries the same command after reload and another view", async ({
  page,
  request,
}) => {
  await page.getByRole("button", { name: "배분 추가", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "수납 배분", exact: true });
  await dialog.getByLabel("배분 대상", { exact: true }).selectOption("11");
  await dialog.getByLabel("배분액", { exact: true }).fill("600");
  await request.post("http://127.0.0.1:14100/__control", {
    data: { loseNextResponse: true },
  });
  await dialog.getByRole("button", { name: "배분 확정", exact: true }).click();
  await expect(
    page.getByText("배분을 저장하지 못했습니다.", { exact: true }).last(),
  ).toBeVisible();
  await page.reload();
  await page
    .getByRole("button", { name: "대상 미지정 수납", exact: true })
    .click();
  const retry = page.getByRole("button", {
    name: "같은 요청 다시 확인",
    exact: true,
  });
  await expect(retry).toBeVisible();
  await retry.click();
  await expect(retry).not.toBeVisible();
  await expect(
    page.getByText("미배분 수납 1,400원", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "배분·정정", exact: true }).click();
  await expect(
    page.getByText("입금 1,500원 / 미배분 900원", { exact: true }),
  ).toBeVisible();
  const control = await (
    await request.post("http://127.0.0.1:14100/__control", { data: {} })
  ).json();
  expect(control.data.calls).toHaveLength(2);
  expect(control.data.calls[0].body).toEqual(control.data.calls[1].body);
  await expect(page.getByLabel("배분 #1 선택", { exact: true })).toHaveCount(1);
});
