import "@testing-library/jest-dom/vitest";

import { cleanup } from "@testing-library/react";
import { afterEach, vi } from "vitest";

// `server-only` throws outside a React Server environment; tests import pure helpers directly.
vi.mock("server-only", () => ({}));

afterEach(() => {
  cleanup();
  window.localStorage.clear();
  document.cookie.split("; ").forEach((c) => {
    document.cookie = `${c.split("=")[0]}=; Max-Age=0; Path=/`;
  });
});
