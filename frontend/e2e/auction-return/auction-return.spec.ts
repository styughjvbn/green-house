import {
  test,
  expect,
  type Page,
  type APIRequestContext,
} from "@playwright/test";
const control = async (
  request: APIRequestContext,
  body: Record<string, unknown>,
) =>
  (
    await request.post("http://127.0.0.1:14100/__control", { data: body })
  ).json();
async function chooseFarmReturn(page: Page) {
  await page.getByRole("button", { name: "유찰 처리", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "유찰 잔량 처리 방법 선택" });
  await dialog.getByRole("radio", { name: "농장 반환" }).check();
  await dialog.getByLabel("결정 사유").fill("유찰품 농장 반환");
  await dialog.getByRole("button", { name: "처리 방법 저장" }).click();
  await expect(dialog).not.toBeVisible();
}
async function fillArrival(page: Page) {
  if (
    !(await page
      .getByRole("dialog", { name: "농장 반환", exact: true })
      .isVisible())
  ) {
    await page.getByRole("button", { name: "농장 반환", exact: true }).click();
  }
  await page.getByRole("button", { name: "반환분 배치", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "실제 반환품 도착 기록" });
  await expect(dialog.getByLabel("실제 도착일")).toHaveValue("2026-10-08");
  await dialog.getByLabel("도착 수량", { exact: true }).fill("10");
  await dialog.getByLabel("반환품 품종").selectOption("9");
  await dialog.getByLabel("반환품 상태").selectOption("주의");
  await dialog.getByLabel("배치 규격").selectOption("SINGLE_POT");
  await dialog.getByRole("button", { name: "배치 칸", exact: true }).click();
  const picker = page.getByRole("dialog", { name: "반환품 배치 칸 선택" });
  await picker.getByRole("button", { name: "선택 완료", exact: true }).click();
  return dialog;
}
test.beforeEach(async ({ page, request, context }) => {
  await control(request, { reset: true });
  await context.addCookies([
    {
      name: "JSESSIONID",
      value: "isolated-ui-fixture",
      url: "http://127.0.0.1:13100",
    },
  ]);
  await page.goto("/sales/auction?lotId=42");
  await expect(
    page.getByRole("button", { name: "유찰 처리", exact: true }),
  ).toBeEnabled();
});
test("decision, partial physical arrival, blocked compensation and cancellation refresh current facts", async ({
  page,
  request,
}) => {
  const trigger = page.getByRole("button", {
    name: "유찰 처리",
    exact: true,
    includeHidden: true,
  });
  await trigger.click();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await expect(trigger).toBeFocused();
  await chooseFarmReturn(page);
  const unsoldEvent = page
    .getByRole("listitem")
    .filter({ hasText: "1차 경매" });
  const placementButton = unsoldEvent.getByRole("button", {
    name: "농장 반환",
    exact: true,
  });
  await expect(placementButton).toBeEnabled();
  await expect(
    page.getByRole("button", { name: "반환분 배치", exact: true }),
  ).toHaveCount(0);
  await placementButton.click();
  await expect(
    page.getByRole("dialog", { name: "농장 반환", exact: true }),
  ).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(placementButton).toBeFocused();

  await expect(
    page.getByText("유찰 잔량 후속 처리", { exact: true }),
  ).toHaveCount(0);
  const placementBounds = await placementButton.boundingBox();
  const decisionBounds = await trigger.boundingBox();
  expect(placementBounds).not.toBeNull();
  expect(decisionBounds).not.toBeNull();
  expect(placementBounds!.x + placementBounds!.width).toBeLessThanOrEqual(
    decisionBounds!.x,
  );
  await expect(
    page
      .getByRole("listitem")
      .filter({ hasText: "1차 경매" })
      .getByText("농장 반환", { exact: true }),
  ).toBeVisible();
  const dialog = await fillArrival(page);
  await dialog.getByRole("button", { name: "실제 도착 저장" }).click();
  await expect(dialog).not.toBeVisible();
  await expect(
    page.getByRole("dialog", { name: "농장 반환", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText("도착 대기 20분", { exact: false }),
  ).toBeVisible();
  await expect(trigger).toBeDisabled();
  await expect(
    page.getByText("생성 난 묶음 #901", { exact: false }),
  ).toBeVisible();
  await control(request, { rejectCancel: true });
  await page
    .getByRole("button", { name: "도착 취소 정정", exact: true })
    .click();
  const correction = page.getByRole("dialog", {
    name: "도착 취소 정정",
    exact: true,
  });
  await correction.getByLabel("도착 취소 사유").fill("도착 입력 정정");
  await correction.getByRole("button", { name: "도착 취소 저장" }).click();
  await expect(correction.getByRole("alert")).toContainText(
    "후속 사용을 먼저 정정하세요.",
  );
  await expect(trigger).toBeDisabled();
  await control(request, { rejectCancel: false });
  await correction.getByRole("button", { name: "도착 취소 저장" }).click();
  await expect(correction).not.toBeVisible();
  await expect(trigger).toBeEnabled();
  await expect(page.getByText("도착 취소됨", { exact: false })).toBeVisible();
  await expect(
    page.getByRole("button", { name: "도착 취소 정정", exact: true }),
  ).toBeDisabled();
  await page.keyboard.press("Escape");
  await expect(placementButton).toBeFocused();
  await expect(
    page.getByText("생성 난 묶음 #901", { exact: false }),
  ).not.toBeVisible();
  const {
    data: { calls },
  } = await control(request, {});
  const arrival = calls.find((call: { path: string }) =>
    call.path.endsWith("/arrivals"),
  );
  expect(arrival.body.details).toMatchObject({
    varietyId: 9,
    quantity: 10,
    status: "주의",
    placementType: "SINGLE_POT",
    startPosition: 0,
    endPosition: 1,
  });
  expect(
    calls.some(
      (call: { path: string }) =>
        call.path.includes("inbound") || call.path.includes("confirm-return"),
    ),
  ).toBe(false);
});
test("an acknowledged arrival with a lost response replays the original input after reload", async ({
  page,
  request,
}) => {
  await chooseFarmReturn(page);
  const dialog = await fillArrival(page);
  await control(request, { loseNextResponse: true });
  await dialog.getByRole("button", { name: "실제 도착 저장" }).click();
  await expect(dialog.getByRole("alert")).toBeVisible();
  await dialog.getByRole("button", { name: "닫기", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "이전 요청 다시 확인" }),
  ).toBeVisible();
  await page.reload();
  await expect(
    page
      .getByRole("listitem")
      .filter({ hasText: "1차 경매" })
      .getByText("농장 반환", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "이전 요청 다시 확인" }).click();
  await expect(
    page.getByRole("button", { name: "이전 요청 다시 확인" }),
  ).not.toBeVisible();
  await page.getByRole("button", { name: "농장 반환", exact: true }).click();

  await expect(
    page.getByText("도착 대기 20분", { exact: false }),
  ).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(
    page.getByText("도착 대기 20분", { exact: false }),
  ).not.toBeVisible();
  await expect(
    page.getByRole("button", { name: "유찰 처리", exact: true }),
  ).toBeDisabled();
  const {
    data: { calls },
  } = await control(request, {});
  const arrivals = calls.filter((call: { path: string }) =>
    call.path.endsWith("/arrivals"),
  );
  expect(arrivals).toHaveLength(2);
  expect(arrivals[1].body).toEqual(arrivals[0].body);
});

test("follow-up is opened from the current unsold event, excluding older and inferred events", async ({
  page,
  request,
}) => {
  await expect(
    page.getByRole("button", { name: "처리 방법 선택", exact: true }),
  ).toHaveCount(0);
  const event = page.getByRole("listitem").filter({ hasText: "1차 경매" });
  await expect(
    event.getByRole("button", { name: "유찰 처리", exact: true }),
  ).toBeEnabled();
  await control(request, {
    lotOverride: {
      followUpAttemptId: 430,
      attempts: [
        {
          id: 420,
          auctionDate: "2026-10-05",
          attemptNo: 1,
          attemptStatus: "FAILED",
          resultLines: [{ id: 421, quantity: 30, unitPrice: 0, amount: 0 }],
        },
        {
          id: 430,
          auctionDate: "2026-10-07",
          attemptNo: 2,
          attemptStatus: "PARTIALLY_SOLD",
          resultLines: [
            { id: 431, quantity: 10, unitPrice: 100, amount: 1000 },
            { id: 432, quantity: 20, unitPrice: 0, amount: 0 },
          ],
        },
      ],
    },
  });
  await page.reload();
  await expect(
    page
      .getByRole("listitem")
      .filter({ hasText: "1차 경매" })
      .getByRole("button", { name: "유찰 처리" }),
  ).toHaveCount(0);
  const latest = page.getByRole("listitem").filter({ hasText: "2차 경매" });
  await expect(latest.getByRole("button", { name: "유찰 처리" })).toBeEnabled();
  await latest.getByRole("button", { name: "유찰 처리" }).click();
  await expect(
    page.getByRole("dialog", { name: "유찰 잔량 처리 방법 선택" }),
  ).toBeVisible();
  await page.keyboard.press("Escape");
  await control(request, {
    lotOverride: {
      followUpAttemptId: null,
      attempts: [
        {
          id: 440,
          auctionDate: "2026-10-08",
          attemptNo: 3,
          attemptStatus: "RETURN_INFERRED",
          resultLines: [{ id: 441, quantity: 30, unitPrice: 0, amount: 0 }],
        },
      ],
    },
  });
  await page.reload();
  await expect(
    page.getByRole("button", { name: "유찰 처리", exact: true }),
  ).toHaveCount(0);
});
