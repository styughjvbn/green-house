import { test } from "node:test";
import assert from "node:assert/strict";
import { createLogger, errorDetails } from "../src/logging.js";
import { createRuntimeServer } from "../src/server.js";
import { once } from "node:events";

test("development details are opt-in and raw provider errors never enter diagnostics", () => {
  const lines: string[] = [];
  const production = createLogger(false, (line) => lines.push(line));
  production("model_request", { turn: 1 }, true);
  assert.equal(lines.length, 0);
  production("server_started", { port: 8091 });
  assert.equal(JSON.parse(lines[0]).event, "server_started");
  const development = createLogger(true, (line) => lines.push(line));
  development("model_request", { turn: 1 }, true);
  assert.equal(JSON.parse(lines[1]).turn, 1);
  const error = new Error("401 authentication failed: secret-photo-content", {
    cause: { code: "ECONNREFUSED", token: "private-token" },
  });
  const details = errorDetails(error);
  assert.equal(details.reason, "authentication");
  assert.equal(details.networkCode, "ECONNREFUSED");
  assert.doesNotMatch(
    JSON.stringify(details),
    /secret-photo-content|private-token/,
  );
});

test("failed extraction logs the request, stage and network cause without request content", async () => {
  const lines: string[] = [];
  const server = createRuntimeServer(
    {
      name: "mock",
      model: "mock",
      async run() {
        throw new Error("private-upstream-message", {
          cause: { code: "ECONNREFUSED" },
        });
      },
    },
    "s".repeat(32),
    "http://127.0.0.1:1",
    20,
    120,
    createLogger(true, (line) => lines.push(line)),
  );
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  try {
    const port = (server.address() as { port: number }).port;
    const response = await fetch(`http://127.0.0.1:${port}/infer/slip`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${"s".repeat(32)}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        requestId: "diagnostic-test",
        toolToken: "private-token".repeat(4),
        contentType: "image/png",
        contentBase64: Buffer.from("private-photo").toString("base64"),
        timeoutSeconds: 1,
      }),
    });
    assert.equal(response.status, 502);
    const failure = lines
      .map((line) => JSON.parse(line))
      .find((entry) => entry.event === "ai_run_failed");
    assert.equal(failure.requestId, "diagnostic-test");
    assert.equal(failure.stage, "inference");
    assert.equal(failure.networkCode, "ECONNREFUSED");
    assert.doesNotMatch(
      lines.join("\n"),
      /private-photo|private-token|private-upstream-message/,
    );
    assert.doesNotMatch(
      lines.join("\n"),
      new RegExp(Buffer.from("private-photo").toString("base64")),
    );
  } finally {
    server.closeAllConnections();
    await new Promise<void>((resolve) => server.close(() => resolve()));
  }
});
