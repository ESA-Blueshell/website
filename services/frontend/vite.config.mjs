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
        rollupOptions: {
            output: {
                entryFileNames: 'assets/[hash].js',
                chunkFileNames: 'assets/[hash].js',
                assetFileNames: 'assets/[hash][extname]',
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
        allowedHosts: ['frontend', process.env.ALLOWED_HOST || 'v2.esa-blueshell.nl'],
        hmr: {
            protocol: 'ws'
        },
        watch: {
            usePolling: true,
            interval: 100,
        }
    }
})
