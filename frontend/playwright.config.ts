import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests against a running stack (backend dev profile with seed data + frontend).
 * Base URL: E2E_BASE_URL (default http://localhost:3000). Uses the locally installed Chrome;
 * set E2E_BROWSER_CHANNEL="" and run `npx playwright install chromium` to use bundled Chromium.
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    {
      name: "chrome",
      use: { ...devices["Desktop Chrome"], channel: process.env.E2E_BROWSER_CHANNEL ?? "chrome" },
      grepInvert: /@mobile/,
    },
    {
      name: "mobile",
      use: { ...devices["Pixel 7"], channel: process.env.E2E_BROWSER_CHANNEL ?? "chrome" },
      grep: /@mobile/,
    },
  ],
});
