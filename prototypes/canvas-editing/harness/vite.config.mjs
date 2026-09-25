import vue from '@vitejs/plugin-vue'
import vuetify from 'vite-plugin-vuetify'
import {defineConfig} from 'vite'
import svgLoader from 'vite-svg-loader'
import tailwind from '@tailwindcss/vite'
import {existsSync} from 'node:fs'
import path from 'node:path'

// Canvas harness: the real frontend with a scratch overlay on top, built as one script a canvas
// board loads. Nothing here is the site's code; the overlay previews changes before they are made.
const H = path.dirname(new URL(import.meta.url).pathname)
const FE = path.resolve(H, '../wt-games/services/frontend')
const SRC = path.join(FE, 'src')
const OVERLAY = path.join(H, 'overlay')

const toOverlay = id => {
  if (!id.startsWith(SRC + path.sep)) return null
  const candidate = path.join(OVERLAY, path.relative(SRC, id))
  return existsSync(candidate) ? candidate : null
}
const toSource = id => id.startsWith(OVERLAY + path.sep) ? path.join(SRC, path.relative(OVERLAY, id)) : id

const overlay = {
  name: 'canvas-overlay',
  enforce: 'pre',
  async resolveId(source, importer, options) {
    if (source.startsWith('\0') || source.includes('?')) return null
    let resolved
    if (importer && importer.startsWith(OVERLAY + path.sep) && source.startsWith('.')) {
      // A relative import from an overlay file means the file beside its source twin.
      const inOverlay = path.resolve(path.dirname(importer), source)
      resolved = await this.resolve(inOverlay, importer, {...options, skipSelf: true})
      if (!resolved) resolved = await this.resolve(path.resolve(path.dirname(toSource(importer)), source), toSource(importer), {...options, skipSelf: true})
    } else {
      resolved = await this.resolve(source, importer && toSource(importer), {...options, skipSelf: true})
    }
    if (!resolved && (source.startsWith('@/') || source.startsWith(SRC + path.sep))) {
      // A file only the overlay has: new parts the preview adds. The alias may already have
      // turned `@/` into the source path, which does not exist.
      const rest = source.startsWith('@/') ? source.slice(2) : path.relative(SRC, source)
      resolved = await this.resolve(path.join(OVERLAY, rest), importer, {...options, skipSelf: true})
      if (resolved) return resolved
    }
    if (!resolved || resolved.external) return resolved
    const swapped = toOverlay(resolved.id.split('?')[0])
    return swapped ? {...resolved, id: swapped} : resolved
  },
  transform(code, id) {
    // The board opens on a page of its own choosing, so the router keeps its place in memory.
    if (id === path.join(SRC, 'plugins/router.ts') || id === path.join(OVERLAY, 'plugins/router.ts')) {
      return code.replace('createWebHistory("/")', 'createMemoryHistory()').replace('import {createRouter, createWebHistory', 'import {createRouter, createMemoryHistory, createWebHistory')
    }
    return null
  },
}

// One file a board loads: the stylesheet goes inside the script, which puts it in the page.
const inlineCss = {
  name: 'canvas-inline-css',
  apply: 'build',
  enforce: 'post',
  generateBundle(_, bundle) {
    const sheets = Object.values(bundle).filter(one => one.type === 'asset' && one.fileName.endsWith('.css'))
    const css = sheets.map(one => String(one.source)).join('\n')
    for (const sheet of sheets) delete bundle[sheet.fileName]
    const script = Object.values(bundle).find(one => one.type === 'chunk' && one.isEntry)
    script.code = `(function(){var s=document.createElement("style");s.setAttribute("data-blueshell-live","");s.textContent=${JSON.stringify(css)};(document.head||document.documentElement).appendChild(s)})();\n` + script.code
  },
}

export default defineConfig({
  root: FE,
  experimental: {
    // A placeholder the publish step swaps for each file's canvas upload (/_blob/<id>), or for
    // live/ beside a board when previewed locally: a canvas board's relative paths resolve
    // against the canvas's own base, where no bundled file is served.
    renderBuiltUrl(filename) {
      return '__LIVE_ASSET__/' + filename
    },
  },
  logLevel: 'warn',
  build: {
    target: 'esnext',
    outDir: process.env.HARNESS_OUT || path.join(H, 'dist'),
    emptyOutDir: true,
    sourcemap: false,
    // Small marks go inside the script; pictures and fonts sit beside the boards under live/.
    assetsInlineLimit: 12_000,
    assetsDir: 'live-assets',
    cssCodeSplit: false,
    chunkSizeWarningLimit: 100_000,
    rollupOptions: {
      input: path.join(H, 'src/main.ts'),
      output: {format: 'iife', inlineDynamicImports: true, entryFileNames: 'blueshell-live.js', assetFileNames: 'live-assets/[hash][extname]'},
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
        sassOptions: {api: 'modern'},
      },
    },
  },
  plugins: [overlay, inlineCss, vue(), vuetify({autoImport: true, styles: {configFile: 'src/styles/settings.scss'}}), svgLoader(), tailwind()],
  resolve: {
    alias: [
      {find: /^node:buffer$/, replacement: path.join(H, 'src/shims/buffer.ts')},
      {find: /^@playwright\/test$/, replacement: path.join(H, 'src/shims/playwright.ts')},
      {find: /^@\//, replacement: SRC + '/'},
    ],
  },
})
