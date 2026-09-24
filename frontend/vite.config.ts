import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      // same-origin API in dev → no CORS, cookies/headers flow normally
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // WebSocket (STOMP) for real-time chat
      '/ws': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true,
      },
    },
  },
})
