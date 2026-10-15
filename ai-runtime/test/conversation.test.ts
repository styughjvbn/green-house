import { test } from "node:test";
import assert from "node:assert/strict";
import { createServer, type Server } from "node:http";
import { once } from "node:events";
import { createRuntimeServer } from "../src/server.js";
import { codexProvider } from "../src/providers/codex.js";
import { openAiProvider } from "../src/providers/openai.js";
import {
  validateResult,
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
};
const photo = (id: string, feedback: string | null = null) => ({
  photoId: id,
  contentType: "image/png",
  contentBase64: Buffer.from("image").toString("base64"),
  feedback,
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

test("one batch of ten photos, individual reread and feedback reuse the Codex thread", async () => {
  const state: SessionState = {
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
    photos: [photo("photo-0", "수량은 20개로 다시 확인해주세요")],
  };
  validateResult(await provider.run(current, context), current);
  assert.equal(starts, 1);
  assert.equal(resumes, 2);
  assert.equal(turns[0].filter((p) => p.type === "local_image").length, 10);
  assert.equal(turns[1].filter((p) => p.type === "local_image").length, 1);
  assert.match(JSON.stringify(turns[2]), /수량은 20개/);
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
    assert.equal(
      (await send("/sessions", { sessionId: input.sessionId })).status,
      200,
    );
    const first = await send("/infer/slip", input);
    assert.equal(first.status, 200);
    assert.equal((await first.json()).photos.length, 10);
    assert.equal(
      (
        await send("/infer/slip", {
          ...input,
          photos: [photo("photo-0", "20개")],
        })
      ).status,
      200,
    );
    assert.equal(states[0], states[1]);
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

test("OpenAI continuation retains prior extraction and feedback without retaining photo binaries", async () => {
  const state: SessionState = {
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
      assert.match(JSON.stringify(body.input), /20개/);
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
    current = { ...input, photos: [photo("photo-0", "20개로 수정")] };
    await provider.run(current, context);
    assert.equal(turn, 2);
  } finally {
    await stop(fake);
  }
});
