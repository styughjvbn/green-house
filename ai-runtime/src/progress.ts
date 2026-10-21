/** Best-effort summary delivery: at most one request and one pending snapshot per inference. */
export function createProgressReporter(baseUrl: string, token: string) {
  const controller = new AbortController();
  let pending: string | undefined;
  let sending = false;
  let previous = "";
  async function drain() {
    sending = true;
    try {
      while (pending && !controller.signal.aborted) {
        const summary = pending;
        pending = undefined;
        try {
          await fetch(new URL("/internal/ai/tools/progress", baseUrl), {
            method: "POST",
            headers: {
              Authorization: `Bearer ${token}`,
              "Content-Type": "application/json",
            },
            body: JSON.stringify({ summary }),
            signal: AbortSignal.any([
              controller.signal,
              AbortSignal.timeout(2000),
            ]),
          }).then(async (response) => {
            await response.body?.cancel();
          });
        } catch {
          /* Progress transport must never fail or delay extraction. */
        }
      }
    } finally {
      sending = false;
    }
  }
  return {
    report(text: string) {
      const summary = text.trim().slice(0, 2000);
      if (!summary || summary === previous || controller.signal.aborted) return;
      previous = summary;
      pending = summary;
      if (!sending) void drain();
    },
    close() {
      pending = undefined;
      controller.abort();
    },
  };
}
