import { test, expect } from "@playwright/test";

test("partial price adjustment retries the original request and a return uses its price portion", async ({
  page,
  request,
  context,
}) => {
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
  const slip = {
    id: 900,
    slipNumber: "PORTIONS-900",
    saleDate: "2026-10-07",
    salesType: "DIRECT",
    partner: { id: 7, name: "성은", partnerType: "WHOLESALE" },
    totalAmount: 1000000,
    paidAmount: 0,
    remainingAmount: 1000000,
    paymentStatus: "미입금",
    salesStatus: "출고 완료",
    availableActions: ["CANCEL"],
    financialReviewRequired: false,
    items: [
      {
        id: 901,
        itemName: "버드로즈",
        genus: "긴기아남",
        quantity: 100,
        unitPrice: 10000,
        amount: 1000000,
        allocations: [],
      },
    ],
  };
  const state = {
    documentId: 900,
    originalAmount: 1000000,
    currentAmount: 1000000,
    refundDueAmount: 0,
    availableTypes: ["PRICE_ADJUSTMENT", "RETURN"],
    portions: [
      {
        itemId: 901,
        itemName: "버드로즈",
        spec: null,
        sourceChangeId: null as number | null,
        quantity: 100,
        unitPrice: 10000,
        amount: 1000000,
      },
    ],
    returnedPortions: [] as {
      itemId: number;
      itemName: string;
      spec: null;
      sourceChangeId: number | null;
      quantity: number;
      unitPrice: number;
      amount: number;
    }[],
    history: {
      content: [] as object[],
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
    },
  };
  const arrivalState = {
    pendingReturns: [] as {
      returnChangeId: number;
      itemId: number;
      itemName: string;
      pendingQuantity: number;
      arrivalAllowed: boolean;
    }[],
    history: {
      content: [] as Record<string, unknown>[],
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
    },
  };
  let loseArrival = true;
  const calls: { key: string; path: string; body: Record<string, unknown> }[] =
    [];
  await page.route("**/api/sales-slips/page?**", (route) =>
    route.fulfill({
      json: {
        data: {
          content: [slip],
          page: 0,
          size: 10,
          totalElements: 1,
          totalPages: 1,
        },
      },
    }),
  );
  await page.route("**/api/sales-slips/900", (route) =>
    route.fulfill({ json: { data: slip } }),
  );
  await page.route("**/api/sales-slips/900/direct-changes**", async (route) => {
    const req = route.request();
    if (req.method() === "GET" && req.url().includes("/return-arrivals")) {
      await route.fulfill({
        json: {
          data: arrivalState,
        },
      });
      return;
    }
    if (req.method() === "GET") {
      await route.fulfill({ json: { data: state } });
      return;
    }
    calls.push({
      key: req.headers()["idempotency-key"],
      path: new URL(req.url()).pathname,
      body: req.postDataJSON(),
    });
    if (calls.length === 1) {
      await route.fulfill({
        status: 503,
        json: { error: { code: "TEST_FAILURE", message: "응답 확인 필요" } },
      });
      return;
    }
    if (req.url().endsWith("/12/arrivals")) {
      if (loseArrival) {
        loseArrival = false;
        await route.fulfill({
          status: 503,
          json: {
            error: { code: "TEST_FAILURE", message: "도착 응답 확인 필요" },
          },
        });
        return;
      }
      arrivalState.pendingReturns[0].pendingQuantity = 0;
      arrivalState.pendingReturns[0].arrivalAllowed = false;
      arrivalState.history = {
        content: [
          {
            id: 21,
            returnChangeId: 12,
            quantity: 5,
            arrivalDate: "2026-10-09",
            orchidGroupId: null,
            creationMutationId: null,
            recordOnly: true,
            canceledAt: null,
            cancellationAllowed: true,
          },
        ],
        page: 0,
        size: 10,
        totalElements: 1,
        totalPages: 1,
      };
      const original = state.history.content.find(
        (value) => (value as { id: number }).id === 12,
      );
      Object.assign(original!, { cancelAllowed: false });
      await route.fulfill({ json: { data: { arrivalId: 21 } } });
      return;
    }
    if (req.url().endsWith("/return-arrivals/21/cancel")) {
      arrivalState.history.content[0].canceledAt = "2026-10-10T00:00:00";
      arrivalState.history.content[0].cancellationAllowed = false;
      arrivalState.pendingReturns[0].pendingQuantity = 5;
      arrivalState.pendingReturns[0].arrivalAllowed = true;
      const original = state.history.content.find(
        (value) => (value as { id: number }).id === 12,
      );
      Object.assign(original!, { cancelAllowed: true });
      await route.fulfill({ json: { data: { arrivalId: 21 } } });
      return;
    }
    const returning = req.url().endsWith("/returns");
    const canceling = req.url().endsWith("/cancel");
    if (canceling) {
      state.portions[1].quantity = 20;
      state.portions[1].amount = 160000;
      state.currentAmount = 960000;
      state.returnedPortions = [];
      const returned = state.history.content.find(
        (value) => (value as { id: number }).id === 12,
      );
      if (returned)
        Object.assign(returned, { canceled: true, cancelAllowed: false });
    } else if (returning) {
      arrivalState.pendingReturns = [
        {
          returnChangeId: 12,
          itemId: 901,
          itemName: "버드로즈",
          pendingQuantity: 5,
          arrivalAllowed: true,
        },
      ];
      state.portions[1].quantity = 15;
      state.portions[1].amount = 120000;
      state.currentAmount = 920000;
      state.returnedPortions.push({
        itemId: 901,
        itemName: "버드로즈",
        spec: null,
        sourceChangeId: 11,
        quantity: 5,
        unitPrice: 8000,
        amount: 40000,
      });
    } else {
      state.portions[0].quantity = 80;
      state.portions[0].amount = 800000;
      state.portions.push({
        itemId: 901,
        itemName: "버드로즈",
        spec: null,
        sourceChangeId: 11,
        quantity: 20,
        unitPrice: 8000,
        amount: 160000,
      });
      state.currentAmount = 960000;
    }
    slip.totalAmount = state.currentAmount;
    state.history.content.unshift({
      id: canceling ? 13 : returning ? 12 : 11,
      itemId: 901,
      itemName: "버드로즈",
      type: canceling ? "CANCEL" : returning ? "RETURN" : "PRICE_ADJUSTMENT",
      sourceChangeId: returning || canceling ? 11 : null,
      canceledChangeId: canceling ? 12 : null,
      quantity: returning || canceling ? 5 : 20,
      previousUnitPrice: returning || canceling ? 8000 : 10000,
      nextUnitPrice: returning || canceling ? 0 : 8000,
      amountDelta: canceling ? 40000 : -40000,
      changeDate: "2026-10-08",
      reason: "품질 조정",
      createdAt: "2026-10-08T00:00:00",
      canceled: false,
      cancelAllowed: true,
    });
    state.history.totalElements = state.history.content.length;
    state.history.totalPages = 1;
    await route.fulfill({
      json: { data: { changeId: canceling ? 13 : returning ? 12 : 11 } },
    });
  });
  await page.goto("/sales/slips?slipId=900");
  async function clickHeaderAction(name: string) {
    const more = page.getByRole("button", {
      name: "상세 작업 더보기",
      exact: true,
    });
    if (
      (await more.isVisible()) &&
      !(await page.getByRole("button", { name, exact: true }).isVisible())
    )
      await more.click();
    await page.getByRole("button", { name, exact: true }).click();
  }
  await expect(
    page.getByRole("region", { name: "가격 조정·반품", exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "가격 조정", exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "반품 등록", exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "변경 이력", exact: true }),
  ).toHaveCount(0);
  await clickHeaderAction("가격 조정 및 반품");
  const dialog = page.getByRole("dialog", {
    name: "가격 조정 및 반품",
    exact: true,
  });
  await dialog.getByLabel("조정 수량").fill("20");
  await dialog.getByLabel("변경 단가").fill("8000");
  await dialog.getByLabel("처리일").fill("2026-10-08");
  await dialog.getByLabel("사유").fill("품질 조정");
  await dialog.getByRole("button", { name: "기록", exact: true }).click();
  await expect(
    dialog.getByText("응답 확인 필요", { exact: true }),
  ).toBeVisible();
  await dialog.getByRole("button", { name: "닫기", exact: true }).click();
  await page.reload();
  await clickHeaderAction("가격 조정 및 반품");
  const history = page.getByRole("dialog", {
    name: "가격 조정 및 반품",
    exact: true,
  });
  await history
    .getByRole("button", { name: "요청 결과 확인", exact: true })
    .click();
  await expect(
    history.getByText("원판매 금액 1,000,000원 · 현재 거래금액 960,000원", {
      exact: true,
    }),
  ).toBeVisible();
  await history.getByRole("button", { name: "닫기", exact: true }).click();
  expect(calls[1]).toEqual(calls[0]);
  const itemRow = page.getByRole("row").filter({ hasText: "버드로즈" });
  await expect(itemRow.getByText("조정", { exact: true })).toBeVisible();
  await expect(itemRow.getByText("20", { exact: true })).toBeVisible();
  await expect(itemRow.getByText("8,000", { exact: true })).toBeVisible();
  await expect(itemRow.getByText("160,000", { exact: true })).toBeVisible();
  await expect(
    itemRow.getByText("원단가 10,000", { exact: true }),
  ).toBeVisible();
  await expect(
    itemRow.getByText("원금액 1,000,000", { exact: true }),
  ).toBeVisible();
  await clickHeaderAction("가격 조정 및 반품");
  await dialog.getByRole("button", { name: "반품 등록", exact: true }).click();
  const returnDialog = dialog;
  await returnDialog.getByLabel("품목 · 가격 구간").selectOption("901:11");
  await returnDialog.getByLabel("반품 수량").fill("5");
  await returnDialog.getByLabel("처리일").fill("2026-10-08");
  await returnDialog.getByLabel("사유").fill("품질 조정");
  await returnDialog.getByRole("button", { name: "기록", exact: true }).click();
  await expect(
    dialog.getByRole("button", { name: "변경 이력", exact: true }),
  ).toHaveAttribute("aria-pressed", "true");
  await dialog.getByRole("button", { name: "닫기", exact: true }).click();
  await expect(itemRow.getByText("15", { exact: true })).toBeVisible();
  await expect(itemRow.getByText("120,000", { exact: true })).toBeVisible();
  await expect(
    itemRow.getByText("8,000", { exact: true }).first(),
  ).toBeVisible();
  await expect(itemRow.locator("del", { hasText: /^5$/ })).toBeVisible();
  await expect(itemRow.locator("del", { hasText: /^8,000$/ })).toBeVisible();
  await expect(itemRow.locator("del", { hasText: /^40,000$/ })).toBeVisible();
  await expect(itemRow.getByText(/원수량/)).toHaveCount(0);
  await clickHeaderAction("가격 조정 및 반품");
  await dialog.getByRole("button", { name: "농장 도착", exact: true }).click();
  await dialog
    .getByRole("button", { name: "실제 도착 등록", exact: true })
    .click();
  await dialog
    .getByRole("radio", {
      name: "이미 재고에 반영됨 · 기록만 등록",
      exact: true,
    })
    .check();
  await expect(dialog.getByLabel("반환품 품종")).toHaveCount(0);
  await expect(
    dialog.getByRole("button", { name: "배치 칸", exact: true }),
  ).toHaveCount(0);
  await dialog.getByLabel("실제 도착일").fill("2026-10-09");
  await dialog
    .getByRole("button", { name: "실제 도착 저장", exact: true })
    .click();
  await expect(dialog.getByRole("alert")).toContainText("도착 응답 확인 필요");
  await dialog.getByRole("button", { name: "닫기", exact: true }).click();
  await dialog.getByRole("button", { name: "닫기", exact: true }).click();
  await page.reload();
  await clickHeaderAction("가격 조정 및 반품");
  await dialog
    .getByRole("button", { name: "요청 결과 확인", exact: true })
    .click();
  await expect(
    dialog.getByRole("button", { name: "가격 조정", exact: true }),
  ).toBeEnabled();
  await dialog.getByRole("button", { name: "농장 도착", exact: true }).click();
  await expect(
    dialog.getByText("재고 반영 없이 기록만 등록", { exact: true }),
  ).toBeVisible();
  await expect(
    dialog.getByRole("button", { name: "실제 도착 등록", exact: true }),
  ).toBeDisabled();
  const arrivalCalls = calls.filter((call) =>
    call.path.endsWith("/12/arrivals"),
  );
  expect(arrivalCalls).toHaveLength(2);
  expect(arrivalCalls[1]).toEqual(arrivalCalls[0]);
  expect(arrivalCalls[0].body).toMatchObject({ quantity: 5, recordOnly: true });
  expect(arrivalCalls[0].body.details).toBeUndefined();
  await dialog.getByRole("button", { name: "변경 이력", exact: true }).click();
  await expect(
    dialog.getByRole("button", { name: "처리 취소", exact: true }),
  ).toHaveCount(1);
  await dialog.getByRole("button", { name: "농장 도착", exact: true }).click();
  await dialog
    .getByRole("button", { name: "도착 취소 정정", exact: true })
    .click();
  await expect(dialog.getByLabel("정정일")).toBeFocused();
  await dialog.getByLabel("정정일").fill("2026-10-10");
  await dialog.getByLabel("도착 취소 사유").fill("도착 오입력");
  await dialog
    .getByRole("button", { name: "도착 취소 저장", exact: true })
    .click();
  await expect(dialog.getByText(/도착 취소됨/)).toBeVisible();
  await dialog.getByRole("button", { name: "닫기", exact: true }).click();

  await clickHeaderAction("가격 조정 및 반품");
  await dialog.getByRole("button", { name: "변경 이력", exact: true }).click();
  await expect(
    history.getByText("원판매 금액 1,000,000원 · 현재 거래금액 920,000원", {
      exact: true,
    }),
  ).toBeVisible();
  await history
    .getByRole("button", { name: "처리 취소", exact: true })
    .first()
    .click();
  const cancellation = dialog;
  await expect(cancellation).toBeVisible();
  await expect(page.getByRole("dialog")).toHaveCount(1);
  await expect(cancellation.getByLabel("처리일")).toBeFocused();
  await cancellation.getByLabel("처리일").fill("2026-10-09");
  await cancellation.getByLabel("사유").fill("반품 정정");
  await cancellation.getByRole("button", { name: "기록", exact: true }).click();
  await expect(
    dialog.getByRole("button", { name: "변경 이력", exact: true }),
  ).toHaveAttribute("aria-pressed", "true");
  await dialog.getByRole("button", { name: "닫기", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(itemRow.locator("del")).toHaveCount(0);
  await expect(itemRow.getByText("20", { exact: true })).toBeVisible();
  await expect(itemRow.getByText("160,000", { exact: true })).toBeVisible();
  expect(calls[2].body.sourceChangeId).toBe(11);
  expect(calls[2].body.quantity).toBe(5);
  expect(calls[2].body.unitPrice).toBeUndefined();
  expect(calls[2].key).not.toBe(calls[0].key);
  await clickHeaderAction("인쇄");
  await expect(
    page.getByText("가격 조정·반품 반영 후 판매 구성", { exact: true }),
  ).toBeVisible();
  await expect(page.getByText(/20개 × 8,000원 = 160,000원/)).toBeVisible();
});

test("unfinished and canceled slips do not expose adjustment or return actions", async ({
  page,
  request,
  context,
}) => {
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
  const slip = {
    id: 900,
    slipNumber: "NO-CHANGES-900",
    saleDate: "2026-10-07",
    salesType: "DIRECT",
    partner: { id: 7, name: "성은", partnerType: "WHOLESALE" },
    totalAmount: 1000000,
    paidAmount: 0,
    remainingAmount: 1000000,
    paymentStatus: "미입금",
    salesStatus: "작성중",
    availableActions: [],
    financialReviewRequired: false,
    items: [
      {
        id: 901,
        itemName: "버드로즈",
        genus: "긴기아남",
        quantity: 100,
        unitPrice: 10000,
        amount: 1000000,
        allocations: [],
      },
    ],
  };
  await page.route("**/api/sales-slips/page?**", (route) =>
    route.fulfill({
      json: {
        data: {
          content: [slip],
          page: 0,
          size: 10,
          totalElements: 1,
          totalPages: 1,
        },
      },
    }),
  );
  await page.route("**/api/sales-slips/900", (route) =>
    route.fulfill({ json: { data: slip } }),
  );
  let reads = 0;
  await page.route("**/api/sales-slips/900/direct-changes?**", (route) => {
    reads += 1;
    return route.fulfill({
      json: {
        data: {
          documentId: 900,
          originalAmount: 1000000,
          currentAmount: 1000000,
          refundDueAmount: 0,
          availableTypes: [],
          portions: [],
          history: {
            content: [],
            page: 0,
            size: 10,
            totalElements: 0,
            totalPages: 0,
          },
        },
      },
    });
  });
  for (const status of ["작성중", "취소"]) {
    const before = reads;
    slip.salesStatus = status;
    await page.goto("/sales/slips?slipId=900");
    await expect.poll(() => reads).toBeGreaterThan(before);
    await expect(
      page.getByRole("button", { name: "가격 조정", exact: true }),
    ).toHaveCount(0);
    await expect(
      page.getByRole("button", { name: "반품 등록", exact: true }),
    ).toHaveCount(0);
    await expect(
      page.getByRole("button", { name: "가격 조정 및 반품", exact: true }),
    ).toHaveCount(0);
    await expect(
      page.getByRole("region", { name: "가격 조정·반품", exact: true }),
    ).toHaveCount(0);
  }
});
