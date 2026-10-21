import { RuntimeError, type ToolArgs, type ToolName } from "../contracts.js";
import { errorDetails, type Diagnostic } from "../logging.js";
export function createToolClient(
  baseUrl: string,
  token: string,
  maxCalls: number,
  diagnostic?: Diagnostic,
) {
  let calls = 0;
  const base = new URL(baseUrl);
  if (!["http:", "https:"].includes(base.protocol))
    throw new Error("Invalid tool API URL");
  return async (name: ToolName, args: ToolArgs, signal: AbortSignal) => {
    if (!["search_varieties", "search_partners"].includes(name))
      throw new RuntimeError(403, "AI_TOOL_NOT_ALLOWED");
    if (++calls > maxCalls) throw new RuntimeError(429, "AI_TOOL_LIMIT");
    if (
      !args ||
      typeof args.query !== "string" ||
      !args.query.trim() ||
      args.query.length > 100
    )
      throw new RuntimeError(400, "AI_TOOL_ARGUMENT_INVALID");
    const started = performance.now();
    diagnostic?.("tool_started", { tool: name, call: calls });
    try {
      const response = await fetch(
        new URL(`/internal/ai/tools/${name}`, base),
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token}`,
          },
          body: JSON.stringify(args),
          signal,
        },
      );
      diagnostic?.("tool_response", {
        tool: name,
        status: response.status,
        durationMs: Math.round(performance.now() - started),
      });
      if (!response.ok)
        throw new RuntimeError(response.status, "AI_TOOL_FAILED");
      const body = (await response.json()) as { data: unknown };
      return body.data;
    } catch (error) {
      diagnostic?.("tool_failed", { tool: name, ...errorDetails(error) });
      throw error;
    }
  };
}
