import {afterEach, beforeEach, vi} from "vitest"
import {config, type VueWrapper} from "@vue/test-utils"
import {type Component, defineComponent, getCurrentInstance, h, nextTick, resolveComponent} from "vue"

// `v-app` looked up by name rather than imported, because importing it drags Vuetify
// into a runner that has not installed one, where it throws for want of a theme. Until
// one is installed the name resolves to nothing and the mount is passed through
// untouched; once it does, this is the layout every `v-main` injects from.
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

config.global.config = {
  warnHandler: (msg: string) => {
    if (msg.startsWith("Failed to resolve component:")) return
    console.warn("[Vue warn]:", msg)
  },
}

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

config.global.mocks = {
  $vuetify: {
    display: {
      mdAndUp: true,
      smAndDown: false,
      smAndUp: true,
      sm: false,
      xs: false,
    },
    theme: {
      global: {
        name: "light",
        current: {
          dark: false,
        },
      },
      computedThemes: {
        light: {
          colors: {
            wallpaper: "#101010",
          },
        },
      },
    },
  },
}

// jsdom implements no media queries, so anything asking for one needs an answer
// here. A plain function rather than a mock: mockReset is on, which strips a
// mock's implementation before every test, and a matchMedia that returns
// undefined is worse than none at all — it passes a typeof check and then throws
// on the property access. Nothing needs to assert on these calls.
Object.defineProperty(globalThis, "matchMedia", {
  configurable: true,
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
})

// jsdom has no visual viewport, and the overlay positioning reads it bare rather
// than guarding it. A plain object for the same reason matchMedia is a plain
// function; nothing here is laid out, so the numbers only have to agree with the
// window they describe.
Object.defineProperty(globalThis, "visualViewport", {
  configurable: true,
  writable: true,
  value: {
    width: globalThis.innerWidth,
    height: globalThis.innerHeight,
    scale: 1,
    offsetLeft: 0,
    offsetTop: 0,
    pageLeft: 0,
    pageTop: 0,
    onresize: null,
    onscroll: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  },
})

// jsdom never intersects anything, so content held back until it scrolls into view
// would stay collapsed for the whole run. Reporting one intersection per observed
// element lets it resolve; nothing here waits for a second.
Object.defineProperty(globalThis, "IntersectionObserver", {
  configurable: true,
  value: class {
    constructor(
      private readonly callback: (
        entries: IntersectionObserverEntry[],
        observer: IntersectionObserver,
      ) => void,
    ) {}

    observe(target: Element) {
      this.callback(
        [{isIntersecting: true, intersectionRatio: 1, target} as IntersectionObserverEntry],
        this as unknown as IntersectionObserver,
      )
    }

    unobserve() {}
    disconnect() {}
    takeRecords(): IntersectionObserverEntry[] {
      return []
    }
  },
})

Object.defineProperty(globalThis.HTMLElement.prototype, "scrollIntoView", {
  configurable: true,
  value: vi.fn(),
})

Object.defineProperty(globalThis, "ResizeObserver", {
  configurable: true,
  value: class {
    observe() {}
    unobserve() {}
    disconnect() {}
  },
})

Object.defineProperty(globalThis.URL, "createObjectURL", {
  configurable: true,
  value: vi.fn(() => "blob:mock"),
})

Object.defineProperty(globalThis.URL, "revokeObjectURL", {
  configurable: true,
  value: vi.fn(),
})

const clipboard = {
  writeText: vi.fn().mockResolvedValue(undefined),
}

if (!("clipboard" in globalThis.navigator)) {
  Object.defineProperty(globalThis.navigator, "clipboard", {
    configurable: true,
    value: clipboard,
  })
} else {
  vi.spyOn(globalThis.navigator.clipboard, "writeText").mockResolvedValue(undefined)
}

beforeEach(() => {
  document.cookie = ""
})

afterEach(() => {
  vi.clearAllMocks()
})
