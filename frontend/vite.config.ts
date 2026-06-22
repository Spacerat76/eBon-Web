import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite";
import istanbul from "vite-plugin-istanbul";

const e2eCoverageEnabled = process.env.VITE_EBON_E2E_COVERAGE === "true";

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    ...(e2eCoverageEnabled
      ? [
          istanbul({
            exclude: ["src/**/*.test.*", "src/test/**", "src/components/ui/**", "src/lib/mock-api.ts"],
            extension: [".js", ".ts", ".tsx"],
            include: ["src/**/*"],
            requireEnv: false
          })
        ]
      : [])
  ],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url))
    }
  },
  build: {
    sourcemap: e2eCoverageEnabled ? "inline" : false
  },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true
      }
    }
  },
  preview: {
    port: 4173,
    strictPort: true
  }
});
