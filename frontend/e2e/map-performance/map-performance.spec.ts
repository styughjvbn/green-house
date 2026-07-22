import {
  expect,
  test,
  type Locator,
  type Page,
  type Request,
  type Response,
} from "@playwright/test";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

const WARMUP_COUNT = readNonNegativeInteger("MAP_E2E_WARMUP", 5);
const ITERATION_COUNT = readPositiveInteger("MAP_E2E_ITERATIONS", 20);
const CONTINUOUS_SWIPE_STEPS = readPositiveInteger(
  "MAP_E2E_CONTINUOUS_STEPS",
  10,
);
const RESULT_PATH = path.resolve(
  process.cwd(),
  process.env.MAP_E2E_RESULT_FILE ?? "e2e-results/map-performance-before.json",
);

type WorkLocation = {
  houseId: number;
  houseNumber: number;
  physicalBedId: number;
  physicalBedNumber: number;
  bedZoneId: number;
  bedZoneName: string;
};

type WorkHistory = {
  sourceKind: string;
  workOperationId: number;
  workType: string;
  title: string;
  workDate: string;
  propagated: boolean;
  sourceScopeType: string;
  sourceScopeId: number | null;
  currentLocation: WorkLocation;
};

type OrchidGroup = {
  id: number;
  bedZoneId: number;
  varietyName: string;
};

type BedZone = {
  id: number;
  name: string;
  physicalBedId: number;
  orchidGroups: OrchidGroup[];
};

type PhysicalBed = {
  id: number;
  houseId: number;
  number: number;
  positionUnitCount: number;
  bedZones: BedZone[];
};

type House = {
  id: number;
  number: number;
  physicalBeds: PhysicalBed[];
};

type ApiEnvelope<T> = { data: T };

type HistoryResponseMetric = {
  apiResponseTimeMs: number;
  groupId: number;
  payloadBytes: number;
  records: WorkHistory[];
  requestStartedAtEpoch: number;
  status: number;
};

type IterationMetric = {
  apiRequestCount: number;
  apiResponseTimesMs: number[];
  apiStartDelayMs: number | null;
  clickToRenderMs: number;
  dataReadyMs?: number;
  payloadBytes: number;
  renderReadyMs?: number;
  responses: HistoryResponseMetric[];
};

type AccuracyResult = {
  actualTotalCount: number;
  directCount: number;
  duplicateCount: number;
  expectedTotalCount: number;
  houseCount: number;
  passed: boolean;
  physicalBedCount: number;
  propagatedCount: number;
  requestCount: number;
  requestDuplicateCount: number;
  sampleTitles: string[];
  violations: string[];
  zoneCount: number;
};

type ScenarioResult = {
  scenario: string;
  repetitions: number;
  p50: number;
  p95: number;
  max: number;
  apiRequestCount: ReturnType<typeof summarize>;
  apiResponseTimeMs: ReturnType<typeof summarize>;
  apiStartDelayMs: ReturnType<typeof summarize>;
  responseSizeBytes: ReturnType<typeof summarize>;
  samples: Array<{
    apiRequestCount: number;
    apiStartDelayMs: number | null;
    clickToRenderMs: number;
    dataReadyMs?: number;
    payloadBytes: number;
    renderReadyMs?: number;
  }>;
  accuracy?: AccuracyResult;
};

type BaselineResult = {
  generatedAt: string;
  environment: {
    baseUrl: string;
    browserVersion: string;
    continuousSwipeSteps: number;
    iterations: number;
    viewport: { width: number; height: number };
    warmups: number;
    workers: number;
  };
  seed: {
    bedZoneCount: number;
    houseCount: number;
    orchidGroupCount: number;
    physicalBedCount: number;
    positionUnitCountSum: number;
  };
  scenarios: Record<string, ScenarioResult>;
  renderingStability: Record<string, unknown>;
  consoleErrors: string[];
  failedRequests: string[];
  comparisonTable: Array<{
    scenario: string;
    beforeP50: number;
    beforeP95: number;
  }>;
};

class HistoryRequestCollector {
  private active = false;
  private pending = 0;
  private requestStartedAt = new WeakMap<Request, number>();
  private requestStarts: number[] = [];
  private responses: HistoryResponseMetric[] = [];

  constructor(private readonly page: Page) {
    page.on("request", (request) => {
      if (!this.active || !isWorkHistoryUrl(request.url())) return;
      this.pending += 1;
      this.requestStartedAt.set(request, Date.now());
    });
    page.on("response", (response) => {
      if (!this.requestStartedAt.has(response.request())) return;
      void this.collectResponse(response);
    });
  }

  begin() {
    this.active = true;
    this.pending = 0;
    this.responses = [];
    this.requestStarts = [];
    this.requestStartedAt = new WeakMap<Request, number>();
  }

  async finish(metricStartEpoch: number): Promise<IterationMetric> {
    await expect
      .poll(() => this.responses.length > 0 && this.pending === 0, {
        timeout: 120_000,
      })
      .toBeTruthy();
    this.active = false;

    const responseTimes = this.responses
      .map((item) => item.apiResponseTimeMs)
      .filter((value) => value >= 0);
    const firstRequestEpoch = Math.min(...this.requestStarts);

    return {
      apiRequestCount: this.responses.length,
      apiResponseTimesMs: responseTimes,
      apiStartDelayMs: Number.isFinite(firstRequestEpoch)
        ? round(firstRequestEpoch - metricStartEpoch)
        : null,
      clickToRenderMs: 0,
      payloadBytes: this.responses.reduce(
        (sum, item) => sum + item.payloadBytes,
        0,
      ),
      responses: [...this.responses],
    };
  }

  private async collectResponse(response: Response) {
    const request = response.request();
    const startedAt = this.requestStartedAt.get(request) ?? Date.now();
    try {
      await response.finished();
      const body = await response.body();
      const payload = JSON.parse(body.toString("utf8")) as ApiEnvelope<
        WorkHistory[]
      >;
      const timing = request.timing();
      const responseEnd = timing.responseEnd;
      this.responses.push({
        apiResponseTimeMs:
          responseEnd >= 0 ? round(responseEnd) : round(Date.now() - startedAt),
        groupId: parseGroupId(request.url()),
        payloadBytes: body.byteLength,
        records: payload.data,
        requestStartedAtEpoch: startedAt,
        status: response.status(),
      });
      this.requestStarts.push(startedAt);
    } finally {
      this.pending -= 1;
    }
  }
}

test("난 묶음 관리 맵 리팩터링 전 정확성·성능 기준값", async ({
  browser,
  page,
}, testInfo) => {
  test.slow();
  const baseUrl = testInfo.project.use.baseURL as string;
  const consoleErrors: string[] = [];
  const failedRequests: string[] = [];
  const correctnessFailures: string[] = [];
  const scenarioResults: Record<string, ScenarioResult> = {};
  const renderingStability: Record<string, unknown> = {};
  let houses: House[] = [];

  page.on("console", (message) => {
    if (message.type() === "error") consoleErrors.push(message.text());
  });
  page.on("pageerror", (error) => consoleErrors.push(error.message));
  page.on("requestfailed", (request) => {
    const failure = request.failure();
    const url = request.url();

    const isExpectedAbort =
      failure?.errorText === "net::ERR_ABORTED" &&
      url.includes("_rsc=");

    if (isExpectedAbort) {
      return;
    }

    failedRequests.push(
      `${request.method()} ${url}: ${failure?.errorText}`,
    );
  });
  page.on("response", (response) => {
    if (isWorkHistoryUrl(response.url()) && response.status() >= 400) {
      failedRequests.push(
        `${response.request().method()} ${response.url()}: HTTP ${response.status()}`,
      );
    }
  });



  await page.context().addCookies([
    {
      name: "JSESSIONID",
      value: "map-performance-e2e",
      url: baseUrl,
      httpOnly: true,
      sameSite: "Lax",
    },
  ]);

  try {
    const structureResponse = await page.request.get(`${baseUrl}/api/houses`);
    expect(structureResponse.ok()).toBeTruthy();
    houses = ((await structureResponse.json()) as ApiEnvelope<House[]>).data;
    validateSeedStructure(houses);

    await page.goto("/orchid-groups?bedCount=3", {
      waitUntil: "domcontentloaded",
      timeout: 180_000,
    });
    await waitForHistoryDom(page, "HOUSE", houses[0]!.id);

    const collector = new HistoryRequestCollector(page);
    const houseTargets = houses.slice(1).concat(houses[0]).slice(0, 8);
    scenarioResults.houseHistory = await runSelectionScenario({
      page,
      collector,
      scenario: "동 이력 표시",
      targets: houseTargets.map((house) => ({
        id: house.id,
        scope: scopeForHouse(house),
      })),
      selectionType: "HOUSE",
      action: async (target) => {
        await page
          .getByLabel("동으로 빠른 이동")
          .selectOption(String(target.id));
      },
    });

    await navigateToHouseAndWait(page, houses[Math.floor(houses.length / 2)]!);
    const swipeResults = await runSwipeScenarios(page, collector, houses);
    Object.assign(scenarioResults, swipeResults.scenarios);
    Object.assign(renderingStability, swipeResults.stability);

    const firstHouse = houses[0]!;
    await navigateToHouseAndWait(page, firstHouse);
    const bedTargets = firstHouse.physicalBeds.slice(0, 2);
    scenarioResults.physicalBedHistory = await runSelectionScenario({
      page,
      collector,
      scenario: "다이 이력 표시",
      targets: bedTargets.map((bed) => ({
        id: bed.id,
        scope: scopeForBed(bed, firstHouse),
      })),
      selectionType: "PHYSICAL_BED",
      action: async (target) => {
        await clickPhysicalBed(page, target.id);
      },
    });

    const firstBed = firstHouse.physicalBeds[0]!;
    const firstZone = firstBed.bedZones[0]!;
    scenarioResults.orchidGroupHistory = await runSelectionScenario({
      page,
      collector,
      scenario: "난 묶음 이력 표시",
      targets: firstZone.orchidGroups.slice(0, 2).map((group) => ({
        id: group.id,
        scope: scopeForGroup(group, firstZone, firstBed, firstHouse),
      })),
      selectionType: "ORCHID_GROUP",
      action: async (target) => {
        await clickOrchidGroup(page, target.id);
      },
    });

    Object.assign(renderingStability, await inspectRenderingStability(page));

    for (const [key, result] of Object.entries(scenarioResults)) {
      if (result.accuracy && !result.accuracy.passed) {
        correctnessFailures.push(
          `${key}: ${result.accuracy.violations.join(", ")}`,
        );
      }
    }
  } finally {
    const seed = summarizeSeed(houses);
    const result: BaselineResult = {
      generatedAt: new Date().toISOString(),
      environment: {
        baseUrl,
        browserVersion: browser.version(),
        continuousSwipeSteps: CONTINUOUS_SWIPE_STEPS,
        iterations: ITERATION_COUNT,
        viewport: { width: 1180, height: 820 },
        warmups: WARMUP_COUNT,
        workers: 1,
      },
      seed,
      scenarios: scenarioResults,
      renderingStability,
      consoleErrors,
      failedRequests,
      comparisonTable: Object.values(scenarioResults).map((scenario) => ({
        scenario: scenario.scenario,
        beforeP50: scenario.p50,
        beforeP95: scenario.p95,
      })),
    };
    await mkdir(path.dirname(RESULT_PATH), { recursive: true });
    await writeFile(
      RESULT_PATH,
      `${JSON.stringify(result, null, 2)}\n`,
      "utf8",
    );
  }

  if (
    correctnessFailures.length > 0 ||
    consoleErrors.length > 0 ||
    failedRequests.length > 0
  ) {
    console.log("correctnessFailures:", correctnessFailures);
    console.log("consoleErrors:", consoleErrors);
    console.log("failedRequests:", failedRequests);

    await page.pause();
  }

  expect(correctnessFailures, "정확성 검증 실패").toEqual([]);
  expect(consoleErrors, "브라우저 콘솔 오류").toEqual([]);
  expect(failedRequests, "브라우저 요청 실패").toEqual([]);
});

type SelectionScope = {
  groups: OrchidGroup[];
  zones: BedZone[];
  beds: PhysicalBed[];
  houses: House[];
};

async function runSelectionScenario({
  page,
  collector,
  scenario,
  targets,
  selectionType,
  action,
}: {
  page: Page;
  collector: HistoryRequestCollector;
  scenario: string;
  targets: Array<{ id: number; scope: SelectionScope }>;
  selectionType: "HOUSE" | "PHYSICAL_BED" | "BED_ZONE" | "ORCHID_GROUP";
  action: (target: { id: number; scope: SelectionScope }) => Promise<void>;
}): Promise<ScenarioResult> {
  const samples: IterationMetric[] = [];
  let accuracy: AccuracyResult | undefined;
  const totalRuns = WARMUP_COUNT + ITERATION_COUNT;

  for (let index = 0; index < totalRuns; index += 1) {
    const target = targets[index % targets.length]!;
    collector.begin();
    const start = await browserNow(page);
    await action(target);
    const metric = await collector.finish(start.epoch);
    const historySection = await waitForHistoryDom(
      page,
      selectionType,
      target.id,
    );
    const ready = await renderSettledAt(page);
    metric.clickToRenderMs = round(ready.startTime - start.startTime);

    if (index >= WARMUP_COUNT) {
      accuracy ??= await verifyAccuracy(
        historySection,
        target.scope,
        metric.responses,
      );
      metric.responses = [];
      samples.push(metric);
    }
  }

  return buildScenarioResult(scenario, samples, accuracy);
}

async function runSwipeScenarios(
  page: Page,
  collector: HistoryRequestCollector,
  houses: House[],
) {
  const middleHouse = houses[Math.floor(houses.length / 2)]!;
  await navigateToHouseAndWait(page, middleHouse);
  const dragSelectionBefore = await currentSelectionLabel(page);
  const dragStartBedId = await visibleStartBedId(page);
  await dragCarousel(page, "next");
  await expect
    .poll(() => visibleStartBedId(page), { timeout: 120_000 })
    .not.toBe(dragStartBedId);
  const dragSelectionAfter = await currentSelectionLabel(page);
  const dragStability = {
    dragSelectionBefore,
    dragSelectionAfter,
    dragPreservedSelection: dragSelectionAfter === dragSelectionBefore,
  };
  await navigateToHouseAndWait(page, middleHouse);

  const nextSamples: IterationMetric[] = [];
  const previousSamples: IterationMetric[] = [];
  const warmSamples: IterationMetric[] = [];
  const totalRuns = WARMUP_COUNT + ITERATION_COUNT;
  const coldHouseTargets = houses.slice(3, -3);

  for (let index = 0; index < totalRuns; index += 1) {
    const nextHouse = coldHouseTargets[index % coldHouseTargets.length]!;
    const previousHouse =
      coldHouseTargets[
        (index + Math.ceil(coldHouseTargets.length / 2)) %
          coldHouseTargets.length
      ]!;
    await navigateToHouseAndWait(page, nextHouse);
    const nextMetric = await measureSwipe(page, collector, "next");
    const warmMetric = await measureSwipe(page, collector, "previous");
    await navigateToHouseAndWait(page, previousHouse);
    const previousMetric = await measureSwipe(page, collector, "previous");
    if (index >= WARMUP_COUNT) {
      nextMetric.responses = [];
      previousMetric.responses = [];
      warmMetric.responses = [];
      nextSamples.push(nextMetric);
      previousSamples.push(previousMetric);
      warmSamples.push(warmMetric);
    }
  }

  const continuousSamples: IterationMetric[] = [];
  let stability: Record<string, unknown> = {};
  for (let index = 0; index < totalRuns; index += 1) {
    await navigateToHouseAndWait(page, middleHouse);
    const mountedBefore = await mountedBedCount(page);
    collector.begin();
    const startedAt = await browserNow(page);
    for (let step = 0; step < CONTINUOUS_SWIPE_STEPS; step += 1) {
      await page.getByLabel("다음 다이").click();
      await page.waitForTimeout(25);
    }
    for (let step = 0; step < CONTINUOUS_SWIPE_STEPS; step += 1) {
      await page.getByLabel("이전 다이").click();
      await page.waitForTimeout(25);
    }
    await waitForHistoryDom(page, "HOUSE", houses[0]!.id);
    const metric = await collector.finish(startedAt.epoch);
    const ready = await renderSettledAt(page);
    metric.clickToRenderMs = round(ready.startTime - startedAt.startTime);
    const mountedAfter = await mountedBedCount(page);
    const visibleAfter = await visibleBedIds(page);
    const expectedFinalGroupIds = new Set(
      scopeForHouse(middleHouse).groups.map((group) => group.id),
    );
    const finalResponseGroupIds = latestRequestWaveGroupIds(
      metric.responses,
      expectedFinalGroupIds.size,
    );
    const finalScopeMatches = setsEqual(
      expectedFinalGroupIds,
      finalResponseGroupIds,
    );
    const finalHistoryReady =
      (await page.getByTestId("selected-history").getAttribute("aria-busy")) ===
      "false";
    stability = {
      ...dragStability,
      mountedBedsBefore: mountedBefore,
      mountedBedsAfter: mountedAfter,
      domGrowth: mountedAfter - mountedBefore,
      visibleBedsAfter: visibleAfter.length,
      visibleBedIdsAfter: visibleAfter,
      withinVisibleBedLimit: mountedAfter <= 4,
      duplicateHistoryRequestCount: duplicateRequestCount(metric.responses),
      expectedFinalGroupIds: [...expectedFinalGroupIds],
      finalResponseGroupIds: [...finalResponseGroupIds],
      finalScopeMatches,
      finalHistoryReady,
    };
    if (index >= WARMUP_COUNT) {
      metric.responses = [];
      continuousSamples.push(metric);
    }
  }

  return {
    scenarios: {
      nextBedCold: buildScenarioResult("다음 다이 Cold 스와이프", nextSamples),
      previousBedCold: buildScenarioResult(
        "이전 다이 Cold 스와이프",
        previousSamples,
      ),
      warmBedRevisit: buildScenarioResult("다이 Warm 재방문", warmSamples),
      continuousSwipe: {
        ...buildScenarioResult("연속 스와이프", continuousSamples),
        accuracy: {
          actualTotalCount: 0,
          directCount: 0,
          duplicateCount: 0,
          expectedTotalCount: 0,
          houseCount: 0,
          physicalBedCount: 0,
          propagatedCount: 0,
          requestCount: 0,
          requestDuplicateCount: Number(
            stability.duplicateHistoryRequestCount ?? 0,
          ),
          sampleTitles: [],
          passed:
            stability.finalHistoryReady === true &&
            stability.finalScopeMatches === true,
          violations: [
            ...(stability.finalHistoryReady === true
              ? []
              : ["연속 스와이프 후 작업 이력이 준비되지 않음"]),
            ...(stability.finalScopeMatches === true
              ? []
              : ["늦은 응답이 최종 선택 범위와 다른 작업 이력을 표시함"]),
          ],
          zoneCount: 0,
        },
      },
    },
    stability,
  };
}

async function dragCarousel(page: Page, direction: "next" | "previous") {
  const carousel = await physicalBedMapSection(page);
  const box = await carousel.boundingBox();
  if (!box) throw new Error("다이 캐러셀 위치를 찾지 못했습니다.");
  const startX =
    direction === "next" ? box.x + box.width * 0.75 : box.x + box.width * 0.25;
  const endX =
    direction === "next" ? box.x + box.width * 0.25 : box.x + box.width * 0.75;
  const y = box.y + box.height * 0.5;
  await page.mouse.move(startX, y);
  await page.mouse.down();
  await page.mouse.move(endX, y, { steps: 8 });
  await page.mouse.up();
}

async function measureSwipe(
  page: Page,
  collector: HistoryRequestCollector,
  direction: "next" | "previous",
): Promise<IterationMetric> {
  const beforeId = await visibleStartBedId(page);
  collector.begin();
  const start = await browserNow(page);
  await page
    .getByLabel(direction === "next" ? "다음 다이" : "이전 다이")
    .click();
  await expect
    .poll(() => visibleStartBedId(page), { timeout: 120_000 })
    .not.toBe(beforeId);
  const metric = await collector.finish(start.epoch);
  await expect(page.getByTestId("selected-history")).toHaveAttribute(
    "aria-busy",
    "false",
    { timeout: 180_000 },
  );
  const ready = await renderSettledAt(page);
  const responseReadyEpoch = Math.max(
    start.epoch,
    ...metric.responses.map(
      (response) => response.requestStartedAtEpoch + response.apiResponseTimeMs,
    ),
  );
  metric.clickToRenderMs = round(ready.startTime - start.startTime);
  metric.dataReadyMs = round(responseReadyEpoch - start.epoch);
  metric.renderReadyMs = metric.clickToRenderMs;
  return metric;
}

async function navigateToHouseAndWait(page: Page, house: House) {
  const houseSelect = page.getByLabel("동으로 빠른 이동");

  await houseSelect.selectOption(String(house.id));
  await expect(houseSelect).toHaveValue(String(house.id));

  const expectedBedIds = new Set(house.physicalBeds.map((bed) => bed.id));

  await expect
    .poll(
      async () => {
        const visible = new Set(await visibleBedIds(page));
        return setsEqual(expectedBedIds, visible);
      },
      { timeout: 120_000 },
    )
    .toBe(true);

  await expect(page.getByTestId("selected-history")).toHaveAttribute(
    "aria-busy",
    "false",
    { timeout: 180_000 },
  );
}

async function clickPhysicalBed(page: Page, bedId: number) {
  await page.getByTestId(`physical-bed-${bedId}`).click();
}

async function clickBedZone(page: Page, zoneId: number) {
  await page.getByTestId(`bed-zone-${zoneId}`).click();
}

async function clickOrchidGroup(page: Page, groupId: number) {
  await page.getByTestId(`orchid-group-${groupId}`).click();
}

async function waitForHistoryDom(
  page: Page,
  type: "HOUSE" | "PHYSICAL_BED" | "BED_ZONE" | "ORCHID_GROUP",
  targetId: number,
) {
  const section = page.getByTestId("selected-history");
  await expect(section).toBeVisible({ timeout: 120_000 });
  await expect(section).toHaveAttribute("data-selection-type", type, {
    timeout: 120_000,
  });
  if (type !== "HOUSE") {
    await expect(section).toHaveAttribute(
      "data-selection-id",
      String(targetId),
      {
        timeout: 120_000,
      },
    );
  }
  await expect(section).toHaveAttribute("aria-busy", "false", {
    timeout: 180_000,
  });
  await renderSettledAt(page);
  return section;
}

async function browserNow(page: Page) {
  return page.evaluate(() => ({
    epoch: performance.timeOrigin + performance.now(),
    startTime: performance.now(),
  }));
}

async function renderSettledAt(page: Page) {
  return page.evaluate(
    () =>
      new Promise<{ epoch: number; startTime: number }>((resolve) => {
        requestAnimationFrame(() => {
          requestAnimationFrame(() => {
            const startTime = performance.now();
            resolve({ epoch: performance.timeOrigin + startTime, startTime });
          });
        });
      }),
  );
}

async function physicalBedMapSection(page: Page) {
  return page.getByTestId("map-root");
}

async function mountedBedCount(page: Page) {
  return page.locator('[data-testid^="physical-bed-"]').count();
}

async function visibleBedIds(page: Page) {
  const mapSection = await physicalBedMapSection(page);
  const sectionBox = await mapSection.boundingBox();

  if (!sectionBox) {
    return [];
  }

  return page
    .locator('[data-testid^="physical-bed-"]')
    .evaluateAll((beds, bounds) => {
      return beds
        .filter((bed) => {
          const box = bed.getBoundingClientRect();

          const centerX = box.left + box.width / 2;
          const centerY = box.top + box.height / 2;

          return (
            centerX >= bounds.x &&
            centerX <= bounds.x + bounds.width &&
            centerY >= bounds.y &&
            centerY <= bounds.y + bounds.height
          );
        })
        .map((bed) =>
          Number((bed as HTMLElement).dataset.testid?.split("-").at(-1)),
        );
    }, sectionBox);
}

async function visibleStartBedId(page: Page) {
  const mapSection = await physicalBedMapSection(page);
  const sectionBox = await mapSection.boundingBox();

  if (!sectionBox) {
    return "";
  }

  const visible = await page
    .locator('[data-testid^="physical-bed-"]')
    .evaluateAll((beds, bounds) => {
      return beds
        .map((bed) => {
          const box = bed.getBoundingClientRect();

          return {
            id: Number((bed as HTMLElement).dataset.testid?.split("-").at(-1)),
            left: box.left,
            centerX: box.left + box.width / 2,
          };
        })
        .filter(
          (item) =>
            item.centerX >= bounds.x && item.centerX <= bounds.x + bounds.width,
        )
        .sort((a, b) => a.left - b.left);
    }, sectionBox);

  return visible[0]?.id ?? 0;
}

async function currentSelectionLabel(page: Page) {
  return (
    (await page
      .getByTestId("selected-history")
      .getAttribute("data-selection-type")) ?? "NONE"
  );
}

function latestRequestWaveGroupIds(
  responses: HistoryResponseMetric[],
  expectedCount: number,
) {
  return new Set(
    [...responses]
      .sort(
        (left, right) =>
          right.requestStartedAtEpoch - left.requestStartedAtEpoch,
      )
      .slice(0, expectedCount)
      .map((response) => response.groupId),
  );
}

function setsEqual(left: Set<number>, right: Set<number>) {
  return left.size === right.size && [...left].every((item) => right.has(item));
}

async function verifyAccuracy(
  historySection: Locator,
  scope: SelectionScope,
  responses: HistoryResponseMetric[],
): Promise<AccuracyResult> {
  const records = responses.flatMap((response) => response.records);
  const uniqueRecords = deduplicateHistory(records);

  const groupIds = new Set(scope.groups.map((group) => group.id));
  const zoneIds = new Set(scope.zones.map((zone) => zone.id));
  const bedIds = new Set(scope.beds.map((bed) => bed.id));
  const houseIds = new Set(scope.houses.map((house) => house.id));

  const expectedByScope = {
    ORCHID_GROUP: scope.groups.length * 60,
    BED_ZONE: scope.zones.length * 20,
    PHYSICAL_BED: scope.beds.length * 10,
    HOUSE: scope.houses.length * 10,
  };

  const expectedTotalCount = Object.values(expectedByScope).reduce(
    (sum, count) => sum + count,
    0,
  );

  const actualByScope = countBy(
    uniqueRecords,
    (record) => record.sourceScopeType,
  );

  const requestedGroupIds = new Set(
    responses.map((response) => response.groupId),
  );

  const requestDuplicateCount = duplicateRequestCount(responses);

  /*
   * 원본 응답 전체에서 중복된 작업 이력 수를 측정한다.
   * 중복 API 호출로 인해 같은 데이터가 반복 수집된 경우도 포함한다.
   * 기준값으로만 기록하며 테스트 실패 조건으로 사용하지 않는다.
   */
  const duplicateCount = records.length - new Set(records.map(historyKey)).size;

  const violations: string[] = [];

  /*
   * 요청 횟수와 중복 요청은 리팩터링 전후 비교 지표로만 기록한다.
   * 요청 대상이 현재 선택 범위와 다른 경우만 정확성 실패로 처리한다.
   */

  if (!setsEqual(groupIds, requestedGroupIds)) {
    violations.push("선택 범위와 work-history 요청 대상이 다름");
  }

  /*
   * 응답에 포함된 작업 이력 개수를 범위별로 검증한다.
   */

  for (const [scopeType, expectedCount] of Object.entries(expectedByScope)) {
    const actualCount = actualByScope[scopeType] ?? 0;

    if (actualCount !== expectedCount) {
      violations.push(`${scopeType} ${actualCount}건(예상 ${expectedCount}건)`);
    }
  }

  /*
   * 응답의 각 작업 이력이 현재 선택 범위에 속하는지 검증한다.
   */

  for (const record of uniqueRecords) {
    const belongsToScope =
      (record.sourceScopeType === "ORCHID_GROUP" &&
        record.sourceScopeId != null &&
        groupIds.has(record.sourceScopeId)) ||
      (record.sourceScopeType === "BED_ZONE" &&
        record.sourceScopeId != null &&
        zoneIds.has(record.sourceScopeId)) ||
      (record.sourceScopeType === "PHYSICAL_BED" &&
        record.sourceScopeId != null &&
        bedIds.has(record.sourceScopeId)) ||
      (record.sourceScopeType === "HOUSE" &&
        record.sourceScopeId != null &&
        houseIds.has(record.sourceScopeId));

    if (!belongsToScope) {
      violations.push(`범위 밖 작업 ${record.workOperationId}`);
      break;
    }
  }

  /*
   * 중복 제거 후 전체 작업 이력 개수를 검증한다.
   */

  if (uniqueRecords.length !== expectedTotalCount) {
    violations.push(
      `통합 이력 ${uniqueRecords.length}건(예상 ${expectedTotalCount}건)`,
    );
  }

  /*
   * 화면에 렌더링된 작업 이력 목록을 확인한다.
   *
   * 정확성 검증은 전체 항목을 사용하고,
   * 결과 파일에는 처음 두 항목만 표본으로 저장한다.
   */

  const historyTitles = await historySection
    .getByTestId("history-item")
    .allTextContents();

  const sampleTitles = historyTitles.slice(0, 2);

  const latestRecord = [...uniqueRecords].sort(compareHistoryDesc)[0];

  if (latestRecord) {
    const latestDateDigits = normalizeDateDigits(latestRecord.workDate);

    const latestDateShortDigits =
      latestDateDigits.length === 8
        ? latestDateDigits.slice(2)
        : latestDateDigits;

    const normalizedHistoryTitles = historyTitles.map(normalizeDateDigits);

    const latestDateVisible = normalizedHistoryTitles.some(
      (titleDigits) =>
        titleDigits.includes(latestDateDigits) ||
        titleDigits.includes(latestDateShortDigits),
    );

    if (!latestDateVisible) {
      violations.push(
        `최근 작업 목록에 최신 작업일 ${latestRecord.workDate}이 없음`,
      );
    }
  }

  /*
   * HTTP 응답 상태를 검증한다.
   */

  const failedResponse = responses.find((response) => response.status !== 200);

  if (failedResponse) {
    violations.push(`work-history 응답 상태 ${failedResponse.status}`);
  }

  return {
    actualTotalCount: uniqueRecords.length,
    directCount: actualByScope.ORCHID_GROUP ?? 0,
    duplicateCount,
    expectedTotalCount,
    houseCount: actualByScope.HOUSE ?? 0,
    physicalBedCount: actualByScope.PHYSICAL_BED ?? 0,
    propagatedCount: uniqueRecords.filter((record) => record.propagated).length,
    requestCount: responses.length,
    requestDuplicateCount,
    sampleTitles,
    passed: violations.length === 0,
    violations,
    zoneCount: actualByScope.BED_ZONE ?? 0,
  };
}

function normalizeDateDigits(value: string): string {
  return value.replace(/\D/g, "");
}

function scopeForGroup(
  group: OrchidGroup,
  zone: BedZone,
  bed: PhysicalBed,
  house: House,
): SelectionScope {
  return { groups: [group], zones: [zone], beds: [bed], houses: [house] };
}

function scopeForZone(
  zone: BedZone,
  bed: PhysicalBed,
  house: House,
): SelectionScope {
  return {
    groups: zone.orchidGroups,
    zones: [zone],
    beds: [bed],
    houses: [house],
  };
}

function scopeForBed(bed: PhysicalBed, house: House): SelectionScope {
  return {
    groups: bed.bedZones.flatMap((zone) => zone.orchidGroups),
    zones: bed.bedZones,
    beds: [bed],
    houses: [house],
  };
}

function scopeForHouse(house: House): SelectionScope {
  return {
    groups: house.physicalBeds.flatMap((bed) =>
      bed.bedZones.flatMap((zone) => zone.orchidGroups),
    ),
    zones: house.physicalBeds.flatMap((bed) => bed.bedZones),
    beds: house.physicalBeds,
    houses: [house],
  };
}

function buildScenarioResult(
  scenario: string,
  metrics: IterationMetric[],
  accuracy?: AccuracyResult,
): ScenarioResult {
  const durations = metrics.map((metric) => metric.clickToRenderMs);
  const apiStartDelays = metrics
    .map((metric) => metric.apiStartDelayMs)
    .filter((value): value is number => value != null);
  const result: ScenarioResult = {
    scenario,
    repetitions: metrics.length,
    p50: percentile(durations, 50),
    p95: percentile(durations, 95),
    max: durations.length ? Math.max(...durations) : 0,
    apiRequestCount: summarize(metrics.map((metric) => metric.apiRequestCount)),
    apiResponseTimeMs: summarize(
      metrics.flatMap((metric) => metric.apiResponseTimesMs),
    ),
    apiStartDelayMs: summarize(apiStartDelays),
    responseSizeBytes: summarize(metrics.map((metric) => metric.payloadBytes)),
    samples: metrics.map((metric) => ({
      apiRequestCount: metric.apiRequestCount,
      apiStartDelayMs: metric.apiStartDelayMs,
      clickToRenderMs: metric.clickToRenderMs,
      dataReadyMs: metric.dataReadyMs,
      payloadBytes: metric.payloadBytes,
      renderReadyMs: metric.renderReadyMs,
    })),
  };
  if (accuracy) result.accuracy = accuracy;
  return result;
}

function deduplicateHistory(records: WorkHistory[]) {
  const unique = new Map<string, WorkHistory>();
  for (const record of records) {
    if (!unique.has(historyKey(record))) unique.set(historyKey(record), record);
  }
  return [...unique.values()];
}

function historyKey(record: WorkHistory) {
  return `${record.sourceKind}-${record.workOperationId}`;
}

function compareHistoryDesc(a: WorkHistory, b: WorkHistory) {
  if (a.workDate !== b.workDate) return b.workDate.localeCompare(a.workDate);
  return b.workOperationId - a.workOperationId;
}

function duplicateRequestCount(responses: HistoryResponseMetric[]) {
  const counts = countBy(responses, (response) => String(response.groupId));
  return Object.values(counts).reduce(
    (sum, count) => sum + Math.max(0, count - 1),
    0,
  );
}

function countBy<T>(items: T[], key: (item: T) => string) {
  return items.reduce<Record<string, number>>((counts, item) => {
    const value = key(item);
    counts[value] = (counts[value] ?? 0) + 1;
    return counts;
  }, {});
}

function summarize(values: number[]) {
  return {
    total: round(values.reduce((sum, value) => sum + value, 0)),
    p50: percentile(values, 50),
    p95: percentile(values, 95),
    max: values.length ? round(Math.max(...values)) : 0,
  };
}

function percentile(values: number[], target: number) {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.max(0, Math.ceil((target / 100) * sorted.length) - 1);
  return round(sorted[index]!);
}

function round(value: number) {
  return Math.round(value * 100) / 100;
}

function isWorkHistoryUrl(url: string) {
  return /\/api\/orchid-groups\/\d+\/work-history(?:\?|$)/.test(url);
}

function parseGroupId(url: string) {
  const match = url.match(/\/orchid-groups\/(\d+)\/work-history/);
  return Number(match?.[1]);
}

function validateSeedStructure(houses: House[]) {
  expect(houses).toHaveLength(15);
  expect(houses.flatMap((house) => house.physicalBeds)).toHaveLength(45);
  expect(
    houses.flatMap((house) =>
      house.physicalBeds.flatMap((bed) => bed.bedZones),
    ),
  ).toHaveLength(90);
  const beds = houses.flatMap((house) => house.physicalBeds);
  const groups = beds.flatMap((bed) =>
    bed.bedZones.flatMap((zone) => zone.orchidGroups),
  );
  const expectedGroups = beds.reduce(
    (sum, bed) => sum + Math.floor(bed.positionUnitCount) * bed.bedZones.length,
    0,
  );
  expect(groups).toHaveLength(expectedGroups);
}

function summarizeSeed(houses: House[]) {
  const beds = houses.flatMap((house) => house.physicalBeds);
  const zones = beds.flatMap((bed) => bed.bedZones);
  return {
    bedZoneCount: zones.length,
    houseCount: houses.length,
    orchidGroupCount: zones.flatMap((zone) => zone.orchidGroups).length,
    physicalBedCount: beds.length,
    positionUnitCountSum: beds.reduce(
      (sum, bed) => sum + Number(bed.positionUnitCount),
      0,
    ),
  };
}

async function inspectRenderingStability(page: Page) {
  const mountedBeds = await mountedBedCount(page);
  const visibleBeds = (await visibleBedIds(page)).length;
  const orchidGroupNodes = await page
    .locator('[data-testid^="orchid-group-"]')
    .count();
  return {
    mountedBeds,
    visibleBeds,
    orchidGroupNodes,
    withinVisibleBedLimit: mountedBeds <= 4,
  };
}

function readNonNegativeInteger(name: string, fallback: number) {
  const value = Number(process.env[name]);

  return Number.isInteger(value) && value >= 0 ? value : fallback;
}

function readPositiveInteger(name: string, fallback: number) {
  const value = Number(process.env[name]);

  return Number.isInteger(value) && value > 0 ? value : fallback;
}
