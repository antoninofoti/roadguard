import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
  ],
  build: {
    outDir: 'dist-app',
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return;
          }

          if (id.includes('/firebase/')) {
            return 'vendor-firebase';
          }

          if (id.includes('/leaflet/') || id.includes('/react-leaflet')) {
            return 'vendor-map';
          }

          if (id.includes('/framer-motion/')) {
            return 'vendor-motion';
          }

          if (
            id.includes('/react/') ||
            id.includes('/react-dom/') ||
            id.includes('/react-router/') ||
            id.includes('/scheduler/')
          ) {
            return 'vendor-react';
          }

          return 'vendor-misc';
        },
      },
    },
  },
})
