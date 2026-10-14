import { test, expect } from "@playwright/test";
import type { CreateSalesSlipPayload } from "../../src/features/sales/api/types";

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

for (const salesType of ["DIRECT", "AUCTION"] as const) {
  test(`${salesType} records completed facts without allocations and retries with the same key`, async ({
    page,
  }) => {
    const requests: {
      key: string | undefined;
      body: CreateSalesSlipPayload;
    }[] = [];
    let created: unknown;
    const varietySearches: string[] = [];
    await page.route("**/api/varieties?**", async (route) => {
      const keyword =
        new URL(route.request().url()).searchParams.get("keyword") ?? "";
      varietySearches.push(keyword);
      const varieties = [
        {
          id: 9,
          name: "미니퍼플",
          genus: "팔레놉시스",
          active: true,
          defaultPotSize: null,
        },
        {
          id: 10,
          name: "파이어버드",
          genus: "덴드로비움",
          active: true,
          defaultPotSize: null,
        },
      ].filter((variety) =>
        `${variety.name} ${variety.genus}`.includes(keyword),
      );
      await route.fulfill({
        json: {
          data: {
            content: varieties,
            page: 0,
            size: 20,
            totalElements: varieties.length,
            totalPages: 1,
          },
        },
      });
    });
    await page.route("**/api/sales-slips", async (route) => {
      const request = route.request();
      requests.push({
        key: request.headers()["idempotency-key"],
        body: request.postDataJSON(),
      });
      if (requests.length === 1) {
        await route.fulfill({
          status: 503,
          json: {
            error: { code: "TEST_FAILURE", message: "다시 시도하세요." },
          },
        });
        return;
      }
      const body = request.postDataJSON() as CreateSalesSlipPayload;
      created = {
        ...body,
        id: 99,

        partner: {
          id: salesType === "AUCTION" ? 8 : 7,
          name: "기록 거래처",
          partnerType: salesType === "AUCTION" ? "AUCTION_HOUSE" : "WHOLESALE",
        },
        totalAmount: salesType === "DIRECT" ? 300000 : 0,
        paidAmount: 0,
        remainingAmount: salesType === "DIRECT" ? 300000 : 0,
        auctionShipmentId: salesType === "AUCTION" ? 98 : null,
        expectedPaymentDate: body.saleDate,
        financialReviewRequired: false,
        availableActions: ["CANCEL"],
        items: body.items.map((item, index) => ({
          ...item,
          id: 100 + index,
          amount: item.quantity * item.unitPrice,
          auctionShipmentLotId: salesType === "AUCTION" ? 200 + index : null,
          auctionLot: null,
        })),
      };
      await route.fulfill({ status: 201, json: { data: created } });
    });
    await page.route("**/api/sales-slips/99", (route) =>
      route.fulfill({ json: { data: created } }),
    );
    await page.goto("/sales/slips");
    await page.getByRole("button", { name: "전표 작성", exact: true }).click();
    const form = page.getByRole("dialog", { name: "전표 작성", exact: true });
    await form.getByLabel("등록 방식").selectOption("RECORD_ONLY");
    if (salesType === "AUCTION")
      await form
        .getByRole("button", { name: "경매 판매", exact: true })
        .click();
    const partnerLabel = salesType === "AUCTION" ? "경매장" : "거래처";
    await form
      .getByRole("combobox", { name: partnerLabel, exact: true })
      .click();
    await page.getByRole("listbox").getByRole("option").first().click();
    await form
      .getByLabel(salesType === "AUCTION" ? "출하일" : "판매일", {
        exact: true,
      })
      .fill("2026-09-01");
    const variety = form.getByRole("combobox", {
      name: "품종명 / 속",
      exact: true,
    });
    const search = salesType === "DIRECT" ? "미니" : "팔레";
    await variety.fill(search);
    await page
      .getByRole("listbox")
      .getByRole("option", { name: "미니퍼플 / 팔레놉시스", exact: true })
      .click();
    await expect(
      form.getByText("미니퍼플 / 팔레놉시스", { exact: true }),
    ).toBeVisible();
    await expect(
      form.getByRole("textbox", { name: "속", exact: true }),
    ).toHaveCount(0);
    await form.getByLabel("수량", { exact: true }).fill("150");
    await form.getByLabel("단위", { exact: true }).fill("분");
    await form.getByLabel("상자 수", { exact: true }).fill("3");
    if (salesType === "DIRECT")
      await form.getByLabel("단가", { exact: true }).fill("2000");
    else await expect(form.getByLabel("단가", { exact: true })).toHaveCount(0);
    await expect(form.getByLabel("출고·출하")).toBeDisabled();
    await expect(form.getByText("난 묶음 선택", { exact: true })).toHaveCount(
      0,
    );
    await expect(form.getByText("난 묶음 배분", { exact: true })).toHaveCount(
      0,
    );
    const save = form.getByRole("button", {
      name: salesType === "AUCTION" ? "경매 출하 전표 저장" : "판매 전표 저장",
      exact: true,
    });
    await save.click();
    await expect(
      form.getByText("다시 시도하세요.", { exact: true }),
    ).toBeVisible();
    await save.click();
    await expect(form).not.toBeVisible();
    await expect(page).toHaveURL(/slipId=99/);
    await expect(
      page.getByText("난 묶음 연결 없음", { exact: true }),
    ).toBeVisible();
    expect(requests).toHaveLength(2);
    expect(requests[0].key).toBeTruthy();
    expect(requests[1]).toEqual(requests[0]);
    expect(requests[0].body.recordOnly).toBe(true);
    expect(requests[0].body.salesStatus).toBe(
      salesType === "AUCTION" ? "출하 완료" : "출고 완료",
    );
    expect(requests[0].body.items[0].allocations).toEqual([]);
    expect(requests[0].body.items[0].itemName).toBe("미니퍼플");
    expect(requests[0].body.items[0].genus).toBe("팔레놉시스");
    expect(varietySearches).toContain(search);
    expect(requests[0].body.initialReceipt).toBeNull();
  });
}
