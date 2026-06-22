import { createServer } from "vite";
import { existsSync } from "node:fs";
import { mkdir, rm } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { join } from "node:path";

import { runSmoke } from "./smoke.mjs";

const baseUrl = new URL(process.env.EBON_E2E_BASE_URL ?? "http://127.0.0.1:5173");
const frontendRoot = fileURLToPath(new URL("..", import.meta.url));
const collectCoverage = process.env.EBON_E2E_COVERAGE === "true";
const coverageDirectory = join(frontendRoot, ".nyc_output");
const coverageOutputFile = join(coverageDirectory, "selenium-e2e.json");

process.env.VITE_EBON_MOCK_API = "true";
if (collectCoverage) {
  await rm(coverageDirectory, { force: true, recursive: true });
  await mkdir(coverageDirectory, { recursive: true });
  process.env.VITE_EBON_E2E_COVERAGE = "true";
}

const server = await createServer({
  root: frontendRoot,
  server: {
    host: baseUrl.hostname,
    port: Number(baseUrl.port || "5173"),
    strictPort: true
  }
});

try {
  await server.listen();
  await runSmoke({
    baseUrl: baseUrl.href.replace(/\/$/, ""),
    coverageOutputFile: collectCoverage ? coverageOutputFile : undefined
  });

  if (collectCoverage && !existsSync(coverageOutputFile)) {
    throw new Error("Selenium E2E coverage file was not written.");
  }
} finally {
  await server.close();
}
