import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5175,
    strictPort: true,
    // Same reasoning as guard-pwa's vite.config.ts: forwards API calls so the
    // app works when reached through a tunnel from another device, and
    // allows *.trycloudflare.com hostnames past Vite's Host-header check.
    allowedHosts: [".trycloudflare.com"],
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
})
