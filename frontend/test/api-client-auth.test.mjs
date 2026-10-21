import assert from "node:assert/strict";
import test from "node:test";
import { registerHooks } from "node:module";

// Resolve the real client's existing bundler imports without changing production code.
const hooks = registerHooks({
  resolve(specifier, context, nextResolve) {
    if (specifier === "@/shared/lib/id")
      return nextResolve(
        new URL("../src/shared/lib/id.ts", import.meta.url).href,
        context,
      );
    if (
      context.parentURL?.endsWith("/src/shared/api/client.ts") &&
      specifier === "./error"
    )
      return nextResolve(
        new URL("../src/shared/api/error.ts", import.meta.url).href,
        context,
      );
    return nextResolve(specifier, context);
  },
});
const { requestApi, requestApiBlob, ApiError } =
  await import("../src/shared/api/client.ts");
hooks.deregister();

test("API permission errors preserve the page and structured error; only 401 redirects to login", async (t) => {
  const redirects = [];
  const beforeWindow = globalThis.window;
  globalThis.window = {
    location: { assign: (path) => redirects.push(path) },
    localStorage: { getItem: () => "test-client", setItem() {} },
  };
  t.after(() => {
    if (beforeWindow === undefined) delete globalThis.window;
    else globalThis.window = beforeWindow;
  });
  const fakeFetch = t.mock.method(globalThis, "fetch");
  for (const request of [requestApi, requestApiBlob]) {
    fakeFetch.mock.mockImplementation(
      async () =>
        new Response(
          JSON.stringify({
            error: {
              code: "FORBIDDEN",
              message: "접근 권한이 없습니다.",
              details: [],
            },
          }),
          { status: 403 },
        ),
    );
    await assert.rejects(
      request("/business-partners/1", { method: "PUT" }),
      (error) => {
        assert(error instanceof ApiError);
        assert.equal(error.status, 403);
        assert.equal(error.code, "FORBIDDEN");
        assert.equal(error.message, "접근 권한이 없습니다.");
        return true;
      },
    );
    assert.deepEqual(redirects, []);
    fakeFetch.mock.mockImplementation(
      async () => new Response("Invalid CORS request", { status: 403 }),
    );
    await assert.rejects(
      request("/business-partners/1", { method: "PUT" }),
      (error) => error.code === "HTTP_403",
    );
    assert.deepEqual(redirects, []);
  }
  fakeFetch.mock.mockImplementation(
    async () =>
      new Response(
        JSON.stringify({
          error: {
            code: "UNAUTHORIZED",
            message: "로그인이 필요합니다.",
            details: [],
          },
        }),
        { status: 401 },
      ),
  );
  await assert.rejects(
    requestApi("/business-partners/1"),
    (error) => error.code === "UNAUTHORIZED",
  );
  assert.deepEqual(redirects, ["/login"]);
});
