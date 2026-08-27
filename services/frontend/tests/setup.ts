import {afterEach, beforeEach, vi} from "vitest"
import {config} from "@vue/test-utils"

config.global.config = {
  warnHandler: (msg: string) => {
    if (msg.startsWith("Failed to resolve component:")) return
    console.warn("[Vue warn]:", msg)
  },
}

config.global.stubs = {
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
