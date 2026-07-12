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
    chunkSizeWarningLimit: 700,
    rollupOptions: {
      input: {
        main: path.resolve(__dirname, 'index.html'),
        order: path.resolve(__dirname, 'order.html'),
      },
      // No hand-rolled manualChunks. The previous split forced React into its own `react-vendor`
      // chunk while libraries that call `React.createContext` at module-init — react-leaflet
      // (maps-vendor), react-i18next (i18n-vendor), @radix-ui/lucide-react (ui-vendor) — landed in
      // OTHER chunks. Those chunks could evaluate before React's chunk initialised across the
      // boundary, so `React` was undefined → "Cannot read properties of undefined (reading
      // 'createContext')" and a blank white page. Vite/Rollup's automatic chunking orders chunks by
      // the real import graph and keeps React ahead of its dependents, so it does not have this bug.
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
