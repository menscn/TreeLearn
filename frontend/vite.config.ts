import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  base: "./",
  plugins: [react()],
  server: { host: "127.0.0.1", port: 5173 },
  preview: { host: "127.0.0.1", port: 4173 },
  build: {
    cssCodeSplit: false,
    modulePreload: false,
    assetsInlineLimit: 1024 * 1024
  },
  test: {
    environment: "node",
    include: ["test/**/*.test.ts"]
  }
});
