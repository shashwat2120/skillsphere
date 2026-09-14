// import.meta.dirname rather than __dirname: Vite's native config loader does
// not support CommonJS globals, and that loader becomes the default soon.
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],

  resolve: {
    // '@' points at src so imports stay stable when files move. shadcn/ui
    // generates components that rely on this alias existing.
    alias: { '@': new URL('./src', import.meta.url).pathname },
  },

  server: {
    port: 5173,
    // The backend runs on 8080 and the SPA on 5173, so every API call is
    // cross-origin. Proxying in development means the browser sees one origin,
    // which matters more than convenience here: the refresh token is a
    // SameSite=Strict cookie, and a strict cookie is not sent cross-site at all.
    // Without this proxy the refresh flow would simply never work locally,
    // while working correctly in production behind a single domain.
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: 'http://localhost:8080', ws: true },
    },
  },

  build: {
    // Source maps in production builds: the bundle is already public, and
    // without them a stack trace from a real user is unreadable.
    sourcemap: true,
    rollupOptions: {
      output: {
        // Split the heaviest libraries into their own chunks so a change to
        // application code does not invalidate the cached vendor bundle.
        // Written as a function rather than the object form because Rollup's
        // current types only accept ManualChunksFunction here.
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined
          if (id.includes('recharts') || id.includes('d3-')) return 'charts'
          if (id.includes('framer-motion') || id.includes('motion-dom')) return 'motion'
          if (id.includes('react-router') || id.includes('/react-dom/') || id.includes('/react/')) {
            return 'react'
          }
          return undefined
        },
      },
    },
  },
})
