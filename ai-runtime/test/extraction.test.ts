import { test } from "node:test";
import assert from "node:assert/strict";
import sharp from "sharp";
import {
  extractWithResolutionFallback,
  resizePhoto,
} from "../src/extraction.js";
import {
  RuntimeError,
  inputPhotos,
  type AgentProvider,
  type PhotoInput,
  type RunContext,
  type SlipInput,
} from "../src/contracts.js";
const result = (confidence: number) => ({
  salesType: null,
  saleDate: null,
  partnerName: null,
  partnerId: null,
  memo: null,
  items: [],
  warnings: [],
  confidence,
});
async function photo(
  photoId: string,
  width: number,
  height: number,
): Promise<PhotoInput> {
  return {
    photoId,
    contentType: "image/png",
    contentBase64: (
      await sharp({
        create: { width, height, channels: 3, background: "#f0f0f0" },
      })
        .png()
        .toBuffer()
    ).toString("base64"),
    feedback: null,
  };
}
const request = (photos: PhotoInput[]): SlipInput => ({
  requestId: "resize-test",
  sessionId: "12345678-1234-1234-1234-123456789abc",
  toolToken: "t".repeat(43),
  timeoutSeconds: 120,
  photos,
});
const context = (): RunContext => ({
  signal: new AbortController().signal,
  maxToolCalls: 20,
  callTool: async () => [],
});

test("resizing fits either orientation, preserves small originals and never enlarges", async () => {
  for (const [w, h] of [
    [3000, 1500],
    [1500, 3000],
  ]) {
    const original = await photo("p", w, h);
    const before = original.contentBase64;
    const small = await resizePhoto(original, 1024);
    const large = await resizePhoto(original, 2048);
    assert.equal(small.longEdge, 1024);
    assert.equal(large.longEdge, 2048);
    const metadata = await sharp(
      Buffer.from(small.photo.contentBase64, "base64"),
    ).metadata();
    assert.equal(metadata.width! / metadata.height!, w / h);
    assert.equal(original.contentBase64, before);
  }
  const original = await photo("small", 800, 600);
  const resized = await resizePhoto(original, 1024);
  assert.equal(resized.photo.contentBase64, original.contentBase64);
  assert.equal(resized.longEdge, 800);
  assert.equal((await resizePhoto(original, 2048)).longEdge, 800);
  const middle = await photo("middle", 1500, 750);
  assert.equal((await resizePhoto(middle, 2048)).longEdge, 1500);
  const rotated = await sharp({
    create: { width: 600, height: 1200, channels: 3, background: "white" },
  })
    .jpeg()
    .withMetadata({ orientation: 6 })
    .toBuffer();
  const corrected = await resizePhoto(
    {
      photoId: "rotated",
      contentType: "image/jpeg",
      contentBase64: rotated.toString("base64"),
    },
    1024,
  );
  const metadata = await sharp(
    Buffer.from(corrected.photo.contentBase64, "base64"),
  ).metadata();
  assert.equal(metadata.width, 1024);
  assert.equal(metadata.height, 512);
  assert.equal(metadata.orientation, undefined);
});

test("batch retries only low-confidence larger originals and shares session, deadline and tool budget", async () => {
  const high = await photo("high", 3000, 1500);
  const low = { ...high, photoId: "low", feedback: "수량을 다시 확인" };
  const small = await photo("small", 800, 600);
  const input = request([high, low, small]);
  const calls: SlipInput[] = [];
  const contexts: RunContext[] = [];
  const provider: AgentProvider = {
    name: "mock",
    model: "mock",
    async run(input, ctx) {
      calls.push(input);
      contexts.push(ctx);
      return {
        photos: inputPhotos(input).map((p) => ({
          photoId: p.photoId,
          result: result(
            p.photoId === "high"
              ? 95
              : p.photoId === "small"
                ? 40
                : calls.length === 1
                  ? 55
                  : 90,
          ),
        })),
      };
    },
  };
  const output = (await extractWithResolutionFallback(
    provider,
    input,
    context(),
  )) as {
    photos: {
      photoId: string;
      result: ReturnType<typeof result> & {
        imageLongEdge: number;
        attempts: number;
      };
    }[];
  };
  assert.equal(calls.length, 2);
  assert.equal(calls[0].photos?.length, 3);
  assert.deepEqual(
    calls[1].photos?.map((p) => p.photoId),
    ["low"],
  );
  assert.equal(calls[1].photos![0].feedback, low.feedback);
  assert.equal(
    calls[1].photos![0].contentBase64,
    (await resizePhoto(low, 2048)).photo.contentBase64,
  );
  assert.equal(contexts[0].session, contexts[1].session);
  assert.equal(contexts[0].signal, contexts[1].signal);
  assert.equal(contexts[0].deadlineMs, contexts[1].deadlineMs);
  assert.equal(contexts[0].toolBudget, contexts[1].toolBudget);
  const byId = new Map(output.photos.map((p) => [p.photoId, p.result]));
  assert.equal(byId.get("high")!.attempts, 1);
  assert.equal(byId.get("high")!.imageLongEdge, 1024);
  assert.equal(byId.get("low")!.attempts, 2);
  assert.equal(byId.get("low")!.imageLongEdge, 2048);
  assert.equal(byId.get("low")!.confidence, 90);
  assert.equal(byId.get("small")!.attempts, 1);
  assert.equal(byId.get("small")!.imageLongEdge, 800);
  assert.match(byId.get("small")!.warnings.join(), /다시 촬영/);
});

test("unreadable photos are retried once and a low second score ends at original size below 2048", async () => {
  const input = request([await photo("p", 1500, 750)]);
  let calls = 0;
  const provider: AgentProvider = {
    name: "mock",
    model: "mock",
    async run() {
      calls++;
      return {
        photos: [{ photoId: "p", result: calls === 1 ? null : result(30) }],
      };
    },
  };
  const output = (await extractWithResolutionFallback(
    provider,
    input,
    context(),
  )) as {
    photos: {
      result: { imageLongEdge: number; attempts: number; warnings: string[] };
    }[];
  };
  assert.equal(calls, 2);
  assert.equal(output.photos[0].result.imageLongEdge, 1500);
  assert.equal(output.photos[0].result.attempts, 2);
  assert.match(output.photos[0].result.warnings.join(), /다시 촬영/);
});

test("a failed high-resolution retry preserves first-pass results and surfaces a warning", async () => {
  const input = request([await photo("p", 2500, 1250)]);
  let calls = 0;
  const provider: AgentProvider = {
    name: "mock",
    model: "mock",
    async run() {
      if (++calls === 2) throw new RuntimeError(429, "AI_TOOL_LIMIT");
      return { photos: [{ photoId: "p", result: result(60) }] };
    },
  };
  const output = (await extractWithResolutionFallback(
    provider,
    input,
    context(),
  )) as {
    photos: {
      result: {
        confidence: number;
        attempts: number;
        imageLongEdge: number;
        warnings: string[];
      };
    }[];
  };
  assert.equal(calls, 2);
  assert.equal(output.photos[0].result.confidence, 60);
  assert.equal(output.photos[0].result.attempts, 2);
  assert.equal(output.photos[0].result.imageLongEdge, 1024);
  assert.match(output.photos[0].result.warnings.join(), /첫 인식값을 유지/);
});

test("invalid confidence is rejected and cancellation never starts another model turn", async () => {
  const input = request([await photo("p", 2500, 1250)]);
  let calls = 0;
  const controller = new AbortController();
  const invalid: AgentProvider = {
    name: "mock",
    model: "mock",
    async run() {
      calls++;
      return { photos: [{ photoId: "p", result: result(101) }] };
    },
  };
  await assert.rejects(
    extractWithResolutionFallback(invalid, input, context()),
    (e: unknown) => (e as RuntimeError).code === "AI_INVALID_RESULT",
  );
  assert.equal(calls, 1);
  calls = 0;
  const cancelled: AgentProvider = {
    name: "mock",
    model: "mock",
    async run() {
      calls++;
      controller.abort();
      return { photos: [{ photoId: "p", result: result(20) }] };
    },
  };
  await assert.rejects(
    extractWithResolutionFallback(cancelled, input, {
      ...context(),
      signal: controller.signal,
    }),
  );
  assert.equal(calls, 1);
});

test("single-photo compatibility uses the same resizing and confidence policy", async () => {
  const original = await photo("single", 3000, 1500);
  let calls = 0;
  const provider: AgentProvider = {
    name: "mock",
    model: "mock",
    async run(input) {
      calls++;
      assert.equal(
        (await sharp(Buffer.from(input.contentBase64!, "base64")).metadata())
          .width,
        1024,
      );
      return result(80);
    },
  };
  const output = (await extractWithResolutionFallback(
    provider,
    {
      requestId: "single",
      toolToken: "t".repeat(43),
      timeoutSeconds: 120,
      contentType: original.contentType,
      contentBase64: original.contentBase64,
    },
    context(),
  )) as { confidence: number; imageLongEdge: number; attempts: number };
  assert.equal(calls, 1);
  assert.equal(output.confidence, 80);
  assert.equal(output.imageLongEdge, 1024);
  assert.equal(output.attempts, 1);
});

test("Codex reuses only the remaining tool budget and original deadline on another attempt", async () => {
  const { codexProvider } = await import("../src/providers/codex.js");
  const { imageBase64 } = await import("./fixtures.js");
  let remaining: unknown;
  let deadline: unknown;
  const shared = {
    ...context(),
    toolBudget: { used: 1 },
    maxToolCalls: 1,
    deadlineMs: Date.now() + 10000,
  };
  const provider = codexProvider(
    "mock",
    "/tmp/mock-home",
    "http://127.0.0.1:1",
    (options) => {
      const config = options.config as {
        mcp_servers: { greenhouse: { env: Record<string, string> } };
      };
      remaining = config.mcp_servers.greenhouse.env.AI_MAX_TOOL_CALLS;
      deadline = config.mcp_servers.greenhouse.env.AI_DEADLINE_MS;
      return {
        startThread() {
          return {
            async runStreamed() {
              return {
                events: (async function* () {
                  yield {
                    type: "item.started",
                    item: {
                      id: "call",
                      type: "mcp_tool_call",
                      server: "greenhouse",
                      tool: "search_partners",
                      arguments: { query: "test" },
                      status: "in_progress",
                    },
                  };
                })(),
              };
            },
          } as unknown as ReturnType<
            import("@openai/codex-sdk").Codex["startThread"]
          >;
        },
      };
    },
  );
  await assert.rejects(
    provider.run(
      {
        requestId: "budget-test",
        toolToken: "t".repeat(43),
        timeoutSeconds: 120,
        contentType: "image/png",
        contentBase64: imageBase64,
      },
      shared,
    ),
    (e: unknown) => (e as RuntimeError).code === "AI_TOOL_LIMIT",
  );
  assert.equal(remaining, "0");
  assert.equal(deadline, String(shared.deadlineMs));
  assert.equal(shared.toolBudget.used, 2);
});
