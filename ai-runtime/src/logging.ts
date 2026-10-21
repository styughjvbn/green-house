export type Diagnostic = (
  event: string,
  fields?: Record<string, string | number | boolean | undefined>,
) => void;

export function createLogger(
  development = process.env.NODE_ENV === "development",
  write: (line: string) => void = (line) => console.info(line),
) {
  return (
    event: string,
    fields: Parameters<Diagnostic>[1] = {},
    debug = false,
  ) => {
    if (debug && !development) return;
    write(JSON.stringify({ time: new Date().toISOString(), event, ...fields }));
  };
}

// Never serialize upstream messages, response bodies, prompts or SDK stderr.
export function errorDetails(error: unknown) {
  const message = error instanceof Error ? error.message : "";
  const reason =
    /unauthorized|authentication|not logged in|login required|401/i.test(
      message,
    )
      ? "authentication"
      : /rate.?limit|quota|usage.?limit|429/i.test(message)
        ? "usage_limit"
        : /mcp|tool.*(?:connect|start|fail)/i.test(message)
          ? "tool_connection"
          : /config|reasoning|Invalid response.create payload|Invalid value|model.*(?:not found|not supported|unsupported)/i.test(
                message,
              )
            ? "configuration"
            : undefined;
  const cause = error instanceof Error ? error.cause : undefined;
  const code =
    cause && typeof cause === "object" && "code" in cause
      ? String(cause.code)
      : undefined;
  return {
    errorType: error instanceof Error ? error.name : "UnknownError",
    reason,
    networkCode: code && /^[A-Z0-9_]{1,64}$/.test(code) ? code : undefined,
  };
}
