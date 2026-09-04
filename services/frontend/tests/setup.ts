import {afterEach, beforeEach, vi} from "vitest"
import {config, type VueWrapper} from "@vue/test-utils"
import {type Component, defineComponent, getCurrentInstance, h, nextTick, resolveComponent} from "vue"
import {createVuetify} from "vuetify"
import * as components from "vuetify/components"
import * as directives from "vuetify/directives"
import {aliases as mdiAliases, mdi} from "vuetify/iconsets/mdi"

// The width every mount starts at, set before the display state reads it. 1024 is jsdom's
// own, and it lands in `md`, which is what the `$vuetify` mock this replaces described:
// `mdAndUp` true, `sm` and `xs` false. A test wanting another size writes this and
// dispatches a resize.
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

// `v-app` looked up by name rather than imported, so the layout stays the same object the
// plugin registered and a `v-main` inside the mount injects from this one.
//
// Renamed on the way through: a shallow mount stubs every component below the root by
// name, and a stub never calls the slot the mounted component sits in. `VApp` itself
// cannot be the name opted out of stubbing below, because opting a name out registers
// a bodyless placeholder under it that the application's own `<v-app>` resolves to.
let appLayoutRoot: Component | undefined

const AppLayout = defineComponent({
  name: "AppLayout",
  setup: (_props, {slots}) => () => {
    const vApp = resolveComponent("v-app")
    if (typeof vApp === "string") return slots.default?.()

    appLayoutRoot ??= {...vApp, name: "AppLayoutRoot"}
    return h(appLayoutRoot, null, {default: () => slots.default?.()})
  },
})

// Vue Test Utils has no option for a wrapping component, so the layout is pushed in
// by patching the render of the root it mounts. The component under test has to keep
// creating its vnode in that root's render — a shallow mount only spares the root
// from being stubbed, and a component wrapped one level deeper is no longer it.
config.global.mixins = [
  {
    beforeCreate() {
      const instance = getCurrentInstance()
      if (!instance || instance.parent) return

      const render = instance.render
      instance.render = function (...args: unknown[]) {
        const mounted = render?.apply(this, args as never)
        return h(AppLayout, null, {default: () => mounted})
      }
    },
  },
]

// The layout stands between the mounted root and the component under test, and Vue
// Test Utils reads that gap as "not your own component" and refuses setProps. The
// setter it holds writes the same reactive props either way, so hand it back.
type PropsSetter = {__setProps?: (props: Record<string, unknown>) => void}

config.plugins.VueWrapper.install((wrapper: VueWrapper) => ({
  setProps(props: Record<string, unknown>) {
    const setProps = (wrapper as VueWrapper & PropsSetter).__setProps
    if (!setProps) throw new Error("You can only use setProps on your mounted component")

    setProps(props)
    return nextTick()
  },
}))

config.global.stubs = {
  // false for both, or a shallow mount would stub the layout away and the component
  // under test would render inside a stub that never calls its slot. Only the root
  // escapes stubbing, and the layout is not it.
  AppLayout: false,
  AppLayoutRoot: false,
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
