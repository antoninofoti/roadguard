import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    outDir: "dist-app",
    minify: "oxc",
    chunkSizeWarningLimit: 600,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes("node_modules")) {
            return;
          }

          // Firebase split (reduce main chunk pressure)
          if (id.includes("/@firebase/app/") || id.includes("/firebase/app/")) {
            return "firebase-app";
          }
          if (
            id.includes("/@firebase/auth/") ||
            id.includes("/firebase/auth/")
          ) {
            return "firebase-auth";
          }
          if (
            id.includes("/@firebase/firestore/") ||
            id.includes("/firebase/firestore/")
          ) {
            return "firebase-firestore";
          }
          if (id.includes("/@firebase/") || id.includes("/firebase/")) {
            return "vendor-firebase";
          }

          // Maps and clustering (lazy-load by route)
          if (id.includes("/leaflet/")) {
            return "vendor-leaflet";
          }
          if (id.includes("/react-leaflet")) {
            return "vendor-leaflet";
          }
          if (id.includes("/react-leaflet-cluster/")) {
            return "vendor-leaflet";
          }

          // Charts and visualization (lazy-load)
          if (id.includes("/recharts/")) {
            return "vendor-charts";
          }

          // Animations
          if (id.includes("/framer-motion/")) {
            return "vendor-motion";
          }

          // Router (essential)
          if (id.includes("/react-router-dom/")) {
            return "vendor-router";
          }

          // React core
          if (
            id.includes("/react/") ||
            id.includes("/react-dom/") ||
            id.includes("/scheduler/")
          ) {
            return "vendor-react";
          }

          // Icons (small, utility)
          if (id.includes("/lucide-react/")) {
            return "vendor-icons";
          }

          // Catch-all
          return "vendor-misc";
        },
      },
    },
  },
  // Optimize dependencies pre-bundling
  optimizeDeps: {
    include: ["react", "react-dom", "react-router-dom"],
    exclude: ["leaflet", "react-leaflet-cluster"],
  },
});
