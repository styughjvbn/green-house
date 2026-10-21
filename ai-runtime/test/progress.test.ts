import test from "node:test";
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { once } from "node:events";
import { createProgressReporter } from "../src/progress.js";

test("summary relay bounds in-flight work, coalesces updates and uses only the scoped token", async () => {
  const received: string[] = [];
  let first!: () => void;
  let second!: () => void;
  const firstSeen = new Promise<void>((resolve) => {
    first = resolve;
  });
  const secondSeen = new Promise<void>((resolve) => {
    second = resolve;
  });
  let release!: () => void;
  const released = new Promise<void>((resolve) => {
    release = resolve;
  });
  const server = createServer(async (request, response) => {
    assert.equal(request.url, "/internal/ai/tools/progress");
    assert.equal(request.headers.authorization, "Bearer scoped-token");
    let body = "";
    for await (const part of request) body += part;
    received.push(JSON.parse(body).summary);
    if (received.length === 1) {
      first();
      await released;
    } else second();
    response.writeHead(200).end("{}");
  });
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  const reporter = createProgressReporter(
    `http://127.0.0.1:${(server.address() as { port: number }).port}`,
    "scoped-token",
  );
  try {
    reporter.report("첫 요약");
    await firstSeen;
    for (let index = 0; index < 1000; index++) reporter.report(`요약 ${index}`);
    assert.deepEqual(received, ["첫 요약"]);
    release();
    await secondSeen;
    assert.deepEqual(received, ["첫 요약", "요약 999"]);
  } finally {
    release();
    reporter.close();
    server.closeAllConnections();
    await new Promise<void>((resolve) => server.close(() => resolve()));
  }
});

test("summary delivery failure never rejects the model progress callback", async () => {
  const reporter = createProgressReporter("http://127.0.0.1:1", "scoped-token");
  assert.doesNotThrow(() => reporter.report("요약"));
  reporter.close();
  assert.doesNotThrow(() => reporter.report("종료 후 무시"));
});
