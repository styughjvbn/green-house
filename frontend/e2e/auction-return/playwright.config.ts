import { defineConfig, devices } from "@playwright/test";
export default defineConfig({
  testDir: ".",
  testMatch: "auction-return.spec.ts",
  workers: 1,
  fullyParallel: false,
  timeout: 60000,
  expect: { timeout: 10000 },
  reporter: [["line"]],
  outputDir: "../../test-results/auction-return",
  use: {
    ...devices["Desktop Chrome"],
    baseURL: "http://127.0.0.1:13100",
    viewport: { width: 1440, height: 1000 },
    trace: "retain-on-failure",
  },
  webServer: {
    command: "node e2e/auction-return/serve-fixture.mjs",
    cwd: "../..",
    url: "http://127.0.0.1:13100/login",
    timeout: 120000,
    reuseExistingServer: false,
  },
});
