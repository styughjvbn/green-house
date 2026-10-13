import { expect, test } from "@playwright/test";

test.use({ viewport: { width: 1180, height: 820 } });

test("filter popover stays open for internal clicks and closes for outside interactions", async ({
  page,
  context,
}) => {
  await context.addCookies([
    {
      name: "JSESSIONID",
      value: "isolated-ui-fixture",
      url: "http://127.0.0.1:13100",
    },
  ]);
  await page.goto("/work-records?scope=ALL&view=LIST");
  await page.mouse.move(1100, 700);
  await expect
    .poll(() =>
      page
        .locator(".app-shell-sidebar")
        .evaluate((element) => element.getBoundingClientRect().width),
    )
    .toBe(48);

  const trigger = page.getByRole("button", { name: /^필터 선택/ });
  const popover = page.getByRole("dialog", { name: "필터", exact: true });

  await trigger.click();
  await popover.getByText("필터", { exact: true }).click();
  await expect(popover).toBeVisible();
  await popover.getByText("기간", { exact: true }).click();
  await expect(popover).toBeVisible();

  await popover
    .getByRole("checkbox", {
      name: "미완료 또는 오늘 업데이트 된 작업",
      exact: true,
    })
    .check();
  await expect(popover).toBeVisible();
  await popover
    .getByLabel("작업 상태", { exact: true })
    .selectOption("PLANNED");
  await popover.getByLabel("시작일", { exact: true }).fill("2026-10-01");
  await popover.getByLabel("시작일", { exact: true }).evaluate((input) => {
    (input as HTMLInputElement).blur();
  });
  await expect(popover).toBeVisible();
  await popover.getByText("기간", { exact: true }).click();
  await expect(popover).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(popover).toHaveCount(0);
  await expect(trigger).toBeFocused();

  await trigger.click();
  await expect(popover.getByLabel("작업 상태", { exact: true })).toHaveValue(
    "PLANNED",
  );
  await expect(popover.getByLabel("시작일", { exact: true })).toHaveValue(
    "2026-10-01",
  );
  await page.getByLabel("키워드").click();
  await expect(popover).toHaveCount(0);
  await expect(page.getByLabel("키워드")).toBeFocused();

  await trigger.click();
  await popover.getByRole("button", { name: "필터 적용", exact: true }).focus();
  await page.keyboard.press("Tab");
  await expect(popover).toHaveCount(0);
  await expect(page.getByRole("button", { name: "목록 설정" })).toBeFocused();
});

test("management checkbox sits below the filter rows and applies, resets, and follows view changes", async ({
  page,
  context,
}) => {
  await context.addCookies([
    {
      name: "JSESSIONID",
      value: "isolated-ui-fixture",
      url: "http://127.0.0.1:13100",
    },
  ]);
  await page.goto("/work-records?scope=ALL&view=LIST");
  const trigger = page.getByRole("button", { name: /^필터 선택/ });
  const popover = page.getByRole("dialog", { name: "필터", exact: true });
  const checkboxName = "미완료 또는 오늘 업데이트 된 작업";
  const checkbox = popover.getByRole("checkbox", {
    name: checkboxName,
    exact: true,
  });
  const apply = popover.getByRole("button", { name: "필터 적용", exact: true });

  await trigger.click();
  await expect(popover.getByRole("checkbox")).toHaveCount(1);
  await expect(checkbox).not.toBeChecked();
  await expect(popover.getByText("조회 범위", { exact: true })).toHaveCount(0);
  const statusBounds = await popover
    .getByLabel("작업 상태", { exact: true })
    .boundingBox();
  const checkboxBounds = await checkbox.boundingBox();
  expect(checkboxBounds!.y).toBeGreaterThan(
    statusBounds!.y + statusBounds!.height,
  );
  expect(
    await checkbox
      .locator("..")
      .locator("..")
      .evaluate((element) => getComputedStyle(element).display),
  ).toBe("flex");

  await checkbox.check();
  expect(new URL(page.url()).searchParams.get("scope")).toBe("ALL");
  await expect(popover).toBeVisible();
  await expect(apply).toBeEnabled();
  await apply.click();
  await expect(popover).toHaveCount(0);
  await expect
    .poll(() => new URL(page.url()).searchParams.get("scope"))
    .toBe("MANAGEMENT");
  await expect(trigger).toHaveAccessibleName("필터 선택, 1개 적용");
  await page.reload();
  await trigger.click();
  await expect(checkbox).toBeChecked();
  await checkbox.uncheck();
  await apply.click();
  await expect
    .poll(() => new URL(page.url()).searchParams.get("scope"))
    .toBe("ALL");

  await trigger.click();
  await checkbox.check();
  await apply.click();
  await trigger.click();
  await popover.getByRole("button", { name: "초기화", exact: true }).click();
  await expect(checkbox).not.toBeChecked();
  await page.keyboard.press("Escape");
  await page.reload();
  await expect
    .poll(() => new URL(page.url()).searchParams.get("scope"))
    .toBe("ALL");

  await page.getByRole("button", { name: "캘린더 보기", exact: true }).click();
  await page.getByRole("checkbox", { name: checkboxName, exact: true }).check();
  await expect
    .poll(() => new URL(page.url()).searchParams.get("scope"))
    .toBe("MANAGEMENT");
  await page.getByRole("button", { name: "목록 보기", exact: true }).click();
  await trigger.click();
  await expect(checkbox).toBeChecked();
});

test("second toolbar row shows applied filters rather than drafts and resets them", async ({
  page,
  context,
}, testInfo) => {
  await context.addCookies([
    {
      name: "JSESSIONID",
      value: "isolated-ui-fixture",
      url: "http://127.0.0.1:13100",
    },
  ]);
  await page.goto(
    "/work-records?scope=MANAGEMENT&view=LIST&keyword=관수&from=2026-10-01&to=2026-10-13&status=PLANNED",
  );
  await page.mouse.move(1100, 700);
  await expect
    .poll(() =>
      page
        .locator(".app-shell-sidebar")
        .evaluate((element) => element.getBoundingClientRect().width),
    )
    .toBe(48);
  const row = page.getByRole("group", { name: "적용된 필터", exact: true });
  const badges = row.getByRole("listitem");
  const originalBadges = [
    "검색: 관수",
    "기간: 2026-10-01 ~ 2026-10-13",
    "상태: 계획",
    "미완료 또는 오늘 업데이트 된 작업",
  ];
  await expect(badges).toHaveText(originalBadges);
  const searchButton = page.getByRole("button", { name: "검색", exact: true });
  await expect(searchButton).toHaveCSS(
    "background-color",
    "rgb(238, 245, 237)",
  );
  const geometry = await row.evaluate((element) => {
    const badgeBounds = [...element.querySelectorAll("li")].map((badge) =>
      badge.getBoundingClientRect(),
    );
    const list = element.querySelector("ul")!;
    return {
      rightGap:
        element.getBoundingClientRect().right -
        element.querySelector(":scope > button")!.getBoundingClientRect().right,
      badgeRows: badgeBounds.map((badge) => badge.y),
      overflowing: list.scrollWidth > list.clientWidth,
    };
  });
  await expect(
    row.getByRole("button", { name: "전체 초기화", exact: true }),
  ).toBeVisible();
  expect(Math.abs(geometry.rightGap)).toBeLessThan(1);
  expect(new Set(geometry.badgeRows).size).toBe(1);
  expect(geometry.overflowing).toBe(true);
  const inputBounds = await page.getByLabel("키워드").boundingBox();
  const rowBounds = await row.boundingBox();
  expect(rowBounds!.y).toBeGreaterThan(inputBounds!.y + inputBounds!.height);
  await page.screenshot({
    path: testInfo.outputPath("applied-filters.png"),
    caret: "initial",
  });

  await page.getByLabel("키워드").fill("분갈이");
  await page.getByRole("button", { name: /^필터 선택/ }).click();
  const popover = page.getByRole("dialog", { name: "필터", exact: true });
  await popover
    .getByLabel("작업 상태", { exact: true })
    .selectOption("IN_PROGRESS");
  await popover.getByLabel("시작일", { exact: true }).fill("2026-10-05");
  await popover
    .getByRole("checkbox", {
      name: "미완료 또는 오늘 업데이트 된 작업",
      exact: true,
    })
    .uncheck();
  await expect(badges).toHaveText(originalBadges);
  await popover.getByRole("button", { name: "필터 적용", exact: true }).click();
  await expect(badges).toHaveText([
    "검색: 분갈이",
    "기간: 2026-10-05 ~ 2026-10-13",
    "상태: 진행 중",
  ]);

  await row.getByRole("button", { name: "전체 초기화", exact: true }).click();
  await expect(badges).toHaveCount(0);
  await expect(page.getByLabel("키워드")).toHaveValue("");
  await expect(row).toHaveCount(0);
  await page.keyboard.press("Escape");
  await page.getByLabel("키워드").fill("미적용 검색");
  await expect(row).toHaveCount(0);
  await expect(searchButton).toHaveCSS(
    "background-color",
    "rgb(238, 245, 237)",
  );
  await page
    .getByLabel("키워드")
    .evaluate((input) => (input as HTMLInputElement).blur());
  await expect(searchButton).toHaveCSS(
    "background-color",
    "rgb(255, 255, 255)",
  );
  await expect
    .poll(() =>
      ["keyword", "status", "from", "to", "scope"].every(
        (key) => !new URL(page.url()).searchParams.has(key),
      ),
    )
    .toBe(true);
});
