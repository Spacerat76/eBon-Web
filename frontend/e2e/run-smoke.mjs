import { createServer } from "vite";
import { fileURLToPath } from "node:url";

const baseUrl = new URL(process.env.EBON_E2E_BASE_URL ?? "http://127.0.0.1:5173");
const frontendRoot = fileURLToPath(new URL("..", import.meta.url));

process.env.VITE_EBON_MOCK_API = "true";

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
  await import("./smoke.mjs");
} finally {
  await server.close();
}
