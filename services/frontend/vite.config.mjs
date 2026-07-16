import vue from '@vitejs/plugin-vue'
import vuetify from 'vite-plugin-vuetify'
import {defineConfig} from 'vite'
import {fileURLToPath} from 'node:url'
import tsconfigPaths from 'vite-tsconfig-paths'
import svgLoader from 'vite-svg-loader'
import istanbul from 'vite-plugin-istanbul'

export default defineConfig({
    build: {
        target: "esnext",
        // vuetify (~500 kB) and country-data (~615 kB) are legitimately
        // above Vite's 500 kB default; lift the threshold above the floor.
        chunkSizeWarningLimit: 700,
        rollupOptions: {
            output: {
                entryFileNames: 'assets/[hash].js',
                chunkFileNames: 'assets/[hash].js',
                assetFileNames: 'assets/[hash][extname]',
                // Pin heavy vendors to dedicated chunks so the browser
                // caches them independently from app code.
                manualChunks(id) {
                    if (!/[\\/](?:node_modules|\.yarn[\\/]cache)[\\/]/.test(id)) return
                    if (/[\\/]vuetify[\\/]/.test(id)) return 'vuetify'
                    if (/[\\/]libphonenumber-js[\\/]/.test(id)) return 'libphonenumber'
                    if (/[\\/](?:world-countries|countries-list|i18n-nationality)[\\/]/.test(id)) return 'country-data'
                    if (/[\\/](?:v-phone-input|flag-icons)[\\/]/.test(id)) return 'phone-input'
                    if (/[\\/](?:vue|@vue|vue-router|vuex|vue-axios)[\\/]/.test(id)) return 'vue-core'
                    if (/[\\/]luxon[\\/]/.test(id)) return 'datetime'
                    if (/[\\/](?:marked|dompurify|xss|node-emoji)[\\/]/.test(id)) return 'markup'
                },
            },
        },
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
        istanbul({
            include: ['src/**/*'],
            exclude: [
                'node_modules',
                'src/services/api/**',
                '**/*.gen.ts',
            ],
            extension: ['.js', '.ts', '.vue'],
            requireEnv: true,
            cypress: false,
            checkProd: false,
            forceBuildInstrument: true,
        }),
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
        allowedHosts: ['frontend', process.env.ALLOWED_HOST || 'esa-blueshell.nl'],
        hmr: {
            protocol: 'ws'
        },
        watch: {
            usePolling: true,
            interval: 100,
        },
        warmup: {
            clientFiles: ['./src/main.ts', './src/App.vue'],
        }
    }
})
