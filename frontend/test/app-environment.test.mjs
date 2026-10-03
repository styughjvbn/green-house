import assert from "node:assert/strict";
import test from "node:test";
import { resolveAppEnvironment } from "../src/shared/config/appEnvironment.ts";

test("explicit APP_ENV selects dev or prod independently of NODE_ENV", () => {
  assert.equal(resolveAppEnvironment("dev", "production"), "dev");
  assert.equal(resolveAppEnvironment("prod", "development"), "prod");
});

test("missing APP_ENV falls back safely to the framework environment", () => {
  assert.equal(resolveAppEnvironment(undefined, "development"), "dev");
  assert.equal(resolveAppEnvironment(undefined, "production"), "prod");
  assert.equal(resolveAppEnvironment(undefined, "test"), "prod");
});

test("invalid APP_ENV fails fast", () => {
  assert.throws(() => resolveAppEnvironment("stage", "production"));
});
