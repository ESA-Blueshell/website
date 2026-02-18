import vue from '@vitejs/plugin-vue'
import vuetify from 'vite-plugin-vuetify'
import {defineConfig} from 'vite'
import {fileURLToPath} from 'node:url'
import tsconfigPaths from 'vite-tsconfig-paths'
import svgLoader from 'vite-svg-loader'

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
          @use "@/styles/forms" as *;
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
            styles: {
                configFile: 'src/styles/settings.scss',
            }
        }),
        svgLoader(),
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
    },
    server: {
        port: 3000,
        host: true,
        allowedHosts: ['frontend', 'esa-blueshell.nl'],
        hmr: {
            protocol: 'ws'
        },
        watch: {
            usePolling: true,
            interval: 100,
        }
    }
})
