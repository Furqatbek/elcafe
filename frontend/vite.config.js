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
      output: {
        // Split the big shared vendors into their own long-lived, separately-cached chunks so the
        // initial download isn't one monolith and a page-only dep (leaflet) loads with its page.
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined;
          if (id.includes('react-router') || id.includes('/react-dom/') || /\/react\//.test(id)) return 'react-vendor';
          if (id.includes('@radix-ui') || id.includes('lucide-react')) return 'ui-vendor';
          if (id.includes('i18next')) return 'i18n-vendor';
          if (id.includes('leaflet')) return 'maps-vendor';
          if (id.includes('@stomp') || id.includes('sockjs')) return 'realtime-vendor';
          if (id.includes('date-fns')) return 'date-vendor';
          return 'vendor';
        },
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
