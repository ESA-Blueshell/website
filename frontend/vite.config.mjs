import vue from '@vitejs/plugin-vue'
import vuetify from 'vite-plugin-vuetify'
import { defineConfig } from 'vite'
import { fileURLToPath } from 'node:url'
import tsconfigPaths from 'vite-tsconfig-paths'
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
          @use "vuetify/styles" as *;
        `,
                sassOptions: {
                    api: 'modern'
                }
            }
        }
    },
    plugins: [
        tsconfigPaths(),
        vue(),
        vuetify({
            autoImport: true,
            configFile: './src/styles/settings.scss',
        }),
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
        host: true,
        hmr: {
            protocol: 'ws'
        },
        watch: {
            usePolling: true,
            interval: 100,
        }
    }
})