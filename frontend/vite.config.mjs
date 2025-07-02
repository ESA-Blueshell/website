import vue from '@vitejs/plugin-vue'
import vuetify from 'vite-plugin-vuetify'
import { defineConfig } from 'vite'
import { fileURLToPath } from 'node:url'

export default defineConfig({
  build: {
    target: "esnext"
  },
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: `
          @use "@/styles/fonts" as *;
          @use "@/styles/settings" as *;
          @use "@/styles/housestyle" as *;
          @use "@/styles/colors" as *;
          // now pull in the Vuetify Sass API:
          @use "vuetify/styles" as *;
        `,
        sassOptions: {
          api: 'modern'
        }
      }
    }
  },
  plugins: [
    vue(),
    vuetify({ autoImport: true }),
  ],
  optimizeDeps: {
    exclude: [
      'vuetify',
    ]
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
    extensions: ['.vue', '.ts', '.js']
  },
  server: {
    port: 3000,
    watch: {
      usePolling: true
    }
  }
})