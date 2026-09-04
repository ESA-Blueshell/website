import {afterEach, beforeEach, vi} from "vitest"
import {config} from "@vue/test-utils"
import {h} from "vue"
import {createVuetify} from "vuetify"
import * as components from "vuetify/components"
import * as directives from "vuetify/directives"
import {aliases as mdiAliases, mdi} from "vuetify/iconsets/mdi"

// The width every mount starts at, set before the display state reads it. 1024 is jsdom's
// own and lands in `md`. A test wanting another size writes this and dispatches a resize.
const DEFAULT_VIEWPORT_WIDTH = 1024

// Writable, because jsdom declares it read-only and a test asking for a phone has
// nowhere else to say so.
Object.defineProperty(globalThis, "innerWidth", {
  configurable: true,
  writable: true,
  value: DEFAULT_VIEWPORT_WIDTH,
})

// Configured like `src/plugins/vuetify.ts`, minus what a runner cannot have: that module
// pulls in the icon fonts and stylesheets, and its custom icon set imports `.svg?component`,
// which vitest resolves to nothing. Every component and directive is registered rather than
// a curated list, so no test fails for a component another test never needed.
const vuetify = createVuetify({
  components,
  directives,
  locale: {locale: "en"},
  // Overlays teleport to the body by default, which is outside the wrapper a test searches.
  // Attached in place they stay findable, and jsdom positions nothing either way.
  defaults: {
    VDialog: {attach: true},
    VMenu: {attach: true},
    VOverlay: {attach: true},
    VTooltip: {attach: true},
  },
  icons: {
    defaultSet: "mdi",
    aliases: {
      ...mdiAliases,
      discord: "custom:discord",
      accountMultipleEdit: "custom:account-multiple-edit",
    },
    sets: {
      mdi,
      custom: {component: () => h("span")},
    },
  },
  theme: {
    defaultTheme: "light",
    variations: {colors: ["primary"], lighten: 1, darken: 1},
    themes: {
      light: {
        dark: false,
        colors: {
          primary: "#3387FA",
          accent: "#000000",
          error: "#ff0022",
          warning: "#8a5300",
          anchor: "#3387FA",
          wallpaper: "#1E1E1E",
        },
      },
      dark: {
        dark: true,
        colors: {
          primary: "#3387FA",
          accent: "#A8FF00",
          error: "#ff0022",
          warning: "#ffb020",
          anchor: "#3387FA",
          wallpaper: "#343434",
          background: "#1E1E1E",
        },
      },
    },
  },
})

config.global.plugins = [vuetify]

config.global.stubs = {
  RouterLink: {
    template: "<a><slot /></a>",
  },
  RouterView: {
    template: "<div><slot /></div>",
  },
}

beforeEach(() => {
  document.cookie = ""

  // The display state lives on the one instance every file shares, so a test that shrank
  // the window would hand the next one a phone.
  globalThis.innerWidth = DEFAULT_VIEWPORT_WIDTH
  globalThis.dispatchEvent(new Event("resize"))
})

afterEach(() => {
  vi.clearAllMocks()
})
