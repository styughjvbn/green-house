import { Codex, type CodexOptions } from "@openai/codex-sdk";
import { mkdtemp, writeFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { errorDetails } from "../logging.js";
import {
  instructions,
  schema,
  batchSchema,
  inputPhotos,
  turnPrompt,
  sessionReferencePrompt,
  RuntimeError,
  type AgentProvider,
} from "../contracts.js";
export function codexProvider(
  model: string,
  codexHome: string,
  toolsUrl: string,
  makeCodex: (
    options: CodexOptions,
  ) => Pick<Codex, "startThread"> & Partial<Pick<Codex, "resumeThread">> = (
    options,
  ) => new Codex(options),
): AgentProvider {
  return {
    name: "codex",
    model,
    async run(input, context) {
      if (!codexHome) throw new RuntimeError(503, "AI_NOT_CONFIGURED");
      const directory = await mkdtemp(join(tmpdir(), "green-house-slip-"));
      try {
        const parts: (
          | { type: "text"; text: string }
          | { type: "local_image"; path: string }
        )[] = [];
        if (!context.session?.threadId) {
          parts.push({ type: "text", text: instructions });
          const reference = sessionReferencePrompt(context.session);
          if (reference) parts.push({ type: "text", text: reference });
        }
        if (parts.length && !input.photos)
          (parts[0] as { type: "text"; text: string }).text +=
            "\n" + turnPrompt(input);
        else parts.push({ type: "text", text: turnPrompt(input) });
        for (const [index, photo] of inputPhotos(input).entries()) {
          const image = join(
            directory,
            `${index}.${photo.contentType === "image/png" ? "png" : "jpg"}`,
          );
          await writeFile(image, Buffer.from(photo.contentBase64, "base64"), {
            mode: 0o600,
          });
          if (input.photos)
            parts.push({ type: "text", text: `photoId: ${photo.photoId}` });
          parts.push({ type: "local_image", path: image });
        }
        const env: Record<string, string> = {
          PATH: process.env.PATH ?? "",
          HOME: codexHome,
          CODEX_HOME: codexHome,
        };
        for (const key of ["SSL_CERT_FILE", "CODEX_CA_CERTIFICATE"])
          if (process.env[key]) env[key] = process.env[key]!;
        const codex = makeCodex({
          env,
          config: {
            features: { shell_tool: false },
            mcp_servers: {
              greenhouse: {
                command: process.execPath,
                args: [
                  fileURLToPath(
                    new URL("../tools/mcp-server.js", import.meta.url),
                  ),
                ],
                env: {
                  AI_TOOLS_URL: toolsUrl,
                  AI_TOOL_TOKEN: input.toolToken,
                  AI_MAX_TOOL_CALLS: String(
                    Math.max(
                      0,
                      context.maxToolCalls - (context.toolBudget?.used ?? 0),
                    ),
                  ),
                  AI_DEADLINE_MS: String(
                    context.deadlineMs ??
                      Date.now() + input.timeoutSeconds * 1000,
                  ),
                },
                enabled_tools: ["search_varieties", "search_partners"],
                tools: {
                  search_varieties: { approval_mode: "approve" },
                  search_partners: { approval_mode: "approve" },
                },
              },
            },
          },
        });
        const options = {
          ...(model ? { model } : {}),
          workingDirectory: directory,
          skipGitRepoCheck: true,
          sandboxMode: "read-only" as const,
          approvalPolicy: "never" as const,
          webSearchMode: "disabled" as const,
          networkAccessEnabled: false,
        };
        const thread = context.session?.threadId
          ? codex.resumeThread!(context.session.threadId, options)
          : codex.startThread(options);
        context.diagnostic?.("codex_thread_started");
        const limit = new AbortController();
        const signal = AbortSignal.any([context.signal, limit.signal]);
        const streamed = await thread.runStreamed(parts, {
          outputSchema: input.photos ? batchSchema : schema,
          signal,
        });
        let calls = 0;
        let finalResponse = "";
        let completed = false;
        let failure: RuntimeError | undefined;
        try {
          for await (const event of streamed.events) {
            if (event.type === "thread.started" && context.session)
              context.session.threadId = event.thread_id;
            if (
              event.type === "item.started" &&
              event.item.type === "mcp_tool_call"
            ) {
              context.diagnostic?.("codex_tool_started", {
                tool: event.item.tool,
                call: calls + 1,
              });
              calls++;
              const totalCalls = context.toolBudget
                ? ++context.toolBudget.used
                : calls;
              if (totalCalls > context.maxToolCalls) {
                failure = new RuntimeError(429, "AI_TOOL_LIMIT");
                limit.abort();
              }
            }
            if (
              event.type === "item.completed" &&
              event.item.type === "mcp_tool_call"
            )
              context.diagnostic?.("codex_tool_completed", {
                tool: event.item.tool,
                status: event.item.status,
              });
            if (
              event.type === "item.completed" &&
              event.item.type === "agent_message"
            )
              finalResponse = event.item.text;
            if (event.type === "turn.completed") completed = true;
            if (event.type === "turn.failed" || event.type === "error") {
              context.diagnostic?.("codex_failed", {
                eventType: event.type,
                ...errorDetails(
                  new Error(
                    event.type === "turn.failed"
                      ? event.error.message
                      : event.message,
                  ),
                ),
              });
              failure ??= new RuntimeError(502, "AI_PROVIDER_FAILED");
            }
          }
        } catch (error) {
          throw failure ?? error;
        }
        if (failure) throw failure;
        if (!completed) throw new RuntimeError(502, "AI_INCOMPLETE");
        try {
          return JSON.parse(finalResponse);
        } catch {
          throw new RuntimeError(502, "AI_INVALID_RESULT");
        }
      } finally {
        await rm(directory, { recursive: true, force: true });
      }
    },
  };
}
