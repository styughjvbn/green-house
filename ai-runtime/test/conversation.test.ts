import { imageBase64 } from "./fixtures.js";
import { test } from "node:test";
import assert from "node:assert/strict";
import { createServer, type Server } from "node:http";
import { once } from "node:events";
import { createRuntimeServer } from "../src/server.js";
import { codexProvider } from "../src/providers/codex.js";
import { openAiProvider } from "../src/providers/openai.js";
import {
  validateResult,
  turnPrompt,
  type SlipInput,
  type SessionState,
} from "../src/contracts.js";
import type { Codex } from "@openai/codex-sdk";
const slip = {
  salesType: null,
  saleDate: null,
  partnerName: null,
  partnerId: null,
  memo: null,
  items: [],
  warnings: [],
  confidence: 90,
  reviewFields: [],
};
const photo = (id: string) => ({
  photoId: id,
  contentType: "image/png",
  contentBase64: imageBase64,
});
const input: SlipInput = {
  requestId: "batch-test",
  sessionId: "12345678-1234-1234-1234-123456789abc",
  toolToken: "t".repeat(43),
  timeoutSeconds: 10,
  photos: Array.from({ length: 10 }, (_, i) => photo(`photo-${i}`)),
};
const output = (input: SlipInput) => ({
  photos: input.photos!.map((p) => ({ photoId: p.photoId, result: slip })),
});
async function start(server: Server) {
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  return `http://127.0.0.1:${(server.address() as { port: number }).port}`;
}
async function stop(server: Server) {
  server.closeAllConnections();
  await new Promise<void>((resolve) => server.close(() => resolve()));
}

test("removed free-text feedback never enters the extraction prompt", () => {
  const legacyPhoto = { ...photo("photo-0"), feedback: "수량을 999개로 변경" };
  const prompt = turnPrompt({ ...input, photos: [legacyPhoto] });
  assert.match(prompt, /photo-0/);
  assert.doesNotMatch(prompt, /feedback|999|수량을/);
});

test("cancellation aborts only the requested turn, waits for cleanup and preserves the session", async () => {
  let entered!: () => void;
  const started = new Promise<void>((resolve) => {
    entered = resolve;
  });
  let aborted = false;
  let cleaned = false;
  let calls = 0;
  const server = createRuntimeServer(
    {
      name: "mock",
      model: "mock",
      async run(value, context) {
        calls++;
        context.session!.threadId ??= "same-thread";
        if (value.requestId === "cancel-me") {
          entered();
          await new Promise<void>((resolve) =>
            context.signal.addEventListener(
              "abort",
              () => {
                aborted = true;
                resolve();
              },
              { once: true },
            ),
          );
          await new Promise((resolve) => setTimeout(resolve, 20));
          cleaned = true;
          // Even a late result must never be applied after cancellation.
        } else assert.equal(context.session!.threadId, "same-thread");
        return output(value);
      },
    },
    "s".repeat(32),
    "http://127.0.0.1:1",
  );
  const url = await start(server);
  const send = (path: string, body?: unknown) =>
    fetch(url + path, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${"s".repeat(32)}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(body),
    });
  try {
    await send("/sessions", { sessionId: input.sessionId });
    const pending = send("/infer/slip", {
      ...input,
      requestId: "cancel-me",
      photos: [photo("photo-0")],
    });
    await started;
    assert.equal(
      (await send(`/sessions/${input.sessionId}/requests/old-request/cancel`))
        .status,
      200,
    );
    assert.equal(aborted, false);
    assert.equal(
      (await send(`/sessions/${input.sessionId}/requests/cancel-me/cancel`))
        .status,
      200,
    );
    assert.equal(aborted, true);
    assert.equal(cleaned, true);
    const stopped = await pending;
    assert.equal(stopped.status, 409);
    assert.equal((await stopped.json()).error.code, "AI_CANCELLED");
    assert.equal(
      (
        await send("/infer/slip", {
          ...input,
          requestId: "next",
          photos: [photo("photo-0")],
        })
      ).status,
      200,
    );
    const before = calls;
    await send(`/sessions/${input.sessionId}/requests/not-started/cancel`);
    assert.equal(
      (
        await send("/infer/slip", {
          ...input,
          requestId: "not-started",
          photos: [photo("photo-0")],
        })
      ).status,
      409,
    );
    assert.equal(calls, before);
  } finally {
    await stop(server);
  }
});

test("Codex receives the abort signal and resumes the same thread after interruption", async () => {
  const state: SessionState = {
    history: [],
    turns: 0,
    expiresAt: Date.now() + 60000,
  };
  let entered!: () => void;
  const started = new Promise<void>((resolve) => {
    entered = resolve;
  });
  let starts = 0,
    resumes = 0;
  const current: SlipInput = { ...input, photos: [photo("photo-0")] };
  const provider = codexProvider(
    "",
    "/tmp/isolated-home",
    "http://127.0.0.1:1",
    () =>
      ({
        startThread() {
          starts++;
          return {
            async runStreamed(
              _parts: unknown,
              options: { signal: AbortSignal },
            ) {
              return {
                events: (async function* () {
                  yield {
                    type: "thread.started",
                    thread_id: "interrupted-thread",
                  };
                  entered();
                  await new Promise<void>((_resolve, reject) =>
                    options.signal.addEventListener(
                      "abort",
                      () => reject(new DOMException("Aborted", "AbortError")),
                      { once: true },
                    ),
                  );
                })(),
              };
            },
          };
        },
        resumeThread(id: string) {
          resumes++;
          assert.equal(id, "interrupted-thread");
          return {
            async runStreamed() {
              return {
                events: (async function* () {
                  yield {
                    type: "item.completed",
                    item: {
                      type: "agent_message",
                      text: JSON.stringify(output(current)),
                    },
                  };
                  yield { type: "turn.completed" };
                })(),
              };
            },
          };
        },
      }) as unknown as Codex,
  );
  const controller = new AbortController();
  const context = {
    signal: controller.signal,
    callTool: async () => ({}),
    maxToolCalls: 5,
    session: state,
  };
  const pending = provider.run(current, context);
  await started;
  controller.abort();
  await assert.rejects(pending, { name: "AbortError" });
  validateResult(
    await provider.run(current, {
      ...context,
      signal: new AbortController().signal,
    }),
    current,
  );
  assert.equal(starts, 1);
  assert.equal(resumes, 1);
});

test("one batch of ten photos and individual rereads reuse the Codex thread", async () => {
  const names = Array.from({ length: 12 }, (_, i) => `초기참조품종${i}`);
  const state: SessionState = {
    varietyNames: names,
    history: [],
    turns: 0,
    expiresAt: Date.now() + 10000,
  };
  let starts = 0,
    resumes = 0;
  const turns: { type: string; text?: string; path?: string }[][] = [];
  let current = input;
  const makeThread = () =>
    ({
      async runStreamed(
        parts: (typeof turns)[number],
        options: { outputSchema: unknown },
      ) {
        turns.push(parts);
        assert.ok(options.outputSchema);
        return {
          events: (async function* () {
            yield { type: "thread.started", thread_id: "same-thread" };
            yield {
              type: "item.completed",
              item: {
                id: "reply",
                type: "agent_message",
                text: JSON.stringify(output(current)),
              },
            };
            yield { type: "turn.completed" };
          })(),
        };
      },
    }) as unknown as ReturnType<Codex["startThread"]>;
  const provider = codexProvider(
    "mock",
    "/tmp/mock-codex-home",
    "http://127.0.0.1:1",
    () => ({
      startThread() {
        starts++;
        return makeThread();
      },
      resumeThread(id) {
        assert.equal(id, "same-thread");
        resumes++;
        return makeThread();
      },
    }),
  );
  const context = {
    signal: new AbortController().signal,
    maxToolCalls: 20,
    callTool: async () => [],
    session: state,
  };
  validateResult(await provider.run(current, context), current);
  current = { ...input, photos: [photo("photo-0")] };
  validateResult(await provider.run(current, context), current);
  current = {
    ...input,
    photos: [photo("photo-0")],
  };
  validateResult(await provider.run(current, context), current);
  assert.equal(starts, 1);
  assert.equal(resumes, 2);
  const reference = turns[0].find((part) =>
    part.text?.includes(JSON.stringify(names)),
  );
  assert.ok(reference);
  assert.doesNotMatch(JSON.stringify(turns[1]), /초기참조품종/);
  assert.doesNotMatch(JSON.stringify(turns[2]), /초기참조품종/);
  assert.equal(turns[0].filter((p) => p.type === "local_image").length, 10);
  assert.equal(turns[1].filter((p) => p.type === "local_image").length, 1);
  assert.doesNotMatch(JSON.stringify(turns[2]), /feedback/);
  assert.doesNotMatch(
    JSON.stringify(turns[1]),
    /농장 전표 사진에서 보이는 값만/,
  );
  assert.throws(() =>
    validateResult({ photos: [{ photoId: "unknown", result: slip }] }, current),
  );
  assert.throws(() =>
    validateResult(
      {
        photos: [
          { photoId: "photo-0", result: slip },
          { photoId: "photo-0", result: slip },
        ],
      },
      current,
    ),
  );
});

test("runtime requires an open session, validates photo IDs and closes conversation access", async () => {
  let calls = 0;
  const states: unknown[] = [];
  const server = createRuntimeServer(
    {
      name: "mock",
      model: "mock",
      async run(value, context) {
        calls++;
        states.push(context.session);
        return output(value);
      },
    },
    "s".repeat(32),
    "http://127.0.0.1:1",
  );
  const url = await start(server);
  const send = (path: string, body: unknown, method = "POST") =>
    fetch(url + path, {
      method,
      headers: {
        Authorization: `Bearer ${"s".repeat(32)}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(body),
    });
  try {
    assert.equal((await send("/infer/slip", input)).status, 410);
    for (const varietyNames of [
      "invalid",
      [null],
      [""],
      ["a".repeat(151)],
      [{ id: 1, name: "품종" }],
    ])
      assert.equal(
        (await send("/sessions", { sessionId: input.sessionId, varietyNames }))
          .status,
        400,
      );
    const names = ["하니문", "프라그랑스"];
    assert.equal(
      (
        await send("/sessions", {
          sessionId: input.sessionId,
          varietyNames: [...names, names[0]],
        })
      ).status,
      200,
    );
    const first = await send("/infer/slip", input);
    assert.equal(first.status, 200);
    assert.equal((await first.json()).photos.length, 10);
    assert.deepEqual((states[0] as SessionState).varietyNames, names);
    // Reopening an existing ID must not replace its initial reference snapshot.
    assert.equal(
      (
        await send("/sessions", {
          sessionId: input.sessionId,
          varietyNames: ["변경된품종"],
        })
      ).status,
      200,
    );
    assert.equal(
      (
        await send("/infer/slip", {
          ...input,
          photos: [photo("photo-0")],
        })
      ).status,
      200,
    );
    assert.equal(states[0], states[1]);
    assert.deepEqual((states[1] as SessionState).varietyNames, names);
    assert.equal(
      (
        await send("/infer/slip", {
          ...input,
          photos: Array.from({ length: 11 }, (_, i) => photo(`p-${i}`)),
        })
      ).status,
      400,
    );
    assert.equal(
      (
        await send("/infer/slip", {
          ...input,
          photos: [photo("photo-0"), photo("photo-0")],
        })
      ).status,
      400,
    );
    assert.equal(calls, 2);
    assert.equal(
      (await send(`/sessions/${input.sessionId}`, undefined, "DELETE")).status,
      200,
    );
    assert.equal((await send("/infer/slip", input)).status, 410);
  } finally {
    await stop(server);
  }
});

test("OpenAI continuation retains prior extraction without retaining photo binaries", async () => {
  const names = ["하니문", "프라그랑스"];
  const state: SessionState = {
    varietyNames: names,
    history: [],
    turns: 0,
    expiresAt: Date.now() + 10000,
  };
  let turn = 0;
  let current: SlipInput = { ...input, photos: [photo("photo-0")] };
  const fake = createServer(async (req, res) => {
    let text = "";
    for await (const chunk of req) text += chunk;
    const body = JSON.parse(text);
    turn++;
    const referenceMessages = body.input.filter(
      (item: { content?: { text?: string }[] }) =>
        item.content?.some((part) =>
          part.text?.includes(JSON.stringify(names)),
        ),
    );
    assert.equal(referenceMessages.length, 1);
    assert.deepEqual(Object.keys(body.text.format).sort(), [
      "name",
      "schema",
      "strict",
      "type",
    ]);
    const images = body.input
      .flatMap((item: { content?: { type: string }[] }) => item.content ?? [])
      .filter((part: { type: string }) => part.type === "input_image");
    assert.equal(images.length, 1);
    if (turn === 2) {
      assert.ok(body.input.length >= 3);
      assert.doesNotMatch(JSON.stringify(body.input), /feedback/);
    }
    res.end(
      JSON.stringify({
        status: "completed",
        output: [
          {
            type: "message",
            role: "assistant",
            content: [
              { type: "output_text", text: JSON.stringify(output(current)) },
            ],
          },
        ],
      }),
    );
  });
  const url = await start(fake);
  try {
    const provider = openAiProvider("mock", "mock", url);
    const context = {
      signal: new AbortController().signal,
      maxToolCalls: 20,
      callTool: async () => [],
      session: state,
    };
    await provider.run(current, context);
    assert.doesNotMatch(JSON.stringify(state.history), /data:image/);
    current = { ...input, photos: [photo("photo-0")] };
    await provider.run(current, context);
    assert.equal(turn, 2);
  } finally {
    await stop(fake);
  }
});
