import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "node:path";

// API/WS base는 프록시 기준(/api, /ws). 개발 서버에서는 백엔드로 프록시한다.
const BACKEND = process.env.VITE_BACKEND_ORIGIN ?? "http://localhost:8080";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    port: 5173,
    proxy: {
      "/api": { target: BACKEND, changeOrigin: true },
      "/share": { target: BACKEND, changeOrigin: true },
      "/ws": { target: BACKEND, changeOrigin: true, ws: true },
    },
  },
});
