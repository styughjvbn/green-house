import { createServer, type IncomingMessage } from "node:http";
import { timingSafeEqual } from "node:crypto";
import { pathToFileURL } from "node:url";
import { openAiProvider } from "./providers/openai.js";
import { codexProvider } from "./providers/codex.js";
import { createToolClient } from "./tools/client.js";
import { createLogger, errorDetails } from "./logging.js";
import {
  RuntimeError,
  validateResult,
  type AgentProvider,
  type SlipInput,
  type SessionState,
  inputPhotos,
} from "./contracts.js";

async function readBody(request: IncomingMessage) {
  const parts: Buffer[] = [];
  let bytes = 0;
  for await (const part of request) {
    bytes += part.length;
    if (bytes > 112 * 1024 * 1024)
      throw new RuntimeError(413, "AI_IMAGE_TOO_LARGE");
    parts.push(part);
  }
  try {
    return JSON.parse(Buffer.concat(parts).toString("utf8"));
  } catch {
    throw new RuntimeError(400, "AI_INVALID_REQUEST");
  }
}
export function createRuntimeServer(
  provider: AgentProvider,
  secret: string,
  toolsUrl: string,
  maxToolCalls = 20,
  maxSeconds = 120,
  log = createLogger(),
) {
  if (!secret || secret.length < 32)
    throw new Error("AI_RUNTIME_SECRET must contain at least 32 characters");
  if (
    !Number.isInteger(maxToolCalls) ||
    maxToolCalls < 1 ||
    maxToolCalls > 100 ||
    !Number.isInteger(maxSeconds) ||
    maxSeconds < 1 ||
    maxSeconds > 600
  )
    throw new Error("Invalid AI execution limits");
  let active = false;
  const sessions = new Map<string, SessionState>();
  function cleanSessions() {
    for (const [id, state] of sessions)
      if (state.expiresAt < Date.now()) sessions.delete(id);
  }
  return createServer(async (request, response) => {
    const send = (status: number, body: unknown) => {
      if (!response.destroyed) {
        response.writeHead(status, { "Content-Type": "application/json" });
        response.end(JSON.stringify(body));
      }
    };
    if (request.method === "GET" && request.url === "/health") {
      send(200, { status: "ok" });
      return;
    }
    const expected = Buffer.from(`Bearer ${secret}`),
      actual = Buffer.from(request.headers.authorization ?? "");
    if (
      actual.length !== expected.length ||
      !timingSafeEqual(actual, expected)
    ) {
      log("request_rejected", { code: "AI_UNAUTHORIZED", status: 401 });
      send(401, { error: { code: "AI_UNAUTHORIZED" } });
      return;
    }
    cleanSessions();
    if (request.method === "POST" && request.url === "/sessions") {
      try {
        const body = await readBody(request);
        if (
          !body ||
          typeof body.sessionId !== "string" ||
          !/^[a-f0-9-]{36}$/.test(body.sessionId)
        )
          throw new RuntimeError(400, "AI_INVALID_REQUEST");
        if (!sessions.has(body.sessionId)) {
          if (sessions.size >= 100)
            throw new RuntimeError(429, "AI_SESSION_LIMIT");
          sessions.set(body.sessionId, {
            history: [],
            turns: 0,
            expiresAt: Date.now() + 30 * 60 * 1000,
          });
        }
        send(200, { sessionId: body.sessionId });
      } catch (error) {
        const failure =
          error instanceof RuntimeError
            ? error
            : new RuntimeError(400, "AI_INVALID_REQUEST");
        send(failure.status, { error: { code: failure.code } });
      }
      return;
    }
    if (request.method === "DELETE" && request.url?.startsWith("/sessions/")) {
      sessions.delete(request.url.slice("/sessions/".length));
      send(200, { closed: true });
      return;
    }
    if (request.method !== "POST" || request.url !== "/infer/slip") {
      send(404, { error: { code: "NOT_FOUND" } });
      return;
    }
    if (active) {
      log("request_rejected", { code: "AI_BUSY", status: 429 });
      send(429, { error: { code: "AI_BUSY" } });
      return;
    }
    active = true;
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    let requestId = "invalid";
    const started = performance.now();
    let success = false;
    let stage = "upload";
    let failureCode: string | undefined;
    log("request_received", { provider: provider.name }, true);
    const onDisconnect = () => {
      if (!response.writableFinished) controller.abort();
    };
    response.on("close", onDisconnect);
    // Bound slow uploads too, before parsing the complete body.
    const uploadTimer = setTimeout(() => {
      controller.abort();
      request.destroy();
    }, 15000);
    try {
      const input = (await readBody(request)) as SlipInput;
      clearTimeout(uploadTimer);
      if (
        !input ||
        typeof input.requestId !== "string" ||
        !/^[-a-zA-Z0-9]{1,64}$/.test(input.requestId) ||
        typeof input.toolToken !== "string" ||
        input.toolToken.length < 32 ||
        input.toolToken.length > 256 ||
        !Number.isInteger(input.timeoutSeconds) ||
        input.timeoutSeconds < 1
      )
        throw new RuntimeError(400, "AI_INVALID_REQUEST");
      if (
        input.photos !== undefined &&
        (!Array.isArray(input.photos) ||
          !input.photos.length ||
          input.photos.length > 10 ||
          typeof input.sessionId !== "string" ||
          new Set(input.photos.map((p) => p?.photoId)).size !==
            input.photos.length)
      )
        throw new RuntimeError(400, "AI_INVALID_REQUEST");
      let imageBytes = 0;
      for (const photo of inputPhotos(input)) {
        if (
          !photo ||
          typeof photo.photoId !== "string" ||
          !/^[-a-zA-Z0-9]{1,64}$/.test(photo.photoId) ||
          !["image/jpeg", "image/png"].includes(photo.contentType) ||
          typeof photo.contentBase64 !== "string" ||
          photo.contentBase64.length > 11184812 ||
          (photo.feedback != null &&
            (typeof photo.feedback !== "string" ||
              photo.feedback.length > 1000))
        )
          throw new RuntimeError(400, "AI_INVALID_REQUEST");
        const bytes = Buffer.from(photo.contentBase64, "base64").length;
        if (!bytes || bytes > 8 * 1024 * 1024)
          throw new RuntimeError(400, "AI_INVALID_IMAGE");
        imageBytes += bytes;
      }
      const session = input.photos ? sessions.get(input.sessionId) : undefined;
      if (input.photos && !session)
        throw new RuntimeError(410, "AI_SESSION_EXPIRED");
      if (session) {
        if (
          session.turns >= 30 ||
          JSON.stringify(session.history).length > 2 * 1024 * 1024
        )
          throw new RuntimeError(409, "AI_SESSION_LIMIT");
        session.turns++;
        session.expiresAt = Date.now() + 30 * 60 * 1000;
      }
      requestId = input.requestId;
      const diagnostic = (event: string, fields = {}) =>
        log(event, { ...fields, requestId, provider: provider.name }, true);
      input.timeoutSeconds = Math.min(input.timeoutSeconds, maxSeconds);
      timer = setTimeout(() => controller.abort(), input.timeoutSeconds * 1000);
      const callTool = createToolClient(
        toolsUrl,
        input.toolToken,
        maxToolCalls,
        diagnostic,
      );
      stage = "inference";
      diagnostic("inference_started", {
        model: provider.model || "account-default",
        timeoutSeconds: input.timeoutSeconds,
        imageBytes,
        photoCount: inputPhotos(input).length,
      });
      const output = await provider.run(input, {
        signal: controller.signal,
        callTool,
        maxToolCalls,
        diagnostic,
        session,
      });
      stage = "validation";
      diagnostic("result_validation_started");
      const result = validateResult(output, input);
      if (controller.signal.aborted) throw new RuntimeError(504, "AI_TIMEOUT");
      send(200, result);
      success = true;
    } catch (error) {
      const failure = controller.signal.aborted
        ? new RuntimeError(504, "AI_TIMEOUT")
        : error instanceof RuntimeError
          ? error
          : new RuntimeError(502, "AI_RUNTIME_FAILED");
      failureCode = failure.code;
      log("ai_run_failed", {
        requestId,
        provider: provider.name,
        stage,
        code: failure.code,
        status: failure.status,
        ...errorDetails(error),
      });
      send(failure.status, { error: { code: failure.code } });
    } finally {
      response.off("close", onDisconnect);
      clearTimeout(uploadTimer);
      clearTimeout(timer);
      active = false;
      log("ai_run", {
        requestId,
        provider: provider.name,
        model: provider.model,
        durationMs: Math.round(performance.now() - started),
        success,
        code: failureCode,
      });
    }
  });
}
if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(process.argv[1]).href
) {
  const name = process.env.AI_PROVIDER ?? "codex";
  if (!["codex", "openai"].includes(name))
    throw new Error("AI_PROVIDER must be codex or openai");
  const toolsUrl = process.env.AI_TOOLS_URL ?? "http://127.0.0.1:8080";
  const provider =
    name === "openai"
      ? openAiProvider(
          process.env.OPENAI_API_KEY ?? "",
          process.env.AI_OPENAI_MODEL ?? "gpt-4.1-mini",
        )
      : codexProvider(
          process.env.AI_CODEX_MODEL ?? "",
          process.env.AI_CODEX_HOME ?? "",
          toolsUrl,
        );
  const log = createLogger();
  const host = process.env.AI_HOST ?? "127.0.0.1";
  const port = Number(process.env.AI_PORT ?? 8091);
  const server = createRuntimeServer(
    provider,
    process.env.AI_RUNTIME_SECRET ?? "",
    toolsUrl,
    Number(process.env.AI_MAX_TOOL_CALLS ?? 20),
    Number(process.env.AI_TIMEOUT_SECONDS ?? 120),
  );
  server.on("error", (error: NodeJS.ErrnoException) => {
    log("server_failed", { code: error.code, host, port });
    process.exitCode = 1;
  });
  server.listen(port, host, () => {
    log("server_started", {
      host,
      port,
      provider: provider.name,
      model: provider.model || "account-default",
      mode: process.env.NODE_ENV ?? "production",
      healthUrl: `http://${host}:${port}/health`,
    });
  });
}
