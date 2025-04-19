/**
 * plugins/index.js
 *
 * Automatically included in `./src/main.js`
 */

// Plugins
import {loadFonts} from './webfontloader.js'
import vuetify from './vuetify.js'

export function registerPlugins(app) {
  loadFonts()
  app.use(vuetify)
}
