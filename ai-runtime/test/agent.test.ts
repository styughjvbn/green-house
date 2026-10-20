import { imageBase64 } from "./fixtures.js";
import { test } from "node:test";
import assert from "node:assert/strict";
import { createServer, type Server } from "node:http";
import { once } from "node:events";
import { createRuntimeServer } from "../src/server.js";
import { openAiProvider } from "../src/providers/openai.js";
import {
  validateResult,
  type AgentProvider,
  type SlipInput,
} from "../src/contracts.js";
const result = {
  salesType: "DIRECT",
  saleDate: null,
  partnerName: "농장",
  partnerId: 3,
  memo: null,
  items: [],
  warnings: [],
  confidence: 90,
  reviewFields: [],
};
const input: SlipInput = {
  requestId: "test-run",
  toolToken: "t".repeat(43),
  contentType: "image/png",
  contentBase64: imageBase64,
  timeoutSeconds: 1,
};
const secret = "s".repeat(32);
async function start(server: Server) {
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  return `http://127.0.0.1:${(server.address() as { port: number }).port}`;
}
async function stop(server: Server) {
  server.closeAllConnections();
  await new Promise<void>((resolve) => server.close(() => resolve()));
}
test("Responses adapter executes requested lookup and sends the result back before returning structured extraction", async () => {
  let turns = 0;
  let argumentsSeen: unknown;
  const fake = createServer(async (req, res) => {
    let text = "";
    for await (const chunk of req) text += chunk;
    const body = JSON.parse(text);
    turns++;
    assert.equal(body.store, false);
    assert.equal(body.tools.length, 2);
    if (turns === 1) {
      assert.match(body.input[0].content[1].image_url, /^data:image\/png/);
      res.end(
        JSON.stringify({
          status: "completed",
          output: [
            {
              type: "function_call",
              call_id: "call1",
              name: "search_partners",
              arguments: JSON.stringify({ query: "농장", auctionHouse: false }),
            },
          ],
        }),
      );
    } else {
      assert.equal(body.input.at(-1).type, "function_call_output");
      assert.match(body.input.at(-1).output, /거래처/);
      res.end(
        JSON.stringify({
          status: "completed",
          output: [
            {
              type: "message",
              content: [{ type: "output_text", text: JSON.stringify(result) }],
            },
          ],
        }),
      );
    }
  });
  const url = await start(fake);
  try {
    const provider = openAiProvider("test-key", "test-model", url);
    const extracted = await provider.run(input, {
      signal: new AbortController().signal,
      maxToolCalls: 2,
      callTool: async (name, args) => {
        assert.equal(name, "search_partners");
        argumentsSeen = args;
        return [{ id: 3, name: "거래처" }];
      },
    });
    assert.deepEqual(extracted, result);
    assert.deepEqual(argumentsSeen, { query: "농장", auctionHouse: false });
    assert.equal(turns, 2);
  } finally {
    await stop(fake);
  }
});
test("runtime rejects unauthenticated and concurrent calls and releases its execution slot", async () => {
  let finish: ((value: unknown) => void) | undefined;
  const entered = Promise.withResolvers<void>();
  const provider: AgentProvider = {
    name: "mock",
    model: "mock",
    run: async () => {
      entered.resolve();
      return new Promise((resolve) => {
        finish = resolve;
      });
    },
  };
  const server = createRuntimeServer(provider, secret, "http://127.0.0.1:1");
  const url = await start(server);
  const request = () =>
    fetch(url + "/infer/slip", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${secret}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        ...input,
        contentType: "image/webp",
        contentBase64: "UklGRhwAAABXRUJQVlA4TA8AAAAvAUAAAAcQ/Y/+ByKi/wEA",
      }),
    });
  try {
    assert.equal(
      (await fetch(url + "/infer/slip", { method: "POST" })).status,
      401,
    );
    const first = request();
    await entered.promise;
    assert.equal((await request()).status, 429);
    finish!(result);
    assert.equal((await first).status, 200);
  } finally {
    await stop(server);
  }
});
test("runtime cancels timed out runs and rejects malformed structured output", async () => {
  const provider: AgentProvider = {
    name: "mock",
    model: "mock",
    run: async (_, ctx) =>
      new Promise((_, reject) =>
        ctx.signal.addEventListener("abort", () => reject(ctx.signal.reason), {
          once: true,
        }),
      ),
  };
  const server = createRuntimeServer(provider, secret, "http://127.0.0.1:1");
  const url = await start(server);
  try {
    const response = await fetch(url + "/infer/slip", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${secret}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
    assert.equal(response.status, 504);
    assert.throws(() => validateResult({ ...result, partnerId: -1 }));
    assert.throws(() =>
      validateResult({ ...result, items: [{ quantity: -1 }] }),
    );
  } finally {
    await stop(server);
  }
});

test("Codex adapter receives only scoped tools and cleans its temporary image", async () => {
  const { codexProvider } = await import("../src/providers/codex.js");
  const { readFile, access } = await import("node:fs/promises");
  const webpBase64 = "UklGRhwAAABXRUJQVlA4TA8AAAAvAUAAAAcQ/Y/+ByKi/wEA";
  let imagePath = "";
  const provider = codexProvider(
    "test-model",
    "/tmp/isolated-codex-home",
    "http://127.0.0.1:8080",
    (options) => {
      assert.equal(options.env?.CODEX_HOME, "/tmp/isolated-codex-home");
      assert.equal(options.env?.OPENAI_API_KEY, undefined);
      assert.match(JSON.stringify(options.config), /search_varieties/);
      const config = options.config as {
        mcp_servers: {
          greenhouse: {
            enabled_tools: string[];
            tools: Record<string, { approval_mode: string }>;
          };
        };
      };
      assert.deepEqual(
        Object.keys(config.mcp_servers.greenhouse.tools).sort(),
        ["search_partners", "search_varieties"],
      );
      for (const tool of config.mcp_servers.greenhouse.enabled_tools)
        assert.equal(
          config.mcp_servers.greenhouse.tools[tool].approval_mode,
          "approve",
        );
      return {
        startThread(threadOptions) {
          assert.equal(threadOptions?.sandboxMode, "read-only");
          assert.equal(threadOptions?.approvalPolicy, "never");
          return {
            async runStreamed(parts: unknown, turnOptions: unknown) {
              imagePath = (parts as { path?: string }[])[1].path!;
              assert.ok(imagePath.endsWith(".webp"));
              assert.deepEqual(
                await readFile(imagePath),
                Buffer.from(webpBase64, "base64"),
              );
              assert.ok(
                (turnOptions as { outputSchema: unknown }).outputSchema,
              );
              return {
                events: (async function* () {
                  yield {
                    type: "item.completed",
                    item: {
                      id: "answer",
                      type: "agent_message",
                      text: JSON.stringify(result),
                    },
                  };
                  yield { type: "turn.completed" };
                })(),
              };
            },
          } as ReturnType<import("@openai/codex-sdk").Codex["startThread"]>;
        },
      };
    },
  );
  assert.deepEqual(
    await provider.run(
      { ...input, contentType: "image/webp", contentBase64: webpBase64 },
      {
        signal: new AbortController().signal,
        maxToolCalls: 2,
        callTool: async () => [],
      },
    ),
    result,
  );
  await assert.rejects(access(imagePath));
});

test("MCP bridge exposes read-only searches and forwards the execution token to Spring", async () => {
  const { Client } = await import("@modelcontextprotocol/sdk/client/index.js");
  const { StdioClientTransport } =
    await import("@modelcontextprotocol/sdk/client/stdio.js");
  let count = 0;
  const backend = createServer(async (req, res) => {
    assert.equal(req.headers.authorization, `Bearer ${input.toolToken}`);
    assert.equal(req.url, "/internal/ai/tools/search_varieties");
    count++;
    res.setHeader("Content-Type", "application/json");
    res.end(JSON.stringify({ data: [{ id: 7, name: "품종", genus: "속" }] }));
  });
  const url = await start(backend);
  const transport = new StdioClientTransport({
    command: process.execPath,
    args: ["dist/tools/mcp-server.js"],
    env: {
      PATH: process.env.PATH ?? "",
      AI_TOOLS_URL: url,
      AI_TOOL_TOKEN: input.toolToken,
      AI_MAX_TOOL_CALLS: "1",
      AI_DEADLINE_MS: String(Date.now() + 10000),
    },
  });
  const client = new Client({ name: "runtime-test", version: "1.0.0" });
  try {
    await client.connect(transport);
    const tools = await client.listTools();
    for (const tool of tools.tools) {
      assert.equal(tool.annotations?.readOnlyHint, true);
      assert.equal(tool.annotations?.destructiveHint, false);
      assert.equal(tool.annotations?.openWorldHint, false);
    }
    assert.deepEqual(tools.tools.map((tool) => tool.name).sort(), [
      "search_partners",
      "search_varieties",
    ]);
    const response = await client.callTool({
      name: "search_varieties",
      arguments: { query: "품종" },
    });
    assert.equal(response.isError, undefined);
    assert.match(JSON.stringify(response.content), /품종/);
    const exceeded = await client.callTool({
      name: "search_varieties",
      arguments: { query: "품종" },
    });
    assert.equal(exceeded.isError, true);
    assert.equal(count, 1);
  } finally {
    await client.close();
    await stop(backend);
  }
});

test("Codex execution stops when the model exceeds the tool budget", async () => {
  const { codexProvider } = await import("../src/providers/codex.js");
  let signal: AbortSignal | undefined;
  const provider = codexProvider(
    "model",
    "/tmp/isolated-home",
    "http://127.0.0.1:8080",
    () => ({
      startThread() {
        return {
          async runStreamed(_: unknown, options: { signal: AbortSignal }) {
            signal = options.signal;
            return {
              events: (async function* () {
                for (let i = 0; i < 3; i++)
                  yield {
                    type: "item.started",
                    item: {
                      id: String(i),
                      type: "mcp_tool_call",
                      server: "greenhouse",
                      tool: "search_varieties",
                      arguments: { query: "난" },
                      status: "in_progress",
                    },
                  };
              })(),
            };
          },
        } as ReturnType<import("@openai/codex-sdk").Codex["startThread"]>;
      },
    }),
  );
  await assert.rejects(
    provider.run(input, {
      signal: new AbortController().signal,
      maxToolCalls: 2,
      callTool: async () => [],
    }),
    (error: unknown) => (error as { code: string }).code === "AI_TOOL_LIMIT",
  );
  assert.equal(signal?.aborted, true);
});

test("normal HTTP response completion does not cancel a finished provider", async () => {
  let signal: AbortSignal | undefined;
  const server = createRuntimeServer(
    {
      name: "mock",
      model: "mock",
      async run(_, context) {
        signal = context.signal;
        return result;
      },
    },
    secret,
    "http://127.0.0.1:1",
  );
  const url = await start(server);
  try {
    const response = await fetch(url + "/infer/slip", {
      method: "POST",
      headers: { Authorization: `Bearer ${secret}` },
      body: JSON.stringify(input),
    });
    assert.equal(response.status, 200);
    await response.text();
    await new Promise((resolve) => setImmediate(resolve));
    assert.equal(signal?.aborted, false);
  } finally {
    await stop(server);
  }
});

test("Codex error stream is drained until child exit and HTTP server remains healthy", async () => {
  const { Codex } = await import("@openai/codex-sdk");
  const { codexProvider } = await import("../src/providers/codex.js");
  const { mkdtemp, writeFile, rm } = await import("node:fs/promises");
  const { tmpdir } = await import("node:os");
  const { join } = await import("node:path");
  const directory = await mkdtemp(join(tmpdir(), "codex-error-test-"));
  const fake = join(directory, "codex.mjs");
  await writeFile(
    fake,
    '#!/usr/bin/env node\nconsole.log(JSON.stringify({type:"error",message:"Invalid response.create payload"}));\nsetTimeout(()=>process.exit(1),100);\n',
    { mode: 0o700 },
  );
  const diagnostics: string[] = [];
  const provider = codexProvider(
    "mock",
    directory,
    "http://127.0.0.1:1",
    (options) => new Codex({ ...options, codexPathOverride: fake }),
  );
  const originalRun = provider.run.bind(provider);
  let signal: AbortSignal | undefined;
  let elapsed = 0;
  provider.run = async (input, context) => {
    signal = context.signal;
    const started = performance.now();
    try {
      return await originalRun(input, {
        ...context,
        diagnostic: (event) => diagnostics.push(event),
      });
    } finally {
      elapsed = performance.now() - started;
    }
  };
  const server = createRuntimeServer(provider, secret, "http://127.0.0.1:1");
  const url = await start(server);
  try {
    const response = await fetch(url + "/infer/slip", {
      method: "POST",
      headers: { Authorization: `Bearer ${secret}` },
      body: JSON.stringify(input),
    });
    assert.equal(response.status, 502);
    assert.equal((await response.json()).error.code, "AI_PROVIDER_FAILED");
    await new Promise((resolve) => setImmediate(resolve));
    assert.equal(signal?.aborted, false);
    assert.ok(
      elapsed >= 100,
      "must await CLI exit after receiving its error event",
    );
    assert.ok(diagnostics.includes("codex_failed"));
    assert.equal((await fetch(url + "/health")).status, 200);
  } finally {
    await stop(server);
    await rm(directory, { recursive: true, force: true });
  }
});
