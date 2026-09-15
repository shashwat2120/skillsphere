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
    // The SPA runs on 5173, so every API call is cross-origin. Proxying in
    // development means the browser sees one origin, which matters more than
    // convenience here: the refresh token is a SameSite=Strict cookie, and a
    // strict cookie is not sent cross-site at all. Without this proxy the
    // refresh flow would simply never work locally, while working correctly
    // in production behind a single domain.
    //
    // /api goes to the Sprint 6 gateway (:9000), not the monolith directly —
    // every extracted service's routes only exist there now; the monolith
    // itself is just the gateway's catch-all for what hasn't moved out yet.
    //
    // /ws is the one exception, routed straight to realtime-service (:8088)
    // instead: Spring Cloud Gateway Server MVC is servlet-based, and a plain
    // HandlerFunctions.http() route cannot proxy a WebSocket upgrade — a live
    // handshake through the gateway came back 400, the same handshake
    // against realtime-service directly came back a correct 101 Switching
    // Protocols. Keeping /ws off the gateway entirely (rather than chasing a
    // low-level proxy servlet for one path) is the same trade-off real
    // gateways make routinely: a shared API gateway and a dedicated
    // WebSocket ingress are commonly two different things, not evidence this
    // one is broken.
    proxy: {
      '/api': { target: 'http://localhost:9000', changeOrigin: true },
      '/ws': { target: 'http://localhost:8088', ws: true },
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
