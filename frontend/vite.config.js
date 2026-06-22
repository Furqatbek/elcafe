import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  base: '/admin/',
  // sockjs-client (pulled in by services/websocket.js) references a bare `global` at module load,
  // which is undefined in browsers and crashes the whole bundle ("global is not defined"). Vite
  // doesn't polyfill it in dev or build, so map it to globalThis — the standard Vite fix.
  define: { global: 'globalThis' },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    rollupOptions: {
      input: {
        main: path.resolve(__dirname, 'index.html'),
        order: path.resolve(__dirname, 'order.html'),
      },
    },
  },
  server: {
    host: true,
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
