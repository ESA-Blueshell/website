/**
 * plugins/index.ts
 *
 * Automatically included in `./src/main.js`
 */

import type { App } from 'vue'

// Plugins
import { loadFonts } from './webfontloader'
import vuetify from './vuetify'

export function registerPlugins(app: App): void {
  loadFonts()
  app.use(vuetify)
}
