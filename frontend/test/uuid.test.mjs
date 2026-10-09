import assert from "node:assert/strict";
import test from "node:test";
import { webcrypto } from "node:crypto";
import { createUuid } from "../src/shared/lib/id.ts";
import { createPendingCreationRequestKey } from "../src/shared/lib/pendingCreationRequestKey.ts";

const uuidV4 =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

test("UUID generation supports browsers with and without randomUUID", () => {
  const original = Object.getOwnPropertyDescriptor(globalThis, "crypto");
  try {
    for (const crypto of [
      webcrypto,
      { getRandomValues: webcrypto.getRandomValues.bind(webcrypto) },
      undefined,
    ]) {
      Object.defineProperty(globalThis, "crypto", {
        configurable: true,
        value: crypto,
      });
      const keys = createPendingCreationRequestKey("sales-create", createUuid);
      const first = keys.get();
      assert.match(first, uuidV4);
      assert.equal(keys.get(), first);
      keys.complete(first);
      const next = keys.get();
      assert.match(next, uuidV4);
      assert.notEqual(next, first);
    }
  } finally {
    Object.defineProperty(globalThis, "crypto", original);
  }
});
