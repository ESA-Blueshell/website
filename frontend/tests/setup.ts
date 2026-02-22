import {afterEach, beforeEach, vi} from "vitest"
import {config} from "@vue/test-utils"

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

Object.defineProperty(globalThis, "matchMedia", {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
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
