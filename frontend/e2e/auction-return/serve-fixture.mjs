import http from "node:http";
import {
  mkdtempSync,
  symlinkSync,
  copyFileSync,
  cpSync,
  rmSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { spawn } from "node:child_process";

const frontend = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const app = mkdtempSync(resolve(tmpdir(), "auction-return-ui-"));
cpSync(resolve(frontend, "src"), resolve(app, "src"), { recursive: true });
for (const name of ["public", "node_modules"])
  symlinkSync(resolve(frontend, name), resolve(app, name));
for (const name of [
  "package.json",
  "tsconfig.json",
  "next.config.ts",
  "postcss.config.mjs",
])
  copyFileSync(resolve(frontend, name), resolve(app, name));
const lot = {
  id: 42,
  shipmentDate: "2026-10-01",
  auctionMarket: "테스트 경매장",
  itemName: "호접란",
  varietyName: "반환 테스트 품종",
  shipmentGrade: "A",
  boxes: 1,
  shippedQuantity: 30,
  soldQuantity: 0,
  waitingQuantity: 30,
  returnedQuantity: 0,
  returnConfirmableQuantity: 30,
  returnConfirmedDate: null,
  currentStatus: "REAUCTION_WAITING",
  latestAuctionDate: "2026-10-07",
  failedCount: 1,
  totalAmount: 0,
  inspectionStatus: "NORMAL",
  memo: null,
  quantityAdjustmentAllowed: false,
  attempts: [],
  statusHistory: [],
};
const zone = {
  id: 6,
  physicalBedId: 2,
  physicalBedNumber: 1,
  houseId: 1,
  houseNumber: 1,
  name: "반환 구역",
  side: "LEFT",
  zoneType: "NORMAL",
  sortOrder: 1,
  active: true,
  memo: null,
  orchidGroups: [],
};
const bed = {
  id: 2,
  houseId: 1,
  houseNumber: 1,
  number: 1,
  displayOrder: 1,
  lengthCm: 100,
  widthCm: 100,
  wireCount: 1,
  supportIntervalCm: 10,
  positionUnitCount: 10,
  positionUnitLabel: "칸",
  memo: null,
  bedZones: [zone],
};
let summary,
  arrivals,
  calls,
  receipts,
  fail,
  rejectCancel,
  cashEvents,
  allocationEvents;
function reset() {
  summary = {
    lotId: 42,
    decisionId: null,
    method: null,
    decidedQuantity: null,
    pendingQuantity: 30,
    disposedQuantity: 0,
    inferredReturnQuantity: 0,
    decisionChangeAllowed: true,
    arrivalAllowed: false,
    availableMethods: ["REAUCTION", "FARM_RETURN", "AUCTION_DISPOSAL"],
  };
  arrivals = [];
  calls = [];
  cashEvents = [];
  allocationEvents = [];
  receipts = new Map();
  fail = false;
  rejectCancel = false;
}
reset();
const pageOf = (rows, page = 0) => ({
  content: rows.slice(page * 10, (page + 1) * 10),
  page,
  size: 10,
  totalElements: rows.length,
  totalPages: Math.ceil(rows.length / 10),
});
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, "http://127.0.0.1:14100");
  const chunks = [];
  for await (const part of req) chunks.push(part);
  const body = chunks.length
    ? JSON.parse(Buffer.concat(chunks).toString())
    : {};
  function send(data, status = 200) {
    res.writeHead(status, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ data, message: null }));
  }
  if (url.pathname === "/__control") {
    if (body.reset) reset();
    if (body.allocationSeed)
      cashEvents = [
        {
          id: 101,
          partnerId: 7,
          eventType: "PAYMENT_RECEIVED",
          eventDate: "2026-10-08",
          amount: 1500,
          unappliedAmount: 1500,
          status: "UNAPPLIED",
          targetType: "NONE",
          targetId: null,
        },
        {
          id: 102,
          partnerId: 7,
          eventType: "PAYMENT_RECEIVED",
          eventDate: "2026-10-08",
          amount: 500,
          unappliedAmount: 500,
          status: "UNAPPLIED",
          targetType: "NONE",
          targetId: null,
        },
      ];
    if (body.loseNextResponse) fail = true;
    if (body.rejectCancel != null) rejectCancel = body.rejectCancel;
    return send({ calls });
  }
  if (url.pathname === "/api/auth/context")
    return send({ businessDate: "2026-10-08", timeZone: "Asia/Seoul" });
  if (url.pathname === "/api/auth/me")
    return send({ username: "UI 테스트", role: "ADMIN" });
  const partner = {
    id: 7,
    name: "수납 테스트 거래처",
    partnerType: "WHOLESALE",
    active: true,
  };
  if (url.pathname === "/api/business-partners/options")
    return send(pageOf([partner]));
  if (url.pathname === "/api/business-partners/7/option") return send(partner);
  if (url.pathname === "/api/business-partners/7/balance-summary")
    return send({
      partnerId: 7,
      partnerName: partner.name,
      creditBalance: 0,
      receivableBalance: 0,
      unappliedPaymentAmount: cashEvents
        .filter(
          (e) => e.eventType === "PAYMENT_RECEIVED" && e.status !== "CANCELLED",
        )
        .reduce((sum, e) => sum + e.unappliedAmount, 0),
    });

  function receiptResponse(event) {
    return {
      id: event.id,
      partnerId: 7,
      paymentDate: event.eventDate,
      amount: event.amount,
      availableAmount: event.unappliedAmount,
      status: event.status,
      originalTargetType: event.targetType,
      originalTargetId: event.targetId,
      allocationAllowed:
        event.status !== "CANCELLED" && event.unappliedAmount > 0,
      correctionAllowed:
        event.status !== "CANCELLED" && event.unappliedAmount < event.amount,
      reviewRequired: false,
      depositorName: null,
      memo: null,
    };
  }
  if (url.pathname === "/api/payment-allocation-metadata")
    return send({ targetTypes: ["SALES_SLIP", "AUCTION_PROCEEDS"] });
  if (
    req.method === "GET" &&
    url.pathname === "/api/business-partners/7/payment-receipts"
  )
    return send(
      pageOf(
        cashEvents
          .filter((e) => e.eventType === "PAYMENT_RECEIVED")
          .map(receiptResponse),
        Number(url.searchParams.get("page") ?? 0),
      ),
    );
  if (
    req.method === "GET" &&
    /^\/api\/business-partners\/7\/payment-receipts\/\d+$/.test(url.pathname)
  )
    return send(
      receiptResponse(
        cashEvents.find((e) => e.id === Number(url.pathname.split("/").at(-1))),
      ),
    );
  if (req.method === "GET" && url.pathname.endsWith("/allocations")) {
    const id = Number(url.pathname.split("/").at(-2));
    return send(
      pageOf(
        allocationEvents.filter((e) => e.receiptId === id),
        Number(url.searchParams.get("page") ?? 0),
      ),
    );
  }
  if (
    req.method === "GET" &&
    url.pathname === "/api/business-partners/7/payment-allocation-targets"
  ) {
    const type = url.searchParams.get("targetType");
    return send(
      pageOf(
        [11, 12]
          .map((id) => {
            const paid = allocationEvents
              .filter(
                (e) =>
                  e.targetId === id &&
                  e.targetType === type &&
                  e.status === "CONFIRMED",
              )
              .reduce((total, e) => total + e.amount, 0);
            return {
              id,
              targetType: type,
              sourceReference: `전표-${id}`,
              receivableAmount: 1000,
              paidAmount: paid,
              availableAmount: 1000 - paid,
              allocationAllowed: paid < 1000,
              correctionAllowed: true,
              reviewRequired: false,
            };
          })
          .filter((e) =>
            e.sourceReference.includes(url.searchParams.get("keyword") ?? ""),
          ),
        Number(url.searchParams.get("page") ?? 0),
      ),
    );
  }
  if (
    req.method === "POST" &&
    [
      "/api/business-partners/7/payment-allocations",
      "/api/business-partners/7/payment-allocation-corrections",
    ].includes(url.pathname)
  ) {
    calls.push({ path: url.pathname, body });
    if (receipts.has(body.idempotencyKey))
      return send(receipts.get(body.idempotencyKey));
    const canceled = [];
    for (const id of body.cancellationIds ?? []) {
      const event = allocationEvents.find((e) => e.id === id);
      event.status = "CANCELLED";
      event.cancellationAllowed = false;
      cashEvents.find((e) => e.id === event.receiptId).unappliedAmount +=
        event.amount;
      canceled.push(1000 + id);
    }
    const added = body.allocations.map((line) => {
      const event = {
        ...line,
        id: allocationEvents.length + 1,
        allocationDate: body.allocationDate ?? body.correctionDate,
        status: "CONFIRMED",
        cancellationAllowed: true,
      };
      allocationEvents.push(event);
      cashEvents.find((e) => e.id === line.receiptId).unappliedAmount -=
        line.amount;
      return event.id;
    });
    for (const event of cashEvents)
      if (event.eventType === "PAYMENT_RECEIVED")
        event.status =
          event.unappliedAmount === 0
            ? "FULLY_APPLIED"
            : event.unappliedAmount === event.amount
              ? "UNAPPLIED"
              : "PARTIALLY_APPLIED";
    const result = { allocationIds: added, cancellationIds: canceled };
    receipts.set(body.idempotencyKey, structuredClone(result));
    if (fail) {
      fail = false;
      req.socket.destroy();
      return;
    }
    return send(result);
  }
  if (url.pathname === "/api/partner-payment-events/page")
    return send(
      pageOf(
        [...cashEvents].reverse(),
        Number(url.searchParams.get("page") ?? 0),
      ),
    );
  if (
    req.method === "POST" &&
    url.pathname.startsWith("/api/business-partners/7/payment-receipts")
  ) {
    calls.push({ path: url.pathname, body });
    if (receipts.has(body.idempotencyKey))
      return send(receipts.get(body.idempotencyKey));
    let result;
    if (url.pathname.endsWith("/cancel")) {
      const original = cashEvents.find(
        (e) => e.id === Number(url.pathname.split("/").at(-2)),
      );
      original.status = "CANCELLED";
      original.unappliedAmount = 0;
      original.unassignedCancellationAllowed = false;
      result = {
        ...original,
        id: cashEvents.length + 1,
        eventType: "ADJUSTMENT",
        status: "CONFIRMED",
        parentEventId: original.id,
        eventDate: body.correctionDate,
        memo: body.reason,
      };
    } else {
      result = {
        id: cashEvents.length + 1,
        partnerId: 7,
        partnerName: partner.name,
        eventType: "PAYMENT_RECEIVED",
        eventDate: body.paymentDate,
        amount: body.amount,
        unappliedAmount: body.amount,
        targetType: "NONE",
        targetId: null,
        parentEventId: null,
        paymentMethod: body.paymentMethod,
        depositorName: body.depositorName,
        description: "대상 미지정 수납",
        status: "UNAPPLIED",
        memo: body.memo,
        createdBy: "UI 테스트",
        unassignedCancellationAllowed: true,
      };
    }
    cashEvents.push(result);
    receipts.set(body.idempotencyKey, structuredClone(result));
    if (fail) {
      fail = false;
      req.socket.destroy();
      return;
    }
    return send(result);
  }
  if (url.pathname === "/api/auction-lots" && req.method === "GET")
    return send(pageOf([lot]));
  if (url.pathname === "/api/auction-lots/summary")
    return send({
      lotCount: 1,
      shippedQuantity: 30,
      soldQuantity: 0,
      waitingQuantity: summary.pendingQuantity,
      returnedQuantity: arrivals
        .filter((a) => !a.canceledAt)
        .reduce((n, a) => n + a.quantity, 0),
      reviewRequiredCount: 0,
      totalAmount: 0,
    });
  if (url.pathname === "/api/auction-lots/42")
    return send({ ...lot, waitingQuantity: summary.pendingQuantity });
  if (url.pathname === "/api/houses")
    return send([
      {
        id: 1,
        number: 1,
        name: "반환 테스트 농장",
        memo: null,
        physicalBeds: [bed],
      },
    ]);
  if (url.pathname === "/api/varieties/genera")
    return send({
      genera: ["호접란"],
      varieties: [{ id: 9, genus: "호접란", name: "반환 테스트 품종" }],
    });
  if (url.pathname === "/api/farm-status/orchid-management/bed-order")
    return send([{ id: 2, houseId: 1, houseNumber: 1, number: 1 }]);
  if (url.pathname === "/api/farm-status/orchid-management")
    return send({ beds: [bed], bedOrder: [], totalBeds: 1 });
  if (url.pathname === "/api/auction-lots/42/follow-up" && req.method === "GET")
    return send(summary);
  if (url.pathname === "/api/auction-lots/42/arrivals" && req.method === "GET")
    return send(pageOf(arrivals, Number(url.searchParams.get("page") ?? 0)));
  if (
    req.method === "POST" &&
    url.pathname.startsWith("/api/auction-lots/42/")
  ) {
    calls.push({ path: url.pathname, body });
    if (receipts.has(body.idempotencyKey))
      return send(receipts.get(body.idempotencyKey));
    let arrival = null;
    if (url.pathname.endsWith("/follow-up"))
      summary = {
        ...summary,
        decisionId: 1,
        method: body.method,
        decidedQuantity: 30,
        arrivalAllowed: body.method === "FARM_RETURN",
      };
    else if (url.pathname.endsWith("/arrivals")) {
      arrival = {
        id: 1,
        lotId: 42,
        decisionId: 1,
        quantity: body.details.quantity,
        arrivalDate: body.arrivalDate,
        worker: "UI 테스트",
        orchidGroupId: 901,
        creationMutationId: 902,
        createdAt: "2026-10-08T00:00:00",
        canceledAt: null,
        cancellationMutationId: null,
        cancellationReason: null,
        cancellationAllowed: true,
      };
      arrivals = [arrival];
      summary = {
        ...summary,
        pendingQuantity: 30 - arrival.quantity,
        decisionChangeAllowed: false,
        availableMethods: [],
      };
    } else if (url.pathname.endsWith("/cancel")) {
      if (rejectCancel) {
        res.writeHead(409, { "Content-Type": "application/json" });
        return res.end(
          JSON.stringify({
            error: {
              code: "AUCTION_ARRIVAL_COMPENSATION_BLOCKED",
              message: "후속 사용을 먼저 정정하세요.",
              details: [],
            },
          }),
        );
      }
      arrival = {
        ...arrivals[0],
        canceledAt: "2026-10-08T01:00:00",
        cancellationMutationId: 903,
        cancellationReason: body.reason,
        cancellationAllowed: false,
      };
      arrivals = [arrival];
      summary = {
        ...summary,
        pendingQuantity: 30,
        decisionChangeAllowed: true,
        availableMethods: ["REAUCTION", "FARM_RETURN", "AUCTION_DISPOSAL"],
      };
    }
    const result = structuredClone({ followUp: summary, arrival });
    receipts.set(body.idempotencyKey, result);
    if (fail) {
      fail = false;
      return req.socket.destroy();
    }
    return send(result);
  }
  res.writeHead(404, { "Content-Type": "application/json" });
  res.end(
    JSON.stringify({
      error: {
        code: "FIXTURE_ROUTE_MISSING",
        message: url.pathname,
        details: [],
      },
    }),
  );
});
server.listen(14100, "127.0.0.1");
const next = spawn(
  process.execPath,
  [
    resolve(frontend, "node_modules/next/dist/bin/next"),
    "dev",
    app,
    "--webpack",
    "--hostname",
    "127.0.0.1",
    "--port",
    "13100",
  ],
  {
    cwd: app,
    stdio: "inherit",
    env: {
      ...process.env,
      BACKEND_API_URL: "http://127.0.0.1:14100/api",
      NEXT_PUBLIC_API_BASE_URL: "/api",
      DEMO_MODE: "false",
      NEXT_TELEMETRY_DISABLED: "1",
    },
  },
);
function stop() {
  next.kill("SIGTERM");
  server.close();
}
process.on("SIGTERM", stop);
process.on("SIGINT", stop);
next.on("exit", (code) => {
  server.close();
  rmSync(app, { recursive: true, force: true });
  process.exit(code ?? 0);
});
