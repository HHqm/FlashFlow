import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  build: {
    target: "es2022",
    sourcemap: true,
  },
  server: {
    port: 3000,
    proxy: {
      "/api": "http://localhost:8080",
      "/notification-api": {
        target: "http://localhost:8082",
        rewrite: () => "/internal/notifications",
      },
      "/platform-health/gateway": {
        target: "http://localhost:8080",
        rewrite: () => "/actuator/health",
      },
      "/platform-health/trade": {
        target: "http://localhost:8081",
        rewrite: () => "/actuator/health",
      },
      "/platform-health/notification": {
        target: "http://localhost:8082",
        rewrite: () => "/actuator/health",
      },
    },
  },
});
