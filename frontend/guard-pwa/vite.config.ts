import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5176,
    strictPort: true,
    // Quick tunnels (cloudflared tunnel --url ...) get a random *.trycloudflare.com
    // hostname each run — Vite's Host-header check would 403 it otherwise since
    // it isn't localhost. The leading dot allows any subdomain.
    allowedHosts: [".trycloudflare.com"],
    // Forwards API calls to the backend so the app can be reached through a
    // tunnel (or any host other than localhost) from another device — the
    // browser always calls same-origin /api/..., and only this dev server
    // knows the backend actually lives on :8080. See src/api/client.ts.
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
})
