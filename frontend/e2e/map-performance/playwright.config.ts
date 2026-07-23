import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: ".",
  testMatch: "map-performance.spec.ts",
  workers: 1,
  fullyParallel: false,
  timeout: 45 * 60 * 1000,
  expect: {
    timeout: 120 * 1000,
  },
  outputDir: "../../test-results/map-performance",
  reporter: [["line"]],
  use: {
    ...devices["Desktop Chrome"],
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:13000",
    viewport: { width: 1440, height: 1000 },
    trace: "retain-on-failure",
    video: "retain-on-failure",
    screenshot: "only-on-failure",
  },
});
